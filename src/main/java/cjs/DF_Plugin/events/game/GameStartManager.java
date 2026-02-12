package cjs.DF_Plugin.events.game;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.InventoryUtils;
import cjs.DF_Plugin.util.item.PylonItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class GameStartManager {

    private final DF_Main plugin;
    private boolean isGameStarted = false;
    private long nextGiftBoxRefillTime = 0L;
    private final Random random = new Random();

    public GameStartManager(DF_Main plugin) {
        this.plugin = plugin;
        loadState();
    }

    public void startGame() {
        if (isGameStarted) {
            Bukkit.broadcastMessage("§c[알림] §c이미 게임이 시작되었습니다.");
            return;
        }
        isGameStarted = true;

        plugin.getLogger().info("[게임 관리] 게임 시작 프로세스를 진행합니다...");

        // 1. 모든 가문의 선물상자 타이머를 현재 시간으로 초기화합니다.
        plugin.getClanManager().getAllClans().forEach(clan -> {
            if (clan.getLastGiftBoxTime() == 0L) { // 아직 시간이 설정되지 않은 가문만
                clan.setLastGiftBoxTime(System.currentTimeMillis());
                plugin.getClanManager().saveClanData(clan);
            }
        });

        // 2. 선물상자 리필 작업을 시작합니다.
        plugin.getGiftBoxManager().scheduleRefillTask();

        // 3. 보급 이벤트 스케줄러를 시작합니다.
        //    새 게임 시작 시, 이전 상태와 관계없이 새로운 쿨다운을 시작합니다.
        plugin.getRiftScheduler().startNewCooldown();
        plugin.getRiftScheduler().startScheduler();

        // 4. 모든 가문의 시작 지점을 설정합니다. (텔레포트는 재접속 시)
        for (Clan clan : plugin.getClanManager().getAllClans()) {
            Location randomLoc = getRandomSafeLocation(Bukkit.getWorlds().get(0));
            clan.setStartLocation(randomLoc);
            plugin.getClanManager().saveClanData(clan);
        }

    // 5. 모든 플레이어의 초기 텔레포트 필요 상태를 'true'로 설정합니다.
    //    이후, 온라인 상태에서 바로 텔레포트된 플레이어는 이 상태를 'false'로 변경합니다.
    plugin.getPlayerDataManager().resetAllInitialTeleportFlags();

    // [NEW] 게임 시작 시 모든 플레이어를 적절한 위치로 이동시키고 모드를 설정합니다.
        for (Player player : Bukkit.getOnlinePlayers()) {
            Clan playerClan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (playerClan != null && playerClan.getLeader().equals(player.getUniqueId())) {
                // 리더: 가문 시작 지점으로 텔레포트
                Location startLoc = playerClan.getStartLocation();
                if (startLoc != null) {
                    player.teleport(startLoc);
                    player.setGameMode(GameMode.SURVIVAL); // 리더는 서바이벌 모드
                    // 게임 시작 시 가문 대표에게 파일런 코어를 지급합니다.
                    InventoryUtils.giveOrDropItems(player, PylonItemFactory.createMainCore());
                    // 이미 텔레포트되었으므로, 재접속 시 다시 이동하지 않도록 플래그를 업데이트합니다.
                    plugin.getPlayerDataManager().setInitialTeleportDone(player.getUniqueId(), true);
                } else {
                    player.sendMessage("§c[알림] §c가문 시작 지점을 찾을 수 없습니다. 기본 스폰으로 이동합니다.");
                    player.teleport(player.getWorld().getSpawnLocation());
                    player.setGameMode(GameMode.SURVIVAL);
                }
            } else {
                // 비리더 또는 클랜 없는 플레이어: 관전 모드로 전환 및 제한 적용
                plugin.getSpectatorManager().setSpectator(player);
                player.sendMessage("§e[알림] §e게임이 시작되어 관전 모드로 전환됩니다.");
            }
        }
        Bukkit.broadcastMessage("§a[알림] §f다크포레스트 게임이 시작되었습니다!");

        saveState();
    }

    public void stopGame() {
        if (!isGameStarted) {
            Bukkit.broadcastMessage("§c[알림] §c시작된 게임이 없습니다.");
            return;
        }
        isGameStarted = false;
        this.nextGiftBoxRefillTime = 0L; // 리필 시간 초기화

        // 타이머 중지
        plugin.getGiftBoxManager().stopRefillTask();
        plugin.getRiftScheduler().stopScheduler();

        // 모든 가문의 시작 지점 정보 제거
        plugin.getClanManager().getAllClans().forEach(clan -> {
            clan.setStartLocation(null);
            plugin.getClanManager().saveClanData(clan);
        });

        // 모든 플레이어를 서바이벌 모드로 되돌리고 스폰으로 이동
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(player.getWorld().getSpawnLocation());
            }
        }
        Bukkit.broadcastMessage("§6[알림] §e게임이 중지되었습니다!");
        saveState();
    }

    /**
     * 서버 재시작 시, 게임이 이미 진행 중일 경우 중단되었던 게임 관련 작업(타이머 등)을 재개합니다.
     * 이 메서드는 DF_Main의 onEnable에서 호출됩니다.
     */
    public void resumeTasksOnRestart() {
        if (!isGameStarted) return;

        plugin.getLogger().info("[게임 관리] 게임 진행 상태 확인: 중단되었던 게임 관련 작업을 재개합니다...");

        // 1. 선물상자 리필 작업을 남은 시간에 맞춰 다시 시작합니다.
        long nextRefillTimestamp = getNextGiftBoxRefillTime();
        long currentTime = System.currentTimeMillis();
        long delayTicks = 0;

        // 다음 리필 시간이 0이 아니고, 미래인 경우에만 지연 시간을 계산합니다.
        if (nextRefillTimestamp > 0 && nextRefillTimestamp > currentTime) {
            delayTicks = (nextRefillTimestamp - currentTime) / 50; // 1초 = 20틱, 1틱 = 50ms
        }
        // 다음 리필 시간이 과거라면, 지연 없이 즉시 실행합니다 (delayTicks = 0).

        plugin.getLogger().info("[게임 관리] 다음 선물상자 리필까지 약 " + String.format("%.1f", delayTicks / 20.0) + "초 후 작업을 재개합니다.");

        // 지연 시간 후, 첫 리필을 수행하고, 그 다음부터 주기적인 리필을 시작하는 일회성 작업을 예약합니다.
        new BukkitRunnable() {
            @Override
            public void run() {
                // 1. 첫 리필 수행
                plugin.getLogger().info("[게임 관리] 예약된 선물상자 보충을 실행합니다.");
                long refillTime = System.currentTimeMillis();
                plugin.getClanManager().refillAllGiftBoxes(refillTime);

                // 2. 다음 리필 시간 업데이트 및 저장
                long intervalMillis = plugin.getGameConfigManager().getConfig().getLong("pylon.giftbox.cooldown-minutes", 5) * 60 * 1000;
                setNextGiftBoxRefillTime(refillTime + intervalMillis);
                saveState();
                
                // 3. 이후의 모든 주기적인 리필 작업을 GiftBoxManager에 위임하여 시작
                plugin.getGiftBoxManager().scheduleRefillTask();
                plugin.getLogger().info("[게임 관리] 선물상자 리필 스케줄러를 재개했습니다.");
            }
        }.runTaskLater(plugin, delayTicks);

        // 2. 보급 이벤트 스케줄러를 다시 시작합니다.
        plugin.getRiftScheduler().startScheduler();
    }

    public boolean isGameStarted() {
        return isGameStarted;
    }

    public void loadState() {
        this.isGameStarted = plugin.getEventDataManager().getConfig().getBoolean("game.started", false);
        this.nextGiftBoxRefillTime = plugin.getEventDataManager().getConfig().getLong("game.nextGiftBoxRefillTime", 0L);
        if (isGameStarted) {
            plugin.getLogger().info("[게임 관리] 저장된 게임 상태를 불러왔습니다: 게임 진행 중");
        } else {
            plugin.getLogger().info("[게임 관리] 저장된 게임 상태를 불러왔습니다: 게임 미진행 중");
        }
    }

    public void saveState() {
        plugin.getEventDataManager().getConfig().set("game.started", isGameStarted);
        plugin.getEventDataManager().getConfig().set("game.nextGiftBoxRefillTime", this.nextGiftBoxRefillTime);
        plugin.getEventDataManager().saveConfig();
        plugin.getLogger().info("[게임 관리] 게임 진행 상태를 저장했습니다.");
    }

    public long getNextGiftBoxRefillTime() {
        return nextGiftBoxRefillTime;
    }

    public void setNextGiftBoxRefillTime(long nextGiftBoxRefillTime) {
        this.nextGiftBoxRefillTime = nextGiftBoxRefillTime;
    }

    public Location getRandomSafeLocation(World world) {
        double borderSize = plugin.getGameConfigManager().getConfig().getDouble("world.border.overworld-size", 20000);
        double radius = (borderSize / 2.0) * 0.9; // 경계선 바로 근처는 피하도록 90% 반경으로 설정

        double angle = random.nextDouble() * 2 * Math.PI;
        double r = radius * Math.sqrt(random.nextDouble());
        int x = (int) (r * Math.cos(angle));
        int z = (int) (r * Math.sin(angle));
        int y = world.getHighestBlockYAt(x, z) + 1;

        return new Location(world, x + 0.5, y, z + 0.5);
    }
}