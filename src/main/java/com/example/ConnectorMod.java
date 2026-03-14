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
                    // On Windows the running JAR is locked — schedule a batch swap for next exit
                    LOGGER.debug("Direct apply failed (JAR locked), scheduling batch: " + e.getMessage());
                    scheduleWindowsBatch(
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
    // Windows batch-file swap (polling loop — survives force-close)
    // -------------------------------------------------------------------------

    /**
     * Writes _csk_update.bat to the mods folder and either registers a JVM
     * shutdown hook to launch it (normal path) or launches it immediately
     * (called from within a shutdown hook, where addShutdownHook would throw).
     *
     * The batch file polls every 2 s until it can delete the old JAR (i.e. the
     * JVM has released the file lock), then renames .pending → .jar.
     */
    private void scheduleWindowsBatch(String pendingStr, String targetStr, Path modsDir) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            Path batchFile = modsDir.resolve("_csk_update.bat");
            String nl = "\r\n";
            String batch =
                "@echo off" + nl +
                "set PENDING=" + pendingStr + nl +
                "set TARGET=" + targetStr + nl +
                "set COUNT=0" + nl +
                ":LOOP" + nl +
                "set /a COUNT+=1" + nl +
                "if %COUNT% GTR 30 exit /b 1" + nl +
                "timeout /t 2 /nobreak >nul" + nl +
                "del /f /q \"%TARGET%\" 2>nul" + nl +
                "if exist \"%TARGET%\" goto LOOP" + nl +
                "move /y \"%PENDING%\" \"%TARGET%\"" + nl +
                "del \"%~f0\"" + nl;
            Files.writeString(batchFile, batch);

            try {
                // Normal path: register a shutdown hook to launch the batch after JVM exits
                Runtime.getRuntime().addShutdownHook(new Thread(() ->
                        launchBatch(batchFile), "CustomItemsK-WinBatch"));
            } catch (IllegalStateException e) {
                // Already shutting down — launch directly (batch will poll until lock released)
                launchBatch(batchFile);
            }
        } catch (Exception e) {
            LOGGER.error("Could not write update batch: " + e.getMessage());
        }
    }

    private void launchBatch(Path batchFile) {
        try {
            String path = batchFile.toAbsolutePath().toString();
            // "start "" /min cmd /c ..." creates a detached minimised window that
            // outlives the JVM parent process.
            new ProcessBuilder("cmd", "/c",
                    "start \"\" /min cmd /c \"" + path + "\"")
                    .start();
            LOGGER.info("Windows update batch launched — files will swap after JVM exits.");
        } catch (Exception e) {
            LOGGER.error("Could not launch update batch: " + e.getMessage());
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

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Try a direct atomic move first (works on Linux/macOS, and on
                // Windows if Minecraft somehow released the lock early)
                try {
                    cleanupOldVersions(modsDir);
                    Files.move(Paths.get(pending), Paths.get(target),
                            StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Update applied: " + newVersion);
                    return;
                } catch (Exception ignored) {
                    // Expected on Windows — fall through to batch approach
                }

                // Batch-file polling swap: detached cmd process polls until the
                // JVM has fully exited and released the file lock, then swaps.
                scheduleWindowsBatch(pending, target, modsDir);

                if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
                    LOGGER.error("Could not apply update on non-Windows — manual reinstall needed.");
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
