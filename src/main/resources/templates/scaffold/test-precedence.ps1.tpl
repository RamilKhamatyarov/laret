# Config precedence: CLI flag > ENV var > config file > default
$ErrorActionPreference = 'Stop'

$testDir = New-Item -ItemType Directory -Path (Join-Path $env:TEMP "${appName}-precedence-$([guid]::NewGuid())")
try {
    Push-Location $testDir
    @'
greeting:
  name: "from-file"
'@ | Set-Content -Path ".${appName}.yml" -NoNewline

    $env:${envPrefix}_GREETING_NAME = "from-env"

    $output = & ${appName} hello run --name "from-flag" 2>&1 | Out-String

    if ($output -match "Hello, from-flag!") {
        Write-Host "PASS: CLI flag wins over ENV and FILE"
        Write-Host "Precedence test passed"
        exit 0
    }

    Write-Host "FAIL: CLI flag should win"
    Write-Host "Output: $output"
    exit 1
} finally {
    Pop-Location
    Remove-Item -Recurse -Force $testDir
}
