package mindustry.plugin.discordcommands;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import mindustry.plugin.ioMain;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

/** Represents a registry of commands */
public class DiscordCommands implements MessageCreateListener {
    private HashMap<String, Command> registry = new HashMap<>();
    private Set<MessageCreatedListener> messageCreatedListenerRegistry = new HashSet<>();
    public DiscordCommands() {
        // stuff
    }
    /**
     * Register a command in the CommandRegistry
     * @param c The command
     */
    public void registerCommand(Command c) {
        registry.put(c.name.toLowerCase(), c);
    }
    // you can override the name of the command manually, for example for aliases
    /**
     * Register a command in the CommandRegistry
     * @param forcedName Register the command under another name
     * @param c The command to register
     */
    public void registerCommand(String forcedName, Command c) {
        registry.put(forcedName.toLowerCase(), c);
    }
    /**
     * Register a method to be run when a message is created.
     * @param listener MessageCreatedListener to be run when a message is created.
     */
    public void registerOnMessage(MessageCreatedListener listener) {
        messageCreatedListenerRegistry.add(listener);
    }
    /**
     * Parse and run a command
     * @param event Source event associated with the message
     */
    public void onMessageCreate(MessageCreateEvent event) {
        for(MessageCreatedListener listener: messageCreatedListenerRegistry) listener.run(event);

        String message = event.getMessageContent();
        if (!message.startsWith(ioMain.prefix)) return;
        String[] args = message.split(" ");
        int commandLength = args[0].length();
        args[0] = args[0].substring(ioMain.prefix.length());
        String name = args[0];

        String newMessage = null;
        if (args.length > 1) newMessage = message.substring(commandLength + 1);
        runCommand(name, new Context(event, args, newMessage));
    }
    /**
     * Run a command
     * @param name
     * @param ctx
     */
    public void runCommand(String name, Context ctx) {
        Command command = registry.get(name.toLowerCase());
        if (command == null) {
            return;
        }
        if (!command.hasPermission(ctx)) {
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("No permissions!")
                    .setDescription("You need higher permissions to execute this command.");
            ctx.channel.sendMessage(eb);
            return;
        }
        command.run(ctx);
    }
    /**
     * Get a command by name
     * @param name
     * @return
     */
    public Command getCommand(String name) {
        return registry.get(name.toLowerCase());
    }
    /**
     * Get all commands in the registry
     * @return
     */
    public Collection<Command> getAllCommands() {
        return registry.values();
    }
    /**
     * Check if a command exists in the registry
     * @param name
     * @return
     */
    public boolean isCommand(String name) {
        return registry.containsKey(name.toLowerCase());
    }
}