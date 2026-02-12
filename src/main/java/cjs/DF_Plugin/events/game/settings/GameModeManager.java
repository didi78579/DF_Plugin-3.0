package cjs.DF_Plugin.events.game.settings;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.file.FileConfiguration;

public class GameModeManager {

    private final DF_Main plugin;
    private final GameConfigManager configManager;

    public GameModeManager(DF_Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getGameConfigManager();
    }

    /**
     * config.yml에 설정된 현재 게임 모드를 시스템 토글에 적용합니다.
     * 이 메서드는 플러그인 활성화 초기에 호출되어야 합니다.
     */
    public void applyCurrentMode() {
        String modeKey = configManager.getConfig().getString("game-mode", "darkforest");
        applyPreset(modeKey, false); // 시작 시에는 메시지를 보내지 않음
    }

    /**
     * 지정된 게임 모드 프리셋을 적용하고 설정을 업데이트합니다.
     * (관리자 명령어용)
     * @param modeKey 적용할 게임 모드의 키 (예: "darkforest")
     */
    public void applyPreset(String modeKey) {
        applyPreset(modeKey, true); // 명령어 등으로 호출될 때는 메시지 전송
    }

    /**
     * 프리셋을 적용하는 내부 로직.
     * @param modeKey 적용할 모드 키
     * @param notify 로그에 적용 사실을 알릴지 여부
     */
    private void applyPreset(String modeKey, boolean notify) {
        GameMode mode = GameMode.fromString(modeKey);
        if (mode == null) {
            plugin.getLogger().warning("[게임 모드] 알 수 없는 게임 모드 '" + modeKey + "'가 config.yml에 설정되어 있어, 'darkforest'로 기본 설정합니다.");
            mode = GameMode.DARKFOREST;
        }

        FileConfiguration config = configManager.getConfig();
        config.set("system-toggles.pylon", mode.isPylonEnabled());
        config.set("system-toggles.upgrade", mode.isUpgradeEnabled());
        config.set("system-toggles.events", mode.isEventsEnabled());
        config.set("game-mode", mode.getKey());
        configManager.save();

        if (notify) {
            plugin.getLogger().info("[게임 모드] 게임 모드 프리셋 '" + mode.getKey() + "'이(가) 적용되었습니다.");
            plugin.getLogger().info("[게임 모드] 파일런 시스템: " + mode.isPylonEnabled() + ", 강화 시스템: " + mode.isUpgradeEnabled() + ", 이벤트 시스템: " + mode.isEventsEnabled());
        }
    }

    /**
     * 주어진 문자열이 유효한 게임 모드 키인지 확인합니다.
     * @param modeKey 확인할 게임 모드 키
     * @return 유효하면 true, 그렇지 않으면 false
     */
    public boolean isValidMode(String modeKey) {
        return GameMode.fromString(modeKey) != null;
    }
}