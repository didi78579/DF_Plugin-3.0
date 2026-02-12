package cjs.DF_Plugin.events.end;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class EndEventListener implements Listener {

    private final EndEventManager endEventManager;

    public EndEventListener(DF_Main plugin) {
        this.endEventManager = plugin.getEndEventManager();
    }

    @EventHandler
    public void onPlayerUseEndPortal(PlayerPortalEvent event) {
        // 엔드 포탈을 통한 이동만 처리합니다.
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return;
        }

        Player player = event.getPlayer();
        World fromWorld = event.getFrom().getWorld();

        // Case 1: 엔드 월드로 진입 (오버월드 또는 네더에서)
        if (fromWorld.getEnvironment() != World.Environment.THE_END) {
            if (!endEventManager.isEndOpen()) {
                event.setCancelled(true);
                player.sendMessage("§c[엔드 이벤트] §c엔드 포탈은 아직 굳게 닫혀 있습니다.");
                return;
            }

            // 기본 이동을 취소하고 수동으로 텔레포트
            event.setCancelled(true);
            World endWorld = Bukkit.getWorld("world_the_end");

            // 엔드 월드는 openEnd()에서 미리 생성되어 있어야 합니다.
            if (endWorld == null) {
                player.sendMessage("§c[엔드 이벤트] §c엔드 월드에 접근할 수 없습니다. 서버 관리자에게 문의하세요.");
                DF_Main.getInstance().getLogger().severe("[엔드 이벤트] Player " + player.getName() + " tried to enter The End, but the world is not loaded!");
                return;
            }

            double x = (Math.random() * 100) - 50;
            double z = (Math.random() * 100) - 50;
            double y = 250;
            Location randomLocation = new Location(endWorld, x, y, z);

            player.teleport(randomLocation);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30 * 20, 0, true, false));

            // 붕괴 중이라면 보스바에 플레이어를 추가합니다.
            endEventManager.addPlayerToCollapseBar(player);
        }
        // Case 2: 엔드 월드에서 퇴장
        else {
            event.setCancelled(true);
            endEventManager.removePlayerFromCollapseBar(player);
            endEventManager.teleportPlayerToSafety(player);
        }
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon) {

            endEventManager.triggerDragonDefeatSequence();
        }
    }
}