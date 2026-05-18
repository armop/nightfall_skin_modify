package net.kendo.nightfall.mixin;

import com.mojang.authlib.GameProfile;
import net.kendo.nightfall.SkinManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void onGetSkinTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        GameProfile profile = player.getGameProfile();
        ResourceLocation customSkin = SkinManager.getCustomSkin(profile);
        if (customSkin != null) {
            cir.setReturnValue(customSkin);
            cir.cancel();
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void onGetModel(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        GameProfile profile = player.getGameProfile();
        ResourceLocation customSkin = SkinManager.getCustomSkin(profile);
        if (customSkin != null) {
            boolean isSlim = SkinManager.isSlimModel(profile);
            cir.setReturnValue(isSlim ? "slim" : "default");
            cir.cancel();
        }
    }
}
