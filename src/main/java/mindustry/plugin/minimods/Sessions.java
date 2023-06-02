package mindustry.plugin.minimods;

import arc.Events;
import arc.func.Prov;
import arc.struct.ObjectMap;
import arc.util.*;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Rank;

public class Sessions implements MiniMod {
	public static ObjectMap<String, Session> sessions = new ObjectMap<>();

	public static void newSession(Player player, Prov<Session> sessionProv) {
		Session session = sessionProv.get();
		Session found = sessions.getNull(session.sessionName);
		if (found != null) {
			player.sendMessage(GameMsg.error("Sessions", "Session \"@\" already exists", found.desc()));
			return;
		}
		sessions.put(session.sessionName, session);
		String text = session.desc() + " started by" + player.name + "[white]\n" +
				"Vote with /y " + session.sessionName + " or /n " + session.sessionName +
				"\n vote ends in " + Strings.formatMillis(-Time.timeSinceMillis(session.endTime));
		Call.sendMessage(GameMsg.info("Sessions", text));
		player.sendMessage(GameMsg.info("Sessions", "Cancel this session with /c " + session.sessionName));

		session.addVote(player, true);
	}

	@Override
	public void registerCommands(CommandHandler handler) {
		final String[] votes = {"y", "n", "c"};
		for (String voteType : votes) {
			handler.<Player>register(voteType, "[session]", "Vote for a session", (args, player) -> {
				if (sessions.size == 0) {
					Call.sendMessage(GameMsg.error("Sessions", "No vote sessions currently active"));
					return;
				}
				Session found;
				if (args.length == 0 && sessions.size == 1) {
					found = sessions.values().next();
				} else {
					found = sessions.get(args[0]);
				}
				if (found == null) {
					String allSessions = String.join("\n", sessions.keys().toSeq());
					player.sendMessage("Current sessions:\n" + allSessions);
					return;
				}
				String uuid = player.uuid();
				if (voteType.equals("c")) {
					Database.Player data = Database.getPlayerData(uuid);
					if (uuid.equals(found.plaintiff) || (data != null && data.rank > Rank.APPRENTICE)) {
						Call.sendMessage(GameMsg.info("Sessions", "Session @ canceled by @",
								found.sessionName, player.name));
						found.end();
					}
				} else {
					found.addVote(player, voteType.equals("y"));
					player.sendMessage(GameMsg.info("Sessions", "Voted!"));
				}
			});
		}
	}

	@Override
	public void registerServerCommands(CommandHandler handler) {
		handler.register("cancel", "<session>", "cancel a session", args -> {
			Session found = args.length == 0 ? null : sessions.get(args[0]);
			if (found == null) {
				String allSessions = String.join("\n", sessions.keys().toSeq());
				Log.info("Current sessions:\n" + allSessions);
				return;
			}
			Log.info("Session \"@\" canceled", found.desc());
			found.end();
		});
	}

	@Override
	public void registerEvents() {
		Events.on(EventType.PlayerLeave.class, playerLeave -> {
			for (Session session : sessions.values()) {
				session.playerLeave(playerLeave.player.uuid());
			}
		});
	}

	public static abstract class Session {
		public String sessionName;
		public boolean ended = false;
		public String plaintiff;
		public long startTime = Time.millis();
		public long endTime;
		public ObjectMap<String, Boolean> votes = new ObjectMap<>();



		public Session(String name, Player plaintiff, int voteTime) {
			this.sessionName = name;
			this.plaintiff = plaintiff.uuid();
			this.endTime = Time.millis() + voteTime * 1000L;
			Timer.schedule(this::timerFinished, voteTime);
		}

		public void addVote(Player player, boolean vote) {
			if (this.ended) return;
			this.votes.put(player.uuid(), vote);
			// Counting votes
			int yes = 0, no = 0;
			for (var entry : this.votes) {
				String uuid_ = entry.key;
				if (!Groups.player.contains(p -> p.uuid().equals(uuid_))) continue;
				if (entry.value) {
					yes++;
				} else {
					no++;
				}
			}
			if (onVote(yes, no, Groups.player.size(), player)) {
				this.end();
			}
		}

		public void playerLeave(String uuid) {
			this.votes.remove(uuid);
		}

		/**
		 *
		 * @param yes Number of people voting for it
		 * @param no Number of people voting against it
		 * @param players Total number of players
		 * @return If true, the Session is marked complete
		 */
		public abstract boolean onVote(int yes, int no, int players, Player player);

		public void end() {
			this.ended = true;
			sessions.remove(this.sessionName);
		}

		protected void timerFinished() {
			if (this.endTime < Time.millis()) {
				Timer.schedule(this::timerFinished, (this.endTime - Time.millis())/1000f);
				Call.sendMessage(GameMsg.info("Sessions", "Session @ has failed", this.sessionName));
			} else {
				this.end();
			}
		}

		public abstract String desc();
	}
}
