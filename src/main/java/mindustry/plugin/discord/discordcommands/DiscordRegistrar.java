package mindustry.plugin.discord.discordcommands;

import arc.struct.LongSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.DiscordVars;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.utils.PluginConfig;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static mindustry.plugin.discord.DiscordVars.api;

/**
 * Represents a registry of commands
 */
public class DiscordRegistrar {
    /**
     * Includes aliases
     */
    private final ObjectMap<String, CommandEntry> commands = new ObjectMap<>();
    private String prefix = null;

    public DiscordRegistrar(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Registers a command. The format of the args string is the same as for the arc CommandHandler. Use data initializer to configure additional information about
     * the command, or pass null.
     */
    public void register(String name, String args, DataInitializer data, CommandHandler handler) {
        Command cmdData = new Command(name);
        cmdData.usage = args;

        // Parse arguments
        String[] argList = args.split("\\s+");
        Command.Arg[] argObjs = new Command.Arg[0];
        if (!Objects.equals(argList[0], "")) {
            argObjs = new Command.Arg[argList.length];
            boolean hasOptional = false;
            for (int i = 0; i < argList.length; i++) {
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
                if (argStr.startsWith("...", argStr.length() - 4)) {
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
        }
        cmdData.args = argObjs;

        // Run data initializer
        data.init(cmdData);

        CommandEntry cmdEntry = new CommandEntry(cmdData, handler);
        commands.put(cmdData.name, cmdEntry);
        if (cmdData.aliases != null) {
            for (String alias : cmdData.aliases) {
                // warn if alias is already used
                if (commands.containsKey(alias)) {
                    System.out.println("WARNING: alias '" + alias + "' is already used by command '" + commands.get(alias).data.name + "'");
                }
                commands.put(alias, cmdEntry);
            }
        }
    }

    /**
     * Returns a help embed listing all commands
     *
     * @param user the user, by which commands will be filtered (only commands they can use), or null for all commands
     */
    public EmbedBuilder helpEmbed(User user) {
        ObjectMap<String, Seq<String>> commandStringsByCategory = new ObjectMap<>();

        for (var entry : commands) {
            if (entry.key.equals(entry.value.data.name)) {
                if (entry.value.data.hidden) continue;
                if (entry.value.data.roles != null && !Arrays.stream(entry.value.data.roles).anyMatch(roleID ->
                        // user has that role
                        user.getRoles(DiscordVars.server()).stream().anyMatch(r -> r.getId() == roleID)
                )) continue;

                String category = entry.value.data.category;
                if (category == null) category = "General";

                Seq<String> cmds = commandStringsByCategory.get(category, new Seq<>());
                if (!cmds.contains(entry.key)) {
                    cmds.add("**" + DiscordVars.prefix + entry.key + "** " + entry.value.data.usage);
                }
                commandStringsByCategory.put(category, cmds);
            }
        }

        for (var list : commandStringsByCategory.values()) {
            list.sort();
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Commands")
                .setColor(DiscordPalette.INFO);
        for (var entry : commandStringsByCategory) {
            eb.addField(entry.key, entry.value.toString("\n"));
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
                .setColor(DiscordPalette.INFO)
                .addField("Usage", DiscordVars.prefix + data.name + " " + data.usage);

        if (data.category != null)
            eb.addInlineField("Category", data.category);

        if (data.aliases != null && data.aliases.length != 0)
            eb.addInlineField("Aliases", Seq.with(data.aliases).toString(", "));

        if (data.roles != null && data.roles.length != 0)
            eb.addField("Roles", "<@&" + LongSeq.with(data.roles).toString("> <@&") + ">");
        return eb;
    }

    /**
     * Parses command arguments
     */
    public void dispatchEvent(MessageCreateEvent event) {
        if (!event.getMessage().getContent().startsWith(prefix)) {
            return;
        }
        String[] args = event.getMessage().getContent().split("\\ +");
        String command = args[0].substring(prefix.length());
        CommandEntry entry = commands.get(command);
        if (entry == null) {
            return;
        }

        if (entry.data.roles != null) { // only check if the command has a restriction
            List<Role> userRoles = event.getMessageAuthor().asUser().get().getRoles(event.getServer().get());
            LongSeq cmdRoles = LongSeq.with(entry.data.roles);
            if (
                    (entry.data.roles != null && userRoles.stream().noneMatch(x -> cmdRoles.contains(x.getId()))) &&
                            !(userRoles.stream().anyMatch(x -> x.getId() == Roles.DEV) && PluginConfig.serverName.contains("Beta"))
            ) {
                Context ctx = new Context(event, null);
                ctx.error("Lack of permission", "Required to have one of the following roles: <@&" + cmdRoles.toString("><@&") + ">");
                return;
            }
            // TODO fix this, its really ugly
            TextChannel channel = event.getChannel();
            if ((cmdRoles.contains(Roles.APPRENTICE) &&
                    cmdRoles.contains(Roles.MOD) &&
                    cmdRoles.contains(Roles.ADMIN)) && channel == Channels.BOT) {
                Context ctx = new Context(event, null);
                ctx.error("Lack of permission", "Please use a another channel: <#" + Channels.APPRENTICE_BOT.getIdAsString() + ">, <#" + Channels.MOD_BOT.getIdAsString() + "> or <#" + Channels.ADMIN_BOT.getIdAsString() + ">");
                return;
            }
            if ((cmdRoles.contains(Roles.MOD) &&
                    cmdRoles.contains(Roles.ADMIN) &&
                    cmdRoles.contains(Roles.DEV) &&
                    !cmdRoles.contains(Roles.APPRENTICE)) && channel == Channels.APPRENTICE_BOT) {
                Context ctx = new Context(event, null);
                ctx.error("Lack of permission", "Please use a another channel: <#" + Channels.MOD_BOT.getIdAsString() + "> or <#" + Channels.ADMIN_BOT.getIdAsString() + ">");
                return;
            }
            if ((!cmdRoles.contains(Roles.MOD) &&
                    cmdRoles.contains(Roles.ADMIN) &&
                    cmdRoles.contains(Roles.DEV) &&
                    !cmdRoles.contains(Roles.APPRENTICE)) && channel == Channels.MOD_BOT) {
                Context ctx = new Context(event, null);
                ctx.error("Lack of permission", "Please use a another channel: <#" + Channels.ADMIN_BOT.getIdAsString() + ">");
                return;
            }
        }

        int i = 1;
        String error = null;
        StringMap argValues = new StringMap();
        for (Command.Arg arg : entry.data.args) {
            if (args.length <= i) {
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

//        Log.info("user args: " + Seq.with(args).toString(" | "));
//        Log.info("expected args: " + Seq.with(entry.data.args).toString(" | "));
//        Log.info("args", argValues);

        Context ctx = new Context(event, argValues);
        if (error != null) {
            ctx.error(error, "Usage: " + entry.data.usage);
            return;
        }
        entry.handler.run(ctx);
    }

    public Role getRole(String id) {
        Optional<Role> r1 = api.getRoleById(id);
        if (r1.isEmpty()) {
            System.out.println("Error: discord role " + id + " not found");
            return null;
        }
        return r1.get();
    }

    @FunctionalInterface
    public interface DataInitializer {
        void init(Command data);
    }

    @FunctionalInterface
    public interface CommandHandler {
        void run(Context ctx);
    }

    /**
     * Command + CommandHandler
     */
    private static class CommandEntry {
        Command data;
        CommandHandler handler;

        public CommandEntry(Command data, CommandHandler handler) {
            this.data = data;
            this.handler = handler;
        }
    }

    /**
     * Represents information about a command's data
     */
    public class Command {
        /**
         * Name of command
         */
        public String name;
        /**
         * Alises
         */
        public String[] aliases;
        /**
         * Description of the command
         */
        public String help = ":v no information was provided for this command";
        /**
         * Usage string, to be shown in help commands
         */
        public String usage;
        /**
         * aka Parameters
         */
        public Arg[] args;
        /**
         * Roles that are allowed to use/view the command
         */
        public long[] roles;
        /**
         * Category
         */
        public String category = null;
        public boolean hidden = false;

        public Command(String name) {
            this.name = name;
        }

        public static class Arg {
            public String name;
            public boolean optional;
            public boolean ellipses;

            public String toString() {
                return name + (optional ? "?" : "") + (ellipses ? "..." : "");
            }
        }
    }
}
