param([Parameter(Mandatory)][string]$Token)

$repo      = "Naman2608/Real-Time-Orders-Service"
$releaseId = "334353752"
$headers   = @{ Authorization = "token $Token" }

Set-Location $PSScriptRoot

Write-Host "`n[1/3] Building JAR..." -ForegroundColor Cyan
& ./mvnw.cmd package -DskipTests -q
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed." -ForegroundColor Red; exit 1 }
Write-Host "     Built: orders-realtime.jar ($([math]::Round((Get-Item 'orders-realtime.jar').Length/1MB,1)) MB)" -ForegroundColor Green

Write-Host "`n[2/3] Removing old release asset..." -ForegroundColor Cyan
$assets = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/$releaseId/assets" -Headers $headers
foreach ($asset in $assets) {
    Invoke-RestMethod "https://api.github.com/repos/$repo/releases/assets/$($asset.id)" -Method DELETE -Headers $headers
    Write-Host "     Deleted: $($asset.name)" -ForegroundColor Yellow
}

Write-Host "`n[3/3] Uploading new JAR..." -ForegroundColor Cyan
$jar      = [System.IO.File]::ReadAllBytes("$PSScriptRoot\orders-realtime.jar")
$response = Invoke-RestMethod "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=orders-realtime.jar" `
    -Method POST -Headers (@{ Authorization = "token $Token"; "Content-Type" = "application/java-archive" }) -Body $jar
Write-Host "     $($response.browser_download_url)" -ForegroundColor Green

Write-Host "`nRelease updated successfully!" -ForegroundColor Green
