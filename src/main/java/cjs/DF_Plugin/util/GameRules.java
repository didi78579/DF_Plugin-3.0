package cjs.DF_Plugin.util;

/**
 * Paper 1.21+ API의 게임 규칙 상수를 참조하는 헬퍼 클래스입니다.
 * 이 플러그인은 최신 버전을 대상으로 하므로, 하위 호환성 로직을 제거하여 코드를 간소화합니다.
 * 일부 상수는 deprecated 되었지만, 일관된 참조를 위해 이 클래스에서 관리합니다.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class GameRules {

    // 최신 API에서는 일부 상수가 org.bukkit.GameRules로 이동했고, 일부는 org.bukkit.GameRule에 남아있습니다.
    // 각각 올바른 위치에서 참조합니다.
    public static final org.bukkit.GameRule<java.lang.Boolean> KEEP_INVENTORY = org.bukkit.GameRules.KEEP_INVENTORY;
    public static final org.bukkit.GameRule<java.lang.Boolean> REDUCED_DEBUG_INFO = org.bukkit.GameRules.REDUCED_DEBUG_INFO;
    public static final org.bukkit.GameRule<java.lang.Boolean> LOCATOR_BAR = org.bukkit.GameRules.LOCATOR_BAR;

    // 아래 상수들은 org.bukkit.GameRule에 남아있으며, deprecated 되었습니다.
    public static final org.bukkit.GameRule<java.lang.Boolean> DO_INSOMNIA = org.bukkit.GameRule.DO_INSOMNIA;
    public static final org.bukkit.GameRule<java.lang.Boolean> DO_MOB_SPAWNING = org.bukkit.GameRule.DO_MOB_SPAWNING;

    private GameRules() {}
}