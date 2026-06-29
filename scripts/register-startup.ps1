<#
    register-startup.ps1

    Registers (or refreshes) the "MusicStackStartup" scheduled task so the
    music stack launches ~15s after you log in. Run once. No admin required
    (it is a per-user logon task). To remove:
        Unregister-ScheduledTask -TaskName MusicStackStartup -Confirm:$false
#>

$script = Join-Path $PSScriptRoot 'start-music-stack.ps1'

$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`""

$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$trigger.Delay = 'PT15S'   # let the desktop settle first

$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask -TaskName 'MusicStackStartup' `
    -Action $action -Trigger $trigger -Settings $settings `
    -Description 'Launches the music stack (AudioControl server, Qobuz, miniDSP Device Console, Synesthesia) at logon.' `
    -Force | Out-Null

Write-Host 'Registered scheduled task: MusicStackStartup (runs ~15s after logon).'
