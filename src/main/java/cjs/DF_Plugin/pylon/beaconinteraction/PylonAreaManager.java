package cjs.DF_Plugin.pylon.beaconinteraction;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.Map.Entry;

public class PylonAreaManager {
    private final DF_Main plugin;
    private final GameConfigManager configManager;
    // Map<Serialized Location, Clan>
    private final Map<String, Clan> protectedPylons = new HashMap<>();
    // Map<Serialized Pylon Location, Set<Intruder UUID>>
    private final Map<String, Set<UUID>> intruderTracker = new HashMap<>();
    private BukkitTask particleTask;

    public PylonAreaManager(DF_Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getGameConfigManager();
        startParticleTask();
    }

    public void addProtectedPylon(Location location, Clan clan) {
        String locString = PluginUtils.serializeLocation(location);
        protectedPylons.put(locString, clan);

        // 파티클 표시 작업이 실행 중이 아니거나 취소되었다면 다시 시작합니다.
        // 이는 서버 시작 후 첫 파일런을 설치하거나, 모든 파일런이 제거된 후 새로 설치할 때 필요합니다.
        if (particleTask == null || particleTask.isCancelled()) {
            startParticleTask();
        }

        plugin.getLogger().info("[파일런 영역] Pylon protection enabled for clan " + clan.getName() + " at " + locString);
    }

    public void removeProtectedPylon(Location location) {
        String locString = PluginUtils.serializeLocation(location);
        protectedPylons.remove(locString);
        if (protectedPylons.isEmpty() && particleTask != null) {
            particleTask.cancel();
        }
        intruderTracker.remove(locString);
        plugin.getLogger().info("[파일런 영역] Pylon protection removed at " + locString);
    }

    /**
     * 지정된 위치에 파일런이 있는지 확인하고, 있다면 해당 파일런을 소유한 가문을 반환합니다.
     * @param location 확인할 위치
     * @return 파일런을 소유한 Clan 객체, 없으면 null
     */
    public Clan getClanAt(Location location) {
        Location blockLoc = location.getBlock().getLocation();
        for (Entry<String, Clan> entry : protectedPylons.entrySet()) {
            Location pylonLoc = PluginUtils.deserializeLocation(entry.getKey());
            if (pylonLoc != null && pylonLoc.equals(blockLoc)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 지정된 위치의 블록이 파일런의 보호받는 구조물(기반, 배리어)의 일부인지 확인합니다.
     * @param location 확인할 위치
     * @return 구조물의 일부이면 true
     */
    public boolean isPylonStructureBlock(Location location) {
        return isPylonStructureBlock(location, null);
    }

    /**
     * 지정된 위치의 블록이 파일런의 보호받는 구조물(기반, 배리어)의 일부인지 확인합니다.
     * 특정 파일런의 구조물은 검사에서 제외할 수 있습니다.
     * @param location 확인할 위치
     * @param pylonToIgnore 검사에서 제외할 파일런의 위치
     * @return 다른 파일런의 구조물의 일부이면 true
     */
    public boolean isPylonStructureBlock(Location location, Location pylonToIgnore) {
        Location blockLocation = location.getBlock().getLocation();
        Location ignoreLocation = (pylonToIgnore != null) ? pylonToIgnore.getBlock().getLocation() : null;

        for (String pylonLocStr : protectedPylons.keySet()) {
            Location pylonLoc = PluginUtils.deserializeLocation(pylonLocStr);
            if (pylonLoc == null || !pylonLoc.getWorld().equals(blockLocation.getWorld())) {
                continue;
            }

            // 무시할 파일런 위치와 같다면, 이 파일런에 대한 검사는 건너뜁니다.
            if (pylonLoc.equals(ignoreLocation)) {
                continue;
            }

            // 기반 블록(y-1의 3x3 영역)인지 확인
            if (blockLocation.getBlockY() == pylonLoc.getBlockY() - 1) {
                if (Math.abs(blockLocation.getBlockX() - pylonLoc.getBlockX()) <= 1 &&
                        Math.abs(blockLocation.getBlockZ() - pylonLoc.getBlockZ()) <= 1) {
                    return true;
                }
            }
            // 배리어 블록(비콘 위 수직 기둥)인지 확인
            if (blockLocation.getBlockX() == pylonLoc.getBlockX() &&
                    blockLocation.getBlockZ() == pylonLoc.getBlockZ() &&
                    blockLocation.getBlockY() > pylonLoc.getBlockY()) {
                return true;
            }
        }
        return false;
    }

    public void applyAreaEffects() {
        boolean allyBuffEnabled = configManager.getConfig().getBoolean("pylon.area-effects.ally-buff-enabled", true);
        boolean enemyDebuffEnabled = configManager.getConfig().getBoolean("pylon.area-effects.enemy-debuff-enabled", true);


        if (!allyBuffEnabled && !enemyDebuffEnabled) {
            if (!intruderTracker.isEmpty()) {
                intruderTracker.clear();
            }
            return;
        }

        int radius = configManager.getConfig().getInt("pylon.area-effects.radius", 50);
        int radiusSquared = radius * radius;

        // 아군에게 적용할 효과
        final PotionEffect allyHaste = new PotionEffect(PotionEffectType.HASTE, 120, 1, true, false); // 성급함 2, 파티클 숨김
        // 적군에게 적용할 효과
        final PotionEffect enemySlowness = new PotionEffect(PotionEffectType.SLOWNESS, 120, 0, true, true); // 구속 1
        final PotionEffect enemyFatigue = new PotionEffect(PotionEffectType.MINING_FATIGUE, 120, 1, true, true); // 채굴 피로 2
        final PotionEffect enemyGlowing = new PotionEffect(PotionEffectType.GLOWING, 120, 0, true, true); // 발광

        Map<String, Set<UUID>> currentIntrudersByPylon = new HashMap<>();

        for (Entry<String, Clan> entry : protectedPylons.entrySet()) {
            String pylonLocStr = entry.getKey();
            Clan clan = entry.getValue();
            Location pylonLoc = PluginUtils.deserializeLocation(pylonLocStr);

            if (pylonLoc == null || pylonLoc.getWorld() == null) continue;

            Set<UUID> currentIntrudersInRadius = new HashSet<>();
            Set<UUID> previouslyKnownIntruders = intruderTracker.getOrDefault(pylonLocStr, new HashSet<>());

            for (Player player : pylonLoc.getWorld().getPlayers()) {
                // Y축을 무시한 2D 거리 계산
                if (distanceSquared2D(player.getLocation(), pylonLoc) > radiusSquared) continue;

                // 서바이벌 또는 어드벤처 모드가 아닌 플레이어는 효과를 적용하지 않습니다.
                if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                    continue;
                }

                if (clan.getMembers().contains(player.getUniqueId())) {
                    // 아군일 경우: 버프 적용
                    if (allyBuffEnabled) {
                        // 배고픔이 19 (반 칸 남음) 미만일 때만 채워줍니다.
                        if (player.getFoodLevel() < 19) {
                            player.setFoodLevel(19);
                        }
                        player.addPotionEffect(allyHaste);
                    }
                } else {
                    // 적군일 경우: 디버프 적용 및 경고
                    if (enemyDebuffEnabled) {
                        currentIntrudersInRadius.add(player.getUniqueId());
                        player.addPotionEffect(enemySlowness);
                        player.addPotionEffect(enemyFatigue);
                        player.addPotionEffect(enemyGlowing);

                        if (!previouslyKnownIntruders.contains(player.getUniqueId())) {
                            String warningMessage = PluginUtils.colorize("&c[경고] &f외부인이 파일런 영역에 접근했습니다!");
                            clan.broadcastMessage(warningMessage);
                        }
                    }
                }
            }
            if (enemyDebuffEnabled) {
                currentIntrudersByPylon.put(pylonLocStr, currentIntrudersInRadius);
            }
        }

        if (enemyDebuffEnabled) {
            intruderTracker.clear();
            intruderTracker.putAll(currentIntrudersByPylon);
        } else {
            intruderTracker.clear();
        }
    }

    public boolean isLocationInClanPylonArea(Clan clan, Location location) {
        int radius = configManager.getConfig().getInt("pylon.area-effects.radius", 50);
        int radiusSquared = radius * radius;

        return clan.getPylonLocations().keySet().stream().anyMatch(pylonLocStr -> {
            Location pylonLoc = PluginUtils.deserializeLocation(pylonLocStr);
            // 월드가 같고, Y축을 무시한 2D 거리가 반경 이내인지 확인
            return pylonLoc != null && pylonLoc.getWorld() != null && pylonLoc.getWorld().equals(location.getWorld()) && distanceSquared2D(location, pylonLoc) <= radiusSquared;
        });
    }

    /**
     * 지정된 위치가 가문의 '주 파일런' 영역 내에 있는지 확인합니다.
     * @param clan 확인할 가문
     * @param location 확인할 위치
     * @return 주 파일런 영역 내에 있으면 true
     */
    public boolean isLocationInClanMainPylonArea(Clan clan, Location location) {
        int radius = configManager.getConfig().getInt("pylon.area-effects.radius", 50);
        int radiusSquared = radius * radius;

        return clan.getPylonLocations().entrySet().stream()
                .filter(entry -> entry.getValue() == PylonType.MAIN_CORE) // 주 파일런만 필터링
                .anyMatch(entry -> {
                    Location pylonLoc = PluginUtils.deserializeLocation(entry.getKey());
                    return pylonLoc != null && pylonLoc.getWorld() != null && pylonLoc.getWorld().equals(location.getWorld()) && distanceSquared2D(location, pylonLoc) <= radiusSquared;
                });
    }

    /**
     * 클랜의 파일런 영역 내에서 안전한 스폰 위치를 찾습니다.
     * @param clan 스폰 위치를 찾을 클랜
     * @return 안전한 스폰 위치 (Optional)
     */
    public Optional<Location> findSafeSpawnInPylonArea(Clan clan) {
        if (clan.getPylonLocations().isEmpty()) {
            return Optional.empty();
        }
        // Get a random pylon location string
        List<String> pylonLocStrings = new ArrayList<>(clan.getPylonLocations().keySet());
        String randomPylonLocStr = pylonLocStrings.get(new Random().nextInt(pylonLocStrings.size()));
        Location pylonCenter = PluginUtils.deserializeLocation(randomPylonLocStr);

        if (pylonCenter == null) {
            return Optional.empty();
        }

        int radius = configManager.getConfig().getInt("pylon.area-effects.radius", 50);
        Random random = new Random();
        World world = pylonCenter.getWorld();

        for (int i = 0; i < 100; i++) { // 100 attempts
            int xOffset = random.nextInt(radius * 2 + 1) - radius;
            int zOffset = random.nextInt(radius * 2 + 1) - radius;

            if (xOffset * xOffset + zOffset * zOffset > radius * radius) {
                continue;
            }

            Block groundBlock = world.getHighestBlockAt(pylonCenter.getBlockX() + xOffset, pylonCenter.getBlockZ() + zOffset);

            if (groundBlock.isLiquid() || groundBlock.getType() == Material.BARRIER || !groundBlock.getType().isSolid()) {
                continue;
            }

            if (groundBlock.getRelative(BlockFace.UP).isPassable() && groundBlock.getRelative(BlockFace.UP, 2).isPassable()) {
                return Optional.of(groundBlock.getLocation().add(0.5, 1.0, 0.5));
            }
        }
        // Fallback to pylon center
        return Optional.of(pylonCenter.getWorld().getHighestBlockAt(pylonCenter).getLocation().add(0.5, 1, 0.5));
    }

    /**
     * 두 위치의 Y축을 무시한 2D 거리의 제곱을 계산합니다.
     * @param loc1 첫 번째 위치
     * @param loc2 두 번째 위치
     * @return 2D 거리의 제곱
     */
    private double distanceSquared2D(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null || !loc1.getWorld().equals(loc2.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * 파일런 보호 구역의 외곽선에 파티클을 생성하는 작업을 시작합니다.
     */
    public void startParticleTask() {
        if (particleTask != null && !particleTask.isCancelled()) {
            particleTask.cancel();
        }
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (protectedPylons.isEmpty()) return;
                // Use a copy to prevent ConcurrentModificationException if pylons are added/removed
                new HashMap<>(protectedPylons).forEach((locStr, clan) -> {
                    Location center = PluginUtils.deserializeLocation(locStr);
                    if (center != null) {
                        spawnBoundaryParticles(center, clan);
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5초마다 실행하여 끊김 없는 효과를 연출
    }

    private void spawnBoundaryParticles(Location center, Clan clan) {
        World world = center.getWorld();
        if (world == null) return; // 월드가 로드되지 않았으면 중단

        String centerLocStr = PluginUtils.serializeLocation(center);
        PylonType pylonType = clan.getPylonType(centerLocStr);
        if (pylonType == null) return; // 데이터 불일치 시 중단

        Particle particleType = (pylonType == PylonType.MAIN_CORE) ? Particle.FLAME : Particle.SOUL_FIRE_FLAME;
        int radius = configManager.getConfig().getInt("pylon.area-effects.radius", 50);
        int radiusSquared = radius * radius;

        // 파티클 밀도를 높여 경계선이 더 촘촘하게 보이도록 합니다.
        final int points = 150;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location particlePoint = new Location(world, x, center.getY(), z);

            boolean shouldSpawn = true;
            // 주 파일런의 파티클은 다른 영역과 겹쳐도 항상 표시됩니다.
            if (pylonType == PylonType.AUXILIARY) {
                // 보조 파일런의 파티클은 다른 파일런의 영역 내부에 있으면 생성하지 않습니다.
                boolean isInsideAnotherPylon = protectedPylons.keySet().stream()
                        .filter(otherLocStr -> !otherLocStr.equals(centerLocStr)) // 자기 자신은 제외
                        .map(PluginUtils::deserializeLocation)
                        .filter(otherCenter -> otherCenter != null && otherCenter.getWorld().equals(world))
                        .anyMatch(otherCenter -> distanceSquared2D(particlePoint, otherCenter) < radiusSquared);

                if (isInsideAnotherPylon) {
                    shouldSpawn = false;
                }
            }

            if (shouldSpawn) {
                spawnWallParticle(world, (int) x, (int) z, particleType);
            }
        }
    }

    private void spawnWallParticle(World world, int x, int z, Particle particleType) {
        Block highestBlock = world.getHighestBlockAt(x, z);
        Location particleLoc = highestBlock.getLocation().add(0.5, 1.2, 0.5); // 블록 중앙, 약간 위
        // 단일 파티클을 생성하여 '선' 효과를 줍니다.
        world.spawnParticle(particleType, particleLoc, 1, 0, 0, 0, 0);
    }
}