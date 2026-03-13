# Auto-Update System Setup Guide

This guide will help you set up automatic updates for your CustomItemsK mod so your friends always get the latest version!

## What This Does

1. When you push a new version tag to GitHub, it automatically builds and releases the mod
2. The mod checks for updates every time Minecraft starts
3. If a new version is available, it downloads automatically
4. Players just need to restart Minecraft to apply updates!

## Step 1: Verify Your GitHub Username

The username has been set to `hamsterioip`. If this is incorrect, update it in `ConnectorMod.java`:

```java
// In src/main/java/com/example/ConnectorMod.java
private static final String GITHUB_USER = "hamsterioip";
```

## Step 2: Create version.json

The `version.json` file has been created in your repository root. Update it with your info:

```json
{
  "version": "1.0.0",
  "downloadUrl": "https://github.com/hamsterioip/customitemsk/releases/download/v1.0.0/customitemsk.jar",
  "lastUpdated": "2025-01-01T00:00:00Z",
  "changelog": "https://github.com/hamsterioip/customitemsk/releases/tag/v1.0.0"
}
```

Your username `hamsterioip` is already configured.

## Step 3: Push to GitHub

1. Commit all these changes:
```bash
git add .
git commit -m "Add auto-update system"
git push origin main
```

2. Create and push a version tag:
```bash
git tag v1.0.0
git push --tags
```

## Step 4: GitHub Actions

The workflow file `.github/workflows/publish.yml` has been updated. It will:
- Build your mod when you push a tag
- Publish to Modrinth
- Create a GitHub Release
- Update `version.json` automatically

### Setup Required:
1. Go to your GitHub repository Settings → Actions → General
2. Make sure "Read and write permissions" is enabled for workflows
3. The workflow will auto-commit the updated `version.json`

## How It Works for Players

1. **First Install:** Players download the mod from GitHub Releases and put it in their `mods` folder
2. **Auto-Updates:** Every time they start Minecraft, the mod checks for updates
3. **New Version Found:** The mod downloads the new version to `mods/customitemsk.jar`
4. **Restart Required:** A message appears in console: "Please restart Minecraft to apply the update!"
5. **Done!** Player restarts and they're on the latest version

## How to Release a New Version

### Option 1: Using the Auto-Builder Script (Recommended)

Simply run the batch file with your version number:

```bash
# Using the batch script (Windows)
.\build-and-release.bat v1.1.0

# Or use the quick shortcut
.\release.bat v1.1.0

# Using PowerShell (more features)
.\build-and-release.ps1 v1.1.0
```

That's it! The script will:
1. ✅ Pull latest changes from GitHub
2. ✅ Build the mod with Gradle
3. ✅ Commit all changes
4. ✅ Push to GitHub
5. ✅ Create and push the version tag
6. ✅ Trigger GitHub Actions

### Option 2: From VS Code

Press `Ctrl+Shift+P` → Type "Tasks: Run Task" → Select:
- **"Release New Version"** - Full release with PowerShell
- **"Quick Release (Bat)"** - Simple batch release

Enter your version number when prompted (e.g., `v1.1.0`)

### Option 3: Manual (Terminal)

```bash
# 1. Make your code changes
# 2. Commit them
git add .
git commit -m "Add new features"
git push

# 3. Create a new version tag
git tag v1.1.0
git push --tags
```

---

## Builder Script Options

### PowerShell Script Features

The `build-and-release.ps1` script has additional options:

```powershell
# Dry run (see what it would do without doing it)
.\build-and-release.ps1 v1.1.0 -DryRun

# Skip the build step (if you already built)
.\build-and-release.ps1 v1.1.0 -SkipBuild

# Custom commit message
.\build-and-release.ps1 v1.1.0 -CommitMessage "Fixed critical bug"

# Skip committing (just tag and push)
.\build-and-release.ps1 v1.1.0 -SkipCommit
```

### What Happens After Release

GitHub Actions will automatically:
- Build the new JAR
- Publish to Modrinth
- Create a GitHub Release
- Update version.json
- Players get the update automatically when they restart Minecraft!

## Troubleshooting

### "Failed to check for updates"
- Make sure `YOUR_USERNAME` is changed in ConnectorMod.java
- Check that version.json is accessible at the raw GitHub URL

### Updates not downloading
- Check that releases are being created (GitHub → Releases)
- Verify version.json is being updated by the workflow

### Version comparison not working
- Use semantic versioning: `v1.2.3` format
- Don't skip version numbers

## Security Notes

- The mod downloads JAR files over HTTPS
- A backup of the old version is created before updating
- If download fails, the backup is restored
- Only accepts updates from your specified GitHub repository

## Optional: Separate Connector Mod

If you want a really lightweight updater, you can make `ConnectorMod` into a separate mod that players install. Then the main mod can be downloaded automatically. This keeps the initial download small.

To do this:
1. Create a new Fabric mod project
2. Copy only `ConnectorMod.java` and the fabric.mod.json entrypoint
3. Name it something like "CustomItemsK-Updater"
4. Players install the updater, and it downloads the main mod automatically
