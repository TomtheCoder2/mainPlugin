package mindustry.plugin.utils;

public enum LogAction {
    uploadMap("Uploaded Map"),
    updateMap("Updated Map"),
    ban("Banned"),
    ipBan("Ip banned"),
    blacklist("Blacklisted"),
    unban("Unbanned"),
    ipUnban("Ip unbanned"),
    kick("Kicked"),
    setRank("");

    private final String name;

    LogAction(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
