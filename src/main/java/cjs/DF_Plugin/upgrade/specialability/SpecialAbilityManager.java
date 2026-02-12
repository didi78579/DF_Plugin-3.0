package cjs.DF_Plugin.upgrade.specialability;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.data.PlayerDataManager;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.specialability.impl.TotemAbility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class SpecialAbilityManager {

    public static final NamespacedKey SPECIAL_ABILITY_KEY = new NamespacedKey(DF_Main.getInstance(), "special_ability");
    public static final NamespacedKey ITEM_UUID_KEY = new NamespacedKey(DF_Main.getInstance(), "item_uuid");

    private final DF_Main plugin;

    private final Map<UUID, Map<String, CooldownInfo>> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ChargeInfo>> playerCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ItemStack>> lastActiveAbilities = new ConcurrentHashMap<>();
    private final Map<String, ISpecialAbility> registeredAbilities = new HashMap<>();

    private final Map<UUID, Boolean> isUsingLunge = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lungeGauge = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lungeStartTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastLungeLevel = new ConcurrentHashMap<>();
    private static final UUID LUNGE_SPEED_MODIFIER_UUID = UUID.fromString("E4A2B1C3-D5E6-F7A8-B9C0-D1E2F3A4B5C6");

    public static final double GAUGE_PER_LEVEL = 100.0;
    public static final double GAUGE_CONSUMPTION_PER_SECOND = 125.0;
    public static final double GAUGE_REGEN_PER_SECOND = 40.0;

    public static final String MODE_SWITCH_COOLDOWN_KEY = "internal_mode_switch";
    public static final double MODE_SWITCH_COOLDOWN_SECONDS = 5.0;

    public record CooldownInfo(long endTime, String displayName) {}
    public record ChargeInfo(int current, int max, String displayName, boolean visible, ISpecialAbility.ChargeDisplayType displayType) {}
    public record LungeGaugeInfo(double currentGauge, double maxGauge) {}

    public SpecialAbilityManager(DF_Main plugin) {
        this.plugin = plugin;
        loadAllData();
        startPassiveTicker();
    }

    public void loadAllData() {
        loadCooldowns();
        loadCharges();
    }

    public void saveAllData() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        FileConfiguration config = pdm.getConfig();

        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidStr : playersSection.getKeys(false)) {
                config.set("players." + uuidStr + ".abilities", null);
            }
        }

        playerCooldowns.forEach((uuid, cooldownMap) -> cooldownMap.forEach((key, info) -> {
            if (System.currentTimeMillis() < info.endTime()) {
                String path = "players." + uuid + ".abilities.cooldowns." + key;
                config.set(path + ".endTime", info.endTime());
                config.set(path + ".displayName", info.displayName());
            }
        }));

        playerCharges.forEach((uuid, chargeMap) -> chargeMap.forEach((key, info) -> {
            String path = "players." + uuid + ".abilities.charges." + key;
            config.set(path + ".current", info.current());
            config.set(path + ".max", info.max());
            config.set(path + ".displayName", info.displayName());
            config.set(path + ".visible", info.visible());
        }));

        int cooldownCount = (int) playerCooldowns.values().stream().mapToLong(Map::size).sum();
        int chargeCount = (int) playerCharges.values().stream().mapToLong(Map::size).sum();
        plugin.getLogger().info("[능력 관리] " + cooldownCount + "개의 쿨다운 정보와 " + chargeCount + "개의 충전 정보를 저장했습니다.");
    }

    private void loadCooldowns() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        ConfigurationSection playersSection = pdm.getConfig().getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Map<String, CooldownInfo> cooldowns = new ConcurrentHashMap<>();
            ConfigurationSection cooldownsSection = playersSection.getConfigurationSection(uuidStr + ".abilities.cooldowns");
            if (cooldownsSection == null) continue;

            for (String key : cooldownsSection.getKeys(false)) {
                long endTime = cooldownsSection.getLong(key + ".endTime");
                if (System.currentTimeMillis() < endTime) {
                    String displayName = cooldownsSection.getString(key + ".displayName", "Ability");
                    cooldowns.put(key, new CooldownInfo(endTime, displayName));
                }
            }
            if (!cooldowns.isEmpty()) {
                playerCooldowns.put(uuid, cooldowns);
            }
        }
    }

    private void loadCharges() {
        playerCharges.clear();
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        ConfigurationSection playersSection = pdm.getConfig().getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Map<String, ChargeInfo> charges = new ConcurrentHashMap<>();
            ConfigurationSection chargesSection = playersSection.getConfigurationSection(uuidStr + ".abilities.charges");
            if (chargesSection == null) continue;

            for (String key : chargesSection.getKeys(false)) {
                int current = chargesSection.getInt(key + ".current");
                int max = chargesSection.getInt(key + ".max");
                String displayName = chargesSection.getString(key + ".displayName", "Ability");
                boolean visible = chargesSection.getBoolean(key + ".visible", false);
                ISpecialAbility ability = getRegisteredAbility(key);
                ISpecialAbility.ChargeDisplayType displayType = (ability != null) ? ability.getChargeDisplayType() : ISpecialAbility.ChargeDisplayType.DOTS;
                charges.put(key, new ChargeInfo(current, max, displayName, visible, displayType));
            }
            if (!charges.isEmpty()) {
                playerCharges.put(uuid, charges);
            }
        }
    }

    public void registerAbilities() {
        registeredAbilities.clear();
        plugin.getUpgradeManager().getProfileRegistry().getAllProfiles()
                .forEach(profile -> {
                    profile.getSpecialAbility().ifPresent(ability ->
                            registeredAbilities.put(ability.getInternalName(), ability));

                    profile.getAdditionalAbilities().forEach(ability ->
                            registeredAbilities.put(ability.getInternalName(), ability));
                });

        ISpecialAbility totem = new TotemAbility();
        registeredAbilities.put(totem.getInternalName(), totem);

        ISpecialAbility passiveAbsorption = new cjs.DF_Plugin.upgrade.specialability.impl.PassiveAbsorptionAbility();
        registeredAbilities.put(passiveAbsorption.getInternalName(), passiveAbsorption);

        for (ISpecialAbility ability : registeredAbilities.values()) {
            if (ability instanceof Listener) {
                plugin.getServer().getPluginManager().registerEvents((Listener) ability, plugin);
            }
        }
    }

    public void cleanupPlayer(Player player) {
        isUsingLunge.remove(player.getUniqueId());
        lungeStartTime.remove(player.getUniqueId());
        lastLungeLevel.remove(player.getUniqueId());
        setPlayerUsingAbility(player, "lunge", false);

        Map<String, ItemStack> lastAbilities = lastActiveAbilities.remove(player.getUniqueId());
        if (lastAbilities != null) {
            lastAbilities.forEach((abilityName, item) -> Optional.ofNullable(getRegisteredAbility(abilityName)).ifPresent(ability -> ability.onCleanup(player)));
        }
    }

    public void cleanupAllActiveAbilities() {
        new HashSet<>(lastActiveAbilities.keySet()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                cleanupPlayer(player);
            }
        });
    }

    public void forceEquipAndCleanup(Player player, ItemStack newItem) {
        checkPlayerEquipment(player);
    }

    private void startPassiveTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerEquipment(player);
                    handleLungePassive(player);
                    handleWindChargePassive(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    private void handleLungePassive(Player player) {
        ItemStack spearWeapon = findSpearWeaponInHands(player);
        int level = (spearWeapon != null) ? plugin.getUpgradeManager().getUpgradeLevel(spearWeapon) : getLastLungeLevel(player.getUniqueId());

        if (level <= 0) {
            // 창이 없으면 돌진 관련 상태를 모두 초기화합니다.
            if (isPlayerUsingAbility(player, "lunge")) {
                setPlayerUsingAbility(player, "lunge", false);
                removeLungeSpeedModifier(player);
            }
            return;
        }


        double maxGauge = (double) level * GAUGE_PER_LEVEL;
        double currentGauge = getAbilityGauge(player, "lunge");

        // 돌진 사용 중인지 확인
        boolean isUsingSpear = spearWeapon != null && player.isHandRaised() &&
                (spearWeapon.equals(player.getInventory().getItemInMainHand()) || spearWeapon.equals(player.getInventory().getItemInOffHand()));

        double newGauge = currentGauge;

        if (isUsingSpear && currentGauge > 0) {
            // 돌진 사용: 기력 소모
            setPlayerUsingAbility(player, "lunge", true);
            newGauge = Math.max(0, newGauge - (GAUGE_CONSUMPTION_PER_SECOND / 20.0));
            applyLungeSpeedModifier(player, 1.0);

            // 기력이 0이 되면 즉시 돌진 해제
            if (newGauge <= 0) {
                setPlayerUsingAbility(player, "lunge", false);
                removeLungeSpeedModifier(player);
            }
        } else {
            // 돌진 미사용 또는 기력 없음: 기력 회복
            if (isPlayerUsingAbility(player, "lunge")) {
                setPlayerUsingAbility(player, "lunge", false);
                removeLungeSpeedModifier(player);
            }
            if (newGauge < maxGauge) {
                newGauge = Math.min(maxGauge, newGauge + (GAUGE_REGEN_PER_SECOND / 20.0));
            }
        }

        setAbilityGauge(player, "lunge", newGauge);
    }


    private void handleWindChargePassive(Player player) {
        ISpecialAbility windChargeAbility = getRegisteredAbility("wind_charge");
        if (windChargeAbility == null) return;

        ItemStack mace = player.getInventory().getItemInMainHand();
        if (mace.getType() != Material.MACE || plugin.getUpgradeManager().getUpgradeLevel(mace) < 10) {
            mace = player.getInventory().getItemInOffHand();
            if (mace.getType() != Material.MACE || plugin.getUpgradeManager().getUpgradeLevel(mace) < 10) {
                return;
            }
        }

        ChargeInfo chargeInfo = getChargeInfo(player, windChargeAbility);
        int currentCharges = (chargeInfo != null) ? chargeInfo.current() : windChargeAbility.getMaxCharges();

        if (currentCharges < windChargeAbility.getMaxCharges()) {
            if (!isOnCooldown(player, windChargeAbility)) {
                addCharge(player, windChargeAbility, 1);
                setCooldown(player, windChargeAbility, 1.5);
            }
        }
    }

    private void checkPlayerEquipment(Player player) {
        Map<String, ItemStack> currentAbilities = new HashMap<>();

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null) {
                getAbilityFromItem(armor).ifPresent(ability -> currentAbilities.put(ability.getInternalName(), armor));
            }
        }

        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem != null && !isArmor(mainHandItem)) {
            getAbilityFromItem(mainHandItem).ifPresent(ability -> currentAbilities.put(ability.getInternalName(), mainHandItem));
        }

        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null && !isArmor(offHandItem)) {
            getAbilityFromItem(offHandItem).ifPresent(ability -> currentAbilities.put(ability.getInternalName(), offHandItem));
        }

        Map<String, ItemStack> lastAbilities = lastActiveAbilities.getOrDefault(player.getUniqueId(), Collections.emptyMap());

        Set<String> addedAbilityNames = new HashSet<>(currentAbilities.keySet());
        addedAbilityNames.removeAll(lastAbilities.keySet());

        Set<String> removedAbilityNames = new HashSet<>(lastAbilities.keySet());
        removedAbilityNames.removeAll(currentAbilities.keySet());

        boolean hadSpear = lastAbilities.values().stream().anyMatch(this::isSpearWeapon);
        boolean hasSpear = Stream.concat(
                currentAbilities.values().stream(),
                Stream.of(player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand())
        ).anyMatch(this::isSpearWeapon);

        if (hadSpear && !hasSpear) {
            lastAbilities.values().stream()
                    .filter(this::isSpearWeapon)
                    .findFirst()
                    .ifPresent(removedSpear -> lastLungeLevel.put(player.getUniqueId(), plugin.getUpgradeManager().getUpgradeLevel(removedSpear)));
        }

        addedAbilityNames.forEach(abilityName -> Optional.ofNullable(getRegisteredAbility(abilityName)).ifPresent(ability -> ability.onEquip(player, currentAbilities.get(abilityName))));
        removedAbilityNames.forEach(abilityName -> Optional.ofNullable(getRegisteredAbility(abilityName)).ifPresent(ability -> ability.onCleanup(player)));

        lastActiveAbilities.put(player.getUniqueId(), currentAbilities);
    }

    public boolean isAbilityActive(Player player, String abilityName) {
        Map<String, ItemStack> activeAbilities = lastActiveAbilities.get(player.getUniqueId());
        return activeAbilities != null && activeAbilities.containsKey(abilityName);
    }

    public ItemStack getActiveAbilityItem(Player player, String abilityName) {
        Map<String, ItemStack> activeAbilities = lastActiveAbilities.get(player.getUniqueId());
        if (activeAbilities != null) {
            return activeAbilities.get(abilityName);
        }
        return null;
    }

    public ISpecialAbility getRegisteredAbility(String internalName) {
        return registeredAbilities.get(internalName);
    }

    public boolean isOnCooldown(Player player, ISpecialAbility ability, ItemStack item) {
        return getRemainingCooldown(player, ability, item) > 0;
    }

    public boolean isOnCooldown(Player player, ISpecialAbility ability) {
        return getRemainingCooldown(player, ability, null) > 0;
    }

    public boolean tryUseAbility(Player player, ISpecialAbility ability, ItemStack item) {
        long remainingMillis = getRemainingCooldown(player, ability, item);
        if (remainingMillis > 0) {
            return false;
        }

        if (ability.getMaxCharges() > 1) {
            String chargeKey = getChargeKey(ability);
            Map<String, ChargeInfo> charges = playerCharges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            int maxCharges = ability.getMaxCharges();
            ChargeInfo info = charges.getOrDefault(chargeKey, new ChargeInfo(maxCharges, maxCharges, ability.getDisplayName(), true, ability.getChargeDisplayType()));

            if (info.current() <= 0) {
                setCooldown(player, ability, item, ability.getCooldown());
                return false;
            }

            int newCount = info.current() - 1;
            charges.put(chargeKey, new ChargeInfo(newCount, maxCharges, ability.getDisplayName(), true, ability.getChargeDisplayType()));

            if (newCount <= 0) {
                setCooldown(player, ability, item, ability.getCooldown());
                removeChargeInfo(player, ability);
            }
            return true;
        } else {
            setCooldown(player, ability, item, ability.getCooldown());
            return true;
        }
    }

    public boolean tryUseCharge(Player player, ISpecialAbility ability) {
        if (ability.getMaxCharges() <= 1) {
            return tryUseAbility(player, ability, null);
        }

        String chargeKey = getChargeKey(ability);
        Map<String, ChargeInfo> charges = playerCharges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        ChargeInfo info = charges.getOrDefault(chargeKey, new ChargeInfo(ability.getMaxCharges(), ability.getMaxCharges(), ability.getDisplayName(), true, ability.getChargeDisplayType()));

        if (info.current() > 0) {
            int newCount = info.current() - 1;
            charges.put(chargeKey, new ChargeInfo(newCount, ability.getMaxCharges(), ability.getDisplayName(), true, ability.getChargeDisplayType()));
            return true;
        }
        return false;
    }

    public void addCharge(Player player, ISpecialAbility ability, int amount) {
        String chargeKey = getChargeKey(ability);
        Map<String, ChargeInfo> charges = playerCharges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        ChargeInfo info = charges.getOrDefault(chargeKey, new ChargeInfo(0, ability.getMaxCharges(), ability.getDisplayName(), true, ability.getChargeDisplayType()));

        int newCount = Math.min(ability.getMaxCharges(), info.current() + amount);
        if (newCount != info.current()) {
            charges.put(chargeKey, new ChargeInfo(newCount, ability.getMaxCharges(), ability.getDisplayName(), true, ability.getChargeDisplayType()));
        }
    }

    public void setCooldown(Player player, ISpecialAbility ability, ItemStack item, double cooldownSeconds) {
        if (cooldownSeconds <= 0) return;

        double totalCdr = 0;
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece == null || !armorPiece.hasItemMeta()) {
                continue;
            }

            if (armorPiece.getType().name().endsWith("_HELMET")) {
                int level = plugin.getUpgradeManager().getUpgradeLevel(armorPiece);
                totalCdr += (level / 100.0);
            }

            double bonusCdr = armorPiece.getItemMeta().getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_CDR_KEY, PersistentDataType.DOUBLE, 0.0);
            totalCdr += bonusCdr;
        }
        double finalCooldown = cooldownSeconds * (1.0 - totalCdr);

        long newEndTime = System.currentTimeMillis() + (long) (finalCooldown * 1000);
        String cooldownKey = getCooldownKey(player, ability, item);
        String displayName = ability.getDisplayName();

        Map<String, CooldownInfo> cooldowns = playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        if (ability.alwaysOverwriteCooldown()) {
            cooldowns.put(cooldownKey, new CooldownInfo(newEndTime, displayName));
            return;
        }

        CooldownInfo existingCooldown = cooldowns.get(cooldownKey);
        if (existingCooldown != null && existingCooldown.endTime() > newEndTime) {
            return;
        }

        cooldowns.put(cooldownKey, new CooldownInfo(newEndTime, displayName));
    }

    public void setCooldown(Player player, ISpecialAbility ability, double cooldownSeconds) {
        setCooldown(player, ability, null, cooldownSeconds);
    }

    public void setCooldown(Player player, String cooldownKey, double cooldownSeconds, String displayName) {
        if (cooldownSeconds <= 0) return;
        long newEndTime = System.currentTimeMillis() + (long) (cooldownSeconds * 1000);
        Map<String, CooldownInfo> cooldowns = playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        cooldowns.put(cooldownKey, new CooldownInfo(newEndTime, displayName));
    }

    public void resetCooldown(Player player, ISpecialAbility ability) {
        String cooldownKey = getCooldownKey(player, ability, null);
        Map<String, CooldownInfo> cooldowns = playerCooldowns.get(player.getUniqueId());
        if (cooldowns != null) {
            cooldowns.remove(cooldownKey);
            if (cooldowns.isEmpty()) {
                playerCooldowns.remove(player.getUniqueId());
            }
        }
    }

    public long getRemainingCooldown(Player player, ISpecialAbility ability, ItemStack item) {
        String cooldownKey = getCooldownKey(player, ability, item);
        return getRemainingCooldown(player, cooldownKey);
    }

    public long getRemainingCooldown(Player player, String cooldownKey) {
        Map<String, CooldownInfo> cooldowns = playerCooldowns.get(player.getUniqueId());
        if (cooldowns != null && cooldowns.containsKey(cooldownKey)) {
            long endTime = cooldowns.get(cooldownKey).endTime();
            return Math.max(0, endTime - System.currentTimeMillis());
        }
        return 0;
    }

    public void setChargeInfo(Player player, ISpecialAbility ability, int current, int max) {
        String chargeKey = getChargeKey(ability);
        String displayName = ability.getDisplayName();
        playerCharges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(chargeKey, new ChargeInfo(current, max, displayName, true, ability.getChargeDisplayType()));
    }

    public void setChargeVisibility(Player player, ISpecialAbility ability, boolean visible) {
        String chargeKey = getChargeKey(ability);
        Map<String, ChargeInfo> charges = playerCharges.get(player.getUniqueId());
        if (charges != null) {
            ChargeInfo oldInfo = charges.get(chargeKey);
            if (oldInfo != null && oldInfo.visible() != visible) {
                charges.put(chargeKey, new ChargeInfo(oldInfo.current(), oldInfo.max(), oldInfo.displayName(), visible, oldInfo.displayType()));
            }
        }
    }

    public ChargeInfo getChargeInfo(Player player, ISpecialAbility ability) {
        Map<String, ChargeInfo> charges = playerCharges.get(player.getUniqueId());
        if (charges != null) {
            return charges.get(getChargeKey(ability));
        }
        return null;
    }

    public void removeChargeInfo(Player player, ISpecialAbility ability) {
        Map<String, ChargeInfo> charges = playerCharges.get(player.getUniqueId());
        if (charges != null) {
            charges.remove(getChargeKey(ability));
            if (charges.isEmpty()) {
                playerCharges.remove(player.getUniqueId());
            }
        }
    }

    private String getChargeKey(ISpecialAbility ability) {
        return ability.getInternalName();
    }

    private String getCooldownKey(Player player, ISpecialAbility ability, ItemStack item) {
        return ability.getInternalName();
    }

    public Map<String, CooldownInfo> getPlayerCooldowns(UUID playerUUID) {
        return playerCooldowns.get(playerUUID);
    }

    public Map<String, ChargeInfo> getPlayerCharges(UUID playerUUID) {
        return playerCharges.get(playerUUID);
    }

    public Optional<ISpecialAbility> getAbilityFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String abilityKey = item.getItemMeta().getPersistentDataContainer().get(SPECIAL_ABILITY_KEY, PersistentDataType.STRING);
        if (abilityKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registeredAbilities.get(abilityKey));
    }

    public Collection<ISpecialAbility> getAllAbilities() {
        return registeredAbilities.values();
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.endsWith("_HELMET") || typeName.endsWith("_CHESTPLATE") || typeName.endsWith("_LEGGINGS") || typeName.endsWith("_BOOTS");
    }

    public void setPlayerUsingAbility(Player player, String abilityName, boolean isUsing) {
        if ("lunge".equals(abilityName)) {
            isUsingLunge.put(player.getUniqueId(), isUsing);
            if (isUsing) {
                lungeStartTime.put(player.getUniqueId(), System.currentTimeMillis());
            } else {
                lungeStartTime.remove(player.getUniqueId());
            }
        }
    }

    public boolean isPlayerUsingAbility(Player player, String abilityName) {
        if ("lunge".equals(abilityName)) {
            return isUsingLunge.getOrDefault(player.getUniqueId(), false);
        }
        return false;
    }

    public double getAbilityGauge(Player player, String abilityName) {
        if ("lunge".equals(abilityName)) {
            ItemStack spear = findSpearWeaponInHands(player);
            int level = (spear != null) ? plugin.getUpgradeManager().getUpgradeLevel(spear) : getLastLungeLevel(player.getUniqueId());
            double maxGauge = (double) level * GAUGE_PER_LEVEL;
            return lungeGauge.getOrDefault(player.getUniqueId(), maxGauge);
        }
        return 0.0;
    }

    public void setAbilityGauge(Player player, String abilityName, double gauge) {
        if ("lunge".equals(abilityName)) {
            lungeGauge.put(player.getUniqueId(), gauge);
        }
    }

    public long getAbilityStartTime(Player player, String abilityName) {
        if ("lunge".equals(abilityName)) {
            return lungeStartTime.getOrDefault(player.getUniqueId(), 0L);
        }
        return 0L;
    }

    public int getLastLungeLevel(UUID playerUUID) {
        return lastLungeLevel.getOrDefault(playerUUID, 0);
    }

    public Optional<LungeGaugeInfo> getLungeGaugeInfo(Player player) {
        ItemStack spear = findSpearWeaponInHands(player);
        int level = (spear != null) ? plugin.getUpgradeManager().getUpgradeLevel(spear) : getLastLungeLevel(player.getUniqueId());
        if (level <= 0) return Optional.empty();

        double maxGauge = (double) level * GAUGE_PER_LEVEL;
        double currentGauge = lungeGauge.getOrDefault(player.getUniqueId(), maxGauge);
        return Optional.of(new LungeGaugeInfo(currentGauge, maxGauge));
    }

    private ItemStack findSpearWeaponInHands(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isSpearWeapon(mainHand)) return mainHand;
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isSpearWeapon(offHand)) return offHand;
        return null;
    }

    private boolean isSpearWeapon(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_SPEAR");
    }

    private void applyLungeSpeedModifier(Player player, double multiplier) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;

        removeLungeSpeedModifier(player);

        NamespacedKey key = new NamespacedKey(plugin, "lunge_speed_boost");
        AttributeModifier modifier = new AttributeModifier(key, multiplier, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        attribute.addModifier(modifier);
    }

    private void removeLungeSpeedModifier(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;

        attribute.getModifiers().stream()
                .filter(m -> m.getKey().getNamespace().equals(plugin.getName().toLowerCase()) && m.getKey().getKey().equals("lunge_speed_boost"))
                .findFirst().ifPresent(attribute::removeModifier);
    }
}