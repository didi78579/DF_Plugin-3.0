package cjs.DF_Plugin.player.stats;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.data.PlayerDataManager;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.pylon.clan.ClanManager;
import cjs.DF_Plugin.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 플레이어의 접속, 데이터 로딩, 초기 설정을 관리하는 통합 리스너 및 관리자 클래스.
 * 기존 PlayerJoinListener와 PlayerRegistryManager의 기능을 병합했습니다.
 */
public class PlayerConnectionManager implements Listener {
    private final DF_Main plugin;
    private final Map<UUID, RegisteredPlayerData> allPlayers = new HashMap<>();

    public PlayerConnectionManager(DF_Main plugin) {
        this.plugin = plugin;
        loadPlayers();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 서버 입장 메시지를 제거합니다.
        event.setJoinMessage(null);

        Player player = event.getPlayer();
        ClanManager clanManager = plugin.getClanManager();
        StatsManager statsManager = plugin.getStatsManager();
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();

        // 플레이어 이름 및 클랜 정보 업데이트 (레지스트리)
        plugin.getPlayerDataManager().setPlayerName(player);
        updatePlayerClan(player.getUniqueId(), clanManager.getClanByPlayer(player.getUniqueId()));

        // 게임이 시작된 경우, 접속한 플레이어의 상태를 처리합니다.
        if (plugin.getGameStartManager().isGameStarted()) {
            Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());

            if (playerClan == null) {
                // 가문이 없는 플레이어는 관전 모드로 전환
                plugin.getSpectatorManager().setSpectator(player);
                player.sendMessage("§e[알림] §e게임이 진행 중이며 소속된 가문이 없어 관전 모드로 접속합니다.");
            } else {
                player.setGameMode(GameMode.SURVIVAL);

                // 가문 가입 후 첫 접속(신규 영입)인지 확인합니다.
                if (playerClan.isPendingFirstSpawn(player.getUniqueId())) {
                    // 1. 신규 영입된 멤버는 파일런 근처로 소환합니다.
                    plugin.getPylonManager().getAreaManager().findSafeSpawnInPylonArea(playerClan).ifPresentOrElse(
                            safeLoc -> {
                                player.teleport(safeLoc);
                                player.sendMessage("§a[가문] §a가문에 새로 합류하여 파일런 근처로 소환되었습니다.");
                                // 첫 스폰 처리가 끝났으므로 플래그를 제거하고 저장합니다.
                                playerClan.removePendingFirstSpawn(player.getUniqueId());
                                clanManager.saveClanData(playerClan);
                            },
                            () -> {
                                // 소환에 실패했으므로, 플래그를 제거하지 않고 다음 접속 시 다시 시도하도록 합니다.
                                player.sendMessage("§c[가문] §c소속 가문의 파일런을 찾을 수 없어 다음 접속 시 다시 시도합니다.");
                            }
                    );
                } else if (!playerDataManager.hasInitialTeleportDone(player.getUniqueId())) { // 게임 시작 또는 가문 생성 후 첫 접속
                    // 2. 게임 시작 후 첫 접속이거나, 가문 생성 후 첫 접속인 경우 시작 지점으로 이동합니다.
                    Location startLoc = playerClan.getStartLocation();
                    if (startLoc != null) {
                        player.teleport(startLoc);
                        player.sendMessage("§a[알림] §a게임이 시작되어 시작 지점으로 이동합니다.");
                        // 게임 시작 후 첫 접속하는 가문 대표에게 파일런 코어를 지급합니다.
                        if (playerClan.getLeader().equals(player.getUniqueId())) {
                            InventoryUtils.giveOrDropItems(player, CustomItemFactory.createMainPylonCore());
                        }
                    } else {
                        player.sendMessage("§c[알림] §c가문의 시작 지점을 찾을 수 없습니다. 관리자에게 문의하세요.");
                    }
                    playerDataManager.setInitialTeleportDone(player.getUniqueId(), true);
                    // 파일런이 있다면, 아무 행동도 하지 않고 마지막 위치에서 접속합니다.
                }
            }
        }

        // 플레이어 태그 업데이트
        clanManager.getPlayerTagManager().updatePlayerTag(player);

        // 플레이어의 스탯이 등록되어 있는지 확인하고, 없으면 기본값으로 자동 등록합니다.
        if (statsManager != null && !statsManager.hasStats(player.getUniqueId())) {
            statsManager.getPlayerStats(player.getUniqueId()); // 캐시에 기본 스탯 생성
            statsManager.savePlayerStats(player.getUniqueId()); // 파일에 저장
            player.sendMessage("§a[스탯] §a기본 스탯으로 등록되었습니다.");
        }

        // 차원의 균열 보스바 표시
        if (plugin.getRiftManager() != null) {
            plugin.getRiftManager().showBarToPlayer(player);
        }
    }

    // --- Player Registry Logic ---

    private void loadPlayers() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        ConfigurationSection playersSection = pdm.getConfig().getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidString : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String name = playersSection.getString(uuidString + ".name");
                    String clanName = playersSection.getString(uuidString + ".clan");
                    if (name != null) {
                        allPlayers.put(uuid, new RegisteredPlayerData(name, clanName));
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[플레이어 관리] Invalid UUID in playerdata.yml: " + uuidString);
                }
            }
        }

        // 서버에 접속한 적 있는 모든 플레이어를 확인하고, 등록되지 않은 경우 새로 등록합니다.
        boolean needsSave = false;
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID uuid = offlinePlayer.getUniqueId();
            if (!allPlayers.containsKey(uuid)) {
                String name = offlinePlayer.getName();
                if (name != null) {
                    plugin.getLogger().info("[플레이어 관리] 기록에 없는 오프라인 플레이어를 발견하여 등록합니다: " + name);
                    allPlayers.put(uuid, new RegisteredPlayerData(name, null));
                    pdm.getPlayerSection(uuid).set("name", name);
                    pdm.getPlayerSection(uuid).set("clan", null);
                    needsSave = true;
                }
            }
        }

        if (needsSave) {
            pdm.saveConfig();
        }
    }

    public void updatePlayerClan(UUID playerUUID, Clan clan) {
        RegisteredPlayerData currentData = allPlayers.get(playerUUID);
        String playerName = Bukkit.getOfflinePlayer(playerUUID).getName();
        if (playerName == null && currentData != null) {
            playerName = currentData.name();
        }
        if (playerName == null) return; // Cannot find player name.

        String clanName = (clan != null) ? clan.getName() : null;

        if (currentData == null || !currentData.name().equals(playerName) || !Objects.equals(currentData.clanName(), clanName)) {
            allPlayers.put(playerUUID, new RegisteredPlayerData(playerName, clanName));
            PlayerDataManager pdm = plugin.getPlayerDataManager();
            ConfigurationSection playerSection = pdm.getPlayerSection(playerUUID);
            playerSection.set("name", playerName);
            playerSection.set("clan", clanName);
        }
    }

    public List<UUID> getRecruitablePlayerUUIDs() {
        return allPlayers.entrySet().stream()
                .filter(entry -> entry.getValue().clanName() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<UUID> getAllPlayerUUIDs() {
        return new ArrayList<>(allPlayers.keySet());
    }

    private record RegisteredPlayerData(String name, String clanName) {
    }
}