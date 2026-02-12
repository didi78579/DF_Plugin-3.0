package cjs.DF_Plugin.upgrade.specialability;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public class SpecialAbilityListener implements Listener {

    private final DF_Main plugin;
    private final SpecialAbilityManager specialAbilityManager;
    private final cjs.DF_Plugin.upgrade.UpgradeManager upgradeManager;
    private static final UUID LUNGE_SPEED_MODIFIER_UUID = UUID.fromString("E4A2B1C3-D5E6-F7A8-B9C0-D1E2F3A4B5C6");

    public SpecialAbilityListener(DF_Main plugin) {
        this.plugin = plugin;
        this.specialAbilityManager = plugin.getSpecialAbilityManager();
        this.upgradeManager = plugin.getUpgradeManager();
    }

    // --- 상태 정리(Cleanup) 이벤트 핸들러 ---

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        specialAbilityManager.cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        specialAbilityManager.cleanupPlayer(event.getEntity());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // 아이템을 버리면 돌진 상태 해제
        specialAbilityManager.setPlayerUsingAbility(player, "lunge", false);

        // Q키를 사용하는 모든 능력(예: 작살)에 이벤트를 전달합니다.
        dispatchAbilityEvent(droppedItem, (ability, isMain) -> ability.onPlayerDropItem(event, player, droppedItem));
    }

    // --- 능력 발동 이벤트 핸들러 ---

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (event.getAction().isLeftClick() && upgradeManager.getUpgradeLevel(item) >= cjs.DF_Plugin.upgrade.UpgradeManager.MAX_UPGRADE_LEVEL) {

            // 1. '혜성의 창'은 로어로 확인합니다.
            if (hasLore(item, "혜성의 창")) {
                ISpecialAbility ability = specialAbilityManager.getRegisteredAbility("meteor_spear");
                if (ability != null) {
                    ability.onPlayerInteract(event, player, item);
                    return; // 혜성의 창 능력이 처리했으므로 여기서 종료
                }
            }
            // 2. 삼지창은 이름으로 확인합니다.
            if (item.getType() == Material.TRIDENT) {
                String displayName = PlainTextComponentSerializer.plainText().serialize(item.displayName());
                if (displayName.contains("[뇌창]")) {
                    ISpecialAbility ability = specialAbilityManager.getRegisteredAbility("lightning_spear");
                    if (ability != null) {
                        ability.onPlayerInteract(event, player, item);
                        return; // 뇌창 능력이 처리했으므로 여기서 종료
                    }
                } else if (displayName.contains("[역류]")) {
                    ISpecialAbility ability = specialAbilityManager.getRegisteredAbility("backflow");
                    if (ability != null) {
                        ability.onPlayerInteract(event, player, item);
                        return; // 역류 능력이 처리했으므로 여기서 종료
                    }
                }
            }
        }

        // 그 외 다른 모든 상호작용의 경우,
        // 기존의 dispatch 로직을 따릅니다. (예: 돌진 패시브 등)
        // 단, 삼지창의 좌클릭은 위에서 이미 처리되었으므로 중복 실행을 피해야 합니다.
        if (item.getType() != Material.TRIDENT || !event.getAction().isLeftClick()) {
            dispatchAbilityEvent(item, (ability, isMain) -> ability.onPlayerInteract(event, player, item));
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        // 1. '도구 벨트' 능력 처리 (바지)
        // 삼지창을 들고 있지 않을 때만 발동되어야 하므로, 삼지창 처리보다 먼저 확인합니다.
        ItemStack leggings = player.getInventory().getLeggings();
        dispatchAbilityEvent(leggings, (ability, isMain) -> {
            // 이벤트가 이미 다른 능력에 의해 취소되었다면 추가 로직을 실행하지 않습니다.
            if (!event.isCancelled()) {
                ability.onPlayerSwapHandItems(event, player, leggings);
            }
        });

        // 이벤트가 '도구 벨트'에 의해 취소되었다면, 삼지창 모드 변경 로직을 실행하지 않습니다.
        if (event.isCancelled()) {
            return;
        }

        // 2. 삼지창 모드 변경 처리
        // 주 손에 10강 삼지창이 있을 때만 작동합니다.
        if (mainHandItem != null && mainHandItem.getType() == Material.TRIDENT && upgradeManager.getUpgradeLevel(mainHandItem) >= cjs.DF_Plugin.upgrade.UpgradeManager.MAX_UPGRADE_LEVEL) {
            event.setCancelled(true); // 실제 아이템 스왑을 막고, 모드 변경 로직만 실행합니다.
            ItemStack newTrident = upgradeManager.switchTridentMode(player, mainHandItem);
            player.getInventory().setItemInMainHand(newTrident);
        }
    }

    private double getDamageReductionFromArmor(Player player, String reductionType) {
        double totalReduction = 0;
        NamespacedKey key = "generic".equals(reductionType) ? cjs.DF_Plugin.world.item.SpecialItemListener.BONUS_GENERIC_REDUCTION_KEY : cjs.DF_Plugin.world.item.SpecialItemListener.BONUS_SKILL_REDUCTION_KEY;

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

    private boolean isSkillDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            // 특수 능력으로 발사된 투사체인지 확인 (예: 메타데이터)
            return projectile.hasMetadata("df_special_projectile");
        }
        // 다른 스킬 데미지 판별 로직 추가 가능
        return false;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // 플레이어가 착용한 모든 장비의 능력을 확인 (예: 낙하 데미지 면역)
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            dispatchAbilityEvent(armor, (ability, isMain) -> ability.onEntityDamage(event, player, armor));
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // [버그 수정] 공전하는 뇌창이 어떤 이유로든(팀 충돌 실패, 서버 랙 등) 시전자를 공격하는 것을 원천적으로 방지합니다.
        // 이 이벤트가 발생하면, 이후 발사되는 삼지창의 공격이 무효화되는 버그의 트리거가 되는 것으로 보입니다.
        if (event.getDamager() instanceof Trident trident && event.getEntity() instanceof Player victim) {
            // 해당 삼지창이 '공전 중인' 삼지창인지 메타데이터로 확인합니다.
            if (trident.hasMetadata("df_floating_trident")) {
                // 삼지창의 소유자 UUID와 피해자의 UUID가 일치하는지 확인합니다.
                Object ownerUUIDValue = trident.getMetadata("df_floating_trident").get(0).value();
                if (ownerUUIDValue instanceof UUID && victim.getUniqueId().equals(ownerUUIDValue)) {
                    event.setCancelled(true);
                    return; // 이벤트 처리를 즉시 중단합니다.
                }
            }
        }

        // --- 공격자(Attacker)의 능력 처리 ---
        Player attacker = null;
        ItemStack weapon = null;

        // 공격의 주체를 찾습니다. (직접 공격 플레이어 또는 투사체 발사자)
        if (event.getDamager() instanceof Player p) {
            attacker = p;
            weapon = attacker.getInventory().getItemInMainHand();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p) {
            attacker = p;
            // 투사체 종류에 따라 사용한 무기를 추정합니다.
            if (projectile instanceof Arrow) {
                ItemStack mainHand = attacker.getInventory().getItemInMainHand();
                ItemStack offHand = attacker.getInventory().getItemInOffHand();
                if (mainHand.getType() == Material.BOW || mainHand.getType() == Material.CROSSBOW) {
                    weapon = mainHand;
                } else if (offHand.getType() == Material.BOW || offHand.getType() == Material.CROSSBOW) {
                    weapon = offHand;
                }
            } else if (projectile instanceof Trident) {
                ItemStack mainHand = attacker.getInventory().getItemInMainHand();
                ItemStack offHand = attacker.getInventory().getItemInOffHand();

                if (mainHand.getType() == Material.TRIDENT) {
                    weapon = mainHand;
                } else if (offHand.getType() == Material.TRIDENT) {
                    weapon = offHand;
                }
            }
        }

        // 공격자와 사용한 무기가 식별된 경우, 능력 발동
        if (attacker != null && weapon != null) {
            final Player finalAttacker = attacker; // 람다에서 사용하기 위해 final 변수에 할당
            final ItemStack finalWeapon = weapon; // weapon은 이미 effectively final이지만, 명확성을 위해 final로 선언합니다.

            // 공격 시 발동하는 모든 능력을 dispatchAbilityEvent 헬퍼 메서드로 전달합니다.
            dispatchAbilityEvent(finalWeapon, (ability, isMain) -> ability.onDamageByEntity(event, finalAttacker, finalWeapon));
        }

        // --- 피격자(Victim)의 능력 처리 ---
        if (event.getEntity() instanceof Player victim) {
            // 갑옷의 피해 감소 로직
            double damage = event.getDamage();
            if (isSkillDamage(event)) {
                // 스킬 피해 감소 (각반)
                double reduction = getDamageReductionFromArmor(victim, "skill");
                damage *= (1.0 - reduction);
            } else if (event.getDamager() instanceof LivingEntity) {
                // 일반 피해 감소 (흉갑) - 몬스터 또는 플레이어의 일반 공격
                double reduction = getDamageReductionFromArmor(victim, "generic");
                damage *= (1.0 - reduction);
            }
            event.setDamage(damage);


            // 1. 갑옷 능력 (항상 방어/유틸 능력으로 간주)
            for (ItemStack armor : victim.getInventory().getArmorContents()) {
                if (armor == null || armor.getType() == Material.AIR) continue;

                dispatchAbilityEvent(armor, (ability, isMain) -> ability.onDamageByEntity(event, victim, armor));
            }

            // 2. 양손에 든 아이템의 능력 (방어/유틸리티 아이템만)
            handleVictimHeldItem(event, victim, victim.getInventory().getItemInMainHand());
            handleVictimHeldItem(event, victim, victim.getInventory().getItemInOffHand());
        }
    }

    /**
     * 피격자가 손에 든 아이템의 방어/유틸리티 능력을 처리합니다.
     * 공격용 무기에 붙은 능력은 발동되지 않도록, 특정 아이템 타입(방패, 낚싯대 등)만 허용합니다.
     */
    private void handleVictimHeldItem(EntityDamageByEntityEvent event, Player victim, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        Material itemType = item.getType();

        // 피격 시 발동해야 하는 방어/유틸리티 아이템 타입만 명시적으로 허용합니다.
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

        // 비행 관련 능력은 부츠에만 귀속되어야 합니다.
        ItemStack boots = player.getInventory().getBoots();

        // dispatchAbilityEvent를 사용하여 부츠의 비행 능력을 처리합니다.
        // 만약 능력이 없다면, isPresent()가 false를 반환하여 아무 일도 일어나지 않습니다.
        // 이 로직은 onPlayerToggleFlight가 호출될 때, 비행 능력이 있는 부츠를 신었는지 확인하는 데 사용됩니다.
        // 실제 비행 취소 로직은 onPlayerToggleFlight 능력 구현 내부에서 처리되어야 합니다.
        dispatchAbilityEvent(boots, (ability, isMain) -> {
            ability.onPlayerToggleFlight(event, player, boots);
        });
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        // 갑옷 부위의 능력을 확인합니다 (레깅스, 부츠 등).
        // 슈퍼 점프(레깅스)와 공중 대시(부츠)는 모두 웅크리기로 발동되므로,
        // 이 핸들러에서 모든 갑옷을 순회하며 처리하는 것이 효율적입니다.
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            dispatchAbilityEvent(armor, (ability, isMain) -> ability.onPlayerToggleSneak(event, player, armor));
        }

        // 손에 든 아이템 능력 (그래플링 훅 등)
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        dispatchAbilityEvent(itemInHand, (ability, isMain) -> ability.onPlayerToggleSneak(event, player, itemInHand));
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // 이동 시 발동하는 모든 장비의 특수 능력 처리 (예: 재생, 슈퍼점프 상태 초기화)
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            dispatchAbilityEvent(armor, (ability, isMain) -> ability.onPlayerMove(event, player, armor));
        }

        // 손에 든 아이템의 onPlayerMove 이벤트도 처리하도록 추가 (작살 강하)
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        dispatchAbilityEvent(mainHand, (ability, isMain) -> ability.onPlayerMove(event, player, mainHand));
        ItemStack offHand = player.getInventory().getItemInOffHand();
        dispatchAbilityEvent(offHand, (ability, isMain) -> ability.onPlayerMove(event, player, offHand));
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

        // '뇌창' 능력의 fireTrident() 메서드에서 발사된 특수 투사체인 경우,
        // 이 이벤트는 이미 fireTrident() 내에서 처리되었으므로 추가적인 능력 디스패치를 막습니다.
        if (player.hasMetadata("df_is_firing_special")) {
            return;
        }

        // 삼지창 투척의 경우, 양손을 모두 확인해야 합니다.
        if (event.getEntity() instanceof Trident) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            // 주 손에 삼지창이 있고, 능력이 있다면 처리합니다.
            if (mainHand.getType() == Material.TRIDENT) {
                dispatchAbilityEvent(mainHand, (ability, isMain) -> ability.onProjectileLaunch(event, player, mainHand)); // 뇌창 발사 로직
            }

            // 이벤트가 아직 취소되지 않았고, 부 손에 삼지창이 있다면 처리합니다.
            if (!event.isCancelled() && offHand.getType() == Material.TRIDENT) {
                dispatchAbilityEvent(offHand, (ability, isMain) -> ability.onProjectileLaunch(event, player, offHand)); // 뇌창 발사 로직
            }
        }
    }

    @EventHandler
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 10강 삼지창이 뇌창 모드일 때 역류 발동을 막습니다.
        if (hasLore(item, "뇌창")) {
            player.setVelocity(new Vector(0, 0, 0)); // 역류로 인한 이동을 즉시 중단
            player.sendMessage("§c뇌창 모드에서는 역류를 사용할 수 없습니다.");
            return; // 뇌창 모드에서는 역류 처리를 여기서 종료합니다.
        }

        // 뇌창 모드가 아니라면, 일반적인 역류 능력 처리를 진행합니다.
        dispatchAbilityEvent(item, (ability, isMain) -> ability.onPlayerRiptide(event, player, item));
    }

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        // 더블 점프는 부츠에 귀속된 능력이므로 부츠 아이템을 확인합니다.
        ItemStack boots = player.getInventory().getBoots();
        dispatchAbilityEvent(boots, (ability, isMain) -> ability.onPlayerJump(event, player, boots));
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;
    }

    @EventHandler
    public void onPlayerResurrect(EntityResurrectEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        // 불사의 토템이 사용되었을 때, 해당 능력에 쿨다운을 적용합니다.
        ISpecialAbility totemAbility = specialAbilityManager.getRegisteredAbility("totem_of_undying");
        if (totemAbility != null) {
            double cooldown = totemAbility.getCooldown();
            specialAbilityManager.setCooldown(player, totemAbility, cooldown);
        }
    }

    /**
     * 아이템의 강화 레벨에 따라 적절한 능력(들)에 이벤트를 전달하는 헬퍼 메서드입니다.
     * - 10강 이상: 주요 특수 능력과 추가 능력을 모두 전달합니다.
     * - 10강 미만: 추가 능력(패시브 등)에만 이벤트를 전달합니다.
     * @param item 능력을 확인할 아이템
     * @param action 각 능력과, 해당 능력이 주요 능력인지 여부에 대해 실행할 동작
     */
    private void dispatchAbilityEvent(ItemStack item, BiConsumer<ISpecialAbility, Boolean> action) {
        if (item == null || item.getType() == Material.AIR) return;
        IUpgradeableProfile profile = upgradeManager.getProfileRegistry().getProfile(item.getType());
        if (profile == null) return;

        // 1. 10강 이상일 경우, 주요 특수 능력에 이벤트를 전달합니다.
        if (upgradeManager.getUpgradeLevel(item) >= cjs.DF_Plugin.upgrade.UpgradeManager.MAX_UPGRADE_LEVEL) {
            profile.getSpecialAbility().ifPresent(ability -> action.accept(ability, true));
        }

        // 2. 강화 레벨과 상관없이, 모든 추가 능력(패시브 등)에 이벤트를 전달합니다.
        profile.getAdditionalAbilities().forEach(ability -> action.accept(ability, false));
    }

    /**
     * 아이템의 로어에 특정 텍스트가 포함되어 있는지 확인합니다.
     * @param item 확인할 아이템
     * @param text 찾을 텍스트
     * @return 텍스트가 포함되어 있으면 true
     */
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
}