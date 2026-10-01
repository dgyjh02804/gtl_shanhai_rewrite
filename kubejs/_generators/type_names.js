// ═══════════════════════════════════════════════════════════════════════════════
// type_names.js —— 「纸上写的中文配方类型名」⇒ `gtceu:<id>` 的【自动反查表】
// ═══════════════════════════════════════════════════════════════════════════════
// 用户 2026-09-30 原话（逐字）：「就用语言文件里面那样做吧，正好可以检查我写错字」
//   ⇒ 两件事一起做：① 新类型名不用再找人手动加键 ② 写错的时候要【告诉他】
//
// 做法（三条纪律）：
//   ① **中文名来自【现读 jar】的 lang**，不读任何转抄件 —— 因为 GTCEu 命名空间 `gtceu.*` 的键
//      分散在【多个 jar】里（实测：gtceu 本体 1432 键 ／ gtlcore 的 assets/gtceu/lang 480 键 ／
//      gtladditions 的 assets/gtceu/lang 120 键 ／ shanhai 自己的 assets/shanhai/lang 41 键）。
//      只读其中一个 jar 会漏掉另外三个 —— 那正是"抄一份"必犯的错。
//   ② **反查必须限制在「已注册类型 id 集合」上**，绝不用全量 `gtceu.*`：
//      全量里 1838 个中文名有 115 个撞多键（「销毁模式」「物品」「输入」「输出」…都是 GUI/提示串）。
//      受限之后（本工程实测）**0 个撞名** ⇒ 反查结果是唯一的。
//   ③ **构建一次、全进程复用**（一张内存表 + 可选的磁盘缓存）。绝不"每行配方重扫 jar"。
//
// 本模块【不写任何产物】、不改任何游戏文件；只读 jar 与游戏导出目录。纯 node，无外部依赖
// （自带最小 ZIP 读取器 —— 本工程没有 node_modules，也故意不引入依赖）。
'use strict'
var fs = require('fs')
var path = require('path')
var zlib = require('zlib')
var crypto = require('crypto')

// ═══════════════════════════════════════════════════════════════════════════════
// 1. 最小 ZIP 读取器（只做一件事：把指定名字的条目解出来）
// ═══════════════════════════════════════════════════════════════════════════════
// 为什么自己写：node 没有内置 zip；本工程没有 package.json / node_modules；
// 引第三方库会让"用户双击 bat"多一个装依赖的前提。zip 的中央目录格式是公开且稳定的。
// 支持：store(0) 与 deflate(8)；不支持 zip64（本机的 mod jar 都远小于 4 GB，遇到就明确报错而不是猜）。
var EOCD_SIG = 0x06054b50, CEN_SIG = 0x02014b50, LOC_SIG = 0x04034b50

/** 打开一个 zip，返回 { list():[names], read(name):Buffer, close() }。 */
function openZip(file) {
    var fd = fs.openSync(file, 'r')
    var size = fs.fstatSync(fd).size
    // 中央目录尾部扫描：EOCD 固定 22 字节 + 最多 65535 字节注释
    var tailLen = Math.min(size, 22 + 65535)
    var tail = Buffer.alloc(tailLen)
    fs.readSync(fd, tail, 0, tailLen, size - tailLen)
    var eocd = -1
    for (var i = tailLen - 22; i >= 0; i--) { if (tail.readUInt32LE(i) === EOCD_SIG) { eocd = i; break } }
    if (eocd < 0) { fs.closeSync(fd); throw new Error('不是 zip（找不到中央目录结尾记录 EOCD）：' + file) }
    var count = tail.readUInt16LE(eocd + 10)
    var cdSize = tail.readUInt32LE(eocd + 12)
    var cdOff = tail.readUInt32LE(eocd + 16)
    if (cdOff === 0xFFFFFFFF || cdSize === 0xFFFFFFFF) { fs.closeSync(fd); throw new Error('这个 zip 用了 zip64（暂不支持）：' + file) }
    var cd = Buffer.alloc(cdSize)
    fs.readSync(fd, cd, 0, cdSize, cdOff)
    var entries = {}, order = []
    var p = 0
    for (var n = 0; n < count && p + 46 <= cdSize; n++) {
        if (cd.readUInt32LE(p) !== CEN_SIG) break
        var method = cd.readUInt16LE(p + 10)
        var compSize = cd.readUInt32LE(p + 20)
        var nameLen = cd.readUInt16LE(p + 28)
        var extraLen = cd.readUInt16LE(p + 30)
        var commentLen = cd.readUInt16LE(p + 32)
        var locOff = cd.readUInt32LE(p + 42)
        var name = cd.toString('utf8', p + 46, p + 46 + nameLen)
        entries[name] = { method: method, compSize: compSize, locOff: locOff }
        order.push(name)
        p += 46 + nameLen + extraLen + commentLen
    }
    return {
        file: file,
        list: function () { return order },
        has: function (nm) { return !!entries[nm] },
        size: function (nm) { return entries[nm] ? entries[nm].compSize : -1 },
        read: function (nm) {
            var e = entries[nm]
            if (!e) throw new Error('zip 里没有这个条目：' + nm + ' @ ' + file)
            var lh = Buffer.alloc(30)
            fs.readSync(fd, lh, 0, 30, e.locOff)
            if (lh.readUInt32LE(0) !== LOC_SIG) throw new Error('本地文件头签名不对：' + nm + ' @ ' + file)
            var lNameLen = lh.readUInt16LE(26), lExtraLen = lh.readUInt16LE(28)
            var dataOff = e.locOff + 30 + lNameLen + lExtraLen
            var raw = Buffer.alloc(e.compSize)
            fs.readSync(fd, raw, 0, e.compSize, dataOff)
            if (e.method === 0) return raw
            if (e.method === 8) return zlib.inflateRawSync(raw)
            throw new Error('不支持的压缩方式 ' + e.method + '：' + nm + ' @ ' + file)
        },
        close: function () { try { fs.closeSync(fd) } catch (e) { } }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. 从整个 mods 目录里现读 `gtceu.*` 的中文名（并记下【每个键来自哪个 jar】）
// ═══════════════════════════════════════════════════════════════════════════════
// ⚠️ 命名空间不是 `gtceu` 的 lang 文件也要读：shanhai 自己的中文名就放在
//    `assets/shanhai/lang/zh_cn.json` 里，键名仍然是 `gtceu.<id>`。所以判据是【键名】不是【文件名】。
var LANG_ENTRY_RE = /^assets\/([^/]+)\/lang\/zh_cn\.json$/
var TYPE_KEY_RE = /^gtceu\.([a-z0-9_]{1,64})$/

/**
 * 扫 mods 目录里每个 jar 的 `assets/<ns>/lang/zh_cn.json`，抽出 `gtceu.<id>` 键。
 * @returns {{byId:Object, sources:Array, jarCount:number, entryCount:number, skipped:Array}}
 */
function readTypeLang(modsDir) {
    var byId = {}          // id -> [{name, jar, ns, entry}]
    var sources = []       // 逐（jar, ns）的贡献记录 —— 报告要"逐条点名"
    var skipped = []       // 读不了的 jar（如实报出来，不静默）
    var jarCount = 0, entryCount = 0
    var jars = []
    try { jars = fs.readdirSync(modsDir).filter(function (f) { return /\.jar$/i.test(f) }).sort() }
    catch (e) { return { byId: byId, sources: sources, jarCount: 0, entryCount: 0, skipped: [{ jar: modsDir, why: '读不了 mods 目录：' + e.message }], modsDirMissing: true } }
    for (var j = 0; j < jars.length; j++) {
        var zp = path.join(modsDir, jars[j])
        var z
        try { z = openZip(zp) } catch (e) { skipped.push({ jar: jars[j], why: e.message }); continue }
        jarCount++
        var names = z.list()
        for (var i = 0; i < names.length; i++) {
            var m = LANG_ENTRY_RE.exec(names[i])
            if (!m) continue
            entryCount++
            var obj
            try {
                // ⚠️ 先去 BOM 再 parse。实测 gtladditions 的 avaritia / sgjourney 两份 zh_cn 带 BOM
                //    ⇒ `JSON.parse` 直接抛 `Unexpected token '﻿'`（改动前那两份被跳过）。
                //    实测它们里面 **0 个** `gtceu.*` 键 ⇒ 跳过它们不影响反查表；但 BOM 是个真洞，
                //    将来某份 lang 带 BOM 且含 gtceu 键时会被静默漏掉（本模块只是把它放进 skipped 报出来）。
                var raw = z.read(names[i]).toString('utf8')
                if (raw.charCodeAt(0) === 0xFEFF) raw = raw.slice(1)
                obj = JSON.parse(raw)
            } catch (e) { skipped.push({ jar: jars[j], entry: names[i], why: 'JSON 解析失败：' + e.message }); continue }
            var gtceuKeys = 0
            var ks = Object.keys(obj)
            for (var k = 0; k < ks.length; k++) {
                var mm = TYPE_KEY_RE.exec(ks[k])
                if (!mm) continue
                gtceuKeys++
                var id = mm[1]
                if (!byId[id]) byId[id] = []
                byId[id].push({ name: obj[ks[k]], jar: jars[j], ns: m[1], entry: names[i] })
            }
            if (gtceuKeys) sources.push({ jar: jars[j], ns: m[1], entry: names[i], keys: ks.length, gtceuKeys: gtceuKeys })
        }
        z.close()
    }
    return { byId: byId, sources: sources, jarCount: jarCount, entryCount: entryCount, skipped: skipped }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 3. 真源③：游戏自己导出的配方类型目录（现读）
// ═══════════════════════════════════════════════════════════════════════════════
// 为什么需要它：真源②（gtceu 本体的 javap 转储）只有 gtceu 自己注册的类型；
// 而这个整合包把大量【别的 mod 注册的】配方类型也放在 `gtceu:` 命名空间下
// （实测：`local\kubejs\export\recipes\gtceu\` 有 133 个类型目录，其中 aggregation_device /
//  photon_matrix_etch / circuit_assembly_line … 都不在真源②里）。
// ⇒ 少了它，用户写「聚合装置」这种【正确】名字会被误判成笔误。
// 目录名 = 配方类型的路径（实测：`export\recipes\shanhai\worldline_cutting\…json` 里
// `"type": "gtceu:worldline_cutting"`）。目录名可能多层（如 `fastblasting\gtceu\<type>`），
// 所以【每一层的目录名都收】，最后再被"有 lang 中文名"这一条滤掉junk。
function scanExportTypeDirs(exportRecipesDir, maxDepth) {
    var out = {}, dirs = 0
    var depth = maxDepth === undefined ? 3 : maxDepth
    function walk(dir, d) {
        if (d > depth) return
        var ents
        try { ents = fs.readdirSync(dir, { withFileTypes: true }) } catch (e) { return }
        for (var i = 0; i < ents.length; i++) {
            if (!ents[i].isDirectory()) continue
            var nm = ents[i].name
            if (/^[a-z0-9_]{1,64}$/.test(nm)) out[nm] = 1
            dirs++
            walk(path.join(dir, nm), d + 1)
        }
    }
    walk(exportRecipesDir, 1)
    return { ids: Object.keys(out), dirsScanned: dirs }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 4. 编辑距离（**按中文字符算**，不按字节）—— 用于"你是不是想说 XXX"
// ═══════════════════════════════════════════════════════════════════════════════
// ⚠️ 必须用 Array.from（码点序列）而不是 s.length / s.charAt：
//    UTF-16 下中文是 1 个 code unit、日文补充面字符是 2 个 ⇒ 按 code unit 算距离，
//    "一个字写错"会被算成 2 的代价，候选排序会歪。Array.from 才是"按字算"。
function toChars(s) { return Array.from(String(s)) }
function levenshtein(a, b) {
    var A = toChars(a), B = toChars(b)
    var n = A.length, m = B.length
    if (n === 0) return m
    if (m === 0) return n
    var prev = new Array(m + 1), cur = new Array(m + 1)
    for (var j = 0; j <= m; j++) prev[j] = j
    for (var i = 1; i <= n; i++) {
        cur[0] = i
        for (var k = 1; k <= m; k++) {
            var cost = A[i - 1] === B[k - 1] ? 0 : 1
            var v = prev[k - 1] + cost
            if (prev[k] + 1 < v) v = prev[k] + 1
            if (cur[k - 1] + 1 < v) v = cur[k - 1] + 1
            cur[k] = v
        }
        var t = prev; prev = cur; cur = t
    }
    return prev[m]
}

// ═══════════════════════════════════════════════════════════════════════════════
// 5. 建表（★ 本模块的主入口）
// ═══════════════════════════════════════════════════════════════════════════════
/**
 * @param {Object} o
 *   o.idSets    = { '①ShanhaiRecipeTypes.java': ['photon_separation', …], '②GTRecipeTypes.txt(javap)': [...] }
 *   o.modsDir   = mods 目录（现读 jar）
 *   o.exportRecipesDir = 游戏导出配方目录（真源③，可选；读不到只记一跳过的说明）
 *   o.cacheFile = 可选；磁盘缓存路径（签名不符就重建）
 *   o.log       = 可选日志函数
 * @returns {{table:Object, nameById:Object, collisions:Array, meta:Object}}
 */
function buildIndex(o) {
    var t0 = Date.now()
    var log = o.log || function () { }
    var modsDir = o.modsDir
    // ── 缓存签名：mods 目录下每个 jar 的名字 + 大小 + mtime ──────────────────────
    var sigRows = []
    try {
        fs.readdirSync(modsDir).filter(function (f) { return /\.jar$/i.test(f) }).sort().forEach(function (f) {
            var st = fs.statSync(path.join(modsDir, f))
            sigRows.push(f + '|' + st.size + '|' + Math.round(st.mtimeMs))
        })
    } catch (e) { }
    var sig = crypto.createHash('sha256').update(sigRows.join('\n')).digest('hex').toUpperCase()

    var lang = null, cacheUsed = false
    if (o.cacheFile) {
        try {
            var c = JSON.parse(fs.readFileSync(o.cacheFile, 'utf8'))
            if (c && c.sig === sig && c.byId) { lang = c; cacheUsed = true }
        } catch (e) { }
    }
    if (!lang) {
        lang = readTypeLang(modsDir)
        lang.sig = sig
        if (o.cacheFile) {
            try {
                fs.mkdirSync(path.dirname(o.cacheFile), { recursive: true })
                fs.writeFileSync(o.cacheFile, JSON.stringify(lang), 'utf8')
            } catch (e) { log('⚠️ lang 缓存写不进去（' + e.message + '）⇒ 每次都会重扫，不影响正确性') }
        }
    }
    if (lang.modsDirMissing) log('⚠️ ' + modsDir + ' 读不了 ⇒ 反查表会是空的（这一次生成会因此拒绝写产物）')

    // ── id 集合 = ① ∪ ② ∪ ③ ──────────────────────────────────────────────────
    var ids = {}, idWhy = {}
    var idSets = o.idSets || {}
    Object.keys(idSets).forEach(function (k) {
        idSets[k].forEach(function (id) { if (!ids[id]) { ids[id] = 1; idWhy[id] = k } })
    })
    var exp = { ids: [], dirsScanned: 0, how: 'NONE' }
    if (o.exportRecipesDir) {
        exp = scanExportTypeDirs(o.exportRecipesDir)
        exp.how = exp.ids.length ? 'OK' : 'EMPTY'
        exp.ids.forEach(function (id) { if (!ids[id]) { ids[id] = 1; idWhy[id] = '③游戏导出 recipes 目录' } })
    }
    // ⚠️ 真源③ 只收"确实有中文名"的目录名（滤掉 test / 命名空间名之类的 junk）：
    //    没有 lang 名字的 id 本来就进不了反查表，所以这一步只是把报告数字变干净。
    var expWithName = exp.ids.filter(function (id) { return !!lang.byId[id] })

    // ── 受限反查表：中文名 → id（只在 id 集合上）────────────────────────────────
    var table = {}, collisions = [], nameById = {}, srcOfName = {}
    var idWithName = 0
    Object.keys(ids).sort().forEach(function (id) {
        var arr = lang.byId[id]
        if (!arr || !arr.length) return
        idWithName++
        // 同一个 id 在多个 jar 里都有键 ⇒ 取"最后一个 jar"的值会随扫描顺序变 ⇒ 必须显式判：
        // 值相同就无所谓；值不同就**如实报冲突**（这是"抄一份"绝对发现不了的坑）。
        var vals = {}, vlist = []
        arr.forEach(function (e) { if (!vals[e.name]) { vals[e.name] = 1; vlist.push(e) } })
        vlist.forEach(function (e) {
            if (!table[e.name]) { table[e.name] = []; srcOfName[e.name] = e }
            if (table[e.name].indexOf(id) < 0) table[e.name].push(id)
        })
        nameById[id] = vlist.map(function (e) { return e.name })
    })
    var tableList = {}
    Object.keys(table).forEach(function (nm) {
        if (table[nm].length === 1) tableList[nm] = table[nm][0]
        else collisions.push({ name: nm, ids: table[nm] })
    })

    var names = Object.keys(tableList).sort()
    var meta = {
        seconds: Math.round((Date.now() - t0) / 1000 * 1000) / 1000,
        cacheUsed: cacheUsed,
        modsDir: modsDir,
        jarCount: lang.jarCount,
        langEntryCount: lang.entryCount,
        skipped: lang.skipped,
        sources: lang.sources,
        idSetCounts: (function () { var r = {}; Object.keys(idSets).forEach(function (k) { r[k] = idSets[k].length }); return r })(),
        exportDirs: { scanned: exp.dirsScanned, ids: exp.ids.length, withLangName: expWithName.length, how: exp.how, dir: o.exportRecipesDir || null },
        idUnion: Object.keys(ids).length,
        idWithLangName: idWithName,
        idsWithoutLangName: Object.keys(ids).filter(function (id) { return !lang.byId[id] }).sort(),
        tableSize: names.length,
        collisions: collisions.length,
        tableNames: names
    }
    return {
        table: tableList,
        nameById: nameById,
        srcOfName: srcOfName,
        collisions: collisions,
        meta: meta,
        levenshtein: levenshtein,
        /** 最近的 k 个候选（都在反查表里 ⇒ 给出来的名字一定是对的）。 */
        topCandidates: function (name, k) {
            var want = toChars(name).length
            var scored = []
            for (var i = 0; i < names.length; i++) {
                var d = levenshtein(name, names[i])
                scored.push({ name: names[i], d: d, dl: Math.abs(toChars(names[i]).length - want) })
            }
            scored.sort(function (a, b) { return (a.d - b.d) || (a.dl - b.dl) || (a.name < b.name ? -1 : 1) })
            return scored.slice(0, k || 3)
        }
    }
}

module.exports = {
    openZip: openZip,
    readTypeLang: readTypeLang,
    scanExportTypeDirs: scanExportTypeDirs,
    buildIndex: buildIndex,
    levenshtein: levenshtein,
    toChars: toChars
}

// ---------------------------------------------------------------- 自证（正面 + 负面）
// 只有被当作主模块直接运行时才跑；被 require 时不做任何事。
// 用法：node type_names.js <modsDir> <exportRecipesDir>
if (require.main === module) {
    var md = process.argv[2], ed = process.argv[3]
    var r = buildIndex({ modsDir: md, exportRecipesDir: ed, idSets: {} })
    console.log('=== type_names.js 自检（先证它自己对，再信它的结论）===')
    console.log('jar 数 = ' + r.meta.jarCount + ' ／ lang 条目 = ' + r.meta.langEntryCount
        + ' ／ 有 gtceu.* 键的文件 = ' + r.meta.sources.length + ' ／ 耗时 ' + r.meta.seconds + ' 秒')
    console.log('id 并集 = ' + r.meta.idUnion + '（其中有中文名的 ' + r.meta.idWithLangName + '）')
    console.log('反查表 = ' + r.meta.tableSize + ' 个中文名 ／ 撞名 = ' + r.meta.collisions)
    // 正面：已知的键必须能读出来（这两个是人工从字节码/lang 独立核对过的）
    var pos = [['circuit_assembler', '电路组装机'], ['primitive_blast_furnace', '土高炉']]
    var fail = 0
    for (var i = 0; i < pos.length; i++) {
        var id = pos[i][0]
        var ok = false
        if (r.nameById[id]) for (var q = 0; q < r.nameById[id].length; q++) if (r.nameById[id][q] === pos[i][1]) ok = true
        if (!ok) fail++
        console.log('  ' + (ok ? '✅' : '❌') + ' 正面对照 gtceu.' + id + ' ⇒ 期望「' + pos[i][1] + '」 实测 ' + JSON.stringify(r.nameById[id] || null))
    }
    // 负面：GUI/提示串必须**不在**反查表里（否则说明把 junk 也收进来了）
    var neg = ['这个键不存在', '销毁模式', '物品', '无']
    for (var j = 0; j < neg.length; j++) {
        var okn = !r.table[neg[j]]
        console.log('  ' + (okn ? '✅' : '❌') + ' 负面对照 「' + neg[j] + '」 ⇒ 期望查不到　实测 ' + JSON.stringify(r.table[neg[j]] || null))
        if (!okn) fail++
    }
    console.log('正/负对照失败数 = ' + fail)
    process.exit(fail ? 1 : 0)
}
