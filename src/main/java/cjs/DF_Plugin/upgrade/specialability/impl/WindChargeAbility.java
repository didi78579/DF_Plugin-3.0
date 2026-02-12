package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WindChargeAbility implements ISpecialAbility {

    private static final int MAX_CHARGES = 5;
    private static final double RECHARGE_SECONDS = 1.5;

    public WindChargeAbility() {
        startChargeRechargeTask();
    }

    @Override
    public String getInternalName() {
        return "wind_charge";
    }

    @Override
    public String getDisplayName() {
        return "§b윈드차지";
    }

    @Override
    public String getDescription() {
        return "§7[우클릭] 돌풍구 발사, [공중 웅크리기] 돌풍";
    }

    @Override
    public int getMaxCharges() {
        return MAX_CHARGES;
    }

    @Override
    public double getCooldown() {
        // 개별 충전 방식이므로, 전체 쿨다운은 사용하지 않습니다.
        return 0;
    }

    @Override
    public ChargeDisplayType getChargeDisplayType() {
        return ChargeDisplayType.DOTS;
    }

    @Override
    public boolean showInActionBar() {
        return false; // 개별 충전 쿨다운을 액션바에 표시하지 않습니다.
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction().isRightClick()) {
            SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
            // 첫 번째 돌풍구를 발사하기 위해 충전량을 소모합니다.
            if (manager.tryUseCharge(player, this)) { 
                player.launchProjectile(WindCharge.class, player.getLocation().getDirection().multiply(2.5));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.2f);
            }
        }
    }

    @Override
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event, Player player, ItemStack item) {
        // 웅크리기를 시작하고, 공중에 있을 때만 발동
        if (event.isSneaking() && !player.isOnGround()) {
            SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
            if (manager.tryUseCharge(player, this)) {
                Vector direction = player.getLocation().getDirection().setY(0).normalize().multiply(0.8); // 대시 거리 0.8로 변경
                player.setVelocity(direction);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 1.5f);
            }
        }
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        // 철퇴의 내려찍기 공격 (Slam Attack) 감지
        if (item.getType() == Material.MACE) {
            // 플레이어의 낙하 거리가 1.5블록 이상일 때 내려찍기 공격으로 간주합니다.
            if (player.getFallDistance() > 1.5f) {
                // 공격이 성공했으므로 스택 1개 돌려주기
                SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
                manager.addCharge(player, this, 1);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f); // 스택 회수 효과음
            }
        }
    }

    private void startChargeRechargeTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
                for (Player player : DF_Main.getInstance().getServer().getOnlinePlayers()) {
                    // 10강 철퇴를 들고 있는지 확인
                    if (!isHoldingMace(player)) continue;

                    SpecialAbilityManager.ChargeInfo chargeInfo = manager.getChargeInfo(player, WindChargeAbility.this);
                    int currentCharges = (chargeInfo != null) ? chargeInfo.current() : MAX_CHARGES;

                    if (currentCharges < MAX_CHARGES) {
                        // 쿨다운이 없는 경우에만 충전 시도
                        if (!manager.isOnCooldown(player, WindChargeAbility.this)) {
                            manager.setChargeInfo(player, WindChargeAbility.this, currentCharges + 1, MAX_CHARGES);
                            // 다음 충전을 위해 7초 쿨다운 설정
                            manager.setCooldown(player, WindChargeAbility.this, RECHARGE_SECONDS);
                        }
                    }
                }
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 20L); // 1초마다 실행
    }

    private boolean isHoldingMace(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.MACE && DF_Main.getInstance().getUpgradeManager().getUpgradeLevel(mainHand) >= 10) {
            return true;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.MACE && DF_Main.getInstance().getUpgradeManager().getUpgradeLevel(offHand) >= 10) {
            return true;
        }
        return false;
    }
}