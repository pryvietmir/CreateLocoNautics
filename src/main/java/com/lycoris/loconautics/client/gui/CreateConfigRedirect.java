package com.lycoris.loconautics.client.gui;

import com.lycoris.loconautics.mixin.client.gui.SubMenuConfigScreenAccessor;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.SubMenuConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreenList.LabeledEntry;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.DelegatedStencilElement;
import net.createmod.catnip.gui.element.FadableScreenElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.client.event.ScreenEvent.Init.Post;

public final class CreateConfigRedirect {
    private static final String CREATE_ID = "create";
    private static final String TRAINS_TITLE = "Trains";

    private CreateConfigRedirect() {
    }

    public static void onScreenInit(Post event) {
        if (event.getScreen() instanceof SubMenuConfigScreen screen && screen.type == Type.SERVER && "create".equals(ConfigScreen.modID)) {
            SubMenuConfigScreenAccessor access = (SubMenuConfigScreenAccessor)screen;
            ConfigScreenList list = access.loconautics$getList();
            if ("Trains".equals(access.loconautics$getTitle()) && list != null) {
                list.children().add(0, new CreateConfigRedirect.LoconauticsEntry(screen));
            }
        }
    }

    private static final class LoconauticsEntry extends LabeledEntry {
        private final BoxWidget button = new BoxWidget(0, 0, 35, 16);

        private LoconauticsEntry(Screen parent) {
            super(Component.translatable("loconautics.configuration.open_from_create").getString());
            this.button.showingElement(PonderGuiTextures.ICON_CONFIG_OPEN.asStencil().at(10.0F, 0.0F));
            this.button
                .modifyElement(e -> ((DelegatedStencilElement)e).withElementRenderer((FadableScreenElement)BoxWidget.gradientFactory.apply(this.button)));
            this.button.withCallback(() -> ScreenOpener.open(new BaseConfigScreen(parent, "loconautics")));
            this.listeners.add(this.button);
        }

        public void tick() {
            super.tick();
            this.button.tick();
        }

        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);
            this.button.setX(x + width - 108);
            this.button.setY(y + 10);
            this.button.setHeight(height - 20);
            this.button.render(graphics, mouseX, mouseY, partialTicks);
        }

        protected int getLabelWidth(int totalWidth) {
            return (int)(totalWidth * 0.4F) + 30;
        }
    }
}
