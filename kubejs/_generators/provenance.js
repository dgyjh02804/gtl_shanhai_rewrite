// ═══════════════════════════════════════════════════════════════════════════════
// 中间产物「过期防线」—— 每个中间产物必须声明【自己对应的是哪一个 PF.txt】。
// ═══════════════════════════════════════════════════════════════════════════════
//
// 🔴 为什么有这个文件（2026-09-29 两次真实事故，不是我猜的）：
//   事故 1：写 FTB 任务时从 `recipe-convert\样板清单.md` 抄数字 —— 那份是 2026-09-26 的旧版
//           （对应旧 PF.txt，38 条）⇒ **9 处数字全错**，被自己的对照脚本抓出。
//           病根：中间产物【不声明自己对应当前源头】，谁引用谁踩雷。
//   事故 2：`gen_kjs.js` 的 CAP 表缺 31 个键 ⇒ 缺键时静默回落 `[99,99,99,99]`
//           ⇒ 槽位自检对那 19 条配方【假装在工作】。
//
// 本模块提供三件事：
//   ① record(artifact)  —— 产物写完后调用：算它自己的 sha256，连同源头(PF.txt)的
//                          path/sha256/bytes/mtime 一起登记进 `recipe-convert\_provenance.json`。
//   ② embed()           —— 给「本身就是对象」的 JSON 产物（如 parsed.json）加 `_provenance` 字段，
//                          让它**自己一个人也能证明**自己对应哪个 PF.txt（不依赖清单文件）。
//   ③ check(artifact)   —— 【引用方】调用：现算磁盘上 PF.txt 的 sha256，与产物里声明的比对，
//                          不一致就打印醒目的 ❌ 并（默认）直接抛错，拒绝拿旧产物往下做。
//
// ⚠️ 为什么 rows.json / specs.json 用「清单文件」而不是「加字段」：
//   这两个文件在磁盘上是**裸数组**（`[...]`），实测有 **12 处以上**消费者按裸数组解析
//   （`kubejs\_generators\check_ids.js`、`gen_kjs.js`、`temp\ftbq-ai\{check_rows,digest,names,names2,rows_full,validate}.js`、
//   `temp\{compare_module,compare_ruling2,dump_row,scan_rows}.js`）。
//   把它们改成 `{_provenance, rows}` 会**同时打断所有消费者**（其中 `temp\ftbq-ai\` 正被另一个
//   子代理使用中）⇒ 那正是本项目要防的"改了格式把别人搞崩"事故形态。
//   ⇒ 所以：**保持裸数组形状不变**，provenance 走同目录的 `_provenance.json`，
//     并且登记里同时记下产物【自己的 sha256】——这样"产物被单独换掉/改过"也能被发现
//     （self sha256 对不上 ⇒ 没有可用的登记 ⇒ ❌ 而不是 ✅）。
'use strict'
var fs = require('fs')
var path = require('path')
var crypto = require('crypto')

// ═══════════════════════════════════════════════════════════════════════════════
// 🔴 路径来源纪律（上传前清理）：本仓库里【不写任何机器绝对路径】。
//    · 仓库【内】的路径 ⇒ 按【脚本自身位置】(__dirname) 推 —— 不依赖"从哪个目录运行"，
//      所以【故意不用 process.cwd()】；
//    · 仓库【外】的路径（PF.txt）⇒ 从环境变量读；缺了就【响亮抛错并退出】。
//    ⚠️ 本模块是 4 个生成器（parse_pf / gen_manifest / gen_kjs / check_ids）的公共依赖
//       ⇒ 它抛错 = 它们全部拒绝开跑，这是【故意】的（绝不静默退化）。
// ═══════════════════════════════════════════════════════════════════════════════
var WS = path.join(__dirname, '..', '..') + path.sep          // kubejs\_generators → 仓库根
function envPath(name, what, example) {
    var v = process.env[name]
    if (v === undefined || String(v).trim() === '') {
        throw new Error('🔴 缺少环境变量 ' + name + '（' + what + '）\n'
            + '   ⇒ 请先设置它，例如（PowerShell）：$env:' + name + " = '" + example + "'\n"
            + '   ⇒ 本模块【拒绝】在缺少它的前提下被加载：那会拿错路径、静默产出错产物。')
    }
    return String(v).trim()
}
var RC = WS + 'recipe-convert\\'
/** 唯一源头：用户从游戏里导出的 PF.txt（在工程目录之外，是"活文件"，随时可能被覆盖）。 */
var PF_SRC = envPath('SH_PF_SRC', 'PF.txt（AE2 样板导出的 NBT 文本）的绝对路径',
    'D:\\path\\to\\PF.txt')
/** 清单文件：产物 ⇒ 源头 的对应关系。 */
var MANIFEST = RC + '_provenance.json'
/** md/文本产物里可机器解析的声明行（HTML 注释，渲染后不可见）。 */
var MARK_BEGIN = '<!-- [SHANHAI-SRC-PROVENANCE] '
var MARK_END = ' -->'
var MARK_RE = /<!--\s*\[SHANHAI-SRC-PROVENANCE\]\s*(.*?)\s*-->/

function sha256File(p) { return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex').toUpperCase() }
function nowIso() { return new Date().toISOString() }

/** PF.txt 的现状（现算，绝不缓存）。 */
function srcNow(srcPath) {
    var p = srcPath || PF_SRC
    var st = fs.statSync(p)
    return { srcPath: p, srcSha256: sha256File(p), srcBytes: st.size, srcMtime: st.mtime.toISOString() }
}

/** md/文本产物的机器可解析声明行。 */
function markLine(src) {
    return MARK_BEGIN + 'src=' + src.srcPath + ' sha256=' + src.srcSha256
        + ' bytes=' + src.srcBytes + ' mtime=' + src.srcMtime + ' generated=' + nowIso() + MARK_END
}

// ---------------------------------------------------------------- manifest
function readManifest() {
    try {
        var j = JSON.parse(fs.readFileSync(MANIFEST, 'utf8'))
        if (j && typeof j === 'object' && j.artifacts) return j
    } catch (e) { }
    return {
        _note: '中间产物 ⇒ 源头(PF.txt) 的对应关系。由 kubejs\\_generators\\provenance.js 维护，请勿手改。',
        _pfSrc: PF_SRC,
        artifacts: {}
    }
}
function writeManifest(m) {
    var tmp = MANIFEST + '.tmp'
    fs.writeFileSync(tmp, JSON.stringify(m, null, 1), 'utf8')
    fs.renameSync(tmp, MANIFEST)          // 原子替换：不会留下半个清单
}

/**
 * 产物写完后登记：记下它【自己的】sha256 ＋ 它对应的源头 PF.txt 的 path/sha256/bytes/mtime。
 * @param {string} artifactPath 产物绝对路径
 * @param {string} producer     生产者标识（脚本名）
 */
function record(artifactPath, producer, srcPath) {
    var src = srcNow(srcPath)
    var st = fs.statSync(artifactPath)
    var m = readManifest()
    m.artifacts[path.basename(artifactPath)] = {
        artifact: artifactPath,
        artifactBytes: st.size,
        artifactSha256: sha256File(artifactPath),
        srcPath: src.srcPath,
        srcSha256: src.srcSha256,
        srcBytes: src.srcBytes,
        srcMtime: src.srcMtime,
        generatedAt: nowIso(),
        producer: producer
    }
    writeManifest(m)
    return m.artifacts[path.basename(artifactPath)]
}

/** 给「对象型 JSON 产物」加 _provenance 字段（它自己一个人也能自证）。 */
function embed(obj, producer, srcPath) {
    var src = srcNow(srcPath)
    var prov = {
        srcPath: src.srcPath,
        srcSha256: src.srcSha256,
        srcBytes: src.srcBytes,
        srcMtime: src.srcMtime,
        generatedAt: nowIso(),
        producer: producer
    }
    // 放在最前面：JSON.stringify 保持插入顺序 ⇒ _provenance 落在文件头
    var out = { _provenance: prov }
    for (var k in obj) if (Object.prototype.hasOwnProperty.call(obj, k)) out[k] = obj[k]
    return out
}

/** 从产物里读出它声明的源头 sha256（三种载体：内嵌 _provenance / md 标记行 / 清单登记）。 */
function readDeclared(artifactPath) {
    var buf
    try { buf = fs.readFileSync(artifactPath) } catch (e) { return { how: 'MISSING', sha: null } }
    var text = buf.toString('utf8')

    // ① 内嵌 _provenance（对象型 JSON）
    if (/^\s*\{/.test(text) && /"_provenance"/.test(text)) {
        try {
            var j = JSON.parse(text)
            if (j && j._provenance && j._provenance.srcSha256) {
                return { how: 'INLINE', sha: j._provenance.srcSha256, prov: j._provenance, self: null }
            }
        } catch (e) { }
    }
    // ② md/文本里的机器可解析标记行
    var mm = MARK_RE.exec(text)
    if (mm) {
        var ms = /sha256=([0-9A-Fa-f]{64})/.exec(mm[1])
        if (ms) {
            return {
                how: 'MARKER', sha: ms[1].toUpperCase(), self: null,
                prov: { srcPath: (/src=(.*?)\s+sha256=/.exec(mm[1]) || [])[1], srcSha256: ms[1].toUpperCase() }
            }
        }
    }
    // ③ 清单登记 —— 但必须【产物自己的 sha256 对得上】才算数（否则是"被换过"）
    var self = sha256File(artifactPath)
    var m = readManifest()
    var e = m.artifacts[path.basename(artifactPath)]
    if (e && e.artifactSha256 === self) return { how: 'MANIFEST', sha: e.srcSha256, prov: e, self: self }
    if (e) return { how: 'MANIFEST-STALE', sha: e.srcSha256, prov: e, self: self, entrySha: e.artifactSha256 }
    // ④ 老式人类可读行兜底：`SHA256 \`XXXX…\`` / `SHA256 = XXXX`
    var h = /SHA256\s*[`=]\s*([0-9A-Fa-f]{64})/.exec(text)
    if (h) return { how: 'TEXT', sha: h[1].toUpperCase(), self: null }
    return { how: 'NONE', sha: null, self: null }
}

/**
 * 【引用方】过期自检：拿产物声明的源头 sha256 比对磁盘上 PF.txt 的现值。
 * 不一致 ⇒ 打印醒目 ❌（默认还会抛错，拒绝拿旧产物往下做）。
 * @returns {{ok:boolean, declared:string|null, live:string, how:string, msg:string}}
 */
function check(artifactPath, consumerName, opts) {
    opts = opts || {}
    var tag = '[SRC-FRESH] ' + (consumerName || '?') + ' ← ' + path.basename(artifactPath)
    var live
    try { live = srcNow(opts.srcPath) } catch (e) {
        var m0 = '❌ ' + tag + '：**读不到源头** ' + (opts.srcPath || PF_SRC) + '（' + e.message + '）'
        console.log(m0)
        if (opts.hard !== false) throw new Error(m0)
        return { ok: false, declared: null, live: null, how: 'SRC-MISSING', msg: m0 }
    }
    var d = readDeclared(artifactPath)

    if (d.sha === null) {
        var m1 = '❌ ' + tag + '：**这份中间产物没有声明自己对应的源头**（载体=NONE）'
            + ' ⇒ 无法判断它是不是旧的，**引用它会出错**。'
            + ' 修法：重跑产出它的那一步（parse_pf.js / gen_manifest.js / gen_kjs.js / check_ids.js）。'
        console.log(m1)
        if (opts.hard !== false) throw new Error(m1)
        return { ok: false, declared: null, live: live.srcSha256, how: 'NONE', msg: m1 }
    }
    if (d.how === 'MANIFEST-STALE') {
        var m2 = '❌ ' + tag + '：**这份产物在登记之后被改写过**（登记里的 artifactSha256=' + d.entrySha
            + '，磁盘现值=' + d.self + '）⇒ 登记已失效，它到底是哪一版无法判定，**引用它会出错**。'
        console.log(m2)
        if (opts.hard !== false) throw new Error(m2)
        return { ok: false, declared: d.sha, live: live.srcSha256, how: d.how, msg: m2 }
    }
    var ok = (d.sha === live.srcSha256)
    var line = (ok ? '✅ ' : '❌ ') + tag + '：产物声明的源头 sha256 = ' + d.sha
        + '（载体=' + d.how + '）／磁盘上 ' + path.basename(live.srcPath) + ' 现值 = ' + live.srcSha256
        + ' ⇒ ' + (ok ? '一致，可以用' : '**不一致！这份产物对应的是【旧的】'
            + path.basename(live.srcPath) + '，引用它会出错**')
    console.log(line)
    if (!ok && opts.hard !== false) throw new Error(line)
    return { ok: ok, declared: d.sha, live: live.srcSha256, how: d.how, msg: line }
}

module.exports = {
    PF_SRC: PF_SRC, WS: WS, RC: RC, MANIFEST: MANIFEST,
    sha256File: sha256File, srcNow: srcNow, markLine: markLine,
    readManifest: readManifest, writeManifest: writeManifest,
    record: record, embed: embed, readDeclared: readDeclared, check: check,
    MARK_RE: MARK_RE
}

// ---------------------------------------------------------------- 自证（正面 + 负面）
// 只有被当作主模块直接运行时才跑；被 require 时不做任何事。
if (require.main === module) {
    console.log('=== provenance.js 自检（先证它对，再信它）===')
    var a = process.argv[2]
    if (!a) { console.log('用法: node provenance.js <产物路径>'); process.exit(1) }
    console.log('PF.txt 现值 = ' + srcNow().srcSha256)
    var r = check(a, 'provenance-self-test', { hard: false })
    console.log('结果: ok=' + r.ok + ' how=' + r.how)
}
