use std::collections::HashMap;
use std::fs::{self, File};
use std::io::Read;
use std::path::Path;
use std::time::Duration;

use anyhow::{Context, Result, anyhow, bail};
use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use uuid::Uuid;
use zip::ZipArchive;

use crate::callbacks::MessageCallback;
use crate::download::{self, ProgressCallback};
use crate::http::Client;
use crate::library_switch_recovery::{
    self, LibrarySwitchResult, PromptCallback, UiLibraryUpdateResult,
};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UiLibraryRelease {
    pub version: Option<String>,
    pub manifest: HashMap<String, String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiResponse<T> {
    #[serde(alias = "Code")]
    pub code: i32,
    #[serde(alias = "Msg")]
    pub msg: String,
    #[serde(alias = "Data")]
    pub data: Option<T>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerEntry {
    #[serde(alias = "Api")]
    pub api: Option<String>,
}

pub fn try_update(
    primary_url: &str,
    launcher_root: &Path,
    write_info: MessageCallback,
    write_warning: MessageCallback,
    report_progress: ProgressCallback,
    use_backup_api: bool,
    player_ipv4: Option<&str>,
    prompt_recovery: Option<PromptCallback>,
) -> UiLibraryUpdateResult {
    match try_update_inner(
        primary_url,
        launcher_root,
        &write_info,
        &write_warning,
        report_progress,
        use_backup_api,
        player_ipv4,
        prompt_recovery,
    ) {
        Ok(result) => result,
        Err(error) => {
            write_warning(format!("UI库更新失败，将使用本地版本启动: {error}"));
            UiLibraryUpdateResult::LocalVersionAvailable
        }
    }
}

fn try_update_inner(
    primary_url: &str,
    launcher_root: &Path,
    write_info: &MessageCallback,
    write_warning: &MessageCallback,
    report_progress: ProgressCallback,
    use_backup_api: bool,
    player_ipv4: Option<&str>,
    prompt_recovery: Option<PromptCallback>,
) -> Result<UiLibraryUpdateResult> {
    let backup_url = if use_backup_api {
        resolve_backup_url(primary_url, player_ipv4, write_info)
    } else {
        None
    };
    let api_urls = unique_urls(backup_url, primary_url);
    let Some(release) = get_release(&api_urls, write_info) else {
        write_warning("无法获取UI库信息，将使用本地版本启动".to_owned());
        return Ok(UiLibraryUpdateResult::LocalVersionAvailable);
    };

    let lib_directory = launcher_root.join("lib");
    let installed_version = read_installed_version(&lib_directory);
    if let Some(target_version) = &release.version {
        write_info(if let Some(installed_version) = &installed_version {
            format!("本地UI版本{installed_version}，目标版本为{target_version}")
        } else {
            format!("本地UI版本未知，目标版本为{target_version}")
        });
    }

    if is_current(&lib_directory, &release) {
        write_info("UI库已是最新版本".to_owned());
        return Ok(UiLibraryUpdateResult::UpToDate);
    }

    let update_id = Uuid::new_v4().simple().to_string();
    let staging_directory = launcher_root.join(format!("lib.updating-{update_id}"));
    let backup_directory = launcher_root.join(format!("lib.previous-{update_id}"));

    let update_result = (|| -> Result<UiLibraryUpdateResult> {
        fs::create_dir_all(&staging_directory).context("创建UI库暂存目录失败")?;
        for (name, expected_sha1) in &release.manifest {
            let current_file = lib_directory.join(name);
            let staging_file = staging_directory.join(name);
            if matches_sha1(&current_file, expected_sha1) {
                fs::copy(&current_file, &staging_file)
                    .with_context(|| format!("复制{name}到暂存目录失败"))?;
                continue;
            }

            write_info(format!("正在更新{name}"));
            if !download_library(
                &api_urls,
                release.version.as_deref(),
                name,
                expected_sha1,
                &staging_file,
                write_info,
                report_progress.clone(),
            )? {
                bail!("{name}下载或校验失败");
            }
        }

        validate_staging_version(&staging_directory, release.version.as_deref())?;
        let switch_result = library_switch_recovery::switch(
            &lib_directory,
            &staging_directory,
            &backup_directory,
            write_info.clone(),
            write_warning.clone(),
            prompt_recovery,
        )?;
        if switch_result == LibrarySwitchResult::LaunchAborted {
            write_info("UI库恢复未完成，本次不再启动客户端".to_owned());
            return Ok(UiLibraryUpdateResult::LaunchAborted);
        }

        write_info("UI库更新完成".to_owned());
        if backup_directory.exists() {
            if let Err(error) = fs::remove_dir_all(&backup_directory) {
                write_warning(format!("旧UI库清理失败: {error}"));
            }
        }
        Ok(UiLibraryUpdateResult::Updated)
    })();

    if let Err(error) = fs::remove_dir_all(&staging_directory) {
        if staging_directory.exists() {
            write_warning(format!("更新临时文件清理失败: {error}"));
        }
    }
    update_result
}

fn unique_urls(backup_url: Option<String>, primary_url: &str) -> Vec<String> {
    let mut urls: Vec<String> = Vec::new();
    for url in backup_url
        .into_iter()
        .chain(std::iter::once(primary_url.to_owned()))
    {
        let url = url.trim_end_matches('/').to_owned();
        if !url.is_empty() && !urls.iter().any(|item| item.eq_ignore_ascii_case(&url)) {
            urls.push(url);
        }
    }
    urls
}

fn resolve_backup_url(
    primary_url: &str,
    player_ipv4: Option<&str>,
    write_info: &MessageCallback,
) -> Option<String> {
    let result = (|| -> Result<Option<String>> {
        let mut url = format!("{}/server-entry", primary_url.trim_end_matches('/'));
        if let Some(player_ipv4) = player_ipv4.filter(|value| !value.trim().is_empty()) {
            url.push_str("?myIp=");
            url.push_str(&urlencoding::encode(player_ipv4));
        }
        let response: ApiResponse<ServerEntry> = metadata_client()
            .get(url)
            .send()
            .context("请求server-entry失败")?
            .error_for_status()
            .context("server-entry响应失败")?
            .json()
            .context("解析server-entry失败")?;
        let Some(api) = response
            .data
            .filter(|_| response.code == 0)
            .and_then(|entry| entry.api)
        else {
            return Ok(None);
        };
        let api = api.trim();
        let backup_url = if api.contains("://") {
            api.to_owned()
        } else {
            format!("https://{api}")
        };
        write_info("将优先使用加速API，非常快".to_owned());
        Ok(Some(backup_url))
    })();

    match result {
        Ok(value) => value,
        Err(error) => {
            write_info(format!("获取加速API失败，继续使用默认API: {error}"));
            None
        }
    }
}

fn get_release(api_urls: &[String], write_info: &MessageCallback) -> Option<UiLibraryRelease> {
    if let Some(release) = get_versioned_release(api_urls, write_info) {
        return Some(release);
    }

    write_info("服务端不支持版本化UI库接口，使用兼容接口".to_owned());
    get_manifest(api_urls, write_info).and_then(|manifest| {
        if is_valid_manifest(&manifest) {
            Some(UiLibraryRelease {
                version: None,
                manifest,
            })
        } else {
            None
        }
    })
}

fn get_versioned_release(
    api_urls: &[String],
    write_info: &MessageCallback,
) -> Option<UiLibraryRelease> {
    for api_url in api_urls {
        let result = (|| -> Result<UiLibraryRelease> {
            let version_response: ApiResponse<String> = metadata_client()
                .get(format!("{api_url}/update/ui/ver"))
                .send()
                .context("获取UI版本失败")?
                .error_for_status()
                .context("UI版本响应失败")?
                .json()
                .context("解析UI版本失败")?;
            if version_response.code != 0 {
                write_info("API未返回UI版本信息，尝试下一个API".to_owned());
                bail!("UI版本接口返回错误");
            }
            let version = version_response
                .data
                .ok_or_else(|| anyhow!("UI版本为空"))?
                .trim()
                .to_owned();
            if !valid_version(&version) {
                write_info("API返回了无效的UI版本，尝试下一个API".to_owned());
                bail!("UI版本格式无效");
            }

            let manifest_response: ApiResponse<HashMap<String, String>> = metadata_client()
                .get(format!(
                    "{api_url}/update/ui/libs/{}",
                    urlencoding::encode(&version)
                ))
                .send()
                .context("获取版本化manifest失败")?
                .error_for_status()
                .context("版本化manifest响应失败")?
                .json()
                .context("解析版本化manifest失败")?;
            let manifest = manifest_response
                .data
                .filter(|_| manifest_response.code == 0)
                .filter(is_valid_versioned_manifest)
                .ok_or_else(|| anyhow!("版本化manifest无效"))?;
            Ok(UiLibraryRelease {
                version: Some(version),
                manifest,
            })
        })();

        match result {
            Ok(release) => return Some(release),
            Err(error) => write_info(format!("获取版本化UI库信息失败，尝试下一个API: {error}")),
        }
    }
    None
}

fn get_manifest(
    api_urls: &[String],
    write_info: &MessageCallback,
) -> Option<HashMap<String, String>> {
    for api_url in api_urls {
        let result = (|| -> Result<Option<HashMap<String, String>>> {
            let response: ApiResponse<HashMap<String, String>> = metadata_client()
                .get(format!("{api_url}/update/ui/libs"))
                .send()
                .context("获取UI库信息失败")?
                .error_for_status()
                .context("UI库信息响应失败")?
                .json()
                .context("解析UI库信息失败")?;
            if response.code == 0 {
                Ok(response.data)
            } else {
                write_info("API未返回UI库信息".to_owned());
                Ok(None)
            }
        })();
        match result {
            Ok(Some(manifest)) => return Some(manifest),
            Ok(None) => {}
            Err(error) => write_info(format!("获取UI库信息失败，尝试下一个API: {error}")),
        }
    }
    None
}

fn metadata_client() -> Client {
    Client::with_timeout(Duration::ZERO)
}

fn is_valid_manifest(manifest: &HashMap<String, String>) -> bool {
    !manifest.is_empty()
        && manifest
            .keys()
            .any(|name| name.to_ascii_lowercase().ends_with(".jar"))
        && manifest
            .iter()
            .all(|(name, sha1)| is_safe_file_name(name) && valid_sha1(sha1))
}

fn is_valid_versioned_manifest(manifest: &HashMap<String, String>) -> bool {
    is_valid_manifest(manifest) && manifest.contains_key("rdi-ui.jar")
}

fn is_safe_file_name(name: &str) -> bool {
    !name.is_empty()
        && name != "."
        && name != ".."
        && !name.chars().any(|character| {
            character.is_control()
                || matches!(
                    character,
                    '\\' | '/' | ':' | '*' | '?' | '"' | '<' | '>' | '|'
                )
        })
        && Path::new(name).file_name().and_then(|value| value.to_str()) == Some(name)
}

fn valid_version(version: &str) -> bool {
    let mut bytes = version.bytes();
    matches!(bytes.next(), Some(byte) if byte.is_ascii_alphanumeric())
        && bytes.all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-'))
}

fn valid_sha1(value: &str) -> bool {
    value.len() == 40 && value.bytes().all(|byte| byte.is_ascii_hexdigit())
}

fn is_current(lib_directory: &Path, release: &UiLibraryRelease) -> bool {
    let result = (|| -> Result<bool> {
        if !lib_directory.is_dir() {
            return Ok(false);
        }
        if let Some(target_version) = &release.version {
            if read_installed_version(lib_directory).as_deref() != Some(target_version) {
                return Ok(false);
            }
        }
        let entries = fs::read_dir(lib_directory)?.collect::<std::result::Result<Vec<_>, _>>()?;
        if entries.len() != release.manifest.len() {
            return Ok(false);
        }
        Ok(release
            .manifest
            .iter()
            .all(|(name, expected_sha1)| matches_sha1(&lib_directory.join(name), expected_sha1)))
    })();
    result.unwrap_or(false)
}

fn read_installed_version(lib_directory: &Path) -> Option<String> {
    let result = (|| -> Result<Option<String>> {
        let file = File::open(lib_directory.join("rdi-ui.jar"))?;
        let mut archive = ZipArchive::new(file)?;
        for index in 0..archive.len() {
            let mut entry = archive.by_index(index)?;
            if !entry.name().eq_ignore_ascii_case("META-INF/MANIFEST.MF") {
                continue;
            }
            let mut content = String::new();
            entry.read_to_string(&mut content)?;
            for line in content.lines() {
                let lower = line.to_ascii_lowercase();
                if !lower.starts_with("implementation-version:") {
                    continue;
                }
                let version = line["Implementation-Version:".len()..].trim();
                return Ok(valid_version(version).then(|| version.to_owned()));
            }
        }
        Ok(None)
    })();
    result.ok().flatten()
}

fn validate_staging_version(
    staging_directory: &Path,
    expected_version: Option<&str>,
) -> Result<()> {
    let Some(expected_version) = expected_version else {
        return Ok(());
    };
    let actual_version = read_installed_version(staging_directory);
    if actual_version.as_deref() != Some(expected_version) {
        bail!(
            "暂存UI库版本不匹配，目标为{expected_version}，实际为{}",
            actual_version.as_deref().unwrap_or("未知")
        );
    }
    Ok(())
}

fn matches_sha1(path: &Path, expected_sha1: &str) -> bool {
    if !path.is_file() {
        return false;
    }
    sha1(path)
        .map(|actual| actual.eq_ignore_ascii_case(expected_sha1))
        .unwrap_or(false)
}

fn download_library(
    api_urls: &[String],
    version: Option<&str>,
    name: &str,
    expected_sha1: &str,
    target_path: &Path,
    write_info: &MessageCallback,
    report_progress: ProgressCallback,
) -> Result<bool> {
    for api_url in api_urls {
        let _ = fs::remove_file(target_path);
        let escaped_name = urlencoding::encode(name);
        let download_url = if let Some(version) = version {
            format!(
                "{api_url}/update/ui/lib/{}/{escaped_name}",
                urlencoding::encode(version)
            )
        } else {
            format!("{api_url}/update/ui/lib/{escaped_name}")
        };
        match download::file(
            &download_url,
            target_path,
            8,
            Some(report_progress.clone()),
            Some(write_info.clone()),
        ) {
            Ok(()) => match sha1(target_path) {
                Ok(actual) if actual.eq_ignore_ascii_case(expected_sha1) => return Ok(true),
                Ok(_) => write_info(format!("{name}校验失败，尝试下一个API")),
                Err(error) => write_info(format!("{name}校验失败，尝试下一个API: {error}")),
            },
            Err(error) => write_info(format!("{name}下载失败，尝试下一个API: {error}")),
        }
    }
    let _ = fs::remove_file(target_path);
    Ok(false)
}

fn sha1(path: &Path) -> Result<String> {
    let mut file = File::open(path)?;
    let mut hasher = Sha1::new();
    let mut buffer = [0u8; 128 * 1024];
    loop {
        let bytes_read = file.read(&mut buffer)?;
        if bytes_read == 0 {
            break;
        }
        hasher.update(&buffer[..bytes_read]);
    }
    Ok(hex::encode(hasher.finalize()))
}
