<#
  logscan.ps1 - Summarise a game/server log without dumping it.

  Prints one line per error class with a count, then at most -Show lines of
  evidence each. Keeps acceptance runs readable and context-cheap.

  Usage:
    .\tools\logscan.ps1 -Path tools-logs\server.log
    .\tools\logscan.ps1 -Path tools-logs\alice.log -Show 3 -Extra 'Handshake complete','Sync state'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Path,
    [int]$Show = 5,
    [string[]]$Extra = @()
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Path)) { Write-Error "log not found: $Path"; exit 2 }

$lines = Get-Content $Path -ErrorAction SilentlyContinue
Write-Host ("[logscan] {0}  ({1} lines)" -f $Path, $lines.Count)

$patterns = [ordered]@{
    'mixin-apply'   = 'Mixin apply|mixin\.injection\.throwables|InvalidInjectionException|MixinApplyError'
    'classloading'  = 'ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|NoSuchFieldError|IncompatibleClassChangeError'
    'registry'      = 'registry.*(error|failed)|Registry.*Exception|Missing.*registry'
    'native'        = 'UnsatisfiedLinkError|native.*(fail|error)|ysm-core.*(fail|error)'
    'render'        = 'RenderSystem|GL_INVALID|OpenGL error|Failed to render|SubmitNode'
    'exception'     = 'Exception|Throwable|Caused by:'
    'error-level'   = '\[.*\/ERROR\]|/FATAL\]'
}

$anyBad = $false
foreach ($name in $patterns.Keys) {
    $hits = @($lines | Select-String -Pattern $patterns[$name] -AllMatches)
    if ($hits.Count -gt 0) {
        $anyBad = $true
        Write-Host ("  {0,-14} {1}" -f $name, $hits.Count) -ForegroundColor Yellow
        $hits | Select-Object -First $Show | ForEach-Object {
            $t = $_.Line.Trim()
            if ($t.Length -gt 200) { $t = $t.Substring(0, 200) + '...' }
            Write-Host "      $t"
        }
    } else {
        Write-Host ("  {0,-14} 0" -f $name)
    }
}

foreach ($e in $Extra) {
    $hits = @($lines | Select-String -SimpleMatch $e)
    Write-Host ("  [extra] {0,-24} {1}" -f $e, $hits.Count)
    $hits | Select-Object -Last $Show | ForEach-Object {
        $t = $_.Line.Trim()
        if ($t.Length -gt 200) { $t = $t.Substring(0, 200) + '...' }
        Write-Host "      $t"
    }
}

if ($anyBad) { Write-Host "[logscan] issues present (see counts above)" } else { Write-Host "[logscan] clean" }
