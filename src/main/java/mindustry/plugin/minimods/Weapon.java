package mindustry.plugin.minimods;

import java.lang.reflect.Field;

import org.javacord.api.entity.message.embed.EmbedBuilder;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.content.Bullets;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Weaponsc;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Utils;

public class Weapon implements MiniMod {
    private static class WeaponData {
        public float velocity;
        public float lifetime;
        public float damage;
        public BulletType type;
    }

    private ObjectMap<String, WeaponData> weaponDatas = new ObjectMap<>();

    @Override
    public void registerEvents() {
        Events.on(EventType.Trigger.class, event -> {
            for (Player p : Groups.player) {
                if (weaponDatas.containsKey(p.uuid()) && p.shooting) {
                    WeaponData data = weaponDatas.get(p.uuid());
                    Call.createBullet(data.type, p.team(), p.getX(), p.getY(), p.unit().rotation, data.damage, data.velocity, data.lifetime);
                }
            }
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {    
        handler.register("weapon",  "<player> <bullet> [damage] [lifetime] [velocity]",
            data -> {
                data.roles = new long[] { Roles.ADMIN, Roles.MOD, Roles.APPRENTICE };
                data.category = "Moderation";
                data.help = "Modify the specified player's weapon.";
            },
            ctx -> {
                float dmg = ctx.args.getFloat("damage", 1.0f);
                float life  = ctx.args.getFloat("lifetime", 1.0f);
                float vel  = ctx.args.getFloat("velocity", 1.0f);
                BulletType bulletType = null;
                try {
                    Field field = Bullets.class.getDeclaredField(ctx.args.get("bullet"));
                    bulletType = (BulletType) field.get(null);
                } catch (NoSuchFieldException | IllegalAccessException f) {
                    ctx.error("No Such Bullet", "Bullet '" + ctx.args.get("bullet") + "' does not exist. Available bullets:\n```\n" + 
                        Seq.with(Bullets.class.getDeclaredFields()).filter(x -> x.getType().equals(BulletType.class)).map(x -> x.getName()).toString("\n")
                        + "\n```"
                    );
                    return;
                }

                Player player = Utils.findPlayer(ctx.args.get("player"));
                if (player == null) {
                    ctx.error("No Such Player", ctx.args.get("player") + " does not exist");
                    return;
                }

                WeaponData data = new WeaponData();
                data.damage = dmg;
                data.lifetime = life;
                data.velocity = vel;
                data.type = bulletType;
                weaponDatas.put(player.uuid(), data);

                ctx.sendEmbed(new EmbedBuilder()
                    .setTitle("Modded " + Utils.escapeEverything(player.name()) + "'s gun")
                    .addInlineField("Damage", dmg+"")
                    .addInlineField("Lifetime", life+"")
                    .addInlineField("Velocity", vel+"")
                    .addInlineField("Type", ctx.args.get("bullet"))
                    .setColor(DiscordPalette.SUCCESS) 
                );
            }
        );
    }
}
