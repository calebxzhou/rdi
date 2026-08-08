use std::collections::HashSet;
use std::mem::size_of;

use anyhow::{Result, bail};

use crate::win32::{self, FileIdDescriptor, MftEnumDataV0, OwnedHandle};

const USN_RECORD_HEADER_SIZE: usize = 8;
const USN_RECORD_V2_MIN_SIZE: usize = 60;
const MAXIMUM_PATH_LENGTH: usize = 32_768;
const FILE_ID_TYPE_FILE_ID: u32 = 0;
const JAVAC_EXE: &[u8] = b"javac.exe";

#[derive(Debug, Clone)]
pub struct MftEnumerationResult {
    #[allow(dead_code)]
    pub paths: HashSet<String>,
    pub diagnostics: Vec<String>,
}

struct DriveScanResult {
    paths: HashSet<String>,
    records: u64,
    matches: u64,
    path_errors: PathResolutionStats,
}

#[derive(Default)]
struct PathResolutionStats {
    access_denied: u64,
    file_not_found: u64,
    path_not_found: u64,
    sharing_violation: u64,
    other: u64,
}

impl PathResolutionStats {
    fn record(&mut self, error: u32) {
        match error {
            win32::ERROR_ACCESS_DENIED => self.access_denied += 1,
            win32::ERROR_FILE_NOT_FOUND => self.file_not_found += 1,
            win32::ERROR_PATH_NOT_FOUND => self.path_not_found += 1,
            win32::ERROR_SHARING_VIOLATION => self.sharing_violation += 1,
            _ => self.other += 1,
        }
    }

    fn total(&self) -> u64 {
        self.access_denied
            + self.file_not_found
            + self.path_not_found
            + self.sharing_violation
            + self.other
    }
}

pub fn find_javac_executables(on_path_found: Option<&dyn Fn(String)>) -> MftEnumerationResult {
    let mut paths = HashSet::new();
    let mut diagnostics = Vec::new();

    if !cfg!(windows) {
        diagnostics.push("MFT搜索仅支持Windows".to_owned());
        return MftEnumerationResult { paths, diagnostics };
    }

    for drive_root in win32::get_ntfs_drive_roots() {
        let started_at = std::time::Instant::now();
        let Some(drive_letter) = drive_root.chars().next() else {
            continue;
        };
        match find_javac_executables_on_drive(drive_letter, on_path_found) {
            Ok(result) => {
                let path_count = result.paths.len();
                paths.extend(result.paths);
                diagnostics.push(format!(
                    "MFT[{drive_root}]耗时{:.2}秒，V2={}，javac.exe={}，路径成功={}，路径失败={}（拒绝={}，文件不存在={}，路径不存在={}，共享冲突={}，其它={}）",
                    started_at.elapsed().as_secs_f64(),
                    result.records,
                    result.matches,
                    path_count,
                    result.path_errors.total(),
                    result.path_errors.access_denied,
                    result.path_errors.file_not_found,
                    result.path_errors.path_not_found,
                    result.path_errors.sharing_violation,
                    result.path_errors.other,
                ));
            }
            Err(error) => diagnostics.push(format!("MFT[{drive_root}]失败: {error}")),
        }
    }

    MftEnumerationResult { paths, diagnostics }
}

fn find_javac_executables_on_drive(
    drive_letter: char,
    on_path_found: Option<&dyn Fn(String)>,
) -> Result<DriveScanResult> {
    let volume_path = format!(r"\\.\{drive_letter}:");
    let volume_path_wide = win32::wide(&volume_path);
    let volume = unsafe {
        win32::CreateFileW(
            volume_path_wide.as_ptr(),
            win32::GENERIC_READ,
            win32::FILE_SHARE_READ | win32::FILE_SHARE_WRITE | win32::FILE_SHARE_DELETE,
            std::ptr::null_mut(),
            win32::OPEN_EXISTING,
            0,
            std::ptr::null_mut(),
        )
    };
    if volume.is_null() || volume == win32::INVALID_HANDLE_VALUE {
        bail!("无法以只读方式打开{volume_path}，错误码{}", unsafe {
            win32::GetLastError()
        });
    }
    let volume = OwnedHandle(volume);

    let mut paths = HashSet::new();
    let mut enum_data = MftEnumDataV0 {
        low_usn: 0,
        high_usn: i64::MAX,
        ..Default::default()
    };
    let mut buffer = vec![0u8; 1024 * 1024];
    let mut records = 0;
    let mut matches = 0;
    let mut path_errors = PathResolutionStats::default();

    loop {
        let mut bytes_returned = 0;
        let success = unsafe {
            win32::DeviceIoControl(
                volume.0,
                win32::FSCTL_ENUM_USN_DATA,
                &mut enum_data as *mut MftEnumDataV0 as *mut std::ffi::c_void,
                size_of::<MftEnumDataV0>() as u32,
                buffer.as_mut_ptr() as *mut std::ffi::c_void,
                buffer.len() as u32,
                &mut bytes_returned,
                std::ptr::null_mut(),
            )
        };
        if success == 0 {
            let error = unsafe { win32::GetLastError() };
            if error == win32::ERROR_HANDLE_EOF {
                break;
            }
            bail!("FSCTL_ENUM_USN_DATA读取{drive_letter}:失败，错误码{error}");
        }
        let bytes_returned = bytes_returned as usize;
        if bytes_returned < size_of::<u64>() {
            break;
        }

        let next_reference = u64::from_le_bytes(buffer[..8].try_into().unwrap());
        let mut offset = size_of::<u64>();
        while offset + USN_RECORD_HEADER_SIZE <= bytes_returned {
            let record_length =
                u32::from_le_bytes(buffer[offset..offset + 4].try_into().unwrap()) as usize;
            if record_length < USN_RECORD_HEADER_SIZE {
                bail!("MFT[{drive_letter}]发现无效USN记录长度{record_length}");
            }
            let Some(record_end) = offset.checked_add(record_length) else {
                bail!("MFT[{drive_letter}]USN记录长度溢出");
            };
            if record_end > bytes_returned {
                bail!(
                    "MFT[{drive_letter}]USN记录超出缓冲区，offset={offset}，length={record_length}，bytes={bytes_returned}"
                );
            }

            let major_version =
                u16::from_le_bytes(buffer[offset + 4..offset + 6].try_into().unwrap());
            if major_version == 2 {
                records += 1;
                if record_length >= USN_RECORD_V2_MIN_SIZE {
                    let record = &buffer[offset..record_end];
                    if let Some(file_reference) = get_javac_file_reference(record) {
                        matches += 1;
                        match resolve_path(volume.0, file_reference) {
                            Ok(path) => {
                                if paths.insert(path.clone()) {
                                    if let Some(on_path_found) = on_path_found {
                                        on_path_found(path);
                                    }
                                }
                            }
                            Err(error) => path_errors.record(error),
                        }
                    }
                }
            }
            offset = record_end;
        }

        if next_reference <= enum_data.start_file_reference_number {
            break;
        }
        enum_data.start_file_reference_number = next_reference;
    }

    Ok(DriveScanResult {
        paths,
        records,
        matches,
        path_errors,
    })
}

fn get_javac_file_reference(record: &[u8]) -> Option<u64> {
    if record.len() < USN_RECORD_V2_MIN_SIZE {
        return None;
    }
    let file_attributes = u32::from_le_bytes(record[52..56].try_into().ok()?);
    if file_attributes & win32::FILE_ATTRIBUTE_DIRECTORY != 0 {
        return None;
    }
    let file_name_length = u16::from_le_bytes(record[56..58].try_into().ok()?);
    let file_name_offset = u16::from_le_bytes(record[58..60].try_into().ok()?);
    let file_name_length = file_name_length as usize;
    let file_name_offset = file_name_offset as usize;
    let Some(file_name_end) = file_name_offset.checked_add(file_name_length) else {
        return None;
    };
    if file_name_offset < USN_RECORD_V2_MIN_SIZE
        || file_name_end > record.len()
        || file_name_length % 2 != 0
    {
        return None;
    }
    let name_bytes = &record[file_name_offset..file_name_end];
    if !is_javac_exe(name_bytes) {
        return None;
    }
    Some(u64::from_le_bytes(record[8..16].try_into().ok()?))
}

#[inline]
fn is_javac_exe(name_bytes: &[u8]) -> bool {
    if name_bytes.len() != JAVAC_EXE.len() * 2 {
        return false;
    }
    name_bytes
        .chunks_exact(2)
        .zip(JAVAC_EXE.iter())
        .all(|(chunk, expected)| {
            let character = u16::from_le_bytes([chunk[0], chunk[1]]);
            ascii_lower(character) == *expected as u16
        })
}

#[inline]
fn ascii_lower(character: u16) -> u16 {
    if (b'A' as u16..=b'Z' as u16).contains(&character) {
        character + (b'a' - b'A') as u16
    } else {
        character
    }
}

fn resolve_path(volume: win32::RawHandle, file_reference: u64) -> std::result::Result<String, u32> {
    let mut file_id = FileIdDescriptor {
        size: size_of::<FileIdDescriptor>() as u32,
        id_type: FILE_ID_TYPE_FILE_ID,
        file_id: file_reference as i64,
        ..Default::default()
    };
    let file = unsafe {
        win32::OpenFileById(
            volume,
            &mut file_id,
            0,
            win32::FILE_SHARE_READ | win32::FILE_SHARE_WRITE | win32::FILE_SHARE_DELETE,
            std::ptr::null_mut(),
            win32::FILE_FLAG_BACKUP_SEMANTICS,
        )
    };
    if file.is_null() || file == win32::INVALID_HANDLE_VALUE {
        return Err(unsafe { win32::GetLastError() });
    }
    let file = OwnedHandle(file);
    let mut path = vec![0u16; MAXIMUM_PATH_LENGTH];
    let length = unsafe {
        win32::GetFinalPathNameByHandleW(file.0, path.as_mut_ptr(), path.len() as u32, 0)
    } as usize;
    if length == 0 {
        return Err(unsafe { win32::GetLastError() });
    }
    if length >= path.len() {
        return Err(win32::ERROR_INSUFFICIENT_BUFFER);
    }
    let path = win32::from_wide(&path[..length]);
    Ok(path.strip_prefix(r"\\?\").unwrap_or(&path).to_owned())
}
