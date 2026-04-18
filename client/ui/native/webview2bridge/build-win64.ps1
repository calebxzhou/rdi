$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $scriptDir "rdi_webview2_bridge.cpp"
$outDir = Join-Path $scriptDir "out"
$dll = Join-Path $outDir "rdi_webview2_bridge.dll"
$vswhere = "C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe"
$vcvars64 = "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
$cl = "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.40.33807\bin\Hostx64\x64\cl.exe"
$gxx = "C:\Users\calebxzhou\mingw64\bin\g++.exe"

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (Test-Path -LiteralPath $vswhere) {
    $installPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if ($LASTEXITCODE -eq 0 -and $installPath) {
        $candidateVcvars = Join-Path $installPath "VC\Auxiliary\Build\vcvars64.bat"
        if (Test-Path -LiteralPath $candidateVcvars) {
            $vcvars64 = $candidateVcvars
        }
    }
}

if (Test-Path -LiteralPath $vcvars64) {
    $cmd = @(
        "`"$vcvars64`"",
        "&&",
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
    cmd /c $cmd
    if ($LASTEXITCODE -ne 0) {
        throw "MSVC cl.exe构建失败，exit code=$LASTEXITCODE"
    }
    Write-Host "built with MSVC: $dll"
    exit 0
}

if (!(Test-Path -LiteralPath $gxx)) {
    throw "未找到MSVC vcvars64.bat，也未找到g++.exe: $gxx"
}

& $gxx `
    -std=c++20 `
    -municode `
    -shared `
    -O2 `
    -static-libgcc `
    -static-libstdc++ `
    -o $dll `
    $source `
    -lole32 `
    -luuid `
    -luser32

if ($LASTEXITCODE -ne 0) {
    throw "MinGW g++构建失败，exit code=$LASTEXITCODE"
}

Write-Host "built with MinGW fallback: $dll"
