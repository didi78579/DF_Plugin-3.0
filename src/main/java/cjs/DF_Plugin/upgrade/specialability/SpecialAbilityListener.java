package cjs.DF_Plugin.upgrade.specialability;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

public class SpecialAbilityListener implements Listener {

    private final DF_Main plugin;
    private final SpecialAbilityManager specialAbilityManager;
    private final cjs.DF_Plugin.upgrade.UpgradeManager upgradeManager;

    public SpecialAbilityListener(DF_Main plugin) {
        this.plugin = plugin;
        this.specialAbilityManager = plugin.getSpecialAbilityManager();
        this.upgradeManager = plugin.getUpgradeManager();
    }

    /**
     * Handles player-specific data cleanup when they quit the server.
     * @param event The player quit event.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        specialAbilityManager.cleanupPlayer(event.getPlayer());
    }

    /**
     * Handles player-specific data cleanup when they die.
     * @param event The player death event.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        specialAbilityManager.cleanupPlayer(event.getEntity());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // Q키를 사용하는 모든 능력(예: 작살)에 이벤트를 전달합니다.
        dispatchAbilityEvent(droppedItem, (ability, isMain) -> ability.onPlayerDropItem(event, player, droppedItem));
    }

    /**
     * Handles ability activation when a player interacts with an item (left/right click).
     * This is a primary trigger for most active abilities.
     * @param event The player interaction event.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        dispatchInteractAbilityEvent(item, (ability, isMain) -> ability.onPlayerInteract(event, player, item));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        // 이벤트는 아이템 스왑이 '완료된 후'의 상태를 기준으로 하므로,
        // 원래 주 손에 있던 아이템은 이제 보조 손에 있습니다.
        ItemStack originalMainHandItem = event.getOffHandItem();

        // 1. 삼지창(Trident) 모드 변경을 최우선으로 처리합니다.
        // 원래 주 손에 10강 이상인 삼지창이 있었는지 확인합니다.
        if (originalMainHandItem != null && originalMainHandItem.getType() == Material.TRIDENT && upgradeManager.getUpgradeLevel(originalMainHandItem) >= UpgradeManager.MAX_UPGRADE_LEVEL) {
            event.setCancelled(true); // 실제 아이템 스왑을 막습니다.

            // 모드 변경 로직을 실행합니다.
            ItemStack newWeapon = upgradeManager.switchTridentMode(player, originalMainHandItem);

            // 모드가 변경된 아이템을 다시 주 손에 설정합니다.
            player.getInventory().setItemInMainHand(newWeapon);
            return; // 삼지창 모드 변경이 실행되었으므로, 다른 로직은 처리하지 않습니다.
        }

        // 2. '도구 벨트' 능력 처리 (바지)
        // 삼지창 모드 변경이 아닐 경우에만 실행됩니다.
        ItemStack leggings = player.getInventory().getLeggings();
        dispatchInteractAbilityEvent(leggings, (ability, isMain) -> {
            // 이벤트가 이미 다른 플러그인에 의해 취소되었다면 추가 로직을 실행하지 않습니다.
            if (!event.isCancelled()) {
                ability.onPlayerSwapHandItems(event, player, leggings);
            }
        });
    }


    private double getDamageReductionFromArmor(Player player, String reductionType) {
        double totalReduction = 0;
        NamespacedKey key = "generic".equals(reductionType) ? CustomItemFactory.BONUS_GENERIC_REDUCTION_KEY : CustomItemFactory.BONUS_SKILL_REDUCTION_KEY;

        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece == null || !armorPiece.hasItemMeta()) {
                continue;
            }

            // 1. 강화 레벨에 따른 피해 감소 (흉갑/각반)
            if ("generic".equals(reductionType) && armorPiece.getType().name().endsWith("_CHESTPLATE")) {
                totalReduction += (upgradeManager.getUpgradeLevel(armorPiece) / 100.0);
            } else if ("skill".equals(reductionType) && armorPiece.getType().name().endsWith("_LEGGINGS")) {
                totalReduction += (upgradeManager.getUpgradeLevel(armorPiece) / 100.0);
            }

            // 2. '악마의 영혼'으로 부여된 추가 피해 감소 (모든 부위)
            double bonusReduction = armorPiece.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.DOUBLE, 0.0);
            totalReduction += bonusReduction;
        }
        return totalReduction;
    }

    private double getSkillDamageReductionFromArmor(Player player) {
        double totalReduction = 0;
        NamespacedKey key = CustomItemFactory.BONUS_SKILL_REDUCTION_KEY;

        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece == null || !armorPiece.hasItemMeta()) {
                continue;
            }

            // 1. 강화 레벨에 따른 피해 감소 (각반)
            if (armorPiece.getType().name().endsWith("_LEGGINGS")) {
                totalReduction += (upgradeManager.getUpgradeLevel(armorPiece) / 100.0);
            }

            // 2. '악마의 영혼'으로 부여된 추가 피해 감소 (모든 부위)
            double bonusReduction = armorPiece.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.DOUBLE, 0.0);
            totalReduction += bonusReduction;
        }
        return totalReduction;
    }

    /**
     * Determines if the damage source is from a custom skill.
     * @param event The damage event.
     * @return True if the damage is from a skill, false otherwise.
     */
    private boolean isSkillDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player attacker) {
            // A direct attack from a player is only a skill if explicitly marked.
            return attacker.hasMetadata("df_skill_damage");
        }
        // Any damage not from a direct player melee attack (e.g., projectiles, AoE) is considered skill damage.
        return !(damager instanceof Player);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // 플레이어가 착용한 모든 장비의 능력을 확인 (예: 낙하 데미지 면역)
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            dispatchInteractAbilityEvent(armor, (ability, isMain) -> ability.onEntityDamage(event, player, armor));
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // [버그 수정] 공전하는 뇌창이 어떤 이유로든(팀 충돌 실패, 서버 랙 등) 시전자를 공격하는 것을 원천적으로 방지합니다.
        if (event.getDamager() instanceof Trident trident && event.getEntity() instanceof Player victim) {
            if (trident.hasMetadata("df_floating_trident")) {
                Object ownerUUIDValue = trident.getMetadata("df_floating_trident").getFirst().value();
                if (ownerUUIDValue instanceof UUID && victim.getUniqueId().equals(ownerUUIDValue)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // --- 공격자(Attacker)의 능력 처리 ---
        findAttackerAndWeapon(event).ifPresent(attackerAndWeapon -> {
            Player attacker = attackerAndWeapon.player();
            ItemStack weapon = attackerAndWeapon.weapon();

            if (upgradeManager.getUpgradeLevel(weapon) >= UpgradeManager.MAX_UPGRADE_LEVEL) {
                specialAbilityManager.getAbilityFromItem(weapon)
                        .ifPresent(ability -> ability.onDamageByEntity(event, attacker, weapon));
            }
        });

        // --- 피격자(Victim)의 능력 처리 ---
        if (event.getEntity() instanceof Player victim) {
            double damage = event.getDamage();
            if (isSkillDamage(event)) {
                double reduction = getSkillDamageReductionFromArmor(victim);
                damage *= (1.0 - reduction);
            } else if (event.getDamager() instanceof LivingEntity) {
                double reduction = getDamageReductionFromArmor(victim, "generic");
                damage *= (1.0 - reduction);
            }
            event.setDamage(damage);

            for (ItemStack armor : victim.getInventory().getArmorContents()) {
                if (armor == null || armor.getType() == Material.AIR) continue;
                dispatchAbilityEvent(armor, (ability, isMain) -> ability.onDamageByEntity(event, victim, armor));
            }

            handleVictimHeldItem(event, victim, victim.getInventory().getItemInMainHand());
            handleVictimHeldItem(event, victim, victim.getInventory().getItemInOffHand());
        }
    }

    private record AttackerAndWeapon(Player player, ItemStack weapon) {}

    private Optional<AttackerAndWeapon> findAttackerAndWeapon(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            return Optional.of(new AttackerAndWeapon(attacker, attacker.getInventory().getItemInMainHand()));
        }

        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player attacker) {
            ItemStack mainHand = attacker.getInventory().getItemInMainHand();
            ItemStack offHand = attacker.getInventory().getItemInOffHand();

            if (projectile instanceof Arrow) {
                if (mainHand.getType() == Material.BOW || mainHand.getType() == Material.CROSSBOW) {
                    return Optional.of(new AttackerAndWeapon(attacker, mainHand));
                }
                if (offHand.getType() == Material.BOW || offHand.getType() == Material.CROSSBOW) {
                    return Optional.of(new AttackerAndWeapon(attacker, offHand));
                }
            } else if (projectile instanceof Trident) { // 창(Spear)과 삼지창(Trident) 모두 Trident 투사체를 사용
                if (upgradeManager.isSpear(mainHand) || mainHand.getType() == Material.TRIDENT) {
                    return Optional.of(new AttackerAndWeapon(attacker, mainHand));
                }
                if (upgradeManager.isSpear(offHand) || offHand.getType() == Material.TRIDENT) {
                    return Optional.of(new AttackerAndWeapon(attacker, offHand));
                }
            }
            // 다른 투사체(예: 윈드차지)의 경우, 주 손 아이템을 기본으로 확인
            return Optional.of(new AttackerAndWeapon(attacker, mainHand));
        }
        return Optional.empty();
    }

    private void handleVictimHeldItem(EntityDamageByEntityEvent event, Player victim, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        Material itemType = item.getType();
        if (itemType == Material.SHIELD || itemType == Material.FISHING_ROD) {
            dispatchAbilityEvent(item, (ability, isMain) -> ability.onDamageByEntity(event, victim, item));
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        ItemStack boots = player.getInventory().getBoots();
        Optional<ISpecialAbility> bootsAbility = getAbilityIfReady(boots);

        if (bootsAbility.isPresent()) {
            // 부츠에 능력이 있다면, 해당 능력에 이벤트를 전달합니다.
            bootsAbility.get().onPlayerToggleFlight(event, player, boots);
        } else {
            // [버그 수정] 부츠에 비행 관련 능력이 없거나 10강이 아니라면,
            // 서버의 기본 비행 동작이 실행되는 것을 막고 비행을 비활성화합니다.
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            dispatchAbilityEvent(armor, (ability, isMain) -> ability.onPlayerToggleSneak(event, player, armor));
        }
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        dispatchAbilityEvent(itemInHand, (ability, isMain) -> ability.onPlayerToggleSneak(event, player, itemInHand));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        ItemStack[] contents = player.getInventory().getContents();
        int mainHandSlot = player.getInventory().getHeldItemSlot();
        final int OFF_HAND_SLOT = 40; // Bukkit API상 오프핸드 슬롯 인덱스

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }

            // 1. '창(Spear)'의 경우, 인벤토리에 있기만 하면 onPlayerMove를 호출합니다.
            // 이는 기력 회복과 같은 패시브 효과를 위함입니다.
            if (upgradeManager.isSpear(item)) {
                dispatchInteractAbilityEvent(item, (ability, isMain) -> ability.onPlayerMove(event, player, item));
                continue; // '창'은 여기서 처리가 끝나므로 다음 아이템으로 넘어갑니다.
            }

            // 2. '창'이 아닌 다른 모든 아이템(삼지창, 방어구 등)의 경우,
            // 기존과 동일하게 방어구 슬롯에 있거나 손에 들고 있을 때만 처리합니다.
            // 이는 더블 점프나 삼지창의 다른 능력들이 의도치 않게 발동되는 것을 방지합니다.
            boolean isArmorSlot = i >= 36 && i <= 39;
            boolean isHeldSlot = i == mainHandSlot || i == OFF_HAND_SLOT;

            if (isArmorSlot || isHeldSlot) {
                dispatchInteractAbilityEvent(item, (ability, isMain) -> ability.onPlayerMove(event, player, item));
            }
        }
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack rod = null;

        if (mainHand.getType() == Material.FISHING_ROD) rod = mainHand;
        else if (offHand.getType() == Material.FISHING_ROD) rod = offHand;

        if (rod == null) return;

        final ItemStack finalRod = rod;
        dispatchAbilityEvent(finalRod, (ability, isMain) -> ability.onPlayerFish(event, player, finalRod));
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        dispatchAbilityEvent(bow, (ability, isMain) -> ability.onEntityShootBow(event, player, bow));
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        if (player.hasMetadata("df_is_firing_special")) {
            return;
        }

        // 창(Spear) 또는 삼지창(Trident) 발사 시
        if (event.getEntity() instanceof Trident) { // 창(Spear)도 Trident 투사체를 사용합니다.
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            // 주 손에 있는 창/삼지창 능력 처리
            if (upgradeManager.isSpear(mainHand) || mainHand.getType() == Material.TRIDENT) {
                dispatchInteractAbilityEvent(mainHand, (ability, isMain) -> ability.onProjectileLaunch(event, player, mainHand));
            }

            // 보조 손에 있는 창/삼지창 능력 처리 (이벤트가 취소되지 않았을 경우)
            if (!event.isCancelled() && (upgradeManager.isSpear(offHand) || offHand.getType() == Material.TRIDENT)) {
                dispatchAbilityEvent(offHand, (ability, isMain) -> ability.onProjectileLaunch(event, player, offHand));
            }
        }
    }

    @EventHandler
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        player.setMetadata("df_is_riptiding", new FixedMetadataValue(plugin, true));
        new BukkitRunnable() {
            @Override
            public void run() {
                player.removeMetadata("df_is_riptiding", plugin);
            }
        }.runTaskLater(plugin, 30L); // 1.5초

        if (hasLore(item, "뇌창")) {
            player.setVelocity(new Vector(0, 0, 0));
            player.sendMessage("§c뇌창 모드에서는 역류를 사용할 수 없습니다.");
            return;
        }

        dispatchAbilityEvent(item, (ability, isMain) -> ability.onPlayerRiptide(event, player, item));
    }

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        dispatchInteractAbilityEvent(boots, (ability, isMain) -> ability.onPlayerJump(event, player, boots));
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;
    }

    /**
     * Handles the Totem of Undying cooldown when a player resurrects.
     * @param event The entity resurrect event.
     */
    @EventHandler
    public void onPlayerResurrect(EntityResurrectEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        ISpecialAbility totemAbility = specialAbilityManager.getRegisteredAbility("totem_of_undying");
        if (totemAbility != null) {
            double cooldown = totemAbility.getCooldown();
            specialAbilityManager.setCooldown(player, totemAbility, cooldown);
        }
    }

    /**
     * A helper method to find and execute abilities associated with a given item.
     * @param item The item to check for abilities.
     * @param action The action to perform with the found ability.
     */
    private void dispatchAbilityEvent(ItemStack item, BiConsumer<ISpecialAbility, Boolean> action) {
        if (item == null || item.getType() == Material.AIR) return;

        IUpgradeableProfile profile = upgradeManager.getProfileRegistry().getProfile(item.getType());
        if (profile == null) return;

        profile.getSpecialAbility().ifPresent(ability -> action.accept(ability, true));
        profile.getAdditionalAbilities().forEach(ability -> action.accept(ability, false));
    }

    private void dispatchInteractAbilityEvent(ItemStack item, BiConsumer<ISpecialAbility, Boolean> action) {
        if (item == null || item.getType() == Material.AIR) return;
        if (upgradeManager.getUpgradeLevel(item) < UpgradeManager.MAX_UPGRADE_LEVEL) return;

        // 삼지창의 경우, 현재 모드에 맞는 능력을 직접 찾아 실행합니다.
        if (item.getType() == Material.TRIDENT) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;

            String mode = meta.getPersistentDataContainer().getOrDefault(UpgradeManager.TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");
            ISpecialAbility tridentAbility = specialAbilityManager.getRegisteredAbility(mode);
            if (tridentAbility != null) {
                action.accept(tridentAbility, true);
            }
            return; // 삼지창은 여기서 처리를 종료합니다.
        }

        IUpgradeableProfile profile = upgradeManager.getProfileRegistry().getProfile(item.getType());
        if (profile == null) return;

        profile.getSpecialAbility().ifPresent(ability -> action.accept(ability, true));
        profile.getAdditionalAbilities().forEach(ability -> action.accept(ability, false));
    }

    private boolean hasLore(ItemStack item, String text) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }
        List<Component> lore = item.getItemMeta().lore();
        if (lore == null) return false;

        return lore.stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .anyMatch(plainLine -> plainLine.contains(text));
    }

    /**
     * 아이템이 10강 이상일 경우에만 특수 능력 객체를 가져옵니다.
     * 반복되는 강화 레벨 확인 로직을 줄여줍니다.
     * @param item 확인할 아이템
     * @return 조건에 맞는 ISpecialAbility (Optional)
     */
    private Optional<ISpecialAbility> getAbilityIfReady(ItemStack item) {
        if (item != null && upgradeManager.getUpgradeLevel(item) >= cjs.DF_Plugin.upgrade.UpgradeManager.MAX_UPGRADE_LEVEL) {
            return specialAbilityManager.getAbilityFromItem(item);
        }
        return Optional.empty();
    }
}