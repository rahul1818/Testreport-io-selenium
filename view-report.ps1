# Open testreport dashboard for selenium-viewer-main
$ErrorActionPreference = "Stop"
$projectDir = $PSScriptRoot
$reportDir = Join-Path $projectDir "custom-report"
$mvn = "C:\Maven\bin\mvn.cmd"

4173..4177 | ForEach-Object {
  Get-NetTCPConnection -LocalPort $_ -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}

Start-Sleep -Seconds 1

if (-not (Test-Path $reportDir)) {
  Write-Host "No reports yet. Running tests first..."
  Set-Location $projectDir
  & $mvn test "-Dtestreport.open=never"
}

Write-Host "Starting viewer for: $reportDir"
Set-Location $projectDir
Start-Process -FilePath $mvn -ArgumentList @(
  "exec:java",
  "-Dexec.classpathScope=test",
  "-Dexec.args=serve --report-dir `"$reportDir`""
) -WorkingDirectory $projectDir

Start-Sleep -Seconds 4
Start-Process "http://localhost:4173"
Write-Host "Dashboard: http://localhost:4173"
Write-Host "Reports: $reportDir"
