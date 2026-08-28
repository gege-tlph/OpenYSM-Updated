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
    [int]$TimeoutMs   = 8000
)

$ErrorActionPreference = 'Stop'

$SERVERDATA_AUTH          = 3
$SERVERDATA_EXECCOMMAND   = 2

function Write-RconPacket {
    param($Stream, [int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::ASCII.GetBytes($Body)
    $len = 4 + 4 + $bodyBytes.Length + 2
    $bw = New-Object System.IO.BinaryWriter($Stream)
    $bw.Write([int]$len)
    $bw.Write([int]$Id)
    $bw.Write([int]$Type)
    $bw.Write($bodyBytes)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Flush()
}

function Read-RconPacket {
    param($Stream)
    $br = New-Object System.IO.BinaryReader($Stream)
    $len = $br.ReadInt32()
    $id  = $br.ReadInt32()
    $type = $br.ReadInt32()
    $payload = $br.ReadBytes($len - 8)
    $body = [System.Text.Encoding]::ASCII.GetString($payload).TrimEnd([char]0)
    return [pscustomobject]@{ Id = $id; Type = $type; Body = $body }
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
    Write-Host '[rcon] authenticated'

    $i = 10
    foreach ($cmd in $Command) {
        Write-RconPacket -Stream $stream -Id $i -Type $SERVERDATA_EXECCOMMAND -Body $cmd
        $resp = Read-RconPacket -Stream $stream
        $text = $resp.Body.Trim()
        Write-Host ("[rcon] > {0}" -f $cmd)
        if ($text -ne '') { Write-Host ("[rcon] < {0}" -f $text) }
        Write-Output $text
        $i++
        Start-Sleep -Milliseconds 200
    }
} finally {
    if ($client) { $client.Close() }
}
