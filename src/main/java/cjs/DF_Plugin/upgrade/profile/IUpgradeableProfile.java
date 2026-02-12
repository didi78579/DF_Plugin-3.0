package cjs.DF_Plugin.upgrade.profile;

import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface IUpgradeableProfile {
    /**
     * 아이템의 보이지 않는 속성(AttributeModifiers)과 인챈트 등을 적용합니다.
     * 로어(lore)는 이 메서드에서 직접 수정하지 않습니다.
     * @param item 대상 아이템
     * @param meta 아이템의 메타데이터
     * @param level 강화 레벨
     */
    void applyAttributes(ItemStack item, ItemMeta meta, int level);

    /**
     * 강화 레벨에 따른 패시브 보너스 로어를 반환합니다.
     * @param item 대상 아이템
     * @param level 강화 레벨
     * @return 패시브 보너스에 대한 로어 라인 리스트
     */
    default List<String> getPassiveBonusLore(ItemStack item, int level) {
        return Collections.emptyList();
    }

    default Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.empty();
    }

    default Collection<ISpecialAbility> getAdditionalAbilities() {
        return Collections.emptyList();
    }

    /**
     * 아이템의 기본 스탯(공격력, 방어력 등)을 나타내는 로어를 반환합니다.
     * @param item 대상 아이템
     * @param level 강화 레벨
     * @return 기본 스탯에 대한 로어 라인 리스트
     */
    default List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        return Collections.emptyList();
    }
}