package com.theman.toolbelt.belt;

import com.theman.toolbelt.registry.ModRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;

/**
 * 弹药槽接管弓 / 弩的取箭逻辑。
 *
 * <p>原版 {@code Player.getProjectile} 的顺序：副手 → 主手 → 背包从头找 → 创造模式给默认箭。
 * 找到之后触发 {@link LivingGetProjectileEvent}，我们在这里把结果换成弹药槽里的那组物品。
 *
 * <p>规则：
 * <ul>
 *   <li>原版从<b>副手/主手</b>找到的箭保留不动（原版习惯：副手拿药箭 = 指定射这种）；</li>
 *   <li>否则只要弹药槽里的东西这把武器能用，就用弹药槽的；</li>
 *   <li>弹药槽空了 / 武器不支持 → 保持原版结果（去背包里找）。</li>
 * </ul>
 * 返回的是弹药槽里<b>同一个</b> ItemStack 引用，原版射击时直接 split/shrink，消耗逻辑不用自己写。
 * 这个事件客户端也会触发（决定弓能不能拉开），所以腰带必须同步到客户端。
 */
public final class AmmoHandler {

    private AmmoHandler() {}

    @SubscribeEvent
    public static void onGetProjectile(LivingGetProjectileEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack ammo = ModRegistries.belt(player).getAmmo();
        if (ammo.isEmpty()) return;

        ItemStack found = event.getProjectileItemStack();
        if (!found.isEmpty() && (found == player.getItemInHand(InteractionHand.OFF_HAND)
                || found == player.getItemInHand(InteractionHand.MAIN_HAND))) {
            return;
        }

        ItemStack weapon = event.getProjectileWeaponItemStack();
        if (weapon.getItem() instanceof ProjectileWeaponItem weaponItem) {
            // 弩的"手持弹药"规则还允许烟花火箭，所以两个判断都试一下
            if (weaponItem.getAllSupportedProjectiles(weapon).test(ammo)
                    || weaponItem.getSupportedHeldProjectiles(weapon).test(ammo)) {
                event.setProjectileItemStack(ammo);
            }
        } else if (found.isEmpty()) {
            // 模组自定义远程武器触发取弹事件且未在背包找到专属弹药时，兜底供应腰带弹药
            event.setProjectileItemStack(ammo);
        }
    }
}
