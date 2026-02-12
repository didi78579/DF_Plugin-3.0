package cjs.DF_Plugin.upgrade.profile.passive;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class SpearPassiveListener implements Listener {

    private final DF_Main plugin;

    public SpearPassiveListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 플레이어 퇴장 시 필요한 정리 작업은 SpecialAbilityManager의 onPlayerQuit에서 처리됩니다.
        // 추가적으로 창(Trident) 관련 정리가 필요하다면 여기에 작성할 수 있습니다.
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // 모든 종류의 창(_SPEAR로 끝나는 아이템)을 버리면 돌진 상태를 해제합니다.
        if (droppedItem.getType().name().endsWith("_SPEAR")) {
            SpecialAbilityManager specialAbilityManager = plugin.getSpecialAbilityManager();
            specialAbilityManager.setPlayerUsingAbility(player, "lunge", false);
        }
    }
}