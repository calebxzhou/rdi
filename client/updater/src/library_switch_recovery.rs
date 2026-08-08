use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::sync::Arc;

use anyhow::{Context, Result, bail};

use crate::callbacks::MessageCallback;
use crate::win32::{self, RmProcessInfo};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UiLibraryUpdateResult {
    UpToDate,
    Updated,
    LocalVersionAvailable,
    LaunchAborted,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LibrarySwitchResult {
    Switched,
    LaunchAborted,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LibraryRecoveryPromptKind {
    ForceCloseOccupiers,
    ElevateSwitch,
}

#[derive(Debug, Clone)]
pub struct LibraryRecoveryPrompt {
    pub kind: LibraryRecoveryPromptKind,
    pub occupiers: Vec<LibraryOccupier>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LibraryOccupier {
    pub process_id: u32,
    pub name: String,
    pub start_time: u64,
    pub is_critical: bool,
}

pub type PromptCallback = Arc<dyn Fn(LibraryRecoveryPrompt) -> bool + Send + Sync>;

pub const ELEVATED_KILL_ARGUMENT: &str = "--elevated-kill-occupiers";
pub const ELEVATED_SWITCH_ARGUMENT: &str = "--elevated-switch-lib";

const ERROR_ACCESS_DENIED: i32 = 5;
const ERROR_SHARING_VIOLATION: i32 = 32;
const ERROR_LOCK_VIOLATION: i32 = 33;
const APPLICATION_TYPE_CRITICAL: u32 = 1000;

pub fn switch(
    lib_directory: &Path,
    staging_directory: &Path,
    backup_directory: &Path,
    write_info: MessageCallback,
    write_warning: MessageCallback,
    prompt: Option<PromptCallback>,
) -> Result<LibrarySwitchResult> {
    match switch_library_directory(lib_directory, staging_directory, backup_directory) {
        Ok(()) => Ok(LibrarySwitchResult::Switched),
        Err(error) if is_access_denied(&error) => recover_access_denied(
            lib_directory,
            staging_directory,
            backup_directory,
            write_info,
            write_warning,
            prompt,
        ),
        Err(error) => Err(error),
    }
}

pub fn switch_library_directory(
    lib_directory: &Path,
    staging_directory: &Path,
    backup_directory: &Path,
) -> Result<()> {
    if lib_directory.is_dir() {
        fs::rename(lib_directory, backup_directory).context("备份旧UI库目录失败")?;
    }
    if let Err(error) = fs::rename(staging_directory, lib_directory) {
        if backup_directory.is_dir() && !lib_directory.exists() {
            let _ = fs::rename(backup_directory, lib_directory);
        }
        return Err(error).context("切换新UI库目录失败");
    }
    Ok(())
}

pub fn run_elevated_kill(launcher_root: &Path, target_arguments: &[String]) -> i32 {
    if target_arguments.is_empty() || !target_arguments.len().is_multiple_of(2) {
        return 2;
    }

    let mut targets = Vec::new();
    for chunk in target_arguments.chunks_exact(2) {
        let Ok(process_id) = chunk[0].parse::<u32>() else {
            return 2;
        };
        let Ok(start_time) = chunk[1].parse::<u64>() else {
            return 2;
        };
        if process_id <= 4 || start_time == 0 {
            return 2;
        }
        targets.push((process_id, start_time));
    }

    let lib_directory = launcher_root.join("lib");
    let Ok(occupiers) = try_find_occupiers(&lib_directory) else {
        return 1;
    };
    for (process_id, start_time) in targets.iter().copied() {
        let Some(occupier) = occupiers
            .iter()
            .find(|occupier| occupier.process_id == process_id && occupier.start_time == start_time)
            .cloned()
        else {
            continue;
        };
        if !can_terminate(&occupier) || !try_terminate_process(&occupier) {
            return 1;
        }
    }

    let Ok(occupiers) = try_find_occupiers(&lib_directory) else {
        return 1;
    };
    if targets.iter().any(|(process_id, start_time)| {
        occupiers.iter().any(|occupier| {
            occupier.process_id == *process_id && occupier.start_time == *start_time
        })
    }) {
        1
    } else {
        0
    }
}

pub fn run_elevated_switch(launcher_root: &Path, update_id: &str) -> i32 {
    if !is_compact_uuid(update_id) {
        return 2;
    }
    let lib_directory = launcher_root.join("lib");
    let staging_directory = launcher_root.join(format!("lib.updating-{update_id}"));
    let backup_directory = launcher_root.join(format!("lib.previous-{update_id}"));
    if !staging_directory.is_dir() {
        return 2;
    }
    match switch_library_directory(&lib_directory, &staging_directory, &backup_directory) {
        Ok(()) => 0,
        Err(_) => 1,
    }
}

pub fn find_file_occupiers(file: &Path) -> Result<Vec<LibraryOccupier>> {
    WindowsFileLocks::try_find(&[file.to_owned()])
}

fn recover_access_denied(
    lib_directory: &Path,
    staging_directory: &Path,
    backup_directory: &Path,
    write_info: MessageCallback,
    write_warning: MessageCallback,
    prompt: Option<PromptCallback>,
) -> Result<LibrarySwitchResult> {
    let mut occupiers = match try_find_occupiers(lib_directory) {
        Ok(occupiers) => occupiers,
        Err(error) => {
            write_warning(format!("无法确定占用UI库的程序: {error}"));
            return Ok(LibrarySwitchResult::LaunchAborted);
        }
    };

    while !occupiers.is_empty() {
        let protected_occupiers: Vec<_> = occupiers
            .iter()
            .filter(|occupier| !can_terminate(occupier))
            .cloned()
            .collect();
        if !protected_occupiers.is_empty() {
            write_warning(format!(
                "无法安全终止占用UI库的系统进程: {}",
                format_occupiers(&protected_occupiers)
            ));
            return Ok(LibrarySwitchResult::LaunchAborted);
        }

        write_warning(format!(
            "以下程序正在使用UI库: {}",
            format_occupiers(&occupiers)
        ));
        if !confirm(
            prompt.as_ref(),
            LibraryRecoveryPromptKind::ForceCloseOccupiers,
            occupiers.clone(),
        ) {
            return Ok(LibrarySwitchResult::LaunchAborted);
        }

        write_info("正在请求管理员权限关闭占用程序".to_owned());
        if !try_run_elevated_kill(&occupiers) {
            write_warning("占用UI库的程序无法关闭，本次不再启动客户端".to_owned());
            return Ok(LibrarySwitchResult::LaunchAborted);
        }

        if try_switch(lib_directory, staging_directory, backup_directory)? {
            return Ok(LibrarySwitchResult::Switched);
        }

        occupiers = match try_find_occupiers(lib_directory) {
            Ok(occupiers) => occupiers,
            Err(error) => {
                write_warning(format!("关闭占用程序后仍无法检查UI库占用: {error}"));
                return Ok(LibrarySwitchResult::LaunchAborted);
            }
        };
    }

    write_warning("没有找到文件占用进程，可能是RDI目录权限不足".to_owned());
    if !confirm(
        prompt.as_ref(),
        LibraryRecoveryPromptKind::ElevateSwitch,
        Vec::new(),
    ) {
        return Ok(LibrarySwitchResult::LaunchAborted);
    }
    write_info("正在请求管理员权限完成UI库切换".to_owned());
    if try_run_elevated_switch(staging_directory) {
        Ok(LibrarySwitchResult::Switched)
    } else {
        Ok(LibrarySwitchResult::LaunchAborted)
    }
}

fn try_switch(
    lib_directory: &Path,
    staging_directory: &Path,
    backup_directory: &Path,
) -> Result<bool> {
    match switch_library_directory(lib_directory, staging_directory, backup_directory) {
        Ok(()) => Ok(true),
        Err(error) if is_access_denied(&error) => Ok(false),
        Err(error) => Err(error),
    }
}

fn confirm(
    prompt: Option<&PromptCallback>,
    kind: LibraryRecoveryPromptKind,
    occupiers: Vec<LibraryOccupier>,
) -> bool {
    prompt
        .map(|prompt| prompt(LibraryRecoveryPrompt { kind, occupiers }))
        .unwrap_or(false)
}

fn can_terminate(occupier: &LibraryOccupier) -> bool {
    occupier.process_id > 4
        && occupier.process_id != win32::process_id()
        && occupier.start_time != 0
        && !occupier.is_critical
}

fn try_terminate_process(occupier: &LibraryOccupier) -> bool {
    let Ok(has_exited) = win32::process_has_exited(occupier.process_id) else {
        return false;
    };
    if has_exited {
        return true;
    }
    let Ok(start_time) = win32::process_start_time(occupier.process_id) else {
        return false;
    };
    start_time == occupier.start_time && win32::terminate_process(occupier.process_id)
}

fn try_run_elevated_kill(occupiers: &[LibraryOccupier]) -> bool {
    let mut arguments = vec![ELEVATED_KILL_ARGUMENT.to_owned()];
    for occupier in occupiers {
        arguments.push(occupier.process_id.to_string());
        arguments.push(occupier.start_time.to_string());
    }
    try_run_elevated(&arguments)
}

fn try_run_elevated_switch(staging_directory: &Path) -> bool {
    let Some(name) = staging_directory
        .file_name()
        .and_then(|value| value.to_str())
    else {
        return false;
    };
    let Some(update_id) = name.strip_prefix("lib.updating-") else {
        return false;
    };
    is_compact_uuid(update_id)
        && try_run_elevated(&[ELEVATED_SWITCH_ARGUMENT.to_owned(), update_id.to_owned()])
}

fn try_run_elevated(arguments: &[String]) -> bool {
    let Ok(executable) = std::env::current_exe() else {
        return false;
    };
    win32::run_elevated(&executable, arguments).unwrap_or(false)
}

fn is_access_denied(error: &anyhow::Error) -> bool {
    error
        .chain()
        .find_map(|cause| cause.downcast_ref::<std::io::Error>())
        .and_then(std::io::Error::raw_os_error)
        .map(|code| {
            matches!(
                code,
                ERROR_ACCESS_DENIED | ERROR_SHARING_VIOLATION | ERROR_LOCK_VIOLATION
            )
        })
        .unwrap_or(false)
}

fn try_find_occupiers(lib_directory: &Path) -> Result<Vec<LibraryOccupier>> {
    let files = if lib_directory.is_dir() {
        collect_files(lib_directory)?
    } else {
        Vec::new()
    };
    WindowsFileLocks::try_find(&files)
}

fn collect_files(directory: &Path) -> Result<Vec<std::path::PathBuf>> {
    let mut files = Vec::new();
    let mut stack = vec![directory.to_owned()];
    while let Some(current) = stack.pop() {
        for entry in fs::read_dir(&current)
            .with_context(|| format!("读取目录失败: {}", current.display()))?
        {
            let entry = entry?;
            let path = entry.path();
            let metadata = entry.metadata()?;
            if metadata.is_dir() {
                stack.push(path);
            } else if metadata.is_file() {
                files.push(path);
            }
        }
    }
    Ok(files)
}

fn format_occupiers(occupiers: &[LibraryOccupier]) -> String {
    occupiers
        .iter()
        .map(|occupier| format!("{}(PID{})", occupier.name, occupier.process_id))
        .collect::<Vec<_>>()
        .join("、")
}

fn is_compact_uuid(value: &str) -> bool {
    value.len() == 32 && value.chars().all(|character| character.is_ascii_hexdigit())
}

struct WindowsFileLocks;

impl WindowsFileLocks {
    fn try_find(files: &[std::path::PathBuf]) -> Result<Vec<LibraryOccupier>> {
        if files.is_empty() {
            return Ok(Vec::new());
        }

        let mut session_handle = 0;
        let mut session_key = [0u16; 64];
        let status =
            unsafe { win32::RmStartSession(&mut session_handle, 0, session_key.as_mut_ptr()) };
        if status != 0 {
            bail!("Restart Manager启动失败，错误码{status}");
        }

        let result = (|| -> Result<Vec<LibraryOccupier>> {
            let file_strings: Vec<Vec<u16>> =
                files.iter().map(|file| win32::wide_path(file)).collect();
            let file_pointers: Vec<*const u16> =
                file_strings.iter().map(|file| file.as_ptr()).collect();
            let status = unsafe {
                win32::RmRegisterResources(
                    session_handle,
                    file_pointers.len() as u32,
                    file_pointers.as_ptr(),
                    0,
                    std::ptr::null_mut(),
                    0,
                    std::ptr::null_mut(),
                )
            };
            if status != 0 {
                bail!("注册被占用文件失败，错误码{status}");
            }

            let mut process_needed = 0;
            let mut process_count = 0;
            let mut reboot_reasons = 0;
            let status = unsafe {
                win32::RmGetList(
                    session_handle,
                    &mut process_needed,
                    &mut process_count,
                    std::ptr::null_mut(),
                    &mut reboot_reasons,
                )
            };
            if status != 0 && status != win32::ERROR_MORE_DATA {
                bail!("读取文件占用进程失败，错误码{status}");
            }
            if process_needed == 0 {
                return Ok(Vec::new());
            }

            let mut process_info = vec![RmProcessInfo::default(); process_needed as usize];
            process_count = process_needed;
            reboot_reasons = 0;
            let status = unsafe {
                win32::RmGetList(
                    session_handle,
                    &mut process_needed,
                    &mut process_count,
                    process_info.as_mut_ptr(),
                    &mut reboot_reasons,
                )
            };
            if status != 0 {
                bail!("读取文件占用进程失败，错误码{status}");
            }

            let mut result = Vec::new();
            let mut seen_processes = HashSet::new();
            for info in process_info.into_iter().take(process_count as usize) {
                let process_id = info.process.process_id;
                if !seen_processes.insert(process_id) {
                    continue;
                }
                result.push(LibraryOccupier {
                    process_id,
                    name: win32::from_wide(&info.app_name),
                    start_time: win32::file_time_value(info.process.process_start_time),
                    is_critical: info.application_type == APPLICATION_TYPE_CRITICAL,
                });
            }
            Ok(result)
        })();
        unsafe {
            win32::RmEndSession(session_handle);
        }
        result
    }
}
