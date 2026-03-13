# CustomItemsK Auto Builder Script
# Usage: .\build-and-release.ps1 v1.2.0
# Author: hamsterioip

param(
    [Parameter(Mandatory=$true)]
    [string]$Version,
    
    [string]$CommitMessage = "",
    
    [switch]$SkipBuild,
    [switch]$SkipCommit,
    [switch]$DryRun
)

# Configuration
$GithubUser = "hamsterioip"
$RepoName = "customitemsk"
$GradleCommand = ".\gradlew.bat"

# Colors
$Green = "Green"
$Red = "Red"
$Yellow = "Yellow"
$Cyan = "Cyan"

function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor $Cyan
    Write-Host "  $Text" -ForegroundColor $Cyan
    Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor $Cyan
    Write-Host ""
}

function Write-Step {
    param([int]$Step, [int]$Total, [string]$Text)
    Write-Host "[$Step/$Total] " -ForegroundColor $Yellow -NoNewline
    Write-Host $Text
}

function Write-Success {
    param([string]$Text)
    Write-Host "✅ $Text" -ForegroundColor $Green
}

function Write-Error {
    param([string]$Text)
    Write-Host "❌ $Text" -ForegroundColor $Red
}

function Write-Info {
    param([string]$Text)
    Write-Host "ℹ️  $Text" -ForegroundColor $Cyan
}

# Validate version format
if (-not $Version.StartsWith("v")) {
    Write-Warning "Version should start with 'v' (e.g., v1.2.0)"
    $continue = Read-Host "Continue anyway? (y/n)"
    if ($continue -ne "y") { exit 1 }
}

# Main Script
Write-Header "CustomItemsK Auto Builder (hamsterioip)"
Write-Info "Building version: $Version"
Write-Info "Target: github.com/$GithubUser/$RepoName"
Write-Host ""

# Check prerequisites
Write-Step 0 5 "Checking prerequisites..."

# Check Git
try {
    $gitVersion = git --version 2>$null
    Write-Success "Git found: $gitVersion"
} catch {
    Write-Error "Git is not installed or not in PATH"
    exit 1
}

# Check if in git repo
try {
    $repoRoot = git rev-parse --show-toplevel 2>$null
    Write-Success "Git repository found: $repoRoot"
} catch {
    Write-Error "Not a git repository"
    exit 1
}

# Check remote
$remoteUrl = git remote get-url origin 2>$null
if ($remoteUrl -match "github.com[/:]$GithubUser/$RepoName") {
    Write-Success "GitHub remote verified: $remoteUrl"
} else {
    Write-Warning "Remote URL doesn't match expected: github.com/$GithubUser/$RepoName"
    Write-Info "Current remote: $remoteUrl"
    $continue = Read-Host "Continue anyway? (y/n)"
    if ($continue -ne "y") { exit 1 }
}

Write-Host ""

# Step 1: Pull latest changes
Write-Step 1 5 "Pulling latest changes from GitHub..."
if (-not $DryRun) {
    git pull origin main
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Could not pull latest changes"
        $continue = Read-Host "Continue anyway? (y/n)"
        if ($continue -ne "y") { exit 1 }
    } else {
        Write-Success "Pulled latest changes"
    }
} else {
    Write-Info "[DRY RUN] Would run: git pull origin main"
}
Write-Host ""

# Step 2: Build with Gradle
if (-not $SkipBuild) {
    Write-Step 2 5 "Building mod with Gradle..."
    if (-not $DryRun) {
        & $GradleCommand clean build
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Build failed!"
            exit 1
        }
        
        # Verify JAR was created
        $jarFiles = Get-ChildItem -Path "build/libs" -Filter "*.jar" | Where-Object { 
            $_.Name -notmatch "sources|dev" 
        }
        
        if ($jarFiles.Count -eq 0) {
            Write-Error "No JAR file found in build/libs/"
            exit 1
        }
        
        $jarSize = [math]::Round($jarFiles[0].Length / 1KB, 2)
        Write-Success "Build successful: $($jarFiles[0].Name) ($jarSize KB)"
    } else {
        Write-Info "[DRY RUN] Would run: $GradleCommand clean build"
    }
} else {
    Write-Step 2 5 "Skipping build (SkipBuild flag set)"
}
Write-Host ""

# Step 3: Commit changes
if (-not $SkipCommit) {
    Write-Step 3 5 "Committing changes..."
    if (-not $DryRun) {
        git add .
        
        $msg = if ($CommitMessage) { $CommitMessage } else { "Release $Version" }
        git commit -m "$msg"
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Committed: $msg"
        } else {
            Write-Warning "No changes to commit or commit failed"
        }
    } else {
        Write-Info "[DRY RUN] Would commit with message: Release $Version"
    }
} else {
    Write-Step 3 5 "Skipping commit (SkipCommit flag set)"
}
Write-Host ""

# Step 4: Push to GitHub
Write-Step 4 5 "Pushing to GitHub..."
if (-not $DryRun) {
    git push origin main
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to push to GitHub"
        exit 1
    }
    Write-Success "Pushed to main branch"
} else {
    Write-Info "[DRY RUN] Would run: git push origin main"
}
Write-Host ""

# Step 5: Create and push tag
Write-Step 5 5 "Creating release tag $Version..."
if (-not $DryRun) {
    # Delete local tag if exists
    git tag -d $Version 2>$null
    
    # Create new tag
    git tag $Version
    
    # Push tag
    git push origin $Version
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to push tag"
        exit 1
    }
    Write-Success "Tag pushed: $Version"
} else {
    Write-Info "[DRY RUN] Would create and push tag: $Version"
}
Write-Host ""

# Success!
Write-Header "BUILD COMPLETE!"
Write-Host "Version:    " -NoNewline; Write-Host $Version -ForegroundColor $Green
Write-Host "GitHub:     " -NoNewline; Write-Host "https://github.com/$GithubUser/$RepoName" -ForegroundColor $Cyan
Write-Host "Actions:    " -NoNewline; Write-Host "https://github.com/$GithubUser/$RepoName/actions" -ForegroundColor $Cyan
Write-Host "Releases:   " -NoNewline; Write-Host "https://github.com/$GithubUser/$RepoName/releases" -ForegroundColor $Cyan
Write-Host ""
Write-Host "What's happening now:" -ForegroundColor $Yellow
Write-Host "  1. GitHub Actions is building your mod"
Write-Host "  2. It will be published to Modrinth"
Write-Host "  3. A release will be created on GitHub"
Write-Host "  4. version.json will be updated automatically"
Write-Host ""
Write-Host "Players will get this update when they restart Minecraft! 🎉" -ForegroundColor $Green
Write-Host ""

# Open browser if not dry run
if (-not $DryRun) {
    $openBrowser = Read-Host "Open GitHub Actions in browser? (y/n)"
    if ($openBrowser -eq "y") {
        Start-Process "https://github.com/$GithubUser/$RepoName/actions"
    }
}

Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
