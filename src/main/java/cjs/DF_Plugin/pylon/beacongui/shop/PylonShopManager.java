package cjs.DF_Plugin.pylon.beacongui.shop;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.item.ItemBuilder;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.item.UpgradeItems;
import cjs.DF_Plugin.util.InventoryUtils;
import cjs.DF_Plugin.world.enchant.MagicStone;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.TimeUnit;

public class PylonShopManager {

    public static final String SHOP_GUI_TITLE = "§6[파일런 상점]";
    private final DF_Main plugin;

    private static final NamespacedKey SHOP_ITEM_KEY = new NamespacedKey(DF_Main.getInstance(), "shop_item_id");
    private static final NamespacedKey SHOP_COST_KEY = new NamespacedKey(DF_Main.getInstance(), "shop_item_cost");

    public PylonShopManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void openShopGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, SHOP_GUI_TITLE);
        FileConfiguration config = plugin.getGameConfigManager().getConfig();

        // 슬롯 0: 정찰용 폭죽 구매
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan != null) {
            long reconCooldownMillis = TimeUnit.HOURS.toMillis(config.getInt("pylon.recon-firework.cooldown-hours", 12));
            long timeSinceLastRecon = System.currentTimeMillis() - clan.getLastReconFireworkTime();
            int reconCost = config.getInt("pylon.shop.recon-firework.cost-level", 50);

            // 정찰용 폭죽 아이템을 생성하고 커스터마이징합니다.
            ItemStack reconFirework = new ItemStack(Material.FIREWORK_ROCKET);
            FireworkMeta fwm = (FireworkMeta) reconFirework.getItemMeta();
            fwm.setPower(0); // '체공 시간' 로어를 제거합니다.
            fwm.addEnchant(Enchantment.LURE, 1, true); // 반짝이는 효과를 위해 인챈트를 추가합니다.
            fwm.addItemFlags(ItemFlag.HIDE_ENCHANTS); // 인챈트 정보를 숨깁니다.
            reconFirework.setItemMeta(fwm);

            ItemBuilder reconBuilder = new ItemBuilder(reconFirework)
                    .withName("§b정찰용 폭죽 구매")
                    .withLore(
                            "§7하늘 높이 날아올라 주변을 정찰합니다.",
                            "",
                            "§f가격: §a" + reconCost + " 레벨"
                    );
            if (timeSinceLastRecon >= reconCooldownMillis) {
                reconBuilder.addLoreLine("§a구매 가능!");
            } else {
                long remainingMillis = reconCooldownMillis - timeSinceLastRecon;
                String remainingTime = String.format("%02d시간 %02d분",
                        TimeUnit.MILLISECONDS.toHours(remainingMillis),
                        TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60);
                reconBuilder.addLoreLine("§c다음 구매까지: " + remainingTime);
            }
            reconBuilder.withPDCString(SHOP_ITEM_KEY, "recon_firework");
            reconBuilder.withPDCInt(SHOP_COST_KEY, reconCost);
            gui.setItem(0, reconBuilder.build());
        }

        // 슬롯 2: 귀환 주문서
        int scrollCostLevel = config.getInt("pylon.shop.return-scroll.cost-level", 30);
        gui.setItem(2, new ItemBuilder(CustomItemFactory.createReturnScroll())
                .addLoreLine("")
                .addLoreLine("§f가격: §a" + scrollCostLevel + " 레벨")
                .withPDCString(SHOP_ITEM_KEY, "return_scroll")
                .withPDCInt(SHOP_COST_KEY, scrollCostLevel)
                .build());

        // 슬롯 4: 마석 교환
        int enchantScrollCost = config.getInt("pylon.shop.magic-stone-exchange.cost-level", 40);
        int enchantScrollGained = config.getInt("pylon.shop.magic-stone-exchange.gained", 128);
        gui.setItem(4, new ItemBuilder(Material.NETHER_STAR)
                .withName("§6마석 교환 §7[" + enchantScrollGained + "개]")
                .withLore(
                        "§7자신의 경험치를 사용해 마석을 얻습니다.",
                        "",
                        "§f가격: §a" + enchantScrollCost + " 레벨"
                )
                .withPDCString(SHOP_ITEM_KEY, "magic_stone")
                .withPDCInt(SHOP_COST_KEY, enchantScrollCost)
                .build());

        // 슬롯 6: 강화석 교환
        int upgradeStoneCost = config.getInt("pylon.shop.upgrade-stone-exchange.cost-level", 40);
        int upgradeStoneGained = config.getInt("pylon.shop.upgrade-stone-exchange.gained", 128);
        gui.setItem(6, new ItemBuilder(Material.ECHO_SHARD)
                .withName("§b강화석 교환 §7[" + upgradeStoneGained + "개]")
                .withLore(
                        "§7자신의 경험치를 사용해 강화석을 얻습니다.",
                        "",
                        "§f가격: §a" + upgradeStoneCost + " 레벨"
                )
                .withPDCString(SHOP_ITEM_KEY, "upgrade_stone")
                .withPDCInt(SHOP_COST_KEY, upgradeStoneCost)
                .build());

        // 슬롯 8: 보조 파일런 코어
        int auxCoreCostLevel = config.getInt("pylon.shop.aux-core.cost-level", 100);
        ItemStack auxCore = CustomItemFactory.createAuxiliaryPylonCore();

        gui.setItem(8, new ItemBuilder(auxCore)
                .withName("§d보조 파일런 코어")
                .withLore(
                        "§7주 파일런을 보조하는 코어를 구매합니다.",
                        "",
                        "§f가격: §a" + auxCoreCostLevel + " 레벨"
                )
                .withPDCString(SHOP_ITEM_KEY, "aux_core")
                .withPDCInt(SHOP_COST_KEY, auxCoreCostLevel)
                .build());

        player.openInventory(gui);
    }

    public void handleGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        event.setCancelled(true);

        String itemId = clickedItem.getItemMeta().getPersistentDataContainer().get(SHOP_ITEM_KEY, PersistentDataType.STRING);
        if (itemId == null) return;

        switch (itemId) {
            case "recon_firework":
                int reconCost = clickedItem.getItemMeta().getPersistentDataContainer().getOrDefault(SHOP_COST_KEY, PersistentDataType.INTEGER, 50);
                handleBuyRecon(player, reconCost);
                break;
            case "aux_core":
                int coreCostLevel = clickedItem.getItemMeta().getPersistentDataContainer().getOrDefault(SHOP_COST_KEY, PersistentDataType.INTEGER, 100);
                handleBuyAuxCore(player, coreCostLevel);
                break;
            case "return_scroll":
                int scrollCostLevel = clickedItem.getItemMeta().getPersistentDataContainer().getOrDefault(SHOP_COST_KEY, PersistentDataType.INTEGER, 30);
                handleExchangeWithLevels(player, scrollCostLevel, CustomItemFactory.createReturnScroll(), "귀환 주문서");
                break;
            case "magic_stone":
                int enchantScrollCost = clickedItem.getItemMeta().getPersistentDataContainer().getOrDefault(SHOP_COST_KEY, PersistentDataType.INTEGER, 30);
                int enchantScrollGained = plugin.getGameConfigManager().getConfig().getInt("pylon.shop.magic-stone-exchange.gained", 128);
                handleExchangeWithLevels(player, enchantScrollCost, MagicStone.createMagicStone(enchantScrollGained), "마석");
                break;
            case "upgrade_stone":
                int upgradeStoneCost = clickedItem.getItemMeta().getPersistentDataContainer().getOrDefault(SHOP_COST_KEY, PersistentDataType.INTEGER, 40);
                int upgradeStoneGained = plugin.getGameConfigManager().getConfig().getInt("pylon.shop.upgrade-stone-exchange.gained", 128);
                handleExchangeWithLevels(player, upgradeStoneCost, UpgradeItems.createUpgradeStone(upgradeStoneGained), "강화석");
                break;
        }
    }

    private void handleExchangeWithLevels(Player player, int requiredLevels, ItemStack reward, String rewardName) {
        if (player.getLevel() >= requiredLevels) {
            player.setLevel(player.getLevel() - requiredLevels);
            InventoryUtils.giveOrDropItems(player, reward);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void handleBuyAuxCore(Player player, int cost) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c보조 파일런 코어는 가문 대표만 구매할 수 있습니다.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 가문 대표 확인 후, 공통 교환 로직을 호출합니다.
        handleExchangeWithLevels(player, cost, CustomItemFactory.createAuxiliaryPylonCore(), "보조 파일런 코어");
    }

    private void handleBuyRecon(Player player, int cost) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c가문 대표만 정찰용 폭죽을 구매할 수 있습니다.");
            return;
        }

        long reconCooldownMillis = TimeUnit.HOURS.toMillis(plugin.getGameConfigManager().getConfig().getInt("pylon.recon-firework.cooldown-hours", 12));
        if (System.currentTimeMillis() - clan.getLastReconFireworkTime() < reconCooldownMillis) {
            player.sendMessage("§c아직 정찰용 폭죽을 구매할 수 없습니다.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (player.getLevel() < cost) {
            player.sendMessage("§c레벨이 부족합니다. (필요: " + cost + ")");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && !chestplate.getType().isAir()) {
            player.sendMessage("§c겉날개를 장착하려면 갑옷 칸을 비워야 합니다.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        player.setLevel(player.getLevel() - cost);
        plugin.getReconManager().activateRecon(player);
    }
}