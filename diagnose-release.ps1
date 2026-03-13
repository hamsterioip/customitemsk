# Diagnostic script for GitHub Release issues

Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  GitHub Release Diagnostic (hamsterioip)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Check if JAR exists locally
Write-Host "📦 Checking local build..." -ForegroundColor Yellow

$jarFiles = Get-ChildItem -Path "build/libs" -Filter "*.jar" -ErrorAction SilentlyContinue

if (-not $jarFiles) {
    Write-Host "❌ No JAR files found in build/libs/" -ForegroundColor Red
    Write-Host "   You need to build first: .\gradlew build" -ForegroundColor Yellow
    exit 1
}

Write-Host "Found JAR files:" -ForegroundColor Green
foreach ($jar in $jarFiles) {
    $size = [math]::Round($jar.Length / 1KB, 2)
    Write-Host "  - $($jar.Name) ($size KB)" -ForegroundColor White
}

# Check which JAR would be uploaded
$mainJar = $jarFiles | Where-Object { 
    $_.Name -notmatch "sources" -and 
    $_.Name -notmatch "dev" -and
    $_.Name -match "\d" 
} | Select-Object -First 1

if ($mainJar) {
    Write-Host ""
    Write-Host "✅ Main JAR for release: $($mainJar.Name)" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "⚠️  Could not identify main JAR file" -ForegroundColor Yellow
}

# Check git status
Write-Host ""
Write-Host "📋 Git Status:" -ForegroundColor Yellow
$uncommitted = git status --short
if ($uncommitted) {
    Write-Host "⚠️  Uncommitted changes found:" -ForegroundColor Yellow
    Write-Host $uncommitted
    Write-Host ""
    Write-Host "Run: git add . && git commit -m 'Update workflow' && git push" -ForegroundColor Cyan
} else {
    Write-Host "✅ All changes committed" -ForegroundColor Green
}

# Check remote
Write-Host ""
Write-Host "🌐 Git Remote:" -ForegroundColor Yellow
$remote = git remote get-url origin
Write-Host "  $remote" -ForegroundColor White

# Provide solution
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  SOLUTION" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "The issue is likely that the GitHub Actions workflow isn't" -ForegroundColor White
Write-Host "finding the JAR file pattern. Let's fix this:" -ForegroundColor White
Write-Host ""
Write-Host "Step 1: Push any pending changes" -ForegroundColor Yellow
Write-Host "  git add ." -ForegroundColor Gray
Write-Host "  git commit -m 'Fix JAR upload pattern'" -ForegroundColor Gray
Write-Host "  git push origin main" -ForegroundColor Gray
Write-Host ""
Write-Host "Step 2: Delete the broken release and rebuild" -ForegroundColor Yellow
Write-Host "  git push --delete origin v1.0.0" -ForegroundColor Gray
Write-Host "  git tag -d v1.0.0" -ForegroundColor Gray
Write-Host "  .\build-and-release.bat v1.0.0" -ForegroundColor Gray
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
