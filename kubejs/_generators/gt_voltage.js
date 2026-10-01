// ═══════════════════════════════════════════════════════════════════════════════
// GT 电压表 + 元件名 ⇒ EUt —— 生成器侧的【唯一真源】
//   本文件被 gen_kjs.js（产物里的 EUt 值）与 gen_manifest.js（样板清单 §7.1 的表）
//   共用。⇒ 任何一处要改电压口径，**只改这里**，不要再在别处写死这些数字。
// ═══════════════════════════════════════════════════════════════════════════════
'use strict'

// ---------------------------------------------------------------- 真值出处（字节码，不是注释）
// 2026-09-29 实测（复现命令见文件末尾 §复现）：
//   解出 <gtceu jar>!com/gregtechceu/gtceu/api/GTValues.class 后 `javap -p -c`，
//   其 `<clinit>` 片段（dump 行号 106-113）：
//         106: dup
//         107: bipush        14
//         109: ldc2_w        #270                // long 2147483648l
//         112: lastore
//         113: putstatic     #273                // Field V:[J
//   ⇒ V 是 **15 项 long[]**（`bipush 15` / `newarray long`），索引 0..14 = ULV..MAX；
//     V[14] = **2147483648**（= 2^31）。
//   同一 <clinit> 里构造的 VN（15 项 String[]）末尾：
//         827: ldc_w  #353  // String OpV
//         834: ldc_w  #354  // String MAX
//         838: putstatic #356 // Field VN:[Ljava/lang/String;
//   ⇒ VN[14] === "MAX"（索引 0..14 = ULV, LV, MV, HV, EV, IV, LuV, ZPM, UV, UHV, UEV, UIV, UXV, OpV, MAX）
//
// 🔴 三个容易写错的点：
//   ① V[MAX] = 2147483648，**不是** Integer.MAX_VALUE(2147483647)。
//      旧 gen_manifest.js 的本地表 `MAX: 2147483647` 就是错的（本文件建立时已删除该表）。
//   ② 同 class 里还有一个 **31 项** 的 `VEX:[J`（0..30 = 8, 32, …, 9223372036854775807），
//      它是 GTCEu 自己的"扩展档位表"。本工程**不用 VEX**：星门的 EUt 按用户裁决的
//      `V[MAX] × 4^8` 算式算（见下），不要拿 VEX[22] 来"凑"同一个数字。
//   ③ V 是 long[]，`.EUt` 的签名也是 `EUt(long)` ⇒ 乘积只要 ≤ Long.MAX_VALUE 就装得下。
var V = {
    ULV: 8, LV: 32, MV: 128, HV: 512, EV: 2048, IV: 8192,
    LuV: 32768, ZPM: 131072, UV: 524288, UHV: 2097152,
    UEV: 8388608, UIV: 33554432, UXV: 134217728, OpV: 536870912,
    MAX: 2147483648
}
var VN = ['ULV', 'LV', 'MV', 'HV', 'EV', 'IV', 'LuV', 'ZPM', 'UV', 'UHV', 'UEV', 'UIV', 'UXV', 'OpV', 'MAX']

// ---------------------------------------------------------------- 星门（MAX+8）的 EUt
// 用户 2026-09-26 的两次裁决（原话逐字，后一条【覆盖】前一条）：
//   ① 「MAX+16=MAX，4^16A」                ← **已作废**
//        4^16 = 2^32 ⇒ EUt = 2^31 × 2^32 = 2^63 = Long.MAX_VALUE + 1
//        ⇒ 恰好越界 1 ⇒ 回绕成 −9223372036854775808（负数）
//   ② 「溢出那就算了，改成 max+8=max,4^8A」  ← **现行口径**
//        ⇒ 电压 = MAX 档，电流 = 4^8 A
//        ⇒ EUt = V[MAX] × 4^8 = 2^31 × 2^16 = **2^47**，只有 Long.MAX(2^63−1) 的 1/65536
//          ⇒ 【不溢出】，可以整体写进 `.EUt(long)`
// ⚠️ 4^8 与 4^16 是【两个不同的裁决】，别写串。产物文件头 §8 里留着 ① 作废口径的留档。
var STARGATE_AMPS_EXP = 8
var STARGATE_EUT = V.MAX * Math.pow(4, STARGATE_AMPS_EXP)

// ---------------------------------------------------------------- 自检（防"改错了还静默跑过"）
// 断言里只用【独立算出来的恒等式】，不复制上面的数据 —— 复制等于没检查。
function assert(cond, msg) { if (!cond) throw new Error('[gt_voltage] 自检失败：' + msg) }
assert(VN.length === 15, 'VN 应为 15 项')
assert(VN[14] === 'MAX', 'VN[14] 必须是 MAX（读到 ' + VN[14] + '）')
assert(V.MAX === Math.pow(2, 31), 'V[MAX] 必须是 2^31（读到 ' + V.MAX + '）')
assert(V.ULV === Math.pow(2, 3) && V.LV === Math.pow(2, 5) && V.MV === Math.pow(2, 7), 'ULV/LV/MV 应为 2^3/2^5/2^7')
// 每一档都必须是上一档 ×4（GT 电压表的定义），从 ULV=8 起共 15 档
for (var _i = 1; _i < VN.length; _i++) assert(V[VN[_i]] === V[VN[_i - 1]] * 4, VN[_i] + ' 应为 ' + VN[_i - 1] + ' × 4')
assert(STARGATE_EUT === Math.pow(2, 47), '星门 EUt 必须是 2^47（读到 ' + STARGATE_EUT + '）')
assert(STARGATE_EUT <= Number.MAX_SAFE_INTEGER, '星门 EUt 超出 JS 安全整数范围')
// 留档口径的越界性：4^16 口径 = V[MAX] × 4^16 = 2^63 = Long.MAX_VALUE + 1 ⇒ 不可用。
// ⚠️ 用恒等式比较（2^63），不要跟 9223372036854775807 那个字面量比 —— JS 里它解析成 9223372036854775808。
assert(V.MAX * Math.pow(4, 16) === Math.pow(2, 63), '4^16 口径应当恰好等于 2^63（留档用）')
assert(STARGATE_EUT < Math.pow(2, 63), '现行 4^8 口径必须严格小于 2^63（不溢出）')

// ---------------------------------------------------------------- 元件名（AE2 样板显示名）⇒ 档位
// ⚠️ 键 = PF.txt 里那个元件（AE2 样板箱）的 **display.Name 逐字**。
//    改 PF.txt 里的元件名 ⇒ 必须同步改这里（改不到就会落到 undefined ⇒ gen_kjs.js 会【报错并拒绝】，
//    这是刻意的：宁可生成期报错，也不要像 2026-09-26 那次一样把 `EUt(Undefined)` 带进游戏）。
var CELL_TIER = {
    '处理样板ULV': 'ULV',
    '处理样板LV': 'LV',
    '处理样板MV': 'MV',
    '处理样板HV': 'HV',
    '处理样板EV': 'EV',
    // ⚠️「处理样板-星门(MAX+16)」这个显示名里写的是已被 2 次改判作废的 MAX+16，
    //    但**样板名不动**（PF.txt 源数据原文），只改落地的 EUt 值 ⇒ 单独列在下面。
    '处理样板-星门(MAX+16)': 'MAX'
}
/** 元件名 ⇒ 该元件的 EUt。推不出来返回 undefined（调用方必须显式处理，不许兜底成某个数）。 */
function cellEUt(name) {
    if (name === '处理样板-星门(MAX+16)') return STARGATE_EUT
    var t = CELL_TIER[name]
    return t ? V[t] : undefined
}
/** 元件名 ⇒ 档位名（'MAX' / 'ULV' …）。推不出来返回 undefined。 */
function cellTier(name) { return CELL_TIER[name] }
/** 该元件是不是"星门"（MAX+8 口径）那一档。 */
function isStargateCell(name) { return name === '处理样板-星门(MAX+16)' }

module.exports = {
    V: V, VN: VN,
    STARGATE_AMPS_EXP: STARGATE_AMPS_EXP, STARGATE_EUT: STARGATE_EUT,
    CELL_TIER: CELL_TIER, cellEUt: cellEUt, cellTier: cellTier, isStargateCell: isStargateCell
}

// ---------------------------------------------------------------- §复现（本次判定真值用的命令）
//  $jar = '<SH_INSTANCE>\mods\gtceu-1.20.1-1.4.4.jar'      # 环境变量 SH_INSTANCE = 游戏实例根目录
//  # 本仓库不写机器绝对路径 ⇒ 这里用占位符，自行把 <SH_INSTANCE> 换成你的实例根目录。
//  # 解出 GTValues.class（jar 是 zip）：
//  Add-Type -AssemblyName System.IO.Compression.FileSystem
//  $zip=[System.IO.Compression.ZipFile]::OpenRead($jar)
//  ([System.IO.Compression.ZipFileExtensions]::ExtractToFile(
//      ($zip.Entries|?{$_.FullName -eq 'com/gregtechceu/gtceu/api/GTValues.class'}), "$out\GTValues.class", $true))
//  # 反编译常量：
//  javap -p -c "$out\GTValues.class" > javap_GTValues.txt
//  # 本次实测 dump 落到 temp\verify-eut\javap_GTValues.txt（1160 行）
