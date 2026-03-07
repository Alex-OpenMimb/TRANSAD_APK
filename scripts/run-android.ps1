# Script para levantar el emulador e instalar la app (Windows)
# Uso: ejecutar desde la raíz del proyecto, o que la tarea de VS Code lo llame

$ErrorActionPreference = "Stop"

# Buscar Android SDK
$sdkPath = $env:ANDROID_HOME
if (-not $sdkPath) {
    $sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
}
if (-not (Test-Path $sdkPath)) {
    Write-Host "No se encontró Android SDK. Configura ANDROID_HOME o instala Android Studio." -ForegroundColor Red
    exit 1
}

$emulator = Join-Path $sdkPath "emulator\emulator.exe"
$adb = Join-Path $sdkPath "platform-tools\adb.exe"

if (-not (Test-Path $emulator)) {
    Write-Host "No se encontró emulator.exe en: $emulator" -ForegroundColor Red
    exit 1
}

# Listar AVDs y tomar el primero
$avds = & $emulator -list-avds 2>&1
if (-not $avds -or $avds -match "error") {
    Write-Host "No hay emuladores (AVD) configurados. Crea uno en Android Studio (AVD Manager)." -ForegroundColor Red
    exit 1
}

$avdName = ($avds | Select-Object -First 1).Trim()
Write-Host "Iniciando emulador: $avdName" -ForegroundColor Cyan

# Iniciar emulador en segundo plano
$emulatorProcess = Start-Process -FilePath $emulator -ArgumentList "-avd", $avdName -PassThru -WindowStyle Normal

# Esperar a que el dispositivo aparezca en adb
Write-Host "Esperando a que el emulador arranque (puede tardar 1-2 minutos)..." -ForegroundColor Yellow
& $adb wait-for-device

# Esperar a que termine de arrancar (boot completed)
$bootOk = $false
for ($i = 0; $i -lt 60; $i++) {
    $boot = & $adb shell getprop sys.boot_completed 2>$null
    if ($boot -eq "1") {
        $bootOk = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $bootOk) {
    Write-Host "El emulador tarda mucho. Puedes instalar la app más tarde con Ctrl+Shift+B." -ForegroundColor Yellow
}

# Ir a la carpeta del proyecto (raíz, donde está gradlew.bat)
# Si el script está en scripts/run-android.ps1, la raíz es el padre de scripts
$projectRoot = (Split-Path -Parent $PSScriptRoot)
if ($env:WORKSPACE_FOLDER) {
    $projectRoot = $env:WORKSPACE_FOLDER
}
Set-Location $projectRoot

# Para que Gradle encuentre el SDK (igual que el emulador)
$env:ANDROID_HOME = $sdkPath

Write-Host "Instalando la app en el emulador..." -ForegroundColor Cyan
& .\gradlew.bat installDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "Abriendo la app TRANSAD en el emulador..." -ForegroundColor Cyan
    & $adb shell am start -n com.transad.app/.LoginActivity
    Write-Host "Listo. La app debería estar abierta en el emulador." -ForegroundColor Green
} else {
    Write-Host "Error al instalar. Revisa el mensaje de arriba." -ForegroundColor Red
    exit 1
}
