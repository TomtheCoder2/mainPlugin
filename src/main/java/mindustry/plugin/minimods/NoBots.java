package mindustry.plugin.minimods;

import arc.Events;
import arc.files.Fi;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.EventType.ConnectPacketEvent;
import mindustry.gen.Call;
import mindustry.net.NetConnection;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import org.javacord.api.entity.message.MessageAttachment;

import java.util.List;

public class NoBots implements MiniMod {
	Fi filePath = Vars.dataDirectory.child("mods").child("ipv4.txt");
	static int[] ips;
	static short[] masks;
	public NoBots() {
		parseBlacklist(filePath);
	}

	public static int parseBlacklist(Fi file) {
		if (!file.exists()) {
			Log.err("No ip blacklist file specified");
			return -1;
		}
		Log.info("Parsing blacklist file...");
		String[] lines = file.readString().split("\n");
		ips = new int[lines.length];
		masks = new short[lines.length];
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (line.startsWith("#")) continue;
			int slash = line.indexOf('/');
			if (slash == -1) {
				Log.warn("Line at @ has no mask: @\n Consider adding a /0 if you wish to block that specific ip", i, line);
				continue;
			}
			String subnet = line.substring(0, slash);
			int maskBits = 32-Strings.parseInt(line.substring(slash+1));
			ips[i] = convertIp(subnet) >>> maskBits;
			masks[i] = (short) maskBits;
		}

		Log.info("Loaded a total of @ ips", ips.length);
		return ips.length;
	}


	@Override
	public void registerEvents() {
		Events.on(ConnectPacketEvent.class, connectionEvent -> {
			// The code is probably blocking and a long db query could tank tps
			NetConnection con = connectionEvent.connection;
			Database.Player pd = Database.getPlayerData(con.uuid);
			if (pd != null && pd.verified) return;
			int ip = convertIp(con.address);
			for (int i = 0; i < ips.length; i++) {
				if (ips[i] == (ip >>> masks[i])) {
//					 REMOVEME
//					Log.err("Block @", con.address);
					Call.kick(con, "This ip is blocked, try disabling any vpn\ndiscord.phoenix-network.dev");
					con.kick();
					con.kicked = true;
					con.close();
					return;
				}
			}
			Log.info("Ip not blocked: @",con.address);
		});
	}

	@Override
	public void registerDiscordCommands(DiscordRegistrar handler) {
		handler.register("update-ip-blacklist", "",
				data -> {
					data.usage = ".txt file";
					data.roles =  new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
				},
				ctx -> {
					List<MessageAttachment> attachments = ctx.event.getMessageAttachments();
					if (attachments.size() != 1 || (!attachments.get(0).getFileName().endsWith("txt"))) {
						ctx.error("Provide a txt file", "");
					}
					attachments.get(0).downloadAsByteArray().thenAccept(b -> {
						filePath.writeBytes(b);
						int size = parseBlacklist(filePath);
						ctx.success("Updated ip blacklist", "Size: " + size);
					});
				});
	}

	static int convertIp(String ip) {
		int d1 = ip.indexOf('.'), d2 = ip.indexOf('.', d1+1), d3 = ip.indexOf('.', d2+1);
		return Strings.parseInt(ip, 10, 0, d3+1, ip.length()) |
				Strings.parseInt(ip, 10, 0, d2+1, d3) << 8 |
				Strings.parseInt(ip, 10, 0, d1+1, d2) << 16 |
				Strings.parseInt(ip, 10, 0, 0, d1) << 24;
	}
}
