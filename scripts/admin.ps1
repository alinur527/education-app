param(
    [Parameter(Mandatory=$true,Position=0)][ValidateSet('promote')][string]$Action,
    [Parameter(Mandatory=$true,Position=1)][string]$Email
)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskPython=Join-Path $taskRoot '.venv/Scripts/python.exe'
if (Test-Path -LiteralPath $taskPython) {
    & $taskPython -X utf8 (Join-Path $PSScriptRoot 'admin_operator.py') $Action $Email
} else {
    & python -X utf8 (Join-Path $PSScriptRoot 'admin_operator.py') $Action $Email
}
exit $LASTEXITCODE
