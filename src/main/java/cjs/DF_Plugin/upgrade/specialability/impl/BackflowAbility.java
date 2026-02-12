package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.*;
import org.bukkit.block.Biome;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.BubbleColumn;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BackflowAbility implements ISpecialAbility {

    private static final Set<Location> activeEffectCenters = ConcurrentHashMap.newKeySet();
    private record BlockBackup(BlockData blockData, ItemStack[] inventoryContents) {}

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
        if (!event.getAction().isLeftClick()) {
            return;
        }

        if (item.hasItemMeta()) {
            String currentMode = item.getItemMeta().getPersistentDataContainer().getOrDefault(UpgradeManager.TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");
            if ("lightning_spear".equals(currentMode)) {
                return;
            }
        }

        if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
            return;
        }

        RayTraceResult rayTraceResult = player.rayTraceBlocks(60);
        if (rayTraceResult == null || rayTraceResult.getHitBlock() == null) {
            return;
        }
        Location center = rayTraceResult.getHitBlock().getLocation().add(0.5, 1.2, 0.5);

        double radius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.radius", 8.0);
        for (Location activeCenter : activeEffectCenters) {
            if (activeCenter.getWorld().equals(center.getWorld()) && activeCenter.distanceSquared(center) < (radius * 2) * (radius * 2)) {
                player.sendMessage("§c이미 다른 역류가 발동 중인 지역입니다.");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
                return;
            }
        }

        event.setCancelled(true);

        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (manager.isOnCooldown(player, this)) {
            return;
        }

        manager.setCooldown(player, this, getCooldown());
        performBackflow(player, center);
    }

    private BlockData getRandomBlockData() {
        List<Material> effectBlocks = Arrays.asList(
                Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
                Material.BLUE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
                Material.SNOW_BLOCK, Material.QUARTZ_BLOCK
        );
        return effectBlocks.get(ThreadLocalRandom.current().nextInt(effectBlocks.size())).createBlockData();
    }

    private void performBackflow(Player player, Location center) {
        activeEffectCenters.add(center);

        final double preEffectDurationSeconds = 3.0;
        double effectDuration = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.effect-duration-seconds", 3.0);
        double radius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.radius", 8.0);
        double normalDamagePerSecond = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.normal-damage-per-second", 10.0);
        double healthPercentDamage = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.current-health-percent-damage", 60.0) / 100.0;
        double pullStrength = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.pull-strength", 2.5);

        Location tridentSpawnLoc = center.clone().add(0, 30, 0);
        final Trident fallingTrident = (Trident) center.getWorld().spawnEntity(tridentSpawnLoc, EntityType.TRIDENT);
        fallingTrident.setGravity(true);
        fallingTrident.setInvulnerable(true);
        fallingTrident.setPickupStatus(Trident.PickupStatus.DISALLOWED);
        fallingTrident.setVelocity(new Vector(0, -2.5, 0));
        fallingTrident.setGlowing(true);

        Bukkit.getScheduler().runTaskLater(DF_Main.getInstance(), () -> {
            Location strikeLoc = fallingTrident.isValid() ? fallingTrident.getLocation() : center;
            strikeLoc.getWorld().strikeLightningEffect(strikeLoc);
        }, 10L);

        new BukkitRunnable() {
            private int ticksRun = 0;
            private final int DURATION_TICKS = (int) (preEffectDurationSeconds * 20) - 12;
            @Override
            public void run() {
                if (ticksRun >= DURATION_TICKS) {
                    this.cancel();
                    return;
                }
                for (int i = 0; i < (int) (radius * 5); i++) {
                    double r = Math.random() * radius;
                    double angle = Math.random() * 2 * Math.PI;
                    double x = center.getX() + r * Math.cos(angle);
                    double z = center.getZ() + r * Math.sin(angle);
                    center.getWorld().spawnParticle(Particle.DRIPPING_WATER, x, center.getY(), z, 1, 0, 0, 0, 0);
                }
                ticksRun++;
            }
        }.runTaskTimer(DF_Main.getInstance(), 12L, 1L);

        Bukkit.getScheduler().runTaskLater(DF_Main.getInstance(), () -> {
            final double MUTE_RADIUS = 100.0;
            final int DURATION_TICKS = 3 * 20;
            for (Player p : center.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(center) < MUTE_RADIUS * MUTE_RADIUS) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DURATION_TICKS, 0, false, false, false));
                }
            }
        }, (long) (preEffectDurationSeconds * 20) - 40L);

        Bukkit.getScheduler().runTaskLater(DF_Main.getInstance(), () -> {
            if (fallingTrident.isValid()) fallingTrident.remove();
            startMainEffect(player, center, effectDuration, radius, normalDamagePerSecond, healthPercentDamage, pullStrength);
        }, (long) (preEffectDurationSeconds * 20));
    }

    private void startMainEffect(Player player, Location center, double duration, double radius, double normalDamagePerSecond, double healthPercentDamage, double pullStrength) {
        playAmbientSounds(center, duration);
        final Map<Location, Biome> originalBiomes = backupAndChangeBiome(center, radius);
        final List<Map<Location, BlockBackup>> originalBlockChunks = backupBlocks(center, radius);
        runEffectAnimationAndLogic(player, center, duration, radius, normalDamagePerSecond, healthPercentDamage, pullStrength, originalBiomes, originalBlockChunks);
    }

    private void playAmbientSounds(Location center, double duration) {
        final double totalSoundDuration = duration + 2.5;
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
                elapsedTicks += 4;
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 4L);

        center.getWorld().playSound(center, Sound.BLOCK_WATER_AMBIENT, 2.0f, 0.5f);

        final double soundRadiusSquared = 100 * 100;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) < soundRadiusSquared) {
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.5f);
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.55f);
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.6f);
            }
        }
    }

    private Map<Location, Biome> backupAndChangeBiome(Location center, double radius) {
        final Map<Location, Biome> originalBiomes = new HashMap<>();
        final World world = center.getWorld();
        final int centerX = center.getBlockX();
        final int centerZ = center.getBlockZ();
        final int radiusInt = (int) Math.ceil(radius);
        for (int x = centerX - radiusInt; x <= centerX + radiusInt; x++) {
            for (int z = centerZ - radiusInt; z <= centerZ + radiusInt; z++) {
                if (center.distanceSquared(new Location(world, x, center.getY(), z)) <= radius * radius) {
                    Location columnLoc = new Location(world, x, 0, z);
                    originalBiomes.put(columnLoc, world.getBiome(x, 0, z));
                    world.setBiome(x, 0, z, Biome.OCEAN);
                }
            }
        }
        return originalBiomes;
    }

    private List<Map<Location, BlockBackup>> backupBlocks(Location center, double radius) {
        final List<Map<Location, BlockBackup>> originalBlockChunks = new ArrayList<>();
        final int radiusInt = (int) Math.ceil(radius);
        final int backupRadius = radiusInt + 8;
        final double backupRadiusSq = backupRadius * backupRadius;
        final int upwardHeight = 200;
        final int downwardHeight = 50;
        final int CHUNK_HEIGHT = 10;

        for (int y_base = -downwardHeight - 1; y_base <= upwardHeight; y_base += CHUNK_HEIGHT) {
            Map<Location, BlockBackup> chunkMap = new HashMap<>();
            for (int y_offset = 0; y_offset < CHUNK_HEIGHT; y_offset++) {
                int currentYOffset = y_base + y_offset;
                if (currentYOffset > upwardHeight) break;
                for (int x = -backupRadius; x <= backupRadius; x++) {
                    for (int z = -backupRadius; z <= backupRadius; z++) {
                        if (x * x + z * z <= backupRadiusSq) {
                            Location loc = center.clone().add(x, currentYOffset, z);
                            Block block = loc.getBlock();
                            ItemStack[] contents = null;
                            if (block.getState() instanceof InventoryHolder) {
                                contents = ((InventoryHolder) block.getState()).getInventory().getContents().clone();
                            }
                            chunkMap.put(loc.clone(), new BlockBackup(block.getBlockData(), contents));
                        }
                    }
                }
            }
            if (!chunkMap.isEmpty()) {
                originalBlockChunks.add(chunkMap);
            }
        }
        return originalBlockChunks;
    }

    private void runEffectAnimationAndLogic(Player player, Location center, double duration, double radius, double normalDamagePerSecond, double healthPercentDamage, double pullStrength, Map<Location, Biome> originalBiomes, List<Map<Location, BlockBackup>> originalBlockChunks) {
        final double radiusSquared = radius * radius;
        final Map<BlockDisplay, Double> risingBars = new HashMap<>();
        final int upwardHeight = 200;
        final int downwardHeight = 50;
        final int radiusInt = (int) Math.ceil(radius);

        new BukkitRunnable() {
            int tickCounter = 0;
            int yOffset = 0;
            final int maxHeight = Math.max(upwardHeight, downwardHeight);
            final int layersPerTick = 5;

            @Override
            public void run() {
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

                if (tickCounter < duration * 20) {
                    if (yOffset < maxHeight) {
                        for (int i = 0; i < layersPerTick; i++) {
                            if (yOffset >= maxHeight) break;
                            for (double xOffset = -radius; xOffset <= radius; xOffset++) {
                                for (double zOffset = -radius; zOffset <= radius; zOffset++) {
                                    if (xOffset * xOffset + zOffset * zOffset > radiusSquared) continue;
                                    if (yOffset < upwardHeight) {
                                        Location blockLoc = center.clone().add(xOffset, yOffset, zOffset);
                                        Block block = blockLoc.getBlock();
                                        BlockData bubbleData = Material.BUBBLE_COLUMN.createBlockData();
                                        ((BubbleColumn) bubbleData).setDrag(false);
                                        block.setBlockData(bubbleData, false);
                                    }
                                    if (yOffset > 0 && yOffset <= downwardHeight) {
                                        Location blockLoc = center.clone().add(xOffset, -yOffset, zOffset);
                                        Block block = blockLoc.getBlock();
                                        BlockData bubbleData = Material.BUBBLE_COLUMN.createBlockData();
                                        ((BubbleColumn) bubbleData).setDrag(false);
                                        block.setBlockData(bubbleData, false);
                                    }
                                }
                            }
                            final int airWallRadius = radiusInt + 1;
                            for (int xOffset = -airWallRadius; xOffset <= airWallRadius; xOffset++) {
                                for (int zOffset = -airWallRadius; zOffset <= airWallRadius; zOffset++) {
                                    double distSq = xOffset * xOffset + zOffset * zOffset;
                                    if (distSq > radiusSquared && distSq <= Math.pow(airWallRadius, 2)) {
                                        if (yOffset < upwardHeight) {
                                            Location blockLoc = center.clone().add(xOffset, yOffset, zOffset);
                                            if (blockLoc.getBlock().getType() != Material.AIR) {
                                                blockLoc.getBlock().setType(Material.AIR, false);
                                            }
                                        }
                                        if (yOffset > 0 && yOffset <= downwardHeight) {
                                            Location blockLoc = center.clone().add(xOffset, -yOffset, zOffset);
                                            if (blockLoc.getBlock().getType() != Material.AIR) {
                                                blockLoc.getBlock().setType(Material.AIR, false);
                                            }
                                        }
                                    }
                                }
                            }
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

                    for (int i = 0; i < 1; i++) {
                        double radius = 9.0 + Math.random();
                        double angle = Math.random() * 2 * Math.PI;
                        Location pillarBaseLoc = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
                        double pillarSpeed = 2.0 + Math.random() * 1.5;
                        int pillarHeight = 5 + ThreadLocalRandom.current().nextInt(6);
                        for (int y = 0; y < pillarHeight; y++) {
                            Location blockLoc = pillarBaseLoc.clone().add(0, y, 0);
                            BlockDisplay newBar = center.getWorld().spawn(blockLoc, BlockDisplay.class, bar -> {
                                bar.setBlock(getRandomBlockData());
                                bar.setInterpolationDelay(-1);
                                bar.setInterpolationDuration(3);
                                bar.setTeleportDuration(3);
                            });
                            risingBars.put(newBar, pillarSpeed);
                        }
                    }

                    for (int i = 0; i < 2; i++) {
                        double radius = Math.random() * 8.0;
                        double angle = Math.random() * 2 * Math.PI;
                        Location pillarBaseLoc = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
                        double pillarSpeed = 3.0 + Math.random() * 2.0;
                        int pillarHeight = 5 + ThreadLocalRandom.current().nextInt(6);
                        for (int y = 0; y < pillarHeight; y++) {
                            Location blockLoc = pillarBaseLoc.clone().add(0, y, 0);
                            BlockDisplay newBar = center.getWorld().spawn(blockLoc, BlockDisplay.class, bar -> {
                                bar.setBlock(getRandomBlockData());
                                bar.setInterpolationDelay(-1);
                                bar.setInterpolationDuration(3);
                                bar.setTeleportDuration(3);
                            });
                            risingBars.put(newBar, pillarSpeed);
                        }
                    }

                    for (int i = 0; i < 5; i++) {
                        createSplashParticle(center, 11.0, yOffset);
                    }
                    for (int i = 0; i < 2; i++) {
                        createSplashParticle(center, 12.0, yOffset);
                    }

                    if (tickCounter % 4 == 0) {
                        final double normalDamagePerApplication = normalDamagePerSecond / 5.0;
                        for (Entity entity : center.getWorld().getNearbyEntities(center.clone().add(0, 100, 0), radius, 100, radius)) {
                            if (entity instanceof LivingEntity target && !target.getUniqueId().equals(player.getUniqueId()) && target.isValid() && !target.isDead()) {
                                Vector direction = center.toVector().subtract(target.getLocation().toVector());
                                if (direction.lengthSquared() > 1) {
                                    direction.normalize();
                                }
                                target.setVelocity(target.getVelocity().add(direction.multiply(pullStrength * 0.1)));
                                boolean isBoss = target instanceof Wither || target instanceof EnderDragon;
                                double damageMultiplier = isBoss ? 0.1 : 1.0;
                                double trueDamage = target.getHealth() * (healthPercentDamage * damageMultiplier);
                                if (trueDamage > 0) {
                                    double newHealth = Math.max(0.1, target.getHealth() - trueDamage);
                                    target.setHealth(newHealth);
                                    target.playHurtAnimation(target.getLocation().getYaw());
                                }
                                double finalNormalDamage = normalDamagePerApplication * damageMultiplier;
                                if (finalNormalDamage > 0) {
                                    ItemStack tridentItem = player.getInventory().getItemInMainHand();
                                    player.setMetadata("df_skill_weapon", new FixedMetadataValue(DF_Main.getInstance(), tridentItem.clone()));
                                    try {
                                        target.damage(finalNormalDamage, player);
                                    } finally {
                                        player.removeMetadata("df_skill_weapon", DF_Main.getInstance());
                                    }
                                }
                            }
                        }
                    }
                }

                if (tickCounter == duration * 20) {
                    startDestructionAnimation(center, upwardHeight, downwardHeight, originalBiomes, risingBars, originalBlockChunks);
                    this.cancel();
                    return;
                }
                tickCounter++;
            }

            private void createSplashParticle(Location center, double radius, double maxHeight) {
                if (maxHeight <= 0) return;
                double angle = Math.random() * 2 * Math.PI;
                double y = Math.random() * maxHeight;
                Location spawnLoc = center.clone().add(radius * Math.cos(angle), y, radius * Math.sin(angle));
                BlockDisplay splash = center.getWorld().spawn(spawnLoc, BlockDisplay.class, s -> s.setBlock(getRandomBlockData()));
                Bukkit.getScheduler().runTaskLater(DF_Main.getInstance(), () -> {
                    if (splash.isValid()) splash.remove();
                }, 6L);
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 1L);
    }

    private void startDestructionAnimation(Location center, int upwardHeight, int downwardHeight, Map<Location, Biome> originalBiomes, Map<BlockDisplay, Double> risingBars, List<Map<Location, BlockBackup>> originalBlockChunks) {
        new BukkitRunnable() {
            final int DESTRUCTION_DURATION_TICKS = originalBlockChunks.size() + 5;
            int chunkIndex = 0;
            int tickCounter = 0;
            boolean destructionFinished = false;

            @Override
            public void run() {
                if ((destructionFinished && risingBars.isEmpty()) || tickCounter > DESTRUCTION_DURATION_TICKS + 40) {
                    if (!risingBars.isEmpty()) {
                        risingBars.forEach((bar, speed) -> { if (bar.isValid()) bar.remove(); });
                        risingBars.clear();
                    }
                    for (Map.Entry<Location, Biome> entry : originalBiomes.entrySet()) {
                        Location loc = entry.getKey();
                        center.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), entry.getValue());
                    }
                    if (originalBlockChunks != null) {
                        for (Map<Location, BlockBackup> chunk : originalBlockChunks) {
                            for (Map.Entry<Location, BlockBackup> entry : chunk.entrySet()) {
                                Block block = entry.getKey().getBlock();
                                BlockBackup backup = entry.getValue();
                                block.setBlockData(backup.blockData(), false);
                                if (backup.inventoryContents() != null && block.getState() instanceof InventoryHolder) {
                                    ((InventoryHolder) block.getState()).getInventory().setContents(backup.inventoryContents());
                                }
                            }
                        }
                    }
                    activeEffectCenters.remove(center);
                    this.cancel();
                    return;
                }

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

                if (!destructionFinished) {
                    if (tickCounter % 4 == 0) {
                        int yOffset = -downwardHeight + (chunkIndex * 10);
                        Location soundLoc = center.clone().add(0, yOffset, 0);
                        center.getWorld().playSound(soundLoc, Sound.ENTITY_GENERIC_BURN, 2.0f, 0.8f);
                    }
                    if (chunkIndex < originalBlockChunks.size()) {
                        Map<Location, BlockBackup> chunkToRestore = originalBlockChunks.get(chunkIndex);
                        for (Map.Entry<Location, BlockBackup> entry : chunkToRestore.entrySet()) {
                            Block block = entry.getKey().getBlock();
                            BlockBackup backup = entry.getValue();
                            block.setBlockData(backup.blockData(), false);
                            if (backup.inventoryContents() != null && block.getState() instanceof InventoryHolder) {
                                ((InventoryHolder) block.getState()).getInventory().setContents(backup.inventoryContents());
                            }
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