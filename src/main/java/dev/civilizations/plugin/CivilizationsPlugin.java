package dev.civilizations.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public final class CivilizationsPlugin extends JavaPlugin {

    public CivilizationsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        getCommandRegistry().registerCommand(new CivTestCommand());
    }
}
