package mindustry.plugin.discord.discordcommands;

import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.utils.Utils;

import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;

import arc.struct.StringMap;

import java.awt.Color;

/**
 * Represents a specific invocation of a command
 * Generalizes between slash commands and text commands [TODO]
 */
public class Context {
    /**
     * Source event
     */
    public MessageCreateEvent event;
    /**
     * Command arguments
     */
    public StringMap args;


    /**
     * Parse a message object
     */
    public Context(MessageCreateEvent event, StringMap args) {
        this.event = event;
        this.args = args;
    }

    /**
     * Send a message back to the user who invoked the command
     *
     * @param message reply with this message
     */
    public void reply(MessageBuilder message) {
        message.send(channel());
    }

    /**
     * Send a plaintext message back
     *
     * @param message the message to send
     */
    public void reply(String message) {
        MessageBuilder mb = new MessageBuilder();
        mb.append(message);
        mb.replyTo(this.event.getMessage());
        mb.send(channel());
    }

    public User author() {
        return this.event.getMessageAuthor().asUser().get();
    }

    public Server server() {
        return this.event.getServer().get();
    }

    public TextChannel channel() {
        return event.getChannel();
    }

    public void sendEmbed(EmbedBuilder eb) {
        channel().sendMessage(eb);
    }

    public void sendMessage(MessageBuilder mb) {
        mb.send(channel());
    }

    public void sendEmbed(Color color, String title, String description) {
        sendEmbed(
            new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
        );
    }

    public void success(String title, String description) {
        sendEmbed(DiscordPalette.SUCCESS, title, description);
    }

    public void error(String title, String description) {
        sendEmbed(DiscordPalette.ERROR, title, description);
    }
}