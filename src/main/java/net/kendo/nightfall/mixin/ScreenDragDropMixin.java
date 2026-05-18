package net.kendo.nightfall.mixin;

import net.kendo.nightfall.SkinChangerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(Minecraft.class)
public class ScreenDragDropMixin {
    private GLFWDropCallback previousCallback;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;

        if (screen instanceof SkinChangerScreen) {
            if (client.getWindow() != null) {
                long windowHandle = client.getWindow().getWindow();
                previousCallback = GLFW.glfwSetDropCallback(windowHandle, null);
                GLFW.glfwSetDropCallback(windowHandle, (window, count, names) -> {
                    List<String> paths = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        paths.add(GLFWDropCallback.getName(names, i));
                    }
                    if (client.screen instanceof SkinChangerScreen skinScreen) {
                        skinScreen.onFilesDragged(paths);
                    }
                });
            }
        } else if (previousCallback != null) {
            if (client.getWindow() != null) {
                long windowHandle = client.getWindow().getWindow();
                GLFW.glfwSetDropCallback(windowHandle, previousCallback);
                previousCallback = null;
            }
        }
    }
}
