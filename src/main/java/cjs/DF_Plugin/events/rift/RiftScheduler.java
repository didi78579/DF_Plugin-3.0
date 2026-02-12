package cjs.DF_Plugin.events.rift;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public class RiftScheduler implements Listener {

    private final DF_Main plugin;

    private enum State { COOLDOWN, COUNTDOWN }
    private State currentState;
    private long nextStateTime; // Either cooldown end time or event start time
    private BukkitTask dayCheckTask;

    private static final String CONFIG_PATH_ROOT = "events.rift.";
    private static final String CONFIG_PATH_STATE = CONFIG_PATH_ROOT + "state";
    private static final String CONFIG_PATH_NEXT_STATE_TIME = CONFIG_PATH_ROOT + "next-state-time";

    public RiftScheduler(DF_Main plugin) {
        this.plugin = plugin;
    }

    private void loadState() {
        FileConfiguration config = plugin.getEventDataManager().getConfig();
        if (!config.contains(CONFIG_PATH_STATE)) {
            plugin.getLogger().info("[차원의 균열] Scheduler state not found. Initializing.");
            startNewCooldown(); // 새로운 쿨다운 시작 및 파일 생성
            return;
        }

        this.currentState = State.valueOf(config.getString(CONFIG_PATH_STATE, "COOLDOWN"));
        this.nextStateTime = config.getLong(CONFIG_PATH_NEXT_STATE_TIME, 0);

        // If an event is already active (handled by RiftManager's own persistence),
        // we should just wait. The scheduler's job is to start the *next* event.
        if (plugin.getRiftManager().isEventActive()) {
            plugin.getLogger().info("[차원의 균열] An event is already active. Scheduler will wait.");
            return;
        }

        // If server was off, check if we missed the state transition
        if (System.currentTimeMillis() >= nextStateTime) {
            if (currentState == State.COOLDOWN) {
                startCountdown();
            } else if (currentState == State.COUNTDOWN) {
                startEventNow();
            }
        }

        plugin.getLogger().info("[차원의 균열] Loaded scheduler state: " + currentState);
    }

    private void saveState() {
        FileConfiguration config = plugin.getEventDataManager().getConfig();
        config.set(CONFIG_PATH_STATE, currentState.name());
        config.set(CONFIG_PATH_NEXT_STATE_TIME, nextStateTime);
        plugin.getEventDataManager().saveConfig();
    }

    public void startScheduler() {
        loadState();
        if (dayCheckTask != null) {
            dayCheckTask.cancel();
        }

        dayCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getGameConfigManager().getConfig().getBoolean("events.rift.enabled", true)) return;
                if (!plugin.getGameStartManager().isGameStarted()) return;
                if (plugin.getRiftManager().isEventActive()) return; // Don't do anything if an event is running

                if (System.currentTimeMillis() >= nextStateTime) {
                    if (currentState == State.COOLDOWN) {
                        startCountdown();
                    } else if (currentState == State.COUNTDOWN) {
                        startEventNow();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // 1분마다 확인
    }

    public void stopScheduler() {
        if (dayCheckTask != null) {
            dayCheckTask.cancel();
            dayCheckTask = null;
        }
    }

    public void startEventNow() {
        if (!plugin.getGameConfigManager().getConfig().getBoolean("events.rift.enabled", true)) {
            plugin.getLogger().info("[차원의 균열] 이벤트가 config.yml에서 비활성화되어 있어 시작할 수 없습니다.");
            return;
        }
        if (plugin.getRiftManager().isEventActive()) {
            plugin.getLogger().warning("[차원의 균열] An event is already active. Cannot start a new one.");
            return;
        }

        plugin.getLogger().info("[차원의 균열] Triggering rift event.");
        plugin.getRiftManager().triggerEvent();
        // The manager will now handle the ACTIVE state. The scheduler's job is done until the next cooldown.
    }

    private void startCountdown() {
        this.currentState = State.COUNTDOWN;
        long spawnDelayHours = plugin.getGameConfigManager().getConfig().getLong("events.rift.spawn-delay-hours", 1);
        this.nextStateTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(spawnDelayHours);
        saveState();
        plugin.getLogger().info("[차원의 균열] Countdown started. Event will begin in " + spawnDelayHours + " hour(s).");
        Bukkit.broadcastMessage("§d[차원의 균열] §f강력한 기운이 감지됩니다! " + spawnDelayHours + "시간 뒤 균열 제단이 나타납니다!");
    }

    public void startNewCooldown() {
        this.currentState = State.COOLDOWN;
        long cooldownHours = plugin.getGameConfigManager().getConfig().getLong("events.rift.cooldown-hours", 8);
        this.nextStateTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(cooldownHours);
        saveState();
        plugin.getLogger().info("[차원의 균열] 새로운 " + cooldownHours + "시간 쿨다운이 시작되었습니다.");
    }

}