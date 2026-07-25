$desktop = [Environment]::GetFolderPath('Desktop')
$path = Join-Path $desktop 'RDI.lnk'
$ws = New-Object -ComObject WScript.Shell
$lnk = $ws.CreateShortcut($path)
$lnk.TargetPath = '__JAVAW__'
$lnk.Arguments = '__ARGS__'
$lnk.WorkingDirectory = '__WORKDIR__'
$lnk.IconLocation = '__ICON__'
$lnk.Save()
