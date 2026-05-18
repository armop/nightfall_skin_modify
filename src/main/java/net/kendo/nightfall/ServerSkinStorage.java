package net.kendo.nightfall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ServerSkinStorage {
    private static final String STORAGE_DIR = "skinchanger_storage";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class SkinMetadata {
        public String uuid;
        public String model;
        public long uploadedTimestamp;
        public String fileName;

        public SkinMetadata() {}

        public SkinMetadata(UUID uuid, boolean isSlim) {
            this.uuid = uuid.toString();
            this.model = isSlim ? "slim" : "wide";
            this.uploadedTimestamp = System.currentTimeMillis();
            this.fileName = null;
        }

        public boolean isSlim() {
            return "slim".equals(model);
        }
    }

    public static class SkinData {
        public final byte[] imageData;
        public final boolean isSlim;

        public SkinData(byte[] imageData, boolean isSlim) {
            this.imageData = imageData;
            this.isSlim = isSlim;
        }
    }

    public static void initialize() {
        try {
            Path storageDir = Paths.get(STORAGE_DIR);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
                NightfallSkin.LOGGER.info("Created skin storage directory: {}", storageDir.toAbsolutePath());
            }
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to create storage directory", e);
        }
    }

    public static boolean saveSkin(UUID playerUuid, byte[] imageData, boolean isSlim) {
        try {
            Path playerDir = Paths.get(STORAGE_DIR, playerUuid.toString());
            Files.createDirectories(playerDir);

            long timestamp = System.currentTimeMillis();
            String fileName = "skin_" + timestamp + ".png";

            Path skinPath = playerDir.resolve(fileName);
            Files.write(skinPath, imageData);

            SkinMetadata metadata = new SkinMetadata(playerUuid, isSlim);
            metadata.fileName = fileName;
            Path metadataPath = playerDir.resolve("metadata.json");
            Files.writeString(metadataPath, GSON.toJson(metadata));

            NightfallSkin.LOGGER.info("Saved skin for player {} ({} bytes, model: {})", playerUuid, imageData.length, metadata.model);
            return true;
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to save skin for player " + playerUuid, e);
            return false;
        }
    }

    public static SkinData loadSkin(UUID playerUuid) {
        try {
            Path playerDir = Paths.get(STORAGE_DIR, playerUuid.toString());
            if (!Files.exists(playerDir)) return null;

            Path metadataPath = playerDir.resolve("metadata.json");
            if (!Files.exists(metadataPath)) return null;

            SkinMetadata metadata = GSON.fromJson(Files.readString(metadataPath), SkinMetadata.class);
            Path skinPath = playerDir.resolve(metadata.fileName);
            if (!Files.exists(skinPath)) return null;

            byte[] imageData = Files.readAllBytes(skinPath);
            return new SkinData(imageData, metadata.isSlim());
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to load skin for player " + playerUuid, e);
            return null;
        }
    }

    public static boolean hasSkin(UUID playerUuid) {
        try {
            Path metadataPath = Paths.get(STORAGE_DIR, playerUuid.toString(), "metadata.json");
            if (!Files.exists(metadataPath)) return false;

            SkinMetadata metadata = GSON.fromJson(Files.readString(metadataPath), SkinMetadata.class);
            if (metadata.fileName == null) return false;

            return Files.exists(Paths.get(STORAGE_DIR, playerUuid.toString(), metadata.fileName));
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean deleteSkin(UUID playerUuid) {
        try {
            Path playerDir = Paths.get(STORAGE_DIR, playerUuid.toString());
            if (!Files.exists(playerDir)) return false;

            Files.walk(playerDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException ignored) {}
                    });
            return true;
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to delete skin for player " + playerUuid, e);
            return false;
        }
    }

    public static List<UUID> getAllStoredPlayerUuids() {
        List<UUID> uuids = new ArrayList<>();
        try {
            Path storageDir = Paths.get(STORAGE_DIR);
            if (!Files.exists(storageDir)) return uuids;

            Files.list(storageDir)
                    .filter(Files::isDirectory)
                    .forEach(dir -> {
                        try {
                            UUID uuid = UUID.fromString(dir.getFileName().toString());
                            if (hasSkin(uuid)) uuids.add(uuid);
                        } catch (IllegalArgumentException ignored) {}
                    });
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to list stored skins", e);
        }
        return uuids;
    }

    public static SkinMetadata getMetadata(UUID playerUuid) {
        try {
            Path metadataPath = Paths.get(STORAGE_DIR, playerUuid.toString(), "metadata.json");
            if (!Files.exists(metadataPath)) return null;
            return GSON.fromJson(Files.readString(metadataPath), SkinMetadata.class);
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to read metadata for player " + playerUuid, e);
            return null;
        }
    }

    public static long getStorageSize() {
        try {
            Path storageDir = Paths.get(STORAGE_DIR);
            if (!Files.exists(storageDir)) return 0;

            return Files.walk(storageDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try { return Files.size(path); } catch (IOException e) { return 0; }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }
}
