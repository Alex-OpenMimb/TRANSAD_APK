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

# Ir SIEMPRE a la carpeta del proyecto TRANSAD_APK (donde está este script).
# Así aunque tengas abierto el padre (TRANSAD) o otro proyecto Titanium, se compila e instala esta app.
$projectRoot = (Split-Path -Parent $PSScriptRoot)
# Solo usar WORKSPACE_FOLDER si es exactamente esta carpeta (evita que sea la carpeta padre)
if ($env:WORKSPACE_FOLDER -and (Test-Path (Join-Path $env:WORKSPACE_FOLDER "app\build.gradle.kts"))) {
    $projectRoot = $env:WORKSPACE_FOLDER
}
Set-Location $projectRoot
Write-Host "Proyecto: $projectRoot" -ForegroundColor Gray

# Para que Gradle encuentre el SDK (igual que el emulador)
$env:ANDROID_HOME = $sdkPath

Write-Host "Instalando la app en el emulador..." -ForegroundColor Cyan
& .\gradlew.bat installDebug

if ($LASTEXITCODE -eq 0) {
    $package = "com.transad.app"
    Write-Host "Abriendo la app TRANSAD en el emulador..." -ForegroundColor Cyan
    & $adb shell am force-stop $package 2>$null
    $startResult = & $adb shell am start -n "${package}/.LoginActivity" -a android.intent.action.MAIN 2>&1
    if ($startResult -match "Error") {
        Write-Host $startResult -ForegroundColor Red
    } else {
        Write-Host "Listo. Si la app se cierra sola, en 4 s se mostrará el logcat del fallo..." -ForegroundColor Green
        Start-Sleep -Seconds 4
        Write-Host "--- Logcat (errores / crash de la app) ---" -ForegroundColor Yellow
        $log = & $adb logcat -d -t 300 2>$null
        $log | Select-String -Pattern "FATAL|Exception|at com\.transad|AndroidRuntime" -Context 0,1 | ForEach-Object { $_.Line; if ($_.Context.PostContext) { $_.Context.PostContext } }
        Write-Host "--- Fin logcat ---" -ForegroundColor Yellow
    }
} else {
    Write-Host "Error al instalar. Revisa el mensaje de arriba." -ForegroundColor Red
    exit 1
}
