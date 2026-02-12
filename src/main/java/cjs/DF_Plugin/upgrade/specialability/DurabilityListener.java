package cjs.DF_Plugin.upgrade.specialability;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class DurabilityListener implements Listener {

    private final UpgradeManager upgradeManager;

    public DurabilityListener(DF_Main plugin) {
        this.upgradeManager = plugin.getUpgradeManager();
    }

    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();

        // 강화 가능한 아이템인지 확인 (무기, 도구, 갑옷 등)
        if (upgradeManager.getProfileRegistry().getProfile(item.getType()) == null) {
            return;
        }

        int level = upgradeManager.getUpgradeLevel(item);

        // 강화 레벨이 10 이상이면 내구도 감소를 막습니다.
        if (level >= 10) {
            // 내구도 감소 이벤트를 취소하여 아이템이 손상되지 않도록 합니다.
            event.setCancelled(true);

            // 아이템이 시각적으로 손상된 상태라면, 즉시 수리하여 항상 새것처럼 보이게 합니다.
            if (item.getItemMeta() instanceof Damageable meta) {
                if (meta.hasDamage()) {
                    meta.setDamage(0);
                    item.setItemMeta(meta);
                }
            }
        }
    }
}