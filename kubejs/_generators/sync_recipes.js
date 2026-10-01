'use strict'
// ═══════════════════════════════════════════════════════════════════════════════
// 山海配方【一键同步】 —— PF.txt → 中间产物 → KubeJS 产物 → 游戏实例
// ═══════════════════════════════════════════════════════════════════════════════
// 入口：桌面上的 `同步山海配方.bat`（双击）。逻辑全在本文件里，bat 只负责
//       「切 UTF-8 代码页 + 调用 node + 暂停不关窗」。
//
// 它按顺序做 3 步生成 + 1 次部署，每一步都先判断【到底要不要跑】：
//   ① parse_pf.js     PF.txt            → recipe-convert\parsed.json / parsed.txt
//   ② gen_manifest.js parsed.json       → recipe-convert\rows.json / 样板清单.md
//   ③ gen_kjs.js      rows.json         → kubejs\server_scripts\[server_scripts]shanhai_recipes.js
//                                       → recipe-convert\specs.json
//   ④ 门禁 → 备份 → 部署到 GTL山海9.10test → 部署后逐文件核对
//
// 🔴 判据（"要不要跑"）用【两份东西】合起来判，都真查：
//   (a) 现成的「过期防线」`kubejs\_generators\provenance.js`：
//       每个中间产物都声明了自己对应的 PF.txt 的 sha256 ⇒ 与磁盘现值不符就是旧的。
//   (b) 内容哈希链：上次成功跑完时把【每一步的输入与输出】的 sha256 记在
//       `temp\sync-tool\state.json`；下次若任何输入变了 / 任何输出不见了或被改过 ⇒ 这一步要跑。
//       ⚠️ state.json 只存在于 temp\sync-tool\ 下，丢了不影响正确性（丢了就整条重跑一遍）。
//   ⇒ 两条都说不脏 ⇒ 跳过，并【明确打印"跳过"】。绝不假装干了活。
//
// 🔴 绝不伪造：所有数字都是从产物文件里【现读现算】的（读不到就写"读不到"）。
//
// ⚠️ 本文件放在 `_generators\`（下划线开头 ⇒ KubeJS 不加载它）。
var fs = require('fs')
var path = require('path')
var crypto = require('crypto')
var cp = require('child_process')

// ═══════════════════════════════════════════════════════════════════════════════
// 🔴 路径来源纪律（上传前清理）：本仓库里【不写任何机器绝对路径】。
//    · 仓库【内】的路径 ⇒ 按【脚本自身位置】(__dirname) 推（不用 process.cwd()）；
//    · 仓库【外】的路径（PF.txt / 游戏实例）⇒ 从环境变量读；缺了就【响亮抛错并退出】。
//    ⚠️ 改动说明：原先这里是**写死**的（双击 bat 不需要任何输入）。现在改成环境变量后，
//       **双击前必须先设好这两个变量**，否则本脚本会拒绝跑（这是故意的，不是 bug）：
//         $env:SH_PF_SRC   = '<PF.txt 的绝对路径>'
//         $env:SH_INSTANCE = '<游戏实例根目录>'
// ═══════════════════════════════════════════════════════════════════════════════
var REPO = path.join(__dirname, '..', '..')          // kubejs\_generators → 仓库根
function envPath(name, what, example) {
    var v = process.env[name]
    if (v === undefined || String(v).trim() === '') {
        throw new Error('🔴 缺少环境变量 ' + name + '（' + what + '）\n'
            + '   ⇒ 请先设置它，例如（PowerShell）：$env:' + name + " = '" + example + "'\n"
            + '   ⇒ 本脚本【拒绝】在缺少它的前提下继续运行：那会拿错路径、静默产出错产物。')
    }
    return String(v).trim()
}
var INSTANCE = envPath('SH_INSTANCE', '游戏实例的【根目录】（其下有 mods\\ 与 local\\kubejs\\export\\）',
    'D:\\Minecraft\\versions\\<你的实例目录名>') + path.sep
var PF_FILE = envPath('SH_PF_SRC', 'PF.txt（AE2 样板导出的 NBT 文本）的绝对路径',
    'D:\\path\\to\\PF.txt')

// ═══════════════════════════════════════════════════════════════════════════════
// 0. 路径（仓库内 = 按脚本自身位置推；仓库外 = 环境变量 SH_PF_SRC / SH_INSTANCE）
// ═══════════════════════════════════════════════════════════════════════════════
var CFG = {
    WS: REPO + path.sep,
    PF: PF_FILE,
    DEPLOY_DIR: INSTANCE + 'kubejs\\server_scripts\\'
}
var GEN = CFG.WS + 'kubejs\\_generators\\'
var RC = CFG.WS + 'recipe-convert\\'
var SERVER_SCRIPTS = CFG.WS + 'kubejs\\server_scripts\\'
var PRODUCT_NAME = '[server_scripts]shanhai_recipes.js'
var PRODUCT = SERVER_SCRIPTS + PRODUCT_NAME
var STATE = CFG.WS + 'temp\\sync-tool\\state.json'
var BACKUP_DIR = CFG.WS + 'temp\\sync-tool\\backups\\'
var INSTANCE_NAME = 'GTL山海9.10test'

// ═══════════════════════════════════════════════════════════════════════════════
// 1. 小工具
// ═══════════════════════════════════════════════════════════════════════════════
var W = function (s) { process.stdout.write((s === undefined ? '' : s) + '\r\n') }
var H = function () { W('') }
var ts = function (d) {
    d = d || new Date()
    var p = function (n, w) { return String(n).padStart(w || 2, '0') }
    return String(d.getFullYear()) + p(d.getMonth() + 1) + p(d.getDate()) + '-' + p(d.getHours()) + p(d.getMinutes()) + p(d.getSeconds())
}
function sha256File(p) { return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex').toUpperCase() }
function exists(p) { try { fs.statSync(p); return true } catch (e) { return false } }
function shaOrNull(p) { try { return sha256File(p) } catch (e) { return null } }
function short(h, n) { return h ? h.slice(0, n || 16) : '(读不到)' }
function localTime(s) {
    try { var d = new Date(s); return d.toLocaleString('zh-CN', { hour12: false }) } catch (e) { return String(s) }
}

/** 统一的失败出口：把"哪一步 / 原始报错 / 怎么修"三件套打出来，然后退出 1。 */
function abort(where, raw, hint) {
    H()
    W('════════════════════════════════════════════════════════════')
    W(' ❌ 同步失败 —— 停在这一步：' + where)
    W('════════════════════════════════════════════════════════════')
    if (raw) { W('【原始输出（未做任何改写，就是它吐出来的原文）】'); W(raw) }
    if (hint) { W('【怎么修】' + hint) }
    W('')
    W('⇒ 本次【没有把任何东西装进游戏实例】（部署发生在全部步骤通过之后）。')
    process.exit(1)
}

// ── 过期防线（现成的机制，直接用，不另立一套） ────────────────────────────────
var PROV
try { PROV = require('./provenance.js') } catch (e) {
    abort('加载过期防线 provenance.js', String(e && e.stack || e),
        '`kubejs\\_generators\\provenance.js` 读不到或被改坏了；它是整条流水线的过期判据，必须是好的。')
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. 读 PF.txt（先确认它真的读得了 —— 用户可能刚导出、文件还在写）
// ═══════════════════════════════════════════════════════════════════════════════
W('════════════════════════════════════════════════════════════')
W(' 山海配方一键同步')
W('════════════════════════════════════════════════════════════')

function readSource() {
    var b1
    try { b1 = fs.readFileSync(CFG.PF) } catch (e) {
        abort('读取 PF.txt', '读 ' + CFG.PF + ' 时报错：' + (e && e.code ? e.code + ' ' : '') + (e && e.message),
            'PF.txt 读不了，可能还在写（刚导出）／被别的程序占着／不在这里。等导出彻底结束再双击一次。')
    }
    if (!b1 || b1.length === 0) {
        abort('读取 PF.txt', '文件读到了，但长度是 0 字节：' + CFG.PF,
            'PF.txt 是空的。导出没写完就先别同步，等它写满再双击一次。')
    }
    var s1 = crypto.createHash('sha256').update(b1).digest('hex').toUpperCase()
    // 稳定性检查：隔 300ms 再读一次，两次不一致 ⇒ 有人正在往里写
    var t = Date.now(); while (Date.now() - t < 300) { /* 空转 300ms */ }
    var b2
    try { b2 = fs.readFileSync(CFG.PF) } catch (e) {
        abort('读取 PF.txt（第二次）', '第二次读 ' + CFG.PF + ' 时报错：' + (e && e.code ? e.code + ' ' : '') + (e && e.message),
            'PF.txt 读不了，可能还在写。等导出彻底结束再双击一次。')
    }
    var s2 = crypto.createHash('sha256').update(b2).digest('hex').toUpperCase()
    if (s1 !== s2) {
        abort('读取 PF.txt（稳定性检查）',
            '相隔 0.3 秒读到两个不同的内容：\r\n  第一次 sha256 = ' + s1 + '\r\n  第二次 sha256 = ' + s2,
            'PF.txt 正在被写入（多半是导出还没结束）。等它写完再双击一次，别在半路同步。')
    }
    var st = fs.statSync(CFG.PF)
    return { sha: s1, bytes: st.size, mtime: st.mtime.toISOString(), mtimeMs: st.mtimeMs }
}

var PF = readSource()
W('')
W(' 源头：   ' + CFG.PF)
W('         ' + PF.sha)
W('         ' + PF.bytes.toLocaleString('en-US') + ' B   改于 ' + localTime(PF.mtime))
W('')

// ═══════════════════════════════════════════════════════════════════════════════
// 3. 步骤定义（每步：输入有哪些 / 输出有哪些 —— 判据就用这些算）
// ═══════════════════════════════════════════════════════════════════════════════
var LANG = RC + 'lang\\'
var STEPS = [
    {
        key: 'parse', title: '① 解析 PF.txt（parse_pf.js）', script: GEN + 'parse_pf.js',
        inputs: [
            { k: 'PF.txt', p: CFG.PF },
            { k: 'parse_pf.js', p: GEN + 'parse_pf.js' },
            { k: 'provenance.js', p: GEN + 'provenance.js' }
        ],
        outputs: [RC + 'parsed.json', RC + 'parsed.txt'],
        provArtifact: RC + 'parsed.json'
    },
    {
        key: 'manifest', title: '② 生成样板清单 / rows（gen_manifest.js）', script: GEN + 'gen_manifest.js',
        inputs: [
            { k: 'parsed.json', p: RC + 'parsed.json' },
            { k: 'gen_manifest.js', p: GEN + 'gen_manifest.js' },
            { k: 'gt_voltage.js', p: GEN + 'gt_voltage.js' },
            { k: 'provenance.js', p: GEN + 'provenance.js' },
            { k: 'lang/gtceu_zh_cn.json', p: LANG + 'gtceu_zh_cn.json' },
            { k: 'lang/shanhai_zh_cn.json', p: LANG + 'shanhai_zh_cn.json' }
        ],
        outputs: [RC + 'rows.json', RC + '样板清单.md'],
        provArtifact: RC + 'rows.json'
    },
    {
        key: 'kjs', title: '③ 生成 KubeJS 产物（gen_kjs.js）', script: GEN + 'gen_kjs.js',
        inputs: [
            { k: 'rows.json', p: RC + 'rows.json' },
            { k: 'parsed.json', p: RC + 'parsed.json' },
            { k: 'gen_kjs.js', p: GEN + 'gen_kjs.js' },
            // 🔴 2026-09-30 补：`type_names.js`（语言文件自动反查表）是 gen_kjs.js 的【新依赖】，
            //    它决定"纸上的中文类型名认不认得出来"。不登记进来的话，改了它这一步【不会重跑】
            //    ⇒ 产物与生成器不一致而没人知道（这正是本工具第 3 节末尾列的那类"没纳入判据的输入"）。
            { k: 'type_names.js', p: GEN + 'type_names.js' },
            { k: 'gt_voltage.js', p: GEN + 'gt_voltage.js' },
            { k: 'provenance.js', p: GEN + 'provenance.js' }
        ],
        outputs: [PRODUCT, RC + 'specs.json'],
        provArtifact: PRODUCT
    }
]

// ── 读上次记录 ────────────────────────────────────────────────────────────────
function readState() {
    try {
        var j = JSON.parse(fs.readFileSync(STATE, 'utf8'))
        if (j && typeof j === 'object' && j.steps) return j
    } catch (e) { }
    return { _note: '一键同步的运行记录（输入/输出的 sha256）。删掉它只会让下次全部重跑一遍，不会出错。', steps: {} }
}
function writeState(s) {
    try {
        fs.mkdirSync(path.dirname(STATE), { recursive: true })
        var tmp = STATE + '.tmp'
        fs.writeFileSync(tmp, JSON.stringify(s, null, 1), 'utf8')
        fs.renameSync(tmp, STATE)
    } catch (e) {
        // 写不了记录不算致命：只是下次会全部重跑一遍
        W('  ⚠️ 运行记录写不进去（' + (e && e.message) + '）⇒ 下次会整条重跑，不影响正确性。')
    }
}
var STATE_OBJ = readState()

/** 这一步要不要跑？返回 {dirty:bool, why:[...]}，why 里逐条写原因。 */
function stepDirty(st) {
    var why = []
    var rec = STATE_OBJ.steps[st.key]
    // (a) 过期防线：主要产物声明的源头 sha 与磁盘现值比
    var d = PROV.readDeclared(st.provArtifact)
    if (d.how === 'MISSING') why.push('产物 ' + path.basename(st.provArtifact) + ' 不存在')
    else if (d.sha === null) why.push('过期防线：' + path.basename(st.provArtifact) + ' 没声明自己对应哪一版 PF.txt')
    else if (d.how === 'MANIFEST-STALE') why.push('过期防线：' + path.basename(st.provArtifact) + ' 在登记之后被改写过')
    else if (d.sha !== PF.sha) why.push('过期防线：' + path.basename(st.provArtifact) + ' 声明的是【旧的】PF.txt')
    // (b) 输出在不在
    for (var i = 0; i < st.outputs.length; i++) {
        if (!exists(st.outputs[i])) why.push('输出缺失：' + path.basename(st.outputs[i]))
    }
    // (c) 修改时间序：任何输入比最老的输出还新 ⇒ 产物是"输入变了之后没重做过"的
    //     ⚠️ 这一条【不依赖 state.json】，所以第一次运行（没有记录时）也不会误判成"全都要跑"。
    var oldestOut = null
    for (var o = 0; o < st.outputs.length; o++) {
        try {
            var mt = fs.statSync(st.outputs[o]).mtimeMs
            if (oldestOut === null || mt < oldestOut) oldestOut = mt
        } catch (e) { }
    }
    if (oldestOut !== null) {
        for (var p2 = 0; p2 < st.inputs.length; p2++) {
            try {
                if (fs.statSync(st.inputs[p2].p).mtimeMs > oldestOut) {
                    why.push('输入比产物新：' + st.inputs[p2].k)
                }
            } catch (e) { why.push('输入读不到：' + st.inputs[p2].k) }
        }
    }
    // (d) 内容哈希链（有记录时用精确保）
    if (rec) {
        for (var j = 0; j < st.inputs.length; j++) {
            var cur = shaOrNull(st.inputs[j].p)
            var old = rec['in_' + st.inputs[j].k]
            if (cur === null) why.push('输入读不到：' + st.inputs[j].k)
            else if (old !== cur) why.push('输入变了：' + st.inputs[j].k + '（sha256 与上次不同）')
        }
        for (var m = 0; m < st.outputs.length; m++) {
            var cur2 = shaOrNull(st.outputs[m])
            var old2 = rec['out_' + path.basename(st.outputs[m])]
            if (cur2 === null) why.push('输出读不到：' + path.basename(st.outputs[m]))
            else if (old2 !== cur2) why.push('输出被改过：' + path.basename(st.outputs[m]) + '（sha256 与上次不同）')
        }
    }
    return { dirty: why.length > 0, why: why }
}

/** 跑一步：node 脚本，收原始 stdout/stderr，退出码非 0 就停。 */
function runStep(st) {
    var t0 = Date.now()
    var r = cp.spawnSync(process.execPath, [st.script], {
        cwd: GEN, encoding: 'utf8', maxBuffer: 128 * 1024 * 1024, windowsHide: true
    })
    var ms = Date.now() - t0
    var out = (r.stdout || '')
    var err = (r.stderr || '')
    if (r.error) {
        abort(st.title + '（连启动都没启动起来）',
            'spawn 失败：' + (r.error.code || '') + ' ' + r.error.message,
            'node 找不到或脚本路径不对。先确认 `' + st.script + '` 在，且 `node -v` 能跑。')
    }
    if (r.status !== 0) {
        var tail = function (s, n) {
            var L = s.split(/\r?\n/)
            if (L.length <= n) return s
            return '…（前面省略 ' + (L.length - n) + ' 行）\r\n' + L.slice(L.length - n).join('\r\n')
        }
        var raw = ''
        if (out.trim()) raw += '--- 它打到标准输出（末尾 60 行）---\r\n' + tail(out, 60) + '\r\n'
        if (err.trim()) raw += '--- 它打到标准错误（末尾 60 行）---\r\n' + tail(err, 60) + '\r\n'
        if (!raw) raw = '(这一步既没打标准输出也没打标准错误，退出码 = ' + r.status + ')'
        raw = raw.replace(/\r?\n$/, '')
        abort(st.title + '（退出码 = ' + r.status + '）', raw,
            '看上面【最后那几行】的报错原文。它是生成脚本自己判断"这份东西不对劲"才拒绝干的，'
            + '照那几行里说的改源头（多为 PF.txt 里某一行）或改生成脚本，然后重新双击。')
    }
    return { ms: ms, out: out, err: err }
}

/** 提取生成脚本里那几行"给人看的审计结论"（原样引用，不改写）。 */
function highlight(s) {
    var L = String(s).split(/\r?\n/), keep = []
    for (var i = 0; i < L.length; i++) {
        if (/合成样板分流|真·跳过|条数回填|slot overflows|^\s+手写区 |条数（\*\*本次现算/.test(L[i])) keep.push(L[i].trim())
    }
    return keep
}

// ═══════════════════════════════════════════════════════════════════════════════
// 4. 按顺序判断 + 跑
// ═══════════════════════════════════════════════════════════════════════════════
var productShaBefore = shaOrNull(PRODUCT)
var deployedBefore = shaOrNull(CFG.DEPLOY_DIR + PRODUCT_NAME)

W(' ── 步骤判断 ──────────────────────────────────────────')
var ranCount = 0, ranNames = [], skippedNames = []
for (var si = 0; si < STEPS.length; si++) {
    var st = STEPS[si]
    var dj = stepDirty(st)
    if (!dj.dirty) {
        W('   ⏭  ' + st.title + ' —— PF.txt 没变，跳过生成')
        skippedNames.push(st.title)
        continue
    }
    W('   ▶  跑 ' + st.title)
    for (var wi = 0; wi < dj.why.length; wi++) W('        因为：' + dj.why[wi])
    var res = runStep(st)
    ranCount++; ranNames.push(st.title)
    W('        完成，耗时 ' + (res.ms / 1000).toFixed(1) + ' 秒')
    var hl = highlight(res.out)
    for (var hi = 0; hi < hl.length; hi++) W('        · ' + hl[hi])
    // 记录这一步的输入 / 输出（内容哈希链）
    var rec = {}
    for (var k1 = 0; k1 < st.inputs.length; k1++) rec['in_' + st.inputs[k1].k] = shaOrNull(st.inputs[k1].p)
    for (var k2 = 0; k2 < st.outputs.length; k2++) rec['out_' + path.basename(st.outputs[k2])] = shaOrNull(st.outputs[k2])
    rec.at = new Date().toISOString()
    STATE_OBJ.steps[st.key] = rec
    writeState(STATE_OBJ)
}
W('')
if (ranCount === 0) W(' ⇒ 源头一个字没变，三步全部跳过（没有重新生成任何东西）。')
else W(' ⇒ 共跑了 ' + ranCount + ' 步，跳过 ' + skippedNames.length + ' 步。')
W('')

// ═══════════════════════════════════════════════════════════════════════════════
// 5. 门禁（不通过就不部署）
// ═══════════════════════════════════════════════════════════════════════════════
W(' ── 门禁 ──────────────────────────────────────────────')
if (!exists(PRODUCT)) abort('门禁：产物不存在', '找不到 ' + PRODUCT, '生成脚本没写出产物。先看上面那一步的报错。')
var prodBytes = fs.statSync(PRODUCT).size
if (prodBytes <= 0) abort('门禁：产物是空文件', PRODUCT + ' 长度 = ' + prodBytes + ' B', '产物为空，不能部署。先看生成那一步的报错。')
var chk = cp.spawnSync(process.execPath, ['--check', PRODUCT], { encoding: 'utf8', windowsHide: true })
if (chk.status !== 0) {
    abort('门禁：node --check 没通过（产物语法就是坏的）',
        ((chk.stdout || '') + (chk.stderr || '')).trim() || '(没有输出，退出码 ' + chk.status + ')',
        '产物不是合法 JS，绝不能装进游戏。这是生成脚本产出了坏文件，先看第 ③ 步的报错。')
}
var chk2 = cp.spawnSync(process.execPath, ['-e', 'require("fs").readFileSync(process.argv[1]);process.exit(0)', PRODUCT], { encoding: 'utf8', windowsHide: true })
if (chk2.status !== 0) abort('门禁：产物读不出来', ((chk2.stderr || '').trim() || '(无输出)'), '产物被独占锁定或损坏。关掉占用它的程序再试。')
var productSha = sha256File(PRODUCT)
W('   ✅ 产物非空：' + prodBytes.toLocaleString('en-US') + ' B')
W('   ✅ node --check 通过（产物是合法 JS）')
W('   ✅ 产物可读')
W('   产物哈希：' + short(productShaBefore, 32) + '  →  ' + short(productSha, 32)
    + '   （' + (productShaBefore === null ? '这次才第一次生成' : (productShaBefore === productSha ? '没变' : '变了')) + '）')
W('')

// ═══════════════════════════════════════════════════════════════════════════════
// 6. 现算配方条数（从产物文件里读，读不到就说读不到，绝不编）
// ═══════════════════════════════════════════════════════════════════════════════
function readNumbers() {
    var n = { bench: null, gt: null, total: null, hands: null, handsHow: null, how: null }
    var text
    try { text = fs.readFileSync(PRODUCT, 'utf8') } catch (e) { return n }
    var m = /工作台 (\d+) 条 \+ GT 机器 (\d+) 条 = (\d+) 条/.exec(text)
    if (m) { n.bench = Number(m[1]); n.gt = Number(m[2]); n.total = Number(m[3]); n.how = '产物文件头' }
    var lines = text.split(/\r?\n/)
    var a = -1, b = -1
    for (var i = 0; i < lines.length; i++) {
        if (a < 0 && lines[i].indexOf('手写区 开始') >= 0) a = i
        else if (a >= 0 && b < 0 && lines[i].indexOf('手写区 结束') >= 0) b = i
    }
    if (a >= 0 && b > a) { n.hands = b - a - 1; n.handsHow = '产物文件内标记行之间现数' }
    return n
}
var NUM = readNumbers()
var NUMTXT = function () {
    var pb = NUM.bench === null ? '读不到' : NUM.bench + ' 条'
    var pg = NUM.gt === null ? '读不到' : NUM.gt + ' 条'
    var ph = NUM.hands === null ? '读不到' : NUM.hands + ' 行'
    return 'GT ' + pg + ' ／ 工作台 ' + pb + ' ／ 手写区 ' + ph
}

// ═══════════════════════════════════════════════════════════════════════════════
// 7. 备份 → 部署 → 逐文件核对
// ═══════════════════════════════════════════════════════════════════════════════
function snapshotDir(dir) {
    var snap = {}
    var names = fs.readdirSync(dir)
    for (var i = 0; i < names.length; i++) {
        var fp = path.join(dir, names[i])
        var stt
        try { stt = fs.statSync(fp) } catch (e) { continue }
        if (!stt.isFile()) continue
        var h = shaOrNull(fp)
        snap[names[i]] = { size: stt.size, mtimeMs: stt.mtimeMs, sha: h }
    }
    return snap
}

var deployTarget = CFG.DEPLOY_DIR + PRODUCT_NAME
var doDeploy = true, skipReason = ''
if (!exists(CFG.DEPLOY_DIR)) {
    abort('部署：找不到游戏实例目录', '目录不存在：' + CFG.DEPLOY_DIR,
        '实例路径变了／盘符没挂上。确认 `' + INSTANCE_NAME + '` 还在那儿再双击一次。')
}
if (deployedBefore === null) { skipReason = '实例里原先没有这一份' }
else if (deployedBefore === productSha) { doDeploy = false; skipReason = '实例里那份和产物逐字节相同' }

if (!doDeploy) {
    W(' ── 部署 ──────────────────────────────────────────────')
    W('   ⏭  内容没变，已跳过部署（不是错误）')
    W('      实例里那份的 sha256 = ' + short(deployedBefore, 32))
    W('      本次产物的   sha256 = ' + short(productSha, 32))
    W('')
} else {
    W(' ── 部署 ──────────────────────────────────────────────')
    var snapBefore = snapshotDir(CFG.DEPLOY_DIR)
    var backupPath = null
    if (deployedBefore !== null) {
        fs.mkdirSync(BACKUP_DIR, { recursive: true })
        backupPath = BACKUP_DIR + PRODUCT_NAME + '.' + ts() + '.bak'
        try { fs.copyFileSync(deployTarget, backupPath) }
        catch (e) {
            abort('部署：先备份实例里那份，但备份失败了',
                (e && e.code ? e.code + ' ' : '') + (e && e.message) + '\r\n  源：' + deployTarget + '\r\n  目标：' + backupPath,
                '实例里的文件被占着（游戏开着？）或磁盘满了。关掉游戏再双击一次。')
        }
        var bsha = shaOrNull(backupPath)
        if (bsha !== deployedBefore) abort('部署：备份校验不过', '备份出来的 ' + short(bsha, 32) + ' ≠ 原来的 ' + short(deployedBefore, 32), '备份不可信就不能覆盖，已停在原地。')
        W('   ① 已备份实例里那份 → ' + backupPath.replace(CFG.WS, ''))
    } else {
        W('   ① 实例里原先没有这一份，不需要备份' + (skipReason ? '（' + skipReason + '）' : ''))
    }

    // 🔴 不用 -Force：先在 node 里删掉再复制。目标被锁住 ⇒ unlink/copy 会直接抛
    //    EBUSY/EPERM（而不是被静静地忽略），我们据此报错停下。
    try { if (fs.existsSync(deployTarget)) fs.unlinkSync(deployTarget) }
    catch (e) {
        abort('部署：删掉实例里旧的那份失败',
            (e && e.code ? e.code + ' ' : '') + (e && e.message) + '\r\n  路径：' + deployTarget,
            '这个文件被别的程序占着（多半是 Minecraft 还开着）。关掉游戏再双击一次；'
            + '实在关不掉就把游戏关了重开，不会有别的后果（备份已做好）。')
    }
    try { fs.copyFileSync(PRODUCT, deployTarget) }
    catch (e) {
        abort('部署：复制产物到实例里失败',
            (e && e.code ? e.code + ' ' : '') + (e && e.message) + '\r\n  源：' + PRODUCT + '\r\n  目标：' + deployTarget,
            '磁盘满／权限／被占用。看上面的错误码：ENOSPC=磁盘满，EPERM/EACCES=权限或被占。')
    }
    W('   ② 已把产物复制进实例')
    W('')

    // ── 部署后验证 ──────────────────────────────────────────────────────────
    W(' ── 部署后验证 ────────────────────────────────────────')
    var nowSha = shaOrNull(deployTarget)
    if (nowSha !== productSha) {
        abort('部署后验证：哈希对不上',
            '实例里那份 sha256 = ' + short(nowSha, 32) + '\r\n产物          sha256 = ' + short(productSha, 32),
            '复制没落全（磁盘满／被杀）。实例现在可能处于半新半旧状态 ⇒ 用刚做的备份还原，再重试。')
    }
    W('   ✅ 实例里那份的 sha256 == 产物 sha256（' + short(productSha, 32) + '）')
    var snapAfter = snapshotDir(CFG.DEPLOY_DIR)
    var changed = [], same = 0, added = []
    var keysB = Object.keys(snapBefore)
    for (var q = 0; q < keysB.length; q++) {
        var nm = keysB[q]
        if (!snapAfter[nm]) { changed.push(nm + '(不见了)'); continue }
        var A = snapBefore[nm], B = snapAfter[nm]
        if (nm === PRODUCT_NAME) {
            if (A.sha !== B.sha || A.size !== B.size) changed.push(nm + '(本次就是要换掉它)')
            else changed.push(nm + '(内容其实一样，只是被重写了一次)')
        } else if (A.sha === B.sha && A.size === B.size && A.mtimeMs === B.mtimeMs) { same++ }
        else changed.push(nm + '(⚠️ 不该变的却变了)')
    }
    var keysA = Object.keys(snapAfter)
    for (var q2 = 0; q2 < keysA.length; q2++) if (!snapBefore[keysA[q2]]) added.push(keysA[q2] + '(⚠️ 新冒出来的)')
    var bad = []
    for (var ci = 0; ci < changed.length; ci++) if (changed[ci].indexOf('⚠️') >= 0) bad.push(changed[ci])
    for (var ai = 0; ai < added.length; ai++) bad.push(added[ai])
    if (bad.length) {
        abort('部署后验证：同目录里有【不该动】的文件动了',
            '不该动却动了：' + bad.join('、') + '\r\n本次应当只涉及：' + PRODUCT_NAME,
            '除了目标文件，别的 kubejs\\server_scripts 文件不该被碰。这多半是别的程序（游戏/别的脚本）同时写的，'
            + '不是本工具干的；请人工看一眼这些文件。')
    }
    W('   ✅ 同目录其余 ' + same + ' 个文件（size + 修改时间 + sha256 逐位）全都没变')
    W('   ✅ 本次只动了 1 个文件：' + PRODUCT_NAME)
    W('')
}

// ═══════════════════════════════════════════════════════════════════════════════
// 8. 中文总结（用户看的就是这一段）
// ═══════════════════════════════════════════════════════════════════════════════
var declared = PROV.readDeclared(PRODUCT)
var srcSame = (declared.sha === PF.sha)

W('════════════════════════════════════════════════════════════')
W(' ✅ 同步完成')
W('════════════════════════════════════════════════════════════')
W('  源：    ' + CFG.PF)
W('          ' + (ranCount === 0 ? '没变（' + localTime(PF.mtime) + ' 改的，跟上次同步时一模一样）'
    : '已改（' + localTime(PF.mtime) + '）'))
W('  跑了几步：3 步里跑了 ' + ranCount + ' 步'
    + (ranCount === 0 ? '（全跳过）' : '：' + ranNames.map(function (s) { return s.replace(/^[①②③]\s*/, '') }).join('、')))
if (skippedNames.length) W('            跳过：' + skippedNames.map(function (s) { return s.replace(/^[①②③]\s*/, '') }).join('、'))
W('  配方：  ' + NUMTXT() + '（' + (NUM.how || '读不到') + '）')
W('  产物：  ' + short(productSha, 32) + '（' + (productShaBefore === null ? '本次新生成' : (productShaBefore === productSha ? '跟生成前一样，没变' : '变了，已刷新')) + '）')
W('          ' + (srcSame ? '过期防线：产物声明的源头 == 当前 PF.txt ✅' : '🔴 过期防线：产物声明的源头 ≠ 当前 PF.txt（' + short(declared.sha, 16) + ' vs ' + short(PF.sha, 16) + '）'))
if (doDeploy) W('  已装到：' + INSTANCE_NAME + '（' + PRODUCT_NAME + '）')
else W('  已装到：' + INSTANCE_NAME + ' 里那份本来就是这一份，没动它')
W('')
W('  🔔 进游戏后敲  /kubejs reload server_scripts  或重启游戏才生效')
W('')
