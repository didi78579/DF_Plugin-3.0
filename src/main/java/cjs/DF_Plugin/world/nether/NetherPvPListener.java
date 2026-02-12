package cjs.DF_Plugin.world.nether;

import cjs.DF_Plugin.item.CustomItemFactory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * 네더 월드에서의 PvP 관련 이벤트를 처리하는 리스너입니다.
 */
public class NetherPvPListener implements Listener {

    /**
     * 플레이어가 네더 월드에서 다른 플레이어에게 처치되었을 때 '악마의 영혼'을 드롭합니다.
     * @param event PlayerDeathEvent
     */
    @EventHandler
    public void onPlayerDeathInNether(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        World world = victim.getWorld();

        // 1. 사망한 월드가 네더인지 확인합니다.
        if (world.getEnvironment() != World.Environment.NETHER) {
            return;
        }

        // 2. 다른 플레이어에 의해 사망했는지 확인합니다.
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            // 3. 사망 위치에 '악마의 영혼' 아이템을 1개 드롭합니다.
            world.dropItemNaturally(victim.getLocation(), CustomItemFactory.createDemonSoul());

        }
    }
}