package mindustry.plugin.minimods;

import arc.util.*;
import me.mars.rollback.RollbackPlugin;
import me.mars.rollback.TileStore;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.utils.Cooldowns;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Query;

import static mindustry.plugin.minimods.Ranks.warned;

public class Undo implements MiniMod {
	public static TileStore instance;


	/**
	 * Default undo duration in minutes
	 */
	private static final int UNDO_DURATION = 3;

	public Undo() {
		instance = RollbackPlugin.getTileStore();
	}

	@Override
	public void registerCommands(CommandHandler handler) {
		Cooldowns.instance.set("undo", 3 * 60 * 1000L);
		handler.<Player>register("undo", "<name> [duration=1]", "Undo the actions of a player", (args, player)-> {
			String uuid = player.uuid();
			if (!Cooldowns.instance.canRun("undo", uuid)) {
				player.sendMessage(GameMsg.ratelimit("Undo", "undo"));
				return;
			}

			if (warned.contains(player.uuid())) {
				player.sendMessage(GameMsg.error("Undo", "You can't undo actions of other players, because you are flagged as a potential griefer!"));
				return;
			}
			if (Groups.player.size() < 3 && false) {
				player.sendMessage(GameMsg.error("Undo", "At least 3 people are required to start an undo."));
				return;
			}

			Player found = Query.findPlayerEntity(args.length >= 1 ? args[0] : "");
			if (found == null) {
				StringBuilder builder = new StringBuilder();
				builder.append("[orange]List of players: \n");

				Groups.player.each(p -> !p.admin && p.con != null && p != player, p -> {
					builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id()).append(")\n");
				});
				player.sendMessage(builder.toString());
				return;
			}
			if (found == player) {
				player.sendMessage(GameMsg.error("Undo", "Can't undo yourself."));
				return;
			} else if (found.admin) {
				player.sendMessage(GameMsg.error("Undo", "Can't undo an admin."));
				return;
			} else if (found.isLocal()) {
				player.sendMessage(GameMsg.error("Undo", "Can't undo local players."));
				return;
			} else if (found.team() != player.team()) {
				player.sendMessage(GameMsg.error("Undo", "Can't undo players on opposing teams."));
				return;
			}

			int duration = args.length == 2 ? Strings.parseInt(args[1], UNDO_DURATION) : UNDO_DURATION;
			Sessions.newSession(player, () -> new UndoSession(player, found.uuid(), duration));
		});
	}

	public static class UndoSession extends Sessions.Session {
		public final static int Vote_Time = 3 * 60;

		public String target;
		public int duration;

		public UndoSession(Player plaintiff, String target, int duration) {
			super("undo", plaintiff, Vote_Time);
			this.target = target;
			this.duration = duration;
		}

		@Override
		public void addVote(Player player, boolean vote) {
			if (player.uuid().equals(target)) {
				player.sendMessage(GameMsg.error("Undo", "You can't vote for your own trail"));
				return;
			}
			super.addVote(player, vote);
		}

		@Override
		public void playerLeave(String uuid) {
			if (uuid.equals(this.plaintiff)) {
				Call.sendMessage(GameMsg.info("Undo", "Player left, vote canceled"));
				this.end();
				return;
			}
			super.playerLeave(uuid);
		}

		@Override
		public boolean onVote(int yes, int no, int players) {
			int score = yes-no;
			if (score > (players/2) || (players <= 3 && score > 2)) {
				instance.rollback(this.target, this.startTime - this.duration * (60 * 1000L));
				Call.sendMessage(GameMsg.info("Undo", "Vote passed, undoing(@ min)", this.duration));
				return true;
			}
			return false;
		}

		@Override
		public String desc() {
			return Strings.format("Undo session(@ min)", /*Database.getPlayerData(this.target).,*/ this.duration);
		}
	}
}
