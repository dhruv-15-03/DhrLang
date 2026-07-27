<#
.SYNOPSIS
    Reproduces the DhrLang LSP demo transcript in demo/README.md.

.DESCRIPTION
    Sends the framed JSON-RPC requests in demo/requests.jsonl (initialize,
    didOpen of demo/Demo.dhr, hover, definition, references, prepareRename,
    rename, general completion, and receiver-aware dot completion) to a real
    `java -jar DhrLang.jar --lsp` process over stdio, and prints the raw
    responses/notifications the server returns.

    This is the SAME LSP server the VS Code extension (vscode-extension/)
    spawns via LanguageClient — this script just talks to it directly so the
    request/response transcript can be inspected and reproduced without an
    editor.

.PARAMETER JarPath
    Path to the built DhrLang.jar. Defaults to build/libs/DhrLang.jar
    (the standard Gradle `shadowJar`/`jar` output location).

.PARAMETER JavaPath
    Path to a java executable. Defaults to "java" (resolved via PATH).

.EXAMPLE
    ./gradlew shadowJar
    pwsh demo/run-demo.ps1
#>
param(
    [string]$JarPath = (Join-Path $PSScriptRoot "..\build\libs\DhrLang.jar"),
    [string]$JavaPath = "java"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $JarPath)) {
    Write-Error "DhrLang.jar not found at '$JarPath'. Build it first, e.g.: ./gradlew shadowJar"
    exit 1
}

$requestsFile = Join-Path $PSScriptRoot "requests.jsonl"
$fixtureFile = Join-Path $PSScriptRoot "Demo.dhr"
if (-not (Test-Path $requestsFile)) { Write-Error "Missing $requestsFile"; exit 1 }
if (-not (Test-Path $fixtureFile)) { Write-Error "Missing $fixtureFile"; exit 1 }

# Frame every JSON-RPC message from requests.jsonl with an LSP Content-Length header.
$sb = New-Object System.Text.StringBuilder
foreach ($line in Get-Content $requestsFile) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $byteCount = [System.Text.Encoding]::UTF8.GetByteCount($line)
    [void]$sb.Append("Content-Length: $byteCount`r`n`r`n$line")
}

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $JavaPath
$psi.Arguments = "-jar `"$JarPath`" --lsp"
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$psi.UseShellExecute = $false
$proc = [System.Diagnostics.Process]::Start($psi)

$inputBytes = [System.Text.Encoding]::UTF8.GetBytes($sb.ToString())
$proc.StandardInput.BaseStream.Write($inputBytes, 0, $inputBytes.Length)
$proc.StandardInput.BaseStream.Flush()
$proc.StandardInput.Close()

$stdout = $proc.StandardOutput.ReadToEnd()
$stderr = $proc.StandardError.ReadToEnd()
$proc.WaitForExit(30000) | Out-Null

Write-Host "----- LSP server responses/notifications -----"
Write-Host $stdout
if ($stderr) {
    Write-Host "----- stderr -----"
    Write-Host $stderr
}
