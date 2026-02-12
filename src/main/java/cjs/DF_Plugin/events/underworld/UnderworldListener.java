package cjs.DF_Plugin.event.underworld;

import cjs.DF_Plugin.event.item.CustomItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;

public class UnderworldListener implements Listener {

    private final UnderworldEventManager manager = UnderworldEventManager.getInstance();

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!manager.isEventActive()) return;

        Player player = event.getPlayer();
        if (event.getTo().getWorld().getEnvironment() == World.Environment.NETHER) {
            if (manager.hasEntered(player)) {
                player.sendMessage("§c이미 마계에 입장한 기록이 있어 더 이상 들어갈 수 없습니다.");
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true); // 기본 포탈 이동 취소
            Location underworldSpawn = manager.getUnderworld().getSpawnLocation();
            player.teleport(underworldSpawn);
            player.sendMessage("§5마계의 기운에 이끌려 미지의 공간으로 빨려 들어갑니다...");
            manager.addPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerDeathInUnderworld(PlayerDeathEvent event) {
        if (!manager.isEventActive()) return;

        Player victim = event.getEntity();
        if (victim.getWorld() != manager.getUnderworld()) return;

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            killer.getInventory().addItem(CustomItems.DEMON_SOUL.getItem());
            killer.sendMessage("§c다른 플레이어를 처치하여 §4악마의 영혼§c을 1개 획득했습니다.");
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (!manager.isEventActive()) return;

        if (event.getEntity() == manager.getBoss()) {
            // 보상 지급
            event.getDrops().clear(); // 기본 드랍템 제거
            event.getDrops().add(CustomItems.AUXILIARY_PYLON_CORE.getItem());

            // 이벤트 종료
            manager.endEvent("마계의 주인이 처치되어");
        }
    }

    @EventHandler
    public void onWitherSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.WITHER &&
            event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BUILD_WITHER) {
            
            // 이벤트 기간 중에는 플레이어가 위더를 소환할 수 없음
            if (manager.isEventActive()) {
                event.setCancelled(true);
                // 주변 플레이어에게 알림
                event.getLocation().getNearbyPlayers(30).forEach(p ->
                    p.sendMessage("§c불길한 기운이 당신의 소환 의식을 방해합니다. 지금은 위더를 소환할 수 없습니다.")
                );
            }
        }
    }
}