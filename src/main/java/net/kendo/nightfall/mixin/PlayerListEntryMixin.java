package net.kendo.nightfall.mixin;

import com.mojang.authlib.GameProfile;
import net.kendo.nightfall.SkinManager;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerListEntryMixin {

    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkinLocation", at = @At("HEAD"), cancellable = true)
    private void onGetSkinTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        GameProfile profile = this.getProfile();
        ResourceLocation customSkin = SkinManager.getCustomSkin(profile);
        if (customSkin != null) {
            cir.setReturnValue(customSkin);
            cir.cancel();
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void onGetModel(CallbackInfoReturnable<String> cir) {
        GameProfile profile = this.getProfile();
        ResourceLocation customSkin = SkinManager.getCustomSkin(profile);
        if (customSkin != null) {
            boolean isSlim = SkinManager.isSlimModel(profile);
            cir.setReturnValue(isSlim ? "slim" : "default");
            cir.cancel();
        }
    }
}
