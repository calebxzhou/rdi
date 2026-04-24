$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Set-Location -LiteralPath $PSScriptRoot

function Write-Info {
    param([string]$Message)
    Write-Host ("[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message)
}

function Wait-ForUserClose {
    param(
        [string]$Message = "按回车键关闭窗口"
    )
    Write-Host ""
    Read-Host $Message | Out-Null
}

function Show-StartError {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Host $Message -ForegroundColor Red
    try {
        $wshell = New-Object -ComObject WScript.Shell
        $null = $wshell.Popup($Message, 0, "RDI启动失败", 16)
    } catch {}
}

$jdkCacheFile = Join-Path $PSScriptRoot "avaliable_jdks.txt"

function Get-NormalizedPath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    try {
        return [System.IO.Path]::GetFullPath($Path.Trim().Trim('"')).TrimEnd('\')
    } catch {
        return $null
    }
}

function Get-PathScore {
    param([string]$JavaExe)

    $score = 0
    $javaPath = $JavaExe.ToLowerInvariant()
    $scriptDir = (Get-NormalizedPath $PSScriptRoot).ToLowerInvariant()

    if ($javaPath.StartsWith($scriptDir)) {
        $score += 5000
    }
    if ($javaPath.Contains("\.jdks\")) {
        $score += 1000
    }
    if ($javaPath.Contains("\program files\")) {
        $score += 600
    }
    if ($javaPath.Contains("\users\")) {
        $score += 300
    }
    if ($javaPath.Contains("jdk-25") -or $javaPath.Contains("jdk25")) {
        $score += 400
    }
    if ($javaPath.Contains("temurin")) {
        $score += 120
    }
    if ($javaPath.Contains("microsoft")) {
        $score += 100
    }
    if ($javaPath.Contains("oracle")) {
        $score += 80
    }
    return $score
}

function Invoke-ProcessCapture {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [string]$Arguments = ""
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = $Arguments
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo

    try {
        $null = $process.Start()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $combinedOutput = (($stderr.TrimEnd(), $stdout.TrimEnd()) | Where-Object { $_ }) -join [Environment]::NewLine
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Output   = $combinedOutput
        }
    } finally {
        $process.Dispose()
    }
}

function Test-JavaCandidate {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JavaExe
    )

    $normalizedExe = Get-NormalizedPath $JavaExe
    if (-not $normalizedExe) {
        return $null
    }
    if (-not (Test-Path -LiteralPath $normalizedExe -PathType Leaf)) {
        return $null
    }

    $versionResult = $null
    try {
        $versionResult = Invoke-ProcessCapture -FilePath $normalizedExe -Arguments "-version"
    } catch {
        return $null
    }
    $versionOutput = $versionResult.Output
    if ([string]::IsNullOrWhiteSpace($versionOutput)) {
        return $null
    }

    $majorVersion = $null
    $versionLines = @(
        $versionOutput -split "`r?`n" |
            ForEach-Object { $_.Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
    $versionLine = $versionLines |
        Where-Object { $_ -match '\bversion\b\s+"[^"]+"' } |
        Select-Object -First 1
    if (-not $versionLine) {
        $versionLine = $versionLines | Select-Object -First 1
    }
    $displayVersion = if ([string]::IsNullOrWhiteSpace($versionLine)) {
        "<empty>"
    } else {
        ($versionLine -replace '\s+', ' ').Trim()
    }
    Write-Info ("检测到Java版本: {0} -> {1}" -f $normalizedExe, $displayVersion)
    if ($versionLine -match '"(?<version>\d+(?:\.\d+){0,3})') {
        $rawVersion = $Matches.version
        if ($rawVersion.StartsWith("1.")) {
            $parts = $rawVersion.Split(".")
            if ($parts.Length -ge 2) {
                $majorVersion = [int]$parts[1]
            }
        } else {
            $majorVersion = [int]($rawVersion.Split(".")[0])
        }
    }
    if ($majorVersion -ne 25) {
        Write-Info ("跳过该Java，主版本不是25: {0}" -f $normalizedExe)
        return $null
    }

    $is64Bit = -not ($versionOutput -match '32-Bit|x86')
    if (-not $is64Bit) {
        Write-Info ("跳过该Java，不是64位: {0}" -f $normalizedExe)
        return $null
    }

    $javaDir = Split-Path -Parent $normalizedExe
    $javaHome = Split-Path -Parent $javaDir
    $isJdk = Test-Path -LiteralPath (Join-Path $javaHome "bin\javac.exe") -PathType Leaf
    if (-not $isJdk) {
        Write-Info ("跳过该Java，不是JDK: {0}" -f $javaHome)
        return $null
    }

    $releaseFile = Join-Path $javaHome "release"
    $releaseText = ""
    if (Test-Path -LiteralPath $releaseFile -PathType Leaf) {
        try {
            $releaseText = Get-Content -LiteralPath $releaseFile -Raw -Encoding UTF8
        } catch {
            $releaseText = ""
        }
    }

    [pscustomobject]@{
        JavaExe      = $normalizedExe
        JavaHome     = $javaHome
        MajorVersion = $majorVersion
        IsJdk        = $isJdk
        Is64Bit      = $is64Bit
        ReleaseText  = $releaseText
        VersionText  = $versionOutput.Trim()
        PathScore    = Get-PathScore -JavaExe $normalizedExe
    }
}

function Add-CandidateJavaPath {
    param(
        [System.Collections.Generic.HashSet[string]]$Set,
        [string]$Path
    )

    $normalized = Get-NormalizedPath $Path
    if (-not $normalized) {
        return
    }

    if (Test-Path -LiteralPath $normalized -PathType Container) {
        $javaExe = Join-Path $normalized "java.exe"
        if (Test-Path -LiteralPath $javaExe -PathType Leaf) {
            [void]$Set.Add($javaExe)
            return
        }

        $javawExe = Join-Path $normalized "javaw.exe"
        if (Test-Path -LiteralPath $javawExe -PathType Leaf) {
            $siblingJava = Join-Path $normalized "java.exe"
            if (Test-Path -LiteralPath $siblingJava -PathType Leaf) {
                [void]$Set.Add($siblingJava)
            }
            return
        }

        $binJava = Join-Path $normalized "bin\java.exe"
        if (Test-Path -LiteralPath $binJava -PathType Leaf) {
            [void]$Set.Add($binJava)
        }
        return
    }

    if (Test-Path -LiteralPath $normalized -PathType Leaf) {
        $name = [System.IO.Path]::GetFileName($normalized).ToLowerInvariant()
        if ($name -eq "java.exe" -or $name -eq "javaw.exe") {
            $candidateJava = if ($name -eq "javaw.exe") {
                Join-Path (Split-Path -Parent $normalized) "java.exe"
            } else {
                $normalized
            }
            if (Test-Path -LiteralPath $candidateJava -PathType Leaf) {
                [void]$Set.Add($candidateJava)
            }
        }
    }
}

function Read-CachedJdkList {
    if (-not (Test-Path -LiteralPath $jdkCacheFile -PathType Leaf)) {
        Write-Info "未发现JDK缓存文件avaliable_jdks.txt"
        return @()
    }

    try {
        $lines = Get-Content -LiteralPath $jdkCacheFile -Encoding UTF8 |
            ForEach-Object { $_.Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Select-Object -Unique
        Write-Info ("读取JDK缓存成功，共{0}条" -f $lines.Count)
        return @($lines)
    } catch {
        Write-Info ("读取JDK缓存失败，将重新搜索: {0}" -f $_.Exception.Message)
        return @()
    }
}

function Write-CachedJdkList {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Generic.List[object]]$Candidates
    )

    try {
        $javaHomes = $Candidates |
            Select-Object -ExpandProperty JavaHome -Unique |
            Sort-Object
        Set-Content -LiteralPath $jdkCacheFile -Value $javaHomes -Encoding UTF8
        Write-Info ("已写入JDK缓存到avaliable_jdks.txt，共{0}条" -f $javaHomes.Count)
    } catch {
        Write-Info ("写入JDK缓存失败: {0}" -f $_.Exception.Message)
    }
}

function Convert-JavaHomesToCandidateSet {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$JavaHomes
    )

    $candidateSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($javaHome in $JavaHomes) {
        Add-CandidateJavaPath -Set $candidateSet -Path $javaHome
    }
    return $candidateSet
}

function Print-AvailableJdkList {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Generic.List[object]]$Candidates,
        [Parameter(Mandatory = $true)]
        [string]$SourceName
    )

    Write-Info ("可用Java25列表[{0}]，共{1}项:" -f $SourceName, $Candidates.Count)
    $index = 0
    foreach ($candidate in ($Candidates | Sort-Object -Property `
        @{ Expression = { $_.PathScore }; Descending = $true },
        @{ Expression = { $_.JavaHome.Length }; Descending = $false },
        @{ Expression = { $_.JavaHome }; Descending = $false })) {
        $index += 1
        $versionLine = @(
            $candidate.VersionText -split "`r?`n" |
                ForEach-Object { $_.Trim() } |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        ) | Where-Object { $_ -match '\bversion\b\s+"[^"]+"' } | Select-Object -First 1
        if (-not $versionLine) {
            $versionLine = "<unknown version>"
        }
        Write-Info ("  [{0}] {1}" -f $index, $candidate.JavaHome)
        Write-Info ("       {0}" -f $versionLine)
    }
}

function Show-NoJavaOptions {
    Write-Host ""
    Write-Host "未找到可用的64位Java25。" -ForegroundColor Red
    Write-Host "按 R 重新搜索，直接按回车手动选择JDK安装目录。" -ForegroundColor Yellow
}

function Pick-FolderPath {
    param(
        [string]$Title = "选择Java25安装目录"
    )

    try {
        $shell = New-Object -ComObject Shell.Application
        $folder = $shell.BrowseForFolder(0, $Title, 0, 0)
        if ($folder -and $folder.Self -and $folder.Self.Path) {
            return $folder.Self.Path
        }
    } catch {
        Write-Info ("打开文件夹选择框失败: {0}" -f $_.Exception.Message)
    }
    return $null
}

function Select-ManualJdk25 {
    Write-Info "请手动选择Java25安装目录"
    $selectedPath = Pick-FolderPath
    if (-not $selectedPath) {
        Write-Info "未选择目录"
        return $null
    }

    Write-Info ("已选择目录: {0}" -f $selectedPath)
    $candidateSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    Add-CandidateJavaPath -Set $candidateSet -Path $selectedPath
    if ($candidateSet.Count -eq 0) {
        Show-StartError "所选目录中未找到java.exe：`r`n$selectedPath"
        return $null
    }

    $validCandidates = Find-ValidJdk25Candidates -CandidateSet $candidateSet -StageName "手动选择"
    if ($validCandidates.Count -eq 0) {
        Show-StartError "所选目录不是可用的64位Java25：`r`n$selectedPath"
        return $null
    }

    Write-CachedJdkList -Candidates $validCandidates
    Print-AvailableJdkList -Candidates $validCandidates -SourceName "手动选择"
    return Select-BestJdk25Candidate -Candidates $validCandidates
}

function Get-InitialCandidateRoots {
    $roots = New-Object System.Collections.Generic.List[string]

    if ($env:JAVA_HOME) {
        $roots.Add($env:JAVA_HOME)
    }

    $pathValues = @($env:PATH -split ';') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach ($pathEntry in $pathValues) {
        $roots.Add($pathEntry)
    }

    $userProfile = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    $roots.Add($userProfile)
    if ($env:PUBLIC) {
        $roots.Add($env:PUBLIC)
    }
    $roots.Add((Join-Path $userProfile ".jdks"))
    $roots.Add((Join-Path $userProfile ".sdkman\candidates\java"))
    if ($env:LOCALAPPDATA) {
        $roots.Add((Join-Path $env:LOCALAPPDATA "Programs"))
        $roots.Add((Join-Path $env:LOCALAPPDATA "Microsoft"))
    }
    if ($env:ProgramFiles) {
        $roots.Add((Join-Path $env:ProgramFiles "Java"))
        $roots.Add((Join-Path $env:ProgramFiles "Microsoft"))
        $roots.Add((Join-Path $env:ProgramFiles "Eclipse Adoptium"))
        $roots.Add((Join-Path $env:ProgramFiles "BellSoft"))
    }
    $roots.Add($PSScriptRoot)

    return $roots | Where-Object { $_ } | Select-Object -Unique
}

function Get-DriveRoots {
    $roots = New-Object System.Collections.Generic.List[string]

    foreach ($drive in [System.IO.DriveInfo]::GetDrives()) {
        if (-not $drive.IsReady) { continue }
        if ($drive.DriveType -eq [System.IO.DriveType]::Network) { continue }
        $roots.Add($drive.RootDirectory.FullName)
    }

    return $roots | Where-Object { $_ } | Select-Object -Unique
}

function Search-Jdk25Candidates {
    param(
        [string[]]$Roots,
        [string]$StageName
    )

    $candidateSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $seenDirs = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $nameKeywords = @(
        "java", "jdk", "jre", "runtime", "jbr", "temurin", "zulu", "oracle",
        "microsoft", "corretto", "graal", "graalvm", "openjdk", "sdk", "bin", "program",
        "cache", "software", "local", "packages", "appdata", "users", "public", "25"
    )

    $rootIndex = 0
    $totalRoots = @($Roots).Count
    foreach ($root in $Roots) {
        $rootIndex += 1
        $normalizedRoot = Get-NormalizedPath $root
        if (-not $normalizedRoot) { continue }
        if (-not (Test-Path -LiteralPath $normalizedRoot -PathType Container)) { continue }

        Write-Info ("搜索阶段[{0}] {1}/{2}: {3}" -f $StageName, $rootIndex, $totalRoots, $normalizedRoot)

        Add-CandidateJavaPath -Set $candidateSet -Path $normalizedRoot

        $stack = New-Object System.Collections.Stack
        $stack.Push([pscustomobject]@{
            Path      = $normalizedRoot
            ForceDeep = $normalizedRoot -like "*.jdks*" -or $normalizedRoot -like "*.sdkman*"
            Depth     = 0
        })

        while ($stack.Count -gt 0) {
            $current = $stack.Pop()
            $currentPath = Get-NormalizedPath $current.Path
            if (-not $currentPath) { continue }
            if (-not $seenDirs.Add($currentPath)) { continue }
            if (-not (Test-Path -LiteralPath $currentPath -PathType Container)) { continue }

            try {
                $entries = Get-ChildItem -LiteralPath $currentPath -Directory -ErrorAction Stop
            } catch {
                continue
            }

            foreach ($entry in $entries) {
                if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                    continue
                }

                $entryName = $entry.Name.ToLowerInvariant()
                $isInteresting =
                    $current.ForceDeep -or
                    $entryName -eq "bin" -or
                    ($entryName -match '^\d+(\.\d+)*$') -or
                    ($nameKeywords | Where-Object { $entryName.Contains($_) } | Select-Object -First 1)

                if (-not $isInteresting) {
                    continue
                }

                Add-CandidateJavaPath -Set $candidateSet -Path $entry.FullName

                $nextDepth = $current.Depth + 1
                $allowDeeper = $current.ForceDeep -or $nextDepth -lt 4
                if ($allowDeeper) {
                    $stack.Push([pscustomobject]@{
                        Path      = $entry.FullName
                        ForceDeep = $current.ForceDeep -or $entryName -in @("java", "jdk", "jre", "runtime", "jbr", "bin")
                        Depth     = $nextDepth
                    })
                }
            }
        }
    }

    return $candidateSet
}

function Find-ValidJdk25Candidates {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Generic.HashSet[string]]$CandidateSet,
        [Parameter(Mandatory = $true)]
        [string]$StageName
    )

    $candidates = New-Object System.Collections.Generic.List[object]
    $candidateArray = @($CandidateSet)
    $index = 0
    foreach ($candidateJava in $candidateArray) {
        $index += 1
        Write-Info ("校验阶段[{0}] {1}/{2}: {3}" -f $StageName, $index, $candidateArray.Count, $candidateJava)
        $result = Test-JavaCandidate -JavaExe $candidateJava
        if ($result) {
            $candidates.Add($result)
            Write-Info ("发现可用Java25: {0}" -f $result.JavaHome)
        }
    }

    return $candidates
}

function Select-BestJdk25Candidate {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Generic.List[object]]$Candidates
    )

    return $Candidates |
        Sort-Object -Property `
            @{ Expression = { $_.PathScore }; Descending = $true },
            @{ Expression = { $_.JavaHome.Length }; Descending = $false },
            @{ Expression = { $_.JavaHome }; Descending = $false } |
        Select-Object -First 1
}

function Find-BestJdk25 {
    Write-Info "开始搜索Java25"

    $cachedJavaHomes = @(Read-CachedJdkList)
    if ($cachedJavaHomes.Count -gt 0) {
        Write-Info "优先使用JDK缓存列表"
        $cachedCandidates = Convert-JavaHomesToCandidateSet -JavaHomes $cachedJavaHomes
        Write-Info ("缓存候选Java数量: {0}" -f $cachedCandidates.Count)
        $cachedValidCandidates = Find-ValidJdk25Candidates -CandidateSet $cachedCandidates -StageName "缓存校验"
        if ($cachedValidCandidates.Count -gt 0) {
            Write-CachedJdkList -Candidates $cachedValidCandidates
            Print-AvailableJdkList -Candidates $cachedValidCandidates -SourceName "缓存"
            Write-Info "缓存中的Java25仍然可用，直接使用缓存结果"
            return Select-BestJdk25Candidate -Candidates $cachedValidCandidates
        }
        Write-Info "缓存中的Java25已失效，开始重新搜索"
    }

    $priorityRoots = @(Get-InitialCandidateRoots)
    Write-Info ("优先目录数量: {0}" -f $priorityRoots.Count)
    $priorityCandidates = Search-Jdk25Candidates -Roots $priorityRoots -StageName "优先目录"
    Write-Info ("优先目录候选Java数量: {0}" -f $priorityCandidates.Count)
    $priorityValidCandidates = Find-ValidJdk25Candidates -CandidateSet $priorityCandidates -StageName "优先目录"
    if ($priorityValidCandidates.Count -gt 0) {
        Write-CachedJdkList -Candidates $priorityValidCandidates
        Print-AvailableJdkList -Candidates $priorityValidCandidates -SourceName "优先目录"
        Write-Info "已在优先目录找到可用Java25"
        return Select-BestJdk25Candidate -Candidates $priorityValidCandidates
    }

    $driveRoots = @(Get-DriveRoots)
    Write-Info ("优先目录未找到可用Java25，开始搜索Java，共{0}个" -f $driveRoots.Count)
    $driveCandidates = Search-Jdk25Candidates -Roots $driveRoots -StageName "搜索Java"
    Write-Info ("候选Java数量: {0}" -f $driveCandidates.Count)
    $allCandidates = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($item in $priorityCandidates) {
        [void]$allCandidates.Add($item)
    }
    foreach ($item in $driveCandidates) {
        [void]$allCandidates.Add($item)
    }
    Write-Info ("合并后候选Java数量: {0}" -f $allCandidates.Count)
    $validCandidates = Find-ValidJdk25Candidates -CandidateSet $allCandidates -StageName "最终校验"
    if ($validCandidates.Count -eq 0) {
        try {
            if (Test-Path -LiteralPath $jdkCacheFile -PathType Leaf) {
                Remove-Item -LiteralPath $jdkCacheFile -Force -ErrorAction Stop
                Write-Info "未找到可用Java25，已清理失效缓存"
            }
        } catch {
            Write-Info ("清理失效缓存失败: {0}" -f $_.Exception.Message)
        }
        return $null
    }
    Write-CachedJdkList -Candidates $validCandidates
    Print-AvailableJdkList -Candidates $validCandidates -SourceName "重新搜索"
    return Select-BestJdk25Candidate -Candidates $validCandidates
}

function Resolve-BestJdk25 {
    while ($true) {
        $bestJava = Find-BestJdk25
        if ($bestJava) {
            return $bestJava
        }

        Show-NoJavaOptions
        $choice = Read-Host "请输入选项"
        if ($choice -match '^[Rr]$') {
            Write-Info "用户选择重新搜索Java25"
            continue
        }

        $manualJava = Select-ManualJdk25
        if ($manualJava) {
            return $manualJava
        }

        Write-Info "手动选择未得到可用Java25，返回选择菜单"
    }
}

$bestJava = Resolve-BestJdk25
if (-not $bestJava) {
    Show-StartError "未找到可用的64位Java25。`r`n请先安装64位Java25，然后重新双击启动。"
    Wait-ForUserClose
    exit 1
}

$javawExe = Join-Path $bestJava.JavaHome "bin\javaw.exe"
if (-not (Test-Path -LiteralPath $javawExe -PathType Leaf)) {
    Show-StartError "找到的Java25缺少javaw.exe：`r`n$($bestJava.JavaHome)"
    Wait-ForUserClose
    exit 1
}

$libDir = Join-Path $PSScriptRoot "lib"
$mainJar = Join-Path $libDir "rdi-5-ui.jar"
if (-not (Test-Path -LiteralPath $libDir -PathType Container) -or -not (Test-Path -LiteralPath $mainJar -PathType Leaf)) {
    Show-StartError "启动目录缺少lib文件夹或lib\\rdi-5-ui.jar。`r`n请确认脚本位于完整发布目录中。"
    Wait-ForUserClose
    exit 1
}

$workingDir = $PSScriptRoot
$classpath = 'lib/*'
$arguments = @(
    '-Dfile.encoding=UTF-8',
    '-cp', $classpath,
    '--enable-native-access=ALL-UNNAMED',
    'calebxzhou.rdi.client.MainKt'
)

try {
    Write-Info ("将使用Java25启动: {0}" -f $bestJava.JavaHome)
    $process = Start-Process -FilePath $javawExe -WorkingDirectory $workingDir -ArgumentList $arguments -PassThru
    Write-Info ("已启动进程PID: {0}，正在观察5秒确认是否稳定启动" -f $process.Id)
    $exitedEarly = $process.WaitForExit(5000)
    if ($exitedEarly) {
        $message = "程序在启动后5秒内退出，视为启动失败。`r`n退出码: $($process.ExitCode)`r`nJava: $javawExe"
        Show-StartError $message
        Wait-ForUserClose
        exit 1
    }
    Write-Info "启动成功，5秒后关闭本窗口"
    Start-Sleep -Seconds 5
} catch {
    Show-StartError "启动失败。`r`nJava: $javawExe`r`n错误: $($_.Exception.Message)"
    Wait-ForUserClose
    exit 1
}
