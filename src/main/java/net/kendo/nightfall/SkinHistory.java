package net.kendo.nightfall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class SkinHistory {
    private static final int MAX_HISTORY = 10;
    private static final List<SkinEntry> history = new ArrayList<>();
    private static final File HISTORY_FILE = new File("skinchanger_history.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static { loadHistory(); }

    public static void addSkin(File file, ResourceLocation textureId, boolean isSlim) {
        history.removeIf(entry -> entry.getFile().getAbsolutePath().equals(file.getAbsolutePath()));
        history.add(0, new SkinEntry(file, textureId, isSlim, Instant.now()));
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
        saveHistory();
    }

    public static List<SkinEntry> getHistory() { return new ArrayList<>(history); }

    public static SkinEntry getMostRecentSkin() {
        return history.isEmpty() ? null : history.get(0);
    }

    public static void removeSkin(SkinEntry entry) {
        history.remove(entry);
        saveHistory();
    }

    public static void clearHistory() {
        history.clear();
        saveHistory();
    }

    private static void saveHistory() {
        try {
            List<SerializableSkinEntry> serializableEntries = new ArrayList<>();
            for (SkinEntry entry : history) {
                serializableEntries.add(new SerializableSkinEntry(
                        entry.getFile().getAbsolutePath(),
                        entry.isSlim(),
                        entry.getCustomName(),
                        entry.getTimestamp().toEpochMilli()
                ));
            }
            try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
                GSON.toJson(serializableEntries, writer);
            }
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to save skin history", e);
        }
    }

    private static void loadHistory() {
        try {
            if (!HISTORY_FILE.exists()) return;
            try (FileReader reader = new FileReader(HISTORY_FILE)) {
                Type listType = new TypeToken<List<SerializableSkinEntry>>(){}.getType();
                List<SerializableSkinEntry> serializableEntries = GSON.fromJson(reader, listType);
                if (serializableEntries != null) {
                    for (SerializableSkinEntry serializable : serializableEntries) {
                        File file = new File(serializable.filePath);
                        if (file.exists()) {
                            ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(NightfallSkin.MOD_ID, "history_" + System.nanoTime());
                            SkinEntry entry = new SkinEntry(file, textureId, serializable.isSlim,
                                    Instant.ofEpochMilli(serializable.timestamp));
                            if (serializable.customName != null) entry.setCustomName(serializable.customName);
                            history.add(entry);
                        }
                    }
                }
            }
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to load skin history", e);
        }
    }

    private static class SerializableSkinEntry {
        String filePath;
        boolean isSlim;
        String customName;
        long timestamp;

        SerializableSkinEntry(String filePath, boolean isSlim, String customName, long timestamp) {
            this.filePath = filePath;
            this.isSlim = isSlim;
            this.customName = customName;
            this.timestamp = timestamp;
        }
    }

    public static class SkinEntry {
        private final File file;
        private ResourceLocation textureId;
        private final boolean isSlim;
        private final Instant timestamp;
        private String customName;

        public SkinEntry(File file, ResourceLocation textureId, boolean isSlim, Instant timestamp) {
            this.file = file;
            this.textureId = textureId;
            this.isSlim = isSlim;
            this.timestamp = timestamp;
            this.customName = null;
        }

        public File getFile() { return file; }
        public ResourceLocation getTextureId() { return textureId; }
        public void setTextureId(ResourceLocation textureId) { this.textureId = textureId; }
        public boolean isSlim() { return isSlim; }
        public String getFileName() { return file.getName(); }
        public String getCustomName() { return customName; }

        public void setCustomName(String name) {
            this.customName = name;
            saveHistory();
        }

        public String getDisplayName() {
            return customName != null ? customName : file.getName();
        }

        public String getTimeAgo() {
            Instant now = Instant.now();
            long minutes = ChronoUnit.MINUTES.between(timestamp, now);
            long hours = ChronoUnit.HOURS.between(timestamp, now);
            long days = ChronoUnit.DAYS.between(timestamp, now);
            if (minutes < 1) return "Just now";
            else if (minutes < 60) return minutes + "m ago";
            else if (hours < 24) return hours + "h ago";
            else if (days < 7) return days + "d ago";
            else return ">" + days + "d ago";
        }

        public Instant getTimestamp() { return timestamp; }
    }
}
