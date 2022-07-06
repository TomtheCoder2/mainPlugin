package mindustry.plugin.discord.discordcommands;

import mindustry.gen.Call;
import mindustry.plugin.ioMain;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.DiscordVars;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.core.util.handler.message.MessageCreateHandler;
import org.json.JSONObject;

import java.awt.Color;
import java.util.*;
import java.util.Arrays;
import java.util.stream.Collectors;

import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.*;

import arc.struct.LongSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.struct.StringMap;

/**
 * Represents a registry of commands
 */
public class DiscordRegistrar {
    private String prefix = null;

    /** Includes aliases */
    private ObjectMap<String, CommandEntry> commands = new ObjectMap<>();
    /** Command + CommandHandler */
    private static class CommandEntry {
        Command data;
        CommandHandler handler;

        public CommandEntry(Command data, CommandHandler handler) {
            this.data = data;
            this.handler = handler;
        }
    }

    /** Represents information about a command's data */
    public class Command {
        public static class Arg {
            public String name;
            public boolean optional;
            public boolean ellipses;
        }
    
        /** Name of command */
        public String name;
        /** Alises */
        public String[] aliases;
        /** Description of the command */
        public String help = ":v no information was provided for this command";
        /** Usage string, to be shown in help commands */
        public String usage;
        /** aka Parameters */
        public Arg[] args;
        /** Roles that are allowed to use/view the command */
        public long[] roles;
        /** Category */
        public String category = "public";
        public boolean hidden = false;   

        public Command(String name) {
            this.name=  name;
        }
    }

    public DiscordRegistrar(String prefix) {
        this.prefix = prefix;
    }

    @FunctionalInterface
    public interface DataInitializer {
        void init(Command data);
    }

    @FunctionalInterface
    public interface CommandHandler {
        void run(Context ctx);
    }

    /** Registers a commmand. The format of the args string is the same as for the arc CommandHandler. Use data initializer to configure additional information about
     * the command, or pass null.
     */
    public void register(String name, String args, DataInitializer data, CommandHandler handler) {
        Command cmdData = new Command(name);
        cmdData.usage = args;
        
        // Parse arguments
        String[] argList = args.split("\\s+");
        Command.Arg[] argObjs = new Command.Arg[argList.length];
        boolean hasOptional = false;
        for (int i =0 ; i <argList.length; i++) {
            String argStr = argList[i];
            boolean optional;
            if (argStr.startsWith("[")) {
                if (!argStr.endsWith("]")) {
                    throw new IllegalArgumentException("Argument '" + argStr + "' must end with ]");
                }
                optional = true;
                hasOptional = true;
            } else if (argStr.startsWith("<")) {
                if (hasOptional) {
                    throw new IllegalArgumentException("Argument '" + argStr + "' is required, but comes after optional argument");
                }

                if (!argStr.endsWith(">")) {
                    throw new IllegalArgumentException("Argument '" + argStr + "' must end with >");
                }
                optional = false;
            } else {
                throw new IllegalArgumentException("Argument '" + argStr + "' must start with either < or [");
            }

            String argName = argStr.substring(1, argStr.length() - 1);

            boolean ellipses = false;
            if (argStr.substring(argStr.length()-4, argStr.length()-1).equals("...")) {
                if (i != argList.length - 1) {
                    throw new IllegalArgumentException("Ellipses argument " + argStr + " must be final argument");
                }
                ellipses = true;
                argName = argStr.substring(1, argStr.length() - 4);
            }

            Command.Arg argObj = new Command.Arg();
            argObj.name = argName;
            argObj.optional = optional;
            argObj.ellipses = ellipses;
            argObjs[i] = argObj;
        }
        cmdData.args = argObjs;

        // Run data initializer
        data.init(cmdData);

        CommandEntry cmdEntry = new CommandEntry(cmdData, handler);
        commands.put(cmdData.name, cmdEntry);
        if (cmdData.aliases != null) {
            for (String alias : cmdData.aliases) {
                    commands.put(alias, cmdEntry);
            }
        }
    }

    public EmbedBuilder helpEmbed() {
        ObjectMap<String, Seq<String>> commandsByCategory = new ObjectMap<>();

        for (var entry : commands) {
            if (entry.key.equals(entry.value.data.name)) {
                if (entry.value.data.hidden) continue;
                String category = entry.value.data.category;
                if (category == null) category = "General";
                Seq<String> cmds = commandsByCategory.get(category, new Seq<>());
                if (!cmds.contains(entry.key)) {
                    cmds.add(entry.key);
                }
                commandsByCategory.put(category, cmds);
            }
        }
        
        for (var list : commandsByCategory.values()) {
            list.sort();
        }

        EmbedBuilder eb = new EmbedBuilder()
            .setTitle("Commands")
            .setColor(DiscordPalette.INFO);
        for (var entry : commandsByCategory) {
            eb.addInlineField(entry.key, entry.value.toString("\n"));
        }

        return eb;
    }

    public EmbedBuilder helpEmbed(String command) {
        CommandEntry entry = commands.get(command);
        if (entry == null) return null;
        Command data = entry.data;

        EmbedBuilder eb = new EmbedBuilder()
            .setTitle("Command: " + data.name)
            .setDescription(data.help)
            .addField("Usage", DiscordVars.prefix + data.name + " " + data.usage);
        
        if (data.category != null)
            eb.addInlineField("Category", data.category);
        
        if (data.aliases != null && data.aliases.length != 0)
            eb.addInlineField("Aliases", Seq.with(data.aliases).toString(", "));
        
        if (data.roles != null && data.roles.length != 0) 
            eb.addField("Roles", "<@&" + LongSeq.with(data.roles).toString("> <@&") + ">");
        return eb;
    }

    /** Parses command arguments */
    public void dispatchEvent(MessageCreateEvent event) {
        if (!event.getMessage().getContent().startsWith(prefix)) {
            return;
        }
        String[] args = event.getMessage().getContent().split("\\s+");
        String command = args[0].substring(prefix.length());
        CommandEntry entry = commands.get(command);
        if (entry == null) {
            return;
        }

        List<Role> userRoles = event.getMessageAuthor().asUser().get().getRoles(event.getServer().get());
        LongSeq cmdRoles = LongSeq.with(entry.data.roles);
        if (entry.data.roles != null && !userRoles.stream().anyMatch(x -> cmdRoles.contains(x.getId()))) {
            Context ctx = new Context(event, null);
            ctx.error("Lack of permission", "Required to have one of the following roles: <@&" + cmdRoles.toString("><@&") + ">");
            return;
        }

        int i = 1;
        String error = null;
        StringMap argValues = new StringMap();
        for (Command.Arg arg : entry.data.args) {
            if (args.length >= i) {
                if (!arg.optional) {
                    error = "Missing required argument " + arg.name;   
                }
                break;
            }

            String argStr = args[i];
            if (arg.ellipses) {
                argStr = Arrays.stream(args).skip(i).collect(Collectors.joining(" "));
            }

            argValues.put(arg.name, argStr);

            i++;
        }

        Context ctx = new Context(event, argValues);
        if (error != null) {
            ctx.error(error, "Usage: " + entry.data.usage);
            return;
        }
        entry.handler.run(ctx);
    }
}