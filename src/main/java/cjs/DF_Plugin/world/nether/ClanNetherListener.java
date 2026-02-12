package cjs.DF_Plugin.world.nether;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.pylon.beaconinteraction.PylonAreaManager;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.pylon.clan.ClanManager;
import cjs.DF_Plugin.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class ClanNetherListener implements Listener {

    private final DF_Main plugin;
    private final GameConfigManager configManager;
    private final ClanManager clanManager;
    private final WorldManager worldManager;
    private final PylonAreaManager pylonAreaManager;
    private static final String PREFIX = "§c[지옥문] §f";

    public ClanNetherListener(DF_Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getGameConfigManager();
        this.clanManager = plugin.getClanManager();
        this.worldManager = plugin.getWorldManager();
        this.pylonAreaManager = plugin.getPylonManager().getAreaManager();
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        // 서버 재시작 후 플레이어가 접속할 때, 해당 플레이어의 가문 지옥이 로드되어 있도록 보장합니다.
        // 이를 통해 지옥에서 로그아웃한 플레이어가 0,0으로 이동하는 버그를 방지합니다.
        Player player = event.getPlayer();
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());

        if (clan != null) {
            // 이 메서드는 월드가 없으면 생성하고, 로드되어 있지 않으면 로드합니다.
            // 월드가 이미 로드되어 있다면 아무 작업도 하지 않으므로, 성능에 미치는 영향은 미미합니다.
            worldManager.getOrCreateClanNether(clan);
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        // 이 리스너는 네더 포탈로 인한 이동만 처리합니다.
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }

        if (!configManager.getConfig().getBoolean("pylon.features.clan-nether", true)) {
            return;
        }

        Player player = event.getPlayer();
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());

        if (clan == null) {
            player.sendMessage(PREFIX + "가문에 소속되어 있어야 지옥에 입장할 수 있습니다.");
            event.setCancelled(true);
            return;
        }

        World fromWorld = event.getFrom().getWorld();
        
        // 오버월드 -> 지옥
        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            handlePortalToClanNether(event, player, clan);
        } 
        // 가문 전용 지옥 -> 오버월드
        else if (fromWorld.getName().equals(worldManager.getClanNetherWorldName(clan))) {
            handlePortalFromClanNether(event, player, clan);
        }
    }

    /**
     * 오버월드에서 가문 전용 지옥으로 이동하는 로직을 처리합니다.
     */
    private void handlePortalToClanNether(PlayerPortalEvent event, Player player, Clan clan) {
        if (!pylonAreaManager.isLocationInClanPylonArea(clan, event.getFrom())) {
            player.sendMessage(PREFIX + "파일런 범위 내에 있는 지옥문만 사용할 수 있습니다.");
            event.setCancelled(true);
            return;
        }

        World clanNether = worldManager.getOrCreateClanNether(clan);
        if (clanNether == null) {
            player.sendMessage(PREFIX + "§c가문 전용 지옥을 생성하는 데 실패했습니다. 관리자에게 문의하세요.");
            event.setCancelled(true);
            return;
        }

        // 가문 전용 지옥에 게임 규칙(인벤 세이브 등)이 항상 적용되도록 합니다.
        worldManager.applyRulesToWorld(clanNether);

        // 올바른 목적지 Location 객체 생성
        Location from = event.getFrom();
        Location destination = new Location(clanNether, from.getX() / 8.0, from.getY(), from.getZ() / 8.0, from.getYaw(), from.getPitch());
        event.setTo(destination); // 서버가 포탈을 찾거나 생성하도록 목적지 설정
    }

    /**
     * 가문 전용 지옥에서 오버월드로 이동하는 로직을 처리합니다.
     */
    private void handlePortalFromClanNether(PlayerPortalEvent event, Player player, Clan clan) {
        event.setCancelled(true); // 기본 포탈 이동 취소
        clan.getMainPylonLocationObject().ifPresentOrElse(mainPylonLocation -> {
            // 주 파일런 위치로 목적지 설정. 서버가 주변에서 포탈을 찾거나 생성합니다.
            Location safePortalLocation = PortalHelper.findOrCreateSafePortal(mainPylonLocation, 16);
            player.teleport(safePortalLocation);
        }, () -> {
            player.sendMessage(PREFIX + "§c가문의 파일런 코어 위치를 찾을 수 없어 오버월드로 귀환할 수 없습니다.");
        });
    }
}