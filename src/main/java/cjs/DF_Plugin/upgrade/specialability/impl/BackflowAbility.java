package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.BubbleColumn;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BackflowAbility implements ISpecialAbility {

    private static final List<Material> EFFECT_BLOCKS = Arrays.asList(
            Material.ICE,
            Material.PACKED_ICE,
            Material.BLUE_ICE,
            Material.BLUE_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS,
            Material.SNOW_BLOCK,
            Material.QUARTZ_BLOCK
    );

    @Override
    public String getInternalName() {
        return "backflow";
    }

    @Override
    public String getDisplayName() {
        return "§3역류";
    }

    @Override
    public String getDescription() {
        return "§7강력한 역류를 일으킵니다.";
    }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.cooldown", 180.0);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event, Player player, ItemStack item) {
        // 좌클릭으로만 발동되도록 수정합니다.
        if (!event.getAction().isLeftClick()) {
            return;
        }

        // 능력 발동 위치를 플레이어가 바라보는 30미터 내의 블록으로 설정합니다.
        RayTraceResult rayTraceResult = player.rayTraceBlocks(60);
        if (rayTraceResult == null || rayTraceResult.getHitBlock() == null) {
            // 사정거리 내에 대상 블록이 없으면 발동하지 않습니다.
            return;
        }
        Location center = rayTraceResult.getHitBlock().getLocation().add(0.5, 1.2, 0.5); // 블록 바로 위를 중심으로 설정

        event.setCancelled(true); // 기본 삼지창 사용 방지

        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (manager.isOnCooldown(player, this)) { // 아이템과 무관하게 플레이어의 능력 쿨다운을 확인합니다.
            return;
        }

        // tryUseAbility는 1회성 액티브 스킬에 적합. 여기서는 쿨다운만 설정.
        manager.setCooldown(player, this, getCooldown()); // 아이템이 아닌 플레이어에게 직접 쿨다운을 설정합니다.

        // 능력 실행
        performBackflow(player, center);
    }

    private BlockData getRandomBlockData() {
        return EFFECT_BLOCKS.get(ThreadLocalRandom.current().nextInt(EFFECT_BLOCKS.size())).createBlockData();
    }

    private void performBackflow(Player player, Location center) {
        final double preEffectDurationSeconds = 3.0; // 삼지창 투하 후 메인 이펙트까지의 시간
        double effectDuration = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.effect-duration-seconds", 3.0);
        double radius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.radius", 8.0);
        // [수정] 새로운 데미지 설정 값을 읽어옵니다.
        double normalDamagePerSecond = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.normal-damage-per-second", 10.0);
        double healthPercentDamage = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.current-health-percent-damage", 60.0) / 100.0;
        double pullStrength = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.pull-strength", 2.5);

        // --- 이벤트 순서 재정의 ---

        // 1. 삼지창 드롭 (즉시)
        Location tridentSpawnLoc = center.clone().add(0, 30, 0);
        final Trident fallingTrident = (Trident) center.getWorld().spawnEntity(tridentSpawnLoc, EntityType.TRIDENT);
        fallingTrident.setGravity(true);
        fallingTrident.setInvulnerable(true);
        fallingTrident.setPickupStatus(Trident.PickupStatus.DISALLOWED);
        fallingTrident.setVelocity(new Vector(0, -2.5, 0));
        fallingTrident.setGlowing(true); // 발사된 삼지창에 발광 효과 부여

        // 2. 번개 (0.5초 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                Location strikeLoc = fallingTrident.isValid() ? fallingTrident.getLocation() : center;
                strikeLoc.getWorld().strikeLightningEffect(strikeLoc);
            }
        }.runTaskLater(DF_Main.getInstance(), 10L);

        // 3. 땅에서 물이 새어 나옴 (0.6초 ~ 3.0초)
        new BukkitRunnable() {
            private int ticksRun = 0;
            private final int DURATION_TICKS = (int) (preEffectDurationSeconds * 20) - 12; // 60 - 12 = 48 ticks

            @Override
            public void run() {
                if (ticksRun >= DURATION_TICKS) {
                    this.cancel();
                    return;
                }
                for (int i = 0; i < (int) (radius * 5); i++) { // 파티클 생성량을 줄여 랙 완화
                    double r = Math.random() * radius;
                    double angle = Math.random() * 2 * Math.PI;
                    double x = center.getX() + r * Math.cos(angle);
                    double z = center.getZ() + r * Math.sin(angle);
                    center.getWorld().spawnParticle(Particle.DRIPPING_WATER, x, center.getY(), z, 1, 0, 0, 0, 0);
                }
                ticksRun++;
            }
        }.runTaskTimer(DF_Main.getInstance(), 12L, 1L);

        // 어둠 효과 (물기둥 생성 2초 전부터 3초간)
        new BukkitRunnable() {
            @Override
            public void run() {
                final double MUTE_RADIUS = 100.0;
                final int DURATION_TICKS = 3 * 20; // 3초
                for (Player p : center.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(center) < MUTE_RADIUS * MUTE_RADIUS) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DURATION_TICKS, 0, false, false, false));
                    }
                }
            }
        }.runTaskLater(DF_Main.getInstance(), (long) (preEffectDurationSeconds * 20) - 40L); // 60 - 40 = 20 ticks (1초 후)

        // 4 & 5. 물기둥 생성 (3.0초 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (fallingTrident.isValid()) fallingTrident.remove();
                startMainEffect(player, center, effectDuration, radius, normalDamagePerSecond, healthPercentDamage, pullStrength);
            }
        }.runTaskLater(DF_Main.getInstance(), (long) (preEffectDurationSeconds * 20));
    }

    private void startMainEffect(Player player, Location center, double duration, double radius, double normalDamagePerSecond, double healthPercentDamage, double pullStrength) {
        // --- Ambient Rain Sound ---
        final double totalSoundDuration = duration + 2.5; // 2.5 extra seconds for fade out
        new BukkitRunnable() {
            double elapsedTicks = 0;
            @Override
            public void run() {
                if (elapsedTicks >= totalSoundDuration * 20) {
                    this.cancel();
                    return;
                }

                for (Player p : center.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(center) < 100 * 100) {
                        p.playSound(p.getLocation(), Sound.WEATHER_RAIN, 3.0f, 1.0f);
                    }
                }
                elapsedTicks += 4; // Check every 4 ticks
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 4L);

        // 스피커가 터지는 듯한 강력한 시작음
        center.getWorld().playSound(center, Sound.BLOCK_WATER_AMBIENT, 2.0f, 0.5f);

        // 워든 음파 파동 소리를 여러 피치로 동시에 재생하여 웅장한 화음 효과를 냅니다.
        final double soundRadiusSquared = 100 * 100;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) < soundRadiusSquared) {
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.5f);
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.55f);
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.6f);
            }
        }

        // --- Biome Change ---
        final Map<Location, Biome> originalBiomes = new java.util.HashMap<>();
        final World world = center.getWorld();
        final int centerX = center.getBlockX();
        final int centerZ = center.getBlockZ();
        final int radiusInt = (int) Math.ceil(radius);

        final int upwardHeight = 200;
        final int downwardHeight = 50;

        for (int x = centerX - radiusInt; x <= centerX + radiusInt; x++) {
            for (int z = centerZ - radiusInt; z <= centerZ + radiusInt; z++) {
                if (center.distanceSquared(new Location(world, x, center.getY(), z)) <= radius * radius) {
                    Location columnLoc = new Location(world, x, 0, z); // Key for the map
                    originalBiomes.put(columnLoc, world.getBiome(x, 0, z));
                    world.setBiome(x, 0, z, Biome.OCEAN);
                }
            }
        }

        // --- Block Data Backup in Chunks ---
        // To ensure reliable progressive restoration, we save the original blocks in chunks
        // that correspond to the layers that will be restored together.
        final List<Map<Location, BlockData>> originalBlockChunks = new ArrayList<>();
        final int backupRadius = radiusInt + 8;
        final double backupRadiusSq = backupRadius * backupRadius;
        final int CHUNK_HEIGHT = 10; // Must match LAYERS_PER_TICK in startDestructionAnimation

        // Iterate from bottom to top, creating a map for each chunk.
        for (int y_base = -downwardHeight - 1; y_base <= upwardHeight; y_base += CHUNK_HEIGHT) {
            Map<Location, BlockData> chunkMap = new java.util.HashMap<>();
            for (int y_offset = 0; y_offset < CHUNK_HEIGHT; y_offset++) {
                int currentYOffset = y_base + y_offset;
                if (currentYOffset > upwardHeight) break;

                for (int x = -backupRadius; x <= backupRadius; x++) {
                    for (int z = -backupRadius; z <= backupRadius; z++) {
                        if (x * x + z * z <= backupRadiusSq) {
                            Location loc = center.clone().add(x, currentYOffset, z);
                            chunkMap.put(loc.clone(), loc.getBlock().getBlockData());
                        }
                    }
                }
            }
            if (!chunkMap.isEmpty()) {
                originalBlockChunks.add(chunkMap);
            }
        }


        final double radiusSquared = radius * radius;
        final List<Location> waterBlocks = new ArrayList<>();
        final Map<BlockDisplay, Double> risingBars = new java.util.HashMap<>();

        // --- Main Effect Task (Creation, Animation, Game Logic) ---
        new BukkitRunnable() {
            int tickCounter = 0;
            int yOffset = 0;
            final int maxHeight = Math.max(upwardHeight, downwardHeight);
            final int layersPerTick = 5; // 물기둥이 더 빠르게 올라가도록 수정

            @Override
            public void run() {
                // --- Animation logic (runs every tick) ---
                // 1. Rising Bars Animation
                for (Iterator<Map.Entry<BlockDisplay, Double>> iterator = risingBars.entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<BlockDisplay, Double> entry = iterator.next();
                    BlockDisplay bar = entry.getKey();
                    double speed = entry.getValue();

                    if (!bar.isValid()) {
                        iterator.remove();
                        continue;
                    }
                    Location newLoc = bar.getLocation().add(0, speed, 0);
                    if (newLoc.getY() > center.getY() + upwardHeight) {
                        bar.remove();
                        iterator.remove();
                    } else {
                        bar.teleport(newLoc);
                    }
                }

                // --- Spawning and Game Logic (runs only during effect duration) ---
                if (tickCounter < duration * 20) {
                    // --- Creation logic (runs until pillar is built) ---
                    if (yOffset < maxHeight) {
                        for (int i = 0; i < layersPerTick; i++) {
                            if (yOffset >= maxHeight) break;

                            // Create water layer at yOffset
                            for (double xOffset = -radius; xOffset <= radius; xOffset++) {
                                for (double zOffset = -radius; zOffset <= radius; zOffset++) {
                                    if (xOffset * xOffset + zOffset * zOffset > radiusSquared) continue;

                                    // 위쪽 물기둥은 이제 패턴 없이 꽉 채워서 생성합니다.
                                    if (yOffset < upwardHeight) {
                                        Location blockLoc = center.clone().add(xOffset, yOffset, zOffset);
                                        Block block = blockLoc.getBlock();
                                        BlockData bubbleData = Material.BUBBLE_COLUMN.createBlockData();
                                        ((BubbleColumn) bubbleData).setDrag(false); // 위로 올라가는 거품 생성
                                        block.setBlockData(bubbleData, false);
                                        waterBlocks.add(blockLoc.clone());
                                    }
                                    // 아래쪽 물기둥은 패턴 없이 꽉 채워서 생성합니다.
                                    if (yOffset > 0 && yOffset <= downwardHeight) {
                                        Location blockLoc = center.clone().add(xOffset, -yOffset, zOffset);
                                        Block block = blockLoc.getBlock();
                                        BlockData bubbleData = Material.BUBBLE_COLUMN.createBlockData();
                                        ((BubbleColumn) bubbleData).setDrag(false); // 위로 올라가는 거품 생성
                                        block.setBlockData(bubbleData, false);
                                        waterBlocks.add(blockLoc.clone());
                                    }
                                }
                            }

                            // 물기둥 주변에 공기 벽을 생성하여 물이 퍼지는 것을 방지합니다.
                            final int airWallRadius = radiusInt + 1;
                            for (int xOffset = -airWallRadius; xOffset <= airWallRadius; xOffset++) {
                                for (int zOffset = -airWallRadius; zOffset <= airWallRadius; zOffset++) {
                                    double distSq = xOffset * xOffset + zOffset * zOffset;
                                    // 물기둥 바로 바깥쪽 1칸 쉘에만 작용하도록 합니다.
                                    if (distSq > radiusSquared && distSq <= Math.pow(airWallRadius, 2)) {
                                        // Upward
                                        if (yOffset < upwardHeight) {
                                            Location blockLoc = center.clone().add(xOffset, yOffset, zOffset);
                                            if (blockLoc.getBlock().getType() != Material.AIR) {
                                                blockLoc.getBlock().setType(Material.AIR, false);
                                            }
                                        }
                                        // Downward
                                        if (yOffset > 0 && yOffset <= downwardHeight) {
                                            Location blockLoc = center.clone().add(xOffset, -yOffset, zOffset);
                                            if (blockLoc.getBlock().getType() != Material.AIR) {
                                                blockLoc.getBlock().setType(Material.AIR, false);
                                            }
                                        }
                                    }
                                }
                            }

                            // 51번째 블록(y=-51)에 공기층을 배치하여 물이 아래로 흐르는 것을 방지합니다.
                            if (yOffset == downwardHeight) {
                                int airLayerY = -downwardHeight - 1;
                                for (double xOffsetLayer = -radius; xOffsetLayer <= radius; xOffsetLayer++) {
                                    for (double zOffsetLayer = -radius; zOffsetLayer <= radius; zOffsetLayer++) {
                                        if (xOffsetLayer * xOffsetLayer + zOffsetLayer * zOffsetLayer <= radiusSquared) {
                                            Location airLoc = center.clone().add(xOffsetLayer, airLayerY, zOffsetLayer);
                                            airLoc.getBlock().setType(Material.AIR, false);
                                        }
                                    }
                                }
                            }
                            yOffset++;
                        }
                    }

                    // Spawn new outer pillars
                    for (int i = 0; i < 1; i++) { // 생성량을 줄여 랙 완화
                        double radius = 9.0 + Math.random(); // 9 to 10
                        double angle = Math.random() * 2 * Math.PI;
                        Location pillarBaseLoc = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
                        double pillarSpeed = 2.0 + Math.random() * 1.5; // 외부 기둥 속도: 2.0 ~ 3.5
                        int pillarHeight = 5 + ThreadLocalRandom.current().nextInt(6); // 5-10칸 길이

                        for (int y = 0; y < pillarHeight; y++) {
                            Location blockLoc = pillarBaseLoc.clone().add(0, y, 0);
                            BlockDisplay newBar = center.getWorld().spawn(blockLoc, BlockDisplay.class, (bar) -> {
                                bar.setBlock(getRandomBlockData());
                                bar.setInterpolationDelay(-1);
                                bar.setInterpolationDuration(3);
                                bar.setTeleportDuration(3);
                            });
                            risingBars.put(newBar, pillarSpeed);
                        }
                    }

                    // Spawn new inner pillars
                    for (int i = 0; i < 2; i++) { // 생성량을 줄여 랙 완화
                        double radius = Math.random() * 8.0; // 0 to 8
                        double angle = Math.random() * 2 * Math.PI;
                        Location pillarBaseLoc = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
                        double pillarSpeed = 3.0 + Math.random() * 2.0; // 내부 기둥 속도: 3.0 ~ 5.0
                        int pillarHeight = 5 + ThreadLocalRandom.current().nextInt(6); // 5-10칸 길이

                        for (int y = 0; y < pillarHeight; y++) {
                            Location blockLoc = pillarBaseLoc.clone().add(0, y, 0);
                            BlockDisplay newBar = center.getWorld().spawn(blockLoc, BlockDisplay.class, (bar) -> {
                                bar.setBlock(getRandomBlockData());
                                bar.setInterpolationDelay(-1);
                                bar.setInterpolationDuration(3);
                                bar.setTeleportDuration(3);
                            });
                            risingBars.put(newBar, pillarSpeed);
                        }
                    }

                    // 2. Splash Animation
                    for (int i = 0; i < 5; i++) { // 생성량을 줄여 랙 완화
                        createSplashParticle(center, 11.0, yOffset);
                    }
                    for (int i = 0; i < 2; i++) { // 생성량을 줄여 랙 완화
                        createSplashParticle(center, 12.0, yOffset);
                    }

                    // --- Game Logic (every 4 ticks) ---
                    if (tickCounter % 4 == 0) {
                        // [수정] 새로운 데미지 및 효과 로직
                        // 4틱(0.2초)마다 피해를 적용하므로, 초당 피해량을 5로 나눕니다.
                        final double normalDamagePerApplication = normalDamagePerSecond / 5.0;

                        // 주변 엔티티에 효과 적용 (범위를 물기둥 높이에 맞춤)
                        for (Entity entity : center.getWorld().getNearbyEntities(center.clone().add(0, 100, 0), radius, 100, radius)) {
                            if (entity instanceof LivingEntity target && !target.getUniqueId().equals(player.getUniqueId())) {
                                // 1. 중앙으로 끌어당기는 효과
                                Vector direction = center.toVector().subtract(target.getLocation().toVector());
                                if (direction.lengthSquared() > 1) { // 너무 가까우면 힘이 과도해지는 것을 방지
                                    direction.normalize();
                                }
                                target.setVelocity(target.getVelocity().add(direction.multiply(pullStrength * 0.1)));

                                // 2. 방어력 무시 현재 체력 비례 데미지
                                double trueDamage = target.getHealth() * healthPercentDamage;
                                if (target.isValid() && !target.isDead()) {
                                    double newHealth = Math.max(0, target.getHealth() - trueDamage);
                                    target.setHealth(newHealth);
                                    target.playEffect(org.bukkit.EntityEffect.HURT);
                                }

                                // 3. 일반 고정 데미지
                                if (target.isValid() && !target.isDead()) {
                                    target.damage(normalDamagePerApplication, player);
                                }
                            }
                        }
                    }
                }

                // --- Start Destruction when duration is over ---
                if (tickCounter == duration * 20) {
                    startDestructionAnimation(waterBlocks, center, upwardHeight, downwardHeight, originalBiomes, risingBars, originalBlockChunks);
                    this.cancel();
                    return;
                }

                // --- Stop condition ---
                tickCounter++;
            }

            private void createSplashParticle(Location center, double radius, double maxHeight) {
                if (maxHeight <= 0) return;
                double angle = Math.random() * 2 * Math.PI;
                double y = Math.random() * maxHeight;
                Location spawnLoc = center.clone().add(radius * Math.cos(angle), y, radius * Math.sin(angle));

                BlockDisplay splash = center.getWorld().spawn(spawnLoc, BlockDisplay.class, (s) -> {
                    s.setBlock(getRandomBlockData());
                });

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (splash.isValid()) {
                            splash.remove();
                        }
                    }
                }.runTaskLater(DF_Main.getInstance(), 6L); // Lasts for 6 ticks
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 1L);
    }

    private void startDestructionAnimation(List<Location> waterBlocks, Location center, int upwardHeight, int downwardHeight, Map<Location, Biome> originalBiomes, Map<BlockDisplay, Double> risingBars, List<Map<Location, BlockData>> originalBlockChunks) {
        // The list of specific water blocks is no longer needed for destruction.
        waterBlocks.clear();

        new BukkitRunnable() {
            final int DESTRUCTION_DURATION_TICKS = originalBlockChunks.size() + 5; // 각 청크가 1회 실행을 의미하며, 5회(0.5초)의 추가 시간을 부여합니다.
            int chunkIndex = 0; // We will restore one chunk at a time
            int tickCounter = 0;
            boolean destructionFinished = false;

            @Override
            public void run() {
                // --- Stop Condition ---
                // Stop when both water blocks are cleared and decorative bars are gone, or after a timeout.
                if ((destructionFinished && risingBars.isEmpty()) || tickCounter > DESTRUCTION_DURATION_TICKS + 40) {
                    // Final cleanup just in case
                    if (!risingBars.isEmpty()) {
                        risingBars.forEach((bar, speed) -> { if (bar.isValid()) bar.remove(); });
                        risingBars.clear();
                    }

                    // --- Biome Restoration ---
                    for (Map.Entry<Location, Biome> entry : originalBiomes.entrySet()) {
                        Location loc = entry.getKey();
                        center.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), entry.getValue());
                    }

                    // --- Final Block Restoration from Backup ---
                    // This is the ultimate cleanup, reverting the entire area to its pre-ability state
                    // to ensure nothing is left behind, even if the animation misses something.
                    if (originalBlockChunks != null) {
                        for (Map<Location, BlockData> chunk : originalBlockChunks) {
                            for (Map.Entry<Location, BlockData> entry : chunk.entrySet()) {
                                entry.getKey().getBlock().setBlockData(entry.getValue(), false);
                            }
                        }
                    }

                    this.cancel();
                    return;
                }

                // --- Animate and accelerate rising bars to finish within DESTRUCTION_DURATION_TICKS ---
                for (Iterator<Map.Entry<BlockDisplay, Double>> iterator = risingBars.entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<BlockDisplay, Double> entry = iterator.next();
                    BlockDisplay bar = entry.getKey();
                    double originalSpeed = entry.getValue();

                    if (!bar.isValid()) {
                        iterator.remove();
                        continue;
                    }

                    double remainingHeight = (center.getY() + upwardHeight) - bar.getLocation().getY();
                    double remainingTicks = Math.max(1, DESTRUCTION_DURATION_TICKS - tickCounter);
                    double requiredSpeed = remainingHeight / remainingTicks;
                    double currentSpeed = Math.max(originalSpeed, requiredSpeed);

                    Location newLoc = bar.getLocation().add(0, currentSpeed, 0);

                    if (newLoc.getY() >= center.getY() + upwardHeight || tickCounter >= DESTRUCTION_DURATION_TICKS) {
                        bar.remove();
                        iterator.remove();
                    } else {
                        bar.teleport(newLoc);
                    }
                }

                // --- 아래에서 위로 올라가며 블록 복원 ---
                if (!destructionFinished) {
                    if (tickCounter % 4 == 0) {
                        // Play sound at the approximate height of the current chunk
                        int yOffset = -downwardHeight + (chunkIndex * 10);
                        Location soundLoc = center.clone().add(0, yOffset, 0);
                        center.getWorld().playSound(soundLoc, Sound.ENTITY_GENERIC_BURN, 2.0f, 0.8f);
                    }

                    // Restore one chunk of blocks
                    if (chunkIndex < originalBlockChunks.size()) {
                        Map<Location, BlockData> chunkToRestore = originalBlockChunks.get(chunkIndex);
                        for (Map.Entry<Location, BlockData> entry : chunkToRestore.entrySet()) {
                            entry.getKey().getBlock().setBlockData(entry.getValue(), false);
                        }
                        chunkIndex++;
                    }

                    if (chunkIndex >= originalBlockChunks.size()) {
                        destructionFinished = true;
                    }
                }

                tickCounter++;
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 2L);
    }
}