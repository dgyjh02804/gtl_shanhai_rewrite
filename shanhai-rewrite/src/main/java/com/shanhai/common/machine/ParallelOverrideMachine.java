package com.shanhai.common.machine;

/**
 * <b>「玩家可调的并行数」契约 —— 原初主机与 24 台原初模块共用的一条。</b>
 *
 * <h2>为什么要有这个接口（而不是在两台机器上各写一份字段 + setter）</h2>
 * 用户 2026-09-27 原话（逐字）：
 * <blockquote>「在模块和主机的左下角再新增一个全新的按钮，他可以调节主机或者模块的并行数，
 * 作为一个输入框，可以让玩家输入数字，并且右边有一个一键调至最大的按钮」</blockquote>
 * 两台机器的 GUI 面板是同一个控件（{@link ParallelOverrideConfigurator}）⇒ 它只能依赖一个
 * <b>两边都实现</b>的契约。各写一份的后果是本工程最忌讳的形态：<b>两份实现迟早漂移</b>
 * （表现是"模块上的按钮改了并行、主机上的没改"，或者"GUI 显示的数与引擎真读的数不是同一个"）。
 *
 * <h2>语义（写死，控件与 tooltip 都必须照这个说）</h2>
 * <pre>
 *   parallelOverride == {@link #PARALLEL_AUTO}(0)  ⇒  【自动】用机器自己算出来的值
 *   parallelOverride &gt; 0                        ⇒  【覆盖】它就是该机器的并行<b>上限</b>
 *      🔴 且恒 {@code <= } {@link #getParallelOverrideCeiling()}（= 该机器此刻能达到的并行数）
 *         —— 2026-09-27 用户原话：「不允许玩家输入超出机器可以达到最大并行数的数字」
 * </pre>
 * 说它是<b>上限</b>而不是"精确值"的理由（不是随便定的）：两台机器的并行最终都要过
 * {@code IParallelLogic.getMaxParallel / getMinParallel} 这两道闸 —— 真正跑多少由
 * <b>可用输入量</b>与<b>输出空间</b>决定。玩家填 1000 而只有 5 份料时，行为必然是跑 5 份；
 * 把它说成"精确并行"就是<b>假数据</b>（本工程红线）。
 *
 * <h2>钳位纪律（服务端兜底，防伪造包）</h2>
 * 输入框自身有两道钳位（{@code TextFieldWidget.setNumbersOnly} 在客户端与服务端各跑一次），
 * 但网络包不保证只带合法值 ⇒ {@link #clampOverride(long)} 是<b>写入口那一层</b>的兜底：
 * <ul>
 *   <li>{@code <= 0}（含负数、含 0）⇒ 归到 {@link #PARALLEL_AUTO} = 自动，<b>不是</b>非法态；</li>
 *   <li>{@code > }{@link #PARALLEL_MAX} ⇒ 钳到上限；</li>
 *   <li>非数字永远到不了这里（控件那一层的 validator 会把它退回旧值）。</li>
 * </ul>
 *
 * @see ParallelOverrideConfigurator 侧栏那一个面板（左边输入框、右边「一键最大」）
 */
public interface ParallelOverrideMachine {

    /** 未覆盖：跟随机器自己算出来的值。 */
    long PARALLEL_AUTO = 0L;

    /**
     * 覆盖值的<b>绝对</b>上限（= {@code Long.MAX_VALUE}）。
     *
     * <h2>🔴 2026-09-27 用途收窄：它<b>不再</b>是「一键最大」填的那个数，也不再是钳位终点</h2>
     * 用户原话（逐字）：
     * <blockquote>「我说一键最大是到机器可以达到的并行数（也就是设置 0 时机器的并行数），
     * 而且也不允许玩家输入超出机器可以达到最大并行数的数字」</blockquote>
     * ⇒ 真正的天花板是 {@link #getParallelOverrideCeiling()}（= 该机器<b>当前</b>的
     * {@link #getAutoParallel()}），本常量降级为<b>数值类型的硬边界</b>：
     * 它只在"自动值本身取不到 / 溢出保护"时兜底，正常玩法里永远不会被触及
     * （主机的自动值就是 {@code Long.MAX_VALUE}，模块的自动值是并行表某一档）。
     */
    long PARALLEL_MAX = Long.MAX_VALUE;

    /**
     * 钳位（纯函数，唯一一份）。
     *
     * <p>⚠️ 它只做<b>类型层</b>的钳位（去掉负数、压到 {@code Long.MAX_VALUE}）。
     * 「不许超过这台机器能达到的并行数」那一条由 {@link #clampOverrideToCeiling(long)}
     * 完成 —— 因为它需要机器实例（要读当时的自动值）。
     *
     * @param value 任何来源的数（GUI 回调、网络包、命令）
     * @return {@code 0}（自动）或 {@code [1, }{@link #PARALLEL_MAX}{@code ]} 之间的值
     */
    static long clampOverride(long value) {
        if (value <= PARALLEL_AUTO) {
            return PARALLEL_AUTO;
        }
        return Math.min(value, PARALLEL_MAX);
    }

    /**
     * 🔴 <b>按本条机器【当前能达到的并行数】上钳（2026-09-27 用户实机提出）。</b>
     *
     * <pre>
     *   天花板 ceiling = max(0, getParallelOverrideCeiling())        // = getAutoParallel()
     *   ceiling <= 0  ⇒ 只有"自动"这一种合法态（返回 PARALLEL_AUTO）
     *   否则          ⇒ 0（自动） 或 [1, ceiling]
     * </pre>
     * 用<b>实例方法</b>而不是 {@code static}：天花板随机器状态变（模块的自动值每 3 tick 跟物质模块走），
     * 写成静态就只能读到一个过期快照。
     *
     * <p><b>为什么超限是"钳"而不是"拒绝"</b>：拒绝需要记住"上一个合法值"并回退输入框，
     * 而输入框的权威值来自服务端（{@code @DescSynced} 字段）⇒ 钳位后由既有的回灌机制
     * （{@code detectAndSendChanges} → {@code writeUpdateInfo(1, …)}）把钳过的值推回框里，
     * 玩家看到的就是"我刚打的数被改成了这台机器能达到的最大值" —— 比静默拒绝更不容易误解。
     *
     * @param value 任何来源的数（GUI 回调、网络包、命令）
     */
    default long clampOverrideToCeiling(long value) {
        final long ceiling = Math.max(0L, getParallelOverrideCeiling());
        if (ceiling <= PARALLEL_AUTO) {
            // 这台机器此刻只能自动（自动值为 0 / 未知）⇒ 除了"自动"没有合法覆盖值。
            return PARALLEL_AUTO;
        }
        return Math.min(clampOverride(value), ceiling);
    }

    /** 当前覆盖值；{@link #PARALLEL_AUTO} = 未覆盖。 */
    long getParallelOverride();

    /** 写入覆盖值。实现必须自己钳位（{@link #clampOverrideToCeiling(long)}）并在值真变了时才同步。 */
    void setParallelOverride(long value);

    /**
     * 🔴 <b>这台机器<b>当前</b>能达到的并行数 —— 「一键最大」填的就是它，玩家输入的上钳终点也是它。</b>
     *
     * <h2>用户原话（逐字）</h2>
     * <blockquote>「我说一键最大是到机器可以达到的并行数（也就是设置 0 时机器的并行数），
     * 而且也不允许玩家输入超出机器可以达到最大并行数的数字」</blockquote>
     * <h2>🔴 2026-09-27 语义改正（上一版填的是 {@link #PARALLEL_MAX}）</h2>
     * <pre>
     *   ⛔ 上一版：主机返回 MAX_PARALLEL（Long.MAX_VALUE）；模块返回 PARALLEL_MAX（Long.MAX_VALUE）
     *   ✅ 现行  ：返回 {@link #getAutoParallel()} ——「设 0 时机器能达到的那个并行数」
     *             主机：MAX_PARALLEL（本主机的自动值本来就是它 ⇒ 一键最大 = 恒等操作，用户口径 ④）
     *             模块：currentParallel（并行表按物质模块算出的值，默认 64）
     * </pre>
     * ⇒ <b>这个改动同时消掉了两个毛病</b>：①「一键最大」不再把一个玩家肉眼无法验证的
     * {@code 9223372036854775807} 塞进输入框；② 输入框有了一个<b>真实存在</b>的上限可钳。
     */
    long getParallelOverrideCeiling();

    /** 机器自己算出来的并行（未覆盖时用的那个）。<b>只用于显示</b>，不参与写入。 */
    long getAutoParallel();

    /** 覆盖生效之后的并行 —— <b>引擎真正读的那个数</b>。 */
    long getEffectiveParallel();
}
