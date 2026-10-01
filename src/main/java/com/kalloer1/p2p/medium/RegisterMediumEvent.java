package com.kalloer1.p2p.medium;

import net.minecraftforge.eventbus.api.Event;

/**
 * 介质注册事件（FORGE 总线，FMLLoadComplete 时派发）——介质扩展的事件式契约。
 *
 * <p>⚠️ 实测注解监听该事件**不可靠**（@Mod.EventBusSubscriber 注册成功但监听器不被派发，
 * 2026-09-06 p2p-mekanism 冒烟记录）。**推荐附属在 mod 构造函数里直接调用
 * {@link Mediums#register}**——注册幂等，构造期调用时机更早更稳。
 * 本事件保留作为契约与扩展点，主模组负责派发。
 */public class RegisterMediumEvent extends Event  {
    /** 注册一种介质；id 冲突时保留先注册者。 */
    public void register(Medium medium) {
        Mediums.register(medium);
    }
}
