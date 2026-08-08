use std::fs::File;
use std::io::{self, Read, Write};
use std::path::Path;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use crate::http::{Client, Response};
use anyhow::{Context, Result, anyhow, bail};

#[derive(Clone, Debug)]
pub struct FileDownloadProgress {
    pub downloaded_bytes: u64,
    pub total_bytes: Option<u64>,
    pub bytes_per_second: f64,
    pub completed: bool,
}

pub type ProgressCallback = Arc<dyn Fn(FileDownloadProgress) + Send + Sync>;
pub type StatusCallback = Arc<dyn Fn(String) + Send + Sync>;

pub fn file(
    url: &str,
    target_path: &Path,
    max_parallel_parts: usize,
    report_progress: Option<ProgressCallback>,
    report_status: Option<StatusCallback>,
) -> Result<()> {
    if max_parallel_parts == 0 {
        bail!("max_parallel_parts必须大于0");
    }

    let client = Client::with_timeout(Duration::ZERO);

    let response = client
        .get(url)
        .header("Range", "bytes=0-0")
        .send()
        .with_context(|| format!("请求下载探测失败: {url}"))?;

    if response.status() != 206 {
        let response = response.error_for_status().context("下载响应失败")?;
        return download_response(response, target_path, report_progress);
    }

    let total_bytes = response
        .header("Content-Range")
        .and_then(parse_content_range_length);
    drop(response);

    let Some(total_bytes) = total_bytes else {
        return download_single(&client, url, target_path, report_progress);
    };

    match download_parallel(
        &client,
        url,
        target_path,
        total_bytes,
        max_parallel_parts,
        report_progress.clone(),
        report_status.clone(),
    ) {
        Ok(()) => Ok(()),
        Err(error) => {
            if let Some(report_status) = report_status {
                report_status(format!("并行下载失败，切换为单流下载: {error}"));
            }
            download_single(&client, url, target_path, report_progress)
        }
    }
}

fn download_single(
    client: &Client,
    url: &str,
    target_path: &Path,
    report_progress: Option<ProgressCallback>,
) -> Result<()> {
    let response = client
        .get(url)
        .send()
        .with_context(|| format!("请求下载失败: {url}"))?
        .error_for_status()
        .context("下载响应失败")?;
    download_response(response, target_path, report_progress)
}

fn download_response(
    mut response: Response,
    target_path: &Path,
    report_progress: Option<ProgressCallback>,
) -> Result<()> {
    let total_bytes = response.content_length();
    let progress = ProgressTracker::new(total_bytes, report_progress);
    let result = (|| -> Result<()> {
        let mut target = File::create(target_path)
            .with_context(|| format!("创建下载文件失败: {}", target_path.display()))?;
        let downloaded_bytes = copy_stream(&mut response, &mut target, &progress)?;
        if let Some(expected_bytes) = total_bytes {
            if downloaded_bytes != expected_bytes {
                bail!("下载长度不正确，应为{expected_bytes}，实际为{downloaded_bytes}");
            }
        }
        Ok(())
    })();
    progress.finish();
    result
}

fn download_parallel(
    client: &Client,
    url: &str,
    target_path: &Path,
    total_bytes: u64,
    max_parallel_parts: usize,
    report_progress: Option<ProgressCallback>,
    report_status: Option<StatusCallback>,
) -> Result<()> {
    let part_count = max_parallel_parts.min(total_bytes as usize).max(1);
    let part_size = total_bytes.div_ceil(part_count as u64);
    if let Some(report_status) = report_status {
        report_status(format!("启动{part_count}线程高速下载"));
    }

    let part_paths: Vec<_> = (0..part_count)
        .map(|index| part_path(target_path, index))
        .collect();
    let progress = ProgressTracker::new(Some(total_bytes), report_progress);

    let result = (|| -> Result<()> {
        let mut handles = Vec::with_capacity(part_count);
        for (index, part_path) in part_paths.iter().enumerate() {
            let client = client.clone();
            let url = url.to_owned();
            let part_path = part_path.clone();
            let progress = progress.clone();
            let start = index as u64 * part_size;
            let end = (total_bytes - 1).min(start + part_size - 1);
            handles.push(thread::spawn(move || {
                download_range(&client, &url, &part_path, start, end, &progress)
            }));
        }

        let mut thread_error = None;
        for handle in handles {
            match handle.join() {
                Ok(Ok(())) => {}
                Ok(Err(error)) if thread_error.is_none() => thread_error = Some(error),
                Ok(Err(_)) => {}
                Err(_) if thread_error.is_none() => {
                    thread_error = Some(anyhow!("Range下载线程崩溃"));
                }
                Err(_) => {}
            }
        }
        if let Some(error) = thread_error {
            return Err(error);
        }

        let mut target = File::create(target_path)
            .with_context(|| format!("创建合并文件失败: {}", target_path.display()))?;
        for part_path in &part_paths {
            let mut part = File::open(part_path)
                .with_context(|| format!("打开下载分片失败: {}", part_path.display()))?;
            io::copy(&mut part, &mut target).context("合并下载分片失败")?;
        }
        Ok(())
    })();

    progress.finish();
    for part_path in part_paths {
        let _ = std::fs::remove_file(part_path);
    }
    result
}

fn download_range(
    client: &Client,
    url: &str,
    part_path: &Path,
    start: u64,
    end: u64,
    progress: &ProgressTracker,
) -> Result<()> {
    let range = format!("bytes={start}-{end}");
    let mut response = client
        .get(url)
        .header("Range", range)
        .send()
        .with_context(|| format!("Range请求失败: {start}-{end}"))?;
    if response.status() != 206 {
        bail!("服务器未返回Range数据: {}", response.status());
    }

    let content_range = response
        .header("Content-Range")
        .ok_or_else(|| anyhow!("服务器未返回Content-Range"))?;
    let (actual_start, actual_end) = parse_content_range(content_range)
        .ok_or_else(|| anyhow!("服务器返回了错误的Range: {content_range}"))?;
    if actual_start != start || actual_end != end {
        bail!("服务器返回了错误的Range: {content_range}");
    }

    let mut target = File::create(part_path)
        .with_context(|| format!("创建下载分片失败: {}", part_path.display()))?;
    let downloaded_bytes = copy_stream(&mut response, &mut target, progress)?;
    let expected_bytes = end - start + 1;
    if downloaded_bytes != expected_bytes {
        bail!("分片长度不正确，应为{expected_bytes}，实际为{downloaded_bytes}");
    }
    Ok(())
}

fn copy_stream<R: Read, W: Write>(
    source: &mut R,
    target: &mut W,
    progress: &ProgressTracker,
) -> Result<u64> {
    let mut buffer = [0u8; 128 * 1024];
    let mut downloaded_bytes = 0;
    loop {
        let bytes_read = source.read(&mut buffer).context("读取下载流失败")?;
        if bytes_read == 0 {
            break;
        }
        target
            .write_all(&buffer[..bytes_read])
            .context("写入下载文件失败")?;
        progress.report(bytes_read as u64);
        downloaded_bytes += bytes_read as u64;
    }
    Ok(downloaded_bytes)
}

fn parse_content_range_length(value: &str) -> Option<u64> {
    value.rsplit_once('/')?.1.parse().ok()
}

fn parse_content_range(value: &str) -> Option<(u64, u64)> {
    let range = value.strip_prefix("bytes ")?.split('/').next()?;
    let (start, end) = range.split_once('-')?;
    Some((start.parse().ok()?, end.parse().ok()?))
}

fn part_path(target_path: &Path, index: usize) -> std::path::PathBuf {
    std::path::PathBuf::from(format!("{}.part-{index}", target_path.display()))
}

#[derive(Clone)]
struct ProgressTracker {
    total_bytes: Option<u64>,
    report_progress: Option<ProgressCallback>,
    started_at: Instant,
    downloaded_bytes: Arc<std::sync::atomic::AtomicU64>,
    report_lock: Arc<Mutex<ReportState>>,
}

struct ReportState {
    last_report_at: Instant,
}

impl ProgressTracker {
    fn new(total_bytes: Option<u64>, report_progress: Option<ProgressCallback>) -> Self {
        Self {
            total_bytes,
            report_progress,
            started_at: Instant::now(),
            downloaded_bytes: Arc::new(std::sync::atomic::AtomicU64::new(0)),
            report_lock: Arc::new(Mutex::new(ReportState {
                last_report_at: Instant::now() - Duration::from_millis(100),
            })),
        }
    }

    fn report(&self, bytes: u64) {
        self.downloaded_bytes
            .fetch_add(bytes, std::sync::atomic::Ordering::Relaxed);
        self.publish(false);
    }

    fn finish(&self) {
        self.publish(true);
    }

    fn publish(&self, completed: bool) {
        let Some(report_progress) = &self.report_progress else {
            return;
        };
        let mut state = self.report_lock.lock().expect("下载进度锁中毒");
        let elapsed = self.started_at.elapsed();
        if !completed && state.last_report_at.elapsed() < Duration::from_millis(100) {
            return;
        }
        state.last_report_at = Instant::now();
        let downloaded = self
            .downloaded_bytes
            .load(std::sync::atomic::Ordering::Relaxed);
        report_progress(FileDownloadProgress {
            downloaded_bytes: downloaded,
            total_bytes: self.total_bytes,
            bytes_per_second: downloaded as f64 / elapsed.as_secs_f64().max(0.001),
            completed,
        });
    }
}
