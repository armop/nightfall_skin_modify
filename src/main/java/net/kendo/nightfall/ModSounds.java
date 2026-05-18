package net.kendo.nightfall;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NightfallSkin.MOD_ID);

    public static final RegistryObject<SoundEvent> SKIN_CHANGE_1 = SOUNDS.register(
            "updateskin_fun",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(NightfallSkin.MOD_ID, "updateskin_fun"))
    );

    public static final RegistryObject<SoundEvent> SKIN_CHANGE_2 = SOUNDS.register(
            "updateskinka",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(NightfallSkin.MOD_ID, "updateskinka"))
    );
}
