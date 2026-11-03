package dev.maelity.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Solo cliente: activa el boton "Config" del mod en la lista de mods (Mods > Maelity > Config). */
public final class ConfigScreenSetup {
    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private ConfigScreenSetup() {}
}
