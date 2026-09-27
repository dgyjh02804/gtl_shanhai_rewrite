// SNBT parser for PF.txt (Minecraft chest NBT dump).
// Goal: extract every ae2:processing_pattern with its in/out arrays.
// Discipline: raw occurrence count MUST equal parsed count (no silent drops).
'use strict'
var fs = require('fs')
var OUT = []
var _log = console.log
console.log = function () { var a = []; for (var q = 0; q < arguments.length; q++) a.push(arguments[q]); var line = a.join(' '); OUT.push(line); _log(line) }
process.on('exit', function () { fs.writeFileSync('C:\\Users\\david\\Desktop\\构建\\shanhai重构\\recipe-convert\\parsed.txt', OUT.join('\n'), 'utf8') })

var SRC = 'C:\\Users\\david\\Desktop\\PF.txt'
var text = fs.readFileSync(SRC, 'utf8')

// ---------------------------------------------------------------- tokenizer
function parse(s) {
    var i = 0
    var n = s.length
    function die(msg) { throw new Error(msg + ' at offset ' + i + ' :: ' + JSON.stringify(s.substring(Math.max(0, i - 60), i + 60))) }
    function ws() { while (i < n && (s[i] === ' ' || s[i] === '\n' || s[i] === '\r' || s[i] === '\t')) i++ }
    function readQuoted() {
        var q = s[i]; i++
        var out = ''
        while (i < n) {
            var c = s[i]
            if (c === '\\') {
                var e = s[i + 1]
                if (e === 'n') out += '\n'
                else if (e === 't') out += '\t'
                else if (e === 'r') out += '\r'
                else out += e
                i += 2
                continue
            }
            if (c === q) { i++; return out }
            out += c; i++
        }
        die('unterminated string')
    }
    // bare token: read until a delimiter at depth 0
    function readBare() {
        var st = i
        while (i < n) {
            var c = s[i]
            if (c === ',' || c === '}' || c === ']' || c === ':' || c === '{' || c === '[') break
            i++
        }
        return s.substring(st, i)
    }
    function numFromBare(tok) {
        // 1b / 1L / 19981.0d / 3L / 19981.0f / plain
        var m = /^([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)([bBsSlLfFdD])?$/.exec(tok.trim())
        if (!m) return null
        return parseFloat(m[1])
    }
    function value() {
        ws()
        var c = s[i]
        if (c === '{') return compound()
        if (c === '[') return list()
        if (c === '"' || c === '\'') return readQuoted()
        var tok = readBare()
        var num = numFromBare(tok)
        if (num !== null) return num
        if (tok === 'true') return true
        if (tok === 'false') return false
        return { __bare: tok }
    }
    function list() {
        i++ // [
        // typed array prefix: L; I; B; S; F; D;
        var typed = false
        var save = i
        if (i + 1 < n && s[i + 1] === ';' && 'LIBFD'.indexOf(s[i]) >= 0) { typed = true; i += 2 } else { i = save }
        var arr = []
        ws()
        if (s[i] === ']') { i++; return arr }
        for (;;) {
            ws()
            if (s[i] === ']') { i++; return arr }
            arr.push(value())
            ws()
            if (s[i] === ',') { i++; continue }
            if (s[i] === ']') { i++; return arr }
            die('expected , or ] in list')
        }
    }
    function compound() {
        i++ // {
        var o = {}
        ws()
        if (s[i] === '}') { i++; return o }
        for (;;) {
            ws()
            if (s[i] === '}') { i++; return o }
            var key
            if (s[i] === '"' || s[i] === '\'') key = readQuoted()
            else key = readBare()
            ws()
            if (s[i] !== ':') die('expected : after key ' + JSON.stringify(key))
            i++
            o[key] = value()
            ws()
            if (s[i] === ',') { i++; continue }
            if (s[i] === '}') { i++; return o }
            die('expected , or } in compound')
        }
    }
    var v = value()
    ws()
    if (i !== n) die('trailing garbage')
    return v
}

var root = parse(text)

// ---------------------------------------------------------------- walk
function nameOf(item) {
    // reads display.Name which is a JSON string like {"text":"..."}
    var t = item && item.tag
    if (!t) return null
    if (!t.display || typeof t.display.Name !== 'string') return null
    try { var j = JSON.parse(t.display.Name); return j.text } catch (e) { return { __unparsed: t.display.Name } }
}

var cells = []
var patterns = []
var walk = function (v, cellName) {
    if (!v) return
    if (Array.isArray(v)) { for (var k = 0; k < v.length; k++) walk(v[k], cellName) ; return }
    if (typeof v !== 'object' || v.__bare !== undefined) return
    var id = v.id
    if (id === 'ae2:portable_item_cell_16k') {
        var nm = nameOf(v)
        cells.push({ slot: v.Slot, count: v.Count, name: nm, rawName: v.tag && v.tag.display && v.tag.display.Name })
        // recurse into its keys with this cell name attached
        if (v.tag && v.tag.keys) {
            var keys = v.tag.keys
            for (var k2 = 0; k2 < keys.length; k2++) walk(keys[k2], nm)
        }
        // also recurse any other nested structure (defensive)
        return
    }
    if (id === 'ae2:processing_pattern') {
        patterns.push({
            cell: cellName,
            slot: v.Slot,
            in: (v.tag && v.tag.in) || [],
            out: (v.tag && v.tag.out) || [],
            encodePlayer: v.tag && v.tag.encodePlayer,
            gtlcore: v.tag && v.tag.gtlcore
        })
        return
    }
    // generic recursion
    for (var key in v) {
        if (!Object.prototype.hasOwnProperty.call(v, key)) continue
        walk(v[key], cellName)
    }
}
walk(root, null)

// ---------------------------------------------------------------- report
var rawCount = (text.match(/ae2:processing_pattern/g) || []).length

console.log('=== RAW vs PARSED ===')
console.log('raw occurrences of "ae2:processing_pattern" = ' + rawCount)
console.log('parsed  patterns                            = ' + patterns.length)
console.log('MATCH = ' + (rawCount === patterns.length))
console.log('')
console.log('=== CELLS (' + cells.length + ') ===')
for (var c = 0; c < cells.length; c++) {
    console.log('  Slot ' + cells[c].slot + '  Count=' + cells[c].count + '  Name=' + JSON.stringify(cells[c].name) + '  raw=' + cells[c].rawName)
}
console.log('')
console.log('=== CELL SLOT HISTOGRAM ===')
var bySlot = {}
for (var c2 = 0; c2 < cells.length; c2++) bySlot[cells[c2].slot] = (bySlot[cells[c2].slot] || 0) + 1
console.log(JSON.stringify(bySlot))
console.log('')

function slotDesc(el) {
    if (el === undefined) return { kind: 'UNDEFINED' }
    if (el === null) return { kind: 'NULL' }
    if (typeof el !== 'object') return { kind: 'SCALAR', v: el }
    if (Array.isArray(el)) return { kind: 'ARRAY', len: el.length }
    var keys = Object.keys(el)
    if (keys.length === 0) return { kind: 'EMPTY' }
    var cls = el['#c']
    var cnt = el['#']
    var id = el.id
    if (cls === 'ae2:i') {
        var nm = nameOf(el)
        return { kind: 'ITEM', id: id, count: cnt, name: nm, nbt: el.tag || null, rawTag: el.tag ? el.tag.display && el.tag.display.Name : null }
    }
    if (cls === 'ae2:f') return { kind: 'FLUID', id: id, amount: cnt, nbt: el.tag || null }
    return { kind: 'OTHER', keys: keys, cls: cls, id: id, raw: el }
}

var report = []
for (var p = 0; p < patterns.length; p++) {
    var pt = patterns[p]
    var inS = [], outS = []
    for (var a = 0; a < pt.in.length; a++) inS.push(slotDesc(pt.in[a]))
    for (var b = 0; b < pt.out.length; b++) outS.push(slotDesc(pt.out[b]))
    report.push({ idx: p, cell: pt.cell, encodePlayer: pt.encodePlayer, gtlcore: pt.gtlcore, in: inS, out: outS, inLen: pt.in.length, outLen: pt.out.length })
}

console.log('=== PATTERNS ===')
for (var r = 0; r < report.length; r++) {
    var R = report[r]
    console.log('--- #' + (r + 1) + '  cell=' + JSON.stringify(R.cell) + ' ---')
    console.log('    in(len=' + R.inLen + '):')
    for (var x = 0; x < R.in.length; x++) {
        var d = R.in[x]
        if (d.kind === 'EMPTY') continue
        console.log('      [' + x + '] ' + JSON.stringify(d))
    }
    console.log('    out(len=' + R.outLen + '):')
    for (var y = 0; y < R.out.length; y++) {
        var d2 = R.out[y]
        if (d2.kind === 'EMPTY') continue
        console.log('      [' + y + '] ' + JSON.stringify(d2))
    }
}

fs.writeFileSync('C:\\Users\\david\\Desktop\\构建\\shanhai重构\\recipe-convert\\parsed.json', JSON.stringify({ rawCount: rawCount, parsedCount: patterns.length, cells: cells, patterns: report }, null, 1), 'utf8')
console.log('')
console.log('wrote kubejs/generators\\parsed.json')
