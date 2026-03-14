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
 *   1. Apply any .pending update left over from a previous crashed session.
 *   2. Check GitHub for a newer version.
 *   3. If found, download to customitemsk.jar.pending (no file-lock conflict).
 *   4. Register a JVM shutdown hook that moves .pending → customitemsk.jar
 *      after the JVM has released all file locks (works on Windows too).
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
        applyPendingUpdate();   // Apply any leftover .pending from a crashed session

        new Thread(() -> {
            try { checkForUpdates(); }
            catch (Exception e) { LOGGER.error("Update check failed: " + e.getMessage()); }
        }, "CustomItemsK-Updater").start();
    }

    // -------------------------------------------------------------------------
    // Apply a previously-downloaded pending update (crash-recovery)
    // -------------------------------------------------------------------------

    private void applyPendingUpdate() {
        try {
            Path modsDir    = FabricLoader.getInstance().getGameDir().resolve("mods");
            Path pendingPath = modsDir.resolve(MOD_FILENAME + ".pending");
            Path targetPath  = modsDir.resolve(MOD_FILENAME);

            if (Files.exists(pendingPath)) {
                Files.move(pendingPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Applied pending update from previous session — restart may be needed.");
            }
        } catch (Exception e) {
            // File still locked or missing — not a hard error
            LOGGER.debug("Could not apply .pending on startup: " + e.getMessage());
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
    // Download update to .pending, then apply via shutdown hook
    // -------------------------------------------------------------------------

    private void downloadUpdate(String downloadUrl, String newVersion) {
        try {
            LOGGER.info("Downloading update " + newVersion + "...");

            Path modsDir     = FabricLoader.getInstance().getGameDir().resolve("mods");
            Path targetPath  = modsDir.resolve(MOD_FILENAME);
            Path pendingPath = modsDir.resolve(MOD_FILENAME + ".pending");

            // Download to .pending — avoids touching the locked .jar
            URLConnection conn = new URL(downloadUrl).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, pendingPath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!Files.exists(pendingPath) || Files.size(pendingPath) < 1000) {
                throw new Exception("Downloaded file too small — possible corruption");
            }

            // Signal the client-side UI that an update is waiting
            updateReady   = true;
            pendingVersion = newVersion;

            // Shutdown hook: runs after JVM releases all file locks (works on Windows)
            String pending = pendingPath.toAbsolutePath().toString();
            String target  = targetPath.toAbsolutePath().toString();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.move(Paths.get(pending), Paths.get(target),
                            StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Update applied: " + newVersion);
                } catch (Exception e) {
                    // Windows fallback: launch a delayed OS command after JVM exits
                    if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                        try {
                            new ProcessBuilder("cmd", "/c",
                                    "timeout /t 3 >nul && move /y \"" + pending + "\" \"" + target + "\"")
                                    .start();
                        } catch (Exception pe) {
                            LOGGER.error("OS fallback failed: " + pe.getMessage());
                        }
                    } else {
                        LOGGER.error("Could not apply update: " + e.getMessage());
                    }
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
