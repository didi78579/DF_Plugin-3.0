package cjs.DF_Plugin.events.end;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.UpgradeItems;
import cjs.DF_Plugin.world.enchant.MagicStone;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class EndEventManager {

    private final DF_Main plugin;

    private enum State { CLOSED, SCHEDULED, OPEN, COLLAPSING }
    private State currentState = State.CLOSED;

    private long nextEventTime = -1; // For SCHEDULED state
    private long collapseFinishTime = -1; // For COLLAPSING state

    private BukkitTask openTask;
    private BukkitTask collapseUpdateTask;
    private BossBar collapseBossBar;

    private static final String CONFIG_PATH_STATE = "events.end.state";
    private static final String CONFIG_PATH_NEXT_EVENT_TIME = "events.end.next-event-time";
    private static final String CONFIG_PATH_COLLAPSE_FINISH_TIME = "events.end.collapse-finish-time";

    public EndEventManager(DF_Main plugin) {
        this.plugin = plugin;
        loadState();
    }

    /**
     * 서버 시작 시 event_data.yml에서 엔드 이벤트의 상태(개방, 예약, 붕괴)를 불러옵니다.
     */
    public void loadState() {
        FileConfiguration config = plugin.getEventDataManager().getConfig();
        this.currentState = State.valueOf(config.getString(CONFIG_PATH_STATE, "CLOSED"));
        this.nextEventTime = config.getLong(CONFIG_PATH_NEXT_EVENT_TIME, -1);
        this.collapseFinishTime = config.getLong(CONFIG_PATH_COLLAPSE_FINISH_TIME, -1);

        switch (currentState) {
            case SCHEDULED:
                long delayMillis = nextEventTime - System.currentTimeMillis();
                if (delayMillis <= 0) {
                    openEnd(false);
                } else {
                    scheduleOpen(TimeUnit.MILLISECONDS.toMinutes(delayMillis), false);
                }
                break;
            case COLLAPSING:
                long remainingMillis = collapseFinishTime - System.currentTimeMillis();
                if (remainingMillis <= 0) {
                    closeAndResetEnd();
                } else {
                    long totalDurationMinutes = plugin.getGameConfigManager().getConfig().getLong("end-event.collapse-delay-minutes", 10);
                    Bukkit.broadcastMessage("§5[엔드 이벤트] §c엔드 월드 붕괴가 재개됩니다! 서둘러 탈출하세요!");
                    startCollapseCountdown(totalDurationMinutes, collapseFinishTime);
                }
                break;
            default:
                // OPEN, CLOSED 상태는 별도 조치 없음
                break;
        }
    }

    private void saveState() {
        FileConfiguration config = plugin.getEventDataManager().getConfig();
        config.set(CONFIG_PATH_STATE, currentState.name());
        config.set(CONFIG_PATH_NEXT_EVENT_TIME, nextEventTime);
        config.set(CONFIG_PATH_COLLAPSE_FINISH_TIME, collapseFinishTime);
        plugin.getEventDataManager().saveConfig();
    }

    public void openEnd(boolean broadcast) {
        if (currentState == State.OPEN) return;

        // 엔드를 열기 전에 항상 월드를 초기화하여 새로운 경험을 제공합니다.
        plugin.getLogger().info("[엔드 이벤트] Resetting The End before opening...");
        plugin.getWorldManager().resetWorld("world_the_end");

        // 월드가 로드되었는지 확인하고, 그렇지 않으면 생성합니다.
        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld == null) {
            plugin.getLogger().info("[엔드 이벤트] The End world is not loaded, creating it now...");
            endWorld = new WorldCreator("world_the_end")
                    .environment(World.Environment.THE_END)
                    .createWorld();
            if (endWorld == null) {
                plugin.getLogger().severe("[엔드 이벤트] 엔드 월드를 생성하는 데 실패했습니다!");
                Bukkit.broadcastMessage("§c[오류] 엔드 월드를 생성하는 데 실패했습니다. 서버 관리자에게 문의하세요.");
                return;
            }
        }

        // 월드 규칙을 다시 적용하여 새로 생성된 엔드 월드에도 config.yml 설정이 반영되도록 합니다.
        plugin.getLogger().info("[엔드 이벤트] 엔드 월드에 게임 규칙을 적용합니다...");
        plugin.getWorldManager().applyAllWorldSettings();

        if (openTask != null) {
            openTask.cancel();
            openTask = null;
        }

        this.currentState = State.OPEN;
        this.nextEventTime = -1;
        this.collapseFinishTime = -1;
        saveState();

        if (broadcast) {
            Bukkit.broadcastMessage("§5[엔드 이벤트] §d엔더 드래곤의 포효가 들려옵니다! 엔드 포탈이 활성화되었습니다!");
            // 모든 플레이어에게 엔드 포탈 활성화 소리를 재생합니다.
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);
            }
        }
        plugin.getLogger().info("[엔드 이벤트] The End has been opened.");
    }

    public void scheduleOpen(long minutes, boolean broadcast) {
        if (minutes <= 0) {
            openEnd(true);
            return;
        }

        if (openTask != null) {
            openTask.cancel();
        }

        this.currentState = State.SCHEDULED;
        this.nextEventTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes);
        this.collapseFinishTime = -1;
        saveState();

        if (broadcast) {
            Bukkit.broadcastMessage("§5[엔드 이벤트] §d공허의 기운이 꿈틀거립니다... " + minutes + "분 뒤 엔드 포탈이 열립니다!");
        }

        openTask = Bukkit.getScheduler().runTaskLater(plugin, () -> openEnd(true), minutes * 60 * 20L);
        plugin.getLogger().info("[엔드 이벤트] The End is scheduled to open in " + minutes + " minutes.");
    }

    /**
     * 관리자 명령 등으로 엔드를 강제로 닫고 초기화합니다.
     */
    public void forceCloseEnd() {
        // 붕괴 중이 아니더라도 관련 상태를 모두 초기화하기 위해 closeAndResetEnd를 호출합니다.
        Bukkit.broadcastMessage("§5[엔드 이벤트] §4관리자에 의해 엔드 월드가 강제로 닫혔습니다.");
        closeAndResetEnd();
    }

    public boolean isEndOpen() {
        return currentState == State.OPEN;
    }

    /**
     * 엔더 드래곤 처치 시 보상 지급 및 월드 붕괴 절차를 시작합니다.
     */
    public void triggerDragonDefeatSequence() {
        if (currentState != State.OPEN) return;

        // 드래곤이 죽으면 붕괴 상태로 전환됩니다. isEndOpen은 false가 되어 더 이상 진입할 수 없습니다.
        this.currentState = State.COLLAPSING;
        Bukkit.broadcastMessage("§5[엔드 이벤트] §c엔더 드래곤이 쓰러져 엔드 포탈이 닫혔습니다!");

        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld != null) {
            scatterRewards(endWorld);
            Bukkit.broadcastMessage("§5[엔드 이벤트] §d엔더 드래곤이 쓰러졌습니다! 하늘에서 보상이 쏟아집니다!");
        } else {
            plugin.getLogger().log(Level.WARNING, "[엔드 이벤트] 엔드 월드가 로드되지 않아 보상을 지급할 수 없습니다.");
        }

        long delayMinutes = plugin.getGameConfigManager().getConfig().getLong("end-event.collapse-delay-minutes", 10);
        Bukkit.broadcastMessage("§5[엔드 이벤트] §c엔드 월드가 " + delayMinutes + "분 뒤 붕괴를 시작합니다! 서둘러 탈출하세요!");

        this.collapseFinishTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes);
        saveState();
        startCollapseCountdown(delayMinutes, this.collapseFinishTime);
    }

    /**
     * 붕괴 카운트다운과 보스바 업데이트를 시작합니다.
     * @param totalDurationMinutes 붕괴까지 총 소요 시간(분)
     */
    private void startCollapseCountdown(long totalDurationMinutes, long finishTime) {
        if (collapseUpdateTask != null) {
            collapseUpdateTask.cancel();
        }
        if (collapseBossBar != null) {
            collapseBossBar.removeAll();
        }

        collapseBossBar = Bukkit.createBossBar("§c엔드 월드 붕괴까지...", BarColor.RED, BarStyle.SOLID);
        collapseBossBar.setVisible(true);

        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld != null) {
            endWorld.getPlayers().forEach(collapseBossBar::addPlayer);
        }

        final long totalDurationMillis = TimeUnit.MINUTES.toMillis(totalDurationMinutes);

        collapseUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                long remainingMillis = finishTime - System.currentTimeMillis();

                if (remainingMillis <= 0) {
                    closeAndResetEnd();
                    this.cancel();
                    return;
                }

                double progress = (double) remainingMillis / totalDurationMillis;
                collapseBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 업데이트
    }

    private void closeAndResetEnd() {
        if (collapseUpdateTask != null) {
            collapseUpdateTask.cancel();
            collapseUpdateTask = null;
        }
        if (collapseBossBar != null) {
            collapseBossBar.removeAll();
            collapseBossBar.setVisible(false);
            collapseBossBar = null;
        }
        // 예약된 개방 작업이 있다면 취소합니다.
        if (openTask != null) {
            openTask.cancel();
            openTask = null;
        }

        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld != null) {
            // 플레이어 목록을 복사하여 반복 중 텔레포트로 인한 리스트 변경 오류를 방지합니다.
            for (Player player : new java.util.ArrayList<>(endWorld.getPlayers())) {
                player.sendMessage("§5[엔드 이벤트] §c엔드 월드가 붕괴하여 안전한 장소로 귀환합니다.");
                teleportPlayerToSafety(player);
            }
        }

        this.currentState = State.CLOSED;
        this.nextEventTime = -1;
        this.collapseFinishTime = -1;
        saveState();

        Bukkit.broadcastMessage("§5[엔드 이벤트] §4엔드 월드가 붕괴하여 닫혔습니다.");
        plugin.getLogger().info("[엔드 이벤트] The End has been closed and is being reset.");

        plugin.getWorldManager().resetWorld("world_the_end");
    }

    public void teleportPlayerToSafety(Player player) {
        plugin.getWorldManager().teleportPlayerToSafety(player);
    }

    /**
     * 현재 엔드 월드가 붕괴 중인지 확인합니다.
     * @return 붕괴 중이면 true
     */
    public boolean isCollapsing() {
        return this.currentState == State.COLLAPSING;
    }

    /**
     * 특정 플레이어를 붕괴 보스바에 추가합니다.
     * @param player 추가할 플레이어
     */
    public void addPlayerToCollapseBar(Player player) {
        if (collapseBossBar != null && isCollapsing()) {
            collapseBossBar.addPlayer(player);
        }
    }

    public void removePlayerFromCollapseBar(Player player) {
        if (collapseBossBar != null) {
            collapseBossBar.removePlayer(player);
        }
    }

    /**
     * 엔더 드래곤 처치 후 설정된 위치에 보상을 뿌립니다.
     * @param world 보상을 뿌릴 월드 (엔드 월드)
     */
    public void scatterRewards(World world) {
        FileConfiguration config = plugin.getGameConfigManager().getConfig();
        if (world.getEnvironment() != World.Environment.THE_END) {
            plugin.getLogger().warning("[엔드 이벤트] 보상 뿌리기는 엔드 월드에서만 가능합니다.");
            return;
        }

        // 설정 값 읽기
        int areaSize = config.getInt("end-event.rewards.area-size", 100);
        int dropY = config.getInt("end-event.rewards.drop-y-level", 200);
        int minStack = config.getInt("end-event.rewards.min-stack-size", 2);
        int maxStack = config.getInt("end-event.rewards.max-stack-size", 4);
        int minTotal = config.getInt("end-event.rewards.min-total-quantity", 1000);
        int maxTotal = config.getInt("end-event.rewards.max-total-quantity", 2000);

        int totalItemsToDrop = ThreadLocalRandom.current().nextInt(minTotal, maxTotal + 1);
        int itemsDropped = 0;

        plugin.getLogger().info(String.format("[엔드 이벤트] 엔더 드래곤 보상을 뿌립니다. 총 %d개", totalItemsToDrop));

        while (itemsDropped < totalItemsToDrop) {
            // 랜덤 위치 생성
            double x = ThreadLocalRandom.current().nextDouble(-areaSize / 2.0, areaSize / 2.0);
            double z = ThreadLocalRandom.current().nextDouble(-areaSize / 2.0, areaSize / 2.0);
            Location dropLocation = new Location(world, x, dropY, z);

            // 랜덤 스택 크기 결정
            int stackSize = ThreadLocalRandom.current().nextInt(minStack, maxStack + 1);
            if (itemsDropped + stackSize > totalItemsToDrop) {
                stackSize = totalItemsToDrop - itemsDropped;
            }

            // 50% 확률로 마석 또는 강화석 선택
            ItemStack rewardItem;
            if (ThreadLocalRandom.current().nextBoolean()) {
                rewardItem = MagicStone.createMagicStone(stackSize);
            } else {
                rewardItem = UpgradeItems.createUpgradeStone(stackSize);
            }

            // 아이템 드롭 및 발광 효과 적용
            Item droppedItemEntity = world.dropItemNaturally(dropLocation, rewardItem);
            droppedItemEntity.setGlowing(true);
            droppedItemEntity.setUnlimitedLifetime(true); // 아이템이 사라지지 않도록 설정

            itemsDropped += stackSize;
        }
    }
}