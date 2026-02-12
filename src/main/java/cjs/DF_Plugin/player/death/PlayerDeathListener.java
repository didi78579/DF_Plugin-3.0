package cjs.DF_Plugin.player.death;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

public class PlayerDeathListener implements Listener {

    // 다른 플러그인이 인벤토리를 조작하기 전에 실행되도록 우선순위를 높게 설정
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();

        // --- 사망 메시지 수정 로직 ---
        if (killer != null && killer.hasMetadata("df_skill_weapon")) {
            List<MetadataValue> values = killer.getMetadata("df_skill_weapon");
            if (!values.isEmpty() && values.get(0).value() instanceof ItemStack) {
                ItemStack skillWeapon = (ItemStack) values.get(0).value();

                // 새로운 사망 메시지 생성
                Component deathMessage = Component.text()
                        .append(player.displayName())
                        .append(Component.text(" was slain by "))
                        .append(killer.displayName())
                        .append(Component.text(" using "))
                        .append(skillWeapon.displayName().hoverEvent(skillWeapon.asHoverEvent()))
                        .build();

                event.deathMessage(deathMessage);
            }
        }
        // --- (수정 끝) ---

        // 킬러가 다른 플레이어인 경우 킬 이펙트 생성
        if (killer != null && !killer.equals(player)) {
            spawnKillFirework(player.getLocation());
        }

        Boolean keepInventory = player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY);

        // keepInventory가 true일 때만 소실 저주를 수동으로 처리
        if (keepInventory != null && keepInventory) {
            PlayerInventory inventory = player.getInventory();

            // 1. 주 인벤토리 (핫바 포함) 확인
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) { // 인덱스로 접근하여 직접 수정
                ItemStack item = contents[i];
                if (item != null && item.hasItemMeta() && item.getEnchantments().containsKey(Enchantment.VANISHING_CURSE)) {
                    inventory.setItem(i, null);
                }
            }

            // 2. 갑옷 슬롯 확인
            ItemStack[] armorContents = inventory.getArmorContents();
            for (int i = 0; i < armorContents.length; i++) {
                ItemStack item = armorContents[i];
                if (item != null && item.hasItemMeta() && item.getEnchantments().containsKey(Enchantment.VANISHING_CURSE)) {
                    armorContents[i] = null; // 복사된 배열을 수정
                }
            }
            inventory.setArmorContents(armorContents); // 수정된 배열을 다시 적용

            // 3. 왼손(오프핸드) 슬롯 확인
            ItemStack offHandItem = inventory.getItemInOffHand();
            if (offHandItem.hasItemMeta() && offHandItem.getEnchantments().containsKey(Enchantment.VANISHING_CURSE)) {
                inventory.setItemInOffHand(null);
            }
        }
    }

    private void spawnKillFirework(Location location) {
        Firework fw = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK_ROCKET);
        FireworkMeta fwm = fw.getFireworkMeta();

        fwm.setPower(1);
        fwm.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.STAR)
                .withColor(Color.PURPLE)
                .withFlicker()
                .build());

        fw.setFireworkMeta(fwm);
        fw.detonate();
    }
}