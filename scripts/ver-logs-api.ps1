# Ver logs TRANSAD_API en terminal (requiere Android SDK / platform-tools).
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Host "No se encontró adb en:" $adb
    Write-Host ""
    Write-Host "Opciones:"
    Write-Host "  1. Instala Android Studio y platform-tools."
    Write-Host "  2. En la app: menú lateral -> Logs API (desarrollo)."
    Write-Host "  3. Archivo en el teléfono: Android/data/com.transad.app/files/logs/api.log"
    exit 1
}
Write-Host "Escuchando logs de API (Ctrl+C para salir)..."
& $adb logcat -s TRANSAD_API
