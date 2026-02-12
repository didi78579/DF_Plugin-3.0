package cjs.DF_Plugin.util;

import org.bukkit.attribute.Attribute;

/**
 * Paper 1.21 API 변경에 따른 Attribute 호환성 헬퍼 클래스입니다.
 * <p>
 * 이 클래스는 리플렉션을 사용하여 새로운 API의 존재 여부를 확인하고,
 * 존재하면 새로운 상수를, 존재하지 않으면 기존 상수를 반환하여 하위 버전과의 호환성을 유지합니다.
 */
@SuppressWarnings("unchecked")
public final class Attributes {

    public static final Attribute GENERIC_MAX_HEALTH;
    public static final Attribute GENERIC_ATTACK_DAMAGE;
    public static final Attribute GENERIC_MOVEMENT_SPEED;

    static {
        GENERIC_MAX_HEALTH = getAttribute("GENERIC_MAX_HEALTH");
        GENERIC_ATTACK_DAMAGE = getAttribute("GENERIC_ATTACK_DAMAGE");
        GENERIC_MOVEMENT_SPEED = getAttribute("GENERIC_MOVEMENT_SPEED");
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static Attribute getAttribute(String name) {
        try {
            // 최신 API: org.bukkit.attribute.Attribute 클래스에서 직접 필드를 찾습니다.
            return (Attribute) Attribute.class.getField(name).get(null);
        } catch (Exception e) {
            // 구버전 API 또는 필드를 찾지 못한 경우의 대체 수단
            return Attribute.valueOf(name);
        }
    }

    private Attributes() {}
}