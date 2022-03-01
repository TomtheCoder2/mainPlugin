package mindustry.plugin.discordcommands;

import mindustry.gen.Call;
import mindustry.plugin.ioMain;
import mindustry.plugin.requests.Translate;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.json.JSONObject;

import java.awt.*;
import java.util.*;

import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.*;

/**
 * Represents a registry of commands
 */
public class DiscordCommands implements MessageCreateListener {
    public static final TextChannel error_log_channel = getTextChannel("891677596117504020");
    private final Set<MessageCreatedListener> messageCreatedListenerRegistry = new HashSet<>();
    private final TextChannel admin_bot_channel = getTextChannel(admin_bot_channel_id);
    private final TextChannel staff_bot_channel = getTextChannel(staff_bot_channel_id);
    private final TextChannel bot_channel = getTextChannel(bot_channel_id);
    private final TextChannel apprentice_bot_channel = getTextChannel(ioMain.apprentice_bot_channel_id);
    private final TextChannel live_chat_channel;
    public HashMap<String, Command> registry = new HashMap<>();
    public HashMap<String, Command> aliasRegistry = new HashMap<>();


    public DiscordCommands() {
        // stuff
        if (!Objects.equals(live_chat_channel_id, "")) {
            live_chat_channel = getTextChannel(live_chat_channel_id);
        } else {
            live_chat_channel = getTextChannel("881300954845179914");
        }
    }

    // you can override the name of the command manually, for example for aliases

    /**
     * Register a command in the CommandRegistry
     *
     * @param c The command
     */
    public void registerCommand(Command c) {
        registry.put(c.name.toLowerCase(), c);

        for (String alias : c.aliases) {
            aliasRegistry.put(alias.toLowerCase(), c);
        }
    }

    /**
     * Register a command in the CommandRegistry
     *
     * @param forcedName Register the command under another name
     * @param c          The command to register
     */
    public void registerCommand(String forcedName, Command c) {
        registry.put(forcedName.toLowerCase(), c);
    }

    /**
     * Register a method to be run when a message is created.
     *
     * @param listener MessageCreatedListener to be run when a message is created.
     */
    public void registerOnMessage(MessageCreatedListener listener) {
        messageCreatedListenerRegistry.add(listener);
    }

    /**
     * Parse and run a command
     *
     * @param event Source event associated with the message
     */
    public void onMessageCreate(MessageCreateEvent event) {
        String message = event.getMessageContent();

        if (previewSchem) {
            if (checkIfSchem(event)) return;
        }

        // check if it's a live chat message
        if (event.getChannel().getId() == live_chat_channel.getId() && !event.getMessageAuthor().isBotUser()) {
            if (event.getMessageContent().split(" ")[0].substring(prefix.length()).equals("translate") || event.getMessageContent().split(" ")[0].substring(prefix.length()).equals("t")) {
                if (event.getMessageContent().split(" ").length < 3) {
                    event.getChannel().sendMessage(new EmbedBuilder()
                            .setTitle("Too few arguments!")
                            .setColor(new Color(0xff0000))
                    );
                    return;
                }
                try {
                    String[] args = message.split(" ", 3);
                    JSONObject res = new JSONObject(Translate.translate(escapeEverything(args[2]), args[1]));
                    if (res.has("translated") && res.getJSONObject("translated").has("text")) {
                        String translated = res.getJSONObject("translated").getString("text");
                        Call.sendMessage("[sky]" + event.getMessageAuthor().getName() + "@discord >[] " + translated);
                        event.getMessage().delete();
                        event.getChannel().sendMessage("<translated>**" + event.getMessageAuthor().getName() + "@discord**: " + translated);
                    } else {
                        event.getChannel().sendMessage(new EmbedBuilder()
                                .setTitle("There was an error!")
                                .setColor(new Color(0xff0000))
                                .setDescription("There was an error: " + (res.has("error") ? res.getString("error") : "No more information, ask Nautilus!")));
                    }
                } catch (Exception e) {
                    event.getChannel().sendMessage(new EmbedBuilder()
                            .setTitle("There was an error!")
                            .setColor(new Color(0xff0000))
                            .setDescription("There was an error: " + e.getMessage()));
                }
                return;
            }
            System.out.println(event.getMessageContent());
//            String message = event.getMessage().getContent().toString();
//            message = message.replaceAll("<@![0-9]+>", message.substring(message.indexOf("<@!") + 2, (message.indexOf("<@!") + 18)));
//            message = message.replaceAll("/<@![0-9]+>/gm", "Nautilus");
//            debug(message);
            Call.sendMessage("[sky]" + event.getMessageAuthor().getName() + "@discord >[] " + event.getMessage().getContent());
            return;
        }
        for (MessageCreatedListener listener : messageCreatedListenerRegistry) listener.run(event);

        // check if it's a command
        if (!message.startsWith(ioMain.prefix)) return;
        // get the arguments for the command
        String[] args = message.split(" ");
        int commandLength = args[0].length();
        args[0] = args[0].substring(ioMain.prefix.length());
        // command name
        String name = args[0];
        if (!isCommand(name)) return;

        // the message without the command name and the prefix
        String newMessage = null;
        if (args.length > 1) newMessage = message.substring(commandLength + 1);

        // get the command to check the category
        Command command = registry.getOrDefault(name.toLowerCase(), aliasRegistry.get(name.toLowerCase()));
        // you can only run not public commands in #staff-bot and #admin-bot
        if (!Objects.equals(command.category, "public")) {
            if (event.getChannel().getId() != Long.parseLong(staff_bot_channel_id)
                    && event.getChannel().getId() != Long.parseLong(admin_bot_channel_id)) {
                if (event.getChannel().getId() == Long.parseLong(apprentice_bot_channel_id) && !command.apprenticeCommand) {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Wrong Channel!")
                            .setDescription("Please use <#" + staff_bot_channel.getIdAsString() + "> or <#" + admin_bot_channel.getIdAsString() + ">! ")
                            .setColor(Utils.Pals.error);
                    event.getChannel().sendMessage(eb);
                    return;
                } else if (event.getChannel().getId() != Long.parseLong(apprentice_bot_channel_id)) {
                    assert staff_bot_channel != null;
                    assert admin_bot_channel != null;
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Wrong Channel!")
                            .setDescription("Please use <#" + staff_bot_channel.getIdAsString() + "> or <#" + admin_bot_channel.getIdAsString() + ">! ")
                            .setColor(Utils.Pals.error);
                    event.getChannel().sendMessage(eb);
                    return;
                }
            }
        }
        // check if the command gets executed in the #bot channel
        if (event.getChannel().getId() != Long.parseLong(bot_channel_id)
                && event.getChannel().getId() != Long.parseLong(staff_bot_channel_id)
                && event.getChannel().getId() != Long.parseLong(admin_bot_channel_id)
                && event.getChannel().getId() != Long.parseLong(apprentice_bot_channel_id)) {
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("Wrong Channel!")
                    .setDescription("Please use <#" + bot_channel.getIdAsString() + ">! ")
                    .setColor(Utils.Pals.error);
            event.getChannel().sendMessage(eb);
            return;
        }


        // run the command
        runCommand(name, new Context(event, args, newMessage));
    }

    /**
     * Run a command
     *
     * @param name the name of the command
     * @param ctx  the context of the command
     */
    public void runCommand(String name, Context ctx) {
        Command command = registry.getOrDefault(name.toLowerCase(), aliasRegistry.get(name.toLowerCase()));
        if (command == null) {
            return;
        }
        if (!command.hasPermission(ctx)) {
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("No permissions!")
                    .setDescription("You need higher permissions to execute this command.");
            ctx.sendMessage(eb);
            return;
        }
        if (command.minArguments > ctx.args.length - 1) {
            tooFewArguments(ctx, command);
            return;
        }
        try {
            command.run(ctx);
        } catch (Exception error) {
            error.printStackTrace();
            System.out.println(error.getMessage());
            try {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("There was an error executing this command: " + name + "!")
                        .setDescription(error.getStackTrace()[0].toString())
                        .setColor(Color.decode("#ff0000"));
                assert error_log_channel != null;
                error_log_channel.sendMessage(eb);
            } catch (Exception error2) {
                System.out.println("There was an error at outputting the error!!!");
                error2.printStackTrace();
            }
        }
    }

    /**
     * Get a command by name
     *
     * @param name the requested command name
     * @return the command
     */
    public Command getCommand(String name) {
        return registry.get(name.toLowerCase());
    }

    /**
     * Get all commands in the registry
     *
     * @return all commands
     */
    public Collection<Command> getAllCommands() {
        return registry.values();
    }

    /**
     * Check if a command exists in the registry
     *
     * @param name command name
     * @return return true if there is a command, else return false
     */
    public boolean isCommand(String name) {
        return registry.containsKey(name.toLowerCase()) || aliasRegistry.containsKey(name.toLowerCase());
    }
}