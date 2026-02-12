package cjs.DF_Plugin.upgrade.specialability;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.data.PlayerDataManager;
import cjs.DF_Plugin.upgrade.specialability.impl.TotemAbility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
    // 플레이어 UUID -> (능력 이름 -> 능력을 제공하는 아이템)
    private final Map<UUID, Map<String, ItemStack>> lastActiveAbilities = new ConcurrentHashMap<>();
    private final Map<String, ISpecialAbility> registeredAbilities = new HashMap<>();

    // --- Lunge Ability State ---
    private final Map<UUID, Boolean> isUsingLunge = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lungeGauge = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lungeStartTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastLungeLevel = new ConcurrentHashMap<>();
    private static final UUID LUNGE_SPEED_MODIFIER_UUID = UUID.fromString("E4A2B1C3-D5E6-F7A8-B9C0-D1E2F3A4B5C6");

    public static final double GAUGE_PER_LEVEL = 100.0;
    public static final double GAUGE_CONSUMPTION_PER_SECOND = 125.0;
    public static final double GAUGE_REGEN_PER_SECOND = 40.0;

    // --- Constants for Mode Switching ---
    public static final String MODE_SWITCH_COOLDOWN_KEY = "internal_mode_switch";
    public static final double MODE_SWITCH_COOLDOWN_SECONDS = 5.0;

    // Record to hold cooldown information for the action bar
    public record CooldownInfo(long endTime, String displayName) {}
    // Record to hold charge information for the action bar
    public record ChargeInfo(int current, int max, String displayName, boolean visible, ISpecialAbility.ChargeDisplayType displayType) {}
    // Record to hold lunge gauge information
    public record LungeGaugeInfo(double currentGauge, double maxGauge) {}

    public SpecialAbilityManager(DF_Main plugin) {
        this.plugin = plugin;
        loadAllData();
        startPassiveTicker();
    }

    public void loadAllData() {
        // 데이터 로딩은 각 하위 메서드에서 처리합니다.
        loadCooldowns();
        loadCharges();
    }

    public void saveAllData() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        FileConfiguration config = pdm.getConfig();

        // 기존 데이터를 지우기 위해 players 섹션을 순회하며 abilities 관련 데이터만 제거
        if (config.isConfigurationSection("players")) {
            for (String uuidStr : config.getConfigurationSection("players").getKeys(false)) {
                config.set("players." + uuidStr + ".abilities", null);
            }
        }

        playerCooldowns.forEach((uuid, cooldownMap) -> {
            cooldownMap.forEach((key, info) -> {
                if (System.currentTimeMillis() < info.endTime()) {
                    String path = "players." + uuid + ".abilities.cooldowns." + key;
                    config.set(path + ".endTime", info.endTime());
                    config.set(path + ".displayName", info.displayName());
                }
            });
        });

        playerCharges.forEach((uuid, chargeMap) -> {
            chargeMap.forEach((key, info) -> {
                String path = "players." + uuid + ".abilities.charges." + key;
                config.set(path + ".current", info.current());
                config.set(path + ".max", info.max());
                config.set(path + ".displayName", info.displayName());
                config.set(path + ".visible", info.visible());
            });
        });

        int cooldownCount = (int) playerCooldowns.values().stream().mapToLong(Map::size).sum();
        int chargeCount = (int) playerCharges.values().stream().mapToLong(Map::size).sum();
        plugin.getLogger().info("[능력 관리] " + cooldownCount + "개의 쿨다운 정보와 " + chargeCount + "개의 충전 정보를 저장했습니다.");
        // PlayerDataManager가 파일 저장을 담당하므로 여기서는 호출하지 않음
        // DF_Main의 onDisable에서 한 번에 저장됩니다.
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
        // 모든 프로필과 수동 등록을 통해 능력들을 수집합니다.
        plugin.getUpgradeManager().getProfileRegistry().getAllProfiles()
                .forEach(profile -> {
                    profile.getSpecialAbility().ifPresent(ability ->
                            registeredAbilities.put(ability.getInternalName(), ability));

                    profile.getAdditionalAbilities().forEach(ability ->
                            registeredAbilities.put(ability.getInternalName(), ability));
                });

        // Manually register abilities that don't come from an item profile
        ISpecialAbility totem = new TotemAbility();
        registeredAbilities.put(totem.getInternalName(), totem);

        // Listener를 구현하는 능력들을 등록합니다.
        ISpecialAbility passiveAbsorption = new cjs.DF_Plugin.upgrade.specialability.impl.PassiveAbsorptionAbility();
        registeredAbilities.put(passiveAbsorption.getInternalName(), passiveAbsorption);

        // 등록된 모든 능력을 확인하고, Listener를 구현하는 경우 이벤트 리스너로 등록합니다.
        for (ISpecialAbility ability : registeredAbilities.values()) {
            if (ability instanceof Listener) {
                plugin.getServer().getPluginManager().registerEvents((Listener) ability, plugin);
            }
        }
    }

    /**
     * 플레이어의 상태를 정리합니다. (로그아웃, 사망 시 호출)
     * @param player 정리할 플레이어
     */
    public void cleanupPlayer(Player player) {
        // Lunge ability cleanup
        isUsingLunge.remove(player.getUniqueId());
        lungeStartTime.remove(player.getUniqueId());
        lastLungeLevel.remove(player.getUniqueId());
        setPlayerUsingAbility(player, "lunge", false);

        // 플레이어가 나갈 때, 활성화된 모든 능력에 대해 정리 작업을 수행합니다.
        Map<String, ItemStack> lastAbilities = lastActiveAbilities.remove(player.getUniqueId());
        if (lastAbilities != null) {
            lastAbilities.forEach((abilityName, item) -> {
                Optional.ofNullable(getRegisteredAbility(abilityName)).ifPresent(ability -> ability.onCleanup(player));
            });
        }
    }

    /**
     * 서버 종료 시, 온라인 상태인 모든 플레이어의 활성화된 능력을 정리합니다.
     * 이는 onCleanup을 호출하여 액션바, 공전 삼지창 등을 올바르게 제거하고,
     * 이 상태가 파일에 저장되어 재접속 시 원치 않는 효과가 나타나는 문제를 방지합니다.
     */
    public void cleanupAllActiveAbilities() {
        // lastActiveAbilities 맵을 순회하며 온라인 플레이어에 대한 정리 작업을 수행합니다.
        // ConcurrentModificationException을 방지하기 위해 키셋의 복사본을 사용합니다.
        new HashSet<>(lastActiveAbilities.keySet()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            // 플레이어가 온라인 상태일 때만 정리 작업을 수행합니다.
            // 오프라인 플레이어는 onPlayerQuit에서 이미 정리되었습니다.
            if (player != null && player.isOnline()) {
                cleanupPlayer(player);
            }
        });
    }

    /**
     * 특정 아이템에 대한 능력을 강제로 활성화하고, 이전 능력은 비활성화(정리)합니다.
     * 모드 변경처럼 즉각적인 반응이 필요할 때 사용됩니다.
     * @param player 대상 플레이어
     * @param newItem 새로 장착한 아이템
     */
    public void forceEquipAndCleanup(Player player, ItemStack newItem) {
        // 이 메서드는 이제 주기적으로 실행되는 Ticker와 동일한 로직을 즉시 실행합니다.
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
        }.runTaskTimer(plugin, 20L, 1L); // 1틱마다 반복
    }

    private void handleLungePassive(Player player) {
        ItemStack lungeWeapon = findLungeWeaponInHands(player);
        int level = (lungeWeapon != null) ? plugin.getUpgradeManager().getUpgradeLevel(lungeWeapon) : getLastLungeLevel(player.getUniqueId());

        if (level <= 0) return;

        double maxGauge = (double) level * GAUGE_PER_LEVEL;
        double currentGauge = getAbilityGauge(player, "lunge");

        boolean isUsingSpear = lungeWeapon != null && player.isHandRaised() && (lungeWeapon.equals(player.getInventory().getItemInMainHand()) || lungeWeapon.equals(player.getInventory().getItemInOffHand()));

        if (isUsingSpear && currentGauge > 0) {
            setPlayerUsingAbility(player, "lunge", true);
            double newGauge = Math.max(0, currentGauge - (GAUGE_CONSUMPTION_PER_SECOND / 20.0));
            setAbilityGauge(player, "lunge", newGauge);
            applyLungeSpeedModifier(player, 1.0); // 즉시 속도 2배 적용
        } else {
            setPlayerUsingAbility(player, "lunge", false);
            removeLungeSpeedModifier(player); // 속도 효과 제거
            if (currentGauge < maxGauge) {
                double newGauge = Math.min(maxGauge, currentGauge + (GAUGE_REGEN_PER_SECOND / 20.0));
                setAbilityGauge(player, "lunge", newGauge);
            }
        }
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
                setCooldown(player, windChargeAbility, 1.5); // 1.5초 쿨다운
            }
        }
    }

    /**
     * 플레이어의 장비를 확인하여 능력의 장착/해제 상태를 갱신합니다.
     * @param player 확인할 플레이어
     */
    private void checkPlayerEquipment(Player player) {
        // 1. 현재 장착된 아이템에서 능력 목록을 가져옵니다. (갑옷은 착용, 무기/도구는 손에 든 경우만)
        Map<String, ItemStack> currentAbilities = new HashMap<>();

        // 갑옷 슬롯 확인
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isItemReady(armor)) {
                getAbilityFromItem(armor).ifPresent(ability -> currentAbilities.put(ability.getInternalName(), armor));
            }
        }

        // 양손 확인 (갑옷류 아이템은 손에 들었을 때 능력이 활성화되지 않도록 방지)
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (isItemReady(mainHandItem) && !isArmor(mainHandItem)) {
            getAbilityFromItem(mainHandItem).ifPresent(ability -> currentAbilities.put(ability.getInternalName(), mainHandItem));
        }

        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (isItemReady(offHandItem) && !isArmor(offHandItem)) {
            getAbilityFromItem(offHandItem).ifPresent(ability -> currentAbilities.put(ability.getInternalName(), offHandItem));
        }

        // 2. 이전 상태와 비교하여 추가/제거된 능력을 확인합니다.
        Map<String, ItemStack> lastAbilities = lastActiveAbilities.getOrDefault(player.getUniqueId(), Collections.emptyMap());

        Set<String> addedAbilityNames = new HashSet<>(currentAbilities.keySet());
        addedAbilityNames.removeAll(lastAbilities.keySet());

        Set<String> removedAbilityNames = new HashSet<>(lastAbilities.keySet());
        removedAbilityNames.removeAll(currentAbilities.keySet());

        // 창 장착 해제 시, 마지막 레벨을 기록합니다.
        boolean hadSpear = lastAbilities.values().stream().anyMatch(this::isLungeWeapon);
        boolean hasSpear = Stream.concat(
                currentAbilities.values().stream(),
                Stream.of(player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand())
        ).anyMatch(this::isLungeWeapon);

        if (hadSpear && !hasSpear) {
            lastAbilities.values().stream()
                    .filter(this::isLungeWeapon)
                    .findFirst()
                    .ifPresent(removedSpear -> lastLungeLevel.put(player.getUniqueId(), plugin.getUpgradeManager().getUpgradeLevel(removedSpear)));
        }

        // 4. 최종적으로 결정된 변경점에 따라 onEquip 및 onCleanup을 호출합니다.
        addedAbilityNames.forEach(abilityName -> Optional.ofNullable(getRegisteredAbility(abilityName)).ifPresent(ability -> ability.onEquip(player, currentAbilities.get(abilityName))));
        removedAbilityNames.forEach(abilityName -> Optional.ofNullable(getRegisteredAbility(abilityName)).ifPresent(ability -> ability.onCleanup(player)));

        // 5. 마지막 상태를 현재 상태로 업데이트합니다.
        lastActiveAbilities.put(player.getUniqueId(), currentAbilities);
    }

    /**
     * 플레이어가 특정 능력을 현재 활성화(장착)하고 있는지 확인합니다. (10강 이상)
     * @param player 확인할 플레이어
     * @param abilityName 확인할 능력의 internal name
     * @return 능력이 활성화 상태이면 true
     */
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

    /**
     * 능력이 쿨다운 상태인지 확인합니다. (사용자에게 메시지를 보내지 않음)
     * @return 쿨다운 상태이면 true
     */
    public boolean isOnCooldown(Player player, ISpecialAbility ability, ItemStack item) {
        return getRemainingCooldown(player, ability, item) > 0;
    }

    /**
     * 능력이 쿨다운 상태인지 확인합니다. (아이템과 무관)
     * @return 쿨다운 상태이면 true
     */
    public boolean isOnCooldown(Player player, ISpecialAbility ability) {
        return getRemainingCooldown(player, ability, null) > 0;
    }

    /**
     * 액티브 능력의 사용을 시도합니다.
     * 쿨다운과 충전 횟수를 모두 관리하고 액션바에 상태를 표시합니다.
     * @return 능력을 사용할 수 있으면 true
     */
    public boolean tryUseAbility(Player player, ISpecialAbility ability, ItemStack item) {
        long remainingMillis = getRemainingCooldown(player, ability, item);
        if (remainingMillis > 0) {
            // 쿨다운 중일 때는 액션바 매니저가 주기적으로 상태를 표시하므로,
            // 여기서는 별도의 메시지를 보내지 않습니다.
            return false;
        }

        if (ability.getMaxCharges() > 1) {
            String chargeKey = getChargeKey(ability);
            Map<String, ChargeInfo> charges = playerCharges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            int maxCharges = ability.getMaxCharges();
            ChargeInfo info = charges.getOrDefault(chargeKey, new ChargeInfo(maxCharges, maxCharges, ability.getDisplayName(), true, ability.getChargeDisplayType()));

            if (info.current() <= 0) {
                // 이 경우는 보통 쿨다운이 막 시작되었을 때 발생합니다. 쿨다운 메시지를 다시 표시해줍니다.
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

    /**
     * 쿨다운 없이 충전량만 소모하는 능력을 사용합니다. (예: 윈드차지)
     * @return 충전량을 성공적으로 소모했으면 true
     */
    public boolean tryUseCharge(Player player, ISpecialAbility ability) {
        if (ability.getMaxCharges() <= 1) {
            return tryUseAbility(player, ability, null); // 충전 시스템을 사용하지 않는 능력은 기존 로직 사용
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

    /**
     * 특정 능력의 충전 횟수를 증가시킵니다. (최대치를 넘지 않음)
     * @param player 대상 플레이어
     * @param ability 충전량을 증가시킬 능력
     * @param amount 증가시킬 충전량
     */
    public void addCharge(Player player, ISpecialAbility ability, int amount) {
        String chargeKey = getChargeKey(ability);
        Map<String, ChargeInfo> charges = playerCharges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        ChargeInfo info = charges.getOrDefault(chargeKey, new ChargeInfo(0, ability.getMaxCharges(), ability.getDisplayName(), true, ability.getChargeDisplayType()));

        int newCount = Math.min(ability.getMaxCharges(), info.current() + amount);
        if (newCount != info.current()) {
            charges.put(chargeKey, new ChargeInfo(newCount, ability.getMaxCharges(), ability.getDisplayName(), true, ability.getChargeDisplayType()));
        }
    }

    /**
     * Sets a cooldown for a specific ability with a custom duration.
     * @param player The player to set the cooldown for.
     * @param ability The ability to set the cooldown for.
     * @param item The item associated with the ability.
     * @param cooldownSeconds The duration of the cooldown in seconds.
     */
    public void setCooldown(Player player, ISpecialAbility ability, ItemStack item, double cooldownSeconds) {
        if (cooldownSeconds <= 0) return;

        // 모든 착용 갑옷의 쿨타임 감소 효과를 합산합니다.
        double totalCdr = 0;
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece == null || !armorPiece.hasItemMeta()) {
                continue;
            }

            // 1. 강화 레벨에 따른 쿨감 (투구만 해당)
            if (armorPiece.getType().name().endsWith("_HELMET")) {
                int level = plugin.getUpgradeManager().getUpgradeLevel(armorPiece);
                totalCdr += (level / 100.0);
            }

            // 2. '악마의 영혼'으로 부여된 추가 쿨감 (모든 부위)
            double bonusCdr = armorPiece.getItemMeta().getPersistentDataContainer().getOrDefault(cjs.DF_Plugin.world.item.SpecialItemListener.BONUS_CDR_KEY, PersistentDataType.DOUBLE, 0.0);
            totalCdr += bonusCdr;
        }
        double finalCooldown = cooldownSeconds * (1.0 - totalCdr);

        long newEndTime = System.currentTimeMillis() + (long) (finalCooldown * 1000);
        String cooldownKey = getCooldownKey(player, ability, item);
        String displayName = ability.getDisplayName();

        Map<String, CooldownInfo> cooldowns = playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        if (ability.alwaysOverwriteCooldown()) { // 예: 더블점프 피격시 쿨타임
            cooldowns.put(cooldownKey, new CooldownInfo(newEndTime, displayName));
            return;
        }

        // 기존 쿨다운이 존재하고, 그 남은 시간이 새로 적용할 쿨다운보다 길다면, 기존 쿨다운을 유지합니다.
        // 이렇게 하면 짧은 쿨다운이 긴 쿨다운을 덮어쓰는 것을 방지합니다.
        CooldownInfo existingCooldown = cooldowns.get(cooldownKey);
        if (existingCooldown != null && existingCooldown.endTime() > newEndTime) {
            return; // 기존 쿨다운이 더 길므로 아무것도 하지 않음
        }

        // 새 쿨다운을 적용합니다.
        cooldowns.put(cooldownKey, new CooldownInfo(newEndTime, displayName));
    }

    /**
     * Sets a cooldown for a specific ability with a custom duration. (아이템과 무관)
     * @param player The player to set the cooldown for.
     * @param ability The ability to set the cooldown for.
     * @param cooldownSeconds The duration of the cooldown in seconds.
     */
    public void setCooldown(Player player, ISpecialAbility ability, double cooldownSeconds) {
        setCooldown(player, ability, null, cooldownSeconds);
    }

    public void setCooldown(Player player, String cooldownKey, double cooldownSeconds, String displayName) {
        if (cooldownSeconds <= 0) return;
        long newEndTime = System.currentTimeMillis() + (long) (cooldownSeconds * 1000);
        Map<String, CooldownInfo> cooldowns = playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        cooldowns.put(cooldownKey, new CooldownInfo(newEndTime, displayName));
    }

    /**
     * 특정 능력의 쿨다운을 즉시 초기화합니다.
     * @param player 대상 플레이어
     * @param ability 쿨다운을 초기화할 능력
     */
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

    /**
     * 특정 능력의 충전 횟수를 1회 되돌려줍니다. (최대치를 넘지 않음)
     * @param player 대상 플레이어
     * @param ability 충전량을 환불할 능력
     */

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
            // 정보가 존재하고, 가시성 상태가 변경될 때만 업데이트합니다.
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
        // 이제 쿨다운은 아이템이 아닌 능력 자체를 기준으로 적용됩니다.
        // 이를 통해 같은 능력을 가진 다른 아이템들이 쿨다운을 공유합니다.
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

        // 등록된 능력 맵에서 해당 키를 가진 능력을 직접 찾아 반환합니다.
        // 이 아이템이 생성될 당시의 프로필이 현재 프로필과 다르더라도,
        // 아이템에 부여된 능력은 유효한 것으로 간주하여 안정성을 높입니다.
        return Optional.ofNullable(registeredAbilities.get(abilityKey));
    }

    public Collection<ISpecialAbility> getAllAbilities() {
        return registeredAbilities.values();
    }

    private boolean isItemReady(ItemStack item) {
        // 모든 강화 레벨의 아이템에 대해 패시브/추가 능력을 확인할 수 있도록 변경
        return item != null;
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.endsWith("_HELMET") || typeName.endsWith("_CHESTPLATE") || typeName.endsWith("_LEGGINGS") || typeName.endsWith("_BOOTS");
    }

    // --- Lunge Ability Specific Methods ---

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
            ItemStack spear = findLungeWeaponInHands(player);
            int level = (spear != null) ? plugin.getUpgradeManager().getUpgradeLevel(spear) : getLastLungeLevel(player.getUniqueId());
            double maxGauge = (double) level * GAUGE_PER_LEVEL;
            return lungeGauge.getOrDefault(player.getUniqueId(), maxGauge);
        }
        return 0;
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
        ItemStack spear = findLungeWeaponInHands(player);
        int level = (spear != null) ? plugin.getUpgradeManager().getUpgradeLevel(spear) : getLastLungeLevel(player.getUniqueId());
        if (level <= 0) return Optional.empty();

        double maxGauge = (double) level * GAUGE_PER_LEVEL;
        double currentGauge = lungeGauge.getOrDefault(player.getUniqueId(), maxGauge);
        return Optional.of(new LungeGaugeInfo(currentGauge, maxGauge));
    }

    private ItemStack findLungeWeaponInHands(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isLungeWeapon(mainHand)) return mainHand;
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isLungeWeapon(offHand)) return offHand;
        return null;
    }

    private boolean isLungeWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().getLore() == null) return false;
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return false;
        for (String line : lore) {
            if (line.contains("최대 기력")) {
                return true;
            }
        }
        return false;
    }

    private void applyLungeSpeedModifier(Player player, double multiplier) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) return;

        // 기존 모디파이어가 있다면 제거
        removeLungeSpeedModifier(player);

        // 새로운 모디파이어 생성 및 적용
        AttributeModifier modifier = new AttributeModifier(
                LUNGE_SPEED_MODIFIER_UUID,
                "lunge_speed_boost",
                multiplier, // 1.0 = 100% 증가 (총 2배)
                AttributeModifier.Operation.MULTIPLY_SCALAR_1
        );
        attribute.addModifier(modifier);
    }

    private void removeLungeSpeedModifier(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) return;

        // UUID로 모디파이어를 찾아 제거
        attribute.getModifiers().stream()
                .filter(m -> m.getUniqueId().equals(LUNGE_SPEED_MODIFIER_UUID))
                .findFirst().ifPresent(attribute::removeModifier);
    }
}