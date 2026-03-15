package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URLConnection;
import java.net.URL;
import java.nio.file.*;
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
    public static volatile boolean updateReady   = false;
    public static volatile String  pendingVersion = "";

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

        new Thread(() -> {
            try { checkForUpdates(); }
            catch (Exception e) { LOGGER.error("Update check failed: " + e.getMessage()); }
        }, "CustomItemsK-Updater").start();
    }

    // -------------------------------------------------------------------------
    // Apply a previously-downloaded pending update
    // -------------------------------------------------------------------------

    private void applyPendingUpdate() {
        try {
            Path modsDir     = FabricLoader.getInstance().getGameDir().resolve("mods");
            Path pendingPath = modsDir.resolve(MOD_FILENAME + ".pending");
            Path targetPath  = modsDir.resolve(MOD_FILENAME);

            if (Files.exists(pendingPath)) {
                try {
                    cleanupOldVersions(modsDir);
                    Files.move(pendingPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Applied pending update — restart required for full effect.");
                } catch (Exception e) {
                    // On Windows the running JAR is locked — launch watchdog to swap after exit
                    LOGGER.debug("Direct apply failed (JAR locked), launching watchdog: " + e.getMessage());
                    scheduleWindowsWatchdog(
                            pendingPath.toAbsolutePath().toString(),
                            targetPath.toAbsolutePath().toString(),
                            modsDir);
                }
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
    private void scheduleWindowsWatchdog(String pendingStr, String targetStr, Path modsDir) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            long pid = ProcessHandle.current().pid();
            Path batchFile = modsDir.resolve("_csk_update.bat");
            String nl = "\r\n";
            String batch =
                "@echo off" + nl +
                "set PENDING=" + pendingStr + nl +
                "set TARGET=" + targetStr + nl +
                // Wait for the Minecraft JVM PID to disappear (works for both
                // clean quit and force-kill by the launcher / Modrinth app).
                ":WAITPID" + nl +
                "tasklist /fi \"PID eq " + pid + "\" 2>nul | find /i \"" + pid + "\" >nul" + nl +
                "if %ERRORLEVEL%==0 (" + nl +
                "    timeout /t 1 /nobreak >nul" + nl +
                "    goto WAITPID" + nl +
                ")" + nl +
                // If the shutdown hook already moved the file, silently exit.
                "if not exist \"%PENDING%\" (del \"%~f0\" & exit /b 0)" + nl +
                // Poll until the file lock is released, then swap.
                ":DELLOOP" + nl +
                "del /f /q \"%TARGET%\" 2>nul" + nl +
                "if exist \"%TARGET%\" (" + nl +
                "    timeout /t 2 /nobreak >nul" + nl +
                "    goto DELLOOP" + nl +
                ")" + nl +
                "move /y \"%PENDING%\" \"%TARGET%\"" + nl +
                "del \"%~f0\"" + nl;
            Files.writeString(batchFile, batch);

            // Launch detached — this process outlives the JVM
            new ProcessBuilder("cmd", "/c",
                    "start \"\" /min cmd /c \"" + batchFile.toAbsolutePath() + "\"")
                    .start();
            LOGGER.info("Watchdog launched (PID " + pid + ") — update will apply after any exit.");
        } catch (Exception e) {
            LOGGER.error("Could not launch update watchdog: " + e.getMessage());
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
                downloadUpdate(downloadUrl, remoteVersion);
            } else {
                LOGGER.info("Mod is up to date (" + localVersion + ")");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for updates: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Download (with timeout)
    // -------------------------------------------------------------------------

    private String downloadString(String urlString) {
        try {
            URLConnection conn = new URL(urlString).openConnection();
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
        try {
            String search = "\"" + fieldName + "\": \"";
            int start = json.indexOf(search);
            if (start == -1) return null;
            start += search.length();
            int end = json.indexOf("\"", start);
            return (end == -1) ? null : json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Version comparison
    // -------------------------------------------------------------------------

    private boolean isNewerVersion(String remote, String local) {
        String[] r = remote.replace("v", "").split("\\.");
        String[] l = local.replace("v",  "").split("\\.");
        int max = Math.max(r.length, l.length);
        for (int i = 0; i < max; i++) {
            int rv = i < r.length ? parseVersionPart(r[i]) : 0;
            int lv = i < l.length ? parseVersionPart(l[i]) : 0;
            if (rv > lv) return true;
            if (rv < lv) return false;
        }
        return false;
    }

    private int parseVersionPart(String part) {
        try {
            String clean = part.replaceAll("[^0-9]", "");
            return clean.isEmpty() ? 0 : Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Download update to .pending, then apply via batch on shutdown
    // -------------------------------------------------------------------------

    private void downloadUpdate(String downloadUrl, String newVersion) {
        try {
            LOGGER.info("Downloading update " + newVersion + "...");

            Path modsDir     = FabricLoader.getInstance().getGameDir().resolve("mods");
            Path targetPath  = modsDir.resolve(MOD_FILENAME);
            Path pendingPath = modsDir.resolve(MOD_FILENAME + ".pending");

            URLConnection conn = new URL(downloadUrl).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, pendingPath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!Files.exists(pendingPath) || Files.size(pendingPath) < 1000) {
                throw new Exception("Downloaded file too small — possible corruption");
            }

            updateReady    = true;
            pendingVersion = newVersion;

            String pending = pendingPath.toAbsolutePath().toString();
            String target  = targetPath.toAbsolutePath().toString();

            // Launch the watchdog NOW — it watches the Minecraft PID and swaps the
            // files the moment that PID disappears, covering both clean quit and
            // Modrinth/launcher force-kill (shutdown hooks don't fire on force-kill).
            scheduleWindowsWatchdog(pending, target, modsDir);

            // Shutdown hook: on non-Windows (or graceful exit where lock releases
            // early) try a direct move.  The watchdog's "if not exist PENDING" guard
            // makes it a no-op if this succeeds first.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    cleanupOldVersions(modsDir);
                    Files.move(Paths.get(pending), Paths.get(target),
                            StandardCopyOption.REPLACE_EXISTING);
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
