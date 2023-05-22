package mindustry.plugin.minimods;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Time;
import me.mars.rollback.RollbackPlugin;
import me.mars.rollback.TileStore;
import mindustry.plugin.MiniMod;

public class Undo implements MiniMod {
	private static TileStore instance;

	/**
	 * Undo duration in seconds
	 */
	private static final float UNDO_DURATION = 3 * Time.toMinutes;

	public Undo() {
		instance = RollbackPlugin.getTileStore();
	}

	@Override
	public void registerCommands(CommandHandler handler) {

	}

	@Override
	public void registerEvents() {
		Events.on(Kick.KickEvent.class, kickEvent -> {
			float time = kickEvent.startTicks - UNDO_DURATION;
			instance.rollback(kickEvent.uuid, time);
			Log.info("Undoing the actions of @ from @ ticks onwards", kickEvent.uuid, time);
		});
	}


}
