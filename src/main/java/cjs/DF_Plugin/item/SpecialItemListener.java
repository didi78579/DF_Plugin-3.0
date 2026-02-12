package cjs.DF_Plugin.item;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SpecialItemListener implements Listener {

    private final DF_Main plugin;
    private final Random random = new Random();

    private static final int MAX_SOUL_USES = 10;
    private static final int MAX_HEART_USES = 10;

    public SpecialItemListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 워든 처치 시 흑요석 포션 드롭
        if (event.getEntity() instanceof Warden) {
            event.getDrops().add(CustomItemFactory.createObsidianPotion());
        }

        // 위더 처치 시
        if (event.getEntityType() == EntityType.WITHER) {
            // 기존 드롭 아이템(네더의 별)을 제거합니다.
            event.getDrops().removeIf(item -> item.getType() == Material.NETHER_STAR);
            // 위더 처치 시 악마의 영혼 드롭
            event.getDrops().add(CustomItemFactory.createDemonSoul());
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null) {
            // 주 손에 든 아이템의 추가 공격력을 적용합니다.
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (CustomItemFactory.isWeapon(weapon.getType()) && weapon.hasItemMeta()) {
                double bonusDamage = weapon.getItemMeta().getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
                event.setDamage(event.getDamage() + bonusDamage);
            }
        }
    }

    @EventHandler
    public void onSpecialItemRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 이벤트가 중복으로 실행되는 것을 방지하기 위해, 주 손(오른손)의 이벤트만 처리합니다.
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        // 오른손에 든 아이템이 없으면 무시
        if (mainHandItem.getType() == Material.AIR) {
            return;
        }

        // 오른손에 '악마의 영혼'이나 '용의 심장'을 들고 우클릭하면, 기본 동작(설치, 소환)을 무조건 막습니다.
        if (CustomItemFactory.isDemonSoul(mainHandItem)) {
            event.setCancelled(true);
            // 왼손에 강화할 아이템이 있을 때만 강화 로직을 실행합니다.
            if (offHandItem.getType() != Material.AIR) {
                ItemStack newArmor = handleDemonSoul(player, offHandItem);
                if (newArmor != null) {
                    mainHandItem.setAmount(mainHandItem.getAmount() - 1);
                    player.getInventory().setItemInOffHand(newArmor);
                }
            }
        } else if (CustomItemFactory.isDragonsHeart(mainHandItem)) {
            event.setCancelled(true);
            // 왼손에 강화할 아이템이 있을 때만 강화 로직을 실행합니다.
            if (offHandItem.getType() != Material.AIR) {
                ItemStack newWeapon = handleDragonHeart(player, offHandItem);
                if (newWeapon != null) {
                    mainHandItem.setAmount(mainHandItem.getAmount() - 1);
                    player.getInventory().setItemInOffHand(newWeapon);
                }
            }
        }
    }

    private ItemStack handleDemonSoul(Player player, ItemStack armor) {
        if (!CustomItemFactory.isArmor(armor.getType())) {
            return null;
        }

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        // 부여할 수 있는 스탯 목록
        List<Map.Entry<NamespacedKey, String>> possibleStats = List.of(
                Map.entry(CustomItemFactory.BONUS_CDR_KEY, "쿨타임 감소"),
                Map.entry(CustomItemFactory.BONUS_GENERIC_REDUCTION_KEY, "일반 피해 감소"),
                Map.entry(CustomItemFactory.BONUS_SKILL_REDUCTION_KEY, "스킬 피해 감소"),
                Map.entry(CustomItemFactory.BONUS_SPEED_KEY, "이동 속도")
        );

        // 목록에서 무작위로 스탯 하나를 선택
        Map.Entry<NamespacedKey, String> chosenStat = possibleStats.get(random.nextInt(possibleStats.size()));
        NamespacedKey keyToUpgrade = chosenStat.getKey();
        String statName = chosenStat.getValue();

        // 최대 사용 횟수 확인
        int currentUses = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.DEMON_SOUL_USES_KEY, PersistentDataType.INTEGER, 0);
        if (currentUses >= MAX_SOUL_USES) {
            player.sendMessage("§c이 갑옷에는 악마의 영혼을 더 이상 사용할 수 없습니다.");
            return null;
        }

        double currentBonus = meta.getPersistentDataContainer().getOrDefault(keyToUpgrade, PersistentDataType.DOUBLE, 0.0);
        double newBonus = currentBonus + 0.02; // 2% 증가

        // 아이템을 복제하여 수정
        ItemStack newArmor = armor.clone();
        ItemMeta newMeta = newArmor.getItemMeta();
        newMeta.getPersistentDataContainer().set(keyToUpgrade, PersistentDataType.DOUBLE, newBonus);
        newMeta.getPersistentDataContainer().set(CustomItemFactory.DEMON_SOUL_USES_KEY, PersistentDataType.INTEGER, currentUses + 1);
        updateLore(newMeta, "§d" + statName, newBonus * 100, "%");
        newArmor.setItemMeta(newMeta);

        // 속성(Attribute)을 아이템에 실제로 적용하기 위해 UpgradeManager를 통해 업데이트합니다.
        plugin.getUpgradeManager().setUpgradeLevel(newArmor, plugin.getUpgradeManager().getUpgradeLevel(newArmor));

        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.8f);
        player.sendMessage("§d갑옷에 악마의 힘이 깃들었습니다! (§e" + statName + " +2%§d)");
        return newArmor;
    }

    private ItemStack handleDragonHeart(Player player, ItemStack weapon) {
        if (CustomItemFactory.isArmor(weapon.getType())) {
            return null;
        }

        if (plugin.getUpgradeManager().getUpgradeLevel(weapon) < 10) {
            player.sendMessage("§c이 아이템은 10강 이상의 무기에만 사용할 수 있습니다.");
            return null;
        }

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) {
            return null;
        }

        // 최대 사용 횟수 확인
        double currentDamageCheck = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
        if (currentDamageCheck >= MAX_HEART_USES) {
            player.sendMessage("§c이 무기에는 용의 심장을 더 이상 사용할 수 없습니다. (최대 " + MAX_HEART_USES + "회)");
            return null;
        }

        double currentBonus = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
        double newBonus = currentBonus + 1.0;

        // 아이템을 복제하여 수정
        ItemStack newWeapon = weapon.clone();
        ItemMeta newMeta = newWeapon.getItemMeta();
        newMeta.getPersistentDataContainer().set(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, newBonus);
        updateLore(newMeta, "§d추가 공격력", newBonus, "");
        newWeapon.setItemMeta(newMeta);

        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
        player.sendMessage("§d무기에 용의 힘이 깃들었습니다! (§e공격력 +1§d)");
        return newWeapon;
    }

    private void updateLore(ItemMeta meta, String lorePrefix, double value, String suffix) {
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        String newLoreLine = String.format("%s: +%.0f%s", lorePrefix, value, suffix);

        int index = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).startsWith(lorePrefix)) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            lore.set(index, newLoreLine);
        } else {
            // 특수 능력 설명(§b로 시작) 바로 위에 추가
            int specialAbilityIndex = -1;
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).startsWith("§b")) {
                    specialAbilityIndex = i;
                    break;
                }
            }
            if (specialAbilityIndex != -1) {
                lore.add(specialAbilityIndex, newLoreLine);
            } else {
                lore.add(newLoreLine); // 특수 능력이 없으면 그냥 맨 아래에 추가
            }
        }
        meta.setLore(lore);
    }
}