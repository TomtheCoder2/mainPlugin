package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectSet;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Strings;
import me.mars.rollback.RollbackPlugin;
import me.mars.rollback.TileStore;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.plugin.MiniMod;
import mindustry.plugin.utils.Cooldowns;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Utils;

import static mindustry.plugin.minimods.Ranks.warned;

public class Undo implements MiniMod {
	/**
	 * Default undo duration in minutes
	 */
	private static final int UNDO_DURATION = 3;
	public static TileStore instance;
	private static ObjectSet<String> players = new ObjectSet<>();

	public Undo() {
		instance = RollbackPlugin.getTileStore();
	}

	@Override
	public void registerEvents() {
		Events.on(WorldLoadEvent.class, worldLoadEvent -> {
			players.clear();
			Groups.player.each(p -> players.add(p.uuid()));
		});

		Events.on(PlayerJoin.class, playerJoin -> {
			players.add(playerJoin.player.uuid());
		});
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
			String target = args.length >= 1 ? args[0] : "";
			Player found = Query.findPlayerEntity(target);
			Administration.PlayerInfo info = Query.findPlayerInfo(target);
			if (found != null) {
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
				target = found.uuid();
			} else if (info != null){
				if (!players.contains(info.id)) {
					Log.info("Id is @", info.id);
					player.sendMessage(GameMsg.error("Undo", "That player has not joined this round."));
					return;
				}
				if (info.admin) {
					player.sendMessage(GameMsg.error("Undo", "Can't undo an admin."));
					return;
				}
				target = info.id;
			} else {
				player.sendMessage(Utils.playerList((p) -> !p.admin && p.con != null && p != player));
				return;
			}
			int duration = args.length == 2 ? Strings.parseInt(args[1], UNDO_DURATION) : UNDO_DURATION;
			String finalTarget = target;
			Sessions.newSession(player, () -> new UndoSession(player, finalTarget, duration));
		});
	}
}

class UndoSession extends Sessions.Session {
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
			player.sendMessage(GameMsg.error("Undo", "You can't vote for your own trial"));
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
	public boolean onVote(int yes, int no, int players,@Nullable Player player) {
		int score = yes-no;
		int required = (players/2) + 1;
		if (score >= required) {
			Undo.instance.rollback(this.target, this.startTime - this.duration * (60 * 1000L));
			Call.sendMessage(GameMsg.info("Undo", "Vote passed, undoing(@ min)", this.duration));
			return true;
		}
		Call.sendMessage(GameMsg.info("Undo", "@ has voted, (@/@)", player.name, score, required));
		return false;
	}

	@Override
	public String desc() {
		return Strings.format("Undo player \"@\"(@ min)", Query.findPlayerInfo(this.target).lastName, this.duration);
	}
}
