package cjs.DF_Plugin.pylon.retrieval;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.InventoryUtils;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PylonRetrievalManager {
    private final DF_Main plugin;
    private static final String PREFIX = PluginUtils.colorize("&b[파일런] &f");

    public PylonRetrievalManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    /**
     * 가문 대표가 파일런을 회수하는 로직을 실행합니다.
     * 모든 파일런을 제거하고 아이템을 지급하며, 쿨타임 및 재설치 타이머를 적용합니다.
     * @param leader 가문 대표
     */
    public boolean executePylonRetrieval(Player leader) {
        Clan clan = plugin.getClanManager().getClanByPlayer(leader.getUniqueId());
        if (clan == null || !clan.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage(PREFIX + "§c파일런 회수는 가문 대표만 가능합니다.");
            return false;
        }

        if (clan.getPylonLocations().isEmpty()) {
            leader.sendMessage(PREFIX + "§c회수할 파일런이 없습니다.");
            return false;
        }

        long cooldownMillis = TimeUnit.HOURS.toMillis(plugin.getGameConfigManager().getConfig().getInt("pylon.retrieval.cooldown-hours", 24));
        if (System.currentTimeMillis() - clan.getLastRetrievalTime() < cooldownMillis) {
            long remainingMillis = cooldownMillis - (System.currentTimeMillis() - clan.getLastRetrievalTime());
            String remainingTime = String.format("%02d시간 %02d분",
                    TimeUnit.MILLISECONDS.toHours(remainingMillis),
                    TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60);
            leader.sendMessage(PREFIX + "§c다음 회수까지 " + remainingTime + " 남았습니다.");
            return false;
        }

        List<ItemStack> pylonItems = new ArrayList<>();
        // Make a copy to avoid ConcurrentModificationException
        Map<String, PylonType> pylonLocations = new HashMap<>(clan.getPylonLocations());

        for (Map.Entry<String, PylonType> entry : pylonLocations.entrySet()) {
            String pylonLocStr = entry.getKey();
            PylonType pylonType = entry.getValue();

            Location pylonLoc = PluginUtils.deserializeLocation(pylonLocStr);
            if (pylonLoc != null && pylonLoc.getBlock().getType() == Material.BEACON) {
                pylonLoc.getBlock().setType(Material.AIR);
                plugin.getPylonManager().getStructureManager().removeBaseAndBarrier(pylonLoc);
                plugin.getPylonManager().getAreaManager().removeProtectedPylon(pylonLoc);
            }

            if (pylonType == PylonType.MAIN_CORE) {
                pylonItems.add(CustomItemFactory.createMainPylonCore());
            } else {
                pylonItems.add(CustomItemFactory.createAuxiliaryPylonCore());
            }
        }

        clan.clearPylonLocations();
        clan.setLastRetrievalTime(System.currentTimeMillis());
        clan.setLastAuxiliaryRetrievalTime(System.currentTimeMillis());
        plugin.getClanManager().saveClanData(clan);

        InventoryUtils.giveOrDropItems(leader, pylonItems.toArray(new ItemStack[0]));
        leader.sendMessage(PREFIX + "§a모든 파일런(" + pylonItems.size() + "개)을 회수했습니다.");
        // 파일런을 회수했으므로, 재설치 마감 기한을 시작합니다.
        plugin.getPylonManager().getReinstallManager().startReinstallDeadline(leader);
        return true;
    }

    /**
     * 플레이어가 자신의 파일런을 파괴(회수)할 때 호출됩니다.
     * @param player 회수하는 플레이어
     * @param pylonBlock 파괴된 파일런 블록
     * @param clan 파일런을 소유한 가문
     * @return 회수에 성공하면 true, 실패(쿨타임 등)하면 false
     */
    public boolean handlePylonRetrieval(Player player, Block pylonBlock, Clan clan) {
        // 가문 대표만 회수 가능하도록 제한합니다.
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§c자신의 가문 파일런은 가문장만 회수할 수 있습니다.");
            return false;
        }

        String pylonLocStr = PluginUtils.serializeLocation(pylonBlock.getLocation());
        PylonType pylonType = clan.getPylonType(pylonLocStr);

        // 주 파일런은 개별 회수 불가, 보조 파일런만 개별 회수 가능
        if (pylonType == PylonType.MAIN_CORE) {
            // 주 파일런을 파괴하면 모든 파일런을 회수하는 로직을 실행합니다.
            return executePylonRetrieval(player);
        }

        if (pylonType == PylonType.AUXILIARY) {
            // 보조 파일런 회수 쿨타임을 확인합니다 (10분).
            long cooldownMillis = TimeUnit.MINUTES.toMillis(10);
            if (System.currentTimeMillis() - clan.getLastAuxiliaryRetrievalTime() < cooldownMillis) {
                long remainingMillis = cooldownMillis - (System.currentTimeMillis() - clan.getLastAuxiliaryRetrievalTime());
                String remainingTime = String.format("%02d분 %02d초",
                        TimeUnit.MILLISECONDS.toMinutes(remainingMillis),
                        TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60);
                player.sendMessage(PREFIX + "§c다음 보조 파일런 회수까지 " + remainingTime + " 남았습니다.");
                return false;
            }

            clan.removePylonLocation(pylonLocStr);
            clan.setLastAuxiliaryRetrievalTime(System.currentTimeMillis());
            plugin.getClanManager().saveClanData(clan);
            plugin.getPylonManager().getAreaManager().removeProtectedPylon(pylonBlock.getLocation());
            plugin.getPylonManager().getStructureManager().removeBaseAndBarrier(pylonBlock.getLocation());

            InventoryUtils.giveOrDropItems(player, CustomItemFactory.createAuxiliaryPylonCore());
            player.sendMessage(PREFIX + "보조 파일런을 성공적으로 회수했습니다.");

            pylonBlock.setType(Material.AIR);
            return true;
        }

        return false; // Should not be reached
    }
}