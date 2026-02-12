package cjs.DF_Plugin.pylon.item;

import cjs.DF_Plugin.util.item.PylonItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ReturnScrollListener implements Listener {

    private final ReturnScrollManager scrollManager;

    public ReturnScrollListener(ReturnScrollManager scrollManager) {
        this.scrollManager = scrollManager;
    }

    @EventHandler
    public void onReturnScrollUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // 우클릭 액션일 때만 반응합니다.
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            // 주 손에 들고 있는 아이템이 귀환 주문서인지 확인합니다.
            if (PylonItemFactory.isReturnScroll(itemInHand)) {
                event.setCancelled(true); // 블록 설치 등 기본 동작을 막습니다.

                // 이미 시전 중이면 중복 실행을 막습니다.
                if (scrollManager.isCasting(player)) return;

                scrollManager.startCasting(player);
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player) {
            if (scrollManager.isCasting(victim)) {
                scrollManager.cancelCasting(victim, true);
            }
        }
    }
}