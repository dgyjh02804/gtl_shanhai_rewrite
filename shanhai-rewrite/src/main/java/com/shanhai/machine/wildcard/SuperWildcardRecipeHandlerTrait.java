package com.shanhai.machine.wildcard;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase;

/**
 * 超级通配符ME样板总成的 ME 配方接收/输出句柄。
 *
 * <h2>🔴 为什么不能直接用 gtlcore 的 {@code WildcardRecipeHandlerTrait}</h2>
 * 它虽然是 {@code public}，但里面的处理器在<b>构造时就硬转</b>成 gtlcore 自己的机器类型
 * （反编译原文，{@code WildcardRecipeHandlerTrait.java:41/51}）：
 * <pre>
 *   new WildcardItemInputHandler(this.getMachine(), io)   // 参数类型 = MEWildcardPatternBufferPartMachine
 *   … 而 getMachine() 体是 (MEWildcardPatternBufferPartMachine) super.getMachine()
 * </pre>
 * 本机不是那个类的子类（见 {@code SuperWildcardPatternBufferPartMachine} 类注释：我们直接继承
 * {@code MEPatternBufferPartMachineBase}）⇒ 用它会在<b>构造仓室那一刻</b>抛 {@code ClassCastException}。
 * 用 {@code MEPatternBufferRecipeHandlerTrait} 也同样不行（它转的是 {@code MEPatternBufferPartMachine}）。
 *
 * <h2>⇒ 本类的做法：与 gtlcore <b>同一层</b>基类上的<b>另一种合法组合</b></h2>
 * 直接继承 {@link MEPatternBufferRecipeHandlerTraitBase} —— 它<b>全部逻辑都写在基类里</b>，
 * 且 {@code getMachine()} 返回的正是基类型 {@link MEPatternBufferPartMachineBase}
 * （反编译原文 {@code MEPatternBufferRecipeHandlerTraitBase.java:44-46}）。
 * 于是「物品/流体句柄读的是哪台机器的哪个槽」这件事**完全不依赖具体机器类型**，
 * 本类只需要把两个抽象的处理器工厂接上。
 *
 * <p>⇒ 行为与 gtlcore 的野生通配符总成<b>逐字同源</b>：同一个基类、同一段
 * {@code meHandleRecipeInner / prepareMEHandleContents / getActiveSlots} 实现，
 * 差别只有「槽位表由谁提供」——由我们的机器给（全局样板索引 0..N）。
 * 这正是需求②「拥有通配符ME样板总成的<b>全部功能</b>」在机制上的落点。
 */
public class SuperWildcardRecipeHandlerTrait extends MEPatternBufferRecipeHandlerTraitBase {

    /**
     * LDLib 的托管字段持有者。
     *
     * <p>⚠️ 本类**没有任何 {@code @Persisted}/{@code @DescSynced} 字段**，
     * 所以这里只需要一个非 null 的持有者即可。写法对齐 gtlcore 的
     * {@code WildcardRecipeHandlerTrait.MANAGED_FIELD_HOLDER}（同样是单参构造）。
     * 不覆写 {@code getFieldHolder()} 也能跑，但上游两个同类都覆写了 —— 跟随上游，减少未知。
     */
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(SuperWildcardRecipeHandlerTrait.class);

    public SuperWildcardRecipeHandlerTrait(MEPatternBufferPartMachineBase ioBuffer, IO io) {
        super(ioBuffer, io);
    }

    @Override
    protected MEItemInputHandlerBase createMEItemHandler(IO io) {
        return new SuperItemInputHandler(this.getMachine(), io);
    }

    @Override
    protected MEFluidHandlerBase createMEFluidHandler(IO io) {
        return new SuperFluidHandler(this.getMachine(), io);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * 让两个 ME 句柄各自通知监听者「槽内内容变了」。
     *
     * <h2>🔴 为什么要绕这一手，而不是让机器直接调 {@code getMeItemHandler().notifyListeners()}</h2>
     * {@code getMeItemHandler()} 的<b>返回类型</b> {@code MEItemInputHandlerBase} 是
     * {@link MEPatternBufferRecipeHandlerTraitBase} 的 <b>protected</b> 内部类。
     * 本方法所在的类是它的子类 ⇒ 能看见；而<b>机器类不是</b>（机器继承的是
     * {@code MEPatternBufferPartMachineBase}，是另一条链）⇒ 在机器里写那一句会报
     * 「{@code notifyListeners()} 在不可访问的类或接口中定义」。
     * 实测：本轮首次 {@code gradle compileJava} 就是这么挂的（原文见交付报告）。
     * ⇒ 把这一步留在<b>能看见的地方</b>（本类），对外只暴露这个方法。
     */
    public void notifyHandlers() {
        this.getMeItemHandler().notifyListeners();
        this.getMeFluidHandler().notifyListeners();
    }

    /**
     * 物品句柄。<b>刻意不覆写 {@code getMachine()}</b> —— 基类型就是我们要的类型，
     * 多一层强转只会把「机器类型」重新绑死（gtlcore 就是在这一步把自己绑死的）。
     */
    public static class SuperItemInputHandler extends MEItemInputHandlerBase {

        public SuperItemInputHandler(MEPatternBufferPartMachineBase machine, IO io) {
            super(machine, io);
        }
    }

    /** 流体句柄，理由同上。 */
    public static class SuperFluidHandler extends MEFluidHandlerBase {

        public SuperFluidHandler(MEPatternBufferPartMachineBase machine, IO io) {
            super(machine, io);
        }
    }
}
