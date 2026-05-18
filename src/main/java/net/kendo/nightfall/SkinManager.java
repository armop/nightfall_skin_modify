package net.kendo.nightfall;

import com.mojang.authlib.GameProfile;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@OnlyIn(Dist.CLIENT)
public class SkinManager {
    private static final Map<UUID, SkinData> customSkins = new HashMap<>();
    private static ResourceLocation currentCustomSkin = null;
    private static boolean currentIsSlim = false;

    public static class SkinData {
        public final ResourceLocation textureId;
        public final boolean isSlim;
        public final byte[] imageData;

        public SkinData(ResourceLocation textureId, boolean isSlim, byte[] imageData) {
            this.textureId = textureId;
            this.isSlim = isSlim;
            this.imageData = imageData;
        }
    }

    public static CompletableFuture<ResourceLocation> applySkinFromUrl(Minecraft client, String url, boolean isSlim) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                NightfallSkin.LOGGER.info("Downloading skin from URL: {}", url);
                BufferedImage skinImage = downloadImageFromUrl(url);

                if (skinImage == null) throw new RuntimeException("Failed to download image");
                if (!isValidSkinDimensions(skinImage)) throw new RuntimeException("Invalid skin dimensions");

                File cachedFile = saveSkinToCache(skinImage, url);
                final BufferedImage finalImage = skinImage;
                final File finalCachedFile = cachedFile;

                return client.submit(() -> {
                    ResourceLocation textureId = applySkin(client, finalImage, isSlim);
                    if (textureId != null && finalCachedFile != null) {
                        SkinHistory.addSkin(finalCachedFile, textureId, isSlim);
                    }
                    return textureId;
                }).join();
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply skin from URL: " + e.getMessage());
            }
        });
    }

    private static File saveSkinToCache(BufferedImage image, String url) {
        try {
            File cacheDir = new File("skinchanger_cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            String fileName = "url_" + Math.abs(url.hashCode()) + "_" + System.currentTimeMillis() + ".png";
            File cacheFile = new File(cacheDir, fileName);
            ImageIO.write(image, "PNG", cacheFile);
            return cacheFile;
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to cache skin", e);
            return null;
        }
    }

    private static BufferedImage downloadImageFromUrl(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) throw new IOException("HTTP error code: " + responseCode);

        try (InputStream inputStream = connection.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) throw new IOException("Failed to decode image");
            return image;
        }
    }

    private static boolean isValidSkinDimensions(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width != height) return false;
        if (width < 64 || width > 512) return false;
        return width % 64 == 0;
    }

    public static ResourceLocation applySkin(Minecraft client, BufferedImage skinImage, boolean isSlim) {
        try {
            if (client.player == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(skinImage, "PNG", baos);
            byte[] imageData = baos.toByteArray();

            // Convert to NativeImage equivalent (using DynamicTexture)
            com.mojang.blaze3d.platform.NativeImage nativeImage = convertToNativeImage(skinImage);
            ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(NightfallSkin.MOD_ID, "skin_" + System.currentTimeMillis());

            DynamicTexture texture = new DynamicTexture(nativeImage);
            client.getTextureManager().register(textureId, texture);

            UUID playerUuid = client.player.getUUID();
            customSkins.put(playerUuid, new SkinData(textureId, isSlim, imageData));

            currentCustomSkin = textureId;
            currentIsSlim = isSlim;

            NightfallSkin.LOGGER.info("Applied custom skin locally: {} (Model: {})", textureId, isSlim ? "Slim" : "Classic");

            // Send to server if in multiplayer
            if (client.getConnection() != null) {
                sendSkinToServer(client, imageData, isSlim);
            }

            return textureId;
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to apply skin", e);
            throw new RuntimeException("Failed to apply skin: " + e.getMessage());
        }
    }

    private static void sendSkinToServer(Minecraft client, byte[] imageData, boolean isSlim) {
        try {
            if (client.player == null) return;
            NightfallSkin.LOGGER.info("Uploading skin to server ({} bytes, slim={})", imageData.length, isSlim);
            net.kendo.nightfall.network.SkinNetworkHandler.uploadSkinToServer(imageData, isSlim);
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to upload skin to server", e);
        }
    }

    public static void applyRemoteSkin(Minecraft client, UUID playerUuid, byte[] imageData, boolean isSlim) {
        try {
            BufferedImage skinImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (skinImage == null) return;

            com.mojang.blaze3d.platform.NativeImage nativeImage = convertToNativeImage(skinImage);
            ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(NightfallSkin.MOD_ID, "remote_" + playerUuid.toString().replace("-", ""));

            // Cleanup old texture
            SkinData oldSkin = customSkins.get(playerUuid);
            if (oldSkin != null && oldSkin.textureId != null) {
                try { client.getTextureManager().release(oldSkin.textureId); } catch (Exception ignored) {}
            }

            DynamicTexture texture = new DynamicTexture(nativeImage);
            client.getTextureManager().register(textureId, texture);
            customSkins.put(playerUuid, new SkinData(textureId, isSlim, imageData));

            NightfallSkin.LOGGER.info("Applied remote skin for player {}", playerUuid);
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to apply remote skin for player " + playerUuid, e);
        }
    }

    public static void resetSkin(Minecraft client) {
        if (client.player == null) return;
        UUID playerUuid = client.player.getUUID();

        if (currentCustomSkin != null) {
            try { client.getTextureManager().release(currentCustomSkin); } catch (Exception ignored) {}
            currentCustomSkin = null;
        }

        customSkins.remove(playerUuid);
        currentIsSlim = false;

        if (client.getConnection() != null) {
            net.kendo.nightfall.network.SkinNetworkHandler.sendSkinReset();
        }
    }

    public static ResourceLocation getCustomSkin(GameProfile profile) {
        if (profile == null || profile.getId() == null) return null;
        SkinData skinData = customSkins.get(profile.getId());
        return skinData != null ? skinData.textureId : null;
    }

    public static boolean isSlimModel(GameProfile profile) {
        if (profile == null || profile.getId() == null) return false;
        SkinData skinData = customSkins.get(profile.getId());
        return skinData != null && skinData.isSlim;
    }

    public static ResourceLocation getCurrentCustomSkin() { return currentCustomSkin; }
    public static boolean isCurrentSlim() { return currentIsSlim; }
    public static boolean hasCustomSkin(UUID playerUuid) { return customSkins.containsKey(playerUuid); }
    public static SkinData getSkinData(UUID playerUuid) { return customSkins.get(playerUuid); }

    private static com.mojang.blaze3d.platform.NativeImage convertToNativeImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        com.mojang.blaze3d.platform.NativeImage nativeImage =
                new com.mojang.blaze3d.platform.NativeImage(width, height, true);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = bufferedImage.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                // Forge/LWJGL uses ABGR
                int abgr = (alpha << 24) | (blue << 16) | (green << 8) | red;
                nativeImage.setPixelRGBA(x, y, abgr);
            }
        }
        return nativeImage;
    }

    public static void removeRemoteSkin(UUID playerUuid) {
        SkinData oldSkin = customSkins.remove(playerUuid);
        if (oldSkin != null && oldSkin.textureId != null) {
            try {
                Minecraft client = Minecraft.getInstance();
                if (client != null) client.getTextureManager().release(oldSkin.textureId);
            } catch (Exception ignored) {}
        }
    }

    public static void clearAllSkins() {
        customSkins.clear();
        currentCustomSkin = null;
        currentIsSlim = false;
    }
}
