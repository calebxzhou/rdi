use std::path::{Path, PathBuf};
use std::process::Command;

use crate::AppResult;
use crate::installer::{InstallRequest, ProgressSink};

pub fn run() -> i32 {
    match run_inner() {
        Ok(()) => 0,
        Err(error) => {
            eprintln!("安装失败:{error}");
            1
        }
    }
}

fn run_inner() -> AppResult<()> {
    crate::windows::set_console_utf8();

    let default_path = crate::windows::select_default_path()?;
    let install_path = select_install_path(&default_path)?;
    let create_desktop_shortcut = ask_yes_no("要创建桌面图标吗？", "不创建")?;
    let create_start_menu_shortcut = ask_yes_no("要创建开始菜单图标吗？", "不创建")?;

    println!();
    println!("开始安装到{}", install_path.display());

    let install_directory = install_path.clone();
    let start_exe = install_path.join("start.exe");
    let mut progress = ConsoleProgress;
    let result = crate::installer::install(
        InstallRequest {
            install_path,
            create_desktop_shortcut,
            create_start_menu_shortcut,
        },
        &mut progress,
    )?;

    for warning in result.warnings {
        println!("warning:{warning}");
    }
    println!("安装完成");
    if ask_yes_no("启动已安装的rdi吗？", "退出")? {
        Command::new(start_exe)
            .current_dir(install_directory)
            .spawn()
            .map_err(|error| format!("启动已安装的rdi失败:{error}"))?;
    }
    Ok(())
}

fn ask_yes_no(question: &str, no_action: &str) -> AppResult<bool> {
    loop {
        println!("{question}");
        println!("按Y同意 按N{no_action}");
        match crate::windows::read_key()? {
            0x59 => {
                println!("Y");
                return Ok(true);
            }
            0x4e => {
                println!("N");
                return Ok(false);
            }
            _ => println!("请按Y或N。"),
        }
    }
}

fn select_install_path(default_path: &Path) -> AppResult<PathBuf> {
    loop {
        if ask_yes_no(
            &format!("要安装到{}吗？", default_path.display()),
            "选择其他文件夹",
        )? {
            return Ok(default_path.to_path_buf());
        }

        if let Some(selected_path) = crate::windows::pick_install_folder(default_path)? {
            println!("已选择：{}", selected_path.display());
            return Ok(selected_path);
        }
        println!("未选择安装文件夹。");
    }
}

struct ConsoleProgress;

impl ProgressSink for ConsoleProgress {
    fn report(&mut self, value: &str) {
        println!("{value}");
    }
}
