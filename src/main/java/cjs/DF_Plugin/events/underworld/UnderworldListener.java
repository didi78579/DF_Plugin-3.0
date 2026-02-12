package cjs.DF_Plugin.events.underworld;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class UnderworldListener implements Listener {

    private final UnderworldEventManager manager = UnderworldEventManager.getInstance();
    private final Random random = new Random();
    private static final List<EntityType> ALLOWED_SPAWNS = List.of(EntityType.BLAZE, EntityType.PIGLIN_BRUTE, EntityType.WITHER_SKELETON);

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!manager.isEventActive()) return;

        Player player = event.getPlayer();
        if (event.getTo().getWorld().getEnvironment() == World.Environment.NETHER) {
            event.setCancelled(true);

            Iterator<KeyedBossBar> bossBarIterator = Bukkit.getBossBars();
            while (bossBarIterator.hasNext()) {
                BossBar bossBar = bossBarIterator.next();
                if (bossBar.getPlayers().contains(player)) {
                    bossBar.removePlayer(player);
                }
            }

            if (!manager.hasPlayersEntered()) {
                manager.spawnBoss();
            }
            manager.addEnteredPlayer(player);

            World underworld = manager.getUnderworld();
            Location randomLoc = findRandomSafeLocation(underworld);
            
            event.setTo(randomLoc);
            event.setCanCreatePortal(true);

            player.teleport(randomLoc);
            player.sendMessage("§5마계의 기운에 이끌려 미지의 공간으로 빨려 들어갑니다...");
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getLocation().getWorld() != manager.getUnderworld()) return;

        if (event.getEntityType() == EntityType.WITHER && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }

        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !ALLOWED_SPAWNS.contains(event.getEntityType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeathInUnderworld(PlayerDeathEvent event) {
        if (event.getPlayer().getWorld() != manager.getUnderworld()) return;

        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            killer.getInventory().addItem(CustomItemFactory.createDemonSoul());
            killer.sendMessage("§c다른 플레이어를 처치하여 §4악마의 영혼§c을 1개 획득했습니다.");
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (manager.getBoss() != null && event.getEntity().equals(manager.getBoss())) {
            event.getDrops().clear();
            event.getDrops().add(CustomItemFactory.createAuxiliaryPylonCore());
            manager.endEvent("마계의 주인이 처치되어");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!manager.isEventActive() || manager.getBoss() == null || manager.getBoss().isDead()) {
            return;
        }

        Entity damaged = event.getEntity();
        Entity damager = event.getDamager();

        if (damager instanceof Player attacker && attacker.hasMetadata("df_is_riptiding")) {
            if (damaged.equals(manager.getBoss()) || manager.getHitboxEntities().contains(damaged)) {
                event.setCancelled(true);
                return;
            }
        }

        if (manager.getHitboxEntities().contains(damaged)) {
            event.setCancelled(true);
            manager.getBoss().damage(event.getDamage() / 10.0, damager);
            return;
        }
        
        if (damaged.equals(manager.getBoss())) {
            event.setDamage(event.getDamage() / 10.0);
        }
    }

    private Location findRandomSafeLocation(World world) {
        int attempts = 0;
        while (attempts < 100) {
            int x = random.nextInt(400) - 200;
            int z = random.nextInt(400) - 200;

            if (Math.abs(x) < 50 && Math.abs(z) < 50) {
                attempts++;
                continue;
            }

            int y = world.getHighestBlockYAt(x, z);
            if (y > 5 && y < 120) {
                Location loc = new Location(world, x, y + 1, z);
                if (loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable()) {
                    return loc;
                }
            }
            attempts++;
        }
        return new Location(world, 150, 100, 150);
    }
}