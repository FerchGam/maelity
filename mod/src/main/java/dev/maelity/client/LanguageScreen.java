package dev.maelity.client;

import dev.maelity.MaelityConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Lo unico que el jugador tiene que hacer: elegir Espanol o English. */
public class LanguageScreen extends Screen {
    public LanguageScreen() {
        super(Component.literal("Maelity"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        addRenderableWidget(Button.builder(Component.literal("Español"), b -> choose("es"))
                .bounds(cx - 105, cy, 100, 24).build());
        addRenderableWidget(Button.builder(Component.literal("English"), b -> choose("en"))
                .bounds(cx + 5, cy, 100, 24).build());
    }

    private void choose(String lang) {
        MaelityConfig.CLIENT.language.set(lang);
        MaelityConfig.CLIENT.languageChosen.set(true);
        MaelityConfig.CLIENT_SPEC.save();
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(this.font, "Maelity", this.width / 2, this.height / 2 - 50, 0xFFD60A);
        g.drawCenteredString(this.font, "Elige tu idioma / Choose your language", this.width / 2, this.height / 2 - 30, 0xFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
