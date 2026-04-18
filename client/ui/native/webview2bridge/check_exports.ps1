$dll = Join-Path $PSScriptRoot "out\rdi_webview2_bridge.dll"
$objdump = "C:\Users\calebxzhou\mingw64\bin\objdump.exe"

Write-Host "=== Export Table ==="
& $objdump -p $dll 2>&1 | Select-String -Pattern "rdi_webview2|Export Table|DLL Name|Ordinal"
Write-Host ""
Write-Host "=== All exported functions ==="
& $objdump -p $dll 2>&1 | Select-String "\[" -Context 0,0
Write-Host ""
Write-Host "=== nm (no flags) ==="
$nm = "C:\Users\calebxzhou\mingw64\bin\nm.exe"
& $nm $dll 2>&1 | Select-String "rdi_webview2"
Write-Host "=== Done ==="
