package cjs.DF_Plugin.item;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.item.UpgradeItems;
import cjs.DF_Plugin.world.enchant.MagicStone;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

public class RecipeManager {

    private final DF_Main plugin;
    private final GameConfigManager configManager;
    private final NamespacedKey notchedAppleKey;
    private final NamespacedKey magicStoneKey;
    private final NamespacedKey upgradeStoneKey;

    public RecipeManager(DF_Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getGameConfigManager();
        this.notchedAppleKey = new NamespacedKey(plugin, "notched_apple_recipe");
        this.magicStoneKey = new NamespacedKey(plugin, "magic_stone_recipe");
        this.upgradeStoneKey = new NamespacedKey(plugin, "upgrade_stone_recipe");
    }

    public void updateRecipes() {
        updateNotchedAppleRecipe();
        updateMagicStoneRecipe();
        updateUpgradeStoneRecipe();
        addCustomRecipes();
        removeVanillaRecipes();
    }

    private void updateNotchedAppleRecipe() {
        // 이전 레시피를 먼저 제거하여 상태 변경(활성/비활성)을 간단하게 처리합니다.
        Bukkit.removeRecipe(notchedAppleKey);

        // config.yml 경로를 GitHub 저장소 기준으로 수정
        boolean enabled = configManager.getConfig().getBoolean("items.notched-apple-recipe", true);
        if (enabled) {
            ShapedRecipe recipe = new ShapedRecipe(notchedAppleKey, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
            recipe.shape(" G ", "GAG", " G ");
            recipe.setIngredient('G', Material.GOLD_BLOCK);
            recipe.setIngredient('A', Material.APPLE);
            Bukkit.addRecipe(recipe);
        }
    }

    private void updateMagicStoneRecipe() {
        Bukkit.removeRecipe(magicStoneKey); // 이전 레시피 제거
        int outputAmount = configManager.getConfig().getInt("enchanting.recipe-output-amount", 4);
        if (outputAmount <= 0) return;

        ItemStack result = MagicStone.createMagicStone(outputAmount);
        ShapedRecipe recipe = new ShapedRecipe(magicStoneKey, result);
        recipe.shape("LLL", "LBL", "LLL");
        recipe.setIngredient('L', Material.LAPIS_LAZULI);
        recipe.setIngredient('B', Material.BOOK);

        Bukkit.addRecipe(recipe);
    }

    private void updateUpgradeStoneRecipe() {
        Bukkit.removeRecipe(upgradeStoneKey); // 이전 레시피 제거
        int outputAmount = configManager.getConfig().getInt("upgrade.recipe-output-amount", 8);
        if (outputAmount <= 0) return;

        ItemStack result = UpgradeItems.createUpgradeStone(outputAmount);
        ShapedRecipe recipe = new ShapedRecipe(upgradeStoneKey, result);
        recipe.shape(" D ", "DAD", " D ");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('A', Material.AMETHYST_SHARD);

        Bukkit.addRecipe(recipe);
    }

    private void addCustomRecipes() {
        // 용의 심장 제작법
        ShapedRecipe dragonsHeartRecipe = new ShapedRecipe(new NamespacedKey(plugin, "dragons_heart"), CustomItemFactory.createDragonsHeart());
        dragonsHeartRecipe.shape("SSS", "SDS", "SSS");
        dragonsHeartRecipe.setIngredient('S', new RecipeChoice.ExactChoice(UpgradeItems.createUpgradeStone(1)));
        dragonsHeartRecipe.setIngredient('D', Material.DRAGON_EGG);
        plugin.getServer().addRecipe(dragonsHeartRecipe);
    }

    private void removeVanillaRecipes() {
        // 신호기 조합법 비활성화
        if (configManager.getConfig().getBoolean("items.disable-beacon-recipe", true)) {
            Bukkit.removeRecipe(NamespacedKey.minecraft("beacon"));
        }
    }
}