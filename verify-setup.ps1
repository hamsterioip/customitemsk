# CustomItemsK Setup Verification Script
# Run this to check if everything is configured correctly

Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  CustomItemsK Setup Verification (hamsterioip)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$errors = 0

# Check 1: Git repository
Write-Host "[1/6] Checking Git repository..." -NoNewline
if (Test-Path ".git") {
    Write-Host " ✅ Found" -ForegroundColor Green
} else {
    Write-Host " ❌ Not a git repository!" -ForegroundColor Red
    $errors++
}

# Check 2: GitHub remote
Write-Host "[2/6] Checking GitHub remote..." -NoNewline
$remote = git remote get-url origin 2>$null
if ($remote -match "hamsterioip/customitemsk") {
    Write-Host " ✅ Correct ($remote)" -ForegroundColor Green
} else {
    Write-Host " ❌ Wrong remote!" -ForegroundColor Red
    Write-Host "       Expected: hamsterioip/customitemsk" -ForegroundColor Yellow
    Write-Host "       Found: $remote" -ForegroundColor Yellow
    $errors++
}

# Check 3: Workflow file
Write-Host "[3/6] Checking workflow file..." -NoNewline
if (Test-Path ".github/workflows/publish.yml") {
    Write-Host " ✅ Found" -ForegroundColor Green
} else {
    Write-Host " ❌ Missing!" -ForegroundColor Red
    $errors++
}

# Check 4: ConnectorMod
Write-Host "[4/6] Checking ConnectorMod..." -NoNewline
if (Test-Path "src/main/java/com/example/ConnectorMod.java") {
    $content = Get-Content "src/main/java/com/example/ConnectorMod.java" -Raw
    if ($content -match 'hamsterioip') {
        Write-Host " ✅ Configured correctly" -ForegroundColor Green
    } else {
        Write-Host " ❌ Username not set!" -ForegroundColor Red
        $errors++
    }
} else {
    Write-Host " ❌ Missing!" -ForegroundColor Red
    $errors++
}

# Check 5: version.json
Write-Host "[5/6] Checking version.json..." -NoNewline
if (Test-Path "version.json") {
    $content = Get-Content "version.json" -Raw
    if ($content -match 'hamsterioip') {
        Write-Host " ✅ Configured correctly" -ForegroundColor Green
    } else {
        Write-Host " ❌ Username not set!" -ForegroundColor Red
        $errors++
    }
} else {
    Write-Host " ❌ Missing!" -ForegroundColor Red
    $errors++
}

# Check 6: Build scripts
Write-Host "[6/6] Checking build scripts..." -NoNewline
$hasBat = Test-Path "build-and-release.bat"
$hasPs1 = Test-Path "build-and-release.ps1"
if ($hasBat -and $hasPs1) {
    Write-Host " ✅ Found (.bat and .ps1)" -ForegroundColor Green
} else {
    Write-Host " ⚠️  Partial" -ForegroundColor Yellow
    if (-not $hasBat) { Write-Host "       Missing: build-and-release.bat" -ForegroundColor Yellow }
    if (-not $hasPs1) { Write-Host "       Missing: build-and-release.ps1" -ForegroundColor Yellow }
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan

if ($errors -eq 0) {
    Write-Host "✅ ALL CHECKS PASSED!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "  1. Push files to GitHub: git push origin main"
    Write-Host "  2. Enable Actions at: https://github.com/hamsterioip/customitemsk/actions"
    Write-Host "  3. Create first release: .\build-and-release.bat v1.0.0"
    Write-Host ""
    Write-Host "Your repo URL: https://github.com/hamsterioip/customitemsk" -ForegroundColor Cyan
} else {
    Write-Host "❌ FOUND $errors ERROR(S)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please fix the issues above before proceeding." -ForegroundColor Yellow
}

Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Check git status
Write-Host "Git Status:" -ForegroundColor Cyan
$status = git status --short 2>$null
if ($status) {
    Write-Host "Uncommitted changes found:" -ForegroundColor Yellow
    Write-Host $status
    Write-Host ""
    Write-Host "Run these commands to commit:" -ForegroundColor Yellow
    Write-Host '  git add .' -ForegroundColor White
    Write-Host '  git commit -m "Add auto-update system"' -ForegroundColor White
    Write-Host '  git push origin main' -ForegroundColor White
} else {
    Write-Host "✅ All changes committed" -ForegroundColor Green
}

Write-Host ""
