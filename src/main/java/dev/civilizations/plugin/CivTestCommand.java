package dev.civilizations.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

final class CivTestCommand extends CommandBase {

    CivTestCommand() {
        super("civtest", "Checks whether the Civilizations plugin is loaded.");
        requireNoPermission();
    }

    @Override
    protected void executeSync(CommandContext context) {
        context.sendMessage(Message.raw("Civilizations smoke test OK."));
    }
}
