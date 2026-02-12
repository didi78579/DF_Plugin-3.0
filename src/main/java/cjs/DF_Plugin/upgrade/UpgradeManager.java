package cjs.DF_Plugin.upgrade;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.command.etc.item.ItemNameManager;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.item.UpgradeItems;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.profile.ProfileRegistry;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class UpgradeManager {

    private final DF_Main plugin;
    private final ProfileRegistry profileRegistry;
    private static final String PREFIX = "§6[강화] §f";
    public static final int MAX_UPGRADE_LEVEL = 10;

    public static final NamespacedKey ITEM_UUID_KEY = new NamespacedKey(DF_Main.getInstance(), "item_uuid");
    public static final NamespacedKey SPECIAL_ABILITY_KEY = new NamespacedKey(DF_Main.getInstance(), "special_ability");
    public static final NamespacedKey TRIDENT_MODE_KEY = new NamespacedKey(DF_Main.getInstance(), "trident_mode");

    public UpgradeManager(DF_Main plugin) {
        this.plugin = plugin;
        this.profileRegistry = new ProfileRegistry();
    }

    public int getUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta.getLore() != null) {
            for (String line : meta.getLore()) {
                if (line.contains("★") || line.contains("☆")) {
                    int level = 0;
                    for (char c : ChatColor.stripColor(line).toCharArray()) {
                        if (c == '★') {
                            level++;
                        }
                    }
                    return level;
                }
            }
        }
        return 0;
    }

    public void attemptUpgrade(Player player, ItemStack item) {
        IUpgradeableProfile profile = this.profileRegistry.getProfile(item.getType());
        if (profile == null) {
            player.sendMessage(PREFIX + "§c이 아이템은 강화할 수 없습니다.");
            return;
        }

        final int currentLevel = getUpgradeLevel(item);

        if (currentLevel >= MAX_UPGRADE_LEVEL) {
            player.sendMessage(PREFIX + "§c최대 강화 레벨에 도달했습니다.");
            return;
        }

        int requiredStones = currentLevel + 1;
        if (!hasEnoughStones(player, requiredStones)) {
            player.sendMessage(PREFIX + "§c강화석이 부족합니다! (필요: " + requiredStones + "개)");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.8f);
            return;
        }

        consumeStones(player, requiredStones);
        FileConfiguration config = plugin.getGameConfigManager().getConfig();
        String path = "upgrade.level-settings." + currentLevel;

        if (!config.isConfigurationSection(path)) {
            player.sendMessage(PREFIX + "§c다음 강화 레벨에 대한 설정이 없습니다. (레벨: " + currentLevel + ")");
            player.getInventory().addItem(UpgradeItems.createUpgradeStone(requiredStones));
            return;
        }

        double successChance = config.getDouble(path + ".success", 0.0);
        double failureChance = config.getDouble(path + ".failure", 0.0);
        double downgradeChance = config.getDouble(path + ".downgrade", 0.0);
        double destroyChance = config.getDouble(path + ".destroy", 0.0);

        double totalChance = successChance + failureChance + downgradeChance + destroyChance;
        if (totalChance <= 0) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }

        Random random = new Random();
        double roll = random.nextDouble() * totalChance;

        if (roll < successChance) {
            int newLevel = currentLevel + 1;
            setUpgradeLevel(item, newLevel);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.5f);
            if (newLevel == MAX_UPGRADE_LEVEL) {
                handleLegendaryUpgrade(player, item);
            }
        } else if (roll < successChance + failureChance) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.0f);
        } else if (roll < successChance + failureChance + downgradeChance) {
            int newLevel = Math.max(0, currentLevel - 1);
            setUpgradeLevel(item, newLevel);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.5f);
        } else {
            if (item.getType() == Material.MACE) {
                player.sendMessage(PREFIX + "§c강화에 실패하여 철퇴가 모든 힘을 잃고 0강으로 초기화되었습니다.");
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
                setUpgradeLevel(item, 0);
            } else {
                ItemStack destroyedItem = item.clone();
                String itemName = destroyedItem.getItemMeta() != null && destroyedItem.getItemMeta().hasDisplayName() ? destroyedItem.getItemMeta().getDisplayName() : destroyedItem.getType().name();

                Component hoverableItemName = LegacyComponentSerializer.legacySection().deserialize(itemName)
                        .hoverEvent(destroyedItem.asHoverEvent());

                Component broadcastMessage = Component.text()
                        .append(Component.text("[!] ", NamedTextColor.DARK_RED))
                        .append(Component.text("한 ", NamedTextColor.GRAY))
                        .append(hoverableItemName)
                        .append(Component.text(" (+" + currentLevel + ")", NamedTextColor.DARK_RED))
                        .append(Component.text(" 아이템이 강화에 실패하여 파괴되었습니다.", NamedTextColor.GRAY))
                        .build();
                Bukkit.broadcast(broadcastMessage);
                item.setAmount(0);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.05);
            }
        }
    }

    public ItemStack switchTridentMode(Player player, ItemStack originalTrident) {
        if (originalTrident == null || originalTrident.getType() != Material.TRIDENT) return originalTrident;

        SpecialAbilityManager abilityManager = plugin.getSpecialAbilityManager();
        if (abilityManager.getRemainingCooldown(player, SpecialAbilityManager.MODE_SWITCH_COOLDOWN_KEY) > 0) {
            return originalTrident;
        }

        ItemStack trident = originalTrident.clone();
        ItemMeta meta = trident.getItemMeta();
        if (meta == null) return originalTrident;

        double bonusDamage = 0;
        if (originalTrident.hasItemMeta()) {
            bonusDamage = originalTrident.getItemMeta().getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
        }

        final int level = getUpgradeLevel(trident);
        final String currentMode = meta.getPersistentDataContainer().getOrDefault(TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");
        final String newMode = currentMode.equals("backflow") ? "lightning_spear" : "backflow";

        meta.getPersistentDataContainer().set(TRIDENT_MODE_KEY, PersistentDataType.STRING, newMode);

        if (bonusDamage > 0) {
            meta.getPersistentDataContainer().set(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, bonusDamage);
        }
        trident.setItemMeta(meta);

        setUpgradeLevel(trident, level);

        abilityManager.forceEquipAndCleanup(player, trident);

        abilityManager.setCooldown(player, SpecialAbilityManager.MODE_SWITCH_COOLDOWN_KEY, SpecialAbilityManager.MODE_SWITCH_COOLDOWN_SECONDS, "§7모드 변환");
        
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.0f);

        final Color particleColor = newMode.equals("lightning_spear") ? Color.YELLOW : Color.BLUE;
        new BukkitRunnable() {
            private double angle = 0;
            private double height = 0;
            private final double radius = 0.7;
            private final int duration = 30;
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= duration || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                height = (double) ticks / duration * 2.2;
                angle += 15;

                for (int i = 0; i < 5; i++) {
                    double strandAngle = Math.toRadians(angle + (i * 72));
                    double x = Math.cos(strandAngle) * radius;
                    double z = Math.sin(strandAngle) * radius;

                    Location particleLoc = player.getLocation().add(x, height, z);
                    Particle.DustOptions dustOptions = new Particle.DustOptions(particleColor, 1.0f);
                    player.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dustOptions);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return trident;
    }

    /**
     * Sets the upgrade level for an item, updating its display name, lore, and attributes.
     * @param item The item to modify.
     * @param level The new upgrade level.
     */
    public void setUpgradeLevel(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        IUpgradeableProfile profile = profileRegistry.getProfile(item.getType());
        if (profile == null) return;

        String finalName = generateDisplayName(meta, item.getType(), level);
        meta.setDisplayName(finalName);

        // 로어를 새로 생성하기 전에 기존 로어를 모두 제거합니다.
        meta.setLore(new ArrayList<>());
        List<String> finalLore = generateLore(profile, item, meta, level);
        meta.setLore(finalLore);

        profile.applyAttributes(item, meta, level);

        updatePersistentData(meta, profile, item, level);

        item.setItemMeta(meta);
    }

    /**
     * Generates the display name for an item based on its custom name and upgrade level.
     * @param meta The item's meta.
     * @param material The item's material.
     * @param level The upgrade level.
     * @return The generated display name.
     */
    private String generateDisplayName(ItemMeta meta, Material material, int level) {
        String customName = meta.getPersistentDataContainer().get(ItemNameManager.CUSTOM_NAME_KEY, PersistentDataType.STRING);
        String customColorChar = meta.getPersistentDataContainer().get(ItemNameManager.CUSTOM_COLOR_KEY, PersistentDataType.STRING);

        String baseName;
        if (customName != null && !customName.isEmpty()) {
            baseName = customName;
        } else if (meta.hasDisplayName()) {
            // 기존 이름에서 "전설의 " 또는 "[모드]" 부분을 제거하여 기본 이름을 추출합니다.
            String currentName = ChatColor.stripColor(meta.getDisplayName());
            if (currentName.startsWith("전설의 ")) {
                currentName = currentName.substring("전설의 ".length()).trim();
            }
            int modeIndex = currentName.lastIndexOf(" [");
            if (modeIndex != -1) {
                currentName = currentName.substring(0, modeIndex).trim();
            }
            baseName = currentName;
        } else {
            baseName = null;
        }

        // 이름이 없는 경우, 기본 이름을 설정합니다. (예: 삼지창)
        if (baseName == null || baseName.isEmpty()) {
            if (material == Material.TRIDENT) {
                baseName = "삼지창";
            } else {
                // 다른 아이템 타입에 대한 기본 이름 설정이 필요하다면 여기에 추가
                return null; // 기본 이름이 없으면 null 반환
            }
        }

        // 최종 색상을 결정합니다.
        String colorCode;
        if (level >= MAX_UPGRADE_LEVEL) {
            colorCode = (customColorChar != null) ? "§" + customColorChar : "§6"; // 10강이면 커스텀 색상 또는 금색
        } else {
            colorCode = "§f"; // 10강 미만이면 흰색
        }

        return colorCode + baseName;
    }


    /**
     * Generates the complete lore for an item based on its profile, stats, and upgrade level.
     * @param profile The item's upgrade profile.
     * @param item The item stack.
     * @param meta The item's meta.
     * @param level The upgrade level.
     * @return A list of strings representing the new lore.
     */
    private List<String> generateLore(IUpgradeableProfile profile, ItemStack item, ItemMeta meta, int level) {
        List<String> lore = new ArrayList<>();

        lore.add(generateStarLore(level));

        List<String> chanceLore = generateChanceLore(level);
        if (!chanceLore.isEmpty()) {
            lore.add("");
            lore.addAll(chanceLore);
        }

        List<String> passiveLore = profile.getPassiveBonusLore(item, level);
        if (!passiveLore.isEmpty()) {
            lore.add("");
            lore.addAll(passiveLore);
        }

        // '추가 공격력' 로어를 먼저 추가
        double bonusDamage = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
        if (bonusDamage > 0) {
            lore.add("");
            lore.add(String.format("§d추가 공격력: +%.0f", bonusDamage));
        }

        // 그 다음에 [특수능력] 로어 추가
        if (level >= MAX_UPGRADE_LEVEL) {
            List<String> abilityLore = generateAbilityLore(profile, item, meta);
            if (!abilityLore.isEmpty()) {
                lore.add("");
                lore.addAll(abilityLore);
            }
        }

        // 기본 스탯 로어 추가 (이제 프로필에서 '용의 심장' 로어를 제거해야 함)
        List<String> baseStatsLore = profile.getBaseStatsLore(item, level, bonusDamage);
        if (!baseStatsLore.isEmpty()) {
            lore.add("");
            lore.addAll(baseStatsLore);
        }

        return lore;
    }

    /**
     * Generates the star rating string (e.g., ★★★☆☆☆☆☆☆☆) for the item's lore.
     * @param level The current upgrade level.
     * @return The formatted star string.
     */
    private String generateStarLore(int level) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < level; i++) {
            stars.append(ChatColor.GOLD).append("★");
        }
        for (int i = level; i < MAX_UPGRADE_LEVEL; i++) {
            stars.append(ChatColor.GRAY).append("☆");
        }
        return stars.toString().trim();
    }

    /**
     * Generates the lore lines detailing the chances of success, failure, etc., for the next upgrade.
     * @param level The current upgrade level.
     * @return A list of strings for the chance-related lore.
     */
    private List<String> generateChanceLore(int level) {
        List<String> chanceLore = new ArrayList<>();
        FileConfiguration config = plugin.getGameConfigManager().getConfig();

        if (!config.getBoolean("upgrade.show-success-chance", true)) {
            return Collections.emptyList();
        }
        if (level >= MAX_UPGRADE_LEVEL) {
            chanceLore.add(ChatColor.GOLD + "최대 강화 레벨에 도달했습니다!");
        } else {
            String path = "upgrade.level-settings." + level;
            if (config.isConfigurationSection(path)) {
                double success = config.getDouble(path + ".success", 0.0) * 100;
                double failure = config.getDouble(path + ".failure", 0.0) * 100;
                double downgrade = config.getDouble(path + ".downgrade", 0.0) * 100;
                double destroy = 100 - success - failure - downgrade;

                chanceLore.add(ChatColor.GREEN + "성공 확률: " + String.format("%.1f", success) + "%");
                chanceLore.add(ChatColor.YELLOW + "실패(유지) 확률: " + String.format("%.1f", failure) + "%");
                chanceLore.add(ChatColor.RED + "하락 확률: " + String.format("%.1f", downgrade) + "%");
                chanceLore.add(ChatColor.DARK_RED + "파괴 확률: " + String.format("%.1f", Math.max(0, destroy)) + "%");
            } else {
                chanceLore.add(ChatColor.GRAY + "다음 강화 정보가 없습니다.");
            }
        }
        return chanceLore;
    }

    /**
     * Generates the lore lines for the item's special ability.
     * @param profile The item's upgrade profile.
     * @param item The item stack.
     * @param meta The item's meta.
     * @return A list of strings for the ability lore.
     */
    private List<String> generateAbilityLore(IUpgradeableProfile profile, ItemStack item, ItemMeta meta) {
        ISpecialAbility ability;
        if (item.getType() == Material.TRIDENT) {
            String currentMode = meta.getPersistentDataContainer().getOrDefault(TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");
            ability = plugin.getSpecialAbilityManager().getRegisteredAbility(currentMode);
        } else {
            ability = profile.getSpecialAbility().orElse(null);
        }

        if (ability != null) {
            return List.of(
                    "§5[특수능력]§f : " + ability.getDisplayName(),
                    ability.getDescription()
            );
        }
        return Collections.emptyList();
    }

    /**
     * Updates the persistent data on an item, such as its special ability key and unique ID.
     * @param meta The item's meta to modify.
     * @param profile The item's upgrade profile.
     * @param item The item stack.
     * @param level The upgrade level.
     */
    private void updatePersistentData(ItemMeta meta, IUpgradeableProfile profile, ItemStack item, int level) {
        if (level < MAX_UPGRADE_LEVEL) {
            meta.getPersistentDataContainer().remove(SPECIAL_ABILITY_KEY);
        } else {
            ISpecialAbility abilityToSave;
            if (item.getType() == Material.TRIDENT) {
                String currentMode = meta.getPersistentDataContainer().getOrDefault(TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");
                abilityToSave = plugin.getSpecialAbilityManager().getRegisteredAbility(currentMode);
            } else {
                abilityToSave = profile.getSpecialAbility().orElse(null);
            }

            if (abilityToSave != null) {
                meta.getPersistentDataContainer().set(SPECIAL_ABILITY_KEY, PersistentDataType.STRING, abilityToSave.getInternalName());
            }

            if (!meta.getPersistentDataContainer().has(ITEM_UUID_KEY, PersistentDataType.STRING)) {
                meta.getPersistentDataContainer().set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            }
        }
    }

    /**
     * Handles the server-wide broadcast and sound effect when an item reaches the legendary (max) upgrade level.
     * @param player The player who achieved the upgrade.
     * @param item The legendary item.
     */
    private void handleLegendaryUpgrade(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String legendaryName = meta.getDisplayName();
        
        Component hoverableItemName = LegacyComponentSerializer.legacySection().deserialize(legendaryName)
                .hoverEvent(item.asHoverEvent());

        Component broadcastMessage = Component.text()
                .color(NamedTextColor.GOLD)
                .append(Component.text("[!] "))
                .append(hoverableItemName)
                .append(Component.text("이(가) 탄생했습니다!"))
                .build();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(broadcastMessage);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
        }
    }

    /**
     * Checks if a player has enough upgrade stones in their inventory.
     * @param player The player to check.
     * @param amount The required amount of stones.
     * @return True if the player has enough stones, false otherwise.
     */
    private boolean hasEnoughStones(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (UpgradeItems.isUpgradeStone(item)) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    /**
     * Consumes a specified amount of upgrade stones from a player's inventory.
     * @param player The player whose stones will be consumed.
     * @param amount The amount of stones to consume.
     */
    private void consumeStones(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (remaining <= 0) break;
            ItemStack item = contents[i];
            if (UpgradeItems.isUpgradeStone(item)) {
                int toTake = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - toTake);
                remaining -= toTake;
                if (item.getAmount() <= 0) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }

    /**
     * Applies enchantments to an item's meta based on its upgrade level and a map of enchantment bonuses.
     * @param meta The item's meta to modify.
     * @param level The item's upgrade level.
     * @param enchantBonuses A map of enchantments to their bonus values.
     */
    public static void applyCyclingEnchantments(ItemMeta meta, int level, Map<Enchantment, Double> enchantBonuses) {
        for (Enchantment enchant : enchantBonuses.keySet()) {
            if (meta.hasEnchant(enchant)) {
                meta.removeEnchant(enchant);
            }
        }

        if (level <= 0) {
            return;
        }

        List<Enchantment> enchants = new ArrayList<>(enchantBonuses.keySet());
        int numEnchants = enchants.size();
        if (numEnchants == 0) {
            return;
        }

        if (numEnchants == 3 && level == 10) {
            for (Enchantment enchant : enchants) {
                meta.addEnchant(enchant, 4, true);
            }
            return;
        }

        int baseLevel = level / numEnchants;
        int extraLevels = level % numEnchants;

        for (int i = 0; i < numEnchants; i++) {
            Enchantment currentEnchant = enchants.get(i);
            int enchantLevel = baseLevel + (i < extraLevels ? 1 : 0);
            if (enchantLevel > 0) {
                meta.addEnchant(currentEnchant, enchantLevel, true);
            }
        }
    }

    /**
     * A utility method to set an item's upgrade level and a specific enchantment.
     * @param item The item to modify.
     * @param level The new upgrade level.
     * @param enchantment The enchantment to apply.
     * @param enchantLevel The level of the enchantment.
     * @return The modified item stack.
     */
    public ItemStack setItemLevel(ItemStack item, int level, Enchantment enchantment, int enchantLevel) {
        IUpgradeableProfile profile = profileRegistry.getProfile(item.getType());
        if (profile != null) {
            setUpgradeLevel(item, level);
        }

        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(enchantment, enchantLevel, true);
        item.setItemMeta(meta);
        return item;
    }

    public ProfileRegistry getProfileRegistry() {
        return profileRegistry;
    }

    public boolean isSpear(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_SPEAR");
    }
}