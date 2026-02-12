package cjs.DF_Plugin.pylon.beacongui;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.beacongui.recruit.RecruitGuiManager;
import cjs.DF_Plugin.pylon.beacongui.resurrect.ResurrectGuiManager;
import cjs.DF_Plugin.pylon.beacongui.shop.PylonShopManager;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BeaconGUIListener implements Listener {

    private final DF_Main plugin;
    private final BeaconGUIManager guiManager;
    private final Map<UUID, BukkitTask> guiUpdateTasks = new ConcurrentHashMap<>();

    public BeaconGUIListener(DF_Main plugin, BeaconGUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onPylonInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.BEACON) return;

        String clickedLocationStr = PluginUtils.serializeLocation(clickedBlock.getLocation());
        Optional<Clan> pylonOwnerClanOpt = plugin.getClanManager().getClanByPylonLocation(clickedLocationStr);

        // 클릭된 신호기가 등록된 파일런인 경우에만 처리합니다.
        if (pylonOwnerClanOpt.isPresent()) {
            // 기본 신호기 GUI가 열리는 것을 막습니다.
            event.setCancelled(true);

            Player player = event.getPlayer();
            Clan playerClan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());

            // 자신의 가문 파일런인지 확인합니다.
            if (pylonOwnerClanOpt.get().equals(playerClan)) {
                guiManager.openMainMenu(player);
                startGuiUpdateTask(player);
            } else {
                player.sendMessage("§c다른 가문의 파일런입니다.");
            }
        }
        // 등록되지 않은 일반 신호기인 경우, 아무것도 하지 않아 기본 GUI가 열리도록 둡니다.
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        String title = event.getView().getTitle();
        if (title.endsWith("§f의 선물상자")) {
            Player player = (Player) event.getPlayer();
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan != null) {
                plugin.getClanManager().setGiftBoxViewer(clan, player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        String title = event.getView().getTitle();
        String expectedMainTitle = BeaconGUIManager.getMainMenuTitle(clan);

        // 메인 GUI가 닫혔을 때 업데이트 작업을 중지합니다.
        if (title.equals(expectedMainTitle)) {
            stopGuiUpdateTask(player);
            return; // 메인 메뉴 자체를 닫을 때는 다시 열지 않음
        }

        // 서브 GUI 식별
        final boolean isGiftBoxGUI = title.endsWith("§f의 선물상자");

        if (isGiftBoxGUI) {
            // 선물상자 닫기 처리 로직 (from GiftBoxGuiManager)
            plugin.getClanManager().setGiftBoxViewer(clan, null);
            plugin.getInventoryDataManager().saveInventory(event.getInventory(), "gift_box", clan.getName());
            plugin.getInventoryDataManager().saveConfig();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 플레이어가 서버를 떠날 때 업데이트 작업을 중지합니다.
        stopGuiUpdateTask(event.getPlayer());
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        String expectedMainTitle = (clan != null) ? BeaconGUIManager.getMainMenuTitle(clan) : "";

        final String title = event.getView().getTitle();
        final boolean isMainGUI = title.equals(expectedMainTitle);
        final boolean isRecruitGUI = title.equals(RecruitGuiManager.RECRUIT_GUI_TITLE_SELECT) || title.equals(RecruitGuiManager.RECRUIT_GUI_TITLE_ROULETTE);
        final boolean isResurrectGUI = title.equals(ResurrectGuiManager.RESURRECT_GUI_TITLE);
        final boolean isShopGUI = title.equals(PylonShopManager.SHOP_GUI_TITLE);
        final boolean isGiftBoxGUI = title.startsWith("§d["); // 선물상자 GUI는 제목이 동적이므로 startsWith로 확인

        // 보호할 GUI인지 확인 (선물상자는 제외)
        final boolean isProtectedGUI = isMainGUI || isRecruitGUI || isResurrectGUI || isShopGUI;

        // 플러그인의 GUI가 아니면 아무것도 하지 않음
        if (!isProtectedGUI && !isGiftBoxGUI) {
            return;
        }

        // 선물상자 GUI에서는 아이템을 가져갈 수 있으므로 이벤트를 취소하지 않습니다.
        if (isGiftBoxGUI) {
            // 선물상자 GUI는 GiftBoxGuiManager에서 별도로 처리하므로 여기서는 아무것도 하지 않음.
            return;
        }

        // 보호 대상 GUI (메인, 모집, 부활, 상점)의 경우
        // 클릭된 인벤토리가 상단 GUI가 아니면 (즉, 플레이어 인벤토리이거나 GUI 바깥이면) 모든 동작을 취소.
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            event.setCancelled(true);
            return;
        }

        // 상단 GUI 내부에서의 클릭은 기본적으로 모두 취소하고, 각 핸들러에 처리를 위임.
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        if (isMainGUI) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta.getPersistentDataContainer().has(BeaconGUIManager.GUI_BUTTON_KEY, PersistentDataType.STRING)) {
                String action = meta.getPersistentDataContainer().get(BeaconGUIManager.GUI_BUTTON_KEY, PersistentDataType.STRING);
                if (action != null) guiManager.handleMenuClick(player, action);
            }
        } else if (isRecruitGUI) {
            guiManager.getRecruitGuiManager().handleGuiClick(event);
        } else if (isResurrectGUI) {
            guiManager.getResurrectGuiManager().handleGuiClick(event);
        } else {
            guiManager.getShopManager().handleGuiClick(event);
        }
    }

    /**
     * 플레이어의 메인 파일런 GUI를 주기적으로 업데이트하는 작업을 시작합니다.
     * @param player GUI를 연 플레이어
     */
    private void startGuiUpdateTask(Player player) {
        stopGuiUpdateTask(player); // 기존 작업이 있다면 중지

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;
        String expectedTitle = BeaconGUIManager.getMainMenuTitle(clan);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.getOpenInventory().getTitle().equals(expectedTitle)) {
                    this.cancel();
                    guiUpdateTasks.remove(player.getUniqueId());
                    return;
                }

                Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                if (clan != null) {
                    // 선물상자 아이템을 생성하여 GUI의 특정 슬롯을 업데이트합니다.
                    // BeaconGUIManager.openMainMenu의 슬롯과 일치해야 합니다. (수정: 8번 슬롯)
                    player.getOpenInventory().getTopInventory().setItem(8, guiManager.createGiftBoxItem(clan));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 즉시 시작하여 1초마다 반복

        guiUpdateTasks.put(player.getUniqueId(), task);
    }

    /**
     * 플레이어의 GUI 업데이트 작업을 중지합니다.
     * @param player 대상 플레이어
     */
    private void stopGuiUpdateTask(Player player) {
        BukkitTask existingTask = guiUpdateTasks.remove(player.getUniqueId());
        if (existingTask != null) {
            existingTask.cancel();
        }
    }
}