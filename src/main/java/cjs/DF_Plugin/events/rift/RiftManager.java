package cjs.DF_Plugin.events.rift;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class RiftManager {

    private final DF_Main plugin;
    private boolean isEventActive = false;
    private Location altarLocation;
    private final Map<Location, BlockData> originalBlocks = new HashMap<>();
    private BossBar altarStateBossBar;
    private BukkitTask altarStateUpdateTask;
    private double breakingProgress = 1.0;

    private static final String CONFIG_PATH_ROOT = "events.rift.";
    private static final String CONFIG_PATH_ACTIVE = CONFIG_PATH_ROOT + "active";
    private static final String CONFIG_PATH_LOCATION = CONFIG_PATH_ROOT + "location";
    private static final String CONFIG_PATH_START_TIME = CONFIG_PATH_ROOT + "start-time";
    private static final String CONFIG_PATH_BREAKING_PROGRESS = CONFIG_PATH_ROOT + "breaking-progress";

    public RiftManager(DF_Main plugin) {
        this.plugin = plugin;
        loadState();
    }

    private void loadState() {
        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        this.isEventActive = eventConfig.getBoolean(CONFIG_PATH_ACTIVE, false);

        if (isEventActive) {
            plugin.getLogger().warning("[차원의 균열] 이전 이벤트가 활성 상태로 남아있습니다. 이벤트를 재개합니다...");
            String locString = eventConfig.getString(CONFIG_PATH_LOCATION);
            if (locString == null) {
                plugin.getLogger().severe("[차원의 균열] 활성 이벤트 위치 데이터가 손상되었습니다. 이벤트를 강제 종료합니다.");
                cleanupAltar();
                return;
            }
            this.altarLocation = cjs.DF_Plugin.util.PluginUtils.deserializeLocation(locString);

            World world = this.altarLocation.getWorld();
            if (world == null) {
                plugin.getLogger().severe("[차원의 균열] 이벤트 재개 실패: 제단이 있는 월드를 찾을 수 없습니다.");
                cleanupAltar();
                return;
            }
            Chunk altarChunk = this.altarLocation.getChunk();

            world.getChunkAtAsync(altarChunk.getX(), altarChunk.getZ()).thenAccept(chunk -> {
                chunk.setForceLoaded(true);
                plugin.getLogger().info("[차원의 균열] 재개된 이벤트의 제단 청크(" + chunk.getX() + ", " + chunk.getZ() + ")를 강제 로드합니다.");

                this.breakingProgress = eventConfig.getDouble(CONFIG_PATH_BREAKING_PROGRESS, 1.0);
                long startTime = eventConfig.getLong(CONFIG_PATH_START_TIME);
                loadAltarData();

                plugin.getLogger().info("[차원의 균열] 제단 구조물을 다시 생성합니다...");
                long eventDurationMillis = TimeUnit.HOURS.toMillis(plugin.getGameConfigManager().getConfig().getLong("events.rift.spawn-delay-hours", 1));
                long elapsedTime = System.currentTimeMillis() - startTime;
                boolean shouldEggBeSpawned = elapsedTime >= eventDurationMillis;
                buildAltarStructure(shouldEggBeSpawned);

                activateAltar(startTime, shouldEggBeSpawned);
            });
        } else if (eventConfig.contains(CONFIG_PATH_ROOT + "altar-blocks")) {
            plugin.getLogger().warning("[차원의 균열] 이전 이벤트에서 남은 제단 데이터를 발견했습니다. 정리를 시작합니다...");
            cleanupAltarFromConfig();
        }
    }

    public void triggerEvent() {
        if (isEventActive) {
            return;
        }
        this.breakingProgress = 1.0;

        World world = Bukkit.getWorlds().getFirst();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            plugin.getLogger().warning("[차원의 균열] 차원의 균열 이벤트는 오버월드에서만 발생할 수 있습니다.");
            return;
        }

        Location groundLocation = findOrPrepareLocation(world);
        this.isEventActive = true;
        this.altarLocation = groundLocation.clone().add(0, 3, 0);

        World altarWorld = this.altarLocation.getWorld();
        Chunk altarChunk = this.altarLocation.getChunk();

        altarWorld.getChunkAtAsync(altarChunk.getX(), altarChunk.getZ()).thenAccept(chunk -> {
            chunk.setForceLoaded(true);
            plugin.getLogger().info("[차원의 균열] 제단 청크(" + chunk.getX() + ", " + chunk.getZ() + ")를 강제 로드합니다.");

            spawnAltar(groundLocation, false);

            FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
            eventConfig.set(CONFIG_PATH_ACTIVE, true);
            eventConfig.set(CONFIG_PATH_LOCATION, cjs.DF_Plugin.util.PluginUtils.serializeLocation(this.altarLocation));
            long startTime = System.currentTimeMillis();
            eventConfig.set(CONFIG_PATH_START_TIME, startTime);
            plugin.getEventDataManager().saveConfig();

            saveAltarData();

            Bukkit.broadcastMessage("§d[차원의 균열] §f차원의 어딘가가 불안정합니다.");
            placeRiftCompassInStorages();

            Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f));
            plugin.getLogger().info("[차원의 균열] 제단 생성 성공. 위치: " + groundLocation.getBlockX() + ", " + groundLocation.getBlockY() + ", " + groundLocation.getBlockZ());

            activateAltar(startTime, false);
        });
    }

    private Location findOrPrepareLocation(World world) {
        int maxAttempts = 25;
        Location candidateLocation = null;

        for (int i = 0; i < maxAttempts; i++) {
            candidateLocation = getRandomSafeLocation(world);
            Location highestBlockLoc = world.getHighestBlockAt(candidateLocation).getLocation();
            Block groundBlock = highestBlockLoc.getBlock();

            if (!groundBlock.isLiquid() && groundBlock.getType() != Material.AIR && !groundBlock.getType().toString().contains("LEAVES")) {
                plugin.getLogger().info("[차원의 균열] " + (i + 1) + "번 시도 후 적절한 제단 위치를 찾았습니다: " + highestBlockLoc);
                return highestBlockLoc;
            }
        }

        plugin.getLogger().warning("[차원의 균열] " + maxAttempts + "번 시도 후에도 적절한 제단 위치를 찾지 못했습니다. 마지막 후보 위치에 플랫폼을 생성합니다.");
        Location groundLocation = world.getHighestBlockAt(candidateLocation).getLocation();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block platformBlock = groundLocation.clone().add(dx, 0, dz).getBlock();
                originalBlocks.put(platformBlock.getLocation(), platformBlock.getBlockData());
                platformBlock.setType(Material.COBBLESTONE);
            }
        }
        plugin.getLogger().info("[차원의 균열] 플랫폼 생성 완료: " + groundLocation);
        return groundLocation;
    }

    private void spawnAltar(Location groundLocation, boolean withEgg) {
        Location eggLocation = this.altarLocation;
        int worldMaxHeight = eggLocation.getWorld().getMaxHeight();

        saveOriginalBlock(eggLocation.clone().subtract(0, 4, 0), 0, 0, 0);
        saveOriginalBlock(eggLocation.clone().subtract(0, 3, 0), 1, 0, 1);
        saveOriginalBlock(eggLocation.clone().subtract(0, 2, 0), 1, 0, 1);
        saveOriginalBlock(eggLocation.clone().subtract(0, 1, 0), 1, 0, 1);
        if (withEgg) {
            saveOriginalBlock(eggLocation, 0, 0, 0);
            for (int y = eggLocation.getBlockY() + 1; y < worldMaxHeight; y++) {
                Location locAbove = new Location(eggLocation.getWorld(), eggLocation.getX(), y, eggLocation.getZ());
                originalBlocks.put(locAbove.getBlock().getLocation(), locAbove.getBlock().getBlockData());
            }
        }

        buildAltarStructure(withEgg);
    }

    private void buildAltarStructure(boolean withEgg) {
        Location eggLocation = this.altarLocation;
        Location beaconLocation = eggLocation.clone().subtract(0, 2, 0);
        int worldMaxHeight = eggLocation.getWorld().getMaxHeight();

        eggLocation.clone().subtract(0, 4, 0).getBlock().setType(Material.LODESTONE);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                beaconLocation.clone().add(x, -1, z).getBlock().setType(Material.NETHERITE_BLOCK);
            }
        }

        beaconLocation.getBlock().setType(Material.BEACON);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                beaconLocation.clone().add(x, 0, z).getBlock().setType(Material.PURPLE_STAINED_GLASS);
            }
        }

        eggLocation.clone().add(0, -1, 0).getBlock().setType(Material.PURPLE_STAINED_GLASS);

        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                Block stairBlock = eggLocation.clone().add(x, -1, z).getBlock();
                stairBlock.setType(Material.MOSSY_COBBLESTONE_STAIRS);
                Stairs stairData = (Stairs) stairBlock.getBlockData();
                stairData.setFacing(z == 1 ? BlockFace.NORTH : BlockFace.SOUTH);
                stairBlock.setBlockData(stairData);
            }
        }
        setStair(eggLocation.clone().add(0,-1,0), 0, 1, BlockFace.NORTH);
        setStair(eggLocation.clone().add(0,-1,0), 0, -1, BlockFace.SOUTH);
        setStair(eggLocation.clone().add(0,-1,0), 1, 0, BlockFace.WEST);
        setStair(eggLocation.clone().add(0,-1,0), -1, 0, BlockFace.EAST);

        if (withEgg) {
            eggLocation.getBlock().setType(Material.DRAGON_EGG);
            for (int y = eggLocation.getBlockY() + 1; y < worldMaxHeight; y++) {
                Location locAbove = new Location(eggLocation.getWorld(), eggLocation.getX(), y, eggLocation.getZ());
                locAbove.getBlock().setType(Material.AIR, false);
            }
        }
    }

    private void setStair(Location center, int dX, int dZ, BlockFace facing) {
        Block stairBlock = center.clone().add(dX, 0, dZ).getBlock();
        stairBlock.setType(Material.MOSSY_COBBLESTONE_STAIRS);
        Stairs stairData = (Stairs) stairBlock.getBlockData();
        stairData.setFacing(facing);
        stairBlock.setBlockData(stairData);
    }

    private void activateAltar(long startTime, boolean initialEggState) {
        long eventDurationMillis = TimeUnit.HOURS.toMillis(plugin.getGameConfigManager().getConfig().getLong("events.rift.spawn-delay-hours", 1));

        altarStateBossBar = Bukkit.createBossBar("§d차원의 균열이 점점 커지고 있습니다...", BarColor.PURPLE, BarStyle.SOLID);
        altarStateBossBar.setProgress(1.0);
        altarStateBossBar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() == World.Environment.NORMAL) {
                altarStateBossBar.addPlayer(p);
            }
        }

        altarStateUpdateTask = new BukkitRunnable() {
            private boolean eggSpawned = initialEggState;
            private long lastSaveTime = System.currentTimeMillis();

            @Override
            public void run() {
                if (!isEventActive) {
                    this.cancel();
                    return;
                }

                if (!eggSpawned) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    double progress = 1.0 - ((double) elapsedTime / eventDurationMillis);

                    if (progress <= 0) {
                        eggSpawned = true;
                        altarLocation.getBlock().setType(Material.DRAGON_EGG);
                        altarStateBossBar.setTitle("§c이계의 알이 도착했습니다");
                        altarStateBossBar.setColor(BarColor.RED);
                        Bukkit.broadcastMessage("§d[차원의 균열] §f균열에서 강력한 기운이 감지됩니다!");
                        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f));
                    } else {
                        altarStateBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                    }
                    return;
                }

                altarStateBossBar.setTitle("§c이계의 알이 도착했습니다");
                altarStateBossBar.setProgress(breakingProgress);

                if (altarLocation.getBlock().getType() != Material.DRAGON_EGG) {
                    Bukkit.broadcastMessage("§d[차원의 균열] §c알이 불안정하여 소멸했습니다!");
                    cleanupAltar();
                    this.cancel();
                    return;
                }
                
                Player breakingPlayer = null;
                int altarX = altarLocation.getBlockX();
                int altarZ = altarLocation.getBlockZ();
                int stairLevelY = altarLocation.getBlockY() - 1;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                        Location playerStandingOnLoc = p.getLocation().getBlock().getRelative(BlockFace.DOWN).getLocation();
                        int standingX = playerStandingOnLoc.getBlockX();
                        int standingY = playerStandingOnLoc.getBlockY();
                        int standingZ = playerStandingOnLoc.getBlockZ();

                        boolean isInArea = Math.abs(standingX - altarX) <= 1 && Math.abs(standingZ - altarZ) <= 1;

                        if (isInArea && standingY == stairLevelY) {
                            breakingPlayer = p;
                            break;
                        }
                    }
                }

                if (breakingPlayer != null) {
                    long breakSeconds = plugin.getGameConfigManager().getConfig().getLong("events.rift.break-duration-seconds", 8);
                    double breakRatePerTick = 1.0 / (breakSeconds * 20.0);
                    breakingProgress = Math.max(0, breakingProgress - breakRatePerTick);

                    if (breakingProgress <= 0) {
                        handleEggBreak(breakingPlayer);
                        this.cancel();
                    }
                } else {
                    if (breakingProgress < 1.0) {
                        double regenRatePerTick = 1.0 / (2 * 20.0);
                        breakingProgress = Math.min(1.0, breakingProgress + regenRatePerTick);
                    }
                }

                if (eggSpawned && System.currentTimeMillis() - lastSaveTime > 5000) {
                    plugin.getEventDataManager().getConfig().set(CONFIG_PATH_BREAKING_PROGRESS, breakingProgress);
                    plugin.getEventDataManager().saveConfig();
                    lastSaveTime = System.currentTimeMillis();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void handleEggBreak(Player player) {
        if (!isEventActive) return;

        cleanupBossBar();

        Bukkit.broadcastMessage("§d[차원의 균열] §f차원의 균열이 닫힙니다.");
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.6f));

        altarLocation.clone().subtract(0, 2, 0).getBlock().setType(Material.AIR);
        altarLocation.getBlock().setType(Material.AIR);

        generateRewards().forEach(item ->
                altarLocation.getWorld().dropItemNaturally(altarLocation, item)
        );

        long cleanupDelayMinutes = plugin.getGameConfigManager().getConfig().getLong("events.rift.cleanup-delay-minutes", 1);
        long cleanupDelayTicks = cleanupDelayMinutes * 20L * 60L;
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupAltar();
            }
        }.runTaskLater(plugin, cleanupDelayTicks);
    }

    private void cleanupAltar() {
        cleanupBossBar();
        isEventActive = false;
        this.breakingProgress = 1.0;

        if (!originalBlocks.isEmpty()) {
            originalBlocks.forEach((loc, data) -> loc.getBlock().setBlockData(data, false));
            originalBlocks.clear();
        }

        if (altarLocation != null) {
            altarLocation.getChunk().setForceLoaded(false);
            plugin.getLogger().info("[차원의 균열] 제단 청크(" + altarLocation.getChunk().getX() + ", " + altarLocation.getChunk().getZ() + ")의 강제 로드를 해제합니다.");
        }

        plugin.getClanManager().updateAllPylonStoragesDynamicSlot();

        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        eventConfig.set(CONFIG_PATH_ACTIVE, false);
        eventConfig.set(CONFIG_PATH_LOCATION, null);
        eventConfig.set(CONFIG_PATH_START_TIME, null);
        eventConfig.set(CONFIG_PATH_BREAKING_PROGRESS, null);
        eventConfig.set(CONFIG_PATH_ROOT + "altar-blocks", null);
        plugin.getEventDataManager().saveConfig();

        altarLocation = null;
        plugin.getRiftScheduler().startNewCooldown();
    }

    public void cleanupBossBar() {
        if (altarStateUpdateTask != null) {
            altarStateUpdateTask.cancel();
            altarStateUpdateTask = null;
        }
        if (altarStateBossBar != null) {
            altarStateBossBar.removeAll();
            altarStateBossBar.setVisible(false);
            altarStateBossBar = null;
        }
    }

    private void placeRiftCompassInStorages() {
        if (altarLocation == null) {
            plugin.getLogger().warning("[차원의 균열] 제단 위치가 설정되지 않아 나침반을 지급할 수 없습니다.");
            return;
        }

        plugin.getClanManager().updateAllPylonStoragesDynamicSlot();

        for (Clan clan : plugin.getClanManager().getAllClans()) {
            clan.broadcastMessage("§d[차원의 균열] §7가문창고에 의문의 나침반이 어딘가를 가리킵니다.");
        }
    }

    public void showBarToPlayer(Player player) {
        if (isEventActive && altarStateBossBar != null) {
            if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
                altarStateBossBar.addPlayer(player);
            }
        }
    }

    public void hideBarFromPlayer(Player player) {
        if (altarStateBossBar != null) {
            altarStateBossBar.removePlayer(player);
        }
    }

    private List<ItemStack> generateRewards() {
        return Collections.singletonList(new ItemStack(Material.DRAGON_EGG));
    }

    public boolean isEventActive() {
        return isEventActive;
    }

    public boolean isAltarBlock(Location loc) {
        if (!isEventActive) return false;
        return originalBlocks.containsKey(loc.getBlock().getLocation());
    }

    public boolean isProtectedZone(Location loc) {
        if (!isEventActive || altarLocation == null) {
            return false;
        }
        int altarX = altarLocation.getBlockX();
        int altarZ = altarLocation.getBlockZ();
        int altarY = altarLocation.getBlockY();

        return loc.getBlockX() >= altarX - 1 && loc.getBlockX() <= altarX + 1 &&
                loc.getBlockZ() >= altarZ - 1 && loc.getBlockZ() <= altarZ + 1 &&
                loc.getBlockY() >= altarY;
    }

    public Location getAltarLocation() {
        return altarLocation;
    }

    private void saveOriginalBlock(Location center, int radiusX, int radiusY, int radiusZ) {
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    Location loc = center.clone().add(x, y, z);
                    originalBlocks.put(loc, loc.getBlock().getBlockData());
                }
            }
        }
    }

    private void saveAltarData() {
        if (originalBlocks.isEmpty() || altarLocation == null) return;

        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        String altarBlocksPath = CONFIG_PATH_ROOT + "altar-blocks";

        eventConfig.set(altarBlocksPath, null);

        eventConfig.set(altarBlocksPath + ".world", altarLocation.getWorld().getName());

        List<Map<String, Object>> blockList = new ArrayList<>();
        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Location loc = entry.getKey();
            Map<String, Object> blockMap = new HashMap<>();
            blockMap.put("x", loc.getBlockX());
            blockMap.put("y", loc.getBlockY());
            blockMap.put("z", loc.getBlockZ());
            blockMap.put("data", entry.getValue().getAsString());
            blockList.add(blockMap);
        }
        eventConfig.set(altarBlocksPath + ".blocks", blockList);

        plugin.getEventDataManager().saveConfig();
    }

    private void loadAltarData() {
        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        String altarBlocksPath = CONFIG_PATH_ROOT + "altar-blocks";
        if (!eventConfig.contains(altarBlocksPath)) {
            plugin.getLogger().warning("[차원의 균열] 제단 블록 데이터가 없어 보호 기능을 재개할 수 없습니다. 이벤트가 비정상적으로 종료될 수 있습니다.");
            return;
        }

        String worldName = eventConfig.getString(altarBlocksPath + ".world");

        if (worldName == null) {
            plugin.getLogger().severe("[차원의 균열] event_data.yml의 제단 월드 데이터가 손상되었습니다.");
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("[차원의 균열] 제단 데이터를 로드할 수 없습니다: 월드 '" + worldName + "'를 찾을 수 없습니다!");
            return;
        }

        originalBlocks.clear();
        boolean needsResave = false;

        if (eventConfig.get(altarBlocksPath + ".blocks") instanceof List) {
            List<Map<?, ?>> blockList = eventConfig.getMapList(altarBlocksPath + ".blocks");
            for (Map<?, ?> blockMap : blockList) {
                try {
                    int x = ((Number) blockMap.get("x")).intValue();
                    int y = ((Number) blockMap.get("y")).intValue();
                    int z = ((Number) blockMap.get("z")).intValue();
                    String dataString = (String) blockMap.get("data");
                    if (dataString == null) {
                        plugin.getLogger().warning("[차원의 균열] 새 형식 데이터에서 블록 데이터 문자열이 null입니다. 스킵합니다: " + blockMap);
                        continue;
                    }
                    Location loc = new Location(world, x, y, z);
                    BlockData data = Bukkit.createBlockData(dataString);
                    originalBlocks.put(loc, data);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[차원의 균열] 제단 블록 데이터(새 형식) 파싱 오류: " + blockMap, e);
                }
            }
        }
        else if (eventConfig.isConfigurationSection(altarBlocksPath + ".blocks")) {
            plugin.getLogger().warning("[차원의 균열] 구 버전의 제단 데이터를 발견했습니다. 새 형식으로 변환합니다...");
            ConfigurationSection section = eventConfig.getConfigurationSection(altarBlocksPath + ".blocks");
            parseOldMapFormat(section, world);
            needsResave = true;
        } else {
            plugin.getLogger().severe("[차원의 균열] event_data.yml의 제단 블록 데이터 형식이 올바르지 않습니다.");
            return;
        }

        if (needsResave) {
            plugin.getLogger().info("[차원의 균열] " + originalBlocks.size() + "개의 블록 데이터를 새 형식으로 저장합니다.");
            saveAltarData();
        }

        plugin.getLogger().info("[차원의 균열] " + originalBlocks.size() + "개의 제단 블록 데이터를 로드했습니다.");
    }

    private void cleanupAltarFromConfig() {
        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        String altarBlocksPath = CONFIG_PATH_ROOT + "altar-blocks";
        if (!eventConfig.contains(altarBlocksPath)) return;

        String worldName = eventConfig.getString(altarBlocksPath + ".world");

        if (worldName == null) {
            plugin.getLogger().severe("[차원의 균열] 정리할 제단 데이터가 손상되었습니다. 데이터를 제거합니다.");
            eventConfig.set(altarBlocksPath, null);
            plugin.getEventDataManager().saveConfig();
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("[차원의 균열] Cannot clean up altar: World '" + worldName + "' not found or not loaded!");
            return;
        }

        Map<Location, BlockData> blocksToRestore = new HashMap<>();

        if (eventConfig.get(altarBlocksPath + ".blocks") instanceof List) {
            List<Map<?, ?>> blockList = eventConfig.getMapList(altarBlocksPath + ".blocks");
            for (Map<?, ?> blockMap : blockList) {
                try {
                    int x = ((Number) blockMap.get("x")).intValue();
                    int y = ((Number) blockMap.get("y")).intValue();
                    int z = ((Number) blockMap.get("z")).intValue();
                    String dataString = (String) blockMap.get("data");
                    if (dataString == null) {
                        plugin.getLogger().warning("[차원의 균열] 정리 중 새 형식 데이터에서 블록 데이터 문자열이 null입니다. 스킵합니다: " + blockMap);
                        continue;
                    }
                    Location loc = new Location(world, x, y, z);
                    blocksToRestore.put(loc, Bukkit.createBlockData(dataString));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[차원의 균열] 정리 중 블록 데이터(새 형식) 파싱 오류: " + blockMap, e);
                }
            }
        }
        else if (eventConfig.isConfigurationSection(altarBlocksPath + ".blocks")) {
            ConfigurationSection section = eventConfig.getConfigurationSection(altarBlocksPath + ".blocks");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        String[] parts = key.split(",");
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[2]);
                        Location loc = new Location(world, x, y, z);
                        String dataString = section.getString(key);
                        if (dataString == null) {
                            plugin.getLogger().warning("[차원의 균열] 정리 중 구 버전 데이터에서 블록 데이터 문자열이 null입니다. 스킵합니다: " + key);
                            continue;
                        }
                        blocksToRestore.put(loc, Bukkit.createBlockData(dataString));
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "[차원의 균열] 정리 중 블록 데이터(구 형식) 파싱 오류: " + key, e);
                    }
                }
            }
        }

        if (!blocksToRestore.isEmpty()) {
            try {
                Location firstLoc = blocksToRestore.keySet().stream().findFirst().get();
                firstLoc.getChunk().setForceLoaded(false);
                plugin.getLogger().info("[차원의 균열] 남은 데이터 정리 중 제단 청크(" + firstLoc.getChunk().getX() + ", " + firstLoc.getChunk().getZ() + ")의 강제 로드를 해제합니다.");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[차원의 균열] 남은 데이터 정리 중 청크 로드 해제에 실패했습니다.", e);
            }
        }

        blocksToRestore.forEach((loc, data) -> loc.getBlock().setBlockData(data, false));

        eventConfig.set(altarBlocksPath, null);
        plugin.getEventDataManager().saveConfig();
        plugin.getLogger().info("[차원의 균열] Leftover altar cleanup complete.");
    }

    private void parseOldMapFormat(ConfigurationSection section, World world) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                String[] parts = key.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                Location loc = new Location(world, x, y, z);
                String dataString = section.getString(key);
                if (dataString == null) {
                    plugin.getLogger().warning("[차원의 균열] 구 버전 맵 형식 파싱 중 블록 데이터 문자열이 null입니다. 스킵합니다: " + key);
                    continue;
                }
                BlockData data = Bukkit.createBlockData(dataString);
                originalBlocks.put(loc, data);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[차원의 균열] 구 버전 맵 형식 파싱 오류: " + key, e);
            }
        }
    }

    private Location getRandomSafeLocation(World world) {
        double borderSize = plugin.getGameConfigManager().getConfig().getDouble("world.border.overworld-size", 20000.0);
        double radius = (borderSize / 2.0) * 0.9;
        Random random = new Random();

        double angle = random.nextDouble() * 2 * Math.PI;
        double r = radius * Math.sqrt(random.nextDouble());
        int x = (int) (r * Math.cos(angle));
        int z = (int) (r * Math.sin(angle));

        return new Location(world, x + 0.5, 0, z + 0.5);
    }
}