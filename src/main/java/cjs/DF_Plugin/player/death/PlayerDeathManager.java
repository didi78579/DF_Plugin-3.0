package cjs.DF_Plugin.player.death;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.data.PlayerDataManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 플레이어의 사망 및 밴 상태를 관리하는 클래스
 */
public class PlayerDeathManager implements Listener {
    private final DF_Main plugin;
    private final Map<UUID, Long> deadPlayers = new ConcurrentHashMap<>(); // Player UUID -> Death Timestamp

    public PlayerDeathManager(DF_Main plugin) {
        this.plugin = plugin;
        loadAllData();
    }

    private void loadAllData() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        FileConfiguration config = pdm.getConfig();
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection != null) {
            playersSection.getKeys(false).forEach(uuidString -> {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    if (config.isSet("players." + uuidString + ".death.timestamp")) {
                        deadPlayers.put(uuid, config.getLong("players." + uuidString + ".death.timestamp"));
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[사망 관리] Invalid UUID found in playerdata.yml: " + uuidString);
                }
            });
        }
        plugin.getLogger().info("[사망 관리] Loaded " + deadPlayers.size() + " death ban entries.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final boolean deathBanEnabled = plugin.getGameConfigManager().getConfig().getBoolean("death-timer.enabled", true);
        if (!deathBanEnabled) return;

        Player player = event.getEntity();
        // config.yml의 death-timer.time은 분 단위로 저장됩니다.
        final int banDurationMinutes = plugin.getGameConfigManager().getConfig().getInt("death-timer.time", 60);
        deadPlayers.put(player.getUniqueId(), System.currentTimeMillis());

        // formatDuration은 분 단위를 받습니다.
        String durationString = formatDuration(banDurationMinutes);
        player.kickPlayer("§c사망하여 " + durationString + " 동안 서버에 접속할 수 없습니다.");
    }

    @EventHandler
    public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
        final UUID playerUUID = event.getUniqueId();
        if (!deadPlayers.containsKey(playerUUID)) return;
        
        long remainingMillis = getRemainingBanMillis(playerUUID);
        if (remainingMillis > 0) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, getKickMessage(remainingMillis));
        } else {
            // Ban time is over, allow login and remove from list
            resurrectPlayer(playerUUID);
        }
    }

    public void resurrectPlayer(UUID playerUUID) {
        deadPlayers.remove(playerUUID);
        // 변경 사항은 메모리에서만 관리하고, 파일 저장은 서버 종료 시 한 번에 처리합니다.
    }

    /**
     * 플레이어가 현재 사망 밴 상태인지 확인합니다.
     * @param playerUUID 확인할 플레이어의 UUID
     * @return 밴 상태이면 true, 아니면 false
     */
    public boolean isDeathBanned(UUID playerUUID) {
        return deadPlayers.containsKey(playerUUID);
    }

    public Map<UUID, Long> getDeadPlayers() {
        return deadPlayers;
    }

    /**
     * 서버 종료 시 호출되어 모든 사망자 데이터를 파일에 저장합니다.
     */
    public void saveAllData() {
        plugin.getLogger().info("[사망 관리] Saving " + deadPlayers.size() + " death ban entries...");
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        FileConfiguration config = pdm.getConfig();
        // 기존 데이터를 지우기 위해 players 섹션을 순회하며 death 관련 데이터만 제거
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidStr : playersSection.getKeys(false)) {
                config.set("players." + uuidStr + ".death", null);
            }
        }
        deadPlayers.forEach((uuid, timestamp) -> config.set("players." + uuid.toString() + ".death.timestamp", timestamp));
    }
    private long getRemainingBanMillis(UUID playerUUID) {
        // config.yml의 death-timer.time은 분 단위로 저장됩니다.
        final long deathTime = deadPlayers.get(playerUUID);
        final int banDurationMinutes = plugin.getGameConfigManager().getConfig().getInt("death-timer.time", 60);
        final long banEndTime = deathTime + TimeUnit.MINUTES.toMillis(banDurationMinutes);
        return banEndTime - System.currentTimeMillis();
    }

    private String getKickMessage(long remainingMillis) {
        String remainingTime = String.format("%02d시간 %02d분 %02d초",
                TimeUnit.MILLISECONDS.toHours(remainingMillis),
                TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60,
                TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60
        );
        return "§c사망으로 인해 추방되었습니다.\n§e남은 시간: " + remainingTime;
    }

    private String formatDuration(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "잠시";
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("시간 ");
        }
        if (minutes > 0 || hours == 0) {
            sb.append(minutes).append("분");
        }
        return sb.toString().trim();
    }
}