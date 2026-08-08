#[cfg(windows)]
mod callbacks;
#[cfg(windows)]
mod download;
#[cfg(windows)]
mod http;
#[cfg(windows)]
mod library_switch_recovery;
#[cfg(windows)]
mod ntfs_mft_enum;
#[cfg(windows)]
mod ui_library_updater;
#[cfg(windows)]
mod win32;

#[cfg(windows)]
use std::collections::{HashMap, HashSet};
#[cfg(windows)]
use std::fs::{self, OpenOptions};
#[cfg(windows)]
use std::io::{BufRead, BufReader, Read, Write};
#[cfg(windows)]
use std::net::Ipv4Addr;
#[cfg(windows)]
use std::os::windows::process::CommandExt;
#[cfg(windows)]
use std::path::{Path, PathBuf};
#[cfg(windows)]
use std::process::{Command, Stdio};
#[cfg(windows)]
use std::sync::Arc;
#[cfg(windows)]
use std::thread;
#[cfg(windows)]
use std::time::{Duration, Instant};

#[cfg(windows)]
use anyhow::{Context, Result, anyhow, bail};
#[cfg(windows)]
use chrono::Local;
#[cfg(windows)]
use crossterm::event::{self, Event, KeyCode, KeyEvent, KeyEventKind};
#[cfg(windows)]
use crossterm::terminal::{disable_raw_mode, enable_raw_mode};
#[cfg(windows)]
use rayon::prelude::*;

#[cfg(windows)]
use callbacks::MessageCallback;
#[cfg(windows)]
use download::{FileDownloadProgress, ProgressCallback};
#[cfg(windows)]
use library_switch_recovery::{
    LibraryRecoveryPrompt, LibraryRecoveryPromptKind, PromptCallback, UiLibraryUpdateResult,
};

#[cfg(windows)]
const MFT_SEARCH_ARGUMENT: &str = "--mft-search";
#[cfg(windows)]
const UNINSTALL_CONFIRMATION: &str = "confirm";
#[cfg(windows)]
const JVM_ARGUMENT_PREFIX: &str = "--jvmArg=";
#[cfg(windows)]
const MFT_LOG_PREFIX: &str = "LOG\t";
#[cfg(windows)]
const MFT_PATH_PREFIX: &str = "PATH\t";
#[cfg(windows)]
const MAX_PARALLEL_JDK_CHECKS: usize = 8;
#[cfg(windows)]
const DEBUG_R_SERVER_URL: &str = "http://127.0.0.1:65231";
#[cfg(windows)]
const OFFICIAL_R_SERVER_URL: &str = "https://rdi.calebxzhou.cn:65331";
#[cfg(windows)]
const JDK25_DOWNLOAD_URL: &str = "https://mirrors.huaweicloud.com/eclipse/temurin-compliance/temurin/25/jdk-25.0.3%2B9/OpenJDK25U-jdk_x64_windows_hotspot_25.0.3_9.msi";

#[cfg(windows)]
fn main() {
    std::process::exit(run(std::env::args().skip(1).collect()));
}

#[cfg(not(windows))]
fn main() {
    eprintln!("RDI updater仅支持Windows");
    std::process::exit(1);
}

#[cfg(windows)]
fn run(args: Vec<String>) -> i32 {
    match run_inner(args) {
        Ok(exit_code) => exit_code,
        Err(error) => fail(&format!("启动失败。\r\n错误: {error}")),
    }
}

#[cfg(windows)]
fn run_inner(args: Vec<String>) -> Result<i32> {
    let launcher_root = std::env::current_exe()?
        .parent()
        .ok_or_else(|| anyhow!("无法确定updater目录"))?
        .to_path_buf();
    if args.first().map(String::as_str) == Some(MFT_SEARCH_ARGUMENT) && args.len() == 2 {
        return Ok(run_elevated_mft_search(&args[1]));
    }
    if args.first().map(String::as_str) == Some(library_switch_recovery::ELEVATED_KILL_ARGUMENT) {
        return Ok(library_switch_recovery::run_elevated_kill(
            &launcher_root,
            &args[1..],
        ));
    }
    if args.first().map(String::as_str) == Some(library_switch_recovery::ELEVATED_SWITCH_ARGUMENT)
        && args.len() == 2
    {
        return Ok(library_switch_recovery::run_elevated_switch(
            &launcher_root,
            &args[1],
        ));
    }

    std::env::set_current_dir(&launcher_root)?;
    let mutex_name = updater_mutex_name(&launcher_root);
    let (_mutex, owns_mutex) = win32::mutex(&mutex_name)?;
    if !owns_mutex {
        return Ok(fail("另一个启动程序正在更新，请稍后重试。"));
    }

    let launch_options = parse_launch_options(&args)?;
    let startup_selection = if win32::is_left_shift_pressed() {
        show_startup_options(&launcher_root)?
    } else {
        StartupSelection::default()
    };
    if startup_selection.uninstall {
        return Ok(start_uninstall(&launcher_root));
    }

    let launch_options = LaunchOptions {
        debug: launch_options.debug || startup_selection.debug,
        no_update: launch_options.no_update || startup_selection.no_update,
        ..launch_options
    };
    let r_server_url = if launch_options.debug {
        DEBUG_R_SERVER_URL
    } else {
        OFFICIAL_R_SERVER_URL
    };

    if launch_options.no_update {
        write_info("已关闭自动更新");
    } else {
        let player_ipv4 = if launch_options.debug {
            None
        } else {
            get_public_ipv4()
        };
        let update_result = ui_library_updater::try_update(
            r_server_url,
            &launcher_root,
            info_callback(),
            warning_callback(),
            progress_callback(),
            !launch_options.debug,
            player_ipv4.as_deref(),
            Some(Arc::new(prompt_library_recovery) as PromptCallback),
        );
        if update_result == UiLibraryUpdateResult::LaunchAborted {
            return Ok(0);
        }
    }

    let lib_directory = launcher_root.join("lib");
    let has_jar = lib_directory.is_dir()
        && fs::read_dir(&lib_directory)
            .map(|entries| {
                entries.flatten().any(|entry| {
                    entry.path().extension().is_some_and(|extension| {
                        extension.to_string_lossy().eq_ignore_ascii_case("jar")
                    })
                })
            })
            .unwrap_or(false);
    if !has_jar {
        return Ok(fail(
            "缺少UI库文件。\r\n请确认客户端已完整解压，或检查网络后重试。",
        ));
    }

    let best_java = match startup_selection.jdk {
        Some(java) => java,
        None => match resolve_best_jdk25(&launcher_root) {
            Some(java) => java,
            None => return Ok(fail("未找到可用Java25，客户端无法启动。")),
        },
    };
    let java_name = if launch_options.app_logs {
        "java.exe"
    } else {
        "javaw.exe"
    };
    let java_exe = best_java.java_home.join("bin").join(java_name);
    if !java_exe.is_file() {
        return Ok(fail(&format!(
            "找到的Java25缺少{java_name}：\r\n{}",
            best_java.java_home.display()
        )));
    }

    Ok(start_client(
        &launcher_root,
        &java_exe,
        &launch_options,
        startup_selection.solid_window,
        r_server_url,
    ))
}

#[cfg(windows)]
fn write_info(message: &str) {
    println!("[{}] {message}", Local::now().format("%H:%M:%S"));
}

#[cfg(windows)]
fn write_warning(message: &str) {
    write_info(&format!("警告: {message}"));
}

#[cfg(windows)]
fn info_callback() -> MessageCallback {
    Arc::new(|message| write_info(&message))
}

#[cfg(windows)]
fn warning_callback() -> MessageCallback {
    Arc::new(|message| write_warning(&message))
}

#[cfg(windows)]
fn progress_callback() -> ProgressCallback {
    Arc::new(render_download_progress)
}

#[cfg(windows)]
fn render_download_progress(progress: FileDownloadProgress) {
    let progress_text = if let Some(total) = progress.total_bytes {
        format!(
            "下载进度: {:>6.2}%  {}/{}  {}/s",
            progress.downloaded_bytes as f64 * 100.0 / total as f64,
            format_bytes(progress.downloaded_bytes as f64),
            format_bytes(total as f64),
            format_bytes(progress.bytes_per_second),
        )
    } else {
        format!(
            "下载进度: {}  {}/s",
            format_bytes(progress.downloaded_bytes as f64),
            format_bytes(progress.bytes_per_second),
        )
    };
    print!("\r{progress_text:<80}");
    if progress.completed {
        println!();
    }
    let _ = std::io::stdout().flush();
}

#[cfg(windows)]
fn format_bytes(bytes: f64) -> String {
    if bytes >= 1024.0 * 1024.0 * 1024.0 {
        format!("{:.2}GB", bytes / 1024.0 / 1024.0 / 1024.0)
    } else if bytes >= 1024.0 * 1024.0 {
        format!("{:.2}MB", bytes / 1024.0 / 1024.0)
    } else if bytes >= 1024.0 {
        format!("{:.2}KB", bytes / 1024.0)
    } else {
        format!("{bytes:.0}B")
    }
}

#[cfg(windows)]
fn get_public_ipv4() -> Option<String> {
    let result = (|| -> Result<String> {
        let client = http::Client::with_timeout(Duration::from_secs(5));
        let text = client
            .get("https://ip.3322.net/")
            .send()?
            .error_for_status()?
            .text()?
            .trim()
            .to_owned();
        text.parse::<Ipv4Addr>()
            .map(|_| text)
            .context("服务未返回有效IPv4")
    })();
    match result {
        Ok(ipv4) => Some(ipv4),
        Err(error) => {
            write_warning(&format!("获取IPv4失败，将继续尝试更新: {error}"));
            None
        }
    }
}

#[cfg(windows)]
fn fail(message: &str) -> i32 {
    show_start_error(message);
    println!();
    print!("按回车键关闭窗口");
    let _ = std::io::stdout().flush();
    let mut ignored = String::new();
    let _ = std::io::stdin().read_line(&mut ignored);
    1
}

#[cfg(windows)]
fn show_start_error(message: &str) {
    win32::message_box(message, "RDI启动失败", win32::MB_ICONERROR);
    eprintln!("{message}");
}

#[cfg(windows)]
fn updater_mutex_name(launcher_root: &Path) -> String {
    use sha2::{Digest, Sha256};
    let mut hasher = Sha256::new();
    hasher.update(launcher_root.to_string_lossy().to_uppercase().as_bytes());
    let hash = hex::encode_upper(hasher.finalize());
    format!("Local\\RDI-Updater-{}", &hash[..16])
}

#[cfg(windows)]
fn prompt_library_recovery(prompt: LibraryRecoveryPrompt) -> bool {
    let occupiers = if prompt.occupiers.is_empty() {
        String::new()
    } else {
        format!(
            "\r\n\r\n占用程序: {}",
            prompt
                .occupiers
                .iter()
                .map(|occupier| format!("{}(PID{})", occupier.name, occupier.process_id))
                .collect::<Vec<_>>()
                .join("、")
        )
    };
    let (message, title) = match prompt.kind {
        LibraryRecoveryPromptKind::ForceCloseOccupiers => (
            format!(
                "以下程序占用RDI的UI库，无法更新，要使用管理员权限强制关闭再更新吗？{occupiers}"
            ),
            "UI库被占用",
        ),
        LibraryRecoveryPromptKind::ElevateSwitch => (
            "没有找到占用程序，但RDI目录权限不足。是否请求管理员权限完成更新？".to_owned(),
            "需要管理员权限",
        ),
    };
    win32::message_box(&message, title, win32::MB_YESNO | win32::MB_ICONQUESTION) == win32::IDYES
}

#[cfg(windows)]
#[derive(Clone, Default)]
struct LaunchOptions {
    debug: bool,
    app_logs: bool,
    no_update: bool,
    jvm_arguments: Vec<String>,
}

#[cfg(windows)]
#[derive(Clone, Default)]
struct StartupSelection {
    jdk: Option<JdkCandidate>,
    solid_window: bool,
    debug: bool,
    no_update: bool,
    uninstall: bool,
}

#[cfg(windows)]
fn parse_launch_options(args: &[String]) -> Result<LaunchOptions> {
    let mut options = LaunchOptions::default();
    for argument in args {
        if argument.eq_ignore_ascii_case("--debug") {
            options.debug = true;
        } else if argument.eq_ignore_ascii_case("--app-logs") {
            options.app_logs = true;
        } else if argument.eq_ignore_ascii_case("--no-update") {
            options.no_update = true;
        } else if argument.len() >= JVM_ARGUMENT_PREFIX.len()
            && argument[..JVM_ARGUMENT_PREFIX.len()].eq_ignore_ascii_case(JVM_ARGUMENT_PREFIX)
        {
            let value = &argument[JVM_ARGUMENT_PREFIX.len()..];
            options.jvm_arguments.extend(parse_jvm_arguments(value)?);
        } else {
            bail!("未知启动参数: {argument}");
        }
    }
    Ok(options)
}

#[cfg(windows)]
fn show_startup_options(launcher_root: &Path) -> Result<StartupSelection> {
    let can_uninstall = launcher_root.join("lib").join("rdi-ui.jar").is_file();
    let mut options = vec![
        "重新手动选择Java25".to_owned(),
        "本次以实心窗口启动".to_owned(),
        "launch w/o upd".to_owned(),
        "launch w/ dbg".to_owned(),
    ];
    if can_uninstall {
        options.push("卸载".to_owned());
    }
    let mut selected_index = 0;
    let mut arrow_key_held = false;

    loop {
        clear_console();
        println!("启动选项（使用↑/↓选择，按Enter确认）");
        println!("按Esc键正常启动，不要选择你看不懂的选项");
        println!();
        for (index, option) in options.iter().enumerate() {
            println!(
                "{} {}.{}",
                if index == selected_index { ">" } else { " " },
                index + 1,
                option
            );
        }

        let Some(key) = read_menu_event()? else {
            continue;
        };
        if key.kind == KeyEventKind::Release {
            if matches!(key.code, KeyCode::Up | KeyCode::Down) {
                arrow_key_held = false;
            }
            continue;
        }
        if key.kind != KeyEventKind::Press {
            continue;
        }
        match key.code {
            KeyCode::Up if !arrow_key_held => {
                arrow_key_held = true;
                selected_index = (selected_index + options.len() - 1) % options.len();
            }
            KeyCode::Down if !arrow_key_held => {
                arrow_key_held = true;
                selected_index = (selected_index + 1) % options.len();
            }
            KeyCode::Esc => {
                clear_console();
                return Ok(StartupSelection::default());
            }
            KeyCode::Enter => {
                if can_uninstall && selected_index == options.len() - 1 {
                    if confirm_uninstall()? {
                        clear_console();
                        return Ok(StartupSelection {
                            uninstall: true,
                            ..StartupSelection::default()
                        });
                    }
                    continue;
                }
                clear_console();
                return match selected_index {
                    0 => match select_manual_jdk25(launcher_root)? {
                        ManualJdkSelectionResult::Selected(jdk) => Ok(StartupSelection {
                            jdk: Some(jdk),
                            ..StartupSelection::default()
                        }),
                        ManualJdkSelectionResult::AutoSearch | ManualJdkSelectionResult::Failed => {
                            Ok(StartupSelection::default())
                        }
                    },
                    1 => Ok(StartupSelection {
                        solid_window: true,
                        ..StartupSelection::default()
                    }),
                    2 => Ok(StartupSelection {
                        no_update: true,
                        ..StartupSelection::default()
                    }),
                    _ => Ok(StartupSelection {
                        debug: true,
                        ..StartupSelection::default()
                    }),
                };
            }
            _ => {}
        }
    }
}

#[cfg(windows)]
fn clear_console() {
    print!("\x1b[2J\x1b[H");
    let _ = std::io::stdout().flush();
}

#[cfg(windows)]
fn read_menu_event() -> Result<Option<KeyEvent>> {
    enable_raw_mode().context("启用启动菜单输入失败")?;
    let event = event::read().context("读取启动菜单输入失败");
    let _ = disable_raw_mode();
    match event? {
        Event::Key(key) => Ok(Some(key)),
        _ => Ok(None),
    }
}

#[cfg(windows)]
fn read_menu_key() -> Result<KeyCode> {
    Ok(read_menu_event()?.map_or(KeyCode::Null, |key| key.code))
}

#[cfg(windows)]
fn confirm_uninstall() -> Result<bool> {
    clear_console();
    println!("卸载会把当前RDI目录下的文件和子目录，以及本地数据移入回收站。\r\n");
    print!("请输入{UNINSTALL_CONFIRMATION}并按回车开始卸载: ");
    std::io::stdout().flush()?;
    let mut confirmation = String::new();
    std::io::stdin().read_line(&mut confirmation)?;
    let confirmed = confirmation.trim_end_matches(&['\r', '\n'][..]) == UNINSTALL_CONFIRMATION;
    if !confirmed {
        println!("确认文本不正确，已取消卸载。");
    }
    Ok(confirmed)
}

#[cfg(windows)]
fn start_uninstall(launcher_root: &Path) -> i32 {
    let result = (|| -> Result<()> {
        let local_app_data =
            std::env::var_os("LOCALAPPDATA").ok_or_else(|| anyhow!("无法确定本地应用数据目录"))?;
        let rdi_ui_jar = launcher_root.join("lib").join("rdi-ui.jar");
        if rdi_ui_jar.is_file() {
            let occupiers = library_switch_recovery::find_file_occupiers(&rdi_ui_jar)
                .context("检查lib/rdi-ui.jar占用失败")?;
            if !occupiers.is_empty() {
                let occupier_names = occupiers
                    .iter()
                    .map(|occupier| format!("{}(PID{})", occupier.name, occupier.process_id))
                    .collect::<Vec<_>>()
                    .join("、");
                bail!(
                    "无法继续卸载，lib/rdi-ui.jar正在被以下程序使用：{occupier_names}。\r\n请先终止占用程序，然后重新选择卸载。"
                );
            }
        }

        let current_exe = std::env::current_exe().ok();
        let mut skipped = false;
        let entries = fs::read_dir(launcher_root).context("读取当前RDI目录内容失败")?;
        for entry in entries {
            let path = match entry {
                Ok(entry) => entry.path(),
                Err(error) => {
                    skipped = true;
                    write_info(&format!("跳过无法读取的目录项，错误: {error}"));
                    continue;
                }
            };
            if current_exe.as_deref() == Some(path.as_path()) {
                write_info(&format!("保留正在运行的Updater: {}", path.display()));
                continue;
            }
            write_info(&format!("正在移入回收站: {}", path.display()));
            if let Err(error) = win32::move_to_recycle_bin(&path) {
                skipped = true;
                write_info(&format!(
                    "跳过无法移入回收站: {}，错误: {error}",
                    path.display()
                ));
            }
        }

        let local_rdi = PathBuf::from(local_app_data).join(".rdi");
        if local_rdi.exists() {
            write_info(&format!("正在移入回收站: {}", local_rdi.display()));
            if let Err(error) = win32::move_to_recycle_bin(&local_rdi) {
                skipped = true;
                write_info(&format!(
                    "跳过无法移入回收站: {}，错误: {error}",
                    local_rdi.display()
                ));
            }
        }
        if skipped {
            write_info("卸载完成，但部分目录未能移入回收站。");
        } else {
            write_info(
                "已确认卸载，当前目录下的可删除内容和本地数据已移入回收站，当前目录本身已保留。",
            );
        }
        Ok(())
    })();
    match result {
        Ok(()) => 0,
        Err(error) => fail(&format!("卸载失败。\r\n错误: {error}")),
    }
}

#[cfg(windows)]
fn parse_jvm_arguments(arguments: &str) -> Result<Vec<String>> {
    if arguments.trim().is_empty() {
        Ok(Vec::new())
    } else {
        win32::parse_windows_command_line(arguments)
    }
}

#[cfg(windows)]
fn resolve_best_jdk25(launcher_root: &Path) -> Option<JdkCandidate> {
    let mut allow_mft_search = true;
    loop {
        match find_best_jdk25(launcher_root, allow_mft_search) {
            JdkSearchResult::Found(best_java) => return Some(best_java),
            JdkSearchResult::AdministratorPermissionDenied => {
                allow_mft_search = false;
                match select_manual_jdk25(launcher_root) {
                    Ok(ManualJdkSelectionResult::Selected(manual_java)) => {
                        return Some(manual_java);
                    }
                    Ok(ManualJdkSelectionResult::AutoSearch) => continue,
                    Ok(ManualJdkSelectionResult::Failed) => {
                        write_info("未选择可用Java25，返回Java选项");
                    }
                    Err(error) => write_info(&format!("手动选择Java25失败: {error}")),
                }
            }
            JdkSearchResult::NotFound => {}
        }
        show_no_java_options(allow_mft_search);
        match read_simple_key() {
            Ok(KeyCode::Char(' ')) => {
                if install_jdk25() {
                    write_info("将重新搜索Java25");
                }
            }
            Ok(KeyCode::Enter) => match select_manual_jdk25(launcher_root) {
                Ok(ManualJdkSelectionResult::Selected(manual_java)) => {
                    return Some(manual_java);
                }
                Ok(ManualJdkSelectionResult::AutoSearch) => continue,
                Ok(ManualJdkSelectionResult::Failed) => {
                    write_info("手动选择未得到可用Java25，返回选择菜单");
                }
                Err(error) => write_info(&format!("手动选择Java25失败: {error}")),
            },
            Ok(_) if allow_mft_search => write_info("用户选择重新授权并搜索Java25"),
            Ok(_) => write_info("跳过管理员搜索，重新扫描Java25"),
            Err(error) => write_info(&format!("读取选择失败: {error}")),
        }
    }
}

#[cfg(windows)]
fn read_simple_key() -> Result<KeyCode> {
    read_menu_key()
}

#[cfg(windows)]
fn show_no_java_options(allow_mft_search: bool) {
    println!();
    println!("没找到完整版Java25");
    println!("按空格键下载并安装Java25，按回车键手动选择Java安装目录，随便按一个键重新搜索");
    if allow_mft_search {
        println!("重新搜索可能会再次请求管理员权限");
    } else {
        println!("已跳过管理员搜索，后续搜索不会再次请求管理员权限");
    }
}

#[cfg(windows)]
fn install_jdk25() -> bool {
    let result = (|| -> Result<()> {
        let installer_path = std::env::current_dir()?.join("java25install.msi");
        write_info("正在下载Java25安装程序");
        download::file(
            JDK25_DOWNLOAD_URL,
            &installer_path,
            8,
            Some(progress_callback()),
            Some(info_callback()),
        )?;
        write_info("下载完成，正在启动Java25安装程序");
        let status = Command::new("msiexec.exe")
            .arg("/i")
            .arg(installer_path.to_string_lossy().as_ref())
            .arg("/norestart")
            .status()
            .context("无法启动Java25安装程序")?;
        if !matches!(status.code(), Some(0) | Some(3010)) {
            bail!("安装程序退出码: {:?}", status.code());
        }
        write_info("Java25安装完成");
        Ok(())
    })();
    match result {
        Ok(()) => true,
        Err(error) => {
            show_start_error(&format!("Java25下载安装失败。\r\n错误: {error}"));
            false
        }
    }
}

#[cfg(windows)]
enum ManualJdkCancelChoice {
    Download,
    AutoSearch,
    Manual,
}

#[cfg(windows)]
enum ManualJdkSelectionResult {
    Selected(JdkCandidate),
    AutoSearch,
    Failed,
}

#[cfg(windows)]
fn prompt_manual_jdk_cancel() -> Result<ManualJdkCancelChoice> {
    println!();
    println!("未选择Java25安装目录");
    println!("按Y下载Java25安装程序，按R自动搜索，按M重新手动选择");
    loop {
        match read_simple_key()? {
            KeyCode::Char('y') | KeyCode::Char('Y') => {
                return Ok(ManualJdkCancelChoice::Download);
            }
            KeyCode::Char('r') | KeyCode::Char('R') => {
                return Ok(ManualJdkCancelChoice::AutoSearch);
            }
            KeyCode::Char('m') | KeyCode::Char('M') => {
                return Ok(ManualJdkCancelChoice::Manual);
            }
            _ => {}
        }
    }
}

#[cfg(windows)]
fn select_manual_jdk25(launcher_root: &Path) -> Result<ManualJdkSelectionResult> {
    loop {
        write_info("请手动选择Java25安装目录");
        let Some(selected_path) = win32::pick_folder_path("选择Java25安装目录")? else {
            write_info("未选择目录");
            match prompt_manual_jdk_cancel()? {
                ManualJdkCancelChoice::Download => {
                    install_jdk25();
                    return Ok(ManualJdkSelectionResult::AutoSearch);
                }
                ManualJdkCancelChoice::AutoSearch => {
                    return Ok(ManualJdkSelectionResult::AutoSearch);
                }
                ManualJdkCancelChoice::Manual => continue,
            }
        };
        write_info(&format!("已选择目录: {selected_path}"));
        let mut candidates = HashMap::new();
        add_jdk_path(&mut candidates, Path::new(&selected_path));
        let valid_candidates = find_valid_jdk25_candidates(&candidates, "手动选择");
        if valid_candidates.is_empty() {
            show_start_error(&format!(
                "所选目录不是可用的64位Java25：\r\n{selected_path}"
            ));
            return Ok(ManualJdkSelectionResult::Failed);
        }
        write_cached_jdk_list(launcher_root, &valid_candidates)?;
        print_available_jdk_list(&valid_candidates, "手动选择");
        return Ok(select_best_jdk25_candidate(valid_candidates)
            .map(ManualJdkSelectionResult::Selected)
            .unwrap_or(ManualJdkSelectionResult::Failed));
    }
}

#[cfg(windows)]
enum JdkSearchResult {
    Found(JdkCandidate),
    AdministratorPermissionDenied,
    NotFound,
}

#[cfg(windows)]
fn find_best_jdk25(launcher_root: &Path, allow_mft_search: bool) -> JdkSearchResult {
    write_info("开始搜索Java25");
    if let Some(cached_java_homes) = read_cached_jdk_list(launcher_root) {
        if !cached_java_homes.is_empty() {
            write_info("优先使用Java缓存列表");
            let mut cached_candidates = HashMap::new();
            for java_home in cached_java_homes {
                add_jdk_path(&mut cached_candidates, Path::new(&java_home));
            }
            if let Some(cached_java) =
                find_first_valid_jdk25_candidate(&cached_candidates, "缓存校验")
            {
                write_info("缓存中的Java25仍然可用，跳过其它缓存项校验");
                return JdkSearchResult::Found(cached_java);
            }
            write_info("缓存中的Java25已失效，开始重新搜索");
        }
    }

    if allow_mft_search {
        let mft_search = find_javac_executables_with_administrator_privilege();
        if mft_search.cancelled {
            return JdkSearchResult::AdministratorPermissionDenied;
        }
        if !mft_search.succeeded {
            return JdkSearchResult::NotFound;
        }
        let mut mft_candidates = HashMap::new();
        for path in mft_search.candidates {
            add_jdk_path(&mut mft_candidates, Path::new(&path));
        }
        if let Some(mft_java) = find_first_valid_jdk25_candidate(&mft_candidates, "MFT搜索") {
            let candidates = vec![mft_java.clone()];
            let _ = write_cached_jdk_list(launcher_root, &candidates);
            print_available_jdk_list(&candidates, "MFT搜索");
            return JdkSearchResult::Found(mft_java);
        }
    }

    let drive_roots = win32::get_drive_roots();
    write_info(&format!(
        "快速搜索未找到可用Java25，开始目录搜索，共{}个磁盘",
        drive_roots.len()
    ));
    let directory_candidates = search_jdk25_candidates(&drive_roots, "搜索Java");
    let valid_candidates = find_valid_jdk25_candidates(&directory_candidates, "最终校验");
    if valid_candidates.is_empty() {
        let _ = fs::remove_file(launcher_root.join("available_jdks.txt"));
        return JdkSearchResult::NotFound;
    }
    let _ = write_cached_jdk_list(launcher_root, &valid_candidates);
    print_available_jdk_list(&valid_candidates, "重新搜索");
    select_best_jdk25_candidate(valid_candidates)
        .map(JdkSearchResult::Found)
        .unwrap_or(JdkSearchResult::NotFound)
}

#[cfg(windows)]
fn read_cached_jdk_list(launcher_root: &Path) -> Option<Vec<String>> {
    let cache_file = launcher_root.join("available_jdks.txt");
    if !cache_file.is_file() {
        write_info("未发现Java缓存文件avaliable_jdks.txt");
        return Some(Vec::new());
    }
    let content = fs::read_to_string(&cache_file).ok()?;
    let has_utf8_bom = content.starts_with('\u{feff}');
    let content = content.strip_prefix('\u{feff}').unwrap_or(&content);
    let mut seen = HashSet::new();
    let values = content
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .filter(|line| seen.insert(line.to_ascii_lowercase()))
        .map(ToOwned::to_owned)
        .collect::<Vec<_>>();
    if has_utf8_bom {
        let normalized_content = if values.is_empty() {
            String::new()
        } else {
            values.join("\n") + "\n"
        };
        match fs::write(&cache_file, normalized_content) {
            Ok(()) => write_info("已将Java缓存转换为无BOMUTF-8"),
            Err(error) => write_info(&format!("读取Java缓存成功，但移除BOM失败: {error}")),
        }
    }
    write_info(&format!("读取Java缓存成功，共{}条", values.len()));
    Some(values)
}

#[cfg(windows)]
fn write_cached_jdk_list(launcher_root: &Path, candidates: &[JdkCandidate]) -> Result<()> {
    let mut java_homes = candidates
        .iter()
        .map(|candidate| candidate.java_home.to_string_lossy().to_string())
        .collect::<Vec<_>>();
    java_homes.sort_by_key(|value| value.to_ascii_lowercase());
    java_homes.dedup_by_key(|value| value.to_ascii_lowercase());
    fs::write(
        launcher_root.join("available_jdks.txt"),
        java_homes.join("\n") + "\n",
    )?;
    write_info(&format!(
        "已写入Java缓存到avaliable_jdks.txt，共{}条",
        java_homes.len()
    ));
    Ok(())
}

#[cfg(windows)]
fn search_jdk25_candidates(roots: &[String], stage_name: &str) -> HashMap<String, String> {
    let mut candidates = HashMap::new();
    let mut seen_directories = HashSet::new();
    for (index, candidate_root) in roots.iter().enumerate() {
        let root = PathBuf::from(candidate_root);
        if !root.is_dir() {
            continue;
        }
        write_info(&format!(
            "搜索阶段[{stage_name}] {}/{}: {}",
            index + 1,
            roots.len(),
            root.display()
        ));
        add_jdk_path(&mut candidates, &root);
        let force_deep = candidate_root.to_ascii_lowercase().contains(".jdks")
            || candidate_root.to_ascii_lowercase().contains(".sdkman");
        let mut stack = vec![SearchDirectory {
            path: root,
            force_deep,
            depth: 0,
        }];
        while let Some(current) = stack.pop() {
            let key = current.path.to_string_lossy().to_ascii_lowercase();
            if !seen_directories.insert(key) || !current.path.is_dir() {
                continue;
            }
            let Ok(directory_entries) = fs::read_dir(&current.path) else {
                continue;
            };
            for entry in directory_entries.flatten() {
                let path = entry.path();
                let Ok(metadata) = fs::symlink_metadata(&path) else {
                    continue;
                };
                if metadata.file_type().is_symlink() || !metadata.is_dir() {
                    continue;
                }
                let name = path
                    .file_name()
                    .and_then(|value| value.to_str())
                    .unwrap_or_default()
                    .to_ascii_lowercase();
                let interesting = current.force_deep
                    || name == "bin"
                    || is_numeric_directory_name(&name)
                    || search_keywords()
                        .iter()
                        .any(|keyword| name.contains(keyword));
                if !interesting {
                    continue;
                }
                add_jdk_path(&mut candidates, &path);
                let next_depth = current.depth + 1;
                if current.force_deep || next_depth < 4 {
                    let force_deep = current.force_deep
                        || matches!(
                            name.as_str(),
                            "java" | "jdk" | "jre" | "runtime" | "jbr" | "bin"
                        );
                    stack.push(SearchDirectory {
                        path,
                        force_deep,
                        depth: next_depth,
                    });
                }
            }
        }
    }
    candidates
}

#[cfg(windows)]
fn add_jdk_path(candidates: &mut HashMap<String, String>, path: &Path) {
    let normalized = normalize_path(path);
    if normalized.is_dir() {
        for javac_executable in [
            normalized.join("javac.exe"),
            normalized.join("bin").join("javac.exe"),
        ] {
            if javac_executable.is_file() {
                insert_java_candidate(candidates, javac_executable);
            }
        }
    } else if normalized.is_file()
        && normalized
            .file_name()
            .is_some_and(|name| name.to_string_lossy().eq_ignore_ascii_case("javac.exe"))
    {
        insert_java_candidate(candidates, normalized);
    }
}

#[cfg(windows)]
fn insert_java_candidate(candidates: &mut HashMap<String, String>, javac_executable: PathBuf) {
    let Some(java_executable) = javac_executable
        .parent()
        .map(|parent| parent.join("java.exe"))
    else {
        return;
    };
    insert_candidate(candidates, java_executable);
}

#[cfg(windows)]
fn insert_candidate(candidates: &mut HashMap<String, String>, path: PathBuf) {
    let key = path.to_string_lossy().to_ascii_lowercase();
    candidates
        .entry(key)
        .or_insert(path.to_string_lossy().to_string());
}

#[cfg(windows)]
fn find_valid_jdk25_candidates(
    candidates: &HashMap<String, String>,
    stage_name: &str,
) -> Vec<JdkCandidate> {
    let values = candidates.values().cloned().collect::<Vec<_>>();
    let valid = validate_jdk25_candidates_parallel(&values, stage_name);
    for candidate in &valid {
        write_info(&format!(
            "发现可用Java25: {}",
            candidate.java_home.display()
        ));
    }
    valid
}

#[cfg(windows)]
fn find_first_valid_jdk25_candidate(
    candidates: &HashMap<String, String>,
    stage_name: &str,
) -> Option<JdkCandidate> {
    let valid = validate_jdk25_candidates_parallel(
        &candidates.values().cloned().collect::<Vec<_>>(),
        stage_name,
    );
    let best = select_best_jdk25_candidate(valid)?;
    write_info(&format!(
        "阶段[{stage_name}]发现可用Java25，直接使用: {}",
        best.java_home.display()
    ));
    Some(best)
}

#[cfg(windows)]
fn validate_jdk25_candidates_parallel(
    candidates: &[String],
    stage_name: &str,
) -> Vec<JdkCandidate> {
    if candidates.is_empty() {
        return Vec::new();
    }
    let thread_count = MAX_PARALLEL_JDK_CHECKS.min(candidates.len());
    rayon::ThreadPoolBuilder::new()
        .num_threads(thread_count)
        .build()
        .expect("创建Java校验线程池失败")
        .install(|| {
            candidates
                .par_iter()
                .enumerate()
                .filter_map(|(index, java_exe)| {
                    write_info(&format!(
                        "并行校验阶段[{stage_name}] {}/{}: {java_exe}",
                        index + 1,
                        candidates.len()
                    ));
                    test_java_candidate(java_exe)
                })
                .collect()
        })
}

#[cfg(windows)]
fn test_java_candidate(java_executable: &str) -> Option<JdkCandidate> {
    let java_exe = Path::new(java_executable);
    if !java_exe.is_file() {
        return None;
    }
    let java_home = java_exe.parent()?.parent()?.to_path_buf();
    let javac_exe = java_home.join("bin").join("javac.exe");
    if !javac_exe.is_file() {
        write_info(&format!("跳过该Java，不是JDK: {}", java_home.display()));
        return None;
    }
    let Some(release) = read_jdk_release_info(&java_home) else {
        write_info(&format!(
            "跳过该JDK，缺少有效release信息: {}",
            java_home.display()
        ));
        return None;
    };
    let Some(release_major_version) = parse_java_major_version(&release.java_version) else {
        write_info(&format!(
            "跳过该JDK，无法解析release版本: {}",
            java_home.display()
        ));
        return None;
    };
    if release_major_version != 25 {
        write_info(&format!(
            "跳过该JDK，release主版本不是25: {}",
            java_home.display()
        ));
        return None;
    }
    if is_32_bit_architecture(&release.os_arch) {
        write_info(&format!("跳过该Java，不是64位: {java_executable}"));
        return None;
    }
    write_info(&format!(
        "检测到JDK版本: {java_executable} -> JAVA_VERSION=\"{}\"，OS_ARCH=\"{}\"",
        release.java_version, release.os_arch
    ));
    Some(JdkCandidate {
        path_score: path_score(java_exe),
        java_home,
        version_text: format!(
            "JAVA_VERSION=\"{}\"，OS_ARCH=\"{}\"",
            release.java_version, release.os_arch
        ),
    })
}

#[cfg(windows)]
struct JdkReleaseInfo {
    java_version: String,
    os_arch: String,
}

#[cfg(windows)]
fn read_jdk_release_info(java_home: &Path) -> Option<JdkReleaseInfo> {
    let content = fs::read_to_string(java_home.join("release")).ok()?;
    Some(JdkReleaseInfo {
        java_version: read_release_field(&content, "JAVA_VERSION=")?,
        os_arch: read_release_field(&content, "OS_ARCH=")?,
    })
}

#[cfg(windows)]
fn read_release_field(content: &str, field: &str) -> Option<String> {
    content.lines().find_map(|line| {
        let value = line.strip_prefix(field)?.trim();
        Some(value.strip_prefix('"')?.strip_suffix('"')?.to_owned())
    })
}

#[cfg(windows)]
fn parse_java_major_version(version: &str) -> Option<i32> {
    let mut parts = version.split('.');
    if version.starts_with("1.") {
        parts.nth(1)?.parse::<i32>().ok()
    } else {
        parts.next()?.parse::<i32>().ok()
    }
}

#[cfg(windows)]
fn is_32_bit_architecture(os_arch: &str) -> bool {
    os_arch.eq_ignore_ascii_case("x86")
        || os_arch.eq_ignore_ascii_case("i386")
        || os_arch.eq_ignore_ascii_case("i686")
        || os_arch.eq_ignore_ascii_case("x86_32")
        || os_arch.eq_ignore_ascii_case("arm")
        || os_arch.eq_ignore_ascii_case("aarch32")
}

#[cfg(windows)]
fn select_best_jdk25_candidate(mut candidates: Vec<JdkCandidate>) -> Option<JdkCandidate> {
    candidates.sort_by(|left, right| {
        right
            .path_score
            .cmp(&left.path_score)
            .then_with(|| {
                left.java_home
                    .to_string_lossy()
                    .len()
                    .cmp(&right.java_home.to_string_lossy().len())
            })
            .then_with(|| left.java_home.cmp(&right.java_home))
    });
    candidates.into_iter().next()
}

#[cfg(windows)]
fn print_available_jdk_list(candidates: &[JdkCandidate], source_name: &str) {
    write_info(&format!(
        "可用Java25列表[{source_name}]，共{}项:",
        candidates.len()
    ));
    let mut sorted = candidates.to_vec();
    sorted.sort_by(|left, right| {
        right
            .path_score
            .cmp(&left.path_score)
            .then_with(|| {
                left.java_home
                    .to_string_lossy()
                    .len()
                    .cmp(&right.java_home.to_string_lossy().len())
            })
            .then_with(|| left.java_home.cmp(&right.java_home))
    });
    for (index, candidate) in sorted.iter().enumerate() {
        write_info(&format!(
            "  [{}] {}",
            index + 1,
            candidate.java_home.display()
        ));
        write_info(&format!("       {}", candidate.version_text));
    }
}

#[cfg(windows)]
fn path_score(java_exe: &Path) -> i32 {
    let path = java_exe.to_string_lossy().to_ascii_lowercase();
    let mut score = 0;
    if path.starts_with(&launcher_root_string().to_ascii_lowercase()) {
        score += 5000;
    }
    if path.contains("\\.jdks\\") {
        score += 1000;
    }
    if path.contains("\\program files\\") {
        score += 600;
    }
    if path.contains("\\users\\") {
        score += 300;
    }
    if path.contains("jdk-25") || path.contains("jdk25") {
        score += 400;
    }
    if path.contains("temurin") {
        score += 120;
    }
    if path.contains("microsoft") {
        score += 100;
    }
    if path.contains("oracle") {
        score += 80;
    }
    score
}

#[cfg(windows)]
fn launcher_root_string() -> String {
    std::env::current_exe()
        .ok()
        .and_then(|path| path.parent().map(Path::to_path_buf))
        .unwrap_or_default()
        .to_string_lossy()
        .to_string()
}

#[cfg(windows)]
fn run_elevated_mft_search(result_file: &str) -> i32 {
    let result = (|| -> Result<()> {
        fs::write(result_file, b"")?;
        let callback = |path: String| {
            let _ = append_line(result_file, &format!("{MFT_PATH_PREFIX}{path}"));
        };
        let result = ntfs_mft_enum::find_javac_executables(Some(&callback));
        for diagnostic in result.diagnostics {
            append_line(result_file, &format!("{MFT_LOG_PREFIX}{diagnostic}"))?;
        }
        Ok(())
    })();
    match result {
        Ok(()) => 0,
        Err(error) => {
            eprintln!("{error}");
            1
        }
    }
}

#[cfg(windows)]
fn append_line(path: &str, line: &str) -> Result<()> {
    let mut file = OpenOptions::new().create(true).append(true).open(path)?;
    writeln!(file, "{line}")?;
    Ok(())
}

#[cfg(windows)]
struct MftSearchResult {
    succeeded: bool,
    cancelled: bool,
    candidates: HashSet<String>,
}

#[cfg(windows)]
fn find_javac_executables_with_administrator_privilege() -> MftSearchResult {
    let result_file =
        std::env::temp_dir().join(format!("rdi-java-{}.txt", uuid::Uuid::new_v4().simple()));
    let mut candidates = HashSet::new();
    let mut succeeded = false;
    let mut cancelled = false;
    let result = (|| -> Result<()> {
        write_info("请给予管理员权限以搜索电脑上所有的java25");
        let executable = std::env::current_exe()?;
        let result_file_string = result_file.to_string_lossy().to_string();
        write_info("正在搜索java25，请稍等15~60秒左右");
        let process = match win32::start_elevated_with_error_code(
            &executable,
            &[MFT_SEARCH_ARGUMENT.to_owned(), result_file_string],
        ) {
            Ok(process) => process,
            Err(error) if error == win32::ERROR_CANCELLED => {
                cancelled = true;
                write_info("用户拒绝管理员权限，将改为手动选择Java25");
                return Ok(());
            }
            Err(error) => bail!("启动管理员流程失败，错误码{error}"),
        };
        let mut read_character_count = 0;
        while !win32::wait_process(process.0, 100) {
            read_mft_search_updates(&result_file, &mut candidates, &mut read_character_count);
        }
        read_mft_search_updates(&result_file, &mut candidates, &mut read_character_count);
        if win32::process_exit_code(process.0)? != 0 {
            write_info("搜索失败");
            return Ok(());
        }
        succeeded = true;
        write_info(&format!("搜索完成，找到{}个javac.exe", candidates.len()));
        Ok(())
    })();
    if let Err(error) = result {
        write_info(&format!("搜索失败: {error}"));
    }
    let _ = fs::remove_file(result_file);
    MftSearchResult {
        succeeded,
        cancelled,
        candidates,
    }
}

#[cfg(windows)]
fn read_mft_search_updates(
    result_file: &Path,
    candidates: &mut HashSet<String>,
    read_character_count: &mut usize,
) {
    let Ok(content) = fs::read_to_string(result_file) else {
        return;
    };
    let Some(last_newline) = content[..].rfind('\n') else {
        return;
    };
    let complete_length = last_newline + 1;
    if complete_length <= *read_character_count {
        return;
    }
    for line in content[*read_character_count..complete_length]
        .split(['\r', '\n'])
        .filter(|line| !line.is_empty())
    {
        if let Some(message) = line.strip_prefix(MFT_LOG_PREFIX) {
            write_info(message);
        } else if let Some(path) = line.strip_prefix(MFT_PATH_PREFIX) {
            if candidates.insert(path.to_owned()) {
                write_info(&format!("发现javac.exe: {path}"));
            }
        }
    }
    *read_character_count = complete_length;
}

#[cfg(windows)]
fn start_client(
    launcher_root: &Path,
    java_exe: &Path,
    options: &LaunchOptions,
    solid_window: bool,
    r_server_url: &str,
) -> i32 {
    let mut command = Command::new(java_exe);
    command.current_dir(launcher_root);
    command.creation_flags(0x0800_0000);
    command.arg("-Dfile.encoding=UTF-8");
    for argument in &options.jvm_arguments {
        command.arg(argument);
    }
    command.arg(format!("-Drdi.debug={}", options.debug));
    if options.no_update {
        command.arg("-Drdi.noUpdate=true");
    }
    if solid_window {
        command.arg("-Drdi.window.transparent=false");
    }
    command.arg(format!("-Drdi.updater.pid={}", win32::process_id()));
    command.arg(format!("-Drdi.updater.islogmode={}", options.app_logs));
    command.arg(format!("-Drserverurl={r_server_url}"));
    command.args([
        "-cp",
        "lib/*",
        "--enable-native-access=ALL-UNNAMED",
        "calebxzau.rdi.client.MainKt",
    ]);
    if options.app_logs {
        command.stdout(Stdio::piped()).stderr(Stdio::piped());
    }

    write_info(&format!(
        "将使用Java25启动: {}",
        java_exe
            .parent()
            .and_then(Path::parent)
            .unwrap_or(java_exe)
            .display()
    ));
    let mut process = match command.spawn() {
        Ok(process) => process,
        Err(error) => return fail(&format!("无法启动RDI客户端。\r\n错误: {error}")),
    };

    if options.app_logs {
        write_info(&format!("已启动进程PID: {}，正在接收RDI日志", process.id()));
        let output_thread = process
            .stdout
            .take()
            .map(|stream| thread::spawn(move || forward_app_logs(stream)));
        let error_thread = process
            .stderr
            .take()
            .map(|stream| thread::spawn(move || forward_app_logs(stream)));
        let status = process
            .wait()
            .ok()
            .and_then(|status| status.code())
            .unwrap_or(1);
        if let Some(thread) = output_thread {
            let _ = thread.join();
        }
        if let Some(thread) = error_thread {
            let _ = thread.join();
        }
        write_info(&format!("RDI已退出，退出码: {status}"));
        return status;
    }

    write_info("已启动，正在观察确认是否稳定启动");
    let started_at = Instant::now();
    loop {
        match process.try_wait() {
            Ok(Some(status)) => {
                return fail(&format!(
                    "程序在启动后3秒内退出\r\n退出码: {:?}\r\nJava: {}",
                    status.code(),
                    java_exe.display()
                ));
            }
            Ok(None) if started_at.elapsed() >= Duration::from_secs(3) => break,
            Ok(None) => thread::sleep(Duration::from_millis(50)),
            Err(error) => return fail(&format!("观察RDI启动状态失败。\r\n错误: {error}")),
        }
    }
    write_info("启动成功，2秒后关闭本窗口");
    thread::sleep(Duration::from_secs(2));
    0
}

#[cfg(windows)]
fn forward_app_logs<R: Read>(reader: R) {
    for line in BufReader::new(reader).lines().map_while(|line| line.ok()) {
        println!("{line}");
    }
}

#[cfg(windows)]
#[derive(Clone)]
struct JdkCandidate {
    java_home: PathBuf,
    version_text: String,
    path_score: i32,
}

#[cfg(windows)]
struct SearchDirectory {
    path: PathBuf,
    force_deep: bool,
    depth: usize,
}

#[cfg(windows)]
fn normalize_path(path: &Path) -> PathBuf {
    let path = PathBuf::from(path.to_string_lossy().trim().trim_matches('"'));
    if path.is_absolute() {
        path
    } else {
        std::env::current_dir()
            .map(|current| current.join(&path))
            .unwrap_or(path)
    }
}

#[cfg(windows)]
fn search_keywords() -> &'static [&'static str] {
    &[
        "java",
        "jdk",
        "jre",
        "runtime",
        "jbr",
        "temurin",
        "zulu",
        "oracle",
        "microsoft",
        "corretto",
        "graal",
        "graalvm",
        "openjdk",
        "sdk",
        "bin",
        "program",
        "cache",
        "software",
        "local",
        "packages",
        "appdata",
        "users",
        "public",
        "25",
    ]
}

#[cfg(windows)]
fn is_numeric_directory_name(name: &str) -> bool {
    !name.is_empty()
        && name
            .split('.')
            .all(|part| !part.is_empty() && part.bytes().all(|byte| byte.is_ascii_digit()))
}
