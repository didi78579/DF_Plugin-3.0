package cjs.DF_Plugin.world.enchant;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Map;

public class EnchantmentRuleListener implements Listener {

    private final GameConfigManager configManager;

    public EnchantmentRuleListener(DF_Main plugin) {
        this.configManager = plugin.getGameConfigManager();
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        // OP 인챈트 비활성화 설정에 따라 인챈트 목록에서 제거
        Map<Enchantment, Integer> enchantsToAdd = event.getEnchantsToAdd();
        enchantsToAdd.keySet().removeIf(enchantment -> {
            if (configManager.getConfig().getBoolean("items.op-enchant.breach-disabled", true) && enchantment.equals(Enchantment.BREACH)) {
                return true;
            }
            if (configManager.getConfig().getBoolean("items.op-enchant.thorns-disabled", true) && enchantment.equals(Enchantment.THORNS)) {
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || !result.hasItemMeta()) return;

        if (configManager.getConfig().getBoolean("items.op-enchant.breach-disabled", true) && hasEnchantment(result, Enchantment.BREACH)) {
            event.setResult(null);
        }
        if (configManager.getConfig().getBoolean("items.op-enchant.thorns-disabled", true) && hasEnchantment(result, Enchantment.THORNS)) {
            event.setResult(null);
        }
    }

    private boolean hasEnchantment(ItemStack item, Enchantment enchantment) {
        if (item.getEnchantments().containsKey(enchantment)) return true;
        if (item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            return meta.getStoredEnchants().containsKey(enchantment);
        }
        return false;
    }
}