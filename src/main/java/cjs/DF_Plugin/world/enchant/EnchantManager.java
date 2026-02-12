package cjs.DF_Plugin.world.enchant;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class EnchantManager {

    private final DF_Main plugin;
    private static final String PREFIX = "§5[마법 부여] §f";

    public EnchantManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public boolean attemptEnchant(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (hasUpgradeStars(meta)) {
            player.sendMessage(PREFIX + "§c강화된 아이템에는 마법을 부여할 수 없습니다.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.8f);
            return false;
        }

        if (!hasMagicStone(player)) {
            player.sendMessage(PREFIX + "§c마석이 부족합니다.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.8f);
            return false;
        }

        consumeMagicStone(player);
        enchantItem(item);

        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.0f);
        return true;
    }

    private void enchantItem(ItemStack item) {
        GameConfigManager configManager = plugin.getGameConfigManager();
        double extraEnchantChance = configManager.getConfig().getDouble("enchanting.random-enchanting.extra-enchant-chance", 0.10);
        double curseChance = configManager.getConfig().getDouble("enchanting.random-enchanting.curse-chance", 0.01);
        int maxEnchantments = configManager.getConfig().getInt("enchanting.random-enchanting.max-enchantments", 8);

        Random random = new Random();

        // 제외할 인챈트 목록
        List<Enchantment> excludedEnchants = new ArrayList<>();
        // 저주는 기본적으로 제외
        excludedEnchants.add(Enchantment.BINDING_CURSE);
        excludedEnchants.add(Enchantment.VANISHING_CURSE);

        // 설정에 따라 OP 인챈트 제외
        if (configManager.getConfig().getBoolean("items.op-enchant.thorns-disabled", true)) {
            excludedEnchants.add(Enchantment.THORNS);
        }
        if (configManager.getConfig().getBoolean("items.op-enchant.breach-disabled", true)) {
            excludedEnchants.add(Enchantment.BREACH);
        }

        // 1. 기존 인챈트와 저주를 분리합니다.
        Map<Enchantment, Integer> existingEnchantments = new HashMap<>();
        Map<Enchantment, Integer> existingCurses = new HashMap<>();

        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (entry.getKey().isCursed()) {
                existingCurses.put(entry.getKey(), entry.getValue());
            } else {
                existingEnchantments.put(entry.getKey(), entry.getValue());
            }
        }

        // 2. 저주를 제외한 모든 기존 인챈트를 제거합니다.
        for (Enchantment enchantment : existingEnchantments.keySet()) {
            item.removeEnchantment(enchantment);
        }

        // 3. 새로운 인챈트를 담을 목록을 준비하고, 기존 저주는 유지합니다.
        Map<Enchantment, Integer> newEnchantments = new HashMap<>(existingCurses);
        int baseEnchantmentCount = existingEnchantments.size();
        int currentEnchantmentCount;

        // 4. 기존 인챈트 개수만큼 새로운 인챈트를 부여합니다. (최소 1개 보장)
        if (baseEnchantmentCount == 0) {
            addRandomEnchant(item, newEnchantments, excludedEnchants, random);
            currentEnchantmentCount = 1;
        } else {
            for (int i = 0; i < baseEnchantmentCount; i++) {
                addRandomEnchant(item, newEnchantments, excludedEnchants, random);
            }
            currentEnchantmentCount = baseEnchantmentCount;
        }

        // 5. 설정된 확률에 따라 추가 인챈트를 부여합니다.
        while (currentEnchantmentCount < maxEnchantments && random.nextDouble() < extraEnchantChance) {
            addRandomEnchant(item, newEnchantments, excludedEnchants, random);
            currentEnchantmentCount++;
        }

        // 6. 설정된 확률에 따라 저주를 부여합니다.
        if (random.nextDouble() < curseChance) {
            newEnchantments.putIfAbsent(Enchantment.BINDING_CURSE, 1);
        }
        if (random.nextDouble() < curseChance) {
            newEnchantments.putIfAbsent(Enchantment.VANISHING_CURSE, 1);
        }

        // 7. 최종적으로 계산된 모든 인챈트를 아이템에 적용합니다.
        item.addUnsafeEnchantments(newEnchantments);
    }

    private void addRandomEnchant(ItemStack item, Map<Enchantment, Integer> currentEnchants, List<Enchantment> excluded, Random random) {
        Enchantment randomEnchant;
        int attempts = 0; // 무한 루프 방지
        do {
            randomEnchant = Enchantment.values()[random.nextInt(Enchantment.values().length)];
            attempts++;
            if (attempts > 1000) { // 1000번 시도 후에도 못찾으면 중단
                plugin.getLogger().warning("[인챈트] Could not find a compatible enchantment for " + item.getType() + " after 1000 attempts.");
                return;
            }
        } while (excluded.contains(randomEnchant) || currentEnchants.containsKey(randomEnchant));

        int level = random.nextInt(randomEnchant.getMaxLevel()) + 1;
        currentEnchants.put(randomEnchant, level);
    }

    private boolean hasMagicStone(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (MagicStone.isMagicStone(item)) {
                return true;
            }
        }
        return false;
    }

    private void consumeMagicStone(Player player) {
        for (ItemStack item : player.getInventory()) {
            if (MagicStone.isMagicStone(item)) {
                item.setAmount(item.getAmount() - 1);
                return;
            }
        }
    }

    private boolean hasUpgradeStars(ItemMeta meta) {
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    if (line.contains("§6★") || line.contains("§7☆")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}