<#
  rcon.ps1 - Minimal Source-RCON client, used to drive the dedicated server
  during acceptance runs (op / gamemode / ysm model set / ysm play ...).

  Usage:
    .\tools\rcon.ps1 -Command 'list'
    .\tools\rcon.ps1 -Command 'ysm model set Alice "misc/1_alex" gsl'
    .\tools\rcon.ps1 -Command @('op Alice','gamemode creative Alice')
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]]$Command,
    [string]$RconHost = '127.0.0.1',
    [int]$Port        = 25575,
    [string]$Password = 'ysmgate',
    [int]$TimeoutMs   = 8000,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$SERVERDATA_AUTH        = 3
$SERVERDATA_EXECCOMMAND = 2

function Write-RconPacket {
    param($Stream, [int]$Id, [int]$Type, [string]$Body)
    # Build the whole frame first: Minecraft's RCON listener parses one packet per
    # read, so a frame split across TCP writes makes it drop the connection.
    $bodyBytes = [System.Text.Encoding]::ASCII.GetBytes($Body)
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    $bw.Write([int](10 + $bodyBytes.Length))
    $bw.Write([int]$Id)
    $bw.Write([int]$Type)
    $bw.Write($bodyBytes)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Flush()
    $frame = $ms.ToArray()
    $Stream.Write($frame, 0, $frame.Length)
    $Stream.Flush()
}

function Read-Exactly {
    param($Stream, [int]$Count)
    $buf = New-Object byte[] $Count
    $off = 0
    while ($off -lt $Count) {
        $n = $Stream.Read($buf, $off, $Count - $off)
        if ($n -le 0) { throw "connection closed after $off/$Count bytes" }
        $off += $n
    }
    return $buf
}

function Read-RconPacket {
    param($Stream)
    $lenBytes = Read-Exactly -Stream $Stream -Count 4
    $len = [BitConverter]::ToInt32($lenBytes, 0)
    $rest = Read-Exactly -Stream $Stream -Count $len
    $id   = [BitConverter]::ToInt32($rest, 0)
    $type = [BitConverter]::ToInt32($rest, 4)
    $body = [System.Text.Encoding]::UTF8.GetString($rest, 8, [Math]::Max(0, $len - 10))
    return [pscustomobject]@{ Id = $id; Type = $type; Body = $body.TrimEnd([char]0) }
}

$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.Connect($RconHost, $Port)
    $client.ReceiveTimeout = $TimeoutMs
    $client.SendTimeout    = $TimeoutMs
    $stream = $client.GetStream()

    Write-RconPacket -Stream $stream -Id 1 -Type $SERVERDATA_AUTH -Body $Password
    $auth = Read-RconPacket -Stream $stream
    if ($auth.Id -eq -1) { Write-Error '[rcon] authentication failed'; exit 3 }
    if (-not $Quiet) { Write-Host '[rcon] authenticated' }

    $i = 10
    foreach ($cmd in $Command) {
        Write-RconPacket -Stream $stream -Id $i -Type $SERVERDATA_EXECCOMMAND -Body $cmd
        $resp = Read-RconPacket -Stream $stream
        $text = $resp.Body.Trim()
        if (-not $Quiet) {
            Write-Host ("[rcon] > {0}" -f $cmd)
            if ($text -ne '') { Write-Host ("[rcon] < {0}" -f $text) }
        }
        Write-Output $text
        $i++
        Start-Sleep -Milliseconds 200
    }
} finally {
    if ($client) { $client.Close() }
}
