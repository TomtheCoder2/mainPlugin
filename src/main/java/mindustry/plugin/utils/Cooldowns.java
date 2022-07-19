package mindustry.plugin.utils;

import arc.struct.ObjectMap;

public class Cooldowns {
    public static Cooldowns instance = new Cooldowns();

    ObjectMap<String, Long> cooldowns = new ObjectMap<>();
    ObjectMap<String, ObjectMap<String, Long>> lastUse = new ObjectMap<>();

    /**
     * Set cooldown time for a command in seconds
     */
    public void set(String cmd, long sec) {
        cooldowns.put(cmd, sec);
    }

    /**
     * Returns whether a command can be run by the given user. If `set` has not been called with the given command, an error is thrown.
     */
    public boolean canRun(String cmd, String uuid) {
        Long cooldown = cooldowns.get(cmd);
        if (cooldown == null) {
            throw new IllegalStateException("Did not set cooldown for '" + cmd + "' command");
        }
        long cooldownMillis = cooldown * 1000;

        ObjectMap<String, Long> lastUsePlayer = lastUse.get(uuid);
        if (lastUsePlayer == null) {
            return true;
        }
        if (lastUsePlayer.get(cmd) == null) {
            return true;
        }
        return lastUsePlayer.get(cmd).longValue() <= System.currentTimeMillis() - cooldownMillis;
    }

    /**
     * Mark that a command was run at this time
     */
    public void run(String cmd, String uuid) {
        ObjectMap<String, Long> lastUsePlayer = lastUse.get(uuid);
        if (lastUsePlayer == null) {
            lastUsePlayer = new ObjectMap<>();
            lastUse.put(uuid, lastUsePlayer);
        }

        lastUsePlayer.put(cmd, System.currentTimeMillis());
    }
}
