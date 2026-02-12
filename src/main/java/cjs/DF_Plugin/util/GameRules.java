package cjs.DF_Plugin.util;

import org.bukkit.GameRule;

/**
 * Paper 1.21 API 변경에 따른 GameRule 호환성 헬퍼 클래스입니다.
 * <p>
 * Paper 1.21부터 {@link org.bukkit.GameRule}의 상수 필드들이 deprecated for removal 처리되었습니다.
 * 대신 {@link org.bukkit.GameRules} 클래스의 상수들을 사용해야 합니다.
 * 이 클래스는 리플렉션을 사용하여 새로운 API의 존재 여부를 확인하고,
 * 존재하면 새로운 상수를, 존재하지 않으면 기존 상수를 반환하여 하위 버전과의 호환성을 유지합니다.
 */
@SuppressWarnings("unchecked")
public final class GameRules {

    public static final GameRule<Boolean> KEEP_INVENTORY;
    public static final GameRule<Boolean> REDUCED_DEBUG_INFO;
    public static final GameRule<Boolean> DO_INSOMNIA;
    public static final GameRule<Boolean> LOCATOR_BAR;

    static {
        KEEP_INVENTORY = getGameRule("KEEP_INVENTORY");
        REDUCED_DEBUG_INFO = getGameRule("REDUCED_DEBUG_INFO");
        DO_INSOMNIA = getGameRule("DO_INSOMNIA");
        LOCATOR_BAR = getGameRule("LOCATOR_BAR");
    }

    private static <T> GameRule<T> getGameRule(String name) {
        try {
            // Paper 1.21+ API: org.bukkit.GameRules 클래스에서 필드를 찾습니다.
            Class<?> gameRulesClass = Class.forName("org.bukkit.GameRules");
            return (GameRule<T>) gameRulesClass.getField(name).get(null);
        } catch (Exception e) {
            // 구버전 API 또는 필드를 찾지 못한 경우: org.bukkit.GameRule 클래스에서 찾습니다.
            // 이 방식은 deprecated 되었지만 하위 호환성을 위해 유지합니다.
            return GameRule.getByName(name);
        }
    }

    private GameRules() {}
}