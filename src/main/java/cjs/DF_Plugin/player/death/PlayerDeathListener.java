package cjs.DF_Plugin.player.death;

import org.bukkit.GameRule;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class PlayerDeathListener implements Listener {

    // 다른 플러그인이 인벤토리를 조작하기 전에 실행되도록 우선순위를 높게 설정
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Boolean keepInventory = player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY);

        // keepInventory가 true일 때만 소실 저주를 수동으로 처리
        if (keepInventory != null && keepInventory) {
            PlayerInventory inventory = player.getInventory();

            // 1. 주 인벤토리 (핫바 포함) 확인
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) { // 인덱스로 접근하여 직접 수정
                ItemStack item = contents[i];
                if (item != null && item.hasItemMeta() && item.getEnchantments().containsKey(Enchantment.VANISHING_CURSE)) {
                    inventory.setItem(i, null);
                }
            }

            // 2. 갑옷 슬롯 확인
            ItemStack[] armorContents = inventory.getArmorContents();
            for (int i = 0; i < armorContents.length; i++) {
                ItemStack item = armorContents[i];
                if (item != null && item.hasItemMeta() && item.getEnchantments().containsKey(Enchantment.VANISHING_CURSE)) {
                    armorContents[i] = null; // 복사된 배열을 수정
                }
            }
            inventory.setArmorContents(armorContents); // 수정된 배열을 다시 적용

            // 3. 왼손(오프핸드) 슬롯 확인
            ItemStack offHandItem = inventory.getItemInOffHand();
            if (offHandItem != null && offHandItem.hasItemMeta() && offHandItem.getEnchantments().containsKey(Enchantment.VANISHING_CURSE)) {
                inventory.setItemInOffHand(null);
            }
        }
    }
}