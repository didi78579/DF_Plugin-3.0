package cjs.DF_Plugin.world.enchant;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class EnchantListener implements Listener {

    private final DF_Main plugin;
    private final EnchantManager enchantManager;
    public static final String GUI_TITLE = "§5마법 부여";
    public static final int ITEM_SLOT = 4;
    private static final String PREFIX = "§5[마법 부여] §f";

    public EnchantListener(DF_Main plugin) {
        this.plugin = plugin;
        this.enchantManager = plugin.getEnchantManager();
    }

    @EventHandler
    public void onEnchantTableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        event.setCancelled(true);
        openEnchantGUI(event.getPlayer());
    }

    private void openEnchantGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, GUI_TITLE);

        // 배경 아이템 설정
        ItemStack filler = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < gui.getSize(); i++) {
            if (i != ITEM_SLOT) {
                gui.setItem(i, filler);
            }
        }

        // 중앙 슬롯 플레이스홀더
        gui.setItem(ITEM_SLOT, createPlaceholder());

        player.openInventory(gui);
    }

    private ItemStack createPlaceholder() {
        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d마법 부여");
            meta.setLore(Arrays.asList("§7이곳에 아이템을 올려두고", "§7좌클릭하여 마법을 부여하세요."));
            placeholder.setItemMeta(meta);
        }
        return placeholder;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        event.setCancelled(true); // GUI 내 모든 기본 상호작용을 막습니다.

        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory == null) return;

        if (clickedInventory.equals(topInventory)) {
            handleGuiClick(event, player, topInventory);
        } else {
            handlePlayerInventoryClick(event, player, topInventory);
        }
    }

    private void handleGuiClick(InventoryClickEvent event, Player player, Inventory gui) {
        if (event.getSlot() != ITEM_SLOT) return; // 중앙 슬롯 외 클릭은 무시

        ItemStack cursorItem = event.getCursor();
        ItemStack itemInSlot = gui.getItem(ITEM_SLOT);

        // 커서에 아이템이 있는 경우: 아이템 놓기
        if (cursorItem != null && cursorItem.getType() != Material.AIR) {
            if (isPlaceholder(itemInSlot)) {
                gui.setItem(ITEM_SLOT, cursorItem.clone());
                player.setItemOnCursor(null);
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.8f);
            }
            return;
        }

        // 슬롯에 아이템이 있는 경우: 인챈트 또는 아이템 빼기
        if (!isPlaceholder(itemInSlot)) {
            if (event.isLeftClick()) { // 좌클릭: 인챈트 시도
                boolean success = enchantManager.attemptEnchant(player, itemInSlot);
                if (success) {
                    // 인챈트에 성공하면 itemInSlot이 이미 업데이트되었으므로, GUI 슬롯을 비우지 않고 그대로 둡니다.
                }
            } else if (event.isRightClick()) { // 우클릭: 아이템 빼기
                InventoryUtils.giveOrDropItems(player, itemInSlot);
                gui.setItem(ITEM_SLOT, createPlaceholder());
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            }
        }
    }

    private void handlePlayerInventoryClick(InventoryClickEvent event, Player player, Inventory gui) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (isPlaceholder(gui.getItem(ITEM_SLOT))) {
            gui.setItem(ITEM_SLOT, clickedItem.clone());
            event.setCurrentItem(null);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.8f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        ItemStack item = event.getInventory().getItem(ITEM_SLOT);
        if (!isPlaceholder(item)) {
            InventoryUtils.giveOrDropItems((Player) event.getPlayer(), item);
        }
    }

    private boolean isPlaceholder(ItemStack item) {
        // 아이템이 없거나, 플레이스홀더 아이템과 동일한 경우
        return item == null || item.getType() == Material.AIR || item.isSimilar(createPlaceholder());
    }
}