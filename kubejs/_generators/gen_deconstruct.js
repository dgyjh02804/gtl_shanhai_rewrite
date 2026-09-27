// =============================================================================
// gen_deconstruct.js -- 生成「原初物质解构」全量配方
//   产物: kubejs/server_scripts/[server_scripts]shanhai_deconstruct_recipes.js   （勿手改【生成部分】；文件末尾有手写区，生成器会原样保留）
//
//   口径（用户 2026-09-26 裁定）：
//     输入  1x 粉 或 1000mB 流体（形态各一条）
//     输出  各元素按其化学式原子数（系数 1）；有粉出粉、没粉出流体
//     token -> 元素 的匹配顺序：
//        ① 整 token 对【完整符号表】精确匹配（表 = materials.json 里 isElement=true 的材质）
//        ② 剥 "-质量数" 后缀再匹配（解决 U / Pu：gtceu:uranium 的 elementSymbol 是 "U-238"）
//        ③ 仍未命中 -> 计入未映射清单
//     🔴 "Au?" 视为【一个整体元素】-> gtceu:infused_gold_dust
//        用户原话：「Au？是一个完整的元素，它的id是gtceu:infused_gold_dust」
//        （尽管注册表里 gtceu:infused_gold 的 isElement=false —— 用户设计意图优先）
//     🔴 上标【质量数】不参与计数（U²³⁸ 算 U:1）。判据：只有式子里真的出现
//        <符号><上标数字> 且数字 > 100 时才收敛成 1，普通 "238x" 不受影响。
//
//   配方 id 规则（用户 2026-09-26 裁定：全带 namespace）：
//        'shanhai:deconstruct/' + <namespace>_<本地名> + '_dust' | '_fluid'
//        => gtceu:ruridit        -> shanhai:deconstruct/gtceu_ruridit_dust
//        => gtladditions:ruridit -> shanhai:deconstruct/gtladditions_ruridit_dust
//      （旧规则丢掉了 namespace，导致这两个撞成同一个 id —— 就是线上那 1 条 Duplicate）
//
//   已跳过 5 个有手写配方的材质（用户裁定：保留手写的）：
//        proto_halkonite / phonon_crystal_solution / star_gate_crystal_slurry
//        / phonon_medium / proto_halkonite_base
//
//   🔴 生成期自检：id 唯一性（Set 去重），不唯一就把重复组全部打印出来。
// =============================================================================
const fs=require('fs');
const EV='C:\\Users\\david\\Desktop\\构建\\shanhai重构\\originals\\matdump\\evidence\\';
const OUT='C:\\Users\\david\\Desktop\\构建\\shanhai重构\\kubejs\\server_scripts\\[server_scripts]shanhai_deconstruct_recipes.js';
const mats=JSON.parse(fs.readFileSync(EV+'materials.json','utf8'));
const pr=JSON.parse(fs.readFileSync(EV+'parse_results.json','utf8'));
const LANG=JSON.parse(fs.readFileSync(EV+'lang_zh_cn.json','utf8'));
const prById={}; pr.forEach(x=>prById[x.id]=x); const mById={}; mats.forEach(m=>mById[m.id]=m);
const cn=id=>LANG['material.gtceu.'+id.split(':')[1]]||LANG['material.'+id.replace(':','.')]||'(无名)';
const SYM={}; mats.forEach(m=>{ if(m.isElement && m.elementSymbol) SYM[m.elementSymbol]=m; });
const ISO=/^([A-Z][a-z]?)-(\d+)$/; const ISOFALL={};
for(const k in SYM){ const g=k.match(ISO); if(g && !ISOFALL[g[1]]) ISOFALL[g[1]]=SYM[k]; }
// 🔴 Au? 的【规范归属】= infused_gold（用户裁定）；各材质自己式子就是 Au? 时会在下面覆盖成自己的粉
SYM['Au?'] = mats.find(x=>x.id==='gtceu:infused_gold');
// 🔴 7 组同符号冲突（T / M / §9Sl / §ketime / §kestar_matter / §6§kestar_matter / §ke§r(u₂);…）
//    SYM 只能留一个 => 靠下面「自己的式子就是自己 => 出自己的粉」统一兜住
const SUPS='\u2070\u00b9\u00b2\u00b3\u2074\u2075\u2076\u2077\u2078\u2079';
const supNum=s=>s.split('').map(c=>String(SUPS.indexOf(c))).join('');
function massFix(m){ const fix={}; const re=new RegExp('([A-Z][a-z]?)(['+SUPS+']{2,})','g'); let mm;
  while((mm=re.exec(String(m.formula||'')))){ if(parseInt(supNum(mm[2]),10)>100) fix[mm[1]]=1; } return fix; }
const SKIP5={'gtladditions:proto_halkonite':1,'gtladditions:phonon_crystal_solution':1,
  'gtladditions:star_gate_crystal_slurry':1,'gtladditions:phonon_medium':1,'gtladditions:proto_halkonite_base':1};
// 🔴 用户 2026-09-26 裁定：式子【含 ?】的材质不生成配方（用户 2026-09-26 扩展裁定）—— 但先把 Au? 整体剔掉，因为它不是 instability（那个 ? 更像"未知成分"，不是元素）
//    但【instability 自己】保留 —— 它的式子就是 ?，而它是真元素
var QDROP={}; mats.forEach(m=>{ if(String(m.formula).split('Au?').join('').indexOf('?')>=0 && m.id!=='gtceu:instability') QDROP[m.id]=1; });
var QDROPPED=[];
function build(id){
  const m=mById[id], p=prById[id]; if(!m||!p) return null;
  const cnt=Object.assign({},p.counts||{});
  const mf=massFix(m); for(const s in mf) if(cnt[s]!==undefined) cnt[s]=1;
  const items=[],fluids=[],drop=[];
  // 🔴 Au? 是【两字符拼出来的一个符号】：配对回去，别让它被拆成 Au + ?
  //    自己式子就是 Au? 的材质(infused_gold/thaumium) => 出自己的粉（上面的自我优先规则会接管）
  //    其它材质(astral_silver 的 Ag₂Au?) => 归到规范材质 infused_gold
  if(String(m.formula).indexOf('Au?')>=0 && cnt['?']>0){
    const nq=cnt['?'], na=cnt['Au']||0, take=Math.min(nq,na);
    cnt['Au']=na-take; cnt['?']=nq-take;
    if(!cnt['Au']) delete cnt['Au'];
    if(!cnt['?']) delete cnt['?'];
    cnt['\u0000AUQ']=take;
  }
  for(const raw of Object.keys(cnt)){
    if(raw==='\u0000AUQ'){ const self2=(String(m.formula)==='Au?')?m:mats.find(x=>x.id==='gtceu:infused_gold'); items.push(cnt['\u0000AUQ']+'x '+self2.id+'_dust'); continue; }
    let t=SYM[raw]?{m:SYM[raw],f:ISO.test(raw)}:(ISOFALL[raw]?{m:ISOFALL[raw],f:false}:null);
    // 🔴 统一规则：本材质的化学式【就是】这个 token => 出它自己的粉（解决 Au?/T/M/§9Sl… 等同符号多材质）
    if(t && String(m.formula)===raw && t.m.id !== m.id){ t={m:m, f:false}; }
    if(!t){ drop.push(raw); continue; }
    const n=t.f?1:cnt[raw];
    if(t.m.dustItem) items.push(n+'x '+t.m.id+'_dust');
    else if(t.m.propFluid) fluids.push(t.m.id + ' ' + (n * 1000));   // 🔴 单位是 mB：输入 1000mB = 1 个化学式单位 => 每个原子 = 1000 mB
    else drop.push(raw);
  }
  return {items,fluids,drop};
}
// =============================================================================
// 🔴 流体 id 修正：从【运行期实测的证据文件】读取（用户 2026-09-26 裁定）。
//    originals/matdump/evidence/fluid_ids.json  ← 专服探针实测产物，可复用，不用再跑探针。
//    全表 958 个 propFluid=true：944 个的 <材质id> 就是真流体；14 个不是（查出来是 minecraft:empty）。
//    fluidFix  = 改用它给的真流体 id   fluidDrop = 找不到真流体 => 不生成流体配方
//    ⚠️ 不能用「有 liquid_ 兄弟就当假」那条规则：hydrogen/air/nether_air/ender_air/starlight
//       都有 liquid_ 兄弟，但气体与液体是两个真流体，那条规则会误删 5 个。
var FLUID_EV = JSON.parse(fs.readFileSync(EV+'fluid_ids.json','utf8'));
var FLUID_MAP = FLUID_EV.fluidFix;
var DROP_FLUID = FLUID_EV.fluidDrop;
var FLUID_DROPPED = [];
const mkId=(id,k)=>'shanhai:deconstruct/'+id.replace(':','_')+'_'+k;   // 🔴 全带 namespace
const jobs=[],empties=[],allIds=[],toks={};
for(const m of mats){
  if(!m.formula||!m.formula.length) continue;
  const o=build(m.id); if(!o) continue;
  o.drop.forEach(d=>{ if(SKIP5[m.id]) return; if(!toks[d]) toks[d]={n:0,ids:[]}; toks[d].n++; if(toks[d].ids.length<4) toks[d].ids.push(m.id); });
  if(!o.items.length&&!o.fluids.length){ empties.push(m.id); continue; }
  if(QDROP[m.id]){ QDROPPED.push(m.id); continue; }
  if(SKIP5[m.id]) continue;
  const forms=[];
  if(m.dustItem)  forms.push({k:'dust', inItem:'1x '+m.id+'_dust', inFluid:null});
  if(m.propFluid){
    var _fid = FLUID_MAP[m.id] || m.id;
    if(DROP_FLUID[m.id]){ if(!SKIP5[m.id]){ FLUID_DROPPED.push(m.id); } }
    else { forms.push({k:'fluid', inItem:null, inFluid:_fid+' 1000'}); }
  }
  for(const f of forms){ const rid=mkId(m.id,f.k); allIds.push(rid);
    jobs.push({id:rid,inItem:f.inItem,inFluid:f.inFluid,items:o.items,fluids:o.fluids}); }
}
// ---------- 🔴 生成期自检：id 唯一性 ----------
const cntId={}; allIds.forEach(i=>cntId[i]=(cntId[i]||0)+1);
const dupIds=Object.keys(cntId).filter(i=>cntId[i]>1);
console.log('=== 生成期自检：id 唯一性 ===');
console.log('  id 条数        = '+allIds.length);
console.log('  唯一 id 数(Set)= '+new Set(allIds).size);
console.log('  重复组数       = '+dupIds.length+(dupIds.length?'  ->  '+dupIds.map(i=>i+' x'+cntId[i]).join(' , '):'  ✅'));
if(dupIds.length){ console.log('  !! 有重复 id，产物仍然会写，但请在部署前修掉'); }
console.log('');
console.log('=== 其他统计 ===');
console.log('  生成条数 = '+jobs.length+'   输出为空 = '+empties.length);
console.log('  max itemOut = '+jobs.reduce((a,j)=>Math.max(a,j.items.length),0)+'   max fluidOut = '+jobs.reduce((a,j)=>Math.max(a,j.fluids.length),0));
const keys=Object.keys(toks);
console.log('  未映射 token（已排除被跳过的材质） = '+keys.length+' 种 / '+keys.reduce((a,k)=>a+toks[k].n,0)+' 次  '+JSON.stringify(keys));
fs.writeFileSync('C:\\Users\\david\\Desktop\\构建\\shanhai重构\\temp\\evidence\\unmapped-tokens.txt',
  ['token\t出现次数\t涉及材质(中文名)'].concat(keys.map(k=>[k,toks[k].n,toks[k].ids.map(i=>i+'('+cn(i)+')').join(' ')].join('\t'))).join('\n')+'\n','utf8');
fs.writeFileSync('C:\\Users\\david\\Desktop\\构建\\shanhai重构\\temp\\evidence\\empty-output-materials.txt',
  empties.map(i=>i+'\t'+cn(i)+'\t'+(mById[i]?mById[i].formula:'')).join('\n')+'\n','utf8');
console.log('  空输出清单 -> _evidence\\empty-output-materials.txt ('+empties.length+' 行)');
console.log('  🔴 因【找不到真流体】而不生成流体配方的材质 = '+FLUID_DROPPED.length+' 条: '+JSON.stringify(FLUID_DROPPED));
console.log('  🔴 因【式子是 ?】而不生成配方的材质 = '+QDROPPED.length+' 条: '+JSON.stringify(QDROPPED));
console.log('');
console.log('=== 举例（namespace 进 id）===');
['gtceu:ruridit','gtladditions:ruridit','gtladditions:liquid_ruridit'].forEach(id=>{
  const m=mById[id]; const f=[]; if(m.dustItem)f.push('dust'); if(m.propFluid)f.push('fluid');
  console.log('   '+id.padEnd(32)+' -> '+f.map(k=>mkId(id,k)).join('  ,  ')); });
// ---------- 写产物 ----------
const L=[],w=s=>L.push(s===undefined?'':s);
w('// priority: 1');
w('// [server_scripts]shanhai_deconstruct_recipes.js -- 「原初物质解构」全量配方（生成部分勿手改；文件末尾手写区由生成器原样保留）｜上限 (1,103,1,16)');
w('//   生成器 kubejs/_generators/gen_deconstruct.js');
w('//   输入 1x 粉 / 1000mB 流体；输出 = 各元素按化学式原子数（系数1），有粉出粉、没粉出流体');
w('//   token 匹配顺序：① 完整符号表精确匹配 ② 剥 "-质量数" 后缀再匹配');
w('//   🔴 用户 2026-09-26：化学式里的 "Au?" 视为【一个整体元素】-> gtceu:infused_gold_dust');
w('//      （尽管注册表 gtceu:infused_gold 的 isElement=false；用户设计意图优先）');
w('//   🔴 用户 2026-09-26：上标【质量数】不参与计数（U²³⁸ 算 U:1，不是 U:238）');
w('//   🔴 用户 2026-09-26：配方 id 全带 namespace（旧规则丢 namespace，导致 ruridit 撞车）');
w('//   已跳过 5 个有手写配方的材质；注册前读 global.SD_EXTRA（表不存在则 {}，不报错）');
w('ServerEvents.recipes(function (event) {');
w('    var gtr = event.recipes.gtceu');
w('    // 🔴 2026-09-27 接进配方统计（用户：「以后配方添加之后都检查一下」）')
w('    //    本脚本【不调 reset()】—— 它跑在三个脚本的最前，累加器本来就是 0；')
w('    //    而且不 reset 更安全：万一以后顺序变了，也不会把别人已经报过的数清零。')
w('    //    ⚠️ 变量名用 ShanhaiStats：Rhino 里 `Stats` 会回落到原版 net.minecraft.stats.Stats。')
w('    var ShanhaiStats = null')
w('    try { ShanhaiStats = Java.loadClass(\'com.shanhai.common.recipe.ShanhaiRecipeStats\') } catch (eS) { ShanhaiStats = null }')
w('    var T = \'primordial_matter_deconstruction\'');
w('    var EXTRA = (typeof SD_EXTRA !== \'undefined\' && SD_EXTRA) ? SD_EXTRA : {}');
  w('    // 🔴 元素符号 -> 材质 id（生成期固化，供 set 覆盖输出时定位）');
  w('    var SYM2MAT = {"Ac":"gtceu:actinium","Al":"gtceu:aluminium","Am":"gtceu:americium","Sb":"gtceu:antimony","Ar":"gtceu:argon","As":"gtceu:arsenic","At":"gtceu:astatine","Ba":"gtceu:barium","Bk":"gtceu:berkelium","Be":"gtceu:beryllium","Bi":"gtceu:bismuth","Bh":"gtceu:bohrium","B":"gtceu:boron","Br":"gtceu:bromine","Cs":"gtceu:caesium","Ca":"gtceu:calcium","Cf":"gtceu:californium","C":"gtceu:carbon","Cd":"gtceu:cadmium","Ce":"gtceu:cerium","Cl":"gtceu:chlorine","Cr":"gtceu:chromium","Co":"gtceu:cobalt","Cn":"gtceu:copernicium","Cu":"gtceu:copper","Cm":"gtceu:curium","Ds":"gtceu:darmstadtium","D":"gtceu:deuterium","Db":"gtceu:dubnium","Dy":"gtceu:dysprosium","Es":"gtceu:einsteinium","Er":"gtceu:erbium","Eu":"gtceu:europium","Fm":"gtceu:fermium","Fl":"gtceu:flerovium","F":"gtceu:fluorine","Fr":"gtceu:francium","Gd":"gtceu:gadolinium","Ga":"gtceu:gallium","Ge":"gtceu:germanium","Au":"gtceu:gold","Hf":"gtceu:hafnium","Hs":"gtceu:hassium","Ho":"gtceu:holmium","H":"gtceu:hydrogen","He":"gtceu:helium","He-3":"gtceu:helium_3","In":"gtceu:indium","I":"gtceu:iodine","Ir":"gtceu:iridium","Fe":"gtceu:iron","Kr":"gtceu:krypton","La":"gtceu:lanthanum","Lr":"gtceu:lawrencium","Pb":"gtceu:lead","Li":"gtceu:lithium","Lv":"gtceu:livermorium","Lu":"gtceu:lutetium","Mg":"gtceu:magnesium","Md":"gtceu:mendelevium","Mn":"gtceu:manganese","Mt":"gtceu:meitnerium","Hg":"gtceu:mercury","Mo":"gtceu:molybdenum","Mc":"gtceu:moscovium","Nd":"gtceu:neodymium","Ne":"gtceu:neon","Np":"gtceu:neptunium","Ni":"gtceu:nickel","Nh":"gtceu:nihonium","Nb":"gtceu:niobium","N":"gtceu:nitrogen","No":"gtceu:nobelium","Og":"gtceu:oganesson","Os":"gtceu:osmium","O":"gtceu:oxygen","Pd":"gtceu:palladium","P":"gtceu:phosphorus","Po":"gtceu:polonium","Pt":"gtceu:platinum","Pu-239":"gtceu:plutonium","Pu-241":"gtceu:plutonium_241","K":"gtceu:potassium","Pr":"gtceu:praseodymium","Pm":"gtceu:promethium","Pa":"gtceu:protactinium","Rn":"gtceu:radon","Ra":"gtceu:radium","Re":"gtceu:rhenium","Rh":"gtceu:rhodium","Rg":"gtceu:roentgenium","Rb":"gtceu:rubidium","Ru":"gtceu:ruthenium","Rf":"gtceu:rutherfordium","Sm":"gtceu:samarium","Sc":"gtceu:scandium","Sg":"gtceu:seaborgium","Se":"gtceu:selenium","Si":"gtceu:silicon","Ag":"gtceu:silver","Na":"gtceu:sodium","Sr":"gtceu:strontium","S":"gtceu:sulfur","Ta":"gtceu:tantalum","Tc":"gtceu:technetium","Te":"gtceu:tellurium","Ts":"gtceu:tennessine","Tb":"gtceu:terbium","Th":"gtceu:thorium","Tl":"gtceu:thallium","Tm":"gtceu:thulium","Sn":"gtceu:tin","Ti":"gtceu:titanium","T":"gtceu:tear","W":"gtceu:tungsten","U-238":"gtceu:uranium","U-235":"gtceu:uranium_235","V":"gtceu:vanadium","Xe":"gtceu:xenon","Yb":"gtceu:ytterbium","Y":"gtceu:yttrium","Zn":"gtceu:zinc","Zr":"gtceu:zirconium","Nq":"gtceu:naquadah","Nq+":"gtceu:enriched_naquadah","*Nq*":"gtceu:naquadria","Nt":"gtceu:neutronium","Tr":"gtceu:tritanium","Dr":"gtceu:duranium","Ke":"gtceu:trinium","An":"gtceu:adamantium","Qt":"gtceu:quantanium","Vi":"gtceu:vibranium","Dc":"gtceu:draconium","§8§kchaos":"gtceu:chaos","Hy⚶":"gtceu:hypogen","Sh⏧":"gtceu:shirabon","Mi":"gtceu:mithril","Tn":"gtceu:taranium","§b§ke§r§b✧§ke":"gtceu:crystalmatrix","Cnt":"gtceu:cosmicneutronium","Ec":"gtceu:echoite","Le":"gtceu:legendarium","✵Dc✵":"gtceu:draconiumawakened","Ad":"gtceu:adamantine","St":"gtceu:starmetal","Or":"gtceu:orichalcum","If":"gtceu:infuscolium","En":"gtceu:enderium","Et❃":"gtceu:eternity","M⎋":"gtceu:magmatter","§bRe":"gtceu:degenerate_rhenium","§b§ke§r§b(u₂);d§ke":"gtceu:heavy_quark_degenerate_matter","§b§ke§r§b(u₂);d(c₂);s(t₂);bg§ke":"gtceu:quantumchromodynamically_confined_matter","§kmetal":"gtceu:transcendentmetal","Ur":"gtceu:uruium","§6§kestar_matter":"gtceu:raw_star_matter","§kestar_matter":"gtceu:black_dwarf_mtter","✧◇✧":"gtceu:astraltitanium","✦◆✦":"gtceu:celestialtungsten","M":"gtceu:attuned_tengam","Yb¹⁷⁸":"gtceu:ytterbium_178","§5§kemana":"gtceu:mana","§ke§re§ke":"gtceu:free_electron_gas","§ke§rα§ke":"gtceu:free_alpha_gas","§ke§rp§ke":"gtceu:free_proton_gas","§ke§r(u2);d(c2);s(t2);bg§ke":"gtceu:quark_gluon","§ke§r(u₂);d§ke":"gtceu:heavy_quarks","§ke§r(c₂);(t₂);b§ke":"gtceu:light_quarks","§ke§rg§ke":"gtceu:gluons","Ti⁵⁰":"gtceu:titanium_50","§ke§r(t₂);u§ke":"gtceu:heavy_lepton_mixture","§ke§r(u₂);d(c₂);s(t₂);b§ke":"gtceu:high_energy_quark_gluon","§9Sl":"gtceu:starlight","§ke§rn§ke":"gtceu:dense_neutron","§ketime":"gtceu:temporalfluid","§kcm":"gtceu:cosmic_mesh","Fs⚶":"gtceu:rhugnor","Cu⁷⁶":"gtceu:copper76","§7熔炼为流体的时空":"gtceu:spacetime","∞":"gtceu:infinity","?":"gtceu:instability","Ct":"gtceu:celestial_secret","⸎":"gtladditions:creon","U":"gtceu:uranium","Pu":"gtceu:plutonium","§ke§r(u₂);d(c₂);s(t₂);bg§ke":"gtceu:high_energy_quark_gluon"}');
w('    var ok = 0, bad = 0, errList = \'\'');
w('    var JOBS = [');
jobs.forEach((j,i)=>{ w('        '+(i>0?',':'')+'{');
  w('            id: \''+j.id+'\', inItem: '+(j.inItem?'\''+j.inItem+'\'':'null')+', inFluid: '+(j.inFluid?'\''+j.inFluid+'\'':'null')+',');
  w('            outItems: ['+j.items.map(v=>'\''+v+'\'').join(', ')+'],');
  w('            outFluids: ['+j.fluids.map(v=>'\''+v+'\'').join(', ')+']');
  w('        }'); });
w('    ]');
  w('    var applySet = function (sym, v) {');
  w('        var mid = SYM2MAT[sym] || (\'gtceu:\' + sym);');
  w('        var isF = false, num = v;');
  w('        if (typeof v === \'string\') {');
  w('            if (v.indexOf(\'mB\') >= 0) { isF = true; num = parseInt(v.replace(/[^0-9]/g, \'\'), 10) }');
  w('            else { num = parseInt(v, 10) }');
  w('        }');
  w('        var src = isF ? fluids : items, out = [], q;');
  w('        for (q = 0; q < src.length; q++) { if (src[q].indexOf(mid + \' \') !== 0) { out.push(src[q]) } }');
  w('        out.push(isF ? (mid + \' \' + num) : (num + \'x \' + mid + \'_dust\'));');
  w('        if (isF) { fluids = out } else { items = out }');
  w('    };');
w('    var j, k');
w('    for (j = 0; j < JOBS.length; j++) {');
w('        var J = JOBS[j], items = J.outItems.slice(), fluids = J.outFluids.slice()');
w('        var ex = EXTRA[J.id] || EXTRA[J.id.replace(/_dust$/, \'\').replace(/_fluid$/, \'\')]');
w('        if (ex) {');
  w('            if (ex.add) { for (k = 0; k < ex.add.length; k++) { if (ex.add[k].indexOf(\'mB\') >= 0) { fluids.push(ex.add[k].replace(/\\s*mB\\s*/, \'\')) } else { items.push(ex.add[k]) } } }');
  w('            if (ex.set) { for (k in ex.set) { applySet(k, ex.set[k]) } }');
  w('            if (ex.setFluid) { for (k in ex.setFluid) { applySet(k, ex.setFluid[k] + \' mB\') } }');
w('        }');
w('        try {');
w('            var b = gtr[T](J.id)');
w('            if (J.inItem !== null) { b = b.itemInputs(J.inItem) }');
w('            if (J.inFluid !== null) { b = b.inputFluids(J.inFluid) }');
w('            if (items.length) { b = b.itemOutputs(items) }');
w('            if (fluids.length) { b = b.outputFluids(fluids) }');
w('            b = b.duration(200).EUt(32)');
w('            ok = ok + 1');
w('            if (ShanhaiStats) ShanhaiStats.addResult(true)')
w('        } catch (e) { bad = bad + 1; if (errList.length < 1500) { errList = errList + J.id + \' => \' + e + \' | \' } ; if (ShanhaiStats) ShanhaiStats.addResult(false) }');
w('    }');
w('    console.info(\'[SHANHAI-DECON] jobs=\' + JOBS.length + \' => ok=\' + ok + \' failed=\' + bad + \' EUt=32\')');
w('    // 🔴 2026-09-27：给本批留一次 summary —— 横幅【一行一批】需要它进 BY_SCOPE。')
w('    //    本脚本【不 reset()】（跑最前，累加器本来就是 0）⇒ 这里的 total 就是本批的条数。')
w('    if (ShanhaiStats) {')
w('        try { ShanhaiStats.reportSummary(' + String.fromCharCode(39) + 'shanhai_deconstruct' + String.fromCharCode(39) + ') } catch (eR3) { console.info(' + String.fromCharCode(39) + '[SHANHAI-DECON] reportSummary FAILED: ' + String.fromCharCode(39) + ' + eR3) }')
w('    }')
w('    if (bad > 0) { console.info(\'[SHANHAI-DECON] fail: \' + errList) }');
w('})');
w('    // 本脚本【不】在这里报 summary —— 汇总由 [server_scripts]shanhai_recipes.js 的 ServerEvents.loaded 打，')
w('    //    那时三批都已上报完（靠 lifetime 字段，与调用时机无关）。')
// 🔴 2026-09-27 用户点单第 3 条：deconstruct 三合一 => 产物 = 【生成部分】+【手写区】。
//    · 手写区 = 两个标记之间的内容，【生成器原样带过去】，绝不改写。
//    · 首次生成（产物不存在）=> 写入默认手写区 `global.SD_EXTRA = {}`。
//    · 手写区放在文件【末尾】且是【顶层】：回调是稍后触发的，SD_EXTRA 一定已经赋值。
const M_BEGIN = '// ═══ 手写区 开始（生成器不会动这一段）═══'
const M_END   = '// ═══ 手写区 结束 ═══'
let HANDS = ['global.SD_EXTRA = {}']
let handNote = '(第 '+1+' 次生成：产物不存在 => 写入默认空表 global.SD_EXTRA = {})'
if (fs.existsSync(OUT)) {
    const prev = fs.readFileSync(OUT, 'utf8').split(/\r?\n/)
    const a = prev.findIndex(l => l.indexOf(M_BEGIN) >= 0)
    const b = prev.findIndex(l => l.indexOf(M_END) >= 0)
    if (a >= 0 && b > a) {
        HANDS = prev.slice(a + 1, b)
        handNote = '(保留自旧产物：' + HANDS.length + ' 行)'
    } else {
        handNote = '(旧产物里【没有】手写区标记 => 写入默认空表；原文件已备份到 .no-handreg-bak)'
        fs.writeFileSync(OUT + '.no-handreg-bak', prev.join('\r\n'), 'utf8')
    }
}
const OUT_TXT = L.join('\r\n') + '\r\n\r\n' + M_BEGIN + '\r\n' + HANDS.join('\r\n') + '\r\n' + M_END + '\r\n'
fs.writeFileSync(OUT, OUT_TXT, 'utf8')
console.log('  手写区 ' + handNote)
console.log('');
console.log('  产物 = '+fs.statSync(OUT).size+' B  -> '+OUT);
