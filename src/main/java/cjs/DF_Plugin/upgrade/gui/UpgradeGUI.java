package cjs.DF_Plugin.upgrade.gui;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class UpgradeGUI {

    private final DF_Main plugin;
    public static final String GUI_TITLE = "§6장비 대장간";
    public static final int UPGRADE_ITEM_SLOT = 4; // 중앙

    public UpgradeGUI(DF_Main plugin) {
        this.plugin = plugin;


    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, GUI_TITLE);

        // 회색 색유리 아이템 생성
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }

        // 모든 칸을 필러 아이템으로 채우기
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, filler);
        }

        // 강화 모루 슬롯 초기화
        gui.setItem(UPGRADE_ITEM_SLOT, createAnvilPlaceholder());

        player.openInventory(gui);
    }

    public static ItemStack createAnvilPlaceholder() {
        ItemStack anvil = new ItemStack(Material.ANVIL);
        ItemMeta meta = anvil.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "강화 모루");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "이곳에 강화할 아이템을 올리세요."));
            anvil.setItemMeta(meta);
        }
        return anvil;
    }
}