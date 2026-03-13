package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.util.Scanner;

/**
 * Connector Mod - Auto-updater for CustomItemsK
 * Checks GitHub for updates on startup and downloads new versions automatically
 */
public class ConnectorMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("customitemsk-connector");
    
    // GitHub Raw URL for version manifest
    private static final String GITHUB_USER = "hamsterioip";
    private static final String GITHUB_REPO = "customitemsk";
    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/" + GITHUB_USER + "/" + GITHUB_REPO + "/main/version.json";
    private static final String MOD_FILENAME = "customitemsk.jar";
    private static final String MOD_ID = "customitemsk";

    @Override
    public void onInitialize() {
        LOGGER.info("CustomItemsK Connector starting...");
        
        // Run update check in separate thread to avoid blocking game startup
        new Thread(() -> {
            try {
                checkForUpdates();
            } catch (Exception e) {
                LOGGER.error("Update check failed: " + e.getMessage());
            }
        }, "CustomItemsK-Updater").start();
    }
    
    private void checkForUpdates() {
        LOGGER.info("Checking for updates...");
        
        try {
            // Download version manifest
            String content = downloadString(MANIFEST_URL);
            if (content == null || content.isEmpty()) {
                LOGGER.warn("Could not retrieve version manifest");
                return;
            }
            
            // Parse JSON (simple string split for lightweight parsing)
            String remoteVersion = extractJsonField(content, "version");
            String downloadUrl = extractJsonField(content, "downloadUrl");
            
            if (remoteVersion == null || downloadUrl == null) {
                LOGGER.error("Invalid version manifest format");
                return;
            }
            
            // Get current local version
            String localVersion = FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("0.0.0");
            
            LOGGER.info("Local version: " + localVersion + " | Remote version: " + remoteVersion);
            
            // Compare versions
            if (isNewerVersion(remoteVersion, localVersion)) {
                LOGGER.info("§aNew version available: " + remoteVersion);
                downloadUpdate(downloadUrl, remoteVersion);
            } else {
                LOGGER.info("§aMod is up to date (" + localVersion + ")");
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to check for updates: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String downloadString(String urlString) {
        try {
            URL url = new URL(urlString);
            try (Scanner scanner = new Scanner(url.openStream()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        } catch (Exception e) {
            LOGGER.error("Failed to download: " + urlString);
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
            if (end == -1) return null;
            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean isNewerVersion(String remote, String local) {
        // Simple version comparison (e.g., "1.2.3" vs "1.2.2")
        String[] remoteParts = remote.replace("v", "").split("\\.");
        String[] localParts = local.replace("v", "").split("\\.");
        
        int maxLength = Math.max(remoteParts.length, localParts.length);
        
        for (int i = 0; i < maxLength; i++) {
            int remotePart = i < remoteParts.length ? parseVersionPart(remoteParts[i]) : 0;
            int localPart = i < localParts.length ? parseVersionPart(localParts[i]) : 0;
            
            if (remotePart > localPart) return true;
            if (remotePart < localPart) return false;
        }
        
        return false; // Equal versions
    }
    
    private int parseVersionPart(String part) {
        try {
            // Handle version parts like "3-beta" -> 3
            String clean = part.replaceAll("[^0-9]", "");
            return clean.isEmpty() ? 0 : Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private void downloadUpdate(String downloadUrl, String newVersion) {
        try {
            LOGGER.info("§eDownloading update: " + newVersion + "...");
            
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            Path targetPath = modsDir.resolve(MOD_FILENAME);
            Path backupPath = modsDir.resolve(MOD_FILENAME + ".backup");
            
            // Create backup of current version
            if (Files.exists(targetPath)) {
                try {
                    Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Backup created");
                } catch (Exception e) {
                    LOGGER.warn("Could not create backup: " + e.getMessage());
                }
            }
            
            // Download new version
            URL url = new URL(downloadUrl);
            try (InputStream in = url.openStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Verify download
            if (Files.exists(targetPath) && Files.size(targetPath) > 1000) {
                LOGGER.info("§a§l========================================");
                LOGGER.info("§a§l  UPDATE DOWNLOADED SUCCESSFULLY!");
                LOGGER.info("§a§l  Version: " + newVersion);
                LOGGER.info("§a§l========================================");
                LOGGER.info("§e§lPlease restart Minecraft to apply the update!");
                
                // You could also show a toast notification or chat message here
                // if you have access to the player entity
            } else {
                throw new Exception("Downloaded file is too small or missing");
            }
            
        } catch (Exception e) {
            LOGGER.error("§cFailed to download update: " + e.getMessage());
            e.printStackTrace();
            
            // Try to restore backup
            try {
                Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
                Path targetPath = modsDir.resolve(MOD_FILENAME);
                Path backupPath = modsDir.resolve(MOD_FILENAME + ".backup");
                
                if (Files.exists(backupPath) && !Files.exists(targetPath)) {
                    Files.move(backupPath, targetPath);
                    LOGGER.info("Restored backup version");
                }
            } catch (Exception restoreError) {
                LOGGER.error("Could not restore backup: " + restoreError.getMessage());
            }
        }
    }
}
