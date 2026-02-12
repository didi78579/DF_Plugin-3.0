package cjs.DF_Plugin.pylon.beaconinteraction;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.pylon.PylonType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class PylonStructureManager {

    private final PylonAreaManager areaManager;

    public PylonStructureManager(PylonAreaManager areaManager) {
        this.areaManager = areaManager;
    }

    /**
     * 파일런의 기반(철 블록)과 배리어 기둥을 제거합니다.
     * 단, 제거하려는 블록이 다른 파일런의 구조물에 속해있다면 제거하지 않습니다.
     * @param pylonLoc 제거할 파일런의 위치
     */
    public void removeBaseAndBarrier(Location pylonLoc) {
        World world = pylonLoc.getWorld();
        if (world == null) return;

        // 기반 제거
        Location baseCenter = pylonLoc.clone().subtract(0, 1, 0);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = baseCenter.clone().add(x, 0, z).getBlock();
                // 이 블록이 '다른' 파일런의 일부가 아닐 경우에만 제거합니다.
                if (!areaManager.isPylonStructureBlock(block.getLocation(), pylonLoc)) {
                    block.setType(Material.AIR);
                }
            }
        }

        // 배리어 기둥 제거
        for (int y = 0; y < world.getMaxHeight() - pylonLoc.getBlockY(); y++) {
            Block block = pylonLoc.clone().add(0, y, 0).getBlock();
            // 배리어 블록만 대상으로 하고, 다른 파일런의 일부가 아닌지 확인합니다.
            if (block.getType() == Material.BARRIER && !areaManager.isPylonStructureBlock(block.getLocation(), pylonLoc)) {
                block.setType(Material.AIR);
            }
        }
    }

    /**
     * 파일런의 기반(철 블록)만 설치합니다.
     * @param pylonLoc 설치할 파일런의 위치
     * @param type 파일런의 종류 (주/보조)
     */
    public void placeBaseOnly(Location pylonLoc, PylonType type) {
        World world = pylonLoc.getWorld();
        if (world == null) return;

        GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
        Material baseMaterial;
        if (type == PylonType.MAIN_CORE) {
            String materialName = configManager.getConfig().getString("pylon.installation.main-core-base-material", "DIAMOND_BLOCK").toUpperCase();
            try {
                baseMaterial = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                DF_Main.getInstance().getLogger().warning("Invalid material name for 'pylon.installation.main-core-base-material': " + materialName + ". Defaulting to DIAMOND_BLOCK.");
                baseMaterial = Material.DIAMOND_BLOCK;
            }
        } else {
            String materialName = configManager.getConfig().getString("pylon.installation.auxiliary-core-base-material", "IRON_BLOCK").toUpperCase();
            try {
                baseMaterial = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                DF_Main.getInstance().getLogger().warning("Invalid material name for 'pylon.installation.auxiliary-core-base-material': " + materialName + ". Defaulting to IRON_BLOCK.");
                baseMaterial = Material.IRON_BLOCK;
            }
        }

        Location baseCenter = pylonLoc.clone().subtract(0, 1, 0);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                baseCenter.clone().add(x, 0, z).getBlock().setType(baseMaterial);
            }
        }
    }

    /**
     * 파일런의 기반(철 블록)과 배리어 기둥을 설치합니다.
     * @param pylonLoc 설치할 파일런의 위치
     * @param type 파일런의 종류 (주/보조)
     */
    public void placeBaseAndBarrier(Location pylonLoc, PylonType type) {
        World world = pylonLoc.getWorld();
        if (world == null) return;

        // 기반 설치
        placeBaseOnly(pylonLoc, type);

        // 배리어 기둥 설치 (비콘 바로 위부터 월드 끝까지)
        for (int y = 1; y < world.getMaxHeight() - pylonLoc.getBlockY(); y++) {
            Block block = pylonLoc.clone().add(0, y, 0).getBlock();
            block.setType(Material.BARRIER);
        }
    }
}