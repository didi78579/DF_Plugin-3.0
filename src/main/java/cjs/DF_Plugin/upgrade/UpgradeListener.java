package cjs.DF_Plugin.upgrade;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.gui.UpgradeGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.Set;

public class UpgradeListener implements Listener {

    private final UpgradeManager upgradeManager;
    private final UpgradeGUI upgradeGUI;
    private static final String PREFIX = "§6[강화] §f";

    private static final Set<Material> ANVIL_TYPES = Set.of(
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL
    );

    public UpgradeListener(DF_Main plugin) {
        this.upgradeManager = plugin.getUpgradeManager();
        this.upgradeGUI = new UpgradeGUI();
    }

    @EventHandler
    public void onAnvilClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !ANVIL_TYPES.contains(clickedBlock.getType())) return;

        event.setCancelled(true);
        upgradeGUI.open(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(UpgradeGUI.GUI_TITLE)) {
            return;
        }

        event.setCancelled(true); // 기본 동작 방지

        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        int slot = event.getRawSlot();

        // GUI 내부 클릭만 처리
        if (slot < topInventory.getSize()) {
            // [가독성 개선] switch문 대신 if문으로 변경하여 코드를 간소화합니다.
            if (slot == UpgradeGUI.UPGRADE_ITEM_SLOT) {
                if (event.isLeftClick()) {
                    handleUpgrade(player, topInventory);
                } else if (event.isRightClick()) {
                    handleWithdraw(player, topInventory);
                }
            }
        }
        // 플레이어 인벤토리에서 아이템을 GUI로 옮기는 경우
        else if (event.getClickedInventory() instanceof PlayerInventory) {
            handlePlaceItem(event, player, topInventory);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(UpgradeGUI.GUI_TITLE)) return;

        ItemStack item = event.getInventory().getItem(UpgradeGUI.UPGRADE_ITEM_SLOT);
        // 플레이스홀더가 아닌 실제 아이템만 돌려줌
        if (item != null && !item.isSimilar(UpgradeGUI.createAnvilPlaceholder())) {
            giveOrDropItems((Player) event.getPlayer(), item);
        }
    }

    private void handleUpgrade(Player player, Inventory inventory) {
        ItemStack targetItem = inventory.getItem(UpgradeGUI.UPGRADE_ITEM_SLOT);

        if (targetItem == null || targetItem.isSimilar(UpgradeGUI.createAnvilPlaceholder())) {
            player.sendMessage(PREFIX + "§c강화할 아이템을 올려주세요.");
            return;
        }

        upgradeManager.attemptUpgrade(player, targetItem);

        if (targetItem.getAmount() == 0) {
            inventory.setItem(UpgradeGUI.UPGRADE_ITEM_SLOT, UpgradeGUI.createAnvilPlaceholder()); // 파괴된 경우 GUI 슬롯을 비움
        }
    }

    private void handleWithdraw(Player player, Inventory inventory) {
        ItemStack targetItem = inventory.getItem(UpgradeGUI.UPGRADE_ITEM_SLOT);

        if (targetItem != null && !targetItem.isSimilar(UpgradeGUI.createAnvilPlaceholder())) {
            giveOrDropItems(player, targetItem);
            inventory.setItem(UpgradeGUI.UPGRADE_ITEM_SLOT, UpgradeGUI.createAnvilPlaceholder());
        }
    }

    private void handlePlaceItem(InventoryClickEvent event, Player player, Inventory inventory) {
        final ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // [수정] 강화할 수 없는 아이템은 아예 넣지 못하게 막습니다.
        if (upgradeManager.getProfileRegistry().getProfile(clickedItem.getType()) == null) {
            player.sendMessage(PREFIX + "§c이 아이템은 강화할 수 없습니다.");
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
            return;
        }

        final ItemStack itemInSlot = inventory.getItem(UpgradeGUI.UPGRADE_ITEM_SLOT);

        // [로직 개선] 아이템을 교체하는 로직을 더 명확하게 수정합니다.
        inventory.setItem(UpgradeGUI.UPGRADE_ITEM_SLOT, clickedItem.clone());

        // 기존에 있던 아이템이 플레이스홀더가 아니었다면 플레이어 인벤토리로 돌려줍니다.
        if (itemInSlot != null && !itemInSlot.isSimilar(UpgradeGUI.createAnvilPlaceholder())) {
            player.getInventory().setItem(event.getSlot(), itemInSlot);
        } else {
            event.setCurrentItem(null); // 기존 슬롯이 비어있었다면, 클릭한 아이템을 제거합니다.
        }

        player.playSound(player.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 0.7f, 1.5f);
    }

    private void giveOrDropItems(Player player, ItemStack items) {
        Map<Integer, ItemStack> notAdded = player.getInventory().addItem(items);
        if (!notAdded.isEmpty()) {
            Location loc = player.getLocation();
            notAdded.values().forEach(item -> loc.getWorld().dropItem(loc, item));
            player.sendMessage(PREFIX + "§e인벤토리가 가득 차 아이템이 바닥에 드롭되었습니다.");
        }
    }
}