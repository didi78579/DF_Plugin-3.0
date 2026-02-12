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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all special behaviors and interactions related to custom items,
 * such as applying bonus stats from Demon Souls and Dragon's Hearts.
 */
public class SpecialItemListener implements Listener {

    private final DF_Main plugin;
    private record StatInfo(NamespacedKey key, String name, Material armorType) {}
    private final Random random = new Random();

    private static final int MAX_SOUL_USES = 10;
    private static final int MAX_HEART_USES = 10;

    public SpecialItemListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles custom drops when specific entities are killed.
     * @param event The entity death event.
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Warden && event.getEntity().getLastDamageCause() != null && event.getEntity().getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            event.getDrops().add(CustomItemFactory.createObsidianPotion());
        }

        if (event.getEntityType() == EntityType.WITHER) {
            event.getDrops().removeIf(item -> item.getType() == Material.NETHER_STAR);
            event.getDrops().add(CustomItemFactory.createDemonSoul());
        }
    }

    /**
     * Applies bonus damage from Dragon's Heart-enchanted weapons.
     * @param event The damage event.
     */
    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null) {
            // Check both main hand and off-hand for bonus damage.
            ItemStack mainHand = attacker.getInventory().getItemInMainHand();
            ItemStack offHand = attacker.getInventory().getItemInOffHand();

            if (CustomItemFactory.isWeapon(mainHand.getType()) && mainHand.hasItemMeta()) {
                double bonusDamage = mainHand.getItemMeta().getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
                event.setDamage(event.getDamage() + bonusDamage);
            } else if (CustomItemFactory.isWeapon(offHand.getType()) && offHand.hasItemMeta()) {
                // If the main hand isn't a weapon, check the off-hand (e.g., dual wielding).
                double bonusDamage = offHand.getItemMeta().getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
                event.setDamage(event.getDamage() + bonusDamage);
            }
        }
    }

    /**
     * Handles the use of special items like Demon Souls and Dragon's Hearts.
     * @param event The player interaction event.
     */
    @EventHandler
    public void onSpecialItemRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        if (mainHandItem.getType() == Material.AIR) {
            return;
        }

        if (CustomItemFactory.isDemonSoul(mainHandItem)) {
            event.setCancelled(true);
            if (offHandItem.getType() != Material.AIR) {
                ItemStack newArmor = handleDemonSoul(player, offHandItem);
                if (newArmor != null) {
                    mainHandItem.setAmount(mainHandItem.getAmount() - 1);
                    player.getInventory().setItemInOffHand(newArmor);
                }
            }
        } else if (CustomItemFactory.isDragonsHeart(mainHandItem)) {
            event.setCancelled(true);
            if (offHandItem.getType() != Material.AIR) {
                ItemStack newWeapon = handleDragonHeart(player, offHandItem);
                if (newWeapon != null) {
                    mainHandItem.setAmount(mainHandItem.getAmount() - 1);
                    player.getInventory().setItemInOffHand(newWeapon);
                }
            }
        }
    }

    /**
     * Handles the logic for applying a Demon Soul to a piece of armor.
     * @param player The player using the soul.
     * @param armor The armor item being upgraded.
     * @return The modified armor stack, or null if the upgrade failed.
     */
    private ItemStack handleDemonSoul(Player player, ItemStack armor) {
        if (!CustomItemFactory.isArmor(armor.getType())) {
            return null;
        }

        if (plugin.getUpgradeManager().getUpgradeLevel(armor) < 10) {
            player.sendMessage("§c이 아이템은 10강 이상의 갑옷에만 사용할 수 있습니다.");
            return null;
        }

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) {
            return null;
        }

        int currentUses = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.DEMON_SOUL_USES_KEY, PersistentDataType.INTEGER, 0);
        if (currentUses >= MAX_SOUL_USES) {
            player.sendMessage("§c이 갑옷에는 악마의 영혼을 더 이상 사용할 수 없습니다.");
            return null;
        }

        Optional<StatInfo> uniqueStatOpt = getUniqueStatForArmor(armor.getType());

        // Check if BOTH stats can be upgraded BEFORE making any changes.
        boolean isHealthUpgradable = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_HEALTH_KEY, PersistentDataType.DOUBLE, 0.0) < MAX_SOUL_USES - 0.001;

        if (uniqueStatOpt.isPresent()) {
            StatInfo uniqueStat = uniqueStatOpt.get();
            boolean isUniqueStatUpgradable = meta.getPersistentDataContainer().getOrDefault(uniqueStat.key(), PersistentDataType.DOUBLE, 0.0) < getMaxBonusForStat(uniqueStat.key()) - 0.001;
            if (!isHealthUpgradable || !isUniqueStatUpgradable) {
                player.sendMessage("§c체력과 고유 스탯을 동시에 강화할 수 없어 악마의 영혼을 사용할 수 없습니다.");
                return null;
            }
        } else { // No unique stat, just check health
            if (!isHealthUpgradable) {
                player.sendMessage("§c체력이 최대치에 도달하여 더 이상 강화할 수 없습니다.");
                return null;
            }
        }

        // If we reach here, it means we can upgrade everything that needs to be upgraded.
        ItemStack newArmor = armor.clone();
        ItemMeta newMeta = newArmor.getItemMeta();
        List<String> enhancedStats = new ArrayList<>();

        // Enhance Health
        enhanceStat(newMeta, CustomItemFactory.BONUS_HEALTH_KEY, 1.0, MAX_SOUL_USES);
        enhancedStats.add("§e최대 체력 +1");

        // Enhance Unique Stat
        uniqueStatOpt.ifPresent(uniqueStat -> {
            enhanceStat(newMeta, uniqueStat.key(), 0.01, getMaxBonusForStat(uniqueStat.key()));
            enhancedStats.add("§e" + uniqueStat.name() + " +1%");
        });

        // Update usage count and lore
        newMeta.getPersistentDataContainer().set(CustomItemFactory.DEMON_SOUL_USES_KEY, PersistentDataType.INTEGER, currentUses + 1);
        updateSoulUsesLore(newMeta, currentUses + 1);
        newArmor.setItemMeta(newMeta);

        plugin.getUpgradeManager().setUpgradeLevel(newArmor, plugin.getUpgradeManager().getUpgradeLevel(newArmor));
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.8f);

        player.sendMessage("§d갑옷에 악마의 힘이 깃들었습니다! (" + String.join(", ", enhancedStats) + "§d)");
        return newArmor;
    }

    /**
     * Applies a stat enhancement if the current bonus is less than the max.
     * @param meta The ItemMeta to modify.
     * @param key The key for the stat.
     * @param amount The amount to increase the stat by.
     * @param max The maximum value for the stat.
     * @return true if the stat was enhanced, false otherwise.
     */
    private boolean enhanceStat(ItemMeta meta, NamespacedKey key, double amount, double max) {
        double currentBonus = meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.DOUBLE, 0.0);
        // Use a small epsilon for floating point comparison
        if (currentBonus < max - 0.001) {
            double newBonus = currentBonus + amount;
            meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, newBonus);
            return true;
        }
        return false;
    }

    /**
     * Gets the unique stat associated with a given armor type.
     * @param armorType The material of the armor.
     * @return An Optional containing the StatInfo if found.
     */
    private Optional<StatInfo> getUniqueStatForArmor(Material armorType) {
        String typeName = armorType.name();
        if (typeName.endsWith("_HELMET")) {
            return Optional.of(new StatInfo(CustomItemFactory.BONUS_CDR_KEY, "쿨타임 감소", armorType));
        } else if (typeName.endsWith("_CHESTPLATE")) {
            return Optional.of(new StatInfo(CustomItemFactory.BONUS_GENERIC_REDUCTION_KEY, "일반 피해 감소", armorType));
        } else if (typeName.endsWith("_LEGGINGS")) {
            return Optional.of(new StatInfo(CustomItemFactory.BONUS_SKILL_REDUCTION_KEY, "스킬 피해 감소", armorType));
        } else if (typeName.endsWith("_BOOTS")) {
            return Optional.of(new StatInfo(CustomItemFactory.BONUS_SPEED_KEY, "이동 속도", armorType));
        }
        return Optional.empty();
    }


    /**
     * Returns the maximum possible bonus value for a given stat key.
     * @param key The NamespacedKey of the stat.
     * @return The maximum bonus value.
     */
    private double getMaxBonusForStat(NamespacedKey key) {
        if (key.equals(CustomItemFactory.BONUS_CDR_KEY)) return 0.1; // 10%
        if (key.equals(CustomItemFactory.BONUS_GENERIC_REDUCTION_KEY)) return 0.1; // 10%
        if (key.equals(CustomItemFactory.BONUS_SKILL_REDUCTION_KEY)) return 0.1; // 10%
        if (key.equals(CustomItemFactory.BONUS_SPEED_KEY)) return 0.1; // 10%
        return 0.0;
    }


    /**
     * Handles the logic for applying a Dragon's Heart to a weapon.
     * @param player The player using the heart.
     * @param weapon The weapon being upgraded.
     * @return The modified weapon stack, or null if the upgrade failed.
     */
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

        int currentUses = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.DRAGONS_HEART_USES_KEY, PersistentDataType.INTEGER, 0);
        if (currentUses >= MAX_HEART_USES) {
            player.sendMessage("§c이 무기에는 용의 심장을 더 이상 사용할 수 없습니다. (최대 " + MAX_HEART_USES + "회)");
            return null;
        }

        double currentBonus = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
        double newBonus = currentBonus + 1.0;

        ItemStack newWeapon = weapon.clone();
        ItemMeta newMeta = newWeapon.getItemMeta();

        newMeta.getPersistentDataContainer().set(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, newBonus);
        newMeta.getPersistentDataContainer().set(CustomItemFactory.DRAGONS_HEART_USES_KEY, PersistentDataType.INTEGER, currentUses + 1);

        updateHeartUsesLore(newMeta, currentUses + 1);
        newWeapon.setItemMeta(newMeta);

        plugin.getUpgradeManager().setUpgradeLevel(newWeapon, plugin.getUpgradeManager().getUpgradeLevel(newWeapon));

        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
        player.sendMessage("§d무기에 용의 힘이 깃들었습니다! (§e공격력 +1§d)");
        return newWeapon;
    }

    private void updateHeartUsesLore(ItemMeta meta, int uses) {
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        String usesLoreLine = "§d용의 심장: " + uses + "/" + MAX_HEART_USES;

        int index = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).startsWith("§d용의 심장:")) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            lore.set(index, usesLoreLine);
        } else {
            int insertIndex = lore.size();
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).startsWith("§7주 손에 있을 때:")) {
                    insertIndex = i;
                    break;
                }
            }
            lore.add(insertIndex, usesLoreLine);
        }
        meta.setLore(lore);
    }


    /**
     * Adds or updates the "Demon Soul: X/10" line in an item's lore.
     * @param meta The item's meta to modify.
     * @param uses The current number of uses.
     */
    private void updateSoulUsesLore(ItemMeta meta, int uses) {
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        String usesLoreLine = "§5악마의 영혼: " + uses + "/" + MAX_SOUL_USES;

        int index = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).startsWith("§5악마의 영혼:")) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            lore.set(index, usesLoreLine);
        } else {
            // Add it after the base stats but before special abilities
            int insertIndex = lore.size();
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).startsWith("§b") || lore.get(i).startsWith("§e[패시브]")) {
                    insertIndex = i;
                    break;
                }
            }
            lore.add(insertIndex, usesLoreLine);
        }
        meta.setLore(lore);
    }
}