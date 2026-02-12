package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.rift.RiftManager;
import cjs.DF_Plugin.pylon.beaconinteraction.PylonAreaManager;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HarpoonAbility implements ISpecialAbility {

    private final Map<UUID, Long> divingPlayers = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Override
    public String getInternalName() {
        return "harpoon";
    }

    @Override
    public String getDisplayName() {
        return "§c작살";
    }

    @Override
    public String getDescription() {
        return "§7공중에서 웅크려 기력 30%를 소모하고 급강하, 충격파를 일으킵니다.";
    }

    @Override
    public double getCooldown() {
        return 1.0;
    }

    @Override
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event, Player player, ItemStack item) {
        if (!event.isSneaking()) {
            return;
        }

        UpgradeManager upgradeManager = DF_Main.getInstance().getUpgradeManager();
        if (!upgradeManager.isSpear(item) || upgradeManager.getUpgradeLevel(item) < UpgradeManager.MAX_UPGRADE_LEVEL) {
            return;
        }

        if (player.isOnGround() || !isHighEnough(player)) {
            return;
        }

        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        int level = upgradeManager.getUpgradeLevel(item);
        double maxGauge = level * SpecialAbilityManager.GAUGE_PER_LEVEL;
        double currentGauge = manager.getAbilityGauge(player, "lunge");
        double requiredGauge = maxGauge * 0.3;

        if (currentGauge < requiredGauge) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
            return;
        }

        if (!manager.tryUseAbility(player, this, item)) return;

        manager.setAbilityGauge(player, "lunge", currentGauge - requiredGauge);
        executeAerialDive(player);
    }

    private boolean isHighEnough(Player player) {
        Location loc = player.getLocation();
        for (int i = 1; i <= 5; i++) {
            if (!loc.clone().subtract(0, i, 0).getBlock().isPassable()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, Player player, ItemStack item) {
        if (!divingPlayers.containsKey(player.getUniqueId())) return;

        if (player.isOnGround()) {
            float fallDistance = player.getFallDistance();
            divingPlayers.remove(player.getUniqueId());
            player.setFallDistance(0);

            if (fallDistance > 3) {
                handleImpact(player, fallDistance);
            }
        } else {
            renderDiveEffect(player);
        }
    }

    public void executeAerialDive(Player player) {
        divingPlayers.put(player.getUniqueId(), System.currentTimeMillis());
        player.setVelocity(new Vector(0, -2.5, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.5f);
    }

    private void renderDiveEffect(Player player) {
        Long startTime = divingPlayers.get(player.getUniqueId());
        if (startTime == null) return;
        long duration = System.currentTimeMillis() - startTime;
        float progress = Math.min(duration / 1500f, 1.0f);

        Color displayColor;
        if (progress < 0.5) {
            float subProgress = progress / 0.5f;
            displayColor = Color.fromRGB(255, (int) (255 * (1 - subProgress)), (int) (255 * (1 - subProgress)));
        } else {
            float subProgress = (progress - 0.5f) / 0.5f;
            displayColor = Color.fromRGB(255, 0, (int) (128 * subProgress));
        }

        Location playerLoc = player.getLocation();
        double baseRadius = 0.1, maxRadius = 0.6, heightRange = 2.0;

        for (int i = 0; i < 30; i++) {
            double yOffset = -0.5 - (random.nextDouble() * heightRange);
            double currentRadius = baseRadius + (maxRadius - baseRadius) * ((-yOffset - 0.5) / heightRange);
            double angle = random.nextDouble() * 2 * Math.PI;
            double x = currentRadius * Math.cos(angle);
            double z = currentRadius * Math.sin(angle);

            Location particleSpawnLoc = playerLoc.clone().add(x, yOffset, z);
            player.getWorld().spawnParticle(Particle.DUST, particleSpawnLoc, 1, new Particle.DustOptions(displayColor, 1.5f));
            player.getWorld().spawnParticle(Particle.FLAME, particleSpawnLoc, 1, 0, 0, 0, 0.01);
            if (random.nextDouble() < 0.2) {
                player.getWorld().spawnParticle(Particle.LAVA, particleSpawnLoc, 1, 0, 0, 0, 0);
            }
        }
    }

    private void handleImpact(Player player, float fallDistance) {
        Location impactLocation = player.getLocation();

        ItemStack weapon = player.getInventory().getItemInMainHand();
        double bonusDamage = 0;
        double baseSpearDamage = 0;

        if (weapon != null && weapon.hasItemMeta()) {
            bonusDamage = weapon.getItemMeta().getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
            baseSpearDamage = getBaseDamageModifier(weapon.getType());
        }

        // 창의 총 공격력 = 기본 공격력 + 용의 심장 추가 공격력 + 기본 스탯 1
        double totalSpearDamage = baseSpearDamage + bonusDamage + 1.0;

        // 낙하 거리 기반 대미지
        double fallBasedDamage = Math.min(50, Math.pow(fallDistance, 2) / 5.0);

        // 최종 대미지 계산
        double damage = fallBasedDamage * (1 + totalSpearDamage / 10.0);
        double knockback = fallDistance;

        impactLocation.getWorld().playSound(impactLocation, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        for (int i = 0; i < 2; i++) {
            Location effectLoc = impactLocation.clone().add(random.nextDouble() * 1.5 - 0.75, 0.5, random.nextDouble() * 1.5 - 0.75);
            Block relative = impactLocation.getBlock().getRelative(0, -1, 0);
            if (relative.getType().isSolid()) {
                effectLoc.getWorld().spawnParticle(Particle.BLOCK, effectLoc, 50, 0.5, 0.5, 0.5, 0.1, relative.getBlockData());
            }
            effectLoc.getWorld().spawnParticle(Particle.CRIT, effectLoc, 30, 0.5, 0.5, 0.5, 0.1);
        }

        impactLocation.getWorld().getNearbyEntities(impactLocation, 5, 5, 5).forEach(entity -> {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                player.setMetadata("df_skill_damage", new FixedMetadataValue(DF_Main.getInstance(), true));
                try {
                    ((LivingEntity) entity).damage(damage, player);
                    Vector direction = entity.getLocation().toVector().subtract(impactLocation.toVector()).normalize();
                    direction.setY(0.5).multiply(Math.min(knockback / 20.0, 5.0));
                    entity.setVelocity(direction);
                } finally {
                    player.removeMetadata("df_skill_damage", DF_Main.getInstance());
                }
            }
        });

        int radius = 4;
        double velocityMultiplier = Math.min(1.2, 0.6 + (fallDistance / 50.0));
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radius * radius) continue;
                    Block block = impactLocation.clone().add(x, y, z).getBlock();
                    Material blockType = block.getType();

                    // 파일런 구조물인지 확인
                    PylonAreaManager pylonAreaManager = DF_Main.getInstance().getPylonAreaManager();
                    if (pylonAreaManager.isPylonStructureBlock(block.getLocation())) {
                        continue;
                    }

                    // 균열 제단 블록인지 확인
                    RiftManager riftManager = DF_Main.getInstance().getRiftManager();
                    if (riftManager.isAltarBlock(block.getLocation())) {
                        continue;
                    }

                    if (block.isLiquid() || blockType == Material.AIR || blockType == Material.BEDROCK || blockType == Material.BEACON || blockType == Material.DRAGON_EGG || blockType == Material.END_PORTAL_FRAME || blockType == Material.END_PORTAL || blockType == Material.NETHER_PORTAL) {
                        continue;
                    }
                    FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(block.getLocation().add(0.5, 0, 0.5), block.getBlockData());
                    block.setType(Material.AIR);
                    Vector direction = new Vector(x, Math.abs(y) + 1.0, z).normalize();
                    fallingBlock.setVelocity(direction.multiply(velocityMultiplier * (0.8 + random.nextDouble() * 0.5)));
                    fallingBlock.setDropItem(false);
                }
            }
        }
    }

    private double getBaseDamageModifier(Material material) {
        String prefix = getMaterialPrefix(material);
        return switch (prefix) {
            case "WOODEN", "GOLDEN" -> 0.0;
            case "STONE", "COPPER" -> 1.0;
            case "IRON" -> 2.0;
            case "DIAMOND" -> 3.0;
            case "NETHERITE", "TRIDENT" -> 4.0;
            default -> 0.0;
        };
    }

    private String getMaterialPrefix(Material material) {
        String name = material.name();
        int underscoreIndex = name.indexOf('_');
        if (underscoreIndex > 0) {
            return name.substring(0, underscoreIndex);
        }
        return name;
    }

    @Override
    public void onCleanup(Player player) {
        divingPlayers.remove(player.getUniqueId());
    }
}