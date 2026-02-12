package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ToolBeltAbility implements ISpecialAbility {
    @Override
    public String getInternalName() {
        return "tool_belt";
    }

    @Override
    public String getDisplayName() {
        return "§e도구 벨트";
    }

    @Override
    public String getDescription() {
        return "§7F키를 눌러 핫바와 인벤토리의 아이템을 교체합니다.";
    }

    @Override
    public double getCooldown() {
        // 이 능력은 별도의 쿨다운이 필요 없습니다.
        return 0;
    }

    @Override
    public boolean showInActionBar() {
        return false; // 이 능력의 쿨다운은 액션바에 표시하지 않습니다.
    }

    @Override
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event, Player player, ItemStack item) {
        // 이 이벤트는 '바지'에 부여된 능력이므로, item은 바지 아이템입니다.
        // 주 손에 들고 있는 아이템을 확인합니다.
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        // 주 손에 삼지창을 들고 있다면, 이 능력은 발동하지 않고 기본 F키 동작(또는 삼지창 모드 변경)이 실행됩니다.
        if (mainHandItem.getType() == Material.TRIDENT) {
            return;
        }

        // 삼지창이 아니면(맨손 포함) 기본 아이템 스왑 이벤트를 취소합니다.
        event.setCancelled(true);

        // 쿨다운이 1초 미만이므로 tryUseAbility 대신 직접 쿨다운을 설정하여 남용을 방지합니다.
        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (manager.isOnCooldown(player, this)) return;
        manager.setCooldown(player, this, 0.5); // 1초의 짧은 쿨다운을 적용합니다.

        PlayerInventory inventory = player.getInventory();
        // 핫바(0-8)와 그 바로 위 인벤토리 줄(27-35)의 아이템을 교체합니다.
        for (int i = 0; i < 9; i++) {
            ItemStack hotbarItem = inventory.getItem(i);
            ItemStack inventoryItem = inventory.getItem(i + 27);
            inventory.setItem(i, inventoryItem);
            inventory.setItem(i + 27, hotbarItem);
        }
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);
    }
}