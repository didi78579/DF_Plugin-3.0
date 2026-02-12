package cjs.DF_Plugin.world;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.util.GameRules;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public class GameRuleListener implements Listener {

    private final GameConfigManager configManager;
    private final SpecialAbilityManager specialAbilityManager;

    public GameRuleListener(DF_Main plugin) {
        this.configManager = plugin.getGameConfigManager();
        this.specialAbilityManager = plugin.getSpecialAbilityManager();
    }

    /**
     * 킵 인벤토리 설정
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // [API 현대화] 월드의 keepInventory 게임 규칙을 직접 확인하는 대신,
        // 이벤트의 setKeepInventory를 사용하여 제어합니다. 이는 다른 플러그인과의 호환성을 높입니다.
        boolean keepInventory = event.getPlayer().getWorld().getGameRuleValue(GameRules.KEEP_INVENTORY);
        if (keepInventory) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /**
     * 팬텀 비활성화 설정
     */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.PHANTOM) {
            if (configManager.getConfig().getBoolean("world.rules.phantom-disabled", true)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 엔더 상자 비활성화 설정
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            if (configManager.getConfig().getBoolean("world.rules.enderchest-disabled", true)) {
                event.setCancelled(true);
                if (event.getPlayer() instanceof Player player) {
                    player.sendMessage("§c[알림] §c엔더 상자는 현재 비활성화되어 있습니다.");
                }
            }
        }
    }

    /**
     * 불사의 토템 쿨다운 시스템
     */
    @EventHandler
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        double cooldownSeconds = configManager.getConfig().getDouble("world.rules.totem-cooldown-seconds", 300);
        if (cooldownSeconds <= 0) {
            // 쿨다운이 0초 이하로 설정되면 토템을 비활성화합니다.
            event.setCancelled(true);
            return;
        }

        final String COOLDOWN_KEY = "internal_totem_cooldown";
        long remainingMillis = specialAbilityManager.getRemainingCooldown(player, COOLDOWN_KEY);

        if (remainingMillis > 0) {
            event.setCancelled(true);
        } else {
            specialAbilityManager.setCooldown(player, COOLDOWN_KEY, cooldownSeconds, "§6불사의 토템");
        }
    }

    /**
     * 포션 소지 제한 및 금지 포션 획득 방지 (아이템 줍기)
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (!isPotion(item.getType())) {
            return;
        }

        // 금지된 포션인지 확인
        if (isBannedPotion(item)) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        int limit = configManager.getConfig().getInt("world.rules.potion-limit", 4);
        if (countPotions(player.getInventory()) >= limit) {
            event.setCancelled(true);
        }
    }

    /**
     * 포션 소지 제한 및 금지 포션 관리 (인벤토리 클릭)
     * 클릭 이벤트가 완료된 후 인벤토리를 검사하여 초과된 포션이나 금지된 포션을 제거합니다.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean isPotionTransaction = (event.getCurrentItem() != null && isPotion(event.getCurrentItem().getType()))
                || (event.getCursor() != null && isPotion(event.getCursor().getType()));

        if (!isPotionTransaction) {
            return;
        }

        // 클릭 이벤트 처리 후(다음 틱)에 인벤토리 상태를 검사합니다.
        player.getServer().getScheduler().runTask(DF_Main.getInstance(), () -> {
            // 1. 포션 소지 개수 제한 확인
            int limit = configManager.getConfig().getInt("world.rules.potion-limit", 4);
            if (countPotions(player.getInventory()) > limit) {
                player.sendMessage("§c[알림] §c포션은 " + limit + "개까지만 소지할 수 있습니다. 초과분은 인벤토리에서 제외됩니다.");
                removeExcessPotions(player, limit);
            }

            // 2. 금지된 포션 확인
            if (removeBannedPotions(player)) {
                player.sendMessage("§c[알림] §c금지된 포션은 소지할 수 없습니다. 해당 포션이 인벤토리에서 제거됩니다.");
            }
        });
    }

    private boolean isPotion(Material material) {
        return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION;
    }

    private int countPotions(PlayerInventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && isPotion(item.getType())) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeExcessPotions(Player player, int limit) {
        PlayerInventory inventory = player.getInventory();
        int potionsToRemove = countPotions(inventory) - limit;

        if (potionsToRemove <= 0) return;

        // 인벤토리를 역순으로 순회하여 초과분을 제거
        for (int i = 35; i >= 0; i--) { // 0-35 is main inventory
            ItemStack item = inventory.getItem(i);
            if (item != null && isPotion(item.getType())) {
                int amount = item.getAmount();
                if (amount > potionsToRemove) {
                    item.setAmount(amount - potionsToRemove);
                    ItemStack toDrop = item.clone();
                    toDrop.setAmount(potionsToRemove);
                    player.getWorld().dropItemNaturally(player.getLocation(), toDrop);
                    potionsToRemove = 0;
                } else {
                    inventory.setItem(i, null);
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    potionsToRemove -= amount;
                }
            }
            if (potionsToRemove <= 0) break;
        }
    }

    /**
     * 해당 아이템이 금지된 포션인지 확인합니다.
     * @param item 확인할 아이템
     * @return 금지된 포션이면 true
     */
    private boolean isBannedPotion(ItemStack item) {
        if (item == null || !isPotion(item.getType()) || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }

        boolean oozingBanned = configManager.getConfig().getBoolean("world.rules.banned-potions.oozing", true);
        boolean infestedBanned = configManager.getConfig().getBoolean("world.rules.banned-potions.infested", true);

        // 커스텀 효과 확인
        if (meta.hasCustomEffects()) {
            for (PotionEffect effect : meta.getCustomEffects()) {
                PotionEffectType type = effect.getType();
                if (oozingBanned && type.equals(PotionEffectType.OOZING)) return true;
                if (infestedBanned && type.equals(PotionEffectType.INFESTED)) return true;
            }
        }

        // 기본 포션 타입 확인 (e.g., Potion of Oozing)
        PotionType baseType = meta.getBasePotionType();
        if (oozingBanned && baseType == PotionType.OOZING) return true;
        if (infestedBanned && baseType == PotionType.INFESTED) return true;

        return false;
    }

    private boolean removeBannedPotions(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean removed = false;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (isBannedPotion(item)) {
                inventory.setItem(i, null);
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                removed = true;
            }
        }
        return removed;
    }
}