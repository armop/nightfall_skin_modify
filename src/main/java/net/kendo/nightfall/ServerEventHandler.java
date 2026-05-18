package net.kendo.nightfall;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NightfallSkin.MOD_ID)
public class ServerEventHandler {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        NightfallSkin.LOGGER.info("Player {} joined the server", event.getEntity().getName().getString());
        // Client will request skins itself
    }
}
