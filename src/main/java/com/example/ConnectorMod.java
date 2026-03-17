package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Scanner;

/**
 * Connector Mod - Auto-updater for CustomItemsK.
 *
 * On startup:
 *   1. Apply any .pending update left over from a previous session.
 *   2. Check GitHub for a newer version.
 *   3. If found, download to customitemsk.jar.pending (no file-lock conflict).
 *   4. On shutdown, write a polling batch file (_csk_update.bat) that waits
 *      until the JVM releases the file lock, then swaps pending → jar.
 *      This is the only approach that reliably works on Windows.
 */
public class ConnectorMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("customitemsk-connector");

    /** Set to true once an update has been fully downloaded and is waiting for restart. */
    public static volatile boolean updateReady    = false;
    public static volatile String  pendingVersion = "";
    /** Set to true once the version check has completed (success or failure). */
    public static volatile boolean checkComplete  = false;

    private static final String GITHUB_USER  = "hamsterioip";
    private static final String GITHUB_REPO  = "customitemsk";
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/" + GITHUB_USER + "/" + GITHUB_REPO + "/main/version.json";
    private static final String MOD_FILENAME = "customitemsk.jar";
    private static final String MOD_ID       = "customitemsk";

    @Override
    public void onInitialize() {
        LOGGER.info("CustomItemsK Connector starting...");
        applyPendingUpdate();

        Thread updater = new Thread(() -> {
            try { checkForUpdates(); }
            catch (Exception e) { LOGGER.error("Update check failed: " + e.getMessage()); }
        }, "CustomItemsK-Updater");
        updater.setDaemon(true);
        updater.start();
    }

    // -------------------------------------------------------------------------
    // Apply a previously-downloaded pending update
    // -------------------------------------------------------------------------

    private void applyPendingUpdate() {
        try {
            Path modsDir      = FabricLoader.getInstance().getGameDir().resolve("mods");
            Path pendingPath  = modsDir.resolve(MOD_FILENAME + ".pending");
            Path sha256Path   = modsDir.resolve(MOD_FILENAME + ".pending.sha256");
            Path targetPath   = modsDir.resolve(MOD_FILENAME);

            if (!Files.exists(pendingPath)) return;

            // Re-verify integrity before applying — guards against partial downloads
            // that survived a crash between the download and the size/hash check.
            // If the sidecar hash file is absent the pending file cannot be trusted
            // (it predates the hash feature, or the sidecar was lost); discard it.
            if (!Files.exists(sha256Path)) {
                LOGGER.warn("Pending update has no SHA-256 sidecar — discarding unverifiable file.");
                Files.deleteIfExists(pendingPath);
                return;
            }
            String expectedHash = Files.readString(sha256Path).strip();
            String actualHash   = computeSha256(pendingPath);
            if (!expectedHash.equalsIgnoreCase(actualHash)) {
                LOGGER.error("Pending update failed SHA-256 re-check — discarding corrupted file."
                        + "\n  expected: " + expectedHash
                        + "\n  got:      " + actualHash);
                Files.deleteIfExists(pendingPath);
                Files.deleteIfExists(sha256Path);
                return;
            }
            LOGGER.info("Pending update SHA-256 re-verified: " + actualHash);

            try {
                cleanupOldVersions(modsDir);
                Files.move(pendingPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(sha256Path);
                LOGGER.info("Applied pending update — restart required for full effect.");
            } catch (Exception e) {
                // On Windows the running JAR is locked — launch watchdog to swap after exit
                LOGGER.debug("Direct apply failed (JAR locked), launching watchdog: " + e.getMessage());
                scheduleWindowsWatchdog(
                        pendingPath.toAbsolutePath().toString(),
                        targetPath.toAbsolutePath().toString(),
                        sha256Path.toAbsolutePath().toString(),
                        modsDir);
            }
        } catch (Exception e) {
            LOGGER.debug("applyPendingUpdate error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Windows watchdog batch — launched immediately, watches PID, survives force-kill
    // -------------------------------------------------------------------------

    /**
     * Writes _csk_update.bat and launches it as a detached process RIGHT NOW.
     *
     * Unlike a shutdown hook, this watchdog is already running before Minecraft exits.
     * It watches the Minecraft JVM PID in a loop.  The moment that PID disappears
     * (clean quit, Modrinth stop button, crash — any exit), it waits for the file
     * lock to release, then swaps .pending → .jar.
     *
     * A guard "if not exist PENDING" at the top of the swap section ensures the
     * batch is a no-op if the shutdown hook already applied the update cleanly.
     */
    private static final String TASK_NAME = "CustomItemsKUpdate";

    private void scheduleWindowsWatchdog(String pendingStr, String targetStr,
                                          String sha256Str, Path modsDir) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            long pid = ProcessHandle.current().pid();
            Path batchFile = modsDir.resolve("_csk_update.bat");
            String nl = "\r\n";
            // Paths are quoted with set "VAR=value" syntax to handle spaces and most
            // special characters safely (ampersands, parentheses, carets, etc.)
            String batch =
                "@echo off" + nl +
                "set \"PENDING=" + pendingStr + "\"" + nl +
                "set \"TARGET=" + targetStr + "\"" + nl +
                "set \"SHA256FILE=" + sha256Str + "\"" + nl +
                "set RETRIES=0" + nl +
                ":WAITPID" + nl +
                "tasklist /fi \"PID eq " + pid + "\" 2>nul | find /i \"" + pid + "\" >nul" + nl +
                "if %ERRORLEVEL%==0 (" + nl +
                "    timeout /t 1 /nobreak >nul" + nl +
                "    goto WAITPID" + nl +
                ")" + nl +
                // No-op if shutdown hook already applied the update
                "if not exist \"%PENDING%\" (" + nl +
                "    schtasks /delete /tn \"" + TASK_NAME + "\" /f >nul 2>&1" + nl +
                "    del \"%~f0\" & exit /b 0" + nl +
                ")" + nl +
                ":DELLOOP" + nl +
                "set /a RETRIES+=1" + nl +
                "if %RETRIES% GTR 30 (" + nl +
                "    echo Update failed: could not delete locked file after 30 attempts. >> \"%~dp0csk_update.log\"" + nl +
                "    schtasks /delete /tn \"" + TASK_NAME + "\" /f >nul 2>&1" + nl +
                "    del \"%~f0\" & exit /b 1" + nl +
                ")" + nl +
                "del /f /q \"%TARGET%\" 2>nul" + nl +
                "if exist \"%TARGET%\" (" + nl +
                "    timeout /t 2 /nobreak >nul" + nl +
                "    goto DELLOOP" + nl +
                ")" + nl +
                "move /y \"%PENDING%\" \"%TARGET%\"" + nl +
                "call \"%~dp0_csk_discord.bat\"" + nl +
                "del /f /q \"%SHA256FILE%\" 2>nul" + nl +
                "schtasks /delete /tn \"" + TASK_NAME + "\" /f >nul 2>&1" + nl +
                "del \"%~f0\"" + nl;
            Files.writeString(batchFile, batch);

            // Write the Discord redirect batch — called by _csk_update.bat on success
            Path discordBatch = modsDir.resolve("_csk_discord.bat");
            String discordScript =
                "@echo off" + nl +
                "start \"\" \"https://discord.gg/PmtsnsReR7\"" + nl +
                "del \"%~f0\"" + nl;
            Files.writeString(discordBatch, discordScript);

            // Primary: Task Scheduler — runs as an independent Windows service,
            // survives job-object kills (Modrinth Stop, task manager End Task, etc.)
            if (!launchViaTaskScheduler(batchFile)) {
                // Fallback: detached start command (works for clean quit, may not
                // survive forced kills if launcher uses Windows Job Objects)
                new ProcessBuilder("cmd", "/c",
                        "start \"\" /min cmd /c \"" + batchFile.toAbsolutePath() + "\"")
                        .start();
                LOGGER.info("Watchdog launched via start command (PID " + pid + ").");
            }
        } catch (Exception e) {
            LOGGER.error("Could not launch update watchdog: " + e.getMessage());
        }
    }

    /**
     * Registers the watchdog batch as a Windows Task Scheduler one-shot task
     * and fires it immediately.  Scheduled tasks run under the Task Scheduler
     * service and are completely independent of any Windows Job Object, so they
     * survive both graceful exits and force-kills (Modrinth Stop, End Task, etc.).
     */
    private boolean launchViaTaskScheduler(Path batchFile) {
        try {
            String bat = batchFile.toAbsolutePath().toString();
            // Remove any stale task left from a previous update attempt
            new ProcessBuilder("schtasks", "/delete", "/tn", TASK_NAME, "/f")
                    .redirectErrorStream(true).start().waitFor();
            // Create a one-time task running as the current user (no password needed)
            int rc = new ProcessBuilder(
                    "schtasks", "/create",
                    "/tn", TASK_NAME,
                    "/tr", "cmd /min /c \"" + bat + "\"",
                    "/sc", "ONCE", "/st", "00:00", "/f")
                    .redirectErrorStream(true).start().waitFor();
            if (rc != 0) return false;
            // Fire immediately — doesn't wait for the scheduled time
            new ProcessBuilder("schtasks", "/run", "/tn", TASK_NAME)
                    .redirectErrorStream(true).start();
            LOGGER.info("Watchdog registered in Task Scheduler (task: " + TASK_NAME + ").");
            return true;
        } catch (Exception e) {
            LOGGER.debug("Task Scheduler unavailable: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Clean up old version JARs to prevent duplicates
    // -------------------------------------------------------------------------

    private void cleanupOldVersions(Path modsDir) {
        try {
            Files.list(modsDir)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.startsWith("customitemsk")
                           && name.endsWith(".jar")
                           && !name.equals(MOD_FILENAME)
                           && !name.equals(MOD_FILENAME + ".pending");
                })
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                        LOGGER.info("Deleted old version: " + p.getFileName());
                    } catch (Exception e) {
                        LOGGER.warn("Could not delete old version " + p.getFileName() + ": " + e.getMessage());
                    }
                });
        } catch (Exception e) {
            LOGGER.debug("Could not list mods directory: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Version check
    // -------------------------------------------------------------------------

    private void checkForUpdates() {
        LOGGER.info("Checking for updates...");
        try {
            String content = downloadString(MANIFEST_URL);
            if (content == null || content.isEmpty()) {
                LOGGER.warn("Could not retrieve version manifest");
                return;
            }

            String remoteVersion = extractJsonField(content, "version");
            String downloadUrl   = extractJsonField(content, "downloadUrl");

            if (remoteVersion == null || downloadUrl == null) {
                LOGGER.error("Invalid version manifest format");
                return;
            }

            String localVersion = FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("0.0.0");

            LOGGER.info("Local: " + localVersion + "  Remote: " + remoteVersion);

            if (isNewerVersion(remoteVersion, localVersion)) {
                LOGGER.info("New version available: " + remoteVersion);
                String expectedSha256 = extractJsonField(content, "sha256");
                downloadUpdate(downloadUrl, remoteVersion, expectedSha256);
            } else {
                LOGGER.info("Mod is up to date (" + localVersion + ")");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for updates: " + e.getMessage());
        } finally {
            checkComplete = true;
        }
    }

    // -------------------------------------------------------------------------
    // Download (with timeout)
    // -------------------------------------------------------------------------

    private String downloadString(String urlString) {
        try {
            URLConnection conn = URI.create(urlString).toURL().openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            try (Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        } catch (Exception e) {
            LOGGER.error("Failed to download " + urlString + " — " + e.getMessage());
            return null;
        }
    }

    private String extractJsonField(String json, String fieldName) {
        return VersionUtils.extractJsonField(json, fieldName);
    }

    // -------------------------------------------------------------------------
    // Version comparison
    // -------------------------------------------------------------------------

    private boolean isNewerVersion(String remote, String local) {
        return VersionUtils.isNewerVersion(remote, local);
    }

    // -------------------------------------------------------------------------
    // SHA-256 integrity check
    // -------------------------------------------------------------------------

    private String computeSha256(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            try (InputStream is = Files.newInputStream(file)) {
                int n;
                while ((n = is.read(buf)) != -1) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            LOGGER.error("SHA-256 computation failed: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Download update to .pending, then apply via batch on shutdown
    // -------------------------------------------------------------------------

    private void downloadUpdate(String downloadUrl, String newVersion, String expectedSha256) {
        try {
            LOGGER.info("Downloading update " + newVersion + "...");

            Path modsDir     = FabricLoader.getInstance().getGameDir().resolve("mods");
            Path targetPath  = modsDir.resolve(MOD_FILENAME);
            Path pendingPath = modsDir.resolve(MOD_FILENAME + ".pending");
            Path sha256Path  = modsDir.resolve(MOD_FILENAME + ".pending.sha256");

            URLConnection conn = URI.create(downloadUrl).toURL().openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, pendingPath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!Files.exists(pendingPath) || Files.size(pendingPath) < 1000) {
                throw new Exception("Downloaded file too small — possible corruption");
            }

            // SHA-256 integrity check
            if (expectedSha256 != null && !expectedSha256.isEmpty()) {
                String actualSha256 = computeSha256(pendingPath);
                if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                    Files.deleteIfExists(pendingPath);
                    throw new Exception("SHA-256 mismatch — download corrupted or tampered.\n"
                            + "  expected: " + expectedSha256 + "\n"
                            + "  got:      " + actualSha256);
                }
                LOGGER.info("SHA-256 verified: " + actualSha256);
                // Persist hash so applyPendingUpdate can re-verify on next boot
                Files.writeString(sha256Path, actualSha256);
            }

            updateReady    = true;
            pendingVersion = newVersion;

            String pending = pendingPath.toAbsolutePath().toString();
            String target  = targetPath.toAbsolutePath().toString();
            String sha256  = sha256Path.toAbsolutePath().toString();

            // Launch the watchdog NOW — it watches the Minecraft PID and swaps the
            // files the moment that PID disappears, covering both clean quit and
            // Modrinth/launcher force-kill (shutdown hooks don't fire on force-kill).
            scheduleWindowsWatchdog(pending, target, sha256, modsDir);

            // Shutdown hook: on non-Windows (or graceful exit where lock releases
            // early) try a direct move.  The watchdog's "if not exist PENDING" guard
            // makes it a no-op if this succeeds first.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    cleanupOldVersions(modsDir);
                    Files.move(Paths.get(pending), Paths.get(target),
                            StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(Paths.get(sha256));
                    LOGGER.info("Update applied via shutdown hook: " + newVersion);
                } catch (Exception ignored) {
                    // On Windows the file is still locked here — watchdog handles it
                }
            }, "CustomItemsK-UpdateApplier"));

            LOGGER.info("========================================");
            LOGGER.info("  UPDATE READY: " + newVersion);
            LOGGER.info("  Restart Minecraft to apply it!");
            LOGGER.info("========================================");

        } catch (Exception e) {
            LOGGER.error("Failed to download update: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
