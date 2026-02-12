package cjs.DF_Plugin.events.underworld;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UnderworldEventManager {

    private static UnderworldEventManager instance;

    private boolean isEventActive = false;
    private World underworld;
    private Wither boss;
    private final List<Entity> hitboxEntities = new ArrayList<>();
    private final Set<UUID> enteredPlayers = new HashSet<>();
    private BukkitTask eventTask;

    private static final String UNDERWORLD_NAME = "underworld";
    private static final int BORDER_INITIAL_SIZE = 500;
    private static final int BORDER_FINAL_SIZE = 100;
    private static final long BORDER_SHRINK_DELAY_SECONDS = 10 * 60;
    private static final long BORDER_SHRINK_DURATION_SECONDS = 30 * 60;
    private static final int BOSS_AREA_RADIUS = 50;

    private UnderworldEventManager() {}

    public static UnderworldEventManager getInstance() {
        if (instance == null) {
            instance = new UnderworldEventManager();
        }
        return instance;
    }

    public void startEvent() {
        if (isEventActive) return;
        isEventActive = true;
        enteredPlayers.clear();
        hitboxEntities.clear();

        WorldCreator wc = new WorldCreator(new NamespacedKey(DF_Main.getInstance(), UNDERWORLD_NAME));
        wc.environment(World.Environment.NETHER);
        wc.type(WorldType.NORMAL);
        underworld = wc.createWorld();
        if (underworld == null) {
            Bukkit.getLogger().severe("[UnderworldEvent] 마계 월드 생성 실패!");
            isEventActive = false;
            return;
        }
        underworld.setGameRule(GameRule.DO_MOB_SPAWNING, true);

        WorldBorder border = underworld.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(BORDER_INITIAL_SIZE);

        Bukkit.broadcastMessage("§5[마계의 주인] §c마계의 문이 열렸습니다! 지옥으로 입장하여 마계의 주인에 도전하세요!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }

        this.eventTask = new BukkitRunnable() {
            private long ticks = 0;
            private boolean borderShrinking = false;

            @Override
            public void run() {
                if (!isEventActive) {
                    this.cancel();
                    return;
                }

                if (!borderShrinking && ticks >= BORDER_SHRINK_DELAY_SECONDS * 20) {
                    border.setSize(BORDER_FINAL_SIZE, BORDER_SHRINK_DURATION_SECONDS);
                    Bukkit.broadcastMessage("§5[마계의 주인] §c마계의 경계가 줄어들기 시작합니다!");
                    borderShrinking = true;
                }

                if (boss != null && !boss.isDead()) {
                    Location bossLoc = boss.getLocation();
                    if (Math.abs(bossLoc.getX()) > BOSS_AREA_RADIUS || Math.abs(bossLoc.getZ()) > BOSS_AREA_RADIUS) {
                        Vector direction = new Vector(0, bossLoc.getY(), 0).subtract(bossLoc.toVector()).normalize();
                        boss.setVelocity(boss.getVelocity().add(direction.multiply(0.2)));
                    }
                    updateHitboxPositions();
                }
                ticks++;
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 1L);
    }

    public void endEvent(String reason) {
        if (!isEventActive) return;

        if (eventTask != null && !eventTask.isCancelled()) {
            eventTask.cancel();
        }

        Bukkit.broadcastMessage("§5[마계의 주인] §c" + reason + " 마계의 문이 닫힙니다.");

        if (underworld != null) {
            for (Player player : underworld.getPlayers()) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                player.sendMessage("§c마계와의 연결이 끊어져 메인 월드로 강제 이동됩니다.");
            }
            for (Wither wither : underworld.getEntitiesByClass(Wither.class)) {
                wither.remove();
            }
            
            File worldFolder = underworld.getWorldFolder();
            Bukkit.unloadWorld(underworld, false);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    deleteWorldFolder(worldFolder);
                }
            }.runTaskAsynchronously(DF_Main.getInstance());
        }

        isEventActive = false;
        boss = null;
        underworld = null;
        eventTask = null;
    }

    public void spawnBoss() {
        if (underworld == null || this.boss != null) return;
        Location spawnLocation = new Location(underworld, 0, 100, 0);

        boss = underworld.spawn(spawnLocation, Wither.class, wither -> {
            wither.setCustomName("§5마계의 주인");
            wither.setCustomNameVisible(true);
            wither.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024.0);
            wither.setHealth(1024.0);
            wither.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(30);
            wither.getAttribute(Attribute.SCALE).setBaseValue(4.0);
            wither.setRemoveWhenFarAway(false);
            wither.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        });

        spawnHitboxEntities();
    }

    private void spawnHitboxEntities() {
        for (int i = 0; i < 5; i++) {
            ArmorStand hitbox = underworld.spawn(boss.getLocation(), ArmorStand.class, as -> {
                as.setMarker(true);
                as.setInvisible(true);
                as.setInvulnerable(true);
                as.setGravity(false);
            });
            hitboxEntities.add(hitbox);
        }
    }

    private void updateHitboxPositions() {
        if (boss == null || boss.isDead()) return;

        Location base = boss.getLocation();
        Vector direction = base.getDirection().setY(0).normalize();
        Vector cross = direction.clone().crossProduct(new Vector(0, 1, 0));

        hitboxEntities.get(0).teleport(base.clone().add(0, 5.5, 0));
        hitboxEntities.get(1).teleport(base.clone().add(cross.clone().multiply(2.5)).add(0, 3.5, 0));
        hitboxEntities.get(2).teleport(base.clone().add(cross.clone().multiply(-2.5)).add(0, 3.5, 0));
        hitboxEntities.get(3).teleport(base.clone().add(direction.clone().multiply(2)).add(0, 4, 0));
        hitboxEntities.get(4).teleport(base.clone().add(direction.clone().multiply(-2)).add(0, 4, 0));
    }
    
    private void deleteWorldFolder(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteWorldFolder(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        path.delete();
    }

    public boolean isEventActive() { return isEventActive; }
    public World getUnderworld() { return underworld; }
    public Wither getBoss() { return boss; }
    public List<Entity> getHitboxEntities() { return hitboxEntities; }
    public void addEnteredPlayer(Player player) { enteredPlayers.add(player.getUniqueId()); }
    public boolean hasPlayersEntered() { return !enteredPlayers.isEmpty(); }
}