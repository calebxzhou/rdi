#[cfg(windows)]
mod archive;
#[cfg(windows)]
mod cli;
#[cfg(windows)]
mod installer;
#[cfg(windows)]
mod windows;

#[cfg(windows)]
pub(crate) type AppResult<T> = Result<T, String>;

#[cfg(windows)]
fn main() {
    std::process::exit(cli::run());
}

#[cfg(not(windows))]
fn main() {
    eprintln!("安装器仅支持Windows。");
    std::process::exit(1);
}
