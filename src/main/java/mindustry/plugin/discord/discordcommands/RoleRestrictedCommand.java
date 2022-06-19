package mindustry.plugin.discord.discordcommands;

import arc.struct.LongSeq;
import arc.util.Log;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.permission.Role;

import java.util.Arrays;

public abstract class RoleRestrictedCommand extends Command {
    public long[] roles = new long[] {};

    public RoleRestrictedCommand(String name) {
        super(name);
    }

    /**
     * check if a user has permissions to execute this command
     */
    @Override
    public boolean hasPermission(Context ctx) {
        for (Role role: ctx.author().getRoles(ctx.server())) {
            if (LongSeq.with(this.roles).contains(role.getId())) {
                return true;
            }
        }
        return false;
    }
}