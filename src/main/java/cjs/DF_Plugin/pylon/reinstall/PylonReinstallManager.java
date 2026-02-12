package cjs.DF_Plugin.pylon.reinstall;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 파일런 회수 후, 정해진 시간 안에 재설치해야 하는 '마감 기한'을 관리하는 클래스.
 */
public class PylonReinstallManager {
    private final DF_Main plugin;
    // 파일런 재설치 마감 기한을 저장합니다. (플레이어 UUID -> 마감 시간 타임스탬프)
    private final Map<UUID, Long> reinstallDeadlines = new HashMap<>();
    private static final String PREFIX = PluginUtils.colorize("&b[파일런] &f");

    public PylonReinstallManager(DF_Main plugin) {
        this.plugin = plugin;
        // TODO: 서버 재시작 시 데이터가 유지되도록 파일에서 마감 기한 정보를 로드/저장하는 로직이 필요합니다.
    }

    /**
     * 플레이어가 파일런을 회수했을 때 재설치 마감 기한 타이머를 시작합니다.
     * @param player 파일런을 회수한 플레이어
     */
    public void startReinstallDeadline(Player player) {
        long durationHours = plugin.getGameConfigManager().getConfig().getLong("pylon.retrieval.reinstall-duration-hours", 2);
        if (durationHours <= 0) return;

        long endTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(durationHours);
        reinstallDeadlines.put(player.getUniqueId(), endTime);
        player.sendMessage(PREFIX + "§c파일런을 회수했습니다. " + durationHours + "시간 안에 재설치해야 합니다.");
    }

    /**
     * 플레이어가 성공적으로 파일런을 재설치했을 때 호출되어 마감 기한을 제거합니다.
     * @param player 파일런을 재설치한 플레이어
     */
    public void completeReinstallation(Player player) {
        if (reinstallDeadlines.remove(player.getUniqueId()) != null) {
            player.sendMessage(PREFIX + "§a파일런을 시간 안에 안전하게 재설치했습니다.");
        }
    }

    /**
     * 플레이어의 재설치까지 남은 시간을 가져옵니다.
     * @param player 확인할 플레이어
     * @return 남은 시간 (밀리초). 마감 기한이 지났거나 없으면 0을 반환합니다.
     */
    public long getRemainingTime(Player player) {
        Long endTime = reinstallDeadlines.get(player.getUniqueId());
        if (endTime == null) {
            return 0;
        }
        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 플레이어의 파일런 재설치 마감 기한이 지났는지 확인합니다.
     * @param player 확인할 플레이어
     * @return 마감 기한이 설정되어 있고, 현재 시간이 마감 시간을 지났으면 true
     */
    public boolean hasDeadlinePassed(Player player) {
        Long endTime = reinstallDeadlines.get(player.getUniqueId());
        return endTime != null && System.currentTimeMillis() > endTime;
    }
}