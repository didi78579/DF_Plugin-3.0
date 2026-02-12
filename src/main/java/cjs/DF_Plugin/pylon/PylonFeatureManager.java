package cjs.DF_Plugin.pylon;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PylonFeatureManager {

    private final DF_Main plugin;

    public PylonFeatureManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    /**
     * 멀티 코어 기능이 비활성화될 때 호출됩니다.
     * 모든 가문의 모든 보조 파일런을 회수합니다.
     */
    public void handleMultiCoreDeactivation() {
        boolean anyRetrieved = false;
        ItemStack auxCoreItem = CustomItemFactory.createAuxiliaryPylonCore();

        for (Clan clan : plugin.getClanManager().getAllClans()) {
            List<String> pylonsToRemove = new ArrayList<>();
            for (String pylonLocStr : clan.getPylonLocations().keySet()) {
                if (clan.getPylonType(pylonLocStr) == PylonType.AUXILIARY) {
                    pylonsToRemove.add(pylonLocStr);
                }
            }

            if (pylonsToRemove.isEmpty()) continue;

            anyRetrieved = true;
            Inventory pylonStorage = plugin.getClanManager().getPylonStorage(clan);

            for (String pylonLocStr : pylonsToRemove) {
                Location pylonLoc = PluginUtils.deserializeLocation(pylonLocStr);
                if (pylonLoc != null) {
                    plugin.getPylonManager().getStructureManager().removeBaseAndBarrier(pylonLoc);
                    pylonLoc.getBlock().setType(Material.AIR);
                    plugin.getPylonManager().getAreaManager().removeProtectedPylon(pylonLoc);
                    if (pylonStorage != null) pylonStorage.addItem(auxCoreItem.clone());
                }
                clan.removePylonLocation(pylonLocStr);
            }
            plugin.getClanManager().saveClanData(clan);
            clan.broadcastMessage(PluginUtils.colorize("&d[보조 파일런] &f서버 설정 변경으로 모든 보조 파일런이 회수되어 파일런 창고에 보관되었습니다."));
        }

        if (anyRetrieved) {
            Bukkit.broadcastMessage(PluginUtils.colorize("&b[파일런] &f멀티 코어 기능이 비활성화되어 모든 보조 파일런이 각 가문의 창고로 회수되었습니다."));
        }
    }
}