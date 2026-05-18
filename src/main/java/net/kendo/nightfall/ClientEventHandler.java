package net.kendo.nightfall;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NightfallSkin.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft client = Minecraft.getInstance();
        if (NightfallSkinClient.openGuiKey != null) {
            while (NightfallSkinClient.openGuiKey.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    client.setScreen(new SkinChangerScreen());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Auto-apply last used skin when joining a world/server
        Minecraft client = Minecraft.getInstance();
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            client.execute(() -> {
                SkinHistory.SkinEntry lastSkin = SkinHistory.getMostRecentSkin();
                if (lastSkin != null && lastSkin.getFile().exists()) {
                    try {
                        NightfallSkin.LOGGER.info("Auto-applying last used skin: {}", lastSkin.getDisplayName());
                        java.awt.image.BufferedImage skinImage = javax.imageio.ImageIO.read(lastSkin.getFile());
                        if (skinImage != null) {
                            boolean useSlim = ModelPreferenceManager.isSlimPreference();
                            SkinManager.applySkin(client, skinImage, useSlim);
                            NightfallSkin.LOGGER.info("Successfully reapplied skin on startup");
                        }
                    } catch (Exception e) {
                        NightfallSkin.LOGGER.error("Failed to auto-apply last skin", e);
                    }
                }

                // Request list of players with custom skins from server
                try {
                    Thread.sleep(500);
                    net.kendo.nightfall.network.SkinNetworkHandler.requestSkinList();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }).start();
    }
}
