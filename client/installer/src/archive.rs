use std::ffi::c_void;
use std::io::{self, Read};
use std::path::{Component, Path, PathBuf};
use std::ptr::null;
use std::time::{Duration, UNIX_EPOCH};

use crate::AppResult;
use crate::installer::ProgressSink;

const RESOURCE_ID: usize = 101;
const RESOURCE_TYPE_RCDATA: usize = 10;

pub fn extract_to(staging_directory: &Path, progress: &mut dyn ProgressSink) -> AppResult<()> {
    std::fs::create_dir_all(staging_directory)
        .map_err(|error| format!("创建暂存目录失败:{}", error))?;

    let source = open_archive_source()?;
    let decoder = zstd::stream::read::Decoder::new(source)
        .map_err(|error| format!("打开内置client.tar.zst失败:{error}"))?;
    let mut archive = tar::Archive::new(decoder);
    let entries = archive
        .entries()
        .map_err(|error| format!("读取TAR条目失败:{error}"))?;

    for entry_result in entries {
        let mut entry = entry_result.map_err(|error| format!("读取TAR条目失败:{error}"))?;
        let entry_path = entry
            .path()
            .map_err(|error| format!("读取TAR路径失败:{error}"))?
            .into_owned();
        let display_path = entry_path.to_string_lossy();
        progress.report(&format!("正在安装:{display_path}"));

        let destination = resolve_archive_path(staging_directory, &entry_path)?;
        let entry_type = entry.header().entry_type();
        if entry_type.is_dir() {
            std::fs::create_dir_all(&destination)
                .map_err(|error| format!("创建归档目录{display_path}失败:{error}"))?;
            continue;
        }
        if !entry_type.is_file() {
            return Err(format!("安装包包含不支持的entry:{display_path}"));
        }

        if let Some(parent) = destination.parent() {
            std::fs::create_dir_all(parent)
                .map_err(|error| format!("创建归档父目录失败:{error}"))?;
        }
        let mut output = std::fs::File::create(&destination)
            .map_err(|error| format!("写入归档文件{display_path}失败:{error}"))?;
        io::copy(&mut entry, &mut output)
            .map_err(|error| format!("写入归档文件{display_path}失败:{error}"))?;

        let modified_time = entry
            .header()
            .mtime()
            .map_err(|error| format!("读取归档时间失败:{error}"))?;
        let modified_time = UNIX_EPOCH + Duration::from_secs(modified_time);
        output
            .set_modified(modified_time)
            .map_err(|error| format!("设置归档文件时间失败:{error}"))?;
    }

    Ok(())
}

fn resolve_archive_path(root_directory: &Path, entry_path: &Path) -> AppResult<PathBuf> {
    if entry_path.as_os_str().is_empty() || entry_path.is_absolute() {
        return Err(format!("非法entry路径:{}", entry_path.to_string_lossy()));
    }

    let mut destination = root_directory.to_path_buf();
    for component in entry_path.components() {
        match component {
            Component::Normal(value) => destination.push(value),
            Component::CurDir => {}
            Component::ParentDir | Component::RootDir | Component::Prefix(_) => {
                return Err(format!("非法entry路径:{}", entry_path.to_string_lossy()));
            }
        }
    }
    Ok(destination)
}

struct ResourceReader {
    pointer: *const u8,
    length: usize,
    position: usize,
}

enum ArchiveSource {
    Embedded(ResourceReader),
    #[cfg(test)]
    Fixture(std::fs::File),
}

impl Read for ArchiveSource {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        match self {
            Self::Embedded(reader) => reader.read(buffer),
            #[cfg(test)]
            Self::Fixture(file) => file.read(buffer),
        }
    }
}

impl Read for ResourceReader {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        if self.position == self.length || buffer.is_empty() {
            return Ok(0);
        }

        let amount = buffer.len().min(self.length - self.position);
        unsafe {
            std::ptr::copy_nonoverlapping(
                self.pointer.add(self.position),
                buffer.as_mut_ptr(),
                amount,
            );
        }
        self.position += amount;
        Ok(amount)
    }
}

fn open_archive_source() -> AppResult<ArchiveSource> {
    match open_embedded_archive() {
        Ok(reader) => Ok(ArchiveSource::Embedded(reader)),
        Err(error) => {
            #[cfg(test)]
            {
                let path =
                    std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("assets/client.tar.zst");
                return std::fs::File::open(path)
                    .map(ArchiveSource::Fixture)
                    .map_err(|fixture_error| {
                        format!("{error};测试fixture读取失败:{fixture_error}")
                    });
            }
            #[cfg(not(test))]
            {
                Err(error)
            }
        }
    }
}

fn open_embedded_archive() -> AppResult<ResourceReader> {
    let module = unsafe { GetModuleHandleW(null()) };
    if module.is_null() {
        return Err(crate::windows::last_error("获取安装器模块失败"));
    }

    let resource = unsafe {
        FindResourceW(
            module,
            resource_id(RESOURCE_ID),
            resource_id(RESOURCE_TYPE_RCDATA),
        )
    };
    if resource.is_null() {
        return Err(crate::windows::last_error("未找到内置client.tar.zst"));
    }

    let loaded = unsafe { LoadResource(module, resource) };
    if loaded.is_null() {
        return Err(crate::windows::last_error("加载内置client.tar.zst失败"));
    }
    let pointer = unsafe { LockResource(loaded) } as *const u8;
    let length = unsafe { SizeofResource(module, resource) } as usize;
    if pointer.is_null() || length == 0 {
        return Err("内置client.tar.zst为空".to_owned());
    }

    Ok(ResourceReader {
        pointer,
        length,
        position: 0,
    })
}

fn resource_id(value: usize) -> *const u16 {
    value as *const u16
}

#[link(name = "kernel32")]
unsafe extern "system" {
    fn GetModuleHandleW(module_name: *const u16) -> *mut c_void;
    fn FindResourceW(
        module: *mut c_void,
        name: *const u16,
        resource_type: *const u16,
    ) -> *mut c_void;
    fn LoadResource(module: *mut c_void, resource: *mut c_void) -> *mut c_void;
    fn LockResource(resource: *mut c_void) -> *mut c_void;
    fn SizeofResource(module: *mut c_void, resource: *mut c_void) -> u32;
}

#[cfg(test)]
mod tests {
    use super::resolve_archive_path;
    use std::path::Path;

    #[test]
    fn resolves_normal_archive_path() {
        let result = resolve_archive_path(Path::new("stage"), Path::new("lib/rdi.jar"));
        assert_eq!(result, Ok(Path::new("stage/lib/rdi.jar").to_path_buf()));
    }

    #[test]
    fn rejects_parent_archive_path() {
        assert!(resolve_archive_path(Path::new("stage"), Path::new("../start.exe")).is_err());
    }
}
