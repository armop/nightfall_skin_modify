package net.kendo.nightfall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ModelPreferenceManager {
    private static final File PREFERENCE_FILE = new File("skinchanger_model_preference.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean isSlimPreference = false;

    static {
        loadPreference();
    }

    public static class ModelPreference {
        boolean isSlim;
        ModelPreference(boolean isSlim) { this.isSlim = isSlim; }
    }

    public static boolean isSlimPreference() {
        return isSlimPreference;
    }

    public static void setSlimPreference(boolean isSlim) {
        isSlimPreference = isSlim;
        savePreference();
        NightfallSkin.LOGGER.info("Model preference set to: {}", isSlim ? "Slim" : "Wide");
    }

    private static void savePreference() {
        try {
            ModelPreference pref = new ModelPreference(isSlimPreference);
            try (FileWriter writer = new FileWriter(PREFERENCE_FILE)) {
                GSON.toJson(pref, writer);
            }
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to save model preference", e);
        }
    }

    private static void loadPreference() {
        try {
            if (!PREFERENCE_FILE.exists()) return;
            try (FileReader reader = new FileReader(PREFERENCE_FILE)) {
                ModelPreference pref = GSON.fromJson(reader, ModelPreference.class);
                if (pref != null) {
                    isSlimPreference = pref.isSlim;
                }
            }
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to load model preference", e);
        }
    }
}
