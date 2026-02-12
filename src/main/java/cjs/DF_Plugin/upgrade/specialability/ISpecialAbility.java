package cjs.DF_Plugin.upgrade.specialability;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public interface ISpecialAbility {

    enum ChargeDisplayType { DOTS, FRACTION }

    String getInternalName();
    String getDisplayName();
    String getDescription();
    double getCooldown();

    /**
     * 이 능력이 다회성(충전형)인지 여부를 결정합니다.
     * @return 최대 사용 가능 횟수. 1 이하면 단일 사용 능력으로 간주됩니다.
     */
    default int getMaxCharges() { return 1; }

    default void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event, Player player, ItemStack item) {}
    default void onPlayerInteract(PlayerInteractEvent event, Player player, ItemStack item) {}
    default void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {}
    default void onPlayerMove(PlayerMoveEvent event, Player player, ItemStack item) {}
    default void onEntityDamage(EntityDamageEvent event, Player player, ItemStack item) {}
    default void onEntityShootBow(EntityShootBowEvent event, Player player, ItemStack item) {}
    default void onProjectileLaunch(ProjectileLaunchEvent event, Player player, ItemStack item) {}
    default void onPlayerRiptide(PlayerRiptideEvent event, Player player, ItemStack item) {}
    default void onPlayerFish(org.bukkit.event.player.PlayerFishEvent event, Player player, ItemStack item) {}
    default void onPlayerToggleSneak(PlayerToggleSneakEvent event, Player player, ItemStack item) {}
    default void onPlayerDropItem(PlayerDropItemEvent event, Player player, ItemStack item) {}
    default void onProjectileHit(ProjectileHitEvent event) {}
    default void onEquip(Player player, ItemStack item) {}
    default void onPlayerJump(PlayerJumpEvent event, Player player, ItemStack item) {}
    default void onCleanup(Player player) {}
    default void onPlayerToggleFlight(PlayerToggleFlightEvent event, Player player, ItemStack item) {}

    /**
     * 작살 능력을 위해, 플레이어가 공중에서 우클릭을 유지하여 기력을 소모할 때 호출됩니다.
     * @param player 대상 플레이어
     * @param currentGauge 현재 기력
     */
    default void onHarpoonCharge(Player player, double currentGauge) {}
    /**
     * 작살 능력을 위해, 외부에서 급강하를 직접 실행할 때 호출됩니다.
     * @param player 대상 플레이어
     */
    default void executeAerialDive(Player player) {}

    /**
     * 이 능력이 기존 쿨다운을 무시하고 항상 새로 설정되어야 하는지 여부를 반환합니다.
     * @return 쿨다운을 항상 덮어써야 하면 true
     */
    default boolean alwaysOverwriteCooldown() {
        return false;
    }

    /**
     * 이 능력의 쿨다운 또는 충전 상태를 액션바에 표시할지 여부를 결정합니다.
     * @return 액션바에 표시하려면 true
     */
    default boolean showInActionBar() {
        return true;
    }

    /**
     * 이 능력의 충전량을 액션바에 어떤 형태로 표시할지 결정합니다.
     * @return 표시 형식 (기본값: DOTS)
     */
    default ChargeDisplayType getChargeDisplayType() {
        return ChargeDisplayType.DOTS;
    }
}