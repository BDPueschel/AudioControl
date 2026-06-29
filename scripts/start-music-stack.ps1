<#
    start-music-stack.ps1

    Launches the laptop "music stack" at logon:
      1. AudioControl server (headless, controls the miniDSP)
      2. Qobuz
      3. miniDSP Device Console
      4. Synesthesia

    Idempotent: if the AudioControl server is already listening on its port,
    it is left alone. Safe to run by hand any time.

    Registered to run at logon via the "MusicStackStartup" scheduled task
    (see scripts/register-startup.ps1).
#>

$ErrorActionPreference = 'Continue'

# --- configuration (edit paths here if anything moves) ----------------------
$Qobuz           = "$env:LOCALAPPDATA\Qobuz\Qobuz.exe"
$DeviceConsole   = "$env:LOCALAPPDATA\Programs\minidsp-device-console\MiniDSP Device Console.exe"
$Synesthesia     = "${env:ProgramFiles(x86)}\Synesthesia\Synesthesia.exe"
$AudioControlDir = 'C:\Users\Brian\Documents\CodeProjects\AudioControl'
$Python          = 'C:\Users\Brian\AppData\Local\Programs\Python\Python313\python.exe'
$MinidspBin      = 'C:\Users\Brian\tools\minidsp\minidsp.exe'
$ServerPort      = 8080
$ServerLog       = Join-Path $AudioControlDir 'server.log'

function Launch($name, [scriptblock]$action) {
    try { & $action; Write-Host "[ok]   $name" }
    catch { Write-Host "[fail] $name : $_" }
}

# 1) AudioControl server — only if not already running on its port
Launch 'AudioControl server' {
    $busy = Get-NetTCPConnection -LocalPort $ServerPort -State Listen -ErrorAction SilentlyContinue
    if ($busy) {
        Write-Host "       server already running on port $ServerPort; skipping"
    } else {
        # cmd /c keeps it headless and redirects logs reliably.
        $line = "set `"AUDIOCONTROL_MINIDSP_BIN=$MinidspBin`" && `"$Python`" run.py > `"$ServerLog`" 2>&1"
        Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $line `
            -WorkingDirectory $AudioControlDir -WindowStyle Hidden
    }
}

# 2) Qobuz
Launch 'Qobuz' {
    if (Test-Path $Qobuz) { Start-Process $Qobuz }
    else { Write-Host "       not found at $Qobuz" }
}

# 3) miniDSP Device Console
Launch 'miniDSP Device Console' {
    if (Test-Path $DeviceConsole) { Start-Process $DeviceConsole }
    else { Write-Host "       not found at $DeviceConsole" }
}

# 4) Synesthesia
Launch 'Synesthesia' {
    if (Test-Path $Synesthesia) { Start-Process $Synesthesia }
    else { Write-Host "       not found at $Synesthesia" }
}

Write-Host 'Music stack launch complete.'
