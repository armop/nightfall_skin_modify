package net.kendo.nightfall;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.network.chat.Component;

@OnlyIn(Dist.CLIENT)
public class SkinRenameScreen extends Screen {
    private final Screen parent;
    private final SkinHistory.SkinEntry entry;
    private EditBox nameField;
    private boolean confirmingDelete = false;

    public SkinRenameScreen(Screen parent, SkinHistory.SkinEntry entry) {
        super(Component.literal("Rename Skin"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (!confirmingDelete) {
            nameField = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, Component.literal("Name"));
            String currentName = entry.getCustomName() != null ? entry.getCustomName() : entry.getFileName();
            nameField.setValue(currentName);
            nameField.setMaxLength(50);
            nameField.setFocused(true);
            this.addWidget(nameField);
            this.setInitialFocus(nameField);

            this.addRenderableWidget(Button.builder(Component.literal("§aSave"), button -> {
                String newName = nameField.getValue().trim();
                if (!newName.isEmpty()) entry.setCustomName(newName);
                this.onClose();
            }).bounds(centerX - 102, centerY + 25, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("§7Cancel"), button -> this.onClose())
                    .bounds(centerX + 2, centerY + 25, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("§cDelete Skin"), button -> {
                confirmingDelete = true;
                this.rebuildWidgets();
            }).bounds(centerX - 100, centerY + 55, 200, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("§c§lConfirm Delete"), button -> {
                SkinHistory.removeSkin(entry);
                this.onClose();
            }).bounds(centerX - 102, centerY + 10, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("§aCancel"), button -> {
                confirmingDelete = false;
                this.rebuildWidgets();
            }).bounds(centerX + 2, centerY + 10, 100, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (!confirmingDelete) {
            context.fill(centerX - 120, centerY - 50, centerX + 120, centerY + 90, 0xCC000000);
            context.fill(centerX - 120, centerY - 50, centerX + 120, centerY - 48, 0xFF888888);
            context.fill(centerX - 120, centerY + 88, centerX + 120, centerY + 90, 0xFF888888);
            context.fill(centerX - 120, centerY - 50, centerX - 118, centerY + 90, 0xFF888888);
            context.fill(centerX + 118, centerY - 50, centerX + 120, centerY + 90, 0xFF888888);
            context.drawCenteredString(this.font, "§lRename Skin", centerX, centerY - 40, 0xFFFFFF);
            context.drawCenteredString(this.font, "§7Enter a new name:", centerX, centerY - 25, 0xAAAAAA);
            if (nameField != null) nameField.render(context, mouseX, mouseY, delta);
        } else {
            context.fill(centerX - 120, centerY - 40, centerX + 120, centerY + 45, 0xCC000000);
            context.fill(centerX - 120, centerY - 40, centerX + 120, centerY - 38, 0xFFFF0000);
            context.fill(centerX - 120, centerY + 43, centerX + 120, centerY + 45, 0xFFFF0000);
            context.fill(centerX - 120, centerY - 40, centerX - 118, centerY + 45, 0xFFFF0000);
            context.fill(centerX + 118, centerY - 40, centerX + 120, centerY + 45, 0xFFFF0000);
            context.drawCenteredString(this.font, "§c§lDelete Skin?", centerX, centerY - 30, 0xFF0000);
            String skinName = entry.getDisplayName();
            if (this.font.width(skinName) > 220) skinName = skinName.substring(0, 20) + "...";
            context.drawCenteredString(this.font, "§7Delete: §f" + skinName, centerX, centerY - 10, 0xFFFFFF);
            context.drawCenteredString(this.font, "§7This cannot be undone!", centerX, centerY + 2, 0xFFAAAA);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
