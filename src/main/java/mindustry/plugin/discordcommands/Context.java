package mindustry.plugin.discordcommands;

import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

import java.awt.*;
import java.util.HashMap;

import static mindustry.plugin.discordcommands.DiscordCommands.error_log_channel;
import static mindustry.plugin.utils.Utils.Pals;

/**
 * Represents the context in which a command was called
 */
public class Context {
    /**
     * Source event
     */
    public MessageCreateEvent event;
    /**
     * Command arguments
     */
    public String[] args;
    /**
     * Full message excluding command
     */
    public String message;
    public TextChannel channel;
    public MessageAuthor author;

    /**
     * set the context of the message to execute the command
     */
    public Context(MessageCreateEvent event, String[] args, String message) {
        this.event = event;
        this.args = args;
        this.message = message;
        this.channel = event.getChannel();
        this.author = event.getMessageAuthor();

    }

    /**
     * Send a message back to the user who invoked the command
     *
     * @param message reply with this message
     */
    public void reply(MessageBuilder message) {
        message.send(channel);
    }

    /**
     * Send a plaintext message back
     *
     * @param message the message to send
     */
    public void reply(String message) {
        MessageBuilder mb = new MessageBuilder();
        mb.append(message);
        mb.send(channel);
    }


    public void sendEmbed(EmbedBuilder eb) {
        channel.sendMessage(eb);
    }

    public void sendEmbed(String title) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        channel.sendMessage(eb);
    }

    public void sendEmbed(boolean success, String title) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        if (success) {
            eb.setColor(Pals.success);
        } else {
            eb.setColor(Pals.error);
        }
        channel.sendMessage(eb);
    }

    public void sendEmbed(String title, String description) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(description);
        channel.sendMessage(eb);
    }

    public void sendEmbed(boolean success, String title, String description) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(description);
        if (success) {
            eb.setColor(Utils.Pals.success);
        } else {
            eb.setColor(Pals.error);
        }
        channel.sendMessage(eb);
        ;
    }

    public void sendEmbed(boolean success, String title, String description, HashMap<String, String> fields, boolean inline) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(description);
        if (success) {
            eb.setColor(Pals.success);
        } else {
            eb.setColor(Pals.error);
        }
        for (String name : fields.keySet()) {
            String desc = fields.get(name);
            eb.addField(name, desc, inline);
        }
        channel.sendMessage(eb);
    }

    public void sendEmbed(boolean success, String title, HashMap<String, String> fields, boolean inline) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        if (success) {
            eb.setColor(Pals.success);
        } else {
            eb.setColor(Pals.error);
        }
        for (String name : fields.keySet()) {
            String desc = fields.get(name);
            eb.addField(name, desc, inline);
        }
        channel.sendMessage(eb);
    }

    public void sendEmbed(String title, String description, HashMap<String, String> fields) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setDescription(description);
        for (String name : fields.keySet()) {
            String desc = fields.get(name);
            eb.addField(name, desc, false);
        }
        channel.sendMessage(eb);
    }

    public void sendMessage(EmbedBuilder eb) {
        try {
            channel.sendMessage(eb).get();
        } catch (Exception e) {
            e.printStackTrace();
            EmbedBuilder errorEmbed = new EmbedBuilder()
                    .setTitle("Error")
                    .setColor(new Color(0xff0000))
                    .setDescription("There was an error while sending the message: \n" + e.getMessage());
            sendEmbed(errorEmbed);
            if (error_log_channel != null)
                error_log_channel.sendMessage(
                        errorEmbed
                                .setTimestampToNow()
                                .addField("Link to command Message", String.valueOf(event.getMessageLink()))
                );
        }
    }

}