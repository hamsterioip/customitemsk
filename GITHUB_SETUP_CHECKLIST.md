# GitHub Setup Checklist for hamsterioip

## ✅ Step 1: Push All Files to GitHub

Open terminal in VS Code and run:

```bash
# Add all new files
git add .

# Commit them
git commit -m "Add auto-update system and build scripts"

# Push to GitHub
git push origin main
```

**Verify:** Go to https://github.com/hamsterioip/customitemsk and check if you see:
- `ConnectorMod.java` in `src/main/java/com/example/`
- `.github/workflows/publish.yml`
- `version.json`
- `build-and-release.bat`

---

## ✅ Step 2: Enable GitHub Actions

1. Go to https://github.com/hamsterioip/customitemsk/actions
2. If you see "I understand my workflows, go ahead and enable them" - **click it**
3. If Actions is disabled, go to:
   - Settings → Actions → General
   - Under "Actions permissions" select **"Allow all actions and reusable workflows"**
   - Click **Save**

---

## ✅ Step 3: Create Your First Release

Run the build script:

```bash
# Using the batch file
.\build-and-release.bat v1.0.0
```

Or manually:
```bash
git add .
git commit -m "Release v1.0.0"
git push origin main
git tag v1.0.0
git push origin v1.0.0
```

---

## ✅ Step 4: Check Actions Tab

After pushing the tag, go to:
https://github.com/hamsterioip/customitemsk/actions

You should see a workflow run called **"Publish to Modrinth & Auto-Update"**

If it's yellow → It's running
If it's green ✅ → Success!
If it's red ❌ → Check the error

---

## 🔍 Troubleshooting

### "No workflows found"
**Cause:** Files not pushed or Actions disabled
**Fix:** 
1. Run `git push origin main`
2. Enable Actions in Settings

### "Workflow not running"
**Cause:** No tag pushed
**Fix:** Run `git push origin v1.0.0`

### "Permission denied"
**Cause:** GitHub token permissions
**Fix:** 
1. Go to Settings → Actions → General
2. Under "Workflow permissions" select **"Read and write permissions"**
3. Check **"Allow GitHub Actions to create and approve pull requests"**
4. Click Save

### "Build failed"
**Cause:** Gradle build error
**Fix:** Check the Actions log for the specific error

---

## 📋 File Verification

Make sure these files exist in your repo:

```
📁 .github/workflows/
   └── publish.yml          ← GitHub Actions workflow
📁 src/main/java/com/example/
   └── ConnectorMod.java    ← Auto-updater code
📄 version.json             ← Version manifest
📄 build-and-release.bat    ← Build script
```

---

## 🔗 Important URLs for Your Repo

| Purpose | URL |
|---------|-----|
| Your Repo | https://github.com/hamsterioip/customitemsk |
| Actions | https://github.com/hamsterioip/customitemsk/actions |
| Releases | https://github.com/hamsterioip/customitemsk/releases |
| version.json (raw) | https://raw.githubusercontent.com/hamsterioip/customitemsk/main/version.json |

---

## 🚀 Quick Test

After everything is set up, test it:

```bash
# Make a small change to any file
echo "// Test" >> README.md

# Build and release v1.0.1
.\build-and-release.bat v1.0.1
```

Then check https://github.com/hamsterioip/customitemsk/actions

You should see the workflow running!
