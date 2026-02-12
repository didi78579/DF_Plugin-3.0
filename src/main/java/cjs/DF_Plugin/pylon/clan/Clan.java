package cjs.DF_Plugin.pylon.clan;

import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class Clan {
    private final String name;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private ChatColor color;
    // 파일런 위치와 타입을 저장하는 맵
    private final Map<String, PylonType> pylonLocations = new HashMap<>();

    // 각종 쿨다운 타임스탬프
    private long lastGiftBoxTime;
    private long lastPylonRecoveryTime; // 주 파일런 회수
    private long lastSubPylonRecoveryTime; // 보조 파일런 회수
    private long lastFireworkTime; // 정찰용 폭죽

    // 새로 영입된 후 첫 접속을 기다리는 멤버 목록
    private final Set<UUID> pendingFirstSpawn = new HashSet<>();

    // 게임 시작 시 임시 부활 지점
    private Location startLocation;

    public Clan(String name, UUID leader, ChatColor color) {
        this.name = name;
        this.leader = leader;
        this.color = color;
        this.members.add(leader);
    }

    // --- Basic Info ---
    public String getName() { return name; }
    public String getDisplayName() { return color + name; }
    public String getFormattedName() { return color + "[" + name + "]"; }

    // --- Leader ---
    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }

    // --- Members ---
    public Set<UUID> getMembers() { return members; }
    public void addMember(UUID member) { members.add(member); }
    public void removeMember(UUID member) { members.remove(member); }
    public void clearAllMembers() { members.clear(); }

    // --- Color ---
    public ChatColor getColor() { return color; }
    public void setColor(ChatColor color) { this.color = color; }

    // --- Pylon Management ---
    public Map<String, PylonType> getPylonLocations() { return pylonLocations; }
    public void addPylonLocation(String locationStr, PylonType type) { pylonLocations.put(locationStr, type); }
    public void removePylonLocation(String locationStr) { pylonLocations.remove(locationStr); }
    public void clearPylonLocations() { pylonLocations.clear(); }
    public PylonType getPylonType(String locationStr) { return pylonLocations.get(locationStr); }
    public boolean hasMainPylon() { return pylonLocations.containsValue(PylonType.MAIN_CORE); }
    public boolean hasAuxiliaryPylons() { return pylonLocations.containsValue(PylonType.AUXILIARY); }

    public Optional<Location> getMainPylonLocationObject() {
        return pylonLocations.entrySet().stream()
                .filter(entry -> entry.getValue() == PylonType.MAIN_CORE)
                .map(Map.Entry::getKey)
                .findFirst()
                .map(PluginUtils::deserializeLocation);
    }

    // --- Cooldowns ---
    public long getLastGiftBoxTime() { return lastGiftBoxTime; }
    public void setLastGiftBoxTime(long lastGiftBoxTime) { this.lastGiftBoxTime = lastGiftBoxTime; }

    public long getLastPylonRecoveryTime() { return lastPylonRecoveryTime; }
    public void setLastPylonRecoveryTime(long lastPylonRecoveryTime) { this.lastPylonRecoveryTime = lastPylonRecoveryTime; }

    public long getLastSubPylonRecoveryTime() { return lastSubPylonRecoveryTime; }
    public void setLastSubPylonRecoveryTime(long lastSubPylonRecoveryTime) { this.lastSubPylonRecoveryTime = lastSubPylonRecoveryTime; }

    // --- First Spawn Management ---
    public Set<UUID> getPendingFirstSpawns() { return pendingFirstSpawn; }
    public boolean isPendingFirstSpawn(UUID uuid) { return pendingFirstSpawn.contains(uuid); }
    public void addPendingFirstSpawn(UUID uuid) { pendingFirstSpawn.add(uuid); }
    public void removePendingFirstSpawn(UUID uuid) { pendingFirstSpawn.remove(uuid); }

    public long getLastFireworkTime() { return lastFireworkTime; }
    public void setLastFireworkTime(long lastFireworkTime) { this.lastFireworkTime = lastFireworkTime; }

    // Legacy support for retrieval cooldowns
    public long getLastRetrievalTime() { return getLastPylonRecoveryTime(); }
    public void setLastRetrievalTime(long time) { setLastPylonRecoveryTime(time); }

    public long getLastAuxiliaryRetrievalTime() { return getLastSubPylonRecoveryTime(); }
    public void setLastAuxiliaryRetrievalTime(long time) { setLastSubPylonRecoveryTime(time); }

    // Legacy support for recon firework
    public long getLastReconFireworkTime() { return getLastFireworkTime(); }
    public void setLastReconFireworkTime(long time) { setLastFireworkTime(time); }

    // --- Utility ---

    public Location getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(Location startLocation) { this.startLocation = startLocation; }
    public void broadcastMessage(String message) {
        for (UUID memberId : members) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }
}