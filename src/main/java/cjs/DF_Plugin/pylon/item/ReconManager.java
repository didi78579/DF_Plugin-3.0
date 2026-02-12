package cjs.DF_Plugin.pylon.item;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ReconManager implements Listener {

    private enum ReconState {
        READY_TO_LAUNCH,
        IN_AIR,
        LANDED
    }

    private final DF_Main plugin;
    private final Map<UUID, ReconState> reconPlayers = new ConcurrentHashMap<>(); // Leader UUID -> State
    private final Map<UUID, List<UUID>> reconParties = new ConcurrentHashMap<>(); // Leader UUID -> List of all party members' UUIDs
    private final Map<UUID, ItemStack> originalChestplates = new ConcurrentHashMap<>(); // Player UUID -> Original Chestplate
    private final Map<UUID, Long> returnTimers = new ConcurrentHashMap<>(); // Leader UUID -> Teleport Timestamp
    private static final String PREFIX = PluginUtils.colorize("&c[정찰] &f");

    public ReconManager(DF_Main plugin) {
        this.plugin = plugin;
        startReturnTask();
    }

    public void activateRecon(Player player) {
        GameConfigManager config = plugin.getGameConfigManager();

        // 1. Check if feature is enabled
        if (!config.getConfig().getBoolean("pylon.recon-firework.enabled", true)) {
            player.sendMessage(PREFIX + "정찰용 폭죽 기능이 비활성화되어 있습니다.");
            return;
        }
        // 2. Check if player is a clan leader
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null || !clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "가문 대표만 정찰용 폭죽을 사용할 수 있습니다.");
            return;
        }

        // 3. Check cooldown
        long cooldownMillis = TimeUnit.HOURS.toMillis(config.getConfig().getInt("pylon.recon-firework.cooldown-hours", 12));
        long lastUsed = clan.getLastFireworkTime();
        if (System.currentTimeMillis() - lastUsed < cooldownMillis) {
            long remainingMillis = cooldownMillis - (System.currentTimeMillis() - lastUsed);
            String remainingTime = String.format("%02d시간 %02d분",
                    TimeUnit.MILLISECONDS.toHours(remainingMillis),
                    TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60);
            player.sendMessage(PREFIX + "다음 정찰까지 " + remainingTime + " 남았습니다.");
            return;
        }

        // All checks passed, activate recon mode
        player.closeInventory();

        // [NEW] 정찰 모드 활성화 시 즉시 겉날개를 장착합니다.
        ItemStack originalChestplate = player.getInventory().getChestplate();
        if (originalChestplate != null && !originalChestplate.getType().isAir()) {
            originalChestplates.put(player.getUniqueId(), originalChestplate.clone());
        }
        player.getInventory().setChestplate(createReconElytra());

        clan.setLastFireworkTime(System.currentTimeMillis());
        plugin.getClanManager().saveClanData(clan);
        reconPlayers.put(player.getUniqueId(), ReconState.READY_TO_LAUNCH);

        player.sendMessage(PREFIX + "정찰 모드가 활성화되었습니다. 점프하여 발사하세요!");
    }

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!reconPlayers.containsKey(player.getUniqueId()) || reconPlayers.get(player.getUniqueId()) != ReconState.READY_TO_LAUNCH) {
            return;
        }

        // PlayerJumpEvent는 점프 시 한 번만 발생하므로, 복잡한 속도/거리 체크가 필요 없습니다.
        // 또한, launchPlayer가 중복 호출되는 것을 근본적으로 방지하여 NullPointerException을 해결합니다.
        launchPlayer(player);
    }

    private void launchPlayer(Player leader) {
        reconPlayers.put(leader.getUniqueId(), ReconState.IN_AIR);

        Clan clan = plugin.getClanManager().getClanByPlayer(leader.getUniqueId());
        if (clan == null) return;

        List<Player> party = new ArrayList<>();
        party.add(leader);

        // 반경 5블록 내의 온라인 상태인 같은 가문원 찾기
        final double radiusSquared = 5 * 5;
        clan.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline() && !p.equals(leader))
                .filter(p -> p.getWorld().equals(leader.getWorld()) && p.getLocation().distanceSquared(leader.getLocation()) <= radiusSquared)
                .forEach(party::add);

        // 2. 파티원들을 리더 위에 태워 플레이어 타워를 만듭니다.
        Player vehicle = leader; // 스택의 가장 아래는 항상 리더입니다.
        for (Player member : party) {
            reconPlayers.put(member.getUniqueId(), ReconState.IN_AIR);

            // 리더(자신)가 아닌 멤버들만 태웁니다.
            if (!member.equals(leader)) {
                vehicle.addPassenger(member);
                vehicle = member; // 다음 플레이어가 탈 수 있도록 vehicle을 현재 멤버로 업데이트
            }
        }

        // 3. 리더(스택의 가장 아래)에게만 발사 효과를 적용합니다.
        leader.getWorld().playSound(leader.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 1.0f);
        leader.setVelocity(new org.bukkit.util.Vector(0, 4, 0));

        // 4. 모든 파티원을 활공 상태로 만듭니다.
        new BukkitRunnable() {
            @Override
            public void run() {
                party.stream()
                        .filter(p -> p.isOnline() && !p.isOnGround())
                        .forEach(p -> p.setGliding(true));
            }
        }.runTaskLater(plugin, 5L); // 약간의 지연을 주어 안정적으로 활공 상태가 되도록 합니다.

        party.forEach(p -> p.sendMessage(PREFIX + "발사!"));

        if (party.size() > 1) {
            List<UUID> partyUUIDs = party.stream().map(Player::getUniqueId).toList();
            reconParties.put(leader.getUniqueId(), partyUUIDs);
        }
    }

    @EventHandler
    public void onPlayerLand(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!reconPlayers.containsKey(player.getUniqueId()) || reconPlayers.get(player.getUniqueId()) != ReconState.IN_AIR) {
            return;
        }

        // 착지 감지
        if (player.isOnGround() && event.getTo().getY() <= event.getFrom().getY()) {
            // 착지한 플레이어가 파티의 리더인 경우
            if (reconParties.containsKey(player.getUniqueId())) {
                handlePartyLanding(player);
            } else { // 일반 참여자인 경우
                handlePassengerLanding(player);
            }
        }
    }

    /**
     * 정찰대 리더가 착지했을 때 호출됩니다. 파티원 전체의 귀환 타이머를 시작합니다.
     * @param leader 착지한 리더
     */
    private void handlePartyLanding(Player leader) {
        List<UUID> partyUUIDs = reconParties.remove(leader.getUniqueId());
        if (partyUUIDs == null) return; // 이미 처리된 경우

        // 플레이어 타워를 해체합니다.
        dismountStack(leader);

        int returnMinutes = plugin.getGameConfigManager().getConfig().getInt("pylon.recon-firework.return-duration-minutes", 1);

        for (UUID memberUUID : partyUUIDs) {
            reconPlayers.put(memberUUID, ReconState.LANDED);
            long returnTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(returnMinutes);
            returnTimers.put(memberUUID, returnTime);

            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) {
                restoreChestplate(member); // 기존 갑옷 복원
                member.sendMessage(PREFIX + "착지했습니다. " + returnMinutes + "분 후에 파일런으로 귀환합니다.");
            }
        }
    }

    /**
     * 정찰대 참여자(리더 제외)가 착지했을 때 호출됩니다.
     * @param passenger 착지한 참여자
     */
    private void handlePassengerLanding(Player passenger) {
        reconPlayers.put(passenger.getUniqueId(), ReconState.LANDED);
        restoreChestplate(passenger); // 기존 갑옷 복원
        passenger.sendMessage(PREFIX + "착지했습니다. 가문 대표가 착지하면 귀환 타이머가 시작됩니다.");
    }

    /**
     * 재귀적으로 엔티티에 탑승한 모든 플레이어를 내리게 합니다.
     * @param vehicle 탑승 스택의 가장 아래 엔티티
     */
    private void dismountStack(Entity vehicle) {
        if (vehicle == null || vehicle.getPassengers().isEmpty()) {
            return;
        }

        // ConcurrentModificationException을 피하기 위해 리스트 복사
        List<Entity> passengers = new ArrayList<>(vehicle.getPassengers());
        for (Entity passenger : passengers) {
            dismountStack(passenger); // 스택의 더 위에 있는 승객부터 내리게 함
            vehicle.removePassenger(passenger);
        }
    }

    private void restoreChestplate(Player player) {
        ItemStack original = originalChestplates.remove(player.getUniqueId());
        player.getInventory().setChestplate(original); // original이 null일 수 있으며, 이는 정상입니다.
    }

    /**
     * 정찰 임무 중이던 플레이어가 재접속하면 즉시 파일런으로 귀환시킵니다.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (reconPlayers.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + "정찰 임무 중 재접속하여 파일런으로 즉시 귀환합니다.");
            teleportAndEndRecon(player);
        }
    }

    /**
     * 정찰 임무 중인 플레이어, 특히 리더가 접속을 종료했을 때의 상황을 처리합니다.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // 만약 접속 종료한 플레이어가 정찰대의 리더였다면, 남은 파티원들의 귀환 절차를 즉시 시작합니다.
        if (reconParties.containsKey(playerUUID)) {
            List<UUID> partyUUIDs = reconParties.remove(playerUUID);
            if (partyUUIDs == null) return;

            int returnMinutes = plugin.getGameConfigManager().getConfig().getInt("pylon.recon-firework.return-duration-minutes", 1);
            long returnTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(returnMinutes);

            for (UUID memberUUID : partyUUIDs) {
                if (memberUUID.equals(playerUUID)) continue; // 방금 나간 리더는 제외

                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    reconPlayers.put(memberUUID, ReconState.LANDED);
                    returnTimers.put(memberUUID, returnTime);
                    restoreChestplate(member);
                    dismountStack(member);
                    member.setGliding(false);
                    member.sendMessage(PREFIX + "§c정찰대장이 연결을 종료하여 귀환 절차를 시작합니다.");
                    member.sendMessage(PREFIX + returnMinutes + "분 후에 파일런으로 귀환합니다.");
                }
            }
        }
    }

    /**
     * 플레이어의 정찰 상태를 완전히 종료하고 정리합니다.
     * @param player 대상 플레이어
     */
    private void endReconForPlayer(Player player) {
        if (player == null) return;
        UUID playerUUID = player.getUniqueId();

        // 겉날개를 원래 갑옷으로 복원합니다.
        restoreChestplate(player);

        // 모든 관련 상태 맵에서 플레이어를 제거합니다.
        reconPlayers.remove(playerUUID);
        returnTimers.remove(playerUUID);

        // 만약 플레이어가 파티의 리더였다면, 파티 정보도 정리합니다.
        if (reconParties.containsKey(playerUUID)) {
            reconParties.remove(playerUUID);
        }
    }

    /**
     * 플레이어를 안전한 파일런 위치로 귀환시키고, 정찰 상태를 완전히 종료합니다.
     * @param player 귀환시킬 플레이어
     */
    private void teleportAndEndRecon(Player player) {
        if (player == null || !player.isOnline()) return;

        // 1. 안전한 위치로 텔레포트
        Location safeLocation = plugin.getPlayerRespawnListener().getCustomRespawnLocation(player);
        if (safeLocation != null) {
            player.teleport(safeLocation);
        } else {
            plugin.getWorldManager().teleportPlayerToSafety(player);
        }

        // 2. 정찰 상태 완전 종료
        endReconForPlayer(player);
    }

    private void startReturnTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (returnTimers.isEmpty()) return;

                long now = System.currentTimeMillis();
                List<UUID> expiredUUIDs = new ArrayList<>();

                // 수정 중 예외를 피하기 위해 만료된 UUID를 먼저 수집합니다.
                returnTimers.forEach((uuid, time) -> {
                    if (now >= time) {
                        expiredUUIDs.add(uuid);
                    }
                });

                // 수집된 UUID를 기반으로 귀환 및 정리 작업을 수행합니다.
                for (UUID uuid : expiredUUIDs) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(PREFIX + "정찰 시간이 만료되어 파일런으로 귀환합니다.");
                        teleportAndEndRecon(player); // 텔레포트 및 모든 상태 정리
                    } else {
                        // 플레이어가 오프라인이면, 타이머만 제거합니다.
                        // 재접속 시 onPlayerJoin 이벤트가 처리합니다.
                        returnTimers.remove(uuid);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 확인
    }

    private ItemStack createReconElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b정찰용 겉날개");
            meta.setLore(Collections.singletonList("§7하늘을 정찰하기 위한 특수 겉날개."));
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            elytra.setItemMeta(meta);
        }
        return elytra;
    }
}