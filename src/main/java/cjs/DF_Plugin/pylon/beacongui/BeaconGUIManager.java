package cjs.DF_Plugin.pylon.beacongui;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.item.ItemBuilder;
import cjs.DF_Plugin.pylon.beacongui.giftbox.GiftBoxManager;
import cjs.DF_Plugin.pylon.beacongui.recruit.RecruitGuiManager;
import cjs.DF_Plugin.pylon.beacongui.resurrect.ResurrectGuiManager;
import cjs.DF_Plugin.pylon.beacongui.shop.PylonShopManager;
import cjs.DF_Plugin.pylon.clan.Clan;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class BeaconGUIManager {

    private final DF_Main plugin;
    private final RecruitGuiManager recruitGuiManager;
    private final ResurrectGuiManager resurrectGuiManager;
    private final GiftBoxManager giftBoxManager;
    private final PylonShopManager shopManager;
    public static final NamespacedKey GUI_BUTTON_KEY = new NamespacedKey(DF_Main.getInstance(), "df_gui_button");

    public BeaconGUIManager(DF_Main plugin) {
        this.plugin = plugin;
        this.recruitGuiManager = new RecruitGuiManager(plugin);
        this.resurrectGuiManager = new ResurrectGuiManager(plugin);
        this.giftBoxManager = plugin.getGiftBoxManager();
        this.shopManager = new PylonShopManager(plugin);
    }

    public static String getMainMenuTitle(Clan clan) {
        return clan.getColor() + "[" + clan.getName() + " 가문]";
    }

    /**
     * 파일런 메인 메뉴 GUI를 엽니다.
     */
    public void openMainMenu(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§c[파일런] §c가문 정보를 찾을 수 없습니다.");
            return;
        }

        String title = getMainMenuTitle(clan);
        Inventory gui = Bukkit.createInventory(null, 9, title);

        // 아이템 생성 및 배치
        gui.setItem(0, createGuiItem(Material.ENDER_CHEST, "§f파일런 창고", "pylon_storage", "§7가문 공유 창고를 엽니다."));
        gui.setItem(2, createGuiItem(Material.BEACON, "§b파일런 상점", "shop", "§7다양한 아이템을 구매합니다."));
        gui.setItem(4, createGuiItem(Material.DIAMOND, "§6팀원 뽑기", "recruit", "§7새로운 팀원을 영입합니다."));
        gui.setItem(6, createGuiItem(Material.TOTEM_OF_UNDYING, "§a팀원 부활", "resurrect", "§7사망한 팀원을 부활시킵니다."));

        gui.setItem(8, createGiftBoxItem(clan));

        player.openInventory(gui);
    }

    /**
     * GUI에 사용될 아이템을 생성하는 헬퍼 메소드
     */
    private ItemStack createGuiItem(Material material, String name, String action, String... lore) {
        return new ItemBuilder(material)
                .withName(name)
                .withLore(lore)
                .withPDCString(GUI_BUTTON_KEY, action)
                .build();
    }

    ItemStack createGiftBoxItem(Clan clan) {
        long cooldownMillis = plugin.getGameConfigManager().getConfig().getLong("pylon.giftbox.cooldown-minutes", 5) * 60 * 1000;
        long lastUsed = clan.getLastGiftBoxTime();

        ItemBuilder builder = new ItemBuilder(Material.CHEST)
                .withName("§d선물상자")
                .withPDCString(GUI_BUTTON_KEY, "giftbox");

        if (lastUsed == 0) {
            // 보충 작업이 아직 실행되지 않은 초기 상태
            builder.addLoreLine("§7다음 선물 보충까지: §e--:--");
        } else {
            long nextRefillTime = lastUsed + cooldownMillis;
            long remainingMillis = Math.max(0, nextRefillTime - System.currentTimeMillis());
            String timeFormatted = String.format("%02d:%02d", (remainingMillis / 1000) / 60, (remainingMillis / 1000) % 60);
            builder.addLoreLine("§7다음 선물 보충까지:");
            builder.addLoreLine("§e" + timeFormatted);
        }

        builder.addLoreLine("§7클릭하여 내용물을 확인하세요.");
        return builder.build();
    }


    public void handleMenuClick(Player player, String action) {
        GameConfigManager configManager = plugin.getGameConfigManager();

        switch (action) {
            case "pylon_storage":
                if (!configManager.getConfig().getBoolean("pylon.features.storage", true)) {
                    player.sendMessage("§c[파일런] §c파일런 창고 기능이 비활성화되어 있습니다.");
                    player.closeInventory();
                    return;
                }
                plugin.getClanManager().openPylonStorage(player);
                break;
            case "clan_nether":
                if (!configManager.getConfig().getBoolean("pylon.features.clan-nether", true)) {
                    player.sendMessage("§c[파일런] §c클랜 지옥 기능이 비활성화되어 있습니다.");
                    player.closeInventory();
                    return;
                }
                player.sendMessage("§a[클랜 지옥 안내] §7파일런 범위 내에 지옥문을 건설하고 이용하면 가문 전용 지옥으로 입장합니다.");
                player.closeInventory();
                break;
            case "resurrect":
                resurrectGuiManager.openResurrectionGui(player);
                break;
            case "recruit":
                recruitGuiManager.startRecruitmentProcess(player);
                break;
            case "giftbox":
                giftBoxManager.openGiftBox(player);
                break;
            case "recon_firework":
                plugin.getPylonManager().getReconManager().activateRecon(player);
                break;
            case "shop":
                shopManager.openShopGui(player);
                break;
        }
    }

    public RecruitGuiManager getRecruitGuiManager() { return recruitGuiManager; }
    public ResurrectGuiManager getResurrectGuiManager() { return resurrectGuiManager; }
    public GiftBoxManager getGiftBoxManager() { return giftBoxManager; }
    public PylonShopManager getShopManager() {
        return shopManager;
    }
}