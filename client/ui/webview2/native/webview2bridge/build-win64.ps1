$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $scriptDir "rdi_webview2_bridge.cpp"
$outDir = Join-Path $scriptDir "out"
$dll = Join-Path $outDir "rdi_webview2_bridge.dll"
$moduleDir = Resolve-Path (Join-Path $scriptDir "..\..")
$bundledDll = Join-Path $moduleDir "src\main\resources\webview2\win-x64\rdi_webview2_bridge.dll"
$vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
$vcvars64 = $null

if (Test-Path -LiteralPath $vswhere) {
    $installPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if ($LASTEXITCODE -eq 0 -and $installPath) {
        $candidate = Join-Path $installPath "VC\Auxiliary\Build\vcvars64.bat"
        if (Test-Path -LiteralPath $candidate) {
            $vcvars64 = $candidate
        }
    }
}

if (!$vcvars64 -and !(Get-Command cl.exe -ErrorAction SilentlyContinue)) {
    throw "未找到Visual Studio C++ x64 toolchain。请安装MSVC，或从x64 Native Tools Command Prompt运行此脚本。"
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$compile = @(
    "cl",
    "/nologo",
    "/std:c++20",
    "/EHsc",
    "/O2",
    "/MT",
    "/LD",
    "/utf-8",
    "/Fe:`"$dll`"",
    "`"$source`"",
    "ole32.lib",
    "uuid.lib",
    "user32.lib"
) -join " "

if ($vcvars64) {
    cmd /d /s /c "`"$vcvars64`" && $compile"
} else {
    cmd /d /s /c $compile
}
if ($LASTEXITCODE -ne 0) {
    throw "MSVC构建失败，exit code=$LASTEXITCODE"
}

& (Join-Path $scriptDir "check_exports.ps1") -Dll $dll -Vcvars64 $vcvars64
Copy-Item -LiteralPath $dll -Destination $bundledDll -Force
Write-Host "已构建并更新bundled DLL: $bundledDll"
