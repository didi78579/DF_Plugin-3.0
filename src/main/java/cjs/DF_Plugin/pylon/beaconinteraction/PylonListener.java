package cjs.DF_Plugin.pylon.beaconinteraction;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.pylon.clan.ClanManager;
import cjs.DF_Plugin.util.PluginUtils;
import cjs.DF_Plugin.util.item.PylonItemFactory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

public class PylonListener implements Listener {
    private final DF_Main plugin;
    private final ClanManager clanManager;
    private final PylonAreaManager areaManager;
    private static final String PREFIX = PluginUtils.colorize("&b[파일런] &f");

    public PylonListener(DF_Main plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
        this.areaManager = plugin.getPylonManager().getAreaManager();
    }

    // --- Pylon Placement (from BeaconInteractionListener & AuxiliaryPylonRegistrationManager) ---
    @EventHandler
    public void onPylonPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        ItemStack itemInHand = event.getItemInHand();

        if (block.getType() != Material.BEACON) {
            return;
        }

        boolean isMainCore = PylonItemFactory.isMainCore(itemInHand);
        boolean isAuxiliaryCore = PylonItemFactory.isAuxiliaryCore(itemInHand);

        if (!isMainCore && !isAuxiliaryCore) {
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(PREFIX + "§c가문에 소속되어 있어야 파일런을 설치할 수 있습니다.");
            event.setCancelled(true);
            return;
        }

        // 파일런 회수 후 재설치 기한이 지났는지 확인합니다.
        if (plugin.getPylonManager().getReinstallManager().hasDeadlinePassed(player)) {
            player.sendMessage(PREFIX + "§c파일런 재설치 기한이 지나 더 이상 설치할 수 없습니다.");
            event.setCancelled(true);
            return;
        }

        int maxPylons = plugin.getGameConfigManager().getConfig().getInt("pylon.max-pylons-per-clan", 1);
        if (clan.getPylonLocations().size() >= maxPylons) {
            player.sendMessage(PREFIX + "§c가문이 가질 수 있는 최대 파일런 개수(" + maxPylons + "개)에 도달했습니다.");
            event.setCancelled(true);
            return;
        }

        if (isMainCore) {
            boolean requireBelowSeaLevel = plugin.getGameConfigManager().getConfig().getBoolean("pylon.installation.require-below-sea-level", true);
            if (requireBelowSeaLevel && block.getLocation().getBlockY() >= block.getWorld().getSeaLevel()) {
                player.sendMessage(PREFIX + "§c주 파일런 코어는 해수면 아래에만 설치할 수 있습니다.");
                event.setCancelled(true);
                return;
            }
            plugin.getPylonManager().getRegistrationManager().registerPylon(player, block, clan, PylonType.MAIN_CORE);

        } else if (isAuxiliaryCore) {
            if (!clan.hasMainPylon()) {
                player.sendMessage(PREFIX + "§c보조 파일런을 설치하려면 먼저 주 파일런 코어를 설치해야 합니다.");
                event.setCancelled(true);
                return;
            }

            boolean multiCoreEnabled = plugin.getGameConfigManager().getConfig().getBoolean("pylon.features.multi-core", false);
            if (!multiCoreEnabled) {
                player.sendMessage(PREFIX + "§c서버에서 멀티 코어 기능이 비활성화되어 있어 보조 파일런을 설치할 수 없습니다.");
                event.setCancelled(true);
                return;
            }

            boolean requireBelowSeaLevel = plugin.getGameConfigManager().getConfig().getBoolean("pylon.installation.require-below-sea-level", true);
            if (requireBelowSeaLevel && block.getLocation().getBlockY() >= block.getWorld().getSeaLevel()) {
                player.sendMessage(PREFIX + "§c보조 파일런은 해수면 아래에만 설치할 수 있습니다.");
                event.setCancelled(true);
                return;
            }

            Location beaconLoc = block.getLocation();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (plugin.getPylonManager().getAreaManager().isPylonStructureBlock(beaconLoc.clone().add(x, -1, z))) {
                        player.sendMessage(PREFIX + "§c다른 파일런의 기반과 겹치는 위치에는 설치할 수 없습니다.");
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            if (!plugin.getPylonManager().getAreaManager().isLocationInClanMainPylonArea(clan, block.getLocation())) {
                player.sendMessage(PREFIX + "§c보조 파일런은 주 파일런의 영역 내에만 설치할 수 있습니다.");
                event.setCancelled(true);
                return;
            }

            // 보조 파일런 등록 로직을 PylonRegistrationManager에 위임합니다.
            plugin.getPylonManager().getRegistrationManager().registerPylon(player, block, clan, PylonType.AUXILIARY);
        }
    }

    // --- Pylon Protection (from PylonProtectionListener) ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPylonBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block.getType() == Material.BEACON) {
            Clan victimClan = areaManager.getClanAt(block.getLocation());
            if (victimClan != null) {
                handleBeaconBreak(event, block, victimClan);
            }
            return;
        }

        if (areaManager.isPylonStructureBlock(block.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c이 블록은 파일런의 일부이므로 파괴할 수 없습니다.");
        }
    }

    private void handleBeaconBreak(BlockBreakEvent event, Block block, Clan victimClan) {
        Player player = event.getPlayer();
        Clan attackerClan = clanManager.getClanByPlayer(player.getUniqueId());
        String pylonLocStr = PluginUtils.serializeLocation(block.getLocation());
        PylonType pylonType = victimClan.getPylonType(pylonLocStr);

        if (victimClan.equals(attackerClan)) {
            if (pylonType == PylonType.MAIN_CORE && !victimClan.getLeader().equals(player.getUniqueId())) {
                player.sendMessage("§c주 파일런 코어는 가문 대표만 회수할 수 있습니다.");
                event.setCancelled(true);
                return;
            }
            boolean success = plugin.getPylonManager().getRetrievalManager().handlePylonRetrieval(player, block, victimClan);
            if (!success) {
                event.setCancelled(true);
            }
            return;
        }

        if (attackerClan == null) {
            player.sendMessage("§c가문 소속원만 다른 가문의 파일런을 파괴할 수 있습니다.");
            event.setCancelled(true);
            return;
        }

        if (pylonType == PylonType.MAIN_CORE) {
            if (victimClan.hasAuxiliaryPylons()) {
                player.sendMessage("§c이 가문의 모든 보조 파일런을 먼저 파괴해야 합니다.");
                event.setCancelled(true);
                return;
            }
            event.setDropItems(false);
            clanManager.absorbClan(attackerClan, victimClan);
        } else if (pylonType == PylonType.AUXILIARY) {
            event.setDropItems(false);
            plugin.getPylonManager().getStructureManager().removeBaseAndBarrier(block.getLocation());
            areaManager.removeProtectedPylon(block.getLocation());
            victimClan.removePylonLocation(pylonLocStr);
            clanManager.saveClanData(victimClan);
            attackerClan.broadcastMessage("§a" + victimClan.getFormattedName() + "§a 가문의 보조 파일런을 파괴했습니다!");
            victimClan.broadcastMessage("§c" + attackerClan.getFormattedName() + "§c 가문에 의해 보조 파일런이 파괴되었습니다!");

            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    Clan currentVictimClan = clanManager.getClanByName(victimClan.getName());
                    if (currentVictimClan != null) {
                        plugin.getPylonManager().reinitializeAllBases(currentVictimClan);
                    }
                }
            }.runTaskLater(plugin, 100L);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplodeByBlock(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> areaManager.getClanAt(block.getLocation()) != null || areaManager.isPylonStructureBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplodeByEntity(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> areaManager.getClanAt(block.getLocation()) != null || areaManager.isPylonStructureBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (areaManager.getClanAt(block.getLocation()) != null || areaManager.isPylonStructureBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (areaManager.getClanAt(block.getLocation()) != null || areaManager.isPylonStructureBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // --- Pylon Item Interactions (from PylonItemListener) ---
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null || event.getInventory().getResult() == null) {
            return;
        }
        if (event.getInventory().getResult().getType() == Material.BEACON) {
            event.getInventory().setResult(PylonItemFactory.createMainCore());
        }
    }

    @EventHandler
    public void onDropPylon(PlayerDropItemEvent event) {
        if (PylonItemFactory.isMainCore(event.getItemDrop().getItemStack())) {
            event.getPlayer().sendMessage(PREFIX + "§c파일런 코어는 버릴 수 없습니다.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onStorePylon(InventoryClickEvent event) {
        if (!event.getView().getTitle().endsWith(" §r§f파일런 창고")) {
            return;
        }

        ItemStack movedItem = null;
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                movedItem = event.getCurrentItem();
            }
        } else {
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                movedItem = event.getCursor();
            }
        }

        if (PylonItemFactory.isMainCore(movedItem)) {
            event.getWhoClicked().sendMessage(PREFIX + "§c파일런 코어는 파일런 창고에 보관할 수 없습니다.");
            event.setCancelled(true);
        }
    }
}