package cjs.DF_Plugin.player.death;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.GameStartManager;
import cjs.DF_Plugin.pylon.clan.Clan;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.Random;

public class PlayerRespawnListener implements Listener {

    private final DF_Main plugin;
    private final GameStartManager gameStartManager;

    public PlayerRespawnListener(DF_Main plugin) {
        this.plugin = plugin;
        this.gameStartManager = plugin.getGameStartManager();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location customRespawnLoc = getCustomRespawnLocation(player);

        if (customRespawnLoc != null) {
            event.setRespawnLocation(customRespawnLoc);

            // 파일런이 없을 때 임시 지점에서 부활하는 경우에만 메시지를 보냅니다.
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan != null && gameStartManager.isGameStarted() && clan.getPylonLocations().isEmpty()) {
                player.sendMessage("§e[부활] §e설치된 파일런이 없어 임시 지점에서 부활합니다.");
            }
        }
    }

    /**
     * 플레이어가 엔드 월드에서 나갈 때(엔드 포탈 사용 시) 부활 위치와 동일한 장소로 이동시킵니다.
     */
    @EventHandler
    public void onPlayerLeaveEnd(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return;
        }
        if (event.getFrom().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }

        Player player = event.getPlayer();
        Location customExitLoc = getCustomRespawnLocation(player);

        if (customExitLoc != null) {
            event.setTo(customExitLoc);
            player.sendMessage("§a[귀환] §f엔드에서 귀환하여 지정된 장소로 이동합니다.");
        }
    }

    /**
     * 플레이어의 상황에 맞는 커스텀 부활/귀환 위치를 결정합니다.
     * @param player 대상 플레이어
     * @return 커스텀 위치. 없으면 null.
     */
    public Location getCustomRespawnLocation(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return null;

        // 파일런이 있으면 파일런 주변에서 부활
        if (!clan.getPylonLocations().isEmpty()) {
            return getSafePylonRespawnLocation(clan);
        }

        // 파일런이 없고, 게임이 시작된 상태라면 임시 시작 지점에서 부활
        if (gameStartManager.isGameStarted()) {
            return clan.getStartLocation();
        }

        return null; // 그 외의 경우(게임 시작 전, 파일런 없음)는 기본 부활 로직 따름
    }

    private Location getSafePylonRespawnLocation(Clan clan) {
        // 주 파일런 위치를 가져와서 안전한 리스폰 장소를 찾습니다.
        return clan.getMainPylonLocationObject()
                .map(this::findSafeRespawnLocation)
                .orElse(null);
    }

    /**
     * 파일런 주변에서 플레이어가 안전하게 스폰될 수 있는 위치를 찾습니다.
     * 지하, 액체, 배리어 블록을 피해 지상의 안전한 공간을 찾습니다.
     * @param pylonCenter 파일런(신호기)의 중앙 위치
     * @return 안전한 리스폰 위치
     */
    private Location findSafeRespawnLocation(Location pylonCenter) {
        int radius = plugin.getGameConfigManager().getConfig().getInt("pylon.area-effects.radius", 30);
        Random random = new Random();
        World world = pylonCenter.getWorld();

        // 안전한 위치를 찾기 위해 일정 횟수 시도합니다.
        for (int i = 0; i < 100; i++) { // 100번의 무작위 시도
            int xOffset = random.nextInt(radius * 2 + 1) - radius;
            int zOffset = random.nextInt(radius * 2 + 1) - radius;

            // 원형 범위 체크
            if (xOffset * xOffset + zOffset * zOffset > radius * radius) {
                continue;
            }

            int checkX = pylonCenter.getBlockX() + xOffset;
            int checkZ = pylonCenter.getBlockZ() + zOffset;

            // 해당 X, Z 좌표의 가장 높은 블록을 찾습니다 (지하 제외).
            Block groundBlock = world.getHighestBlockAt(checkX, checkZ);

            // 안전성 검사
            // 1. 바닥이 액체, 배리어, 또는 통과 불가능한 블록이 아니어야 합니다.
            if (groundBlock.isLiquid() || groundBlock.getType() == Material.BARRIER || !groundBlock.getType().isSolid()) {
                continue;
            }

            // 2. 스폰될 위치(발, 머리)에 충분한 공간이 있어야 합니다.
            Block feetBlock = groundBlock.getRelative(BlockFace.UP);
            Block headBlock = feetBlock.getRelative(BlockFace.UP);

            if (feetBlock.isPassable() && headBlock.isPassable()) {
                // 안전한 위치를 찾았으므로, 블록 중앙으로 위치를 조정하여 반환합니다.
                Location spawnLocation = feetBlock.getLocation().add(0.5, 0, 0.5);
                // 플레이어의 시야 방향을 파일런 중심으로 설정
                Vector direction = pylonCenter.toVector().subtract(spawnLocation.toVector()).normalize();
                spawnLocation.setDirection(direction);
                return spawnLocation;
            }
        }

        // 무작위 탐색 실패 시, 기존의 4방향 탐색 로직을 최후의 수단으로 사용합니다.
        Vector[] fallbackOffsets = {
                new Vector(2, 0, 0), new Vector(-2, 0, 0),
                new Vector(0, 0, 2), new Vector(0, 0, -2)
        };

        for (Vector offset : fallbackOffsets) {
            Location checkLoc = pylonCenter.clone().add(offset);
            Block groundBlock = world.getHighestBlockAt(checkLoc);

            if (groundBlock.getType().isSolid() && !groundBlock.isLiquid() && groundBlock.getType() != Material.BARRIER) {
                Block feetBlock = groundBlock.getRelative(BlockFace.UP);
                Block headBlock = feetBlock.getRelative(BlockFace.UP);
                if (feetBlock.isPassable() && headBlock.isPassable()) {
                    Location spawnLocation = feetBlock.getLocation().add(0.5, 0, 0.5);
                    Vector direction = pylonCenter.toVector().subtract(spawnLocation.toVector()).normalize();
                    spawnLocation.setDirection(direction);
                    return spawnLocation;
                }
            }
        }

        // 최후의 최후의 수단: 파일런 바로 위 (기존 문제의 원인이지만, 다른 방법이 없을 때 사용)
        return pylonCenter.getWorld().getHighestBlockAt(pylonCenter).getLocation().add(0.5, 1, 0.5);
    }
}