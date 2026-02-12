package cjs.DF_Plugin.event.underworld;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * '마계의 주인' 이벤트를 총괄하는 관리자 클래스 (싱글톤)
 */
public class UnderworldEventManager {

    private static UnderworldEventManager instance;

    private boolean isEventActive = false;
    private World underworld;
    private Wither boss;
    private BossBar bossBar;
    private final Set<UUID> enteredPlayers = new HashSet<>();
    private BukkitTask eventEndTask;

    private static final String UNDERWORLD_NAME = "underworld";
    private static final int EVENT_DURATION_MINUTES = 60;

    private UnderworldEventManager() {}

    public static UnderworldEventManager getInstance() {
        if (instance == null) {
            instance = new UnderworldEventManager();
        }
        return instance;
    }

    public void startEvent() {
        if (isEventActive) return;
        this.isEventActive = true;
        this.enteredPlayers.clear();

        // 1. 마계 월드 생성
        WorldCreator wc = new WorldCreator(UNDERWORLD_NAME);
        wc.environment(World.Environment.NETHER);
        wc.type(WorldType.NORMAL);
        this.underworld = wc.createWorld();
        if (this.underworld == null) {
            Bukkit.getLogger().severe("[UnderworldEvent] Failed to create underworld!");
            this.isEventActive = false;
            return;
        }
        underworld.setGameRule(GameRule.DO_MOB_SPAWNING, false); // 일반 몹 스폰 방지

        // 2. 보스 소환
        spawnBoss();

        // 3. 이벤트 시작 알림
        Bukkit.broadcastMessage("§5[마계의 주인] §c마계의 문이 열렸습니다! 지옥으로 입장하여 마계의 주인에 도전하세요!");

        // 4. 1시간 후 이벤트 자동 종료 스케줄러
        this.eventEndTask = new BukkitRunnable() {
            @Override
            public void run() {
                endEvent("시간이 다 되어");
            }
        }.runTaskLater(DF_Main.getInstance(), EVENT_DURATION_MINUTES * 60 * 20L);
    }

    public void endEvent(String reason) {
        if (!isEventActive) return;

        // 자동 종료 태스크 취소
        if (this.eventEndTask != null && !this.eventEndTask.isCancelled()) {
            this.eventEndTask.cancel();
        }

        Bukkit.broadcastMessage("§5[마계의 주인] §c" + reason + " 마계의 문이 닫힙니다.");

        // 1. 마계에 있는 모든 플레이어 메인 월드로 텔레포트
        if (underworld != null) {
            for (Player player : underworld.getPlayers()) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                player.sendMessage("§c마계와의 연결이 끊어져 메인 월드로 강제 이동됩니다.");
            }
        }
        
        // 2. 보스바 제거
        if (bossBar != null) {
            bossBar.removeAll();
        }

        // 3. 보스 제거 및 월드 언로드
        if (boss != null && !boss.isDead()) {
            boss.remove();
        }
        if (underworld != null) {
            Bukkit.unloadWorld(underworld, false);
        }

        // 4. 상태 초기화
        this.isEventActive = false;
        this.boss = null;
        this.underworld = null;
        this.bossBar = null;
    }

    private void spawnBoss() {
        if (underworld == null) return;
        Location spawnLocation = new Location(underworld, 0, 128, 0); // 예시 위치

        underworld.loadChunk(spawnLocation.getChunk());

        boss = underworld.spawn(spawnLocation, Wither.class, wither -> {
            wither.setCustomName("§5마계의 주인");
            wither.setCustomNameVisible(true);
            wither.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(2000);
            wither.setHealth(2000);
            wither.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(30);
            // Paper API가 있다면 크기 조절 가능: wither.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(3.0);
            wither.setRemoveWhenFarAway(false);
        });
        
        // 보스바 생성
        bossBar = Bukkit.createBossBar("§5마계의 주인", BarColor.PURPLE, BarStyle.SOLID);
        bossBar.setVisible(true);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEventActive) {
                    this.cancel();
                    return;
                }
                if (boss != null && !boss.isDead()) {
                    bossBar.setProgress(boss.getHealth() / boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
                    for (Player p : underworld.getPlayers()) {
                        bossBar.addPlayer(p);
                    }
                } else {
                    endEvent("마계의 주인이 처치되어");
                    this.cancel();
                }
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 20L);
    }
    
    public void addPlayer(Player player) {
        enteredPlayers.add(player.getUniqueId());
    }

    public boolean hasEntered(Player player) {
        return enteredPlayers.contains(player.getUniqueId());
    }

    public boolean isEventActive() {
        return isEventActive;
    }

    public World getUnderworld() {
        return underworld;
    }
    
    public Wither getBoss() {
        return boss;
    }
}