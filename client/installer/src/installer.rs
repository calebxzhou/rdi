use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::AppResult;

pub struct InstallRequest {
    pub install_path: PathBuf,
    pub create_desktop_shortcut: bool,
    pub create_start_menu_shortcut: bool,
}

pub struct InstallResult {
    pub warnings: Vec<String>,
}

pub trait ProgressSink {
    fn report(&mut self, value: &str);
}

pub fn install(
    request: InstallRequest,
    progress: &mut dyn ProgressSink,
) -> AppResult<InstallResult> {
    let install_path = std::path::absolute(&request.install_path)
        .map_err(|error| format!("无法确定安装目录:{}", error))?;
    let parent_directory = install_path
        .parent()
        .map(Path::to_path_buf)
        .unwrap_or_else(|| install_path.clone());
    fs::create_dir_all(&parent_directory)
        .map_err(|error| format!("创建安装目录父目录失败:{error}"))?;

    let mut staging = StagingDirectory::create(&parent_directory)?;
    let install_result = (|| {
        crate::archive::extract_to(&staging.path, progress)?;
        if !staging.path.join("start.exe").is_file() {
            return Err("安装包中缺少start.exe".to_owned());
        }

        progress.report("正在写入安装目录");
        merge_into_install_directory(&staging.path, &install_path)?;
        Ok(())
    })();
    let cleanup_result = staging.cleanup();

    match (install_result, cleanup_result) {
        (Err(error), Ok(())) => return Err(error),
        (Err(error), Err(cleanup_error)) => {
            return Err(format!("{error}；清理暂存目录失败:{cleanup_error}"));
        }
        (Ok(()), Err(error)) => return Err(format!("清理暂存目录失败:{error}")),
        (Ok(()), Ok(())) => {}
    }

    let mut warnings = Vec::new();
    let start_exe = install_path.join("start.exe");
    if request.create_desktop_shortcut {
        try_create_shortcut(
            &mut warnings,
            "Desktop",
            crate::windows::KnownFolder::Desktop,
            &start_exe,
            &install_path,
        );
    }
    if request.create_start_menu_shortcut {
        try_create_shortcut(
            &mut warnings,
            "Start Menu",
            crate::windows::KnownFolder::Programs,
            &start_exe,
            &install_path,
        );
    }

    Ok(InstallResult { warnings })
}

fn merge_into_install_directory(staging: &Path, install_path: &Path) -> AppResult<()> {
    if !install_path.exists() {
        fs::rename(staging, install_path).map_err(|error| format!("移动安装目录失败:{error}"))?;
        return Ok(());
    }
    if !install_path.is_dir() {
        return Err(format!("安装路径不是文件夹:{}", install_path.display()));
    }

    let (directories, files) = collect_tree(staging)?;
    for source_file in &files {
        let relative = source_file
            .strip_prefix(staging)
            .map_err(|error| format!("计算安装文件路径失败:{error}"))?;
        let destination = install_path.join(relative);
        if destination.is_file() {
            crate::windows::ensure_exclusive_file(&destination)?;
        }
    }

    for source_directory in directories {
        let relative = source_directory
            .strip_prefix(staging)
            .map_err(|error| format!("计算安装目录路径失败:{error}"))?;
        fs::create_dir_all(install_path.join(relative))
            .map_err(|error| format!("创建安装目录失败:{error}"))?;
    }
    for source_file in files {
        let relative = source_file
            .strip_prefix(staging)
            .map_err(|error| format!("计算安装文件路径失败:{error}"))?;
        let destination = install_path.join(relative);
        if let Some(parent) = destination.parent() {
            fs::create_dir_all(parent)
                .map_err(|error| format!("创建安装文件父目录失败:{error}"))?;
        }
        crate::windows::move_file_replace(&source_file, &destination)?;
    }
    Ok(())
}

fn collect_tree(root: &Path) -> AppResult<(Vec<PathBuf>, Vec<PathBuf>)> {
    let mut directories = Vec::new();
    let mut files = Vec::new();
    collect_tree_inner(root, &mut directories, &mut files)?;
    Ok((directories, files))
}

fn collect_tree_inner(
    directory: &Path,
    directories: &mut Vec<PathBuf>,
    files: &mut Vec<PathBuf>,
) -> AppResult<()> {
    for entry in fs::read_dir(directory).map_err(|error| format!("读取暂存目录失败:{error}"))?
    {
        let entry = entry.map_err(|error| format!("读取暂存条目失败:{error}"))?;
        let path = entry.path();
        let file_type = entry
            .file_type()
            .map_err(|error| format!("读取暂存条目类型失败:{error}"))?;
        if file_type.is_dir() {
            directories.push(path.clone());
            collect_tree_inner(&path, directories, files)?;
        } else if file_type.is_file() {
            files.push(path);
        } else {
            return Err(format!("暂存目录包含不支持的条目:{}", path.display()));
        }
    }
    Ok(())
}

fn try_create_shortcut(
    warnings: &mut Vec<String>,
    name: &str,
    folder: crate::windows::KnownFolder,
    target: &Path,
    working_directory: &Path,
) {
    let result = (|| {
        let folder_path = crate::windows::known_folder_path(folder)?;
        let shortcut_path = match folder {
            crate::windows::KnownFolder::Desktop => folder_path.join("rdi.lnk"),
            crate::windows::KnownFolder::Programs => folder_path.join("rdi").join("rdi.lnk"),
            crate::windows::KnownFolder::Documents => folder_path.join("rdi.lnk"),
        };
        crate::windows::create_shortcut(&shortcut_path, target, working_directory)
    })();
    if let Err(error) = result {
        warnings.push(format!("{name} shortcut创建失败:{error}"));
    }
}

struct StagingDirectory {
    path: PathBuf,
    active: bool,
}

impl StagingDirectory {
    fn create(parent: &Path) -> AppResult<Self> {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|error| format!("读取系统时间失败:{error}"))?
            .as_nanos();
        let process_id = crate::windows::process_id();

        for attempt in 0..10u32 {
            let path = parent.join(format!(
                ".rdi-installing-{process_id}-{timestamp}-{attempt}"
            ));
            match fs::create_dir(&path) {
                Ok(()) => return Ok(Self { path, active: true }),
                Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => continue,
                Err(error) => return Err(format!("创建暂存目录失败:{error}")),
            }
        }
        Err("无法创建唯一暂存目录".to_owned())
    }

    fn cleanup(&mut self) -> Result<(), String> {
        if !self.active || !self.path.exists() {
            self.active = false;
            return Ok(());
        }
        let result =
            fs::remove_dir_all(&self.path).map_err(|error| format!("删除暂存目录失败:{error}"));
        self.active = false;
        result
    }
}

impl Drop for StagingDirectory {
    fn drop(&mut self) {
        if self.active && self.path.exists() {
            let _ = fs::remove_dir_all(&self.path);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{InstallRequest, ProgressSink, install};
    use std::time::{SystemTime, UNIX_EPOCH};

    struct NoProgress;

    impl ProgressSink for NoProgress {
        fn report(&mut self, _value: &str) {}
    }

    #[test]
    fn installs_embedded_archive_and_merges_existing_directory() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("system time")
            .as_nanos();
        let root = std::env::temp_dir().join(format!(
            "rdi-installer-test-{}-{unique}",
            crate::windows::process_id()
        ));
        let install_path = root.join("rdi");
        let mut progress = NoProgress;

        install(
            InstallRequest {
                install_path: install_path.clone(),
                create_desktop_shortcut: false,
                create_start_menu_shortcut: false,
            },
            &mut progress,
        )
        .expect("first installation");
        assert!(install_path.join("start.exe").is_file());
        assert_eq!(
            std::fs::read_to_string(install_path.join("lib/fixture.txt")).expect("fixture file"),
            "fixture-library\n"
        );

        std::fs::write(install_path.join("keep.txt"), "keep").expect("extra file");
        install(
            InstallRequest {
                install_path: install_path.clone(),
                create_desktop_shortcut: false,
                create_start_menu_shortcut: false,
            },
            &mut progress,
        )
        .expect("merge installation");
        assert_eq!(
            std::fs::read_to_string(install_path.join("keep.txt")).expect("extra file"),
            "keep"
        );

        std::fs::remove_dir_all(root).expect("test cleanup");
    }
}
