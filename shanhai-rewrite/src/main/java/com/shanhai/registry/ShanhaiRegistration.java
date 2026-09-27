package com.shanhai.registry;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.shanhai.ShanhaiMod;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 全工程【唯一】的 REGISTRATE 持有类。
 *
 * <h2>为什么必须只有一份</h2>
 * {@link GTRegistrate} 内部持有一张「待注册 builder 列表」与一个
 * {@code registered} 开关（{@code GTRegistrate.registerEventListeners()} 里是
 * {@code AtomicBoolean.getAndSet(true)}，重复调用会被静默忽略）。若工程里出现第二个
 * {@code GTRegistrate.create("shanhai")} 实例，第二个实例的事件监听器<b>永远挂不上</b>，
 * 它注册的东西<b>一个都不会进注册表，且不抛异常</b>。
 * 因此物品（{@code com.shanhai.item}）与机器（{@code com.shanhai.machine}）都必须
 * 从这里取 {@link #REGISTRATE}，禁止各自 {@code create}。
 *
 * <h2>用法</h2>
 * <pre>{@code
 * ShanhaiRegistration.REGISTRATE.item("introductory_material_module", Item::new)....register();
 * }</pre>
 * 并在 mod 构造器里（<b>在 {@code init()} 之前</b>）调用
 * {@link #register(IEventBus)} 把本实例挂到 mod 事件总线。
 */
public final class ShanhaiRegistration {

    /** 全工程共用的 Registrate 实例，命名空间固定为 {@link ShanhaiMod#MOD_ID}。 */
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(ShanhaiMod.MOD_ID);

    private ShanhaiRegistration() {}

    /**
     * 把 {@link #REGISTRATE} 挂到 mod 事件总线。必须由 mod 构造器在注册任何内容之前调用。
     *
     * <p>等价于 {@code REGISTRATE.registerRegistrate()}（后者内部也是取
     * {@code FMLJavaModLoadingContext.get().getModEventBus()}），显式传总线便于测试与阅读。
     */
    public static void register(IEventBus modEventBus) {
        REGISTRATE.registerEventListeners(modEventBus);
    }
}
