package cjs.DF_Plugin.world.nether;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;

public class PortalHelper {

    /**
     * A helper class to store the result of a safe portal location search.
     */
    private static class SafePortalPlacement {
        final Location location;
        final Axis axis;

        SafePortalPlacement(Location location, Axis axis) {
            this.location = location;
            this.axis = axis;
        }
    }

    /**
     * 지정된 위치 주변에서 안전한 포탈을 찾거나 생성합니다.
     * @param center 중심 위치
     * @param searchRadius 포탈을 검색할 반경
     * @return 안전한 텔레포트 위치
     */
    public static Location findOrCreateSafePortal(Location center, int searchRadius) {
        // 1. 주변에 기존 포탈이 있는지 검색
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.NETHER_PORTAL) {
                        // 포탈 블록을 찾으면, 안전한 중앙 위치를 반환합니다.
                        return block.getLocation().add(0.5, 0.5, 0.5);
                    }
                }
            }
        }

        // 2. 기존 포탈이 없으면 안전한 장소를 찾아 새로 생성
        SafePortalPlacement placement = findSafePortalPlacement(center);
        buildPortal(placement.location, placement.axis);

        // 생성된 포탈의 중앙으로 텔레포트할 위치를 반환합니다.
        if (placement.axis == Axis.X) {
            return placement.location.clone().add(1.5, 0.5, 0.5);
        } else { // Axis.Z
            return placement.location.clone().add(0.5, 0.5, 1.5);
        }
    }

    /**
     * 포탈을 생성할 안전한 평지와 방향을 찾습니다.
     * @param center 검색 시작 위치
     * @return 포탈을 생성할 위치와 방향 정보
     */
    private static SafePortalPlacement findSafePortalPlacement(Location center) {
        World world = center.getWorld();
        int startX = center.getBlockX();
        int startZ = center.getBlockZ();

        for (int r = 0; r < 16; r++) { // 반경을 점차 늘려가며 탐색
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (Math.abs(i) != r && Math.abs(j) != r) continue; // 가장자리만 탐색

                    Location checkBaseLoc = world.getHighestBlockAt(startX + i, startZ + j).getLocation();
                    Location checkPortalLoc = checkBaseLoc.clone().add(0, 1, 0); // 포탈은 가장 높은 블록 위에 생성

                    Axis safeAxis = getSafeOrientation(checkPortalLoc);
                    if (safeAxis != null) {
                        return new SafePortalPlacement(checkPortalLoc, safeAxis);
                    }
                }
            }
        }
        // 안전한 위치를 못 찾으면 원래 위치 위에 X축 방향으로 생성 시도
        Location fallbackLoc = center.getWorld().getHighestBlockAt(center).getLocation().add(0, 1, 0);
        return new SafePortalPlacement(fallbackLoc, Axis.X);
    }

    /**
     * 해당 위치에 포탈을 지을 수 있는지, 있다면 어느 방향이 최적인지 확인합니다.
     * @param loc 포탈 프레임의 남서쪽(x, z가 가장 작은) 아래 코너가 될 위치
     * @return 건설 가능한 최적의 축 (Axis.X 또는 Axis.Z), 불가능하면 null
     */
    private static Axis getSafeOrientation(Location loc) {
        // X축 방향 포탈 (남북으로 긴 형태) 건설 가능 여부 확인
        boolean canBuildX = true;
        for (int x = 0; x < 4; x++) {
            if (!loc.clone().add(x, -1, 0).getBlock().getType().isSolid()) { // 바닥 확인
                canBuildX = false;
                break;
            }
            for (int y = 0; y < 5; y++) {
                if (!loc.clone().add(x, y, 0).getBlock().isPassable()) { // 공간 확인
                    canBuildX = false;
                    break;
                }
            }
            if (!canBuildX) break;
        }
        if (canBuildX) return Axis.X;

        // Z축 방향 포탈 (동서로 긴 형태) 건설 가능 여부 확인
        boolean canBuildZ = true;
        for (int z = 0; z < 4; z++) {
            if (!loc.clone().add(0, -1, z).getBlock().getType().isSolid()) { // 바닥 확인
                canBuildZ = false;
                break;
            }
            for (int y = 0; y < 5; y++) {
                if (!loc.clone().add(0, y, z).getBlock().isPassable()) { // 공간 확인
                    canBuildZ = false;
                    break;
                }
            }
            if (!canBuildZ) break;
        }
        if (canBuildZ) return Axis.Z;

        return null;
    }

    /**
     * 지정된 위치와 방향으로 네더 포탈을 생성합니다.
     * @param loc 포탈 프레임의 남서쪽(x, z가 가장 작은) 아래 코너
     * @param axis 포탈의 방향 (X 또는 Z)
     */
    private static void buildPortal(Location loc, Axis axis) {
        if (axis == Axis.X) {
            // X축 방향 포탈 (남북으로 긴 형태)
            loc.clone().add(1, -1, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(2, -1, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 0, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 1, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 2, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(3, 0, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(3, 1, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(3, 2, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(1, 3, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(2, 3, 0).getBlock().setType(Material.OBSIDIAN);

            for (int y = 0; y < 3; y++) {
                for (int x = 1; x < 3; x++) {
                    Block portalBlock = loc.clone().add(x, y, 0).getBlock();
                    portalBlock.setType(Material.NETHER_PORTAL);
                    Orientable portalData = (Orientable) portalBlock.getBlockData();
                    portalData.setAxis(Axis.X);
                    portalBlock.setBlockData(portalData);
                }
            }
        } else { // Axis.Z
            // Z축 방향 포탈 (동서로 긴 형태)
            loc.clone().add(0, -1, 1).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, -1, 2).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 0, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 1, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 2, 0).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 0, 3).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 1, 3).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 2, 3).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 3, 1).getBlock().setType(Material.OBSIDIAN);
            loc.clone().add(0, 3, 2).getBlock().setType(Material.OBSIDIAN);

            for (int y = 0; y < 3; y++) {
                for (int z = 1; z < 3; z++) {
                    Block portalBlock = loc.clone().add(0, y, z).getBlock();
                    portalBlock.setType(Material.NETHER_PORTAL);
                    Orientable portalData = (Orientable) portalBlock.getBlockData();
                    portalData.setAxis(Axis.Z);
                    portalBlock.setBlockData(portalData);
                }
            }
        }
    }
}