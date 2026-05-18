package net.kendo.nightfall;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = NightfallSkin.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NightfallSkinClient {
    public static KeyMapping openGuiKey;

    @SuppressWarnings("removal")
    public static void init() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(NightfallSkinClient::onClientSetup);
        modEventBus.addListener(NightfallSkinClient::registerKeyMappings);
        MinecraftForge.EVENT_BUS.register(NightfallSkinClient.class);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NightfallSkin.LOGGER.info("Skin Changer Client Initialized!");
        net.kendo.nightfall.network.SkinNetworkHandler.registerClientHandlers();
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openGuiKey = new KeyMapping(
                "key.nightfall_skin.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "category.nightfall_skin.general"
        );
        event.register(openGuiKey);
    }
}
