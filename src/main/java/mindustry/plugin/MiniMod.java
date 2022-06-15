package mindustry.plugin;

import arc.util.CommandHandler;

/** A mini-module implements a subset of the functionality of the plugin.
    A mini-module should not reference any other mini-modules, and instead only interface
    with common utility code.
 */
public interface MiniMod {
    void registerCommands(CommandHandler handler);

    default void registerEvents() {}
}