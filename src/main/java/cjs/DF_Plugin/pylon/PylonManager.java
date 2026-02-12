package cjs.DF_Plugin.pylon;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.beacongui.BeaconGUIManager;
import cjs.DF_Plugin.pylon.beaconinteraction.PylonAreaManager;
import cjs.DF_Plugin.pylon.beaconinteraction.PylonRegistrationManager;
import cjs.DF_Plugin.pylon.beaconinteraction.PylonStructureManager;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.pylon.item.ReconManager;
import cjs.DF_Plugin.pylon.item.ReturnScrollManager;
import cjs.DF_Plugin.pylon.reinstall.PylonReinstallManager;
import cjs.DF_Plugin.pylon.retrieval.PylonRetrievalManager;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

public class PylonManager {

    private final DF_Main plugin;
    private final PylonRegistrationManager registrationManager;
    private final PylonAreaManager areaManager;
    private final BeaconGUIManager guiManager;
    private final PylonRetrievalManager retrievalManager;
    private final PylonReinstallManager reinstallManager;
    private final ReconManager reconManager;
    private final PylonStructureManager structureManager;
    private final ReturnScrollManager scrollManager;
    private final PylonFeatureManager featureManager;

    public PylonManager(DF_Main plugin) {
        this.plugin = plugin;
        this.registrationManager = new PylonRegistrationManager(plugin);
        this.areaManager = new PylonAreaManager(plugin);
        this.guiManager = new BeaconGUIManager(plugin);
        this.retrievalManager = new PylonRetrievalManager(plugin);
        this.reinstallManager = new PylonReinstallManager(plugin);
        this.reconManager = new ReconManager(plugin);
        this.structureManager = new PylonStructureManager(this.areaManager);
        this.scrollManager = new ReturnScrollManager(plugin);
        this.featureManager = new PylonFeatureManager(plugin);

        startAreaEffectTask();
        plugin.getLogger().info("[파일런 관리] 파일런 시스템이 초기화되었습니다.");
    }

    public PylonRegistrationManager getRegistrationManager() {
        return registrationManager;
    }
    
    public PylonAreaManager getAreaManager() {
        return areaManager;
    }

    public BeaconGUIManager getGuiManager() {
        return guiManager;
    }

    public PylonRetrievalManager getRetrievalManager() {
        return retrievalManager;
    }

    public PylonReinstallManager getReinstallManager() {
        return reinstallManager;
    }

    public ReconManager getReconManager() {
        return reconManager;
    }

    public PylonStructureManager getStructureManager() {
        return structureManager;
    }

    public ReturnScrollManager getScrollManager() {
        return scrollManager;
    }

    public PylonFeatureManager getFeatureManager() { return featureManager; }

    /**
     * 특정 가문의 모든 파일런 기반(철 블록)을 다시 설치하여 안정화시킵니다.
     * @param clan 안정화할 가문
     */
    public void reinitializeAllBases(Clan clan) {
        for (Map.Entry<String, PylonType> entry : clan.getPylonLocations().entrySet()) {
            Location pylonLoc = PluginUtils.deserializeLocation(entry.getKey());
            PylonType pylonType = entry.getValue();
            if (pylonLoc != null) {
                // 각 파일런 타입에 맞는 기반을 설치합니다.
                structureManager.placeBaseOnly(pylonLoc, pylonType);
            }
        }
        clan.broadcastMessage("§b[파일런] §f모든 파일런 기반이 안정화되었습니다.");
    }

    /**
     * 플러그인 활성화 시, 저장된 모든 파일런 정보를 불러와 Area Manager에 등록합니다.
     * 이를 통해 서버 재시작 후에도 파일런 보호 및 효과가 즉시 적용됩니다.
     */
    public void loadExistingPylons() {
        plugin.getLogger().info("[파일런 관리] 기존 파일런 정보를 불러옵니다...");
        int count = 0;
        for (Clan clan : plugin.getClanManager().getAllClans()) {
            for (String locString : clan.getPylonLocations().keySet()) {
                Location pylonLoc = PluginUtils.deserializeLocation(locString);
                if (pylonLoc != null && pylonLoc.getWorld() != null) {
                    areaManager.addProtectedPylon(pylonLoc, clan);
                    count++;
                }
            }
        }
        plugin.getLogger().info("[파일런 관리] " + count + "개의 파일런을 성공적으로 불러왔습니다.");
    }

    private void startAreaEffectTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                areaManager.applyAreaEffects();
            }
        }.runTaskTimer(plugin, 100L, 100L); // Run every 5 seconds
    }
}