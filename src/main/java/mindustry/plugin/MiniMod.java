package mindustry.plugin;

import arc.util.CommandHandler;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;

/**
 * A mini-module implements a subset of the functionality of the plugin.
 * A mini-module should not reference any other mini-modules, and instead only interface
 * with common utility code.
 */
public interface MiniMod {
    /** Register client (game) commands */
    void registerCommands(CommandHandler handler);

    /** Register server commands */
    default void registerServerCommands(CommandHandler handler) {}

    /** Initialize event handlers + timers */
    default void registerEvents() {}

    default void registerDiscordCommands(DiscordRegistrar handler) {}
}