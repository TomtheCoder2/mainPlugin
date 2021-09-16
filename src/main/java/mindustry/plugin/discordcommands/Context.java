package mindustry.plugin.discordcommands;

import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

/** Represents the context in which a command was called */
public class Context {
    /** Source event */
    public MessageCreateEvent event;
    /** Command arguments */
    public String[] args;
    /** Full message excluding command */
    public String message;
    public TextChannel channel;
    public MessageAuthor author;

    /**
     * set the context of the message to execute the command*/
    public Context(MessageCreateEvent event, String[] args, String message) {
        this.event = event;
        this.args = args;
        this.message = message;
        this.channel = event.getChannel();
        this.author = event.getMessageAuthor();

    }

    /**
     * Send a message back to the user who invoked the command
     * @param message reply with this message
     */
    public void reply(MessageBuilder message) {
        message.send(channel);
    }

    /**
     * Send a plaintext message back
     * @param message the message to send
     */
    public void reply(String message) {
        MessageBuilder mb = new MessageBuilder();
        mb.append(message);
        mb.send(channel);
    }

}