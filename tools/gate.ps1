<#
  gate.ps1 - Aggregate G0-G3 gate runner for the 26.1.2 port.

  Runs every check even when an earlier one fails, then prints one summary
  table. Gradle output goes to tools-logs\*.log so the caller only ever reads
  the summary. See docs/porting/26.1.2/TEST_GATES.md for what each gate means.

  Usage:
    .\tools\gate.ps1                    # G0 + G1 + G2   (every slice)
    .\tools\gate.ps1 -Level milestone   # + G3 artifact/ABI smoke
    .\tools\gate.ps1 -Level release     # + jar hash record
#>
[CmdletBinding()]
param(
    [ValidateSet('slice','milestone','release')][string]$Level = 'slice',
    [switch]$SkipGradle
)

$ErrorActionPreference = 'Continue'
$repoRoot = Split-Path -Parent $PSScriptRoot
$logDir   = Join-Path $repoRoot 'tools-logs'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
Set-Location $repoRoot

. "$PSScriptRoot\lib-env.ps1"
$jdk = Use-Jdk25
Write-Host "[gate] JAVA_HOME=$jdk"
$runStart = Get-Date

$results = New-Object System.Collections.ArrayList
function Add-Result {
    param([string]$Gate, [string]$Check, [bool]$Ok, [string]$Detail = '')
    [void]$results.Add([pscustomobject]@{ Gate = $Gate; Check = $Check; Ok = $Ok; Detail = $Detail })
    $mark = if ($Ok) { 'PASS' } else { 'FAIL' }
    $color = if ($Ok) { 'Green' } else { 'Red' }
    Write-Host ("  [{0}] {1,-32} {2}" -f $mark, $Check, $Detail) -ForegroundColor $color
}

function Invoke-Gradle {
    param([string]$Name, [string[]]$Tasks)
    $log = Join-Path $logDir "$Name.log"
    $args = $Tasks + @('--no-daemon', '--console=plain')
    Write-Host "  running: gradlew $($args -join ' ')  -> $log"
    $p = Start-Process -FilePath (Join-Path $repoRoot 'gradlew.bat') -ArgumentList $args `
                       -WorkingDirectory $repoRoot -RedirectStandardOutput $log `
                       -RedirectStandardError "$log.err" -NoNewWindow -PassThru -Wait

    $reason = ''
    if ($p.ExitCode -ne 0) {
        foreach ($f in @("$log.err", $log)) {
            if (-not (Test-Path $f)) { continue }
            $m = Select-String -Path $f -Pattern 'What went wrong' -Context 0, 2 | Select-Object -First 1
            if ($m) { $reason = ($m.Context.PostContext -join ' ').Trim(); break }
            $e = Select-String -Path $f -Pattern '^\s*(error|e:):' | Select-Object -First 1
            if ($e) { $reason = $e.Line.Trim(); break }
        }
        if ($reason.Length -gt 160) { $reason = $reason.Substring(0, 160) + '...' }
    }
    return [pscustomobject]@{ ExitCode = $p.ExitCode; Log = $log; Reason = $reason }
}

Write-Host "== G0 change hygiene ==" -ForegroundColor Cyan

# Note: git prints CRLF "warning:" lines on stderr; they are not diff problems.
$diffCheck = @(& git diff --check 2>&1 | Where-Object { $_ -notmatch '^warning:' })
Add-Result 'G0' 'git diff --check' ($diffCheck.Count -eq 0) ("{0} line(s)" -f $diffCheck.Count)

$status = @(& git status --short)
Add-Result 'G0' 'git status --short empty' ($status.Count -eq 0) ("{0} entr(y|ies)" -f $status.Count)

# Main sources only: the contract tests legitimately name the donor identity in
# their "must not appear" assertions.
$leaks = @(& git grep -n -i 'sparkle_morpher' -- 'common/src/main' 'fabric/src/main' 'gradle.properties' 2>$null)
Add-Result 'G0' 'no sparkle_morpher in sources' ($leaks.Count -eq 0) ("{0} hit(s)" -f $leaks.Count)

$donorPkg = @(& git grep -n 'com\.micaftic' -- 'common/src' 'fabric/src' 2>$null)
Add-Result 'G0' 'no donor package refs' ($donorPkg.Count -eq 0) ("{0} hit(s)" -f $donorPkg.Count)

# Match the excluded PATTERNS, not the number of exclude statements: `java.exclude 'a', 'b'`
# and the block form `java { exclude 'x' }` both widen the set without adding a second
# `java.exclude` token, so counting statements would let the widening CLAUDE.md forbids
# through. The one sanctioned exclusion is the frozen TLM tree.
$buildGradle = Get-Content (Join-Path $repoRoot 'fabric\build.gradle') -Raw
$excludePatterns = @([regex]::Matches($buildGradle, "exclude\s+((?:'[^']*'|""[^""]*"")(?:\s*,\s*(?:'[^']*'|""[^""]*""))*)") |
    ForEach-Object { $_.Groups[1].Value -split '\s*,\s*' } |
    ForEach-Object { $_.Trim("'", '"') })
$sanctioned = @('rip/ysm/compat/touhoulittlemaid/fabric/tlm/**')
$unexpected = @($excludePatterns | Where-Object { $sanctioned -notcontains $_ })
Add-Result 'G0' 'source exclusions == 1 (TLM)' ($excludePatterns.Count -eq 1 -and $unexpected.Count -eq 0) `
    ("{0} pattern(s){1}" -f $excludePatterns.Count, $(if ($unexpected.Count) { ": unexpected " + ($unexpected -join ', ') } else { '' }))

$junk = @(Get-ChildItem -Path (Join-Path $repoRoot 'common\src'), (Join-Path $repoRoot 'fabric\src') -Recurse -File -ErrorAction SilentlyContinue |
          Where-Object { $_.Extension -in '.orig', '.rej', '.bak' -or ($_.Extension -eq '.java' -and $_.Length -eq 0) })
Add-Result 'G0' 'no backup/empty source files' ($junk.Count -eq 0) ("{0} found" -f $junk.Count)

if (-not $SkipGradle) {
    Write-Host "== G1 JVM contract tests ==" -ForegroundColor Cyan
    # cleanTest first: without it Gradle reports the test tasks UP-TO-DATE, writes no new
    # XML, and the tally below would be satisfied by the PREVIOUS run's results - a gate
    # that passes while nothing ran. This gate's PASS is recorded as evidence, so it has to
    # mean "these tests executed just now".
    $g1 = Invoke-Gradle -Name 'gate-g1-test' -Tasks @(':common:cleanTest', ':fabric:cleanTest', ':common:test', ':fabric:test')
    $g1Ok = $g1.ExitCode -eq 0
    $xml = @(Get-ChildItem -Path (Join-Path $repoRoot 'common\build\test-results\test'), (Join-Path $repoRoot 'fabric\build\test-results\test') -Filter 'TEST-*.xml' -Recurse -ErrorAction SilentlyContinue)
    $freshXml = @($xml | Where-Object { $_.LastWriteTime -ge $runStart })
    # Tally ONLY this run's files; stale results must never contribute.
    $testCount = 0; $failCount = 0
    foreach ($f in $freshXml) {
        try {
            [xml]$doc = Get-Content $f.FullName -Raw
            $testCount += [int]$doc.testsuite.tests
            $failCount += [int]$doc.testsuite.failures + [int]$doc.testsuite.errors
        } catch { }
    }
    Add-Result 'G1' 'gradle :common:test :fabric:test' $g1Ok ("exit {0} {1}" -f $g1.ExitCode, $g1.Reason)
    Add-Result 'G1' 'tests actually executed' ($freshXml.Count -gt 0 -and $testCount -gt 0 -and $failCount -eq 0) ("{0} tests, {1} failed, {2} result files from this run (of {3} on disk)" -f $testCount, $failCount, $freshXml.Count, $xml.Count)

    Write-Host "== G2 compile and package ==" -ForegroundColor Cyan
    $g2 = Invoke-Gradle -Name 'gate-g2-build' -Tasks @(':fabric:build')
    Add-Result 'G2' 'gradle :fabric:build' ($g2.ExitCode -eq 0) ("exit {0} {1}" -f $g2.ExitCode, $g2.Reason)

    if ($Level -in 'milestone', 'release') {
        Write-Host "== G3 artifact and ABI smoke ==" -ForegroundColor Cyan
        $g3 = Invoke-Gradle -Name 'gate-g3-jar' -Tasks @(':fabric:verifyReleaseJar')
        $detail = ''
        if (Test-Path $g3.Log) {
            $line = Select-String -Path $g3.Log -Pattern 'verifyReleaseJar OK' | Select-Object -Last 1
            if ($line) { $detail = $line.Line.Trim() }
            if ($g3.ExitCode -ne 0) {
                $err = Select-String -Path $g3.Log -Pattern 'verifyReleaseJar failed' -Context 0, 8 | Select-Object -First 1
                if ($err) { $detail = ($err.Line + ' ' + ($err.Context.PostContext -join ' ')).Trim() }
            }
        }
        Add-Result 'G3' 'verifyReleaseJar' ($g3.ExitCode -eq 0) $detail

        $jar = Get-ChildItem (Join-Path $repoRoot 'fabric\build\libs') -Filter 'openysm-fabric-*.jar' -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -notlike '*dev-shadow*' -and $_.Name -notlike '*sources*' } |
               Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($jar) {
            $hash = (Get-FileHash $jar.FullName -Algorithm SHA256).Hash
            Add-Result 'G3' 'release jar present' $true ("{0} {1} bytes" -f $jar.Name, $jar.Length)
            Write-Host "  SHA-256: $hash"
            if ($Level -eq 'release') {
                $rec = Join-Path $logDir 'release-jar.txt'
                "$($jar.Name)`n$($jar.Length) bytes`nSHA-256 $hash`n$(Get-Date -Format o)" | Set-Content -Path $rec -Encoding ASCII
                Write-Host "  recorded -> $rec"
            }
        } else {
            Add-Result 'G3' 'release jar present' $false 'no openysm-fabric-*.jar in fabric/build/libs'
        }
    }
}

Write-Host ''
Write-Host '== SUMMARY ==' -ForegroundColor Cyan
$results | ForEach-Object {
    $mark = if ($_.Ok) { 'PASS' } else { 'FAIL' }
    Write-Host ("  {0}  {1,-4} {2,-34} {3}" -f $mark, $_.Gate, $_.Check, $_.Detail)
}
$failed = @($results | Where-Object { -not $_.Ok })
Write-Host ''
if ($failed.Count -eq 0) {
    Write-Host ("GATE {0}: ALL {1} CHECKS PASSED" -f $Level.ToUpper(), $results.Count) -ForegroundColor Green
    exit 0
} else {
    Write-Host ("GATE {0}: {1} of {2} CHECKS FAILED" -f $Level.ToUpper(), $failed.Count, $results.Count) -ForegroundColor Red
    exit 1
}
