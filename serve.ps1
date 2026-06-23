# Same as: npx playwright-viewer serve
$ErrorActionPreference = "Stop"
$projectDir = $PSScriptRoot
$reportDir = Join-Path $projectDir "custom-report"
$mvn = "C:\Maven\bin\mvn.cmd"
$port = 4173

if (-not (Test-Path $mvn)) {
  Write-Error "Maven not found at $mvn"
}

4173..4177 | ForEach-Object {
  Get-NetTCPConnection -LocalPort $_ -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}

Start-Sleep -Seconds 1

if (-not (Test-Path $reportDir)) {
  Write-Host "No reports yet. Running tests first..."
  Set-Location $projectDir
  & $mvn clean test "-Dtestreport.open=never"
}

Write-Host ""
Write-Host "Starting testreport viewer (npx playwright-viewer serve)"
Write-Host "Report folder: $reportDir"
Write-Host ""

Set-Location $projectDir
& $mvn exec:java `
  "-Dexec.classpathScope=test" `
  "-Dexec.args=serve --port $port --report-dir `"$reportDir`""
