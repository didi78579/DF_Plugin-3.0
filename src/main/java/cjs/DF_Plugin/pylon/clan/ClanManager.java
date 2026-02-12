package cjs.DF_Plugin.pylon.clan;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.command.clan.ClanUIManager;
import cjs.DF_Plugin.data.ClanDataManager;
import cjs.DF_Plugin.data.InventoryDataManager;
import cjs.DF_Plugin.events.rift.RiftManager;
import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.upgrade.item.UpgradeItems;
import cjs.DF_Plugin.util.PlayerTagManager;
import cjs.DF_Plugin.util.PluginUtils;
import cjs.DF_Plugin.world.enchant.MagicStone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ClanManager {

    private final DF_Main plugin;
    private final PlayerTagManager playerTagManager;
    private final ClanUIManager uiManager;

    // UI 세션 관리를 위한 맵
    private final Map<UUID, CreationSession> creationSessions = new HashMap<>();
    private final Map<UUID, String> deletionConfirmations = new HashMap<>();
    // Map<클랜이름 (소문자), Clan>
    private final Map<String, Clan> clans = new HashMap<>();
    // Map<플레이어UUID, Clan>
    private final Map<UUID, Clan> playerClanMap = new HashMap<>();
    // Map<클랜이름, Inventory>
    private final Map<String, Inventory> pylonStorages = new HashMap<>();
    // Map<클랜이름, Inventory> for gift boxes
    private final Map<String, Inventory> giftBoxInventories = new HashMap<>();
    // Map<클랜이름, PlayerUUID> for gift box viewers
    private final Map<String, UUID> giftBoxViewers = new ConcurrentHashMap<>();

    public ClanManager(DF_Main plugin) {
        this.plugin = plugin;
        this.playerTagManager = new PlayerTagManager(plugin, this);
        this.uiManager = new ClanUIManager();
        loadAllData();
    }

    public void saveAllData() {
        ClanDataManager cdm = plugin.getClanDataManager();
        InventoryDataManager idm = plugin.getInventoryDataManager();

        // 모든 클랜 정보 저장
        clans.values().forEach(clan -> {
            ConfigurationSection clanSection = cdm.getClanSection(clan.getName());
            clanSection.set("leader", clan.getLeader().toString());
            clanSection.set("color", clan.getColor().name());
            clanSection.set("members", clan.getMembers().stream().map(UUID::toString).collect(Collectors.toList()));
            clanSection.set("pending-first-spawn", clan.getPendingFirstSpawns().stream().map(UUID::toString).collect(Collectors.toList()));
            // Map<String, PylonType>을 저장하기 위해 Map<String, String>으로 변환
            Map<String, String> pylonData = clan.getPylonLocations().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name()));
            clanSection.set("pylon-locations", pylonData);
            clanSection.set("last-giftbox-time", clan.getLastGiftBoxTime());
            clanSection.set("last-pylon-recovery-time", clan.getLastPylonRecoveryTime());
            clanSection.set("last-sub-pylon-recovery-time", clan.getLastSubPylonRecoveryTime());
            clanSection.set("last-firework-time", clan.getLastFireworkTime());
        });

        // 모든 인벤토리 정보 저장
        pylonStorages.forEach((clanName, inv) -> idm.saveInventory(inv, "pylon_storage", clanName));
        giftBoxInventories.forEach((clanName, inv) -> idm.saveInventory(inv, "gift_box", clanName));
        plugin.getLogger().info("[가문 관리] " + clans.size() + "개의 가문 정보와 " + (pylonStorages.size() + giftBoxInventories.size()) + "개의 인벤토리를 저장했습니다.");
    }

    public void loadAllData() {
        clans.clear();
        playerClanMap.clear();

        ClanDataManager cdm = plugin.getClanDataManager();
        InventoryDataManager idm = plugin.getInventoryDataManager();
        ConfigurationSection clansSection = cdm.getClansSection();

        for (String clanName : clansSection.getKeys(false)) {
            ConfigurationSection clanSection = clansSection.getConfigurationSection(clanName);
            if (clanSection == null) continue;

            Clan clan = new Clan(clanName, UUID.fromString(clanSection.getString("leader")), ChatColor.valueOf(clanSection.getString("color", "WHITE")));
            clanSection.getStringList("members").forEach(uuidStr -> clan.addMember(UUID.fromString(uuidStr)));
            // pylon-locations 로드
            ConfigurationSection pylonSection = clanSection.getConfigurationSection("pylon-locations");
            if (pylonSection != null) {
                for (String locStr : pylonSection.getKeys(false)) {
                    PylonType type = PylonType.valueOf(pylonSection.getString(locStr, "MAIN_CORE"));
                    clan.addPylonLocation(locStr, type);
                }
            }
            List<String> pendingUuids = clanSection.getStringList("pending-first-spawn");
            if (pendingUuids != null) {
                pendingUuids.forEach(uuidStr -> clan.addPendingFirstSpawn(UUID.fromString(uuidStr)));
            }
            clan.setLastGiftBoxTime(clanSection.getLong("last-giftbox-time"));
            clan.setLastPylonRecoveryTime(clanSection.getLong("last-pylon-recovery-time"));
            clan.setLastSubPylonRecoveryTime(clanSection.getLong("last-sub-pylon-recovery-time"));
            clan.setLastFireworkTime(clanSection.getLong("last-firework-time"));

            clans.put(clanName.toLowerCase(), clan);
            clan.getMembers().forEach(memberId -> playerClanMap.put(memberId, clan));

            // 인벤토리 로드
            Inventory pylonStorage = Bukkit.createInventory(null, 54, clan.getFormattedName() + "§f의 파일런 창고");
            idm.loadInventory(pylonStorage, "pylon_storage", clanName);
            updatePylonStorageDynamicSlot(pylonStorage); // 서버 로드 시 동적 슬롯을 즉시 업데이트합니다.
            pylonStorages.put(clanName, pylonStorage);

            Inventory giftBox = Bukkit.createInventory(null, 27, clan.getFormattedName() + "§f의 선물상자");
            idm.loadInventory(giftBox, "gift_box", clanName);
            giftBoxInventories.put(clanName, giftBox);
        }
        plugin.getLogger().info("[가문 관리] ClanManager loaded with " + clans.size() + " clans.");
    }

    public Clan createClan(String name, Player leader, ChatColor color) {
        if (isNameTaken(name)) {
            leader.sendMessage("§c[가문] §f이미 사용 중인 가문 이름입니다.");
            return null;
        }

        if (isColorTaken(color)) {
            leader.sendMessage("§c[가문] §f이미 다른 가문이 사용 중인 색상입니다.");
            return null;
        }

        Clan clan = new Clan(name, leader.getUniqueId(), color);
        // clan.setLastGiftBoxTime(System.currentTimeMillis()); // 선물상자 타이머는 이제 GameStartManager에서 게임 시작 시 설정합니다.
        clans.put(name.toLowerCase(), clan);
        playerClanMap.put(leader.getUniqueId(), clan);

        // 게임 도중 가문 생성 시, 시작 지점을 할당하고 플레이어를 재접속시켜 해당 위치로 이동하게 합니다.
        if (plugin.getGameStartManager().isGameStarted()) {
            Location startLoc = plugin.getGameStartManager().getRandomSafeLocation(leader.getWorld());
            clan.setStartLocation(startLoc);
            // 리더가 다음 접속 시 시작 지점으로 이동하도록 초기 텔레포트 플래그를 리셋합니다.
            plugin.getPlayerDataManager().setInitialTeleportDone(leader.getUniqueId(), false);
            // 재접속을 유도하여 PlayerJoinListener 로직을 태웁니다.
            leader.kickPlayer("§a[가문] §a가문이 생성되었습니다. 재접속하여 시작 지점으로 이동해주세요.");
        }

        saveClanData(clan);
        return clan;
    }

    public void disbandClan(Clan clan) {
        clan.broadcastMessage("§a[가문] §f가문이 해체되었습니다.");
        deleteClan(clan);
    }

    private void deleteClan(Clan clan) {
        // This method centralizes the logic for removing a clan from the server.
        // It handles removing members from the map, deleting storage files, and cleaning up teams.

        // 1. Remove all members from the player-clan map and update their tags/registry.
        // A copy is used to prevent ConcurrentModificationException.
        Set<UUID> members = new HashSet<>(clan.getMembers());
        members.forEach(memberId -> {
            playerClanMap.remove(memberId);
            plugin.getPlayerConnectionManager().updatePlayerClan(memberId, null);
            Player player = Bukkit.getPlayer(memberId);
            if (player != null) {
                playerTagManager.removePlayerTag(player);
            }
        });

        // 2. Remove the clan from the main cache.
        clans.remove(clan.getName().toLowerCase());

        // 3. Delete the clan's data files (clan.yml, storage.yml, giftbox.yml).
        plugin.getClanDataManager().getClansSection().set(clan.getName(), null);
        plugin.getInventoryDataManager().getInventoriesSection("pylon_storage").set(clan.getName(), null);
        plugin.getInventoryDataManager().getInventoriesSection("gift_box").set(clan.getName(), null);

        // 4. Remove the clan's scoreboard team.
        playerTagManager.cleanupClanTeam(clan);

        // 5. Clean up in-memory inventories.
        pylonStorages.remove(clan.getName());
        giftBoxInventories.remove(clan.getName());
        giftBoxViewers.remove(clan.getName());
    }

    public void absorbClan(Clan attacker, Clan defender) {
        // Notify defender's members about absorption
        defender.broadcastMessage(PluginUtils.colorize("&c[전쟁] &f가문이 멸망하여 " + attacker.getFormattedName() + " §f가문에 흡수되었습니다."));

        // Announce to the entire server
        String publicMessage = PluginUtils.colorize("&4[전쟁] " + defender.getFormattedName() + " §c가문이 마지막 파일런을 잃고 " + attacker.getFormattedName() + " §c가문에게 흡수되었습니다!");
        Bukkit.broadcastMessage(publicMessage);

        // 패배한 가문의 모든 파일런 구조물과 보호 영역을 제거합니다.
        for (String locString : defender.getPylonLocations().keySet()) {
            Location pylonLoc = PluginUtils.deserializeLocation(locString);
            if (pylonLoc != null) {
                // 블록 자체를 공기로 만들어 아이템 드롭을 방지하고 즉시 제거합니다.
                pylonLoc.getBlock().setType(Material.AIR);
                // 주변 구조물(철 블록, 배리어)을 제거합니다.
                plugin.getPylonManager().getStructureManager().removeBaseAndBarrier(pylonLoc);
                // 보호 영역에서 제거합니다.
                plugin.getPylonManager().getAreaManager().removeProtectedPylon(pylonLoc);
            }
        }

        Set<UUID> membersToAbsorb = new HashSet<>(defender.getMembers());
        for (UUID memberUUID : membersToAbsorb) {
            attacker.addMember(memberUUID);
            plugin.getPlayerConnectionManager().updatePlayerClan(memberUUID, attacker);
            playerClanMap.put(memberUUID, attacker); // Update player's clan mapping
            Player onlineMember = Bukkit.getPlayer(memberUUID);
            if (onlineMember != null) {
                playerTagManager.updatePlayerTag(onlineMember); // Update scoreboard tag for online members
            }
        }

        // Save the state of the victorious clan
        saveClanData(attacker);

        // 패배한 가문의 멤버 목록을 비워서, deleteClan 메소드가
        // 흡수된 멤버들의 소속 정보를 잘못 제거하는 것을 방지합니다.
        defender.clearAllMembers();

        // 패배한 가문의 멤버 목록을 비워서, deleteClan 메소드가
        // 흡수된 멤버들의 소속 정보를 잘못 제거하는 것을 방지합니다.
        defender.clearAllMembers();

        // Remove the defeated clan from the system
        deleteClan(defender);
    }

    public void addPlayerToClan(Player player, Clan clan) {
        clan.addMember(player.getUniqueId());
        playerClanMap.put(player.getUniqueId(), clan);
        saveClanData(clan);
        playerTagManager.updatePlayerTag(player);
        plugin.getPlayerConnectionManager().updatePlayerClan(player.getUniqueId(), clan);
    }

    public void addMemberToClan(Clan clan, UUID memberUUID) {
        clan.addMember(memberUUID);
        playerClanMap.put(memberUUID, clan);
        saveClanData(clan);
        Player onlineMember = Bukkit.getPlayer(memberUUID);
        if (onlineMember != null) {
            playerTagManager.updatePlayerTag(onlineMember);
        }
        plugin.getPlayerConnectionManager().updatePlayerClan(memberUUID, clan);
    }

    public void removePlayerFromClan(Player player, Clan clan) {
        UUID memberUUID = player.getUniqueId();
        boolean wasLeader = clan.getLeader().equals(memberUUID);

        clan.removeMember(memberUUID);
        playerClanMap.remove(memberUUID);
        playerTagManager.removePlayerTag(player);
        plugin.getPlayerConnectionManager().updatePlayerClan(memberUUID, null);

        // 가문원이 한 명도 남지 않으면 가문을 해체합니다.
        if (clan.getMembers().isEmpty()) {
            Bukkit.broadcastMessage(PluginUtils.colorize("&b[가문] &f최후의 가문원 " + player.getName() + "님이 떠나, " + clan.getFormattedName() + "&f 가문이 해체되었습니다."));
            deleteClan(clan);
            return;
        }

        // 가문원이 남아있지만, 떠난 사람이 리더였다면 새로운 리더를 임명합니다.
        if (wasLeader) {
            clan.setLeader(clan.getMembers().iterator().next());
            clan.broadcastMessage("§e[가문] §e" + player.getName() + "님이 가문을 떠나, " + Bukkit.getOfflinePlayer(clan.getLeader()).getName() + "님이 새로운 대표가 되었습니다.");
        }

        // 변경된 가문 정보를 저장합니다.
        saveClanData(clan);
    }

    public void saveClanData(Clan clan) {
        ClanDataManager cdm = plugin.getClanDataManager();
        ConfigurationSection clanSection = cdm.getClanSection(clan.getName());
        clanSection.set("leader", clan.getLeader().toString());
        clanSection.set("color", clan.getColor().name());
        clanSection.set("members", clan.getMembers().stream().map(UUID::toString).collect(Collectors.toList()));
        // 새로 추가된 보류중인 첫 스폰 유저 목록도 저장합니다.
        clanSection.set("pending-first-spawn", clan.getPendingFirstSpawns().stream().map(UUID::toString).collect(Collectors.toList()));
        // Map<String, PylonType>을 저장하기 위해 Map<String, String>으로 변환
        Map<String, String> pylonData = clan.getPylonLocations().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name()));
        clanSection.set("pylon-locations", pylonData);
        clanSection.set("last-giftbox-time", clan.getLastGiftBoxTime());
        clanSection.set("last-pylon-recovery-time", clan.getLastPylonRecoveryTime());
        clanSection.set("last-sub-pylon-recovery-time", clan.getLastSubPylonRecoveryTime());
        clanSection.set("last-firework-time", clan.getLastFireworkTime());
        cdm.saveConfig(); // 개별 저장은 즉시 파일에 반영
    }

    public Clan getClanByName(String name) { return clans.get(name.toLowerCase()); }
    public Clan getClanByPlayer(UUID uuid) { return playerClanMap.get(uuid); }
    public PlayerTagManager getPlayerTagManager() { return playerTagManager; }

    public int getMaxMembers() {
        return plugin.getGameConfigManager().getConfig().getInt("pylon.recruitment.max-members", 4);
    }

    /**
     * 파일런 위치 문자열로 해당 파일런을 소유한 클랜을 찾습니다.
     * @param locationStr 직렬화된 위치 문자열
     * @return 해당 위치에 파일런이 있는 클랜 (Optional)
     */
    public Optional<Clan> getClanByPylonLocation(String locationStr) {
        return clans.values().stream()
                .filter(clan -> clan.getPylonLocations().containsKey(locationStr))
                .findFirst();
    }
    public Collection<Clan> getClans() { return clans.values(); }

    public Collection<Clan> getAllClans() {
        return clans.values();
    }

    /**
     * 관리자가 플레이어를 특정 클랜에 강제로 가입시킵니다.
     * @param player 대상 플레이어
     * @param clanName 가입시킬 클랜 이름
     */
    public void forceJoinClan(Player player, String clanName) {
        Clan clanToJoin = getClanByName(clanName);
        if (clanToJoin == null) {
            plugin.getLogger().warning("[가문 관리] Admin command failed: Clan '" + clanName + "' not found.");
            return;
        }
        // 플레이어가 이미 다른 클랜에 있다면, 먼저 탈퇴시킵니다.
        Clan currentClan = getClanByPlayer(player.getUniqueId());
        if (currentClan != null) {
            removePlayerFromClan(player, currentClan);
        }
        addPlayerToClan(player, clanToJoin);
        player.sendMessage("§a[가문] §a관리자에 의해 " + clanToJoin.getDisplayName() + " §a클랜에 강제 가입되었습니다.");
    }

    /**
     * 관리자가 플레이어를 현재 소속된 클랜에서 강제로 탈퇴시킵니다.
     * @param player 대상 플레이어
     */
    public void forceRemovePlayerFromClan(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        // 중요: 제거 대상이 리더인 경우, 리더 없는 클랜이 생기는 것을 방지하기 위해 클랜을 해체합니다.
        if (clan.getLeader().equals(player.getUniqueId())) {
            disbandClan(clan);
        } else {
            // 일반 멤버는 기존의 탈퇴 로직을 따릅니다.
            removePlayerFromClan(player, clan);
        }
    }

    /**
     * 클랜 이름으로 클랜을 찾거나, 없으면 새로 생성하여 반환합니다.
     * 이 메서드는 주로 서버 시작 시 데이터를 불러올 때 사용됩니다.
     * @param name 클랜 이름
     * @param ownerUUID 리더의 UUID
     * @param colorString 색상 코드 문자열 (예: "§a")
     * @return 찾거나 생성된 Clan 객체
     */
    public Clan getOrCreateClan(String name, UUID ownerUUID, String colorString) {
        Clan existingClan = getClanByName(name);
        if (existingClan != null) {
            return existingClan;
        }
        ChatColor color = (colorString != null && colorString.length() > 1) ? ChatColor.getByChar(colorString.charAt(1)) : ChatColor.WHITE;
        Clan newClan = new Clan(name, ownerUUID, color);
        clans.put(name.toLowerCase(), newClan);
        playerClanMap.put(ownerUUID, newClan);
        return newClan;
    }

    public List<String> getClanNames() {
        return clans.values().stream().map(Clan::getName).collect(Collectors.toList());
    }

    /**
     * 파일런 파괴 시 후속 처리를 담당합니다.
     * 멀티코어 비활성화 시 적에 의해 파괴되면 가문 흡수가 발생할 수 있습니다.
     * @param defender 파괴된 파일런의 소유 가문
     * @param destroyer 파괴한 플레이어 (null일 수 있음)
     * @return 흡수가 발생하여 후속 처리가 필요 없는 경우 true, 아니면 false
     */
    public boolean handlePylonLoss(Clan defender, Player destroyer) {
        boolean multiCoreEnabled = plugin.getGameConfigManager().getConfig().getBoolean("pylon.features.multi-core", false);

        // 멀티코어 비활성화 상태이고, 파괴자가 있으며, 다른 가문 소속일 경우
        if (!multiCoreEnabled && destroyer != null) {
            Clan attacker = getClanByPlayer(destroyer.getUniqueId());
            if (attacker != null && !attacker.equals(defender)) {
                // 마지막 파일런이 파괴되었으므로 가문을 흡수합니다.
                absorbClan(attacker, defender);
                return true; // 흡수 처리 완료, 추가적인 파괴 로직 불필요
            }
        }
        return false; // 일반 파괴 처리 필요
    }

    public boolean isNameTaken(String name) {
        return clans.containsKey(name.toLowerCase());
    }

    public boolean isColorTaken(ChatColor color) {
        return clans.values().stream().anyMatch(clan -> clan.getColor() == color);
    }

    public List<ChatColor> getAvailableColors() {
        Set<ChatColor> usedColors = clans.values().stream()
                .map(Clan::getColor)
                .collect(Collectors.toSet());

        return Arrays.stream(ChatColor.values())
                .filter(ChatColor::isColor)
                .filter(c -> c != ChatColor.BLACK && c != ChatColor.DARK_GRAY && c != ChatColor.GRAY && c != ChatColor.WHITE)
                .filter(c -> !usedColors.contains(c))
                .collect(Collectors.toList());
    }

    /**
     * 플레이어의 클랜 파일런 창고를 엽니다.
     * 창고가 메모리에 없으면 스토리지에서 불러옵니다.
     * @param player 창고를 열 플레이어
     */
    public void openPylonStorage(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§c[파일런] §c파일런 창고를 열려면 가문에 소속되어 있어야 합니다.");
            return;
        }

        Inventory storage = getPylonStorage(clan); // 먼저 인벤토리를 가져오거나 로드합니다.
        updatePylonStorageDynamicSlot(storage); // 인벤토리를 열기 직전에 동적 슬롯을 업데이트합니다.
        player.openInventory(storage); // 업데이트된 인벤토리를 엽니다.
    }

    /**
     * 특정 클랜의 파일런 창고 인벤토리를 가져옵니다.
     * 창고가 메모리에 없으면 스토리지에서 불러옵니다.
     * @param clan 창고를 가져올 클랜
     * @return 클랜의 파일런 창고 인벤토리, 클랜이 null이면 null
     */
    public Inventory getPylonStorage(Clan clan) {
        if (clan == null) return null;
        return pylonStorages.computeIfAbsent(clan.getName(), clanName -> {
            Inventory newStorage = Bukkit.createInventory(null, 54, clan.getFormattedName() + "§f의 파일런 창고");
            plugin.getInventoryDataManager().loadInventory(newStorage, "pylon_storage", clanName); // 파일에서 로드
            // 로드 시 배리어/나침반 설정은 openPylonStorage에서 동적으로 처리하므로 여기서는 호출하지 않습니다.
            return newStorage;
        });
    }

    /**
     * 모든 파일런 창고의 동적 슬롯을 현재 이벤트 상태에 맞게 업데이트합니다.
     */
    public void updateAllPylonStoragesDynamicSlot() {
        pylonStorages.values().forEach(this::updatePylonStorageDynamicSlot);
    }

    /**
     * 지정된 파일런 창고의 동적 슬롯(54번째)을 현재 이벤트 상태에 따라 나침반 또는 배리어로 설정합니다.
     * @param storage 업데이트할 창고 인벤토리
     */
    public void updatePylonStorageDynamicSlot(Inventory storage) {
        if (storage == null || storage.getSize() != 54) return;

        RiftManager riftManager = plugin.getRiftManager();
        if (riftManager != null && riftManager.isEventActive()) {
            Location altarLocation = riftManager.getAltarLocation();
            if (altarLocation != null) {
                ItemStack compass = new ItemStack(Material.COMPASS);
                ItemMeta meta = compass.getItemMeta();
                if (meta instanceof CompassMeta compassMeta) {
                    compassMeta.setDisplayName("§d균열의 나침반");
                    compassMeta.setLore(Arrays.asList(
                            "§7차원의 균열 위치를 가리킵니다.",
                            "§c[주의] §7나침반은 제단과 같은 차원(오버월드)에서만 작동합니다.",
                            "§e클릭하여 나침반을 획득하세요."
                    ));
                    Location lodestoneLocation = altarLocation.clone().subtract(0, 4, 0);
                    compassMeta.setLodestone(lodestoneLocation);
                    compassMeta.setLodestoneTracked(true);
                    compass.setItemMeta(compassMeta);
                }
                storage.setItem(53, compass);
                return; // 나침반 설정 후 종료
            }
        }
        // 이벤트가 비활성이거나, 어떤 이유로든 나침반을 설정할 수 없는 경우 배리어를 설정합니다.
        setBarrierInStorage(storage);
    }

    public Map<String, Inventory> getPylonStorages() {
        return pylonStorages;
    }

    public Inventory getGiftBoxInventory(Clan clan) {
        return giftBoxInventories.computeIfAbsent(clan.getName(), k -> {
            Inventory newGiftBox = Bukkit.createInventory(null, 27, clan.getFormattedName() + "§f의 선물상자");
            plugin.getInventoryDataManager().loadInventory(newGiftBox, "gift_box", clan.getName());
            return newGiftBox;
        });
    }

    /**
     * 모든 가문의 선물상자를 리필합니다.
     * 이 메서드는 GiftBoxRefillTask에 의해 호출됩니다.
     * @param refillTime 리필이 실행된 시간 (모든 가문에 동일하게 적용)
     */
    public void refillAllGiftBoxes(long refillTime) {
        FileConfiguration config = plugin.getGameConfigManager().getConfig();
        int minTotalItems = config.getInt("pylon.giftbox.min-reward-items", 1);
        int maxTotalItems = config.getInt("pylon.giftbox.max-reward-items", 10);
        ClanDataManager cdm = plugin.getClanDataManager(); // 데이터 매니저 가져오기

        for (Clan clan : getAllClans()) {
            Inventory giftBox = getGiftBoxInventory(clan);

            // 1. 총 아이템 개수를 무작위로 결정합니다.
            int totalItemCount = (maxTotalItems > minTotalItems) ? ThreadLocalRandom.current().nextInt(minTotalItems, maxTotalItems + 1) : minTotalItems;

            // 2. 총 개수를 강화석과 마석으로 무작위 분배합니다.
            int upgradeStoneAmount = (totalItemCount > 0) ? ThreadLocalRandom.current().nextInt(0, totalItemCount + 1) : 0;
            int magicStoneAmount = totalItemCount - upgradeStoneAmount;

            // 3. 추가할 아이템 목록을 1개 단위로 만듭니다.
            List<ItemStack> itemsToAdd = new ArrayList<>();
            if (upgradeStoneAmount > 0) {
                for (int i = 0; i < upgradeStoneAmount; i++) {
                    itemsToAdd.add(UpgradeItems.createUpgradeStone(1));
                }
            }
            if (magicStoneAmount > 0) {
                for (int i = 0; i < magicStoneAmount; i++) {
                    itemsToAdd.add(MagicStone.createMagicStone(1));
                }
            }

            // 4. 아이템을 인벤토리에 추가하고, 추가하지 못한 아이템이 있는지 확인합니다.
            // inventory.addItem()은 자동으로 빈 슬롯이나 기존 스택에 아이템을 추가합니다.
            boolean couldNotFitAll = false;
            if (!itemsToAdd.isEmpty()) {
                for (ItemStack itemToAdd : itemsToAdd) {
                    // 1. 아이템을 추가할 수 있는 모든 슬롯(빈 슬롯 + 스택 가능한 슬롯)의 목록을 만듭니다.
                    List<Integer> availableSlots = new ArrayList<>();
                    for (int i = 0; i < giftBox.getSize(); i++) {
                        ItemStack existingItem = giftBox.getItem(i);
                        if (existingItem == null) {
                            availableSlots.add(i); // 빈 슬롯
                        } else if (existingItem.isSimilar(itemToAdd) && existingItem.getAmount() < existingItem.getMaxStackSize()) {
                            availableSlots.add(i); // 스택 가능한 슬롯
                        }
                    }

                    // 2. 추가할 슬롯이 없으면, 인벤토리가 가득 찬 것입니다.
                    if (availableSlots.isEmpty()) {
                        couldNotFitAll = true;
                        break; // 더 이상 아이템을 추가할 수 없으므로 중단
                    }

                    // 3. 가능한 슬롯 중 하나를 무작위로 선택합니다.
                    int chosenSlot = availableSlots.get(ThreadLocalRandom.current().nextInt(availableSlots.size()));

                    // 4. 선택된 슬롯에 아이템을 추가합니다.
                    ItemStack targetSlotItem = giftBox.getItem(chosenSlot);
                    if (targetSlotItem == null) {
                        giftBox.setItem(chosenSlot, itemToAdd);
                    } else {
                        targetSlotItem.setAmount(targetSlotItem.getAmount() + itemToAdd.getAmount());
                    }
                }
            }

            // 선물상자 쿨다운 타이머가 초기화되도록 마지막 리필 시간을 갱신합니다.
            clan.setLastGiftBoxTime(refillTime);
            // 설정 파일 객체에도 즉시 반영합니다.
            cdm.getClanSection(clan.getName()).set("last-giftbox-time", refillTime);

            if (couldNotFitAll) {
                clan.broadcastMessage(PluginUtils.colorize("&c[선물상자] &f선물상자가 가득 차서 일부 또는 모든 선물을 받지 못했습니다!"));
            } else {
                clan.broadcastMessage(PluginUtils.colorize("&d[선물상자] &f가문의 선물상자에 새로운 선물이 도착했습니다!"));
            }
        }

        // 모든 선물상자 리필 후, 변경된 인벤토리 데이터를 즉시 파일에 저장하여 데이터 유실을 방지합니다.
        plugin.getInventoryDataManager().saveConfig();
        // 변경된 클랜 데이터(lastGiftBoxTime)도 파일에 저장합니다.
        cdm.saveConfig();
    }

    /**
     * 지정된 인벤토리의 마지막 칸에 시스템 슬롯(배리어)을 설정합니다.
     * @param storage 대상 인벤토리
     */
    public void setBarrierInStorage(Inventory storage) {
        if (storage == null || storage.getSize() != 54) return;

        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        meta.setDisplayName("§c[시스템 슬롯]");
        meta.setLore(Collections.singletonList("§7이 슬롯은 사용할 수 없습니다."));
        barrier.setItemMeta(meta);

        storage.setItem(53, barrier);
    }

    public Map<String, Inventory> getGiftBoxInventories() {
        return giftBoxInventories;
    }

    /**
     * 선물상자가 다른 플레이어에 의해 사용 중인지 확인합니다.
     * @param clan 확인할 가문
     * @return 사용 중이면 true
     */
    public boolean isGiftBoxInUse(Clan clan) {
        return giftBoxViewers.containsKey(clan.getName());
    }

    /**
     * 선물상자를 보고 있는 플레이어의 UUID를 가져옵니다.
     * @param clan 확인할 가문
     * @return 보고 있는 플레이어의 UUID, 없으면 null
     */
    public UUID getGiftBoxViewer(Clan clan) {
        return giftBoxViewers.get(clan.getName());
    }

    /**
     * 선물상자를 열거나 닫은 플레이어를 기록합니다.
     * @param clan 대상 가문
     * @param playerUUID 연 플레이어의 UUID, 닫았을 경우 null
     */
    public void setGiftBoxViewer(Clan clan, UUID playerUUID) {
        if (playerUUID == null) {
            giftBoxViewers.remove(clan.getName());
        } else {
            giftBoxViewers.put(clan.getName(), playerUUID);
        }
    }
    // --- UI Session Management ---

    public CreationSession startCreationSession(Player player) {
        List<ChatColor> availableColors = getAvailableColors();
        if (availableColors.isEmpty()) {
            return null;
        }
        CreationSession session = new CreationSession(availableColors);
        creationSessions.put(player.getUniqueId(), session);
        return session;
    }

    public CreationSession getCreationSession(Player player) {
        return creationSessions.get(player.getUniqueId());
    }

    public void endCreationSession(Player player) {
        creationSessions.remove(player.getUniqueId());
    }

    public void requestDeletion(Player player, Clan clan) {
        deletionConfirmations.put(player.getUniqueId(), clan.getName());
    }

    public String getDeletionConfirmation(Player player) {
        return deletionConfirmations.get(player.getUniqueId());
    }

    public void clearDeletionConfirmation(Player player) {
        deletionConfirmations.remove(player.getUniqueId());
    }

    public static class CreationSession {
        public String name;
        public ChatColor color;
        private int colorIndex = 0;
        private List<ChatColor> availableColors;

        public CreationSession(List<ChatColor> availableColors) {
            this.availableColors = availableColors;
            this.color = this.availableColors.isEmpty() ? ChatColor.WHITE : this.availableColors.get(0);
        }

        public void nextColor() {
            if (availableColors.isEmpty()) return;
            colorIndex = (colorIndex + 1) % availableColors.size();
            color = availableColors.get(colorIndex);
        }

        public void prevColor() {
            if (availableColors.isEmpty()) return;
            colorIndex = (colorIndex - 1 + availableColors.size()) % availableColors.size();
            color = availableColors.get(colorIndex);
        }

        public void refreshAvailableColors(List<ChatColor> newAvailableColors) {
            this.availableColors = newAvailableColors;

            if (this.availableColors.isEmpty()) {
                this.color = ChatColor.WHITE;
                this.colorIndex = -1;
                return;
            }

            if (!this.availableColors.contains(this.color)) {
                this.colorIndex = 0;
                this.color = this.availableColors.get(0);
            } else {
                this.colorIndex = this.availableColors.indexOf(this.color);
            }
        }
    }

    public ClanUIManager getUiManager() {
        return uiManager;
    }
}