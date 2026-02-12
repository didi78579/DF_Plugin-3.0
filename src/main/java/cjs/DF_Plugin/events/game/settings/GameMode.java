package cjs.DF_Plugin.events.game.settings;

import java.util.Arrays;

public enum GameMode {
    DARKFOREST("darkforest", true, true, true), // 모든 시스템 활성화
    PYLON("pylon", true, false, false),         // 파일런 시스템만 활성화
    UPGRADE("upgrade", false, true, false),       // 강화 시스템만 활성화
    PEACEFUL("peaceful", false, true, false);   // pylon, events 비활성화 (UPGRADE와 동일)

    private final String key;
    private final boolean pylonEnabled;
    private final boolean upgradeEnabled;
    private final boolean eventsEnabled;

    GameMode(String key, boolean pylonEnabled, boolean upgradeEnabled, boolean eventsEnabled) {
        this.key = key;
        this.pylonEnabled = pylonEnabled;
        this.upgradeEnabled = upgradeEnabled;
        this.eventsEnabled = eventsEnabled;
    }

    public String getKey() {
        return key;
    }

    public boolean isPylonEnabled() {
        return pylonEnabled;
    }

    public boolean isUpgradeEnabled() {
        return upgradeEnabled;
    }

    public boolean isEventsEnabled() {
        return eventsEnabled;
    }

    public static GameMode fromString(String key) {
        return Arrays.stream(values())
                .filter(mode -> mode.getKey().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }
}