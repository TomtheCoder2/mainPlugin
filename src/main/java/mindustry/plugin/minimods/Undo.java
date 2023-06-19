package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import arc.util.*;
import me.mars.rollback.RollbackPlugin;
import me.mars.rollback.TileStore;
import me.mars.rollback.actions.Action;
import me.mars.rollback.actions.BuildAction;
import me.mars.rollback.actions.ConfigAction;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.plugin.MiniMod;
import mindustry.plugin.utils.Cooldowns;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Utils;
import mindustry.world.Tile;

import java.util.Comparator;

import static mindustry.plugin.minimods.Ranks.warned;

public class Undo implements MiniMod {
	/**
	 * Default undo duration in minutes
	 */
	private static final int UNDO_DURATION = 3;
	public static TileStore instance;
	private static ObjectMap<String, Team> players = new ObjectMap<>();
	public static OrderedMap<Player, String> inspectorCache = new OrderedMap<>();

	public Undo() {
		instance = RollbackPlugin.getTileStore();
	}

	@Override
	public void registerEvents() {
		Events.on(WorldLoadEvent.class, worldLoadEvent -> {
			players.clear();
			Groups.player.each(p -> players.put(p.uuid(), p.team()));
		});

		Events.on(PlayerJoin.class, playerJoin -> {
			players.put(playerJoin.player.uuid(), playerJoin.player.team());
		});

		Events.on(PlayerLeave.class, playerLeave -> {
			players.put(playerLeave.player.uuid(), playerLeave.player.team());
			inspectorCache.remove(playerLeave.player);
		});

		Cooldowns.instance.set("inspector-tap", 1);
		Events.on(TapEvent.class, tapEvent -> {
			// TODO: Unsure if this can be out of bounds
			Player player = tapEvent.player;
			if (!inspectorCache.containsKey(player)) return;
			if (!Cooldowns.instance.canRun("inspector-tap", player.uuid())) return;
			Cooldowns.instance.run("inspector-tap", player.uuid());
			Tile tile = tapEvent.tile;
			Seq<Action> actions = instance.get(tile.pos()).all().sort(Comparator.comparingInt(Action::getId)).copy();
			StringBuilder sb = new StringBuilder("[orange]");
			sb.append(tile.x).append("[], [orange]").append(tile.y);
			for (Action action : actions) {
				sb.append("\n[accent]");
				sb.append(Strings.formatMillis(Time.timeSinceMillis(action.getTime()))).append(" ago: [white]");
				String name = Vars.netServer.admins.getInfo(action.getUuid()).lastName;
				sb.append(name);
				if (!action.getUuid().equals("")) {
					sb.append(" [sky]- ").append(Utils.calculatePhash(action.getUuid()));
				}
				sb.append("\n[white]");
				if (action instanceof BuildAction) {
					sb.append("Built ").append(((BuildAction)action).getBlock().name);
				} else {
					BuildAction prevBuild = action.getTileInfo().select(-1, BuildAction.class,
							a -> a.getId() < action.getId());
					sb.append(action instanceof ConfigAction ? "Configured " : "Deleted ");
					sb.append(prevBuild != null ? prevBuild.getBlock() : "<???>");
				}
			}
			inspectorCache.put(player, sb.toString());
		});

		Timer.schedule(() -> {
			for (var entry : inspectorCache) {
				Call.infoPopup(entry.key.con, entry.value, 1f, Align.bottomRight, 0, 0, 400, 0);
			}
		}, 0f, 1f);
	}

	@Override
	public void registerCommands(CommandHandler handler) {
		Cooldowns.instance.set("undo", 60);
		handler.<Player>register("undo", Strings.format("[name] [duration=@]", UNDO_DURATION), "Undo the actions of a player", (args, player)-> {
			String uuid = player.uuid();
			if (!Cooldowns.instance.canRun("undo", uuid)) {
				player.sendMessage(GameMsg.ratelimit("Undo", "undo"));
				return;
			}

			if (warned.contains(player.uuid())) {
				player.sendMessage(GameMsg.error("Undo", "You can't undo actions of other players, because you are flagged as a potential griefer!"));
				return;
			}
			if (Groups.player.size() < 3) {
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
				Team team  = players.get(info.id, (Team) null);
				if (info.admin) {
					player.sendMessage(GameMsg.error("Undo", "Can't undo an admin."));
					return;
				} else if (team == null) {
					Log.info("Id is @", info.id);
					player.sendMessage(GameMsg.error("Undo", "That player has not joined this round."));
					return;
				} else if (team != player.team()) {
					player.sendMessage(GameMsg.error("Undo", "Can't undo players on opposing teams."));
					return;
				}
				target = info.id;
			} else {
				player.sendMessage(Utils.playerList((p) -> !p.admin && p.con != null && p != player));
				return;
			}
			int duration = args.length == 2 ? Strings.parseInt(args[1], UNDO_DURATION) : UNDO_DURATION;
			duration = Math.min(duration, 120);
			String finalTarget = target;
			int finalDuration = duration;
			Sessions.newSession(player, () -> new UndoSession(player, finalTarget, finalDuration));
		});

		handler.<Player>register("toggleinspector", "Toggles the tile inspector", (args, player) -> {
			if (inspectorCache.containsKey(player)) {
				inspectorCache.remove(player);
				player.sendMessage(GameMsg.info("Undo", "Inspector disabled"));
			} else {
				inspectorCache.put(player, "Click a tile");
				player.sendMessage(GameMsg.info("Undo", "Inspector enabled"));
			}
		});
	}

	@Override
	public void registerServerCommands(CommandHandler handler) {
		handler.register("undo", "<time> [player...]", "Force undo the actions of a player", arg -> {
			if (arg.length < 2) {
				Log.info("Current players:");
				Log.info(Utils.playerList(p -> true));
				Log.info("Players that have joined this round:");
				Log.info(players.keys().toSeq().toString());
				return;
			}
			int time = Strings.parseInt(arg[0], -1);
			if (time <= 0) {
				Log.warn("Invalid time provided");
			}
			long start = Time.millis() - time * 1000L;
			String target = arg[1];
			Player found = Query.findPlayerEntity(target);
			Administration.PlayerInfo info = Query.findPlayerInfo(target);
			if (found == null && (info == null || !players.containsKey(info.id))) {
				Log.warn("Player not found. However, an undo using the string as the uuid will be done");
			}
			if (found != null) {
				instance.rollback(found.uuid(), start);
				Call.sendMessage(GameMsg.info("Undo", "Forced undo of " + found.name));
			} else if (info != null) {
				instance.rollback(info.id, start);
				Call.sendMessage(GameMsg.info("Undo", "Forced undo of " + info.lastName));
			} else {
				instance.rollback(target, start);
				Call.sendMessage(GameMsg.info("Undo", "Forced undo done"));
			}
		});
	}
}

class UndoSession extends Sessions.Session {
	public final static int Vote_Time = 30;
	public final static int Extend_Time = 15;

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
		this.endTime += Extend_Time*1000L;
		int score = yes-no;
		int required = (players/2) + 1;
		if (score >= required) {
			Undo.instance.rollback(this.target, this.startTime - (this.duration * 60 * 1000L));
			Call.sendMessage(GameMsg.info("Undo", "Vote passed, undoing(@ min)", this.duration));
			return true;
		}
		Call.sendMessage(GameMsg.info("Undo", "@ has voted, (@/@) @ left",
				player.name, score, required, Strings.formatMillis(-Time.timeSinceMillis(this.endTime))));
		return false;
	}

	@Override
	public String desc() {
		return Strings.format("Undo actions of @[white](@ min)", Query.findPlayerInfo(this.target).lastName, this.duration);
	}
}
