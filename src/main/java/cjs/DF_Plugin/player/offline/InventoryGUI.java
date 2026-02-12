package cjs.DF_Plugin.player.offline;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class InventoryGUI {

    public static final ItemStack FILLER_PANE = createFillerPane();

    public static Inventory create(OfflineInventory offlineInventory) {
        ItemMeta headMeta = offlineInventory.getPlayerHead().getItemMeta();
        String displayName = (headMeta != null && headMeta.hasDisplayName())
                ? headMeta.getDisplayName()
                : "오프라인 플레이어";
        Inventory gui = Bukkit.createInventory(null, 54, "§8" + displayName);

        // GUI를 유리판으로 채웁니다.
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, FILLER_PANE);
        }

        // 아이템 배치
        gui.setItem(0, offlineInventory.getPlayerHead());
        ItemStack[] armor = offlineInventory.getArmor();
        gui.setItem(2, armor.length > 3 ? armor[3] : null); // Helmet
        gui.setItem(3, armor.length > 2 ? armor[2] : null); // Chestplate
        gui.setItem(4, armor.length > 1 ? armor[1] : null); // Leggings
        gui.setItem(5, armor.length > 0 ? armor[0] : null); // Boots
        gui.setItem(7, offlineInventory.getOffHand());

        // 메인 인벤토리 (핫바 포함)
        ItemStack[] mainContents = offlineInventory.getMain();
        for (int i = 0; i < 27; i++) { // 9-35번 슬롯 -> GUI 18-44번 슬롯 (3-5번째 줄)
            gui.setItem(i + 18, mainContents[i + 9]);
        }
        for (int i = 0; i < 9; i++) { // 0-8번 슬롯 (핫바) -> GUI 45-53번 슬롯 (6번째 줄)
            gui.setItem(i + 45, mainContents[i]);
        }

        return gui;
    }

    public static OfflineInventory fromGui(Inventory gui) {
        ItemStack[] main = new ItemStack[36];
        for (int i = 0; i < 27; i++) {
            main[i + 9] = gui.getItem(i + 18);
        }
        for (int i = 0; i < 9; i++) { main[i] = gui.getItem(i + 45); }

        ItemStack[] armor = new ItemStack[4];
        armor[3] = gui.getItem(2); armor[2] = gui.getItem(3);
        armor[1] = gui.getItem(4); armor[0] = gui.getItem(5);

        return new OfflineInventory(main, armor, gui.getItem(7), gui.getItem(0));
    }

    private static ItemStack createFillerPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}