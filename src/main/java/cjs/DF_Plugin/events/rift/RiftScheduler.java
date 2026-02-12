package cjs.DF_Plugin.events.rift;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class RiftScheduler {

    private final DF_Main plugin;

    private enum State { COOLDOWN, INACTIVE }
    private State currentState;
    private long nextEventTime;
    private BukkitTask checkTask;

    private static final String CONFIG_PATH_ROOT = "events.rift.";
    private static final String CONFIG_PATH_STATE = CONFIG_PATH_ROOT + "state";
    private static final String CONFIG_PATH_NEXT_EVENT_TIME = CONFIG_PATH_ROOT + "next-event-time";

    public RiftScheduler(DF_Main plugin) {
        this.plugin = plugin;
    }

    private void loadState() {
        FileConfiguration config = plugin.getEventDataManager().getConfig();
        if (!config.contains(CONFIG_PATH_STATE)) {
            plugin.getLogger().info("[차원의 균열] 스케줄러 상태를 찾을 수 없어 초기화합니다.");
            this.currentState = State.INACTIVE;
            this.nextEventTime = 0;
            return;
        }

        this.currentState = State.valueOf(config.getString(CONFIG_PATH_STATE, "INACTIVE"));
        this.nextEventTime = config.getLong(CONFIG_PATH_NEXT_EVENT_TIME, 0);

        if (plugin.getRiftManager().isEventActive()) {
            plugin.getLogger().info("[차원의 균열] 이벤트가 이미 활성화 상태이므로 스케줄러는 대기합니다.");
            return;
        }

        if (currentState == State.COOLDOWN && System.currentTimeMillis() >= nextEventTime) {
            startEventNow();
        }

        plugin.getLogger().info("[차원의 균열] 스케줄러 상태를 불러왔습니다: " + currentState);
    }

    private void saveState() {
        FileConfiguration config = plugin.getEventDataManager().getConfig();
        config.set(CONFIG_PATH_STATE, currentState.name());
        config.set(CONFIG_PATH_NEXT_EVENT_TIME, nextEventTime);
        plugin.getEventDataManager().saveConfig();
    }

    public void startScheduler() {
        loadState();
        if (checkTask != null) {
            checkTask.cancel();
        }

        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getGameConfigManager().getConfig().getBoolean("events.rift.enabled", true)) return;
                if (!plugin.getGameStartManager().isGameStarted()) return;
                if (plugin.getRiftManager().isEventActive()) return;

                if (currentState == State.COOLDOWN && System.currentTimeMillis() >= nextEventTime) {
                    startEventNow();
                }
                
                // 보스바 플레이어 관리
                if (plugin.getRiftManager().isEventActive()) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
                            plugin.getRiftManager().showBarToPlayer(player);
                        } else {
                            plugin.getRiftManager().hideBarFromPlayer(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // 1분마다 확인
    }

    public void stopScheduler() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        this.currentState = State.INACTIVE;
        saveState();
    }

    public void startEventNow() {
        if (!plugin.getGameConfigManager().getConfig().getBoolean("events.rift.enabled", true)) {
            plugin.getLogger().info("[차원의 균열] 이벤트가 비활성화되어 있어 시작할 수 없습니다.");
            return;
        }
        if (plugin.getRiftManager().isEventActive()) {
            plugin.getLogger().warning("[차원의 균열] 이벤트가 이미 활성화 상태입니다.");
            return;
        }

        plugin.getLogger().info("[차원의 균열] 이벤트를 시작합니다.");
        plugin.getRiftManager().triggerEvent();
        this.currentState = State.INACTIVE;
        saveState();
    }

    public void startNewCooldown() {
        this.currentState = State.COOLDOWN;
        
        long minHours = plugin.getGameConfigManager().getConfig().getLong("events.rift.min-cooldown-hours", 8);
        long maxHours = plugin.getGameConfigManager().getConfig().getLong("events.rift.max-cooldown-hours", 12);
        
        long minMinutes = minHours * 60;
        long maxMinutes = maxHours * 60;
        long randomMinutes = ThreadLocalRandom.current().nextLong(minMinutes, maxMinutes + 1);
        
        this.nextEventTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(randomMinutes);
        saveState();
        
        long durationHours = randomMinutes / 60;
        long durationMinutes = randomMinutes % 60;
        plugin.getLogger().info("[차원의 균열] 새로운 랜덤 쿨다운이 시작되었습니다. 다음 이벤트까지 약 " + durationHours + "시간 " + durationMinutes + "분 남았습니다.");
    }

}