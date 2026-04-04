param(
    [Parameter(Mandatory = $true)]
    [string]$InputFile,

    [Parameter(Mandatory = $true)]
    [string]$OutputFile,

    [string]$Wat2WasmPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-Wat2WasmPath {
    param([string]$PreferredPath)

    if ($PreferredPath) {
        if (-not (Test-Path -LiteralPath $PreferredPath)) {
            throw "wat2wasm.exe was not found at '$PreferredPath'."
        }
        return (Resolve-Path -LiteralPath $PreferredPath).Path
    }

    $command = Get-Command wat2wasm.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $candidates = Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA "wabt-py\wabt-py") -Filter wat2wasm.exe -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending

    if ($candidates) {
        return $candidates[0].FullName
    }

    throw "Could not find wat2wasm.exe. Install WABT or pass -Wat2WasmPath explicitly."
}

$resolvedInput = (Resolve-Path -LiteralPath $InputFile).Path
$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputFile)
$resolvedWat2Wasm = Resolve-Wat2WasmPath -PreferredPath $Wat2WasmPath

$source = [System.IO.File]::ReadAllText($resolvedInput)
$source = $source.TrimEnd()

if ($source.EndsWith(")vv")) {
    $source = $source.Substring(0, $source.Length - 2)
}

$importPattern = '^(\\s*)\\(func\\s+(.*?)\\s+\\(import\\s+"([^"]+)"\\s+"([^"]+)"\\)(.*)\\)$'
$normalizedSource = [System.Text.RegularExpressions.Regex]::Replace(
    $source,
    $importPattern,
    '$1(import "$3" "$4" (func $2$5))',
    [System.Text.RegularExpressions.RegexOptions]::Multiline
)

$tempWat = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "crimehunter-rebuilt.wat")

try {
    [System.IO.File]::WriteAllText($tempWat, $normalizedSource, [System.Text.UTF8Encoding]::new($false))

    & $resolvedWat2Wasm --enable-function-references $tempWat -o $resolvedOutput
    if ($LASTEXITCODE -ne 0) {
        throw "wat2wasm.exe failed with exit code $LASTEXITCODE."
    }

    Write-Output "Compiled Unity wasm to $resolvedOutput"
} finally {
    if (Test-Path -LiteralPath $tempWat) {
        Remove-Item -LiteralPath $tempWat -Force
    }
}
