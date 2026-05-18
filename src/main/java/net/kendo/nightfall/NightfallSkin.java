package net.kendo.nightfall;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(NightfallSkin.MOD_ID)
public class NightfallSkin {
    public static final String MOD_ID = "nightfall_skin";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public NightfallSkin() {
        // Forge 47.x ยังต้องการ no-arg constructor — ดึง IEventBus ผ่าน FMLJavaModLoadingContext
        @SuppressWarnings("removal")
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        ModSounds.SOUNDS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Skin Changer Mod Initialized!");
        ServerSkinStorage.initialize();
        net.kendo.nightfall.network.SkinNetworkHandler.registerChannels();
        long storageSize = ServerSkinStorage.getStorageSize();
        int skinCount = ServerSkinStorage.getAllStoredPlayerUuids().size();
        LOGGER.info("Loaded {} stored skins ({} bytes total)", skinCount, storageSize);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting - Skin Changer ready");
    }
}
