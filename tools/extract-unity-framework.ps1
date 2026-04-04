param(
    [Parameter(Mandatory = $true)]
    [string]$InputFile,

    [Parameter(Mandatory = $true)]
    [string]$OutputFile
)

$raw = Get-Content -Path $InputFile -Raw
$startToken = "var unityFramework = ( () => {"
$endToken = "assertive.analytics.override = assertive.analytics.override || {};"

$start = $raw.IndexOf($startToken)
$end = $raw.IndexOf($endToken)

if ($start -lt 0) {
    throw "Could not find Unity runtime start token."
}

if ($end -lt 0 -or $end -le $start) {
    throw "Could not find Unity runtime end token."
}

$unityRuntime = $raw.Substring($start, $end - $start)
$unityRuntime = $unityRuntime.TrimEnd()

Set-Content -Path $OutputFile -Value $unityRuntime -Encoding UTF8
Write-Output "Extracted Unity runtime to $OutputFile"

