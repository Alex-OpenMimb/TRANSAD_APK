# Ver logs de API de TRANSAD en terminal (requiere Android SDK / platform-tools).
#
# Uso:
#   .\scripts\ver-logs-api.ps1           # logcat en vivo (headers, body, respuesta)
#   .\scripts\ver-logs-api.ps1 -Archivo  # lee api.log del telefono formateado
#
param(
    [switch]$Archivo
)

$ErrorActionPreference = "Stop"

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

$package = "com.transad.app"
$remoteLog = "/storage/emulated/0/Android/data/$package/files/logs/api.log"

function Get-AdbLogLines {
    param([string]$Path)
    $raw = & $adb shell "cat `"$Path`" 2>/dev/null"
    if ($null -eq $raw) { return @() }
    if ($raw -is [System.Array]) {
        return @($raw | ForEach-Object { "$_".TrimEnd("`r") } | Where-Object { $_.Trim() -ne "" })
    }
    return @("$raw" -split "`n" | ForEach-Object { $_.TrimEnd("`r") } | Where-Object { $_.Trim() -ne "" })
}

function Write-ApiLogEntry {
    param([string]$Line)
    $trimmed = $Line.Trim()
    if ($trimmed -eq "" -or $trimmed.StartsWith("===")) { return }
    try {
        $e = $trimmed | ConvertFrom-Json
        $status = if ($e.error_message -and -not $e.status_code) { "ERROR" } else { "$($e.status_code)" }
        Write-Host ""
        Write-Host "── $($e.method) $status $($e.duration_ms)ms $($e.path) ──" -ForegroundColor Cyan
        Write-Host "URL: $($e.url)"
        if ($e.request_headers) {
            Write-Host "Headers:" -ForegroundColor Yellow
            $e.request_headers.PSObject.Properties | ForEach-Object {
                Write-Host "  $($_.Name): $($_.Value)"
            }
        }
        if ($e.request_body) {
            Write-Host "Request body:" -ForegroundColor Yellow
            Write-Host $e.request_body
        }
        if ($e.response_body) {
            Write-Host "Response body:" -ForegroundColor Green
            Write-Host $e.response_body
        }
        elseif ($e.status_code) {
            Write-Host "Response body: (vacío, HTTP $($e.status_code))" -ForegroundColor DarkGray
        }
        if ($e.error_message) {
            Write-Host "Error: $($e.error_message)" -ForegroundColor Red
        }
    }
    catch {
        Write-Host $Line
    }
}

if ($Archivo) {
    Write-Host "Leyendo $remoteLog (actualiza cada 1s · Ctrl+C para salir)..." -ForegroundColor Cyan
    $seenLines = 0
    while ($true) {
        $lines = Get-AdbLogLines -Path $remoteLog
        if ($lines.Count -gt $seenLines) {
            for ($i = $seenLines; $i -lt $lines.Count; $i++) {
                Write-ApiLogEntry -Line $lines[$i]
            }
            $seenLines = $lines.Count
        }
        Start-Sleep -Seconds 1
    }
}
else {
    Write-Host "Escuchando logcat TRANSAD_API (URL, headers, body, respuesta · Ctrl+C para salir)..." -ForegroundColor Cyan
    Write-Host "Tip: instala la app debug reciente. También puedes usar: .\scripts\ver-logs-api.ps1 -Archivo" -ForegroundColor DarkGray
    & $adb logcat -s TRANSAD_API
}
