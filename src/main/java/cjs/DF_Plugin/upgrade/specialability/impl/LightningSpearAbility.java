package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import cjs.DF_Plugin.item.CustomItemFactory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LightningSpearAbility implements ISpecialAbility {

    @Override
    public String getInternalName() {
        return "lightning_spear";
    }

    @Override
    public String getDisplayName() {
        return "§e뇌창";
    }

    @Override
    public String getDescription() {
        return "§7공전하는 뇌창을 발사합니다. 5스택 시 번개가 내리칩니다.";
    }

    @Override
    public double getCooldown() {
        return 0;
    }

    @Override
    public int getMaxCharges() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getInt("upgrade.special-abilities.lightning_spear.max-charges", 5);
    }

    @Override
    public boolean showInActionBar() {
        return true;
    }

    private record StackKey(UUID targetId, UUID attackerId) {}
    private record StackInfo(int count, BukkitTask expiryTask) {}

    private final Map<UUID, List<Trident>> floatingTridents = new ConcurrentHashMap<>();
    private final Map<StackKey, StackInfo> embeddedStacks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> animationTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Double> animationAngles = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Trident>> activeProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> nextFireIndex = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> projectileReturnTasks = new ConcurrentHashMap<>();

    public static final String PROJECTILE_META_KEY = "df_lightning_spear_projectile";
    public static final String FLOATING_TRIDENT_META_KEY = "df_floating_trident";

    @Override
    public void onEquip(Player player, ItemStack item) {
        getManager().setChargeVisibility(player, this, true);
        initialize(player);
    }

    @Override
    public void onCleanup(Player player) {
        UUID uuid = player.getUniqueId();
        clearFloatingTridents(player);

        Set<Trident> projectiles = activeProjectiles.remove(uuid);
        if (projectiles != null) {
            for (Trident trident : projectiles) {
                if (trident != null && trident.isValid()) {
                    BukkitTask returnTask = projectileReturnTasks.remove(trident.getUniqueId());
                    if (returnTask != null) {
                        returnTask.cancel();
                    }
                    trident.remove();
                }
            }
        }

        BukkitTask animationTask = animationTasks.remove(uuid);
        if (animationTask != null) animationTask.cancel();

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "df_ls_" + player.getUniqueId().toString().substring(0, 10);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }

        nextFireIndex.remove(uuid);
        animationAngles.remove(uuid);

        embeddedStacks.entrySet().removeIf(entry -> {
            if (entry.getKey().attackerId().equals(uuid)) {
                entry.getValue().expiryTask().cancel();
                return true;
            }
            return false;
        });

        getManager().setChargeVisibility(player, this, false);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction().isLeftClick()) {
            event.setCancelled(true);
            fireTrident(player);
        }
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event, Player player, ItemStack item) {
        if (event.getEntity() instanceof Trident trident) {
            trident.setMetadata("trident_mode", new FixedMetadataValue(DF_Main.getInstance(), getInternalName()));
        }
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        if (!(event.getDamager() instanceof Trident trident) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        boolean isSpecialProjectile = trident.hasMetadata(PROJECTILE_META_KEY);
        boolean isVanillaThrowInMode = false;
        if (trident.hasMetadata("trident_mode")) {
            if ("lightning_spear".equals(trident.getMetadata("trident_mode").getFirst().asString())) {
                isVanillaThrowInMode = true;
            }
        }

        if (!isSpecialProjectile && !isVanillaThrowInMode) {
            return;
        }

        UUID ownerId = null;
        if (isSpecialProjectile) {
            Object metadataValue = trident.getMetadata(PROJECTILE_META_KEY).getFirst().value();
            if (metadataValue instanceof UUID) {
                ownerId = (UUID) metadataValue;
            }
        } else if (trident.getShooter() instanceof Player) {
            ownerId = ((Player) trident.getShooter()).getUniqueId();
        }

        if (ownerId != null && target.getUniqueId().equals(ownerId)) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTaskLater(DF_Main.getInstance(), () -> {
            if (target.isValid() && !target.isDead()) {
                target.setNoDamageTicks(0);
            }
        }, 1L);

        if (isSpecialProjectile) {
            // [수정] 플레이어의 추가 공격력을 가져와 능력 데미지에 합산합니다.
            ItemStack weapon = player.getInventory().getItemInMainHand();
            double bonusDamageFromStats = 0;
            if (weapon.hasItemMeta()) {
                bonusDamageFromStats = weapon.getItemMeta().getPersistentDataContainer()
                        .getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
            }

            double extraDamage = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.extra-physical-damage", 10.0);
            // 기본 삼지창 피해 + 추가 물리 피해 + 플레이어의 추가 공격력
            event.setDamage(event.getDamage() + extraDamage + bonusDamageFromStats);

            StackKey stackKey = new StackKey(target.getUniqueId(), player.getUniqueId());
            StackInfo oldInfo = embeddedStacks.get(stackKey);
            if (oldInfo != null) {
                oldInfo.expiryTask().cancel();
            }
            int newStackCount = (oldInfo != null) ? oldInfo.count() + 1 : 1;

            long stackDurationTicks = (long) (DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.stack-duration-seconds", 30.0) * 20);

            float particleSize = 0.6f + (newStackCount * 0.2f);
            Particle.DustOptions dustOptions = new Particle.DustOptions(Color.YELLOW, particleSize);
            target.getWorld().spawnParticle(Particle.DUST, target.getEyeLocation().add(0, 0.5, 0), 1, dustOptions);

            if (newStackCount >= getMaxCharges()) {
                embeddedStacks.remove(stackKey);

                double finalDamage = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.lightning-damage", 40.0);
                // 5스택 번개 피해에도 플레이어의 추가 공격력을 더합니다.
                finalDamage += bonusDamageFromStats;
                target.getWorld().strikeLightningEffect(target.getLocation()); // 시각 효과만 있는 번개
                target.damage(finalDamage, player);

                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.4f);
            } else {
                final BukkitTask expiryTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        StackInfo latestInfo = embeddedStacks.get(stackKey);
                        if (latestInfo != null && latestInfo.expiryTask().getTaskId() == this.getTaskId()) {
                            embeddedStacks.remove(stackKey);
                            target.getWorld().spawnParticle(Particle.LARGE_SMOKE, target.getEyeLocation(), 5, 0.2, 0.2, 0.2, 0.01);
                        }
                    }
                }.runTaskLater(DF_Main.getInstance(), stackDurationTicks);

                embeddedStacks.put(stackKey, new StackInfo(newStackCount, expiryTask));
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT, 0.8f, 1.2f);
            }
        }
    }

    private void initialize(Player player) {
        UUID uuid = player.getUniqueId();

        Team team = getOrCreatePlayerCollisionTeam(player);
        if (!team.hasEntry(player.getUniqueId().toString())) {
            team.addEntry(player.getUniqueId().toString());
        }

        if (floatingTridents.containsKey(uuid) && floatingTridents.get(uuid).stream().anyMatch(Objects::nonNull)) {
            return;
        }

        setCharges(player, getMaxCharges());

        nextFireIndex.put(uuid, 0);

        spawnFloatingTridents(player);
        startAnimationTask(player);
    }

    private void fireTrident(Player player) {
        int charges = getCharges(player);
        if (charges <= 0) {
            return;
        }

        UUID uuid = player.getUniqueId();
        List<Trident> tridents = floatingTridents.get(uuid);

        int startIndex = nextFireIndex.getOrDefault(uuid, 0);
        Trident tridentToRemove = null;
        int fireIndex = -1;

        if (tridents != null) {
            for (int i = 0; i < tridents.size(); i++) {
                int currentIndex = (startIndex + i) % tridents.size();
                Trident currentTrident = tridents.get(currentIndex);
                if (currentTrident != null && currentTrident.isValid()) {
                    tridentToRemove = currentTrident;
                    fireIndex = currentIndex;
                    break;
                }
            }
        }

        if (tridentToRemove == null) {
            return;
        }

        setCharges(player, charges - 1);
        nextFireIndex.put(uuid, (fireIndex + 1) % getMaxCharges());
        tridents.set(fireIndex, null);
        Location removalLocation = tridentToRemove.getLocation();
        tridentToRemove.remove();

        Trident projectile;
        player.setMetadata("df_is_firing_special", new FixedMetadataValue(DF_Main.getInstance(), true));
        try {
            Vector direction = player.getEyeLocation().getDirection().normalize();
            projectile = player.launchProjectile(Trident.class, direction.multiply(3.0));

            projectile.setShooter(player);
            projectile.setMetadata(PROJECTILE_META_KEY, new FixedMetadataValue(DF_Main.getInstance(), player.getUniqueId()));
            projectile.setGravity(true);
            projectile.setPierceLevel((byte) 0);
            projectile.setLoyaltyLevel(3);
            projectile.setPickupStatus(Trident.PickupStatus.DISALLOWED);
        } finally {
            player.removeMetadata("df_is_firing_special", DF_Main.getInstance());
        }

        activeProjectiles.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(projectile);

        player.getWorld().playSound(removalLocation, Sound.ITEM_TRIDENT_THROW, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, removalLocation, 8, 0.1, 0.1, 0.1, 0.05);

        trackAndHandleReturn(projectile, player);
    }

    private void trackAndHandleReturn(Trident trident, Player player) {
        BukkitTask returnTask = new BukkitRunnable() {
            int ticksLived = 0;

            private void cleanupAndCancel() {
                projectileReturnTasks.remove(trident.getUniqueId());
                Set<Trident> projectiles = activeProjectiles.get(player.getUniqueId());
                if (projectiles != null) {
                    projectiles.remove(trident);
                }
                this.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    if (trident.isValid()) trident.remove();
                    cleanupAndCancel();
                    return;
                }

                if (!trident.isValid()) {
                    cleanupAndCancel();
                    return;
                }

                if (!trident.getWorld().equals(player.getWorld())) {
                    if (trident.isValid()) {
                        trident.remove();
                    }
                    cleanupAndCancel();
                    return;
                }

                if (ticksLived++ > 10 && trident.getLocation().distanceSquared(player.getEyeLocation()) < 2.0 * 2.0) {
                    assimilateReturningTrident(trident, player);
                    cleanupAndCancel();
                }
            }
        }.runTaskTimer(DF_Main.getInstance(), 1L, 1L);

        projectileReturnTasks.put(trident.getUniqueId(), returnTask);
    }

    private void assimilateReturningTrident(Trident returningTrident, Player player) {
        int current = getCharges(player);
        int max = getMaxCharges();

        if (current >= max) {
            if (returningTrident.isValid()) returningTrident.remove();
            return;
        }

        if (returningTrident.isValid()) {
            returningTrident.remove();
        }

        setCharges(player, current + 1);

        List<Trident> tridents = floatingTridents.get(player.getUniqueId());
        if (tridents != null) {
            int emptySlot = tridents.indexOf(null);
            if (emptySlot != -1) {
                tridents.set(emptySlot, spawnSingleFloatingTrident(player, emptySlot));
            }
        } else {
            initialize(player);
        }
    }

    private void rechargeOneCharge(Player player) {
        if (!player.isOnline()) return;
        int current = getCharges(player);
        int max = getMaxCharges();
        if (current >= max) return;

        setCharges(player, current + 1);

        List<Trident> tridents = floatingTridents.get(player.getUniqueId());
        if (tridents != null) {
            int emptySlot = tridents.indexOf(null);
            if (emptySlot != -1) {
                tridents.set(emptySlot, spawnSingleFloatingTrident(player, emptySlot));
            }
        } else {
            initialize(player);
        }
    }

    private void checkAndRegenerateTridents(Player player) {
        int charges = getCharges(player);
        List<Trident> tridents = floatingTridents.get(player.getUniqueId());

        if (tridents != null) {
            for (int i = 0; i < tridents.size(); i++) {
                Trident t = tridents.get(i);
                if (t != null && !t.isValid()) {
                    tridents.set(i, null);
                }
            }
        } else {
            return;
        }

        long actualTridentCount = tridents.stream().filter(Objects::nonNull).count();

        if (charges > actualTridentCount) {
            spawnAndAssimilateOneTrident(player);
        }
    }

    private void spawnAndAssimilateOneTrident(Player player) {
        List<Trident> tridents = floatingTridents.get(player.getUniqueId());
        if (tridents == null) {
            return;
        }

        int emptySlot = tridents.indexOf(null);
        if (emptySlot == -1) {
            return;
        }

        Trident newTrident = spawnSingleFloatingTrident(player, emptySlot, true);
        tridents.set(emptySlot, newTrident);
    }

    private void startAnimationTask(Player player) {
        UUID uuid = player.getUniqueId();
        animationTasks.computeIfAbsent(uuid, k -> new BukkitRunnable() {
            private int tickCounter = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    onCleanup(player);
                    return;
                }

                if (tickCounter++ % 20 == 0) {
                    checkAndRegenerateTridents(player);
                }

                List<Trident> tridents = floatingTridents.get(uuid);
                if (tridents == null) return;
                updateIdleAnimation(player, tridents);
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 1L));
    }

    private void updateIdleAnimation(Player player, List<Trident> tridents) {
        UUID uuid = player.getUniqueId();

        Team team = getOrCreatePlayerCollisionTeam(player);
        if (!team.hasEntry(player.getUniqueId().toString())) {
            team.addEntry(player.getUniqueId().toString());
        }

        double currentAngleDegrees = animationAngles.getOrDefault(uuid, 0.0);
        animationAngles.put(uuid, (currentAngleDegrees + 3.0) % 360);

        Location playerLoc = player.getLocation();
        float playerYaw = player.getLocation().getYaw();
        double radius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.orbit-radius", 1.9);
        double sprintRadius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.sprint-orbit-radius", 1.5);
        int maxCharges = getMaxCharges();
        boolean isSprinting = player.isSprinting();

        for (int i = 0; i < tridents.size(); i++) {
            Trident trident = tridents.get(i);
            if (trident == null || !trident.isValid()) continue;

            if (!trident.getWorld().equals(player.getWorld())) {
                trident.remove();
                tridents.set(i, null);
                rechargeOneCharge(player);
                continue;
            }

            if (!team.hasEntry(trident.getUniqueId().toString())) {
                team.addEntry(trident.getUniqueId().toString());
            }

            double targetAngle = Math.toRadians(currentAngleDegrees + (360.0 / maxCharges) * i);
            double currentRadius = isSprinting ? sprintRadius : radius;
            double yOffset = isSprinting ? 2.5 : 1.2;

            Vector pos = new Vector(Math.cos(targetAngle) * currentRadius, 0, Math.sin(targetAngle) * currentRadius);
            pos.setY(Math.sin(targetAngle * 2) * 0.25);
            pos.rotateAroundY(Math.toRadians(-playerYaw));

            Location targetLoc = playerLoc.clone().add(pos).add(0, yOffset, 0);

            double maxAllowedDistanceSq = (currentRadius + 3.0) * (currentRadius + 3.0);
            boolean isStray = trident.getLocation().distanceSquared(player.getEyeLocation()) > maxAllowedDistanceSq;

            boolean isTargetObstructed = targetLoc.getBlock().getType().isSolid();

            if (isStray || isTargetObstructed) {
                trident.teleport(targetLoc);
                trident.setVelocity(new Vector(0, 0, 0));
            } else {
                Vector velocity = targetLoc.toVector().subtract(trident.getLocation().toVector());
                double correctionStrength = 0.4;
                Vector finalVelocity = velocity.multiply(correctionStrength);

                if (finalVelocity.lengthSquared() > 4.0) {
                    finalVelocity.normalize().multiply(2.0);
                }
                trident.setVelocity(finalVelocity);
            }
        }
    }

    private void spawnFloatingTridents(Player player) {
        clearFloatingTridents(player);
        int maxCharges = getMaxCharges();

        List<Trident> tridents = new ArrayList<>(maxCharges);
        for (int i = 0; i < maxCharges; i++) {
            tridents.add(spawnSingleFloatingTrident(player, i));
        }
        floatingTridents.put(player.getUniqueId(), tridents);
    }

    private Trident spawnSingleFloatingTrident(Player player, int orbitIndex) {
        return spawnSingleFloatingTrident(player, orbitIndex, false);
    }

    private Trident spawnSingleFloatingTrident(Player player, int orbitIndex, boolean fromAfar) {
        UUID uuid = player.getUniqueId();
        double currentAngleDegrees = animationAngles.getOrDefault(uuid, 0.0);
        int maxCharges = getMaxCharges();
        double radius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.orbit-radius", 1.9);
        double sprintRadius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.sprint-orbit-radius", 1.5);
        double targetAngle = Math.toRadians(currentAngleDegrees + (360.0 / maxCharges) * orbitIndex);

        Location playerLoc = player.getLocation();
        float playerYaw = player.getLocation().getYaw();
        boolean isSprinting = player.isSprinting();

        double baseRadius = isSprinting ? sprintRadius : radius;
        double spawnRadius = fromAfar ? baseRadius + 4.0 : baseRadius;
        double yOffset = isSprinting ? 2.5 : 1.2;

        Vector pos = new Vector(Math.cos(targetAngle) * spawnRadius, 0, Math.sin(targetAngle) * spawnRadius);
        pos.setY(Math.sin(targetAngle * 2) * 0.25);
        pos.rotateAroundY(Math.toRadians(-playerYaw));

        Location spawnLoc = playerLoc.clone().add(pos).add(0, yOffset, 0);

        Trident trident = (Trident) player.getWorld().spawnEntity(spawnLoc, EntityType.TRIDENT);
        trident.setGravity(false);
        trident.setInvulnerable(true);
        trident.setPickupStatus(Trident.PickupStatus.DISALLOWED);
        trident.setMetadata(FLOATING_TRIDENT_META_KEY, new FixedMetadataValue(DF_Main.getInstance(), player.getUniqueId()));
        trident.setPierceLevel((byte) 127);

        Team team = getOrCreatePlayerCollisionTeam(player);
        if (!team.hasEntry(trident.getUniqueId().toString())) {
            team.addEntry(trident.getUniqueId().toString());
        }
        return trident;
    }

    private void clearFloatingTridents(Player player) {
        List<Trident> tridents = floatingTridents.remove(player.getUniqueId());
        if (tridents != null) {
            tridents.stream().filter(Objects::nonNull).filter(Trident::isValid).forEach(Trident::remove);
        }
    }

    private Team getOrCreatePlayerCollisionTeam(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "df_ls_" + player.getUniqueId().toString().substring(0, 10);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            team.setAllowFriendlyFire(false);
        }
        return team;
    }

    private SpecialAbilityManager getManager() {
        return DF_Main.getInstance().getSpecialAbilityManager();
    }

    private int getCharges(Player player) {
        SpecialAbilityManager.ChargeInfo info = getManager().getChargeInfo(player, this);
        return (info != null) ? info.current() : getMaxCharges();
    }

    private void setCharges(Player player, int amount) {
        getManager().setChargeInfo(player, this, amount, getMaxCharges());
    }

    public static void cleanupAllLingeringTridents() {
        for (World world : Bukkit.getServer().getWorlds()) {
            for (Trident trident : world.getEntitiesByClass(Trident.class)) {
                if (trident.hasMetadata(FLOATING_TRIDENT_META_KEY) || trident.hasMetadata(PROJECTILE_META_KEY)) {
                    trident.remove();
                }
            }
        }
    }
}