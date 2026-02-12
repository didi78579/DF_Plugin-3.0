package cjs.DF_Plugin.pylon.beaconinteraction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;

public class PylonStorageListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        // 인벤토리 제목으로 파일런 창고인지 확인합니다.
        if (view.getTitle().contains("§f의 파일런 창고")) {
            // 클릭된 슬롯이 최상단 인벤토리의 54번째 슬롯(인덱스 53)인지 확인합니다.
            if (event.getRawSlot() == 53) {
                // 항상 클릭 이벤트를 취소하여 시스템 슬롯을 보호합니다.
                event.setCancelled(true);

                ItemStack clickedItem = event.getCurrentItem();
                // 클릭된 아이템이 '균열의 나침반'인지 확인합니다.
                if (clickedItem != null && clickedItem.getType() == Material.COMPASS && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().equals("§d균열의 나침반")) {
                    Player player = (Player) event.getWhoClicked();

                    // 플레이어에게 지급할 나침반을 복제하고, 설명(lore)을 수정합니다.
                    ItemStack compassToGive = clickedItem.clone();
                    ItemMeta newMeta = compassToGive.getItemMeta();
                    newMeta.setLore(Collections.singletonList("§7차원의 균열 위치를 가리킵니다."));
                    compassToGive.setItemMeta(newMeta);

                    // 인벤토리가 가득 찼으면 바닥에 드롭하고, 아니면 인벤토리에 추가합니다.
                    if (!player.getInventory().addItem(compassToGive).isEmpty()) {
                        player.getWorld().dropItem(player.getLocation(), compassToGive);
                        player.sendMessage("§d[차원의 균열] §f인벤토리가 가득 차서 균열의 나침반을 바닥에 드롭했습니다.");
                    } else {
                        player.sendMessage("§d[차원의 균열] §f균열의 나침반을 획득했습니다.");
                    }
                }
            }
        }
    }
}