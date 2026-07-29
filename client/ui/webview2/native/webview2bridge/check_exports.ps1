param(
    [string]$Dll = (Join-Path $PSScriptRoot "out\rdi_webview2_bridge.dll"),
    [string]$Vcvars64
)

$ErrorActionPreference = "Stop"

if (!(Test-Path -LiteralPath $Dll)) {
    throw "未找到bridge DLL: $Dll"
}

if ($Vcvars64) {
    $exports = cmd /d /s /c "`"$Vcvars64`" && dumpbin /nologo /exports `"$Dll`"" 2>&1
} else {
    if (!(Get-Command dumpbin.exe -ErrorAction SilentlyContinue)) {
        throw "未找到dumpbin.exe。请从Visual Studio developer environment运行此脚本。"
    }
    $exports = & dumpbin.exe /nologo /exports $Dll 2>&1
}
if ($LASTEXITCODE -ne 0) {
    throw "读取bridge exports失败，exit code=$LASTEXITCODE"
}

$requiredExports = @(
    "rdi_webview2_create",
    "rdi_webview2_destroy",
    "rdi_webview2_attach",
    "rdi_webview2_set_bounds",
    "rdi_webview2_set_visible",
    "rdi_webview2_navigate",
    "rdi_webview2_notify_parent_window_position_changed",
    "rdi_webview2_get_state",
    "rdi_webview2_get_last_error",
    "rdi_webview2_get_last_hresult"
)
$exportText = $exports -join "`n"
$missing = $requiredExports | Where-Object { $exportText -notmatch "\b$([Regex]::Escape($_))\b" }
if ($missing) {
    throw "bridge缺少exports: $($missing -join ', ')"
}

Write-Host "bridge exports检查通过: $Dll"
