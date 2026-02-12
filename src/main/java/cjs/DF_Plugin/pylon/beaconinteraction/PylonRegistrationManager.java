package cjs.DF_Plugin.pylon.beaconinteraction;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class PylonRegistrationManager {
    private final DF_Main plugin;
    private static final String MAIN_PYLON_PREFIX = PluginUtils.colorize("&b[파일런] &f");
    private static final String AUX_PYLON_PREFIX = PluginUtils.colorize("&d[보조 파일런] &f");


    public PylonRegistrationManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void registerPylon(Player player, Block beacon, Clan clan, PylonType type) {
        // 1. 구조물 설치
        plugin.getPylonManager().getStructureManager().placeBaseAndBarrier(beacon.getLocation(), type);

        // 2. 파일런 정보 등록
        String locationString = PluginUtils.serializeLocation(beacon.getLocation());
        clan.addPylonLocation(locationString, type);
        plugin.getClanManager().saveClanData(clan);

        // 3. 영역 보호 활성화
        plugin.getPylonManager().getAreaManager().addProtectedPylon(beacon.getLocation(), clan);

        // 4. 타입에 따른 추가 처리 및 메시지 전송
        if (type == PylonType.MAIN_CORE) {
            // 파일런을 성공적으로 설치했으므로, 재설치 마감 기한을 완료 처리합니다.
            plugin.getPylonManager().getReinstallManager().completeReinstallation(player);
            player.sendMessage(MAIN_PYLON_PREFIX + "파일런 코어가 성공적으로 설치 및 활성화되었습니다!");
        } else if (type == PylonType.AUXILIARY) {
            player.sendMessage(AUX_PYLON_PREFIX + "보조 파일런이 성공적으로 설치 및 활성화되었습니다!");
        }
    }
}