package mindustry.plugin.discordcommands;

import java.util.Optional;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.permission.Role;

import arc.util.Log;

public abstract class RoleRestrictedCommand extends Command {
    public String role = null;
    public Role resolvedRole = null;

    public RoleRestrictedCommand(String name) {
        super(name);
    }

    /**
     * check if a user has permissions to execute this command
     */
    @Override
    public boolean hasPermission(Context ctx) {
        if (role == null) return false;
        if (ctx.event.isPrivateMessage()) return false;
        // memoize role resolution
        if (resolvedRole == null) {
            resolvedRole = getRole(ctx.event.getApi(), role);
            if (resolvedRole == null) return false;
        }
        // I am simply not going to touch this
        return ctx.event.getMessageAuthor().asUser().get().getRoles(ctx.event.getServer().get()).contains(resolvedRole);
    }

    public Role getRole(DiscordApi api, String id) {
        Optional<Role> r1 = api.getRoleById(id);
        if (!r1.isPresent()) {
            Log.err("Error: discord role " + id + " not found");
            return null;
        }
        return r1.get();
    }
}