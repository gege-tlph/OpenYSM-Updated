<#
  stop.ps1 - Stop dev clients/servers started by play.ps1.

  Usage:
    .\tools\stop.ps1 -Token run-client-a      # one client
    .\tools\stop.ps1 -All                     # every dev java process of this repo
    .\tools\stop.ps1 -Server                  # dedicated server (graceful via RCON, then kill)
#>
[CmdletBinding()]
param(
    [string]$Token = '',
    [switch]$All,
    [switch]$Server,
    [string]$RconPassword = 'ysmgate',
    [int]$RconPort = 25575
)

$ErrorActionPreference = 'Continue'
$repoRoot = Split-Path -Parent $PSScriptRoot

if ($Server) {
    try {
        & "$PSScriptRoot\rcon.ps1" -Command 'stop' -Password $RconPassword -Port $RconPort -Quiet | Out-Null
        Write-Host '[stop] sent rcon stop; waiting for shutdown'
        Start-Sleep -Seconds 8
    } catch {
        Write-Host "[stop] rcon stop failed: $($_.Exception.Message)"
    }
}

$filters = @()
if ($Token -ne '') { $filters += $Token }
if ($All) { $filters += 'run-client-', 'openysm' }
if ($Server -and -not $All) { $filters += 'runServer' }
if ($filters.Count -eq 0) { Write-Error 'pass -Token, -Server or -All'; exit 2 }

$procs = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $c = $_.CommandLine; $c -and ($filters | Where-Object { $c -like "*$_*" }) })

foreach ($p in $procs) {
    try {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
        Write-Host "[stop] killed pid $($p.ProcessId)"
    } catch {
        Write-Host "[stop] could not kill pid $($p.ProcessId): $($_.Exception.Message)"
    }
}
if ($procs.Count -eq 0) { Write-Host '[stop] nothing matched' }
