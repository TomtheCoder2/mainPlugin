package mindustry.plugin.minimods;

import arc.Events;
import arc.files.Fi;
import arc.struct.IntSet;
import arc.util.Http;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.game.EventType.ConnectionEvent;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.net.NetConnection;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import org.javacord.api.entity.message.MessageAttachment;

import java.util.List;

public class NoBots implements MiniMod {
	Fi filePath = Vars.dataDirectory.child("mods").child("asn-ban.csv");
	public NoBots() {
		// TODO: File path config
		parseBlacklist(filePath);
	}

	public static IntSet asnBlacklist = new IntSet();
	static IpChecker[] checkers = {
			new IpChecker() {
				long cooldownTime = 0;
				@Override
				public void check(String ip) {
					if (Time.millis() < cooldownTime) {
						checkers[1].check(ip);
						return;
					}
					Log.info("Sending http://ip-api.com/json/@?fields=18432", ip);
					Http.get("http://ip-api.com/json/"+ ip + "?fields=18432").submit(res -> {
						if (Strings.parseInt(res.getHeader("X-Rl")) == 0) {
							// Untested code above
							cooldownTime = Time.millis() + Strings.parseInt(res.getHeader("X-Ttl")) * 1000L;
						}
						String asnString = Jval.read(res.getResultAsString()).getString("as");
						Log.info("Asn string: @", asnString);
						int asn = Strings.parseInt(asnString.substring(2, asnString.indexOf(" ")));
						Log.info("@, @", asn, asnBlacklist.contains(asn));
						if (asnBlacklist.contains(asn)) kickAll(ip);
					});
				}
			},
			ip -> {
				Http.get("https://ipapi.co/" + ip + "/json").submit(res -> {
					String asnString = Jval.read(res.getResultAsString()).getString("asn");
					int asn = Strings.parseInt(asnString.substring(2));
					if (asnBlacklist.contains(asn)) kickAll(ip);
				});
			}
	};

	public static void kickAll(String ip) {
		// TODO: Could be possible that player isn't created yet?
		Vars.netServer.admins.blacklistDos(ip);
		Groups.player.each(p -> {
			if (p.con.address.equals(ip)) {
				Call.kick(p.con, "This ip is blocked, try disabling any vpn\ndiscord.phoenix-network.dev");
				p.con.close();
			}
		});
	}

	public static int parseBlacklist(Fi file) {
		if (!file.exists()) {
			Log.err("No asn blacklist file specified");
			return -1;
		}
		String[] asns = file.readString().split("\n");
		for (String line : asns) {
			int end = line.indexOf(",");
			if (end == -1) end = line.length();
			asnBlacklist.add(Strings.parseInt(line.substring(0, end)));
		}
		Log.info("Loaded a total of @ asns", asnBlacklist.size);
		return asnBlacklist.size;
	}


	@Override
	public void registerEvents() {
		Events.on(ConnectionEvent.class, connectionEvent -> {
			NetConnection con = connectionEvent.connection;
			Database.Player pd = Database.getPlayerData(con.uuid);
			if (pd != null && pd.verified) return;
			checkers[0].check(con.address);
		});
	}

	@Override
	public void registerDiscordCommands(DiscordRegistrar handler) {
		handler.register("update-asn", "",
				data -> {
					data.usage = ".csv file";
					data.roles =  new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
				},
				ctx -> {
					List<MessageAttachment> attachments = ctx.event.getMessageAttachments();
					if (attachments.size() != 1 || (!attachments.get(0).getFileName().endsWith("csv"))) {
						ctx.error("Provide a csv file", "");
					}
					attachments.get(0).downloadAsByteArray().thenAccept(b -> {
						filePath.writeBytes(b);
						int size = parseBlacklist(filePath);
						ctx.success("Updated asn blacklist", "Size: " + size);
					});
				});
	}
}

@FunctionalInterface
interface IpChecker {
	void check(String ip);
}

