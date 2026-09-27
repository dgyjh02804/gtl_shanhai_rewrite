// priority: 1
// [server_scripts]shanhai_deconstruct_recipes.js -- 「原初物质解构」全量配方（生成部分勿手改；文件末尾手写区由生成器原样保留）｜上限 (1,103,1,16)
//   生成器 kubejs/_generators/gen_deconstruct.js
//   输入 1x 粉 / 1000mB 流体；输出 = 各元素按化学式原子数（系数1），有粉出粉、没粉出流体
//   token 匹配顺序：① 完整符号表精确匹配 ② 剥 "-质量数" 后缀再匹配
//   🔴 用户 2026-09-26：化学式里的 "Au?" 视为【一个整体元素】-> gtceu:infused_gold_dust
//      （尽管注册表 gtceu:infused_gold 的 isElement=false；用户设计意图优先）
//   🔴 用户 2026-09-26：上标【质量数】不参与计数（U²³⁸ 算 U:1，不是 U:238）
//   🔴 用户 2026-09-26：配方 id 全带 namespace（旧规则丢 namespace，导致 ruridit 撞车）
//   已跳过 5 个有手写配方的材质；注册前读 global.SD_EXTRA（表不存在则 {}，不报错）
ServerEvents.recipes(function (event) {
    var gtr = event.recipes.gtceu
    // 🔴 2026-09-27 接进配方统计（用户：「以后配方添加之后都检查一下」）
    //    本脚本【不调 reset()】—— 它跑在三个脚本的最前，累加器本来就是 0；
    //    而且不 reset 更安全：万一以后顺序变了，也不会把别人已经报过的数清零。
    //    ⚠️ 变量名用 ShanhaiStats：Rhino 里 `Stats` 会回落到原版 net.minecraft.stats.Stats。
    var ShanhaiStats = null
    try { ShanhaiStats = Java.loadClass('com.shanhai.common.recipe.ShanhaiRecipeStats') } catch (eS) { ShanhaiStats = null }
    var T = 'primordial_matter_deconstruction'
    var EXTRA = (typeof SD_EXTRA !== 'undefined' && SD_EXTRA) ? SD_EXTRA : {}
    // 🔴 元素符号 -> 材质 id（生成期固化，供 set 覆盖输出时定位）
    var SYM2MAT = {"Ac":"gtceu:actinium","Al":"gtceu:aluminium","Am":"gtceu:americium","Sb":"gtceu:antimony","Ar":"gtceu:argon","As":"gtceu:arsenic","At":"gtceu:astatine","Ba":"gtceu:barium","Bk":"gtceu:berkelium","Be":"gtceu:beryllium","Bi":"gtceu:bismuth","Bh":"gtceu:bohrium","B":"gtceu:boron","Br":"gtceu:bromine","Cs":"gtceu:caesium","Ca":"gtceu:calcium","Cf":"gtceu:californium","C":"gtceu:carbon","Cd":"gtceu:cadmium","Ce":"gtceu:cerium","Cl":"gtceu:chlorine","Cr":"gtceu:chromium","Co":"gtceu:cobalt","Cn":"gtceu:copernicium","Cu":"gtceu:copper","Cm":"gtceu:curium","Ds":"gtceu:darmstadtium","D":"gtceu:deuterium","Db":"gtceu:dubnium","Dy":"gtceu:dysprosium","Es":"gtceu:einsteinium","Er":"gtceu:erbium","Eu":"gtceu:europium","Fm":"gtceu:fermium","Fl":"gtceu:flerovium","F":"gtceu:fluorine","Fr":"gtceu:francium","Gd":"gtceu:gadolinium","Ga":"gtceu:gallium","Ge":"gtceu:germanium","Au":"gtceu:gold","Hf":"gtceu:hafnium","Hs":"gtceu:hassium","Ho":"gtceu:holmium","H":"gtceu:hydrogen","He":"gtceu:helium","He-3":"gtceu:helium_3","In":"gtceu:indium","I":"gtceu:iodine","Ir":"gtceu:iridium","Fe":"gtceu:iron","Kr":"gtceu:krypton","La":"gtceu:lanthanum","Lr":"gtceu:lawrencium","Pb":"gtceu:lead","Li":"gtceu:lithium","Lv":"gtceu:livermorium","Lu":"gtceu:lutetium","Mg":"gtceu:magnesium","Md":"gtceu:mendelevium","Mn":"gtceu:manganese","Mt":"gtceu:meitnerium","Hg":"gtceu:mercury","Mo":"gtceu:molybdenum","Mc":"gtceu:moscovium","Nd":"gtceu:neodymium","Ne":"gtceu:neon","Np":"gtceu:neptunium","Ni":"gtceu:nickel","Nh":"gtceu:nihonium","Nb":"gtceu:niobium","N":"gtceu:nitrogen","No":"gtceu:nobelium","Og":"gtceu:oganesson","Os":"gtceu:osmium","O":"gtceu:oxygen","Pd":"gtceu:palladium","P":"gtceu:phosphorus","Po":"gtceu:polonium","Pt":"gtceu:platinum","Pu-239":"gtceu:plutonium","Pu-241":"gtceu:plutonium_241","K":"gtceu:potassium","Pr":"gtceu:praseodymium","Pm":"gtceu:promethium","Pa":"gtceu:protactinium","Rn":"gtceu:radon","Ra":"gtceu:radium","Re":"gtceu:rhenium","Rh":"gtceu:rhodium","Rg":"gtceu:roentgenium","Rb":"gtceu:rubidium","Ru":"gtceu:ruthenium","Rf":"gtceu:rutherfordium","Sm":"gtceu:samarium","Sc":"gtceu:scandium","Sg":"gtceu:seaborgium","Se":"gtceu:selenium","Si":"gtceu:silicon","Ag":"gtceu:silver","Na":"gtceu:sodium","Sr":"gtceu:strontium","S":"gtceu:sulfur","Ta":"gtceu:tantalum","Tc":"gtceu:technetium","Te":"gtceu:tellurium","Ts":"gtceu:tennessine","Tb":"gtceu:terbium","Th":"gtceu:thorium","Tl":"gtceu:thallium","Tm":"gtceu:thulium","Sn":"gtceu:tin","Ti":"gtceu:titanium","T":"gtceu:tear","W":"gtceu:tungsten","U-238":"gtceu:uranium","U-235":"gtceu:uranium_235","V":"gtceu:vanadium","Xe":"gtceu:xenon","Yb":"gtceu:ytterbium","Y":"gtceu:yttrium","Zn":"gtceu:zinc","Zr":"gtceu:zirconium","Nq":"gtceu:naquadah","Nq+":"gtceu:enriched_naquadah","*Nq*":"gtceu:naquadria","Nt":"gtceu:neutronium","Tr":"gtceu:tritanium","Dr":"gtceu:duranium","Ke":"gtceu:trinium","An":"gtceu:adamantium","Qt":"gtceu:quantanium","Vi":"gtceu:vibranium","Dc":"gtceu:draconium","§8§kchaos":"gtceu:chaos","Hy⚶":"gtceu:hypogen","Sh⏧":"gtceu:shirabon","Mi":"gtceu:mithril","Tn":"gtceu:taranium","§b§ke§r§b✧§ke":"gtceu:crystalmatrix","Cnt":"gtceu:cosmicneutronium","Ec":"gtceu:echoite","Le":"gtceu:legendarium","✵Dc✵":"gtceu:draconiumawakened","Ad":"gtceu:adamantine","St":"gtceu:starmetal","Or":"gtceu:orichalcum","If":"gtceu:infuscolium","En":"gtceu:enderium","Et❃":"gtceu:eternity","M⎋":"gtceu:magmatter","§bRe":"gtceu:degenerate_rhenium","§b§ke§r§b(u₂);d§ke":"gtceu:heavy_quark_degenerate_matter","§b§ke§r§b(u₂);d(c₂);s(t₂);bg§ke":"gtceu:quantumchromodynamically_confined_matter","§kmetal":"gtceu:transcendentmetal","Ur":"gtceu:uruium","§6§kestar_matter":"gtceu:raw_star_matter","§kestar_matter":"gtceu:black_dwarf_mtter","✧◇✧":"gtceu:astraltitanium","✦◆✦":"gtceu:celestialtungsten","M":"gtceu:attuned_tengam","Yb¹⁷⁸":"gtceu:ytterbium_178","§5§kemana":"gtceu:mana","§ke§re§ke":"gtceu:free_electron_gas","§ke§rα§ke":"gtceu:free_alpha_gas","§ke§rp§ke":"gtceu:free_proton_gas","§ke§r(u2);d(c2);s(t2);bg§ke":"gtceu:quark_gluon","§ke§r(u₂);d§ke":"gtceu:heavy_quarks","§ke§r(c₂);(t₂);b§ke":"gtceu:light_quarks","§ke§rg§ke":"gtceu:gluons","Ti⁵⁰":"gtceu:titanium_50","§ke§r(t₂);u§ke":"gtceu:heavy_lepton_mixture","§ke§r(u₂);d(c₂);s(t₂);b§ke":"gtceu:high_energy_quark_gluon","§9Sl":"gtceu:starlight","§ke§rn§ke":"gtceu:dense_neutron","§ketime":"gtceu:temporalfluid","§kcm":"gtceu:cosmic_mesh","Fs⚶":"gtceu:rhugnor","Cu⁷⁶":"gtceu:copper76","§7熔炼为流体的时空":"gtceu:spacetime","∞":"gtceu:infinity","?":"gtceu:instability","Ct":"gtceu:celestial_secret","⸎":"gtladditions:creon","U":"gtceu:uranium","Pu":"gtceu:plutonium","§ke§r(u₂);d(c₂);s(t₂);bg§ke":"gtceu:high_energy_quark_gluon"}
    var ok = 0, bad = 0, errList = ''
    var JOBS = [
        {
            id: 'shanhai:deconstruct/gtceu_actinium_dust', inItem: '1x gtceu:actinium_dust', inFluid: null,
            outItems: ['1x gtceu:actinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_actinium_fluid', inItem: null, inFluid: 'gtceu:actinium 1000',
            outItems: ['1x gtceu:actinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aluminium_dust', inItem: '1x gtceu:aluminium_dust', inFluid: null,
            outItems: ['1x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aluminium_fluid', inItem: null, inFluid: 'gtceu:aluminium 1000',
            outItems: ['1x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_americium_dust', inItem: '1x gtceu:americium_dust', inFluid: null,
            outItems: ['1x gtceu:americium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_americium_fluid', inItem: null, inFluid: 'gtceu:americium 1000',
            outItems: ['1x gtceu:americium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_antimony_dust', inItem: '1x gtceu:antimony_dust', inFluid: null,
            outItems: ['1x gtceu:antimony_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_antimony_fluid', inItem: null, inFluid: 'gtceu:antimony 1000',
            outItems: ['1x gtceu:antimony_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_argon_fluid', inItem: null, inFluid: 'gtceu:argon 1000',
            outItems: [],
            outFluids: ['gtceu:argon 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_arsenic_dust', inItem: '1x gtceu:arsenic_dust', inFluid: null,
            outItems: ['1x gtceu:arsenic_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_arsenic_fluid', inItem: null, inFluid: 'gtceu:arsenic 1000',
            outItems: ['1x gtceu:arsenic_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_astatine_dust', inItem: '1x gtceu:astatine_dust', inFluid: null,
            outItems: ['1x gtceu:astatine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_astatine_fluid', inItem: null, inFluid: 'gtceu:astatine 1000',
            outItems: ['1x gtceu:astatine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_barium_dust', inItem: '1x gtceu:barium_dust', inFluid: null,
            outItems: ['1x gtceu:barium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_berkelium_dust', inItem: '1x gtceu:berkelium_dust', inFluid: null,
            outItems: ['1x gtceu:berkelium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_berkelium_fluid', inItem: null, inFluid: 'gtceu:berkelium 1000',
            outItems: ['1x gtceu:berkelium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_beryllium_dust', inItem: '1x gtceu:beryllium_dust', inFluid: null,
            outItems: ['1x gtceu:beryllium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_beryllium_fluid', inItem: null, inFluid: 'gtceu:beryllium 1000',
            outItems: ['1x gtceu:beryllium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bismuth_dust', inItem: '1x gtceu:bismuth_dust', inFluid: null,
            outItems: ['1x gtceu:bismuth_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bismuth_fluid', inItem: null, inFluid: 'gtceu:bismuth 1000',
            outItems: ['1x gtceu:bismuth_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bohrium_dust', inItem: '1x gtceu:bohrium_dust', inFluid: null,
            outItems: ['1x gtceu:bohrium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bohrium_fluid', inItem: null, inFluid: 'gtceu:bohrium 1000',
            outItems: ['1x gtceu:bohrium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_boron_dust', inItem: '1x gtceu:boron_dust', inFluid: null,
            outItems: ['1x gtceu:boron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bromine_fluid', inItem: null, inFluid: 'gtceu:bromine 1000',
            outItems: [],
            outFluids: ['gtceu:bromine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_caesium_dust', inItem: '1x gtceu:caesium_dust', inFluid: null,
            outItems: ['1x gtceu:caesium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_calcium_dust', inItem: '1x gtceu:calcium_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_calcium_fluid', inItem: null, inFluid: 'gtceu:calcium 1000',
            outItems: ['1x gtceu:calcium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_californium_dust', inItem: '1x gtceu:californium_dust', inFluid: null,
            outItems: ['1x gtceu:californium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_californium_fluid', inItem: null, inFluid: 'gtceu:californium 1000',
            outItems: ['1x gtceu:californium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_carbon_dust', inItem: '1x gtceu:carbon_dust', inFluid: null,
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_carbon_fluid', inItem: null, inFluid: 'gtceu:carbon 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cadmium_dust', inItem: '1x gtceu:cadmium_dust', inFluid: null,
            outItems: ['1x gtceu:cadmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cerium_dust', inItem: '1x gtceu:cerium_dust', inFluid: null,
            outItems: ['1x gtceu:cerium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cerium_fluid', inItem: null, inFluid: 'gtceu:cerium 1000',
            outItems: ['1x gtceu:cerium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chlorine_fluid', inItem: null, inFluid: 'gtceu:chlorine 1000',
            outItems: [],
            outFluids: ['gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chromium_dust', inItem: '1x gtceu:chromium_dust', inFluid: null,
            outItems: ['1x gtceu:chromium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chromium_fluid', inItem: null, inFluid: 'gtceu:chromium 1000',
            outItems: ['1x gtceu:chromium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cobalt_dust', inItem: '1x gtceu:cobalt_dust', inFluid: null,
            outItems: ['1x gtceu:cobalt_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cobalt_fluid', inItem: null, inFluid: 'gtceu:cobalt 1000',
            outItems: ['1x gtceu:cobalt_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_copernicium_dust', inItem: '1x gtceu:copernicium_dust', inFluid: null,
            outItems: ['1x gtceu:copernicium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_copernicium_fluid', inItem: null, inFluid: 'gtceu:copernicium 1000',
            outItems: ['1x gtceu:copernicium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_copper_dust', inItem: '1x gtceu:copper_dust', inFluid: null,
            outItems: ['1x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_copper_fluid', inItem: null, inFluid: 'gtceu:copper 1000',
            outItems: ['1x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_curium_dust', inItem: '1x gtceu:curium_dust', inFluid: null,
            outItems: ['1x gtceu:curium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_curium_fluid', inItem: null, inFluid: 'gtceu:curium 1000',
            outItems: ['1x gtceu:curium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_darmstadtium_dust', inItem: '1x gtceu:darmstadtium_dust', inFluid: null,
            outItems: ['1x gtceu:darmstadtium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_darmstadtium_fluid', inItem: null, inFluid: 'gtceu:darmstadtium 1000',
            outItems: ['1x gtceu:darmstadtium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_deuterium_fluid', inItem: null, inFluid: 'gtceu:deuterium 1000',
            outItems: [],
            outFluids: ['gtceu:deuterium 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dubnium_dust', inItem: '1x gtceu:dubnium_dust', inFluid: null,
            outItems: ['1x gtceu:dubnium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dubnium_fluid', inItem: null, inFluid: 'gtceu:dubnium 1000',
            outItems: ['1x gtceu:dubnium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dysprosium_dust', inItem: '1x gtceu:dysprosium_dust', inFluid: null,
            outItems: ['1x gtceu:dysprosium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dysprosium_fluid', inItem: null, inFluid: 'gtceu:dysprosium 1000',
            outItems: ['1x gtceu:dysprosium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_einsteinium_dust', inItem: '1x gtceu:einsteinium_dust', inFluid: null,
            outItems: ['1x gtceu:einsteinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_einsteinium_fluid', inItem: null, inFluid: 'gtceu:einsteinium 1000',
            outItems: ['1x gtceu:einsteinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_erbium_dust', inItem: '1x gtceu:erbium_dust', inFluid: null,
            outItems: ['1x gtceu:erbium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_erbium_fluid', inItem: null, inFluid: 'gtceu:erbium 1000',
            outItems: ['1x gtceu:erbium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_europium_dust', inItem: '1x gtceu:europium_dust', inFluid: null,
            outItems: ['1x gtceu:europium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_europium_fluid', inItem: null, inFluid: 'gtceu:europium 1000',
            outItems: ['1x gtceu:europium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fermium_dust', inItem: '1x gtceu:fermium_dust', inFluid: null,
            outItems: ['1x gtceu:fermium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fermium_fluid', inItem: null, inFluid: 'gtceu:fermium 1000',
            outItems: ['1x gtceu:fermium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_flerovium_dust', inItem: '1x gtceu:flerovium_dust', inFluid: null,
            outItems: ['1x gtceu:flerovium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_flerovium_fluid', inItem: null, inFluid: 'gtceu:flerovium 1000',
            outItems: ['1x gtceu:flerovium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluorine_fluid', inItem: null, inFluid: 'gtceu:fluorine 1000',
            outItems: [],
            outFluids: ['gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_francium_dust', inItem: '1x gtceu:francium_dust', inFluid: null,
            outItems: ['1x gtceu:francium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gadolinium_dust', inItem: '1x gtceu:gadolinium_dust', inFluid: null,
            outItems: ['1x gtceu:gadolinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gadolinium_fluid', inItem: null, inFluid: 'gtceu:gadolinium 1000',
            outItems: ['1x gtceu:gadolinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gallium_dust', inItem: '1x gtceu:gallium_dust', inFluid: null,
            outItems: ['1x gtceu:gallium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gallium_fluid', inItem: null, inFluid: 'gtceu:gallium 1000',
            outItems: ['1x gtceu:gallium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_germanium_dust', inItem: '1x gtceu:germanium_dust', inFluid: null,
            outItems: ['1x gtceu:germanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_germanium_fluid', inItem: null, inFluid: 'gtceu:germanium 1000',
            outItems: ['1x gtceu:germanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gold_dust', inItem: '1x gtceu:gold_dust', inFluid: null,
            outItems: ['1x gtceu:gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gold_fluid', inItem: null, inFluid: 'gtceu:gold 1000',
            outItems: ['1x gtceu:gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hafnium_dust', inItem: '1x gtceu:hafnium_dust', inFluid: null,
            outItems: ['1x gtceu:hafnium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hafnium_fluid', inItem: null, inFluid: 'gtceu:hafnium 1000',
            outItems: ['1x gtceu:hafnium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hassium_dust', inItem: '1x gtceu:hassium_dust', inFluid: null,
            outItems: ['1x gtceu:hassium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hassium_fluid', inItem: null, inFluid: 'gtceu:hassium 1000',
            outItems: ['1x gtceu:hassium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_holmium_dust', inItem: '1x gtceu:holmium_dust', inFluid: null,
            outItems: ['1x gtceu:holmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_holmium_fluid', inItem: null, inFluid: 'gtceu:holmium 1000',
            outItems: ['1x gtceu:holmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydrogen_fluid', inItem: null, inFluid: 'gtceu:hydrogen 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_helium_fluid', inItem: null, inFluid: 'gtceu:helium 1000',
            outItems: [],
            outFluids: ['gtceu:helium 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_helium_3_fluid', inItem: null, inFluid: 'gtceu:helium_3 1000',
            outItems: [],
            outFluids: ['gtceu:helium 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_indium_dust', inItem: '1x gtceu:indium_dust', inFluid: null,
            outItems: ['1x gtceu:indium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_indium_fluid', inItem: null, inFluid: 'gtceu:indium 1000',
            outItems: ['1x gtceu:indium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iodine_dust', inItem: '1x gtceu:iodine_dust', inFluid: null,
            outItems: ['1x gtceu:iodine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iridium_dust', inItem: '1x gtceu:iridium_dust', inFluid: null,
            outItems: ['1x gtceu:iridium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iridium_fluid', inItem: null, inFluid: 'gtceu:iridium 1000',
            outItems: ['1x gtceu:iridium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iron_dust', inItem: '1x gtceu:iron_dust', inFluid: null,
            outItems: ['1x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iron_fluid', inItem: null, inFluid: 'gtceu:iron 1000',
            outItems: ['1x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_krypton_fluid', inItem: null, inFluid: 'gtceu:krypton 1000',
            outItems: [],
            outFluids: ['gtceu:krypton 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lanthanum_dust', inItem: '1x gtceu:lanthanum_dust', inFluid: null,
            outItems: ['1x gtceu:lanthanum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lanthanum_fluid', inItem: null, inFluid: 'gtceu:lanthanum 1000',
            outItems: ['1x gtceu:lanthanum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lawrencium_dust', inItem: '1x gtceu:lawrencium_dust', inFluid: null,
            outItems: ['1x gtceu:lawrencium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lawrencium_fluid', inItem: null, inFluid: 'gtceu:lawrencium 1000',
            outItems: ['1x gtceu:lawrencium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lead_dust', inItem: '1x gtceu:lead_dust', inFluid: null,
            outItems: ['1x gtceu:lead_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lead_fluid', inItem: null, inFluid: 'gtceu:lead 1000',
            outItems: ['1x gtceu:lead_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithium_dust', inItem: '1x gtceu:lithium_dust', inFluid: null,
            outItems: ['1x gtceu:lithium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithium_fluid', inItem: null, inFluid: 'gtceu:lithium 1000',
            outItems: ['1x gtceu:lithium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_livermorium_dust', inItem: '1x gtceu:livermorium_dust', inFluid: null,
            outItems: ['1x gtceu:livermorium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_livermorium_fluid', inItem: null, inFluid: 'gtceu:livermorium 1000',
            outItems: ['1x gtceu:livermorium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lutetium_dust', inItem: '1x gtceu:lutetium_dust', inFluid: null,
            outItems: ['1x gtceu:lutetium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lutetium_fluid', inItem: null, inFluid: 'gtceu:lutetium 1000',
            outItems: ['1x gtceu:lutetium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnesium_dust', inItem: '1x gtceu:magnesium_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnesium_fluid', inItem: null, inFluid: 'gtceu:magnesium 1000',
            outItems: ['1x gtceu:magnesium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mendelevium_dust', inItem: '1x gtceu:mendelevium_dust', inFluid: null,
            outItems: ['1x gtceu:mendelevium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mendelevium_fluid', inItem: null, inFluid: 'gtceu:mendelevium 1000',
            outItems: ['1x gtceu:mendelevium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_manganese_dust', inItem: '1x gtceu:manganese_dust', inFluid: null,
            outItems: ['1x gtceu:manganese_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_manganese_fluid', inItem: null, inFluid: 'gtceu:manganese 1000',
            outItems: ['1x gtceu:manganese_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_meitnerium_dust', inItem: '1x gtceu:meitnerium_dust', inFluid: null,
            outItems: ['1x gtceu:meitnerium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_meitnerium_fluid', inItem: null, inFluid: 'gtceu:meitnerium 1000',
            outItems: ['1x gtceu:meitnerium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mercury_fluid', inItem: null, inFluid: 'gtceu:mercury 1000',
            outItems: [],
            outFluids: ['gtceu:mercury 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_molybdenum_dust', inItem: '1x gtceu:molybdenum_dust', inFluid: null,
            outItems: ['1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_molybdenum_fluid', inItem: null, inFluid: 'gtceu:molybdenum 1000',
            outItems: ['1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_moscovium_dust', inItem: '1x gtceu:moscovium_dust', inFluid: null,
            outItems: ['1x gtceu:moscovium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_moscovium_fluid', inItem: null, inFluid: 'gtceu:moscovium 1000',
            outItems: ['1x gtceu:moscovium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_neodymium_dust', inItem: '1x gtceu:neodymium_dust', inFluid: null,
            outItems: ['1x gtceu:neodymium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_neodymium_fluid', inItem: null, inFluid: 'gtceu:neodymium 1000',
            outItems: ['1x gtceu:neodymium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_neon_fluid', inItem: null, inFluid: 'gtceu:neon 1000',
            outItems: [],
            outFluids: ['gtceu:neon 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_neptunium_dust', inItem: '1x gtceu:neptunium_dust', inFluid: null,
            outItems: ['1x gtceu:neptunium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_neptunium_fluid', inItem: null, inFluid: 'gtceu:neptunium 1000',
            outItems: ['1x gtceu:neptunium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nickel_dust', inItem: '1x gtceu:nickel_dust', inFluid: null,
            outItems: ['1x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nickel_fluid', inItem: null, inFluid: 'gtceu:nickel 1000',
            outItems: ['1x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nihonium_dust', inItem: '1x gtceu:nihonium_dust', inFluid: null,
            outItems: ['1x gtceu:nihonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nihonium_fluid', inItem: null, inFluid: 'gtceu:nihonium 1000',
            outItems: ['1x gtceu:nihonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_niobium_dust', inItem: '1x gtceu:niobium_dust', inFluid: null,
            outItems: ['1x gtceu:niobium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_niobium_fluid', inItem: null, inFluid: 'gtceu:niobium 1000',
            outItems: ['1x gtceu:niobium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrogen_fluid', inItem: null, inFluid: 'gtceu:nitrogen 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nobelium_dust', inItem: '1x gtceu:nobelium_dust', inFluid: null,
            outItems: ['1x gtceu:nobelium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nobelium_fluid', inItem: null, inFluid: 'gtceu:nobelium 1000',
            outItems: ['1x gtceu:nobelium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_oganesson_dust', inItem: '1x gtceu:oganesson_dust', inFluid: null,
            outItems: ['1x gtceu:oganesson_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_oganesson_fluid', inItem: null, inFluid: 'gtceu:oganesson 1000',
            outItems: ['1x gtceu:oganesson_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_osmium_dust', inItem: '1x gtceu:osmium_dust', inFluid: null,
            outItems: ['1x gtceu:osmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_osmium_fluid', inItem: null, inFluid: 'gtceu:osmium 1000',
            outItems: ['1x gtceu:osmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_oxygen_fluid', inItem: null, inFluid: 'gtceu:oxygen 1000',
            outItems: [],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_palladium_dust', inItem: '1x gtceu:palladium_dust', inFluid: null,
            outItems: ['1x gtceu:palladium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_palladium_fluid', inItem: null, inFluid: 'gtceu:palladium 1000',
            outItems: ['1x gtceu:palladium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phosphorus_dust', inItem: '1x gtceu:phosphorus_dust', inFluid: null,
            outItems: ['1x gtceu:phosphorus_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phosphorus_fluid', inItem: null, inFluid: 'gtceu:phosphorus 1000',
            outItems: ['1x gtceu:phosphorus_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polonium_dust', inItem: '1x gtceu:polonium_dust', inFluid: null,
            outItems: ['1x gtceu:polonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polonium_fluid', inItem: null, inFluid: 'gtceu:polonium 1000',
            outItems: ['1x gtceu:polonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_platinum_dust', inItem: '1x gtceu:platinum_dust', inFluid: null,
            outItems: ['1x gtceu:platinum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_platinum_fluid', inItem: null, inFluid: 'gtceu:platinum 1000',
            outItems: ['1x gtceu:platinum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_plutonium_dust', inItem: '1x gtceu:plutonium_dust', inFluid: null,
            outItems: ['1x gtceu:plutonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_plutonium_fluid', inItem: null, inFluid: 'gtceu:plutonium 1000',
            outItems: ['1x gtceu:plutonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_plutonium_241_dust', inItem: '1x gtceu:plutonium_241_dust', inFluid: null,
            outItems: ['1x gtceu:plutonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_plutonium_241_fluid', inItem: null, inFluid: 'gtceu:plutonium_241 1000',
            outItems: ['1x gtceu:plutonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_dust', inItem: '1x gtceu:potassium_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_fluid', inItem: null, inFluid: 'gtceu:potassium 1000',
            outItems: ['1x gtceu:potassium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_praseodymium_dust', inItem: '1x gtceu:praseodymium_dust', inFluid: null,
            outItems: ['1x gtceu:praseodymium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_praseodymium_fluid', inItem: null, inFluid: 'gtceu:praseodymium 1000',
            outItems: ['1x gtceu:praseodymium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_promethium_dust', inItem: '1x gtceu:promethium_dust', inFluid: null,
            outItems: ['1x gtceu:promethium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_promethium_fluid', inItem: null, inFluid: 'gtceu:promethium 1000',
            outItems: ['1x gtceu:promethium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_protactinium_dust', inItem: '1x gtceu:protactinium_dust', inFluid: null,
            outItems: ['1x gtceu:protactinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_protactinium_fluid', inItem: null, inFluid: 'gtceu:protactinium 1000',
            outItems: ['1x gtceu:protactinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_radon_fluid', inItem: null, inFluid: 'gtceu:radon 1000',
            outItems: [],
            outFluids: ['gtceu:radon 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_radium_dust', inItem: '1x gtceu:radium_dust', inFluid: null,
            outItems: ['1x gtceu:radium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_radium_fluid', inItem: null, inFluid: 'gtceu:radium 1000',
            outItems: ['1x gtceu:radium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhenium_dust', inItem: '1x gtceu:rhenium_dust', inFluid: null,
            outItems: ['1x gtceu:rhenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhenium_fluid', inItem: null, inFluid: 'gtceu:rhenium 1000',
            outItems: ['1x gtceu:rhenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhodium_dust', inItem: '1x gtceu:rhodium_dust', inFluid: null,
            outItems: ['1x gtceu:rhodium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhodium_fluid', inItem: null, inFluid: 'gtceu:rhodium 1000',
            outItems: ['1x gtceu:rhodium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_roentgenium_dust', inItem: '1x gtceu:roentgenium_dust', inFluid: null,
            outItems: ['1x gtceu:roentgenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_roentgenium_fluid', inItem: null, inFluid: 'gtceu:roentgenium 1000',
            outItems: ['1x gtceu:roentgenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rubidium_dust', inItem: '1x gtceu:rubidium_dust', inFluid: null,
            outItems: ['1x gtceu:rubidium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rubidium_fluid', inItem: null, inFluid: 'gtceu:rubidium 1000',
            outItems: ['1x gtceu:rubidium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ruthenium_dust', inItem: '1x gtceu:ruthenium_dust', inFluid: null,
            outItems: ['1x gtceu:ruthenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ruthenium_fluid', inItem: null, inFluid: 'gtceu:ruthenium 1000',
            outItems: ['1x gtceu:ruthenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rutherfordium_dust', inItem: '1x gtceu:rutherfordium_dust', inFluid: null,
            outItems: ['1x gtceu:rutherfordium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_dust', inItem: '1x gtceu:samarium_dust', inFluid: null,
            outItems: ['1x gtceu:samarium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_fluid', inItem: null, inFluid: 'gtceu:samarium 1000',
            outItems: ['1x gtceu:samarium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_scandium_dust', inItem: '1x gtceu:scandium_dust', inFluid: null,
            outItems: ['1x gtceu:scandium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_scandium_fluid', inItem: null, inFluid: 'gtceu:scandium 1000',
            outItems: ['1x gtceu:scandium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_seaborgium_dust', inItem: '1x gtceu:seaborgium_dust', inFluid: null,
            outItems: ['1x gtceu:seaborgium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_seaborgium_fluid', inItem: null, inFluid: 'gtceu:seaborgium 1000',
            outItems: ['1x gtceu:seaborgium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_selenium_dust', inItem: '1x gtceu:selenium_dust', inFluid: null,
            outItems: ['1x gtceu:selenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silicon_dust', inItem: '1x gtceu:silicon_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silicon_fluid', inItem: null, inFluid: 'gtceu:silicon 1000',
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silver_dust', inItem: '1x gtceu:silver_dust', inFluid: null,
            outItems: ['1x gtceu:silver_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silver_fluid', inItem: null, inFluid: 'gtceu:silver 1000',
            outItems: ['1x gtceu:silver_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_dust', inItem: '1x gtceu:sodium_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_fluid', inItem: null, inFluid: 'gtceu:sodium 1000',
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_strontium_dust', inItem: '1x gtceu:strontium_dust', inFluid: null,
            outItems: ['1x gtceu:strontium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sulfur_dust', inItem: '1x gtceu:sulfur_dust', inFluid: null,
            outItems: ['1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tantalum_dust', inItem: '1x gtceu:tantalum_dust', inFluid: null,
            outItems: ['1x gtceu:tantalum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tantalum_fluid', inItem: null, inFluid: 'gtceu:tantalum 1000',
            outItems: ['1x gtceu:tantalum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_technetium_dust', inItem: '1x gtceu:technetium_dust', inFluid: null,
            outItems: ['1x gtceu:technetium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_technetium_fluid', inItem: null, inFluid: 'gtceu:technetium 1000',
            outItems: ['1x gtceu:technetium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tellurium_dust', inItem: '1x gtceu:tellurium_dust', inFluid: null,
            outItems: ['1x gtceu:tellurium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tennessine_dust', inItem: '1x gtceu:tennessine_dust', inFluid: null,
            outItems: ['1x gtceu:tennessine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tennessine_fluid', inItem: null, inFluid: 'gtceu:tennessine 1000',
            outItems: ['1x gtceu:tennessine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_terbium_dust', inItem: '1x gtceu:terbium_dust', inFluid: null,
            outItems: ['1x gtceu:terbium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_terbium_fluid', inItem: null, inFluid: 'gtceu:terbium 1000',
            outItems: ['1x gtceu:terbium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thorium_dust', inItem: '1x gtceu:thorium_dust', inFluid: null,
            outItems: ['1x gtceu:thorium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thorium_fluid', inItem: null, inFluid: 'gtceu:thorium 1000',
            outItems: ['1x gtceu:thorium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thallium_dust', inItem: '1x gtceu:thallium_dust', inFluid: null,
            outItems: ['1x gtceu:thallium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thulium_dust', inItem: '1x gtceu:thulium_dust', inFluid: null,
            outItems: ['1x gtceu:thulium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thulium_fluid', inItem: null, inFluid: 'gtceu:thulium 1000',
            outItems: ['1x gtceu:thulium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tin_dust', inItem: '1x gtceu:tin_dust', inFluid: null,
            outItems: ['1x gtceu:tin_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tin_fluid', inItem: null, inFluid: 'gtceu:tin 1000',
            outItems: ['1x gtceu:tin_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_dust', inItem: '1x gtceu:titanium_dust', inFluid: null,
            outItems: ['1x gtceu:titanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_fluid', inItem: null, inFluid: 'gtceu:titanium 1000',
            outItems: ['1x gtceu:titanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tritium_fluid', inItem: null, inFluid: 'gtceu:tritium 1000',
            outItems: [],
            outFluids: ['gtceu:tritium 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tungsten_dust', inItem: '1x gtceu:tungsten_dust', inFluid: null,
            outItems: ['1x gtceu:tungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tungsten_fluid', inItem: null, inFluid: 'gtceu:tungsten 1000',
            outItems: ['1x gtceu:tungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uranium_dust', inItem: '1x gtceu:uranium_dust', inFluid: null,
            outItems: ['1x gtceu:uranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uranium_fluid', inItem: null, inFluid: 'gtceu:uranium 1000',
            outItems: ['1x gtceu:uranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uranium_235_dust', inItem: '1x gtceu:uranium_235_dust', inFluid: null,
            outItems: ['1x gtceu:uranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uranium_235_fluid', inItem: null, inFluid: 'gtceu:uranium_235 1000',
            outItems: ['1x gtceu:uranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vanadium_dust', inItem: '1x gtceu:vanadium_dust', inFluid: null,
            outItems: ['1x gtceu:vanadium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vanadium_fluid', inItem: null, inFluid: 'gtceu:vanadium 1000',
            outItems: ['1x gtceu:vanadium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_xenon_fluid', inItem: null, inFluid: 'gtceu:xenon 1000',
            outItems: [],
            outFluids: ['gtceu:xenon 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ytterbium_dust', inItem: '1x gtceu:ytterbium_dust', inFluid: null,
            outItems: ['1x gtceu:ytterbium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ytterbium_fluid', inItem: null, inFluid: 'gtceu:ytterbium 1000',
            outItems: ['1x gtceu:ytterbium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_yttrium_dust', inItem: '1x gtceu:yttrium_dust', inFluid: null,
            outItems: ['1x gtceu:yttrium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_yttrium_fluid', inItem: null, inFluid: 'gtceu:yttrium 1000',
            outItems: ['1x gtceu:yttrium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zinc_dust', inItem: '1x gtceu:zinc_dust', inFluid: null,
            outItems: ['1x gtceu:zinc_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zinc_fluid', inItem: null, inFluid: 'gtceu:zinc 1000',
            outItems: ['1x gtceu:zinc_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zirconium_dust', inItem: '1x gtceu:zirconium_dust', inFluid: null,
            outItems: ['1x gtceu:zirconium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadah_dust', inItem: '1x gtceu:naquadah_dust', inFluid: null,
            outItems: ['1x gtceu:naquadah_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadah_fluid', inItem: null, inFluid: 'gtceu:naquadah 1000',
            outItems: ['1x gtceu:naquadah_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enriched_naquadah_dust', inItem: '1x gtceu:enriched_naquadah_dust', inFluid: null,
            outItems: ['1x gtceu:enriched_naquadah_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enriched_naquadah_fluid', inItem: null, inFluid: 'gtceu:enriched_naquadah 1000',
            outItems: ['1x gtceu:enriched_naquadah_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadria_dust', inItem: '1x gtceu:naquadria_dust', inFluid: null,
            outItems: ['1x gtceu:naquadria_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadria_fluid', inItem: null, inFluid: 'gtceu:naquadria 1000',
            outItems: ['1x gtceu:naquadria_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_neutronium_dust', inItem: '1x gtceu:neutronium_dust', inFluid: null,
            outItems: ['1x gtceu:neutronium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_neutronium_fluid', inItem: null, inFluid: 'gtceu:neutronium 1000',
            outItems: ['1x gtceu:neutronium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tritanium_dust', inItem: '1x gtceu:tritanium_dust', inFluid: null,
            outItems: ['1x gtceu:tritanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tritanium_fluid', inItem: null, inFluid: 'gtceu:tritanium 1000',
            outItems: ['1x gtceu:tritanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_duranium_dust', inItem: '1x gtceu:duranium_dust', inFluid: null,
            outItems: ['1x gtceu:duranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_duranium_fluid', inItem: null, inFluid: 'gtceu:duranium 1000',
            outItems: ['1x gtceu:duranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trinium_dust', inItem: '1x gtceu:trinium_dust', inFluid: null,
            outItems: ['1x gtceu:trinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trinium_fluid', inItem: null, inFluid: 'gtceu:trinium 1000',
            outItems: ['1x gtceu:trinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_almandine_dust', inItem: '1x gtceu:almandine_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust', '3x gtceu:iron_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_andradite_dust', inItem: '1x gtceu:andradite_dust', inFluid: null,
            outItems: ['3x gtceu:calcium_dust', '2x gtceu:iron_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_annealed_copper_dust', inItem: '1x gtceu:annealed_copper_dust', inFluid: null,
            outItems: ['1x gtceu:annealed_copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_annealed_copper_fluid', inItem: null, inFluid: 'gtceu:annealed_copper 1000',
            outItems: ['1x gtceu:annealed_copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_asbestos_dust', inItem: '1x gtceu:asbestos_dust', inFluid: null,
            outItems: ['3x gtceu:magnesium_dust', '2x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ash_dust', inItem: '1x gtceu:ash_dust', inFluid: null,
            outItems: ['1x gtceu:ash_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hematite_dust', inItem: '1x gtceu:hematite_dust', inFluid: null,
            outItems: ['2x gtceu:iron_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_battery_alloy_dust', inItem: '1x gtceu:battery_alloy_dust', inFluid: null,
            outItems: ['4x gtceu:lead_dust', '1x gtceu:antimony_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_battery_alloy_fluid', inItem: null, inFluid: 'gtceu:battery_alloy 1000',
            outItems: ['4x gtceu:lead_dust', '1x gtceu:antimony_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_blue_topaz_dust', inItem: '1x gtceu:blue_topaz_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:fluorine 2000', 'gtceu:hydrogen 2000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bone_dust', inItem: '1x gtceu:bone_dust', inFluid: null,
            outItems: ['3x gtceu:calcium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_brass_dust', inItem: '1x gtceu:brass_dust', inFluid: null,
            outItems: ['1x gtceu:zinc_dust', '3x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_brass_fluid', inItem: null, inFluid: 'gtceu:brass 1000',
            outItems: ['1x gtceu:zinc_dust', '3x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bronze_dust', inItem: '1x gtceu:bronze_dust', inFluid: null,
            outItems: ['1x gtceu:tin_dust', '3x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bronze_fluid', inItem: null, inFluid: 'gtceu:bronze 1000',
            outItems: ['1x gtceu:tin_dust', '3x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_goethite_dust', inItem: '1x gtceu:goethite_dust', inFluid: null,
            outItems: ['1x gtceu:iron_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_calcite_dust', inItem: '1x gtceu:calcite_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cassiterite_dust', inItem: '1x gtceu:cassiterite_dust', inFluid: null,
            outItems: ['1x gtceu:tin_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cassiterite_sand_dust', inItem: '1x gtceu:cassiterite_sand_dust', inFluid: null,
            outItems: ['1x gtceu:tin_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chalcopyrite_dust', inItem: '1x gtceu:chalcopyrite_dust', inFluid: null,
            outItems: ['1x gtceu:copper_dust', '1x gtceu:iron_dust', '2x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_charcoal_dust', inItem: '1x gtceu:charcoal_dust', inFluid: null,
            outItems: ['1x gtceu:charcoal_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chromite_dust', inItem: '1x gtceu:chromite_dust', inFluid: null,
            outItems: ['1x gtceu:iron_dust', '2x gtceu:chromium_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cinnabar_dust', inItem: '1x gtceu:cinnabar_dust', inFluid: null,
            outItems: ['1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:mercury 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_water_fluid', inItem: null, inFluid: 'minecraft:water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_coal_dust', inItem: '1x gtceu:coal_dust', inFluid: null,
            outItems: ['1x gtceu:coal_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cobaltite_dust', inItem: '1x gtceu:cobaltite_dust', inFluid: null,
            outItems: ['1x gtceu:cobalt_dust', '1x gtceu:arsenic_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cooperite_dust', inItem: '1x gtceu:cooperite_dust', inFluid: null,
            outItems: ['3x gtceu:platinum_dust', '1x gtceu:nickel_dust', '1x gtceu:sulfur_dust', '1x gtceu:palladium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cupronickel_dust', inItem: '1x gtceu:cupronickel_dust', inFluid: null,
            outItems: ['1x gtceu:copper_dust', '1x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cupronickel_fluid', inItem: null, inFluid: 'gtceu:cupronickel 1000',
            outItems: ['1x gtceu:copper_dust', '1x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dark_ash_dust', inItem: '1x gtceu:dark_ash_dust', inFluid: null,
            outItems: ['1x gtceu:dark_ash_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diamond_dust', inItem: '1x gtceu:diamond_dust', inFluid: null,
            outItems: ['1x gtceu:diamond_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_electrum_dust', inItem: '1x gtceu:electrum_dust', inFluid: null,
            outItems: ['1x gtceu:silver_dust', '1x gtceu:gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_electrum_fluid', inItem: null, inFluid: 'gtceu:electrum 1000',
            outItems: ['1x gtceu:silver_dust', '1x gtceu:gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_emerald_dust', inItem: '1x gtceu:emerald_dust', inFluid: null,
            outItems: ['3x gtceu:beryllium_dust', '2x gtceu:aluminium_dust', '6x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 18000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_galena_dust', inItem: '1x gtceu:galena_dust', inFluid: null,
            outItems: ['1x gtceu:lead_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_garnierite_dust', inItem: '1x gtceu:garnierite_dust', inFluid: null,
            outItems: ['1x gtceu:nickel_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_green_sapphire_dust', inItem: '1x gtceu:green_sapphire_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grossular_dust', inItem: '1x gtceu:grossular_dust', inFluid: null,
            outItems: ['3x gtceu:calcium_dust', '2x gtceu:aluminium_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ice_dust', inItem: '1x gtceu:ice_dust', inFluid: null,
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ice_fluid', inItem: null, inFluid: 'gtceu:ice 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ilmenite_dust', inItem: '1x gtceu:ilmenite_dust', inFluid: null,
            outItems: ['1x gtceu:iron_dust', '1x gtceu:titanium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rutile_dust', inItem: '1x gtceu:rutile_dust', inFluid: null,
            outItems: ['1x gtceu:titanium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bauxite_dust', inItem: '1x gtceu:bauxite_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_invar_dust', inItem: '1x gtceu:invar_dust', inFluid: null,
            outItems: ['2x gtceu:iron_dust', '1x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_invar_fluid', inItem: null, inFluid: 'gtceu:invar 1000',
            outItems: ['2x gtceu:iron_dust', '1x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_kanthal_dust', inItem: '1x gtceu:kanthal_dust', inFluid: null,
            outItems: ['1x gtceu:iron_dust', '1x gtceu:aluminium_dust', '1x gtceu:chromium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_kanthal_fluid', inItem: null, inFluid: 'gtceu:kanthal 1000',
            outItems: ['1x gtceu:iron_dust', '1x gtceu:aluminium_dust', '1x gtceu:chromium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lazurite_dust', inItem: '1x gtceu:lazurite_dust', inFluid: null,
            outItems: ['6x gtceu:aluminium_dust', '6x gtceu:silicon_dust', '8x gtceu:calcium_dust', '8x gtceu:sodium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnalium_dust', inItem: '1x gtceu:magnalium_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust', '2x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnalium_fluid', inItem: null, inFluid: 'gtceu:magnalium 1000',
            outItems: ['1x gtceu:magnesium_dust', '2x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnesite_dust', inItem: '1x gtceu:magnesite_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnetite_dust', inItem: '1x gtceu:magnetite_dust', inFluid: null,
            outItems: ['3x gtceu:iron_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_molybdenite_dust', inItem: '1x gtceu:molybdenite_dust', inFluid: null,
            outItems: ['1x gtceu:molybdenum_dust', '2x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nichrome_dust', inItem: '1x gtceu:nichrome_dust', inFluid: null,
            outItems: ['4x gtceu:nickel_dust', '1x gtceu:chromium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nichrome_fluid', inItem: null, inFluid: 'gtceu:nichrome 1000',
            outItems: ['4x gtceu:nickel_dust', '1x gtceu:chromium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_niobium_nitride_dust', inItem: '1x gtceu:niobium_nitride_dust', inFluid: null,
            outItems: ['1x gtceu:niobium_dust'],
            outFluids: ['gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_niobium_nitride_fluid', inItem: null, inFluid: 'gtceu:niobium_nitride 1000',
            outItems: ['1x gtceu:niobium_dust'],
            outFluids: ['gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_niobium_titanium_dust', inItem: '1x gtceu:niobium_titanium_dust', inFluid: null,
            outItems: ['1x gtceu:niobium_dust', '1x gtceu:titanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_niobium_titanium_fluid', inItem: null, inFluid: 'gtceu:niobium_titanium 1000',
            outItems: ['1x gtceu:niobium_dust', '1x gtceu:titanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_obsidian_dust', inItem: '1x gtceu:obsidian_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust', '1x gtceu:iron_dust', '2x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phosphate_dust', inItem: '1x gtceu:phosphate_dust', inFluid: null,
            outItems: ['1x gtceu:phosphorus_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_platinum_raw_dust', inItem: '1x gtceu:platinum_raw_dust', inFluid: null,
            outItems: ['1x gtceu:platinum_dust'],
            outFluids: ['gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sterling_silver_dust', inItem: '1x gtceu:sterling_silver_dust', inFluid: null,
            outItems: ['1x gtceu:copper_dust', '4x gtceu:silver_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sterling_silver_fluid', inItem: null, inFluid: 'gtceu:sterling_silver 1000',
            outItems: ['1x gtceu:copper_dust', '4x gtceu:silver_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rose_gold_dust', inItem: '1x gtceu:rose_gold_dust', inFluid: null,
            outItems: ['1x gtceu:copper_dust', '4x gtceu:gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rose_gold_fluid', inItem: null, inFluid: 'gtceu:rose_gold 1000',
            outItems: ['1x gtceu:copper_dust', '4x gtceu:gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_black_bronze_dust', inItem: '1x gtceu:black_bronze_dust', inFluid: null,
            outItems: ['1x gtceu:gold_dust', '1x gtceu:silver_dust', '3x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_black_bronze_fluid', inItem: null, inFluid: 'gtceu:black_bronze 1000',
            outItems: ['1x gtceu:gold_dust', '1x gtceu:silver_dust', '3x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bismuth_bronze_dust', inItem: '1x gtceu:bismuth_bronze_dust', inFluid: null,
            outItems: ['1x gtceu:bismuth_dust', '1x gtceu:zinc_dust', '3x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bismuth_bronze_fluid', inItem: null, inFluid: 'gtceu:bismuth_bronze 1000',
            outItems: ['1x gtceu:bismuth_dust', '1x gtceu:zinc_dust', '3x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_biotite_dust', inItem: '1x gtceu:biotite_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust', '3x gtceu:magnesium_dust', '3x gtceu:aluminium_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:fluorine 2000', 'gtceu:oxygen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_powellite_dust', inItem: '1x gtceu:powellite_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust', '1x gtceu:molybdenum_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pyrite_dust', inItem: '1x gtceu:pyrite_dust', inFluid: null,
            outItems: ['1x gtceu:iron_dust', '2x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pyrolusite_dust', inItem: '1x gtceu:pyrolusite_dust', inFluid: null,
            outItems: ['1x gtceu:manganese_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pyrope_dust', inItem: '1x gtceu:pyrope_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust', '3x gtceu:magnesium_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rock_salt_dust', inItem: '1x gtceu:rock_salt_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust'],
            outFluids: ['gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rtm_alloy_dust', inItem: '1x gtceu:rtm_alloy_dust', inFluid: null,
            outItems: ['4x gtceu:ruthenium_dust', '2x gtceu:tungsten_dust', '1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rtm_alloy_fluid', inItem: null, inFluid: 'gtceu:rtm_alloy 1000',
            outItems: ['4x gtceu:ruthenium_dust', '2x gtceu:tungsten_dust', '1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ruridit_dust', inItem: '1x gtceu:ruridit_dust', inFluid: null,
            outItems: ['2x gtceu:ruthenium_dust', '1x gtceu:iridium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ruby_dust', inItem: '1x gtceu:ruby_dust', inFluid: null,
            outItems: ['1x gtceu:chromium_dust', '2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_salt_dust', inItem: '1x gtceu:salt_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_saltpeter_dust', inItem: '1x gtceu:saltpeter_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sapphire_dust', inItem: '1x gtceu:sapphire_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_scheelite_dust', inItem: '1x gtceu:scheelite_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust', '1x gtceu:tungsten_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodalite_dust', inItem: '1x gtceu:sodalite_dust', inFluid: null,
            outItems: ['3x gtceu:aluminium_dust', '3x gtceu:silicon_dust', '4x gtceu:sodium_dust'],
            outFluids: ['gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aluminium_sulfite_dust', inItem: '1x gtceu:aluminium_sulfite_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust', '3x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tantalite_dust', inItem: '1x gtceu:tantalite_dust', inFluid: null,
            outItems: ['1x gtceu:manganese_dust', '2x gtceu:tantalum_dust'],
            outFluids: ['gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_coke_dust', inItem: '1x gtceu:coke_dust', inFluid: null,
            outItems: ['1x gtceu:coke_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_soldering_alloy_dust', inItem: '1x gtceu:soldering_alloy_dust', inFluid: null,
            outItems: ['6x gtceu:tin_dust', '3x gtceu:lead_dust', '1x gtceu:antimony_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_soldering_alloy_fluid', inItem: null, inFluid: 'gtceu:soldering_alloy 1000',
            outItems: ['6x gtceu:tin_dust', '3x gtceu:lead_dust', '1x gtceu:antimony_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_spessartine_dust', inItem: '1x gtceu:spessartine_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust', '3x gtceu:manganese_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sphalerite_dust', inItem: '1x gtceu:sphalerite_dust', inFluid: null,
            outItems: ['1x gtceu:zinc_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_stainless_steel_dust', inItem: '1x gtceu:stainless_steel_dust', inFluid: null,
            outItems: ['6x gtceu:iron_dust', '1x gtceu:chromium_dust', '1x gtceu:manganese_dust', '1x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_stainless_steel_fluid', inItem: null, inFluid: 'gtceu:stainless_steel 1000',
            outItems: ['6x gtceu:iron_dust', '1x gtceu:chromium_dust', '1x gtceu:manganese_dust', '1x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_steel_dust', inItem: '1x gtceu:steel_dust', inFluid: null,
            outItems: ['1x gtceu:steel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_steel_fluid', inItem: null, inFluid: 'gtceu:steel 1000',
            outItems: ['1x gtceu:steel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_stibnite_dust', inItem: '1x gtceu:stibnite_dust', inFluid: null,
            outItems: ['2x gtceu:antimony_dust', '3x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tetrahedrite_dust', inItem: '1x gtceu:tetrahedrite_dust', inFluid: null,
            outItems: ['3x gtceu:copper_dust', '1x gtceu:antimony_dust', '3x gtceu:sulfur_dust', '1x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tin_alloy_dust', inItem: '1x gtceu:tin_alloy_dust', inFluid: null,
            outItems: ['1x gtceu:tin_dust', '1x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tin_alloy_fluid', inItem: null, inFluid: 'gtceu:tin_alloy 1000',
            outItems: ['1x gtceu:tin_dust', '1x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_topaz_dust', inItem: '1x gtceu:topaz_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:fluorine 1000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tungstate_dust', inItem: '1x gtceu:tungstate_dust', inFluid: null,
            outItems: ['2x gtceu:lithium_dust', '1x gtceu:tungsten_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ultimet_dust', inItem: '1x gtceu:ultimet_dust', inFluid: null,
            outItems: ['5x gtceu:cobalt_dust', '2x gtceu:chromium_dust', '1x gtceu:nickel_dust', '1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ultimet_fluid', inItem: null, inFluid: 'gtceu:ultimet 1000',
            outItems: ['5x gtceu:cobalt_dust', '2x gtceu:chromium_dust', '1x gtceu:nickel_dust', '1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uraninite_dust', inItem: '1x gtceu:uraninite_dust', inFluid: null,
            outItems: ['1x gtceu:uranium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uvarovite_dust', inItem: '1x gtceu:uvarovite_dust', inFluid: null,
            outItems: ['3x gtceu:calcium_dust', '2x gtceu:chromium_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vanadium_gallium_dust', inItem: '1x gtceu:vanadium_gallium_dust', inFluid: null,
            outItems: ['3x gtceu:vanadium_dust', '1x gtceu:gallium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vanadium_gallium_fluid', inItem: null, inFluid: 'gtceu:vanadium_gallium 1000',
            outItems: ['3x gtceu:vanadium_dust', '1x gtceu:gallium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_wrought_iron_dust', inItem: '1x gtceu:wrought_iron_dust', inFluid: null,
            outItems: ['1x gtceu:wrought_iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_wrought_iron_fluid', inItem: null, inFluid: 'gtceu:wrought_iron 1000',
            outItems: ['1x gtceu:wrought_iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_wulfenite_dust', inItem: '1x gtceu:wulfenite_dust', inFluid: null,
            outItems: ['1x gtceu:lead_dust', '1x gtceu:molybdenum_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_yellow_limonite_dust', inItem: '1x gtceu:yellow_limonite_dust', inFluid: null,
            outItems: ['1x gtceu:iron_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_yttrium_barium_cuprate_dust', inItem: '1x gtceu:yttrium_barium_cuprate_dust', inFluid: null,
            outItems: ['1x gtceu:yttrium_dust', '2x gtceu:barium_dust', '3x gtceu:copper_dust'],
            outFluids: ['gtceu:oxygen 7000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_yttrium_barium_cuprate_fluid', inItem: null, inFluid: 'gtceu:yttrium_barium_cuprate 1000',
            outItems: ['1x gtceu:yttrium_dust', '2x gtceu:barium_dust', '3x gtceu:copper_dust'],
            outFluids: ['gtceu:oxygen 7000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nether_quartz_dust', inItem: '1x gtceu:nether_quartz_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_certus_quartz_dust', inItem: '1x gtceu:certus_quartz_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_quartzite_dust', inItem: '1x gtceu:quartzite_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_graphite_dust', inItem: '1x gtceu:graphite_dust', inFluid: null,
            outItems: ['1x gtceu:graphite_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_graphene_dust', inItem: '1x gtceu:graphene_dust', inFluid: null,
            outItems: ['1x gtceu:graphene_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tungstic_acid_dust', inItem: '1x gtceu:tungstic_acid_dust', inFluid: null,
            outItems: ['1x gtceu:tungsten_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_osmiridium_dust', inItem: '1x gtceu:osmiridium_dust', inFluid: null,
            outItems: ['3x gtceu:iridium_dust', '1x gtceu:osmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_osmiridium_fluid', inItem: null, inFluid: 'gtceu:osmiridium 1000',
            outItems: ['3x gtceu:iridium_dust', '1x gtceu:osmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithium_chloride_dust', inItem: '1x gtceu:lithium_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:lithium_dust'],
            outFluids: ['gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_calcium_chloride_dust', inItem: '1x gtceu:calcium_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust'],
            outFluids: ['gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bornite_dust', inItem: '1x gtceu:bornite_dust', inFluid: null,
            outItems: ['5x gtceu:copper_dust', '1x gtceu:iron_dust', '4x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chalcocite_dust', inItem: '1x gtceu:chalcocite_dust', inFluid: null,
            outItems: ['2x gtceu:copper_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gallium_arsenide_dust', inItem: '1x gtceu:gallium_arsenide_dust', inFluid: null,
            outItems: ['1x gtceu:arsenic_dust', '1x gtceu:gallium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gallium_arsenide_fluid', inItem: null, inFluid: 'gtceu:gallium_arsenide 1000',
            outItems: ['1x gtceu:arsenic_dust', '1x gtceu:gallium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potash_dust', inItem: '1x gtceu:potash_dust', inFluid: null,
            outItems: ['2x gtceu:potassium_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_soda_ash_dust', inItem: '1x gtceu:soda_ash_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_indium_gallium_phosphide_dust', inItem: '1x gtceu:indium_gallium_phosphide_dust', inFluid: null,
            outItems: ['1x gtceu:indium_dust', '1x gtceu:gallium_dust', '1x gtceu:phosphorus_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_indium_gallium_phosphide_fluid', inItem: null, inFluid: 'gtceu:indium_gallium_phosphide 1000',
            outItems: ['1x gtceu:indium_dust', '1x gtceu:gallium_dust', '1x gtceu:phosphorus_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nickel_zinc_ferrite_dust', inItem: '1x gtceu:nickel_zinc_ferrite_dust', inFluid: null,
            outItems: ['1x gtceu:nickel_dust', '1x gtceu:zinc_dust', '4x gtceu:iron_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nickel_zinc_ferrite_fluid', inItem: null, inFluid: 'gtceu:nickel_zinc_ferrite 1000',
            outItems: ['1x gtceu:nickel_dust', '1x gtceu:zinc_dust', '4x gtceu:iron_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silicon_dioxide_dust', inItem: '1x gtceu:silicon_dioxide_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnesium_chloride_dust', inItem: '1x gtceu:magnesium_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust'],
            outFluids: ['gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_sulfide_dust', inItem: '1x gtceu:sodium_sulfide_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phosphorus_pentoxide_dust', inItem: '1x gtceu:phosphorus_pentoxide_dust', inFluid: null,
            outItems: ['4x gtceu:phosphorus_dust'],
            outFluids: ['gtceu:oxygen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_quicklime_dust', inItem: '1x gtceu:quicklime_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_bisulfate_dust', inItem: '1x gtceu:sodium_bisulfate_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ferrite_mixture_dust', inItem: '1x gtceu:ferrite_mixture_dust', inFluid: null,
            outItems: ['1x gtceu:nickel_dust', '1x gtceu:zinc_dust', '4x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnesia_dust', inItem: '1x gtceu:magnesia_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_realgar_dust', inItem: '1x gtceu:realgar_dust', inFluid: null,
            outItems: ['4x gtceu:arsenic_dust', '4x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_bicarbonate_dust', inItem: '1x gtceu:sodium_bicarbonate_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_dichromate_dust', inItem: '1x gtceu:potassium_dichromate_dust', inFluid: null,
            outItems: ['2x gtceu:potassium_dust', '2x gtceu:chromium_dust'],
            outFluids: ['gtceu:oxygen 7000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chromium_trioxide_dust', inItem: '1x gtceu:chromium_trioxide_dust', inFluid: null,
            outItems: ['1x gtceu:chromium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_antimony_trioxide_dust', inItem: '1x gtceu:antimony_trioxide_dust', inFluid: null,
            outItems: ['2x gtceu:antimony_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zincite_dust', inItem: '1x gtceu:zincite_dust', inFluid: null,
            outItems: ['1x gtceu:zinc_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cupric_oxide_dust', inItem: '1x gtceu:cupric_oxide_dust', inFluid: null,
            outItems: ['1x gtceu:copper_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cobalt_oxide_dust', inItem: '1x gtceu:cobalt_oxide_dust', inFluid: null,
            outItems: ['1x gtceu:cobalt_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_arsenic_trioxide_dust', inItem: '1x gtceu:arsenic_trioxide_dust', inFluid: null,
            outItems: ['2x gtceu:arsenic_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_massicot_dust', inItem: '1x gtceu:massicot_dust', inFluid: null,
            outItems: ['1x gtceu:lead_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ferrosilite_dust', inItem: '1x gtceu:ferrosilite_dust', inFluid: null,
            outItems: ['1x gtceu:iron_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_hydroxide_dust', inItem: '1x gtceu:sodium_hydroxide_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:oxygen 1000', 'gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_persulfate_fluid', inItem: null, inFluid: 'gtceu:sodium_persulfate 1000',
            outItems: ['2x gtceu:sodium_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bastnasite_dust', inItem: '1x gtceu:bastnasite_dust', inFluid: null,
            outItems: ['1x gtceu:cerium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:fluorine 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pentlandite_dust', inItem: '1x gtceu:pentlandite_dust', inFluid: null,
            outItems: ['9x gtceu:nickel_dust', '8x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_spodumene_dust', inItem: '1x gtceu:spodumene_dust', inFluid: null,
            outItems: ['1x gtceu:lithium_dust', '1x gtceu:aluminium_dust', '2x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lepidolite_dust', inItem: '1x gtceu:lepidolite_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust', '3x gtceu:lithium_dust', '4x gtceu:aluminium_dust'],
            outFluids: ['gtceu:fluorine 2000', 'gtceu:oxygen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_glauconite_sand_dust', inItem: '1x gtceu:glauconite_sand_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust', '2x gtceu:magnesium_dust', '4x gtceu:aluminium_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_malachite_dust', inItem: '1x gtceu:malachite_dust', inFluid: null,
            outItems: ['2x gtceu:copper_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mica_dust', inItem: '1x gtceu:mica_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust', '3x gtceu:aluminium_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:fluorine 2000', 'gtceu:oxygen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_barite_dust', inItem: '1x gtceu:barite_dust', inFluid: null,
            outItems: ['1x gtceu:barium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_alunite_dust', inItem: '1x gtceu:alunite_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust', '3x gtceu:aluminium_dust', '2x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_talc_dust', inItem: '1x gtceu:talc_dust', inFluid: null,
            outItems: ['3x gtceu:magnesium_dust', '4x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_soapstone_dust', inItem: '1x gtceu:soapstone_dust', inFluid: null,
            outItems: ['3x gtceu:magnesium_dust', '4x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_kyanite_dust', inItem: '1x gtceu:kyanite_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnetic_iron_dust', inItem: '1x gtceu:magnetic_iron_dust', inFluid: null,
            outItems: ['1x gtceu:magnetic_iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tungsten_carbide_dust', inItem: '1x gtceu:tungsten_carbide_dust', inFluid: null,
            outItems: ['1x gtceu:tungsten_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tungsten_carbide_fluid', inItem: null, inFluid: 'gtceu:tungsten_carbide 1000',
            outItems: ['1x gtceu:tungsten_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_carbon_dioxide_fluid', inItem: null, inFluid: 'gtceu:carbon_dioxide 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_tetrachloride_fluid', inItem: null, inFluid: 'gtceu:titanium_tetrachloride 1000',
            outItems: ['1x gtceu:titanium_dust'],
            outFluids: ['gtceu:chlorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrogen_dioxide_fluid', inItem: null, inFluid: 'gtceu:nitrogen_dioxide 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydrogen_sulfide_fluid', inItem: null, inFluid: 'gtceu:hydrogen_sulfide 1000',
            outItems: ['1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitric_acid_fluid', inItem: null, inFluid: 'gtceu:nitric_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sulfuric_acid_fluid', inItem: null, inFluid: 'gtceu:sulfuric_acid 1000',
            outItems: ['1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phosphoric_acid_fluid', inItem: null, inFluid: 'gtceu:phosphoric_acid 1000',
            outItems: ['1x gtceu:phosphorus_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sulfur_trioxide_fluid', inItem: null, inFluid: 'gtceu:sulfur_trioxide 1000',
            outItems: ['1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sulfur_dioxide_fluid', inItem: null, inFluid: 'gtceu:sulfur_dioxide 1000',
            outItems: ['1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_carbon_monoxide_fluid', inItem: null, inFluid: 'gtceu:carbon_monoxide 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hypochlorous_acid_fluid', inItem: null, inFluid: 'gtceu:hypochlorous_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:chlorine 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ammonia_fluid', inItem: null, inFluid: 'gtceu:ammonia 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydrofluoric_acid_fluid', inItem: null, inFluid: 'gtceu:hydrofluoric_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitric_oxide_fluid', inItem: null, inFluid: 'gtceu:nitric_oxide 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iron_iii_chloride_fluid', inItem: null, inFluid: 'gtceu:iron_iii_chloride 1000',
            outItems: ['1x gtceu:iron_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iron_ii_chloride_fluid', inItem: null, inFluid: 'gtceu:iron_ii_chloride 1000',
            outItems: ['1x gtceu:iron_dust'],
            outFluids: ['gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uranium_hexafluoride_fluid', inItem: null, inFluid: 'gtceu:uranium_hexafluoride 1000',
            outItems: ['1x gtceu:uranium_dust'],
            outFluids: ['gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enriched_uranium_hexafluoride_fluid', inItem: null, inFluid: 'gtceu:enriched_uranium_hexafluoride 1000',
            outItems: ['1x gtceu:uranium_dust'],
            outFluids: ['gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_depleted_uranium_hexafluoride_fluid', inItem: null, inFluid: 'gtceu:depleted_uranium_hexafluoride 1000',
            outItems: ['1x gtceu:uranium_dust'],
            outFluids: ['gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrous_oxide_fluid', inItem: null, inFluid: 'gtceu:nitrous_oxide 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ender_pearl_dust', inItem: '1x gtceu:ender_pearl_dust', inFluid: null,
            outItems: ['1x gtceu:beryllium_dust', '4x gtceu:potassium_dust'],
            outFluids: ['gtceu:nitrogen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ender_pearl_fluid', inItem: null, inFluid: 'gtceu:ender_pearl 1000',
            outItems: ['1x gtceu:beryllium_dust', '4x gtceu:potassium_dust'],
            outFluids: ['gtceu:nitrogen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_feldspar_dust', inItem: '1x gtceu:potassium_feldspar_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust', '1x gtceu:aluminium_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnetic_neodymium_dust', inItem: '1x gtceu:magnetic_neodymium_dust', inFluid: null,
            outItems: ['1x gtceu:magnetic_neodymium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydrochloric_acid_fluid', inItem: null, inFluid: 'gtceu:hydrochloric_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_steam_fluid', inItem: null, inFluid: 'gtceu:steam 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_distilled_water_fluid', inItem: null, inFluid: 'gtceu:distilled_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_potassium_fluid', inItem: null, inFluid: 'gtceu:sodium_potassium 1000',
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:potassium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnetic_samarium_dust', inItem: '1x gtceu:magnetic_samarium_dust', inFluid: null,
            outItems: ['1x gtceu:magnetic_samarium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_manganese_phosphide_dust', inItem: '1x gtceu:manganese_phosphide_dust', inFluid: null,
            outItems: ['1x gtceu:manganese_dust', '1x gtceu:phosphorus_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_manganese_phosphide_fluid', inItem: null, inFluid: 'gtceu:manganese_phosphide 1000',
            outItems: ['1x gtceu:manganese_dust', '1x gtceu:phosphorus_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnesium_diboride_dust', inItem: '1x gtceu:magnesium_diboride_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust', '2x gtceu:boron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnesium_diboride_fluid', inItem: null, inFluid: 'gtceu:magnesium_diboride 1000',
            outItems: ['1x gtceu:magnesium_dust', '2x gtceu:boron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mercury_barium_calcium_cuprate_dust', inItem: '1x gtceu:mercury_barium_calcium_cuprate_dust', inFluid: null,
            outItems: ['2x gtceu:barium_dust', '2x gtceu:calcium_dust', '3x gtceu:copper_dust'],
            outFluids: ['gtceu:mercury 1000', 'gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mercury_barium_calcium_cuprate_fluid', inItem: null, inFluid: 'gtceu:mercury_barium_calcium_cuprate 1000',
            outItems: ['2x gtceu:barium_dust', '2x gtceu:calcium_dust', '3x gtceu:copper_dust'],
            outFluids: ['gtceu:mercury 1000', 'gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uranium_triplatinum_dust', inItem: '1x gtceu:uranium_triplatinum_dust', inFluid: null,
            outItems: ['1x gtceu:uranium_dust', '3x gtceu:platinum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uranium_triplatinum_fluid', inItem: null, inFluid: 'gtceu:uranium_triplatinum 1000',
            outItems: ['1x gtceu:uranium_dust', '3x gtceu:platinum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_iron_arsenic_oxide_dust', inItem: '1x gtceu:samarium_iron_arsenic_oxide_dust', inFluid: null,
            outItems: ['1x gtceu:samarium_dust', '1x gtceu:iron_dust', '1x gtceu:arsenic_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_iron_arsenic_oxide_fluid', inItem: null, inFluid: 'gtceu:samarium_iron_arsenic_oxide 1000',
            outItems: ['1x gtceu:samarium_dust', '1x gtceu:iron_dust', '1x gtceu:arsenic_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_indium_tin_barium_titanium_cuprate_dust', inItem: '1x gtceu:indium_tin_barium_titanium_cuprate_dust', inFluid: null,
            outItems: ['4x gtceu:indium_dust', '2x gtceu:tin_dust', '2x gtceu:barium_dust', '1x gtceu:titanium_dust', '7x gtceu:copper_dust'],
            outFluids: ['gtceu:oxygen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_indium_tin_barium_titanium_cuprate_fluid', inItem: null, inFluid: 'gtceu:indium_tin_barium_titanium_cuprate 1000',
            outItems: ['4x gtceu:indium_dust', '2x gtceu:tin_dust', '2x gtceu:barium_dust', '1x gtceu:titanium_dust', '7x gtceu:copper_dust'],
            outFluids: ['gtceu:oxygen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uranium_rhodium_dinaquadide_dust', inItem: '1x gtceu:uranium_rhodium_dinaquadide_dust', inFluid: null,
            outItems: ['1x gtceu:uranium_dust', '1x gtceu:rhodium_dust', '2x gtceu:naquadah_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uranium_rhodium_dinaquadide_fluid', inItem: null, inFluid: 'gtceu:uranium_rhodium_dinaquadide 1000',
            outItems: ['1x gtceu:uranium_dust', '1x gtceu:rhodium_dust', '2x gtceu:naquadah_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enriched_naquadah_trinium_europium_duranide_dust', inItem: '1x gtceu:enriched_naquadah_trinium_europium_duranide_dust', inFluid: null,
            outItems: ['4x gtceu:enriched_naquadah_dust', '3x gtceu:trinium_dust', '2x gtceu:europium_dust', '1x gtceu:duranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enriched_naquadah_trinium_europium_duranide_fluid', inItem: null, inFluid: 'gtceu:enriched_naquadah_trinium_europium_duranide 1000',
            outItems: ['4x gtceu:enriched_naquadah_dust', '3x gtceu:trinium_dust', '2x gtceu:europium_dust', '1x gtceu:duranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ruthenium_trinium_americium_neutronate_dust', inItem: '1x gtceu:ruthenium_trinium_americium_neutronate_dust', inFluid: null,
            outItems: ['1x gtceu:ruthenium_dust', '2x gtceu:trinium_dust', '1x gtceu:americium_dust', '2x gtceu:neutronium_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ruthenium_trinium_americium_neutronate_fluid', inItem: null, inFluid: 'gtceu:ruthenium_trinium_americium_neutronate 1000',
            outItems: ['1x gtceu:ruthenium_dust', '2x gtceu:trinium_dust', '1x gtceu:americium_dust', '2x gtceu:neutronium_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_inert_metal_mixture_dust', inItem: '1x gtceu:inert_metal_mixture_dust', inFluid: null,
            outItems: ['1x gtceu:rhodium_dust', '1x gtceu:ruthenium_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhodium_sulfate_fluid', inItem: null, inFluid: 'gtceu:rhodium_sulfate 1000',
            outItems: ['2x gtceu:rhodium_dust', '3x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ruthenium_tetroxide_dust', inItem: '1x gtceu:ruthenium_tetroxide_dust', inFluid: null,
            outItems: ['1x gtceu:ruthenium_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ruthenium_tetroxide_fluid', inItem: null, inFluid: 'gtceu:ruthenium_tetroxide 1000',
            outItems: ['1x gtceu:ruthenium_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_osmium_tetroxide_dust', inItem: '1x gtceu:osmium_tetroxide_dust', inFluid: null,
            outItems: ['1x gtceu:osmium_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iridium_chloride_dust', inItem: '1x gtceu:iridium_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:iridium_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluoroantimonic_acid_fluid', inItem: null, inFluid: 'gtceu:fluoroantimonic_acid 1000',
            outItems: ['1x gtceu:antimony_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:fluorine 7000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_trifluoride_dust', inItem: '1x gtceu:titanium_trifluoride_dust', inFluid: null,
            outItems: ['1x gtceu:titanium_dust'],
            outFluids: ['gtceu:fluorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_calcium_phosphide_dust', inItem: '1x gtceu:calcium_phosphide_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust', '1x gtceu:phosphorus_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_indium_phosphide_dust', inItem: '1x gtceu:indium_phosphide_dust', inFluid: null,
            outItems: ['1x gtceu:indium_dust', '1x gtceu:phosphorus_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_barium_sulfide_dust', inItem: '1x gtceu:barium_sulfide_dust', inFluid: null,
            outItems: ['1x gtceu:barium_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trinium_sulfide_dust', inItem: '1x gtceu:trinium_sulfide_dust', inFluid: null,
            outItems: ['1x gtceu:trinium_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zinc_sulfide_dust', inItem: '1x gtceu:zinc_sulfide_dust', inFluid: null,
            outItems: ['1x gtceu:zinc_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gallium_sulfide_dust', inItem: '1x gtceu:gallium_sulfide_dust', inFluid: null,
            outItems: ['1x gtceu:gallium_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_antimony_trifluoride_dust', inItem: '1x gtceu:antimony_trifluoride_dust', inFluid: null,
            outItems: ['1x gtceu:antimony_dust'],
            outFluids: ['gtceu:fluorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enriched_naquadah_sulfate_dust', inItem: '1x gtceu:enriched_naquadah_sulfate_dust', inFluid: null,
            outItems: ['1x gtceu:enriched_naquadah_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadria_sulfate_dust', inItem: '1x gtceu:naquadria_sulfate_dust', inFluid: null,
            outItems: ['1x gtceu:naquadria_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pyrochlore_dust', inItem: '1x gtceu:pyrochlore_dust', inFluid: null,
            outItems: ['2x gtceu:calcium_dust', '2x gtceu:niobium_dust'],
            outFluids: ['gtceu:oxygen 7000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_hydroxide_dust', inItem: '1x gtceu:potassium_hydroxide_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust'],
            outFluids: ['gtceu:oxygen 1000', 'gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_iodide_dust', inItem: '1x gtceu:potassium_iodide_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust', '1x gtceu:iodine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_carbonate_dust', inItem: '1x gtceu:potassium_carbonate_dust', inFluid: null,
            outItems: ['2x gtceu:potassium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_ferrocyanide_dust', inItem: '1x gtceu:potassium_ferrocyanide_dust', inFluid: null,
            outItems: ['4x gtceu:potassium_dust', '1x gtceu:iron_dust', '6x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_calcium_ferrocyanide_dust', inItem: '1x gtceu:calcium_ferrocyanide_dust', inFluid: null,
            outItems: ['2x gtceu:calcium_dust', '1x gtceu:iron_dust', '6x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_calcium_hydroxide_dust', inItem: '1x gtceu:calcium_hydroxide_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust'],
            outFluids: ['gtceu:oxygen 2000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_calcium_carbonate_dust', inItem: '1x gtceu:calcium_carbonate_dust', inFluid: null,
            outItems: ['2x gtceu:calcium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_cyanide_dust', inItem: '1x gtceu:potassium_cyanide_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydrogen_cyanide_fluid', inItem: null, inFluid: 'gtceu:hydrogen_cyanide 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_formic_acid_fluid', inItem: null, inFluid: 'gtceu:formic_acid 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_sulfate_dust', inItem: '1x gtceu:potassium_sulfate_dust', inFluid: null,
            outItems: ['2x gtceu:potassium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_prussian_blue_dust', inItem: '1x gtceu:prussian_blue_dust', inFluid: null,
            outItems: ['7x gtceu:iron_dust', '18x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 18000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_formaldehyde_fluid', inItem: null, inFluid: 'gtceu:formaldehyde 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_glycolonitrile_fluid', inItem: null, inFluid: 'gtceu:glycolonitrile 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acidic_bromine_solution_fluid', inItem: null, inFluid: 'gtceu:acidic_bromine_solution 1000',
            outItems: [],
            outFluids: ['gtceu:chlorine 1000', 'gtceu:bromine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_concentrated_bromine_solution_fluid', inItem: null, inFluid: 'gtceu:concentrated_bromine_solution 1000',
            outItems: [],
            outFluids: ['gtceu:bromine 2000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydrogen_iodide_fluid', inItem: null, inFluid: 'gtceu:hydrogen_iodide 1000',
            outItems: ['1x gtceu:iodine_dust'],
            outFluids: ['gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diethylenetriamine_pentaacetonitrile_fluid', inItem: null, inFluid: 'gtceu:diethylenetriamine_pentaacetonitrile 1000',
            outItems: ['14x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 18000', 'gtceu:nitrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diethylenetriaminepentaacetic_acid_dust', inItem: '1x gtceu:diethylenetriaminepentaacetic_acid_dust', inFluid: null,
            outItems: ['14x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 23000', 'gtceu:nitrogen 3000', 'gtceu:oxygen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_nitrite_dust', inItem: '1x gtceu:sodium_nitrite_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silicone_rubber_dust', inItem: '1x gtceu:silicone_rubber_dust', inFluid: null,
            outItems: ['2x gtceu:carbon_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silicone_rubber_fluid', inItem: null, inFluid: 'gtceu:silicone_rubber 1000',
            outItems: ['2x gtceu:carbon_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrobenzene_fluid', inItem: null, inFluid: 'gtceu:nitrobenzene 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_raw_rubber_dust', inItem: '1x gtceu:raw_rubber_dust', inFluid: null,
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_raw_styrene_butadiene_rubber_dust', inItem: '1x gtceu:raw_styrene_butadiene_rubber_dust', inFluid: null,
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 26000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_styrene_butadiene_rubber_dust', inItem: '1x gtceu:styrene_butadiene_rubber_dust', inFluid: null,
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 26000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_styrene_butadiene_rubber_fluid', inItem: null, inFluid: 'gtceu:styrene_butadiene_rubber 1000',
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 26000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyvinyl_acetate_fluid', inItem: null, inFluid: 'gtceu:polyvinyl_acetate 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_reinforced_epoxy_resin_dust', inItem: '1x gtceu:reinforced_epoxy_resin_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_reinforced_epoxy_resin_fluid', inItem: null, inFluid: 'gtceu:reinforced_epoxy_resin 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyvinyl_chloride_dust', inItem: '1x gtceu:polyvinyl_chloride_dust', inFluid: null,
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyvinyl_chloride_fluid', inItem: null, inFluid: 'gtceu:polyvinyl_chloride 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyphenylene_sulfide_dust', inItem: '1x gtceu:polyphenylene_sulfide_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyphenylene_sulfide_fluid', inItem: null, inFluid: 'gtceu:polyphenylene_sulfide 1000',
            outItems: ['6x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_glyceryl_trinitrate_fluid', inItem: null, inFluid: 'gtceu:glyceryl_trinitrate 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:nitrogen 3000', 'gtceu:oxygen 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polybenzimidazole_dust', inItem: '1x gtceu:polybenzimidazole_dust', inFluid: null,
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:nitrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polybenzimidazole_fluid', inItem: null, inFluid: 'gtceu:polybenzimidazole 1000',
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:nitrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polydimethylsiloxane_dust', inItem: '1x gtceu:polydimethylsiloxane_dust', inFluid: null,
            outItems: ['2x gtceu:carbon_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyethylene_dust', inItem: '1x gtceu:polyethylene_dust', inFluid: null,
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyethylene_fluid', inItem: null, inFluid: 'gtceu:polyethylene 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_epoxy_dust', inItem: '1x gtceu:epoxy_dust', inFluid: null,
            outItems: ['21x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 25000', 'gtceu:chlorine 1000', 'gtceu:oxygen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_epoxy_fluid', inItem: null, inFluid: 'gtceu:epoxy 1000',
            outItems: ['21x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 25000', 'gtceu:chlorine 1000', 'gtceu:oxygen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polycaprolactam_dust', inItem: '1x gtceu:polycaprolactam_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 11000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polycaprolactam_fluid', inItem: null, inFluid: 'gtceu:polycaprolactam 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 11000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polytetrafluoroethylene_dust', inItem: '1x gtceu:polytetrafluoroethylene_dust', inFluid: null,
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polytetrafluoroethylene_fluid', inItem: null, inFluid: 'gtceu:polytetrafluoroethylene 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sugar_dust', inItem: '1x gtceu:sugar_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_methane_fluid', inItem: null, inFluid: 'gtceu:methane 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_epichlorohydrin_fluid', inItem: null, inFluid: 'gtceu:epichlorohydrin 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:chlorine 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_monochloramine_fluid', inItem: null, inFluid: 'gtceu:monochloramine 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 2000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chloroform_fluid', inItem: null, inFluid: 'gtceu:chloroform 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cumene_fluid', inItem: null, inFluid: 'gtceu:cumene 1000',
            outItems: ['9x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tetrafluoroethylene_fluid', inItem: null, inFluid: 'gtceu:tetrafluoroethylene 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chloromethane_fluid', inItem: null, inFluid: 'gtceu:chloromethane 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_allyl_chloride_fluid', inItem: null, inFluid: 'gtceu:allyl_chloride 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_isoprene_fluid', inItem: null, inFluid: 'gtceu:isoprene 1000',
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_propane_fluid', inItem: null, inFluid: 'gtceu:propane 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_propene_fluid', inItem: null, inFluid: 'gtceu:propene 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethane_fluid', inItem: null, inFluid: 'gtceu:ethane 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_butene_fluid', inItem: null, inFluid: 'gtceu:butene 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_butane_fluid', inItem: null, inFluid: 'gtceu:butane 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dissolved_calcium_acetate_fluid', inItem: null, inFluid: 'gtceu:dissolved_calcium_acetate 1000',
            outItems: ['1x gtceu:calcium_dust', '4x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 5000', 'gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vinyl_acetate_fluid', inItem: null, inFluid: 'gtceu:vinyl_acetate 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_methyl_acetate_fluid', inItem: null, inFluid: 'gtceu:methyl_acetate 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethenone_fluid', inItem: null, inFluid: 'gtceu:ethenone 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tetranitromethane_fluid', inItem: null, inFluid: 'gtceu:tetranitromethane 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 4000', 'gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dimethylamine_fluid', inItem: null, inFluid: 'gtceu:dimethylamine 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dimethylhydrazine_fluid', inItem: null, inFluid: 'gtceu:dimethylhydrazine 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dinitrogen_tetroxide_fluid', inItem: null, inFluid: 'gtceu:dinitrogen_tetroxide 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 2000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dimethyldichlorosilane_fluid', inItem: null, inFluid: 'gtceu:dimethyldichlorosilane 1000',
            outItems: ['2x gtceu:carbon_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_styrene_fluid', inItem: null, inFluid: 'gtceu:styrene 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_butadiene_fluid', inItem: null, inFluid: 'gtceu:butadiene 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dichlorobenzene_fluid', inItem: null, inFluid: 'gtceu:dichlorobenzene 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acetic_acid_fluid', inItem: null, inFluid: 'gtceu:acetic_acid 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phenol_fluid', inItem: null, inFluid: 'gtceu:phenol 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bisphenol_a_fluid', inItem: null, inFluid: 'gtceu:bisphenol_a 1000',
            outItems: ['15x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 16000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vinyl_chloride_fluid', inItem: null, inFluid: 'gtceu:vinyl_chloride 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethylene_fluid', inItem: null, inFluid: 'gtceu:ethylene 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_benzene_fluid', inItem: null, inFluid: 'gtceu:benzene 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acetone_fluid', inItem: null, inFluid: 'gtceu:acetone 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_glycerol_fluid', inItem: null, inFluid: 'gtceu:glycerol 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_methanol_fluid', inItem: null, inFluid: 'gtceu:methanol 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethanol_fluid', inItem: null, inFluid: 'gtceu:ethanol 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_toluene_fluid', inItem: null, inFluid: 'gtceu:toluene 1000',
            outItems: ['7x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diphenyl_isophthalate_fluid', inItem: null, inFluid: 'gtceu:diphenyl_isophthalate 1000',
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phthalic_acid_fluid', inItem: null, inFluid: 'gtceu:phthalic_acid 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dimethylbenzene_fluid', inItem: null, inFluid: 'gtceu:dimethylbenzene 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diaminobenzidine_fluid', inItem: null, inFluid: 'gtceu:diaminobenzidine 1000',
            outItems: ['12x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000', 'gtceu:nitrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dichlorobenzidine_fluid', inItem: null, inFluid: 'gtceu:dichlorobenzidine 1000',
            outItems: ['12x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000', 'gtceu:chlorine 2000', 'gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrochlorobenzene_fluid', inItem: null, inFluid: 'gtceu:nitrochlorobenzene 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:chlorine 1000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chlorobenzene_fluid', inItem: null, inFluid: 'gtceu:chlorobenzene 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_octane_fluid', inItem: null, inFluid: 'gtceu:octane 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 18000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethyl_tertbutyl_ether_fluid', inItem: null, inFluid: 'gtceu:ethyl_tertbutyl_ether 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethylbenzene_fluid', inItem: null, inFluid: 'gtceu:ethylbenzene 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naphthalene_fluid', inItem: null, inFluid: 'gtceu:naphthalene 1000',
            outItems: ['10x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rubber_dust', inItem: '1x gtceu:rubber_dust', inFluid: null,
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rubber_fluid', inItem: null, inFluid: 'gtceu:rubber 1000',
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cyclohexane_fluid', inItem: null, inFluid: 'gtceu:cyclohexane 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrosyl_chloride_fluid', inItem: null, inFluid: 'gtceu:nitrosyl_chloride 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 1000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cyclohexanone_oxime_dust', inItem: '1x gtceu:cyclohexanone_oxime_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 11000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_caprolactam_dust', inItem: '1x gtceu:caprolactam_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 11000', 'gtceu:oxygen 1000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_butyraldehyde_fluid', inItem: null, inFluid: 'gtceu:butyraldehyde 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyvinyl_butyral_dust', inItem: '1x gtceu:polyvinyl_butyral_dust', inFluid: null,
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyvinyl_butyral_fluid', inItem: null, inFluid: 'gtceu:polyvinyl_butyral 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_biphenyl_dust', inItem: '1x gtceu:biphenyl_dust', inFluid: null,
            outItems: ['12x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polychlorinated_biphenyl_fluid', inItem: null, inFluid: 'gtceu:polychlorinated_biphenyl 1000',
            outItems: ['12x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acetic_anhydride_fluid', inItem: null, inFluid: 'gtceu:acetic_anhydride 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aminophenol_fluid', inItem: null, inFluid: 'gtceu:aminophenol 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_paracetamol_dust', inItem: '1x gtceu:paracetamol_dust', inFluid: null,
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 9000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ammonium_formate_fluid', inItem: null, inFluid: 'gtceu:ammonium_formate 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_formamide_fluid', inItem: null, inFluid: 'gtceu:formamide 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gunpowder_dust', inItem: '1x gtceu:gunpowder_dust', inFluid: null,
            outItems: ['2x gtceu:potassium_dust', '1x gtceu:sulfur_dust', '3x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 2000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_glass_dust', inItem: '1x gtceu:glass_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_glass_fluid', inItem: null, inFluid: 'gtceu:glass 1000',
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_perlite_dust', inItem: '1x gtceu:perlite_dust', inFluid: null,
            outItems: ['2x gtceu:magnesium_dust', '2x gtceu:iron_dust', '4x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 9000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_activated_carbon_dust', inItem: '1x gtceu:activated_carbon_dust', inFluid: null,
            outItems: ['1x gtceu:activated_carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_borax_dust', inItem: '1x gtceu:borax_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '4x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 20000', 'gtceu:oxygen 17000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_salt_water_fluid', inItem: null, inFluid: 'gtceu:salt_water 1000',
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:chlorine 1000', 'gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_olivine_dust', inItem: '1x gtceu:olivine_dust', inFluid: null,
            outItems: ['2x gtceu:magnesium_dust', '1x gtceu:iron_dust', '2x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_opal_dust', inItem: '1x gtceu:opal_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_amethyst_dust', inItem: '1x gtceu:amethyst_dust', inFluid: null,
            outItems: ['4x gtceu:silicon_dust', '1x gtceu:iron_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lapis_dust', inItem: '1x gtceu:lapis_dust', inFluid: null,
            outItems: ['78x gtceu:aluminium_dust', '78x gtceu:silicon_dust', '97x gtceu:calcium_dust', '104x gtceu:sodium_dust', '1x gtceu:iron_dust', '2x gtceu:sulfur_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:chlorine 2000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_blaze_dust', inItem: '1x gtceu:blaze_dust', inFluid: null,
            outItems: ['1x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_blaze_fluid', inItem: null, inFluid: 'gtceu:blaze 1000',
            outItems: ['1x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_apatite_dust', inItem: '1x gtceu:apatite_dust', inFluid: null,
            outItems: ['5x gtceu:calcium_dust', '3x gtceu:phosphorus_dust'],
            outFluids: ['gtceu:oxygen 12000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_black_steel_dust', inItem: '1x gtceu:black_steel_dust', inFluid: null,
            outItems: ['1x gtceu:nickel_dust', '1x gtceu:gold_dust', '1x gtceu:silver_dust', '3x gtceu:copper_dust', '3x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_black_steel_fluid', inItem: null, inFluid: 'gtceu:black_steel 1000',
            outItems: ['1x gtceu:nickel_dust', '1x gtceu:gold_dust', '1x gtceu:silver_dust', '3x gtceu:copper_dust', '3x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_damascus_steel_dust', inItem: '1x gtceu:damascus_steel_dust', inFluid: null,
            outItems: ['1x gtceu:damascus_steel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_damascus_steel_fluid', inItem: null, inFluid: 'gtceu:damascus_steel 1000',
            outItems: ['1x gtceu:damascus_steel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tungsten_steel_dust', inItem: '1x gtceu:tungsten_steel_dust', inFluid: null,
            outItems: ['1x gtceu:iron_dust', '1x gtceu:tungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tungsten_steel_fluid', inItem: null, inFluid: 'gtceu:tungsten_steel 1000',
            outItems: ['1x gtceu:iron_dust', '1x gtceu:tungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cobalt_brass_dust', inItem: '1x gtceu:cobalt_brass_dust', inFluid: null,
            outItems: ['7x gtceu:zinc_dust', '21x gtceu:copper_dust', '1x gtceu:aluminium_dust', '1x gtceu:cobalt_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cobalt_brass_fluid', inItem: null, inFluid: 'gtceu:cobalt_brass 1000',
            outItems: ['7x gtceu:zinc_dust', '21x gtceu:copper_dust', '1x gtceu:aluminium_dust', '1x gtceu:cobalt_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tricalcium_phosphate_dust', inItem: '1x gtceu:tricalcium_phosphate_dust', inFluid: null,
            outItems: ['3x gtceu:calcium_dust', '2x gtceu:phosphorus_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_red_garnet_dust', inItem: '1x gtceu:red_garnet_dust', inFluid: null,
            outItems: ['32x gtceu:aluminium_dust', '9x gtceu:magnesium_dust', '48x gtceu:silicon_dust', '15x gtceu:iron_dust', '24x gtceu:manganese_dust'],
            outFluids: ['gtceu:oxygen 192000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_yellow_garnet_dust', inItem: '1x gtceu:yellow_garnet_dust', inFluid: null,
            outItems: ['48x gtceu:calcium_dust', '10x gtceu:iron_dust', '48x gtceu:silicon_dust', '16x gtceu:aluminium_dust', '6x gtceu:chromium_dust'],
            outFluids: ['gtceu:oxygen 192000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_marble_dust', inItem: '1x gtceu:marble_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust', '7x gtceu:calcium_dust', '7x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 21000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_deepslate_dust', inItem: '1x gtceu:deepslate_dust', inFluid: null,
            outItems: ['7x gtceu:silicon_dust', '1x gtceu:potassium_dust', '3x gtceu:magnesium_dust', '3x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 18000', 'gtceu:fluorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_granite_red_dust', inItem: '1x gtceu:granite_red_dust', inFluid: null,
            outItems: ['3x gtceu:aluminium_dust', '1x gtceu:potassium_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 11000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vanadium_magnetite_dust', inItem: '1x gtceu:vanadium_magnetite_dust', inFluid: null,
            outItems: ['3x gtceu:iron_dust', '1x gtceu:vanadium_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_quartz_sand_dust', inItem: '1x gtceu:quartz_sand_dust', inFluid: null,
            outItems: ['2x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pollucite_dust', inItem: '1x gtceu:pollucite_dust', inFluid: null,
            outItems: ['2x gtceu:caesium_dust', '2x gtceu:aluminium_dust', '4x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bentonite_dust', inItem: '1x gtceu:bentonite_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust', '6x gtceu:magnesium_dust', '12x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 14000', 'gtceu:oxygen 41000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fullers_earth_dust', inItem: '1x gtceu:fullers_earth_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust', '4x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 9000', 'gtceu:oxygen 15000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pitchblende_dust', inItem: '1x gtceu:pitchblende_dust', inFluid: null,
            outItems: ['3x gtceu:uranium_dust', '1x gtceu:thorium_dust', '1x gtceu:lead_dust'],
            outFluids: ['gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mirabilite_dust', inItem: '1x gtceu:mirabilite_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 20000', 'gtceu:oxygen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trona_dust', inItem: '1x gtceu:trona_dust', inFluid: null,
            outItems: ['3x gtceu:sodium_dust', '2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gypsum_dust', inItem: '1x gtceu:gypsum_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zeolite_dust', inItem: '1x gtceu:zeolite_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust', '4x gtceu:calcium_dust', '27x gtceu:silicon_dust', '9x gtceu:aluminium_dust'],
            outFluids: ['gtceu:hydrogen 56000', 'gtceu:oxygen 100000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnetic_steel_dust', inItem: '1x gtceu:magnetic_steel_dust', inFluid: null,
            outItems: ['1x gtceu:magnetic_steel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vanadium_steel_dust', inItem: '1x gtceu:vanadium_steel_dust', inFluid: null,
            outItems: ['1x gtceu:vanadium_dust', '1x gtceu:chromium_dust', '7x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vanadium_steel_fluid', inItem: null, inFluid: 'gtceu:vanadium_steel 1000',
            outItems: ['1x gtceu:vanadium_dust', '1x gtceu:chromium_dust', '7x gtceu:iron_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potin_dust', inItem: '1x gtceu:potin_dust', inFluid: null,
            outItems: ['6x gtceu:copper_dust', '2x gtceu:tin_dust', '1x gtceu:lead_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potin_fluid', inItem: null, inFluid: 'gtceu:potin 1000',
            outItems: ['6x gtceu:copper_dust', '2x gtceu:tin_dust', '1x gtceu:lead_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_borosilicate_glass_dust', inItem: '1x gtceu:borosilicate_glass_dust', inFluid: null,
            outItems: ['1x gtceu:boron_dust', '7x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_borosilicate_glass_fluid', inItem: null, inFluid: 'gtceu:borosilicate_glass 1000',
            outItems: ['1x gtceu:boron_dust', '7x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_andesite_dust', inItem: '1x gtceu:andesite_dust', inFluid: null,
            outItems: ['12x gtceu:magnesium_dust', '8x gtceu:silicon_dust', '1x gtceu:potassium_dust'],
            outFluids: ['gtceu:hydrogen 16000', 'gtceu:oxygen 39000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadah_alloy_dust', inItem: '1x gtceu:naquadah_alloy_dust', inFluid: null,
            outItems: ['2x gtceu:naquadah_dust', '3x gtceu:iridium_dust', '1x gtceu:osmium_dust', '1x gtceu:trinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadah_alloy_fluid', inItem: null, inFluid: 'gtceu:naquadah_alloy 1000',
            outItems: ['2x gtceu:naquadah_dust', '3x gtceu:iridium_dust', '1x gtceu:osmium_dust', '1x gtceu:trinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sulfuric_nickel_solution_fluid', inItem: null, inFluid: 'gtceu:sulfuric_nickel_solution 1000',
            outItems: ['1x gtceu:nickel_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 5000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sulfuric_copper_solution_fluid', inItem: null, inFluid: 'gtceu:sulfuric_copper_solution 1000',
            outItems: ['1x gtceu:copper_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 5000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lead_zinc_solution_fluid', inItem: null, inFluid: 'gtceu:lead_zinc_solution 1000',
            outItems: ['1x gtceu:lead_dust', '1x gtceu:silver_dust', '1x gtceu:zinc_dust', '3x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitration_mixture_fluid', inItem: null, inFluid: 'gtceu:nitration_mixture 1000',
            outItems: ['1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 7000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diluted_sulfuric_acid_fluid', inItem: null, inFluid: 'gtceu:diluted_sulfuric_acid 1000',
            outItems: ['2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diluted_hydrochloric_acid_fluid', inItem: null, inFluid: 'gtceu:diluted_hydrochloric_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:chlorine 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_flint_dust', inItem: '1x gtceu:flint_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_air_fluid', inItem: null, inFluid: 'gtceu:air 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 78000', 'gtceu:oxygen 21000', 'gtceu:argon 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_liquid_air_fluid', inItem: null, inFluid: 'gtceu:liquid_air 1000',
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 70000', 'gtceu:oxygen 33000', 'gtceu:helium 2000', 'gtceu:argon 1000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nether_air_fluid', inItem: null, inFluid: 'gtceu:nether_air 1000',
            outItems: ['78x gtceu:carbon_dust', '21x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 78000', 'gtceu:hydrogen 42000', 'gtceu:neon 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ender_air_fluid', inItem: null, inFluid: 'gtceu:ender_air 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 78000', 'gtceu:oxygen 156000', 'gtceu:deuterium 21000', 'gtceu:xenon 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_liquid_ender_air_fluid', inItem: null, inFluid: 'gtceu:liquid_ender_air 1000',
            outItems: ['10x gtceu:tear_dust', '1x gtceu:beryllium_dust', '4x gtceu:potassium_dust'],
            outFluids: ['gtceu:nitrogen 127000', 'gtceu:oxygen 244000', 'gtceu:deuterium 50000', 'gtceu:helium 15000', 'gtceu:krypton 1000', 'gtceu:xenon 1000', 'gtceu:radon 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aqua_regia_fluid', inItem: null, inFluid: 'gtceu:aqua_regia 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 3000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_platinum_sludge_residue_dust', inItem: '1x gtceu:platinum_sludge_residue_dust', inFluid: null,
            outItems: ['2x gtceu:silicon_dust', '3x gtceu:gold_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_palladium_raw_dust', inItem: '1x gtceu:palladium_raw_dust', inFluid: null,
            outItems: ['1x gtceu:palladium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rarest_metal_mixture_dust', inItem: '1x gtceu:rarest_metal_mixture_dust', inFluid: null,
            outItems: ['1x gtceu:iridium_dust', '1x gtceu:osmium_dust'],
            outFluids: ['gtceu:oxygen 5000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ammonium_chloride_dust', inItem: '1x gtceu:ammonium_chloride_dust', inFluid: null,
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 4000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acidic_osmium_solution_fluid', inItem: null, inFluid: 'gtceu:acidic_osmium_solution 1000',
            outItems: ['1x gtceu:osmium_dust'],
            outFluids: ['gtceu:oxygen 5000', 'gtceu:hydrogen 3000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhodium_plated_palladium_dust', inItem: '1x gtceu:rhodium_plated_palladium_dust', inFluid: null,
            outItems: ['3x gtceu:palladium_dust', '1x gtceu:rhodium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhodium_plated_palladium_fluid', inItem: null, inFluid: 'gtceu:rhodium_plated_palladium 1000',
            outItems: ['3x gtceu:palladium_dust', '1x gtceu:rhodium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_clay_dust', inItem: '1x gtceu:clay_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '1x gtceu:lithium_dust', '2x gtceu:aluminium_dust', '2x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_redstone_dust', inItem: '1x gtceu:redstone_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust', '5x gtceu:iron_dust', '10x gtceu:sulfur_dust', '1x gtceu:chromium_dust', '2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 3000', 'gtceu:mercury 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_redstone_fluid', inItem: null, inFluid: 'gtceu:redstone 1000',
            outItems: ['1x gtceu:silicon_dust', '5x gtceu:iron_dust', '10x gtceu:sulfur_dust', '1x gtceu:chromium_dust', '2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 3000', 'gtceu:mercury 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dichloroethane_fluid', inItem: null, inFluid: 'gtceu:dichloroethane 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diethylenetriamine_fluid', inItem: null, inFluid: 'gtceu:diethylenetriamine 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 13000', 'gtceu:nitrogen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_brominated_chlorine_vapor_fluid', inItem: null, inFluid: 'gtceu:brominated_chlorine_vapor 1000',
            outItems: [],
            outFluids: ['gtceu:chlorine 1000', 'gtceu:bromine 1000', 'gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acidic_bromine_exhaust_fluid', inItem: null, inFluid: 'gtceu:acidic_bromine_exhaust 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 3000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_electrotine_dust', inItem: '1x gtceu:electrotine_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust', '5x gtceu:iron_dust', '10x gtceu:sulfur_dust', '1x gtceu:chromium_dust', '2x gtceu:aluminium_dust', '1x gtceu:silver_dust', '1x gtceu:gold_dust'],
            outFluids: ['gtceu:oxygen 3000', 'gtceu:mercury 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ender_eye_dust', inItem: '1x gtceu:ender_eye_dust', inFluid: null,
            outItems: ['1x gtceu:beryllium_dust', '4x gtceu:potassium_dust', '1x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:nitrogen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ender_eye_fluid', inItem: null, inFluid: 'gtceu:ender_eye 1000',
            outItems: ['1x gtceu:beryllium_dust', '4x gtceu:potassium_dust', '1x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:nitrogen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diatomite_dust', inItem: '1x gtceu:diatomite_dust', inFluid: null,
            outItems: ['8x gtceu:silicon_dust', '2x gtceu:iron_dust', '2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 22000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_red_steel_dust', inItem: '1x gtceu:red_steel_dust', inFluid: null,
            outItems: ['16x gtceu:copper_dust', '8x gtceu:silver_dust', '1x gtceu:bismuth_dust', '1x gtceu:zinc_dust', '14x gtceu:iron_dust', '4x gtceu:nickel_dust', '4x gtceu:gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_red_steel_fluid', inItem: null, inFluid: 'gtceu:red_steel 1000',
            outItems: ['16x gtceu:copper_dust', '8x gtceu:silver_dust', '1x gtceu:bismuth_dust', '1x gtceu:zinc_dust', '14x gtceu:iron_dust', '4x gtceu:nickel_dust', '4x gtceu:gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_blue_steel_dust', inItem: '1x gtceu:blue_steel_dust', inFluid: null,
            outItems: ['16x gtceu:copper_dust', '8x gtceu:gold_dust', '1x gtceu:zinc_dust', '14x gtceu:iron_dust', '4x gtceu:nickel_dust', '4x gtceu:silver_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_blue_steel_fluid', inItem: null, inFluid: 'gtceu:blue_steel 1000',
            outItems: ['16x gtceu:copper_dust', '8x gtceu:gold_dust', '1x gtceu:zinc_dust', '14x gtceu:iron_dust', '4x gtceu:nickel_dust', '4x gtceu:silver_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_basalt_dust', inItem: '1x gtceu:basalt_dust', inFluid: null,
            outItems: ['2x gtceu:magnesium_dust', '1x gtceu:iron_dust', '10x gtceu:silicon_dust', '3x gtceu:calcium_dust', '7x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 29000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_granitic_mineral_sand_dust', inItem: '1x gtceu:granitic_mineral_sand_dust', inFluid: null,
            outItems: ['3x gtceu:iron_dust', '7x gtceu:silicon_dust', '1x gtceu:potassium_dust', '3x gtceu:magnesium_dust', '3x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 22000', 'gtceu:fluorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_redrock_dust', inItem: '1x gtceu:redrock_dust', inFluid: null,
            outItems: ['2x gtceu:calcium_dust', '2x gtceu:carbon_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_garnet_sand_dust', inItem: '1x gtceu:garnet_sand_dust', inFluid: null,
            outItems: ['8x gtceu:aluminium_dust', '5x gtceu:iron_dust', '18x gtceu:silicon_dust', '9x gtceu:calcium_dust', '3x gtceu:magnesium_dust', '3x gtceu:manganese_dust', '2x gtceu:chromium_dust'],
            outFluids: ['gtceu:oxygen 72000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hssg_dust', inItem: '1x gtceu:hssg_dust', inFluid: null,
            outItems: ['5x gtceu:iron_dust', '5x gtceu:tungsten_dust', '1x gtceu:chromium_dust', '2x gtceu:molybdenum_dust', '1x gtceu:vanadium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hssg_fluid', inItem: null, inFluid: 'gtceu:hssg 1000',
            outItems: ['5x gtceu:iron_dust', '5x gtceu:tungsten_dust', '1x gtceu:chromium_dust', '2x gtceu:molybdenum_dust', '1x gtceu:vanadium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_red_alloy_dust', inItem: '1x gtceu:red_alloy_dust', inFluid: null,
            outItems: ['1x gtceu:copper_dust', '4x gtceu:silicon_dust', '20x gtceu:iron_dust', '40x gtceu:sulfur_dust', '4x gtceu:chromium_dust', '8x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 12000', 'gtceu:mercury 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_red_alloy_fluid', inItem: null, inFluid: 'gtceu:red_alloy 1000',
            outItems: ['1x gtceu:copper_dust', '4x gtceu:silicon_dust', '20x gtceu:iron_dust', '40x gtceu:sulfur_dust', '4x gtceu:chromium_dust', '8x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 12000', 'gtceu:mercury 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_basaltic_mineral_sand_dust', inItem: '1x gtceu:basaltic_mineral_sand_dust', inFluid: null,
            outItems: ['4x gtceu:iron_dust', '2x gtceu:magnesium_dust', '10x gtceu:silicon_dust', '3x gtceu:calcium_dust', '7x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 33000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hsse_dust', inItem: '1x gtceu:hsse_dust', inFluid: null,
            outItems: ['30x gtceu:iron_dust', '30x gtceu:tungsten_dust', '6x gtceu:chromium_dust', '12x gtceu:molybdenum_dust', '6x gtceu:vanadium_dust', '1x gtceu:cobalt_dust', '1x gtceu:manganese_dust', '1x gtceu:silicon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hsse_fluid', inItem: null, inFluid: 'gtceu:hsse 1000',
            outItems: ['30x gtceu:iron_dust', '30x gtceu:tungsten_dust', '6x gtceu:chromium_dust', '12x gtceu:molybdenum_dust', '6x gtceu:vanadium_dust', '1x gtceu:cobalt_dust', '1x gtceu:manganese_dust', '1x gtceu:silicon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hsss_dust', inItem: '1x gtceu:hsss_dust', inFluid: null,
            outItems: ['30x gtceu:iron_dust', '30x gtceu:tungsten_dust', '6x gtceu:chromium_dust', '12x gtceu:molybdenum_dust', '6x gtceu:vanadium_dust', '2x gtceu:iridium_dust', '1x gtceu:osmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hsss_fluid', inItem: null, inFluid: 'gtceu:hsss 1000',
            outItems: ['30x gtceu:iron_dust', '30x gtceu:tungsten_dust', '6x gtceu:chromium_dust', '12x gtceu:molybdenum_dust', '6x gtceu:vanadium_dust', '2x gtceu:iridium_dust', '1x gtceu:osmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iridium_metal_residue_dust', inItem: '1x gtceu:iridium_metal_residue_dust', inFluid: null,
            outItems: ['1x gtceu:iridium_dust', '2x gtceu:silicon_dust', '3x gtceu:gold_dust'],
            outFluids: ['gtceu:chlorine 3000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_granite_dust', inItem: '1x gtceu:granite_dust', inFluid: null,
            outItems: ['5x gtceu:silicon_dust', '2x gtceu:calcium_dust', '2x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 16000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_brick_dust', inItem: '1x gtceu:brick_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '1x gtceu:lithium_dust', '2x gtceu:aluminium_dust', '2x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fireclay_dust', inItem: '1x gtceu:fireclay_dust', inFluid: null,
            outItems: ['4x gtceu:sodium_dust', '2x gtceu:lithium_dust', '4x gtceu:aluminium_dust', '4x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 24000', 'gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diorite_dust', inItem: '1x gtceu:diorite_dust', inFluid: null,
            outItems: ['18x gtceu:sodium_dust', '2x gtceu:sulfur_dust', '7x gtceu:lithium_dust', '14x gtceu:aluminium_dust', '14x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 124000', 'gtceu:oxygen 70000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_blue_alloy_dust', inItem: '1x gtceu:blue_alloy_dust', inFluid: null,
            outItems: ['4x gtceu:silicon_dust', '20x gtceu:iron_dust', '40x gtceu:sulfur_dust', '4x gtceu:chromium_dust', '8x gtceu:aluminium_dust', '5x gtceu:silver_dust', '4x gtceu:gold_dust'],
            outFluids: ['gtceu:oxygen 12000', 'gtceu:mercury 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_blue_alloy_fluid', inItem: null, inFluid: 'gtceu:blue_alloy 1000',
            outItems: ['4x gtceu:silicon_dust', '20x gtceu:iron_dust', '40x gtceu:sulfur_dust', '4x gtceu:chromium_dust', '8x gtceu:aluminium_dust', '5x gtceu:silver_dust', '4x gtceu:gold_dust'],
            outFluids: ['gtceu:oxygen 12000', 'gtceu:mercury 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rad_away_dust', inItem: '1x gtceu:rad_away_dust', inFluid: null,
            outItems: ['5x gtceu:potassium_dust', '5x gtceu:iodine_dust', '21x gtceu:iron_dust', '124x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 69000', 'gtceu:hydrogen 115000', 'gtceu:oxygen 50000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tantalum_carbide_dust', inItem: '1x gtceu:tantalum_carbide_dust', inFluid: null,
            outItems: ['1x gtceu:tantalum_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tantalum_carbide_fluid', inItem: null, inFluid: 'gtceu:tantalum_carbide 1000',
            outItems: ['1x gtceu:tantalum_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hsla_steel_dust', inItem: '1x gtceu:hsla_steel_dust', inFluid: null,
            outItems: ['4x gtceu:iron_dust', '2x gtceu:nickel_dust', '1x gtceu:vanadium_dust', '1x gtceu:titanium_dust', '1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hsla_steel_fluid', inItem: null, inFluid: 'gtceu:hsla_steel 1000',
            outItems: ['4x gtceu:iron_dust', '2x gtceu:nickel_dust', '1x gtceu:vanadium_dust', '1x gtceu:titanium_dust', '1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_molybdenum_disilicide_dust', inItem: '1x gtceu:molybdenum_disilicide_dust', inFluid: null,
            outItems: ['1x gtceu:molybdenum_dust', '2x gtceu:silicon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_molybdenum_disilicide_fluid', inItem: null, inFluid: 'gtceu:molybdenum_disilicide 1000',
            outItems: ['1x gtceu:molybdenum_dust', '2x gtceu:silicon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zeron_100_dust', inItem: '1x gtceu:zeron_100_dust', inFluid: null,
            outItems: ['10x gtceu:iron_dust', '2x gtceu:nickel_dust', '2x gtceu:tungsten_dust', '1x gtceu:niobium_dust', '1x gtceu:cobalt_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zeron_100_fluid', inItem: null, inFluid: 'gtceu:zeron_100 1000',
            outItems: ['10x gtceu:iron_dust', '2x gtceu:nickel_dust', '2x gtceu:tungsten_dust', '1x gtceu:niobium_dust', '1x gtceu:cobalt_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_watertight_steel_dust', inItem: '1x gtceu:watertight_steel_dust', inFluid: null,
            outItems: ['7x gtceu:iron_dust', '4x gtceu:aluminium_dust', '2x gtceu:nickel_dust', '1x gtceu:chromium_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_watertight_steel_fluid', inItem: null, inFluid: 'gtceu:watertight_steel 1000',
            outItems: ['7x gtceu:iron_dust', '4x gtceu:aluminium_dust', '2x gtceu:nickel_dust', '1x gtceu:chromium_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_incoloy_ma_956_dust', inItem: '1x gtceu:incoloy_ma_956_dust', inFluid: null,
            outItems: ['4x gtceu:vanadium_dust', '4x gtceu:chromium_dust', '28x gtceu:iron_dust', '2x gtceu:manganese_dust', '5x gtceu:aluminium_dust', '2x gtceu:yttrium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_incoloy_ma_956_fluid', inItem: null, inFluid: 'gtceu:incoloy_ma_956 1000',
            outItems: ['4x gtceu:vanadium_dust', '4x gtceu:chromium_dust', '28x gtceu:iron_dust', '2x gtceu:manganese_dust', '5x gtceu:aluminium_dust', '2x gtceu:yttrium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_maraging_steel_300_dust', inItem: '1x gtceu:maraging_steel_300_dust', inFluid: null,
            outItems: ['16x gtceu:iron_dust', '1x gtceu:titanium_dust', '1x gtceu:aluminium_dust', '4x gtceu:nickel_dust', '2x gtceu:cobalt_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_maraging_steel_300_fluid', inItem: null, inFluid: 'gtceu:maraging_steel_300 1000',
            outItems: ['16x gtceu:iron_dust', '1x gtceu:titanium_dust', '1x gtceu:aluminium_dust', '4x gtceu:nickel_dust', '2x gtceu:cobalt_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloy_x_dust', inItem: '1x gtceu:hastelloy_x_dust', inFluid: null,
            outItems: ['8x gtceu:nickel_dust', '3x gtceu:iron_dust', '4x gtceu:tungsten_dust', '2x gtceu:molybdenum_dust', '1x gtceu:chromium_dust', '1x gtceu:niobium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloy_x_fluid', inItem: null, inFluid: 'gtceu:hastelloy_x 1000',
            outItems: ['8x gtceu:nickel_dust', '3x gtceu:iron_dust', '4x gtceu:tungsten_dust', '2x gtceu:molybdenum_dust', '1x gtceu:chromium_dust', '1x gtceu:niobium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_stellite_100_dust', inItem: '1x gtceu:stellite_100_dust', inFluid: null,
            outItems: ['4x gtceu:iron_dust', '3x gtceu:chromium_dust', '2x gtceu:tungsten_dust', '1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_stellite_100_fluid', inItem: null, inFluid: 'gtceu:stellite_100 1000',
            outItems: ['4x gtceu:iron_dust', '3x gtceu:chromium_dust', '2x gtceu:tungsten_dust', '1x gtceu:molybdenum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_carbide_dust', inItem: '1x gtceu:titanium_carbide_dust', inFluid: null,
            outItems: ['1x gtceu:titanium_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_carbide_fluid', inItem: null, inFluid: 'gtceu:titanium_carbide 1000',
            outItems: ['1x gtceu:titanium_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_tungsten_carbide_dust', inItem: '1x gtceu:titanium_tungsten_carbide_dust', inFluid: null,
            outItems: ['2x gtceu:titanium_dust', '3x gtceu:carbon_dust', '1x gtceu:tungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_tungsten_carbide_fluid', inItem: null, inFluid: 'gtceu:titanium_tungsten_carbide 1000',
            outItems: ['2x gtceu:titanium_dust', '3x gtceu:carbon_dust', '1x gtceu:tungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloy_c_276_dust', inItem: '1x gtceu:hastelloy_c_276_dust', inFluid: null,
            outItems: ['12x gtceu:nickel_dust', '8x gtceu:molybdenum_dust', '7x gtceu:chromium_dust', '1x gtceu:tungsten_dust', '1x gtceu:cobalt_dust', '1x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloy_c_276_fluid', inItem: null, inFluid: 'gtceu:hastelloy_c_276 1000',
            outItems: ['12x gtceu:nickel_dust', '8x gtceu:molybdenum_dust', '7x gtceu:chromium_dust', '1x gtceu:tungsten_dust', '1x gtceu:cobalt_dust', '1x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_infused_gold_dust', inItem: '1x gtceu:infused_gold_dust', inFluid: null,
            outItems: ['1x gtceu:infused_gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thaumium_dust', inItem: '1x gtceu:thaumium_dust', inFluid: null,
            outItems: ['1x gtceu:thaumium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_astral_silver_dust', inItem: '1x gtceu:astral_silver_dust', inFluid: null,
            outItems: ['2x gtceu:silver_dust', '1x gtceu:infused_gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_astral_silver_fluid', inItem: null, inFluid: 'gtceu:astral_silver 1000',
            outItems: ['2x gtceu:silver_dust', '1x gtceu:infused_gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pulsating_alloy_dust', inItem: '1x gtceu:pulsating_alloy_dust', inFluid: null,
            outItems: ['1x gtceu:pulsating_alloy_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_conductive_alloy_dust', inItem: '1x gtceu:conductive_alloy_dust', inFluid: null,
            outItems: ['6x gtceu:iron_dust', '1x gtceu:silicon_dust', '10x gtceu:sulfur_dust', '1x gtceu:chromium_dust', '2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 3000', 'gtceu:mercury 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_celestine_dust', inItem: '1x gtceu:celestine_dust', inFluid: null,
            outItems: ['1x gtceu:strontium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zircon_dust', inItem: '1x gtceu:zircon_dust', inFluid: null,
            outItems: ['1x gtceu:zirconium_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bismuth_tellurite_dust', inItem: '1x gtceu:bismuth_tellurite_dust', inFluid: null,
            outItems: ['2x gtceu:bismuth_dust', '3x gtceu:tellurium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_prasiolite_dust', inItem: '1x gtceu:prasiolite_dust', inFluid: null,
            outItems: ['5x gtceu:silicon_dust', '1x gtceu:iron_dust'],
            outFluids: ['gtceu:oxygen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cubic_zirconia_dust', inItem: '1x gtceu:cubic_zirconia_dust', inFluid: null,
            outItems: ['1x gtceu:zirconium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magneto_resonatic_dust', inItem: '1x gtceu:magneto_resonatic_dust', inFluid: null,
            outItems: ['15x gtceu:silicon_dust', '4x gtceu:iron_dust', '12x gtceu:bismuth_dust', '18x gtceu:tellurium_dust', '1x gtceu:zirconium_dust'],
            outFluids: ['gtceu:oxygen 32000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_adamantium_dust', inItem: '1x gtceu:adamantium_dust', inFluid: null,
            outItems: ['1x gtceu:adamantium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_adamantium_fluid', inItem: null, inFluid: 'gtceu:adamantium 1000',
            outItems: ['1x gtceu:adamantium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_quantanium_dust', inItem: '1x gtceu:quantanium_dust', inFluid: null,
            outItems: ['1x gtceu:quantanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_quantanium_fluid', inItem: null, inFluid: 'gtceu:quantanium 1000',
            outItems: ['1x gtceu:quantanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vibranium_dust', inItem: '1x gtceu:vibranium_dust', inFluid: null,
            outItems: ['1x gtceu:vibranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vibranium_fluid', inItem: null, inFluid: 'gtceu:vibranium 1000',
            outItems: ['1x gtceu:vibranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_indalloy_140_dust', inItem: '1x gtceu:indalloy_140_dust', inFluid: null,
            outItems: ['47x gtceu:bismuth_dust', '25x gtceu:lead_dust', '13x gtceu:tin_dust', '10x gtceu:cadmium_dust', '5x gtceu:indium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_indalloy_140_fluid', inItem: null, inFluid: 'gtceu:indalloy_140 1000',
            outItems: ['47x gtceu:bismuth_dust', '25x gtceu:lead_dust', '13x gtceu:tin_dust', '10x gtceu:cadmium_dust', '5x gtceu:indium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_artherium_sn_dust', inItem: '1x gtceu:artherium_sn_dust', inFluid: null,
            outItems: ['12x gtceu:tin_dust', '7x gtceu:actinium_dust', '20x gtceu:enriched_naquadah_dust', '15x gtceu:trinium_dust', '10x gtceu:europium_dust', '5x gtceu:duranium_dust', '4x gtceu:caesium_dust', '9x gtceu:iridium_dust', '3x gtceu:osmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_artherium_sn_fluid', inItem: null, inFluid: 'gtceu:artherium_sn 1000',
            outItems: ['12x gtceu:tin_dust', '7x gtceu:actinium_dust', '20x gtceu:enriched_naquadah_dust', '15x gtceu:trinium_dust', '10x gtceu:europium_dust', '5x gtceu:duranium_dust', '4x gtceu:caesium_dust', '9x gtceu:iridium_dust', '3x gtceu:osmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tairitsu_dust', inItem: '1x gtceu:tairitsu_dust', inFluid: null,
            outItems: ['8x gtceu:tungsten_dust', '7x gtceu:naquadria_dust', '4x gtceu:trinium_dust', '4x gtceu:carbon_dust', '3x gtceu:vanadium_dust', '1x gtceu:plutonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tairitsu_fluid', inItem: null, inFluid: 'gtceu:tairitsu 1000',
            outItems: ['8x gtceu:tungsten_dust', '7x gtceu:naquadria_dust', '4x gtceu:trinium_dust', '4x gtceu:carbon_dust', '3x gtceu:vanadium_dust', '1x gtceu:plutonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_draconium_dust', inItem: '1x gtceu:draconium_dust', inFluid: null,
            outItems: ['1x gtceu:draconium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_draconium_fluid', inItem: null, inFluid: 'gtceu:draconium 1000',
            outItems: ['1x gtceu:draconium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chaos_dust', inItem: '1x gtceu:chaos_dust', inFluid: null,
            outItems: ['1x gtceu:chaos_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chaos_fluid', inItem: null, inFluid: 'gtceu:chaos 1000',
            outItems: ['1x gtceu:chaos_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hypogen_dust', inItem: '1x gtceu:hypogen_dust', inFluid: null,
            outItems: ['1x gtceu:hypogen_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hypogen_fluid', inItem: null, inFluid: 'gtceu:hypogen 1000',
            outItems: ['1x gtceu:hypogen_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_shirabon_dust', inItem: '1x gtceu:shirabon_dust', inFluid: null,
            outItems: ['1x gtceu:shirabon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_shirabon_fluid', inItem: null, inFluid: 'gtceu:shirabon 1000',
            outItems: ['1x gtceu:shirabon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mithril_dust', inItem: '1x gtceu:mithril_dust', inFluid: null,
            outItems: ['1x gtceu:mithril_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mithril_fluid', inItem: null, inFluid: 'gtceu:mithril 1000',
            outItems: ['1x gtceu:mithril_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_taranium_dust', inItem: '1x gtceu:taranium_dust', inFluid: null,
            outItems: ['1x gtceu:taranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_taranium_fluid', inItem: null, inFluid: 'gtceu:taranium 1000',
            outItems: ['1x gtceu:taranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_crystalmatrix_dust', inItem: '1x gtceu:crystalmatrix_dust', inFluid: null,
            outItems: ['1x gtceu:crystalmatrix_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_crystalmatrix_fluid', inItem: null, inFluid: 'gtceu:crystalmatrix 1000',
            outItems: ['1x gtceu:crystalmatrix_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cosmicneutronium_dust', inItem: '1x gtceu:cosmicneutronium_dust', inFluid: null,
            outItems: ['1x gtceu:cosmicneutronium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cosmicneutronium_fluid', inItem: null, inFluid: 'gtceu:cosmicneutronium 1000',
            outItems: ['1x gtceu:cosmicneutronium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_echoite_dust', inItem: '1x gtceu:echoite_dust', inFluid: null,
            outItems: ['1x gtceu:echoite_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_echoite_fluid', inItem: null, inFluid: 'gtceu:echoite 1000',
            outItems: ['1x gtceu:echoite_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_legendarium_dust', inItem: '1x gtceu:legendarium_dust', inFluid: null,
            outItems: ['1x gtceu:legendarium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_legendarium_fluid', inItem: null, inFluid: 'gtceu:legendarium 1000',
            outItems: ['1x gtceu:legendarium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_draconiumawakened_dust', inItem: '1x gtceu:draconiumawakened_dust', inFluid: null,
            outItems: ['1x gtceu:draconiumawakened_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_draconiumawakened_fluid', inItem: null, inFluid: 'gtceu:draconiumawakened 1000',
            outItems: ['1x gtceu:draconiumawakened_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_adamantine_dust', inItem: '1x gtceu:adamantine_dust', inFluid: null,
            outItems: ['1x gtceu:adamantine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_adamantine_fluid', inItem: null, inFluid: 'gtceu:adamantine 1000',
            outItems: ['1x gtceu:adamantine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_starmetal_dust', inItem: '1x gtceu:starmetal_dust', inFluid: null,
            outItems: ['1x gtceu:starmetal_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_starmetal_fluid', inItem: null, inFluid: 'gtceu:starmetal 1000',
            outItems: ['1x gtceu:starmetal_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_orichalcum_dust', inItem: '1x gtceu:orichalcum_dust', inFluid: null,
            outItems: ['1x gtceu:orichalcum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_orichalcum_fluid', inItem: null, inFluid: 'gtceu:orichalcum 1000',
            outItems: ['1x gtceu:orichalcum_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_infuscolium_dust', inItem: '1x gtceu:infuscolium_dust', inFluid: null,
            outItems: ['1x gtceu:infuscolium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_infuscolium_fluid', inItem: null, inFluid: 'gtceu:infuscolium 1000',
            outItems: ['1x gtceu:infuscolium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enderium_dust', inItem: '1x gtceu:enderium_dust', inFluid: null,
            outItems: ['1x gtceu:enderium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enderium_fluid', inItem: null, inFluid: 'gtceu:enderium 1000',
            outItems: ['1x gtceu:enderium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_eternity_dust', inItem: '1x gtceu:eternity_dust', inFluid: null,
            outItems: ['1x gtceu:eternity_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_eternity_fluid', inItem: null, inFluid: 'gtceu:eternity 1000',
            outItems: ['1x gtceu:eternity_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magmatter_dust', inItem: '1x gtceu:magmatter_dust', inFluid: null,
            outItems: ['1x gtceu:magmatter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magmatter_fluid', inItem: null, inFluid: 'gtceu:magmatter 1000',
            outItems: ['1x gtceu:magmatter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_degenerate_rhenium_dust', inItem: '1x gtceu:degenerate_rhenium_dust', inFluid: null,
            outItems: ['1x gtceu:degenerate_rhenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_degenerate_rhenium_fluid', inItem: null, inFluid: 'gtceu:liquid_degenerate_rhenium 1000',
            outItems: ['1x gtceu:degenerate_rhenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_heavy_quark_degenerate_matter_dust', inItem: '1x gtceu:heavy_quark_degenerate_matter_dust', inFluid: null,
            outItems: ['1x gtceu:heavy_quark_degenerate_matter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_heavy_quark_degenerate_matter_fluid', inItem: null, inFluid: 'gtceu:heavy_quark_degenerate_matter 1000',
            outItems: ['1x gtceu:heavy_quark_degenerate_matter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_metastable_hassium_fluid', inItem: null, inFluid: 'gtceu:liquid_metastable_hassium 1000',
            outItems: [],
            outFluids: ['gtceu:metastable_hassium 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_inconel_625_dust', inItem: '1x gtceu:inconel_625_dust', inFluid: null,
            outItems: ['8x gtceu:nickel_dust', '6x gtceu:chromium_dust', '4x gtceu:molybdenum_dust', '4x gtceu:niobium_dust', '3x gtceu:titanium_dust', '2x gtceu:iron_dust', '2x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_inconel_625_fluid', inItem: null, inFluid: 'gtceu:inconel_625 1000',
            outItems: ['8x gtceu:nickel_dust', '6x gtceu:chromium_dust', '4x gtceu:molybdenum_dust', '4x gtceu:niobium_dust', '3x gtceu:titanium_dust', '2x gtceu:iron_dust', '2x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloy_n_75_dust', inItem: '1x gtceu:hastelloy_n_75_dust', inFluid: null,
            outItems: ['15x gtceu:nickel_dust', '9x gtceu:molybdenum_dust', '4x gtceu:chromium_dust', '2x gtceu:titanium_dust', '2x gtceu:erbium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloy_n_75_fluid', inItem: null, inFluid: 'gtceu:hastelloy_n_75 1000',
            outItems: ['15x gtceu:nickel_dust', '9x gtceu:molybdenum_dust', '4x gtceu:chromium_dust', '2x gtceu:titanium_dust', '2x gtceu:erbium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_metastable_oganesson_dust', inItem: '1x gtceu:metastable_oganesson_dust', inFluid: null,
            outItems: ['1x gtceu:metastable_oganesson_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_metastable_oganesson_fluid', inItem: null, inFluid: 'gtceu:metastable_oganesson 1000',
            outItems: ['1x gtceu:metastable_oganesson_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_quantumchromodynamically_confined_matter_dust', inItem: '1x gtceu:quantumchromodynamically_confined_matter_dust', inFluid: null,
            outItems: ['1x gtceu:quantumchromodynamically_confined_matter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_quantumchromodynamically_confined_matter_fluid', inItem: null, inFluid: 'gtceu:quantumchromodynamically_confined_matter 1000',
            outItems: ['1x gtceu:quantumchromodynamically_confined_matter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_transcendentmetal_dust', inItem: '1x gtceu:transcendentmetal_dust', inFluid: null,
            outItems: ['1x gtceu:transcendentmetal_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_transcendentmetal_fluid', inItem: null, inFluid: 'gtceu:transcendentmetal 1000',
            outItems: ['1x gtceu:transcendentmetal_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uruium_dust', inItem: '1x gtceu:uruium_dust', inFluid: null,
            outItems: ['1x gtceu:uruium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_uruium_fluid', inItem: null, inFluid: 'gtceu:uruium 1000',
            outItems: ['1x gtceu:uruium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnetohydrodynamicallyconstrainedstarmatter_dust', inItem: '1x gtceu:magnetohydrodynamicallyconstrainedstarmatter_dust', inFluid: null,
            outItems: ['1x gtceu:magnetohydrodynamicallyconstrainedstarmatter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnetohydrodynamicallyconstrainedstarmatter_fluid', inItem: null, inFluid: 'gtceu:magnetohydrodynamicallyconstrainedstarmatter 1000',
            outItems: ['1x gtceu:magnetohydrodynamicallyconstrainedstarmatter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_white_dwarf_mtter_dust', inItem: '1x gtceu:white_dwarf_mtter_dust', inFluid: null,
            outItems: ['1x gtceu:white_dwarf_mtter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_white_dwarf_mtter_fluid', inItem: null, inFluid: 'gtceu:white_dwarf_mtter 1000',
            outItems: ['1x gtceu:white_dwarf_mtter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_black_dwarf_mtter_dust', inItem: '1x gtceu:black_dwarf_mtter_dust', inFluid: null,
            outItems: ['1x gtceu:black_dwarf_mtter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_black_dwarf_mtter_fluid', inItem: null, inFluid: 'gtceu:black_dwarf_mtter 1000',
            outItems: ['1x gtceu:black_dwarf_mtter_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_astraltitanium_dust', inItem: '1x gtceu:astraltitanium_dust', inFluid: null,
            outItems: ['1x gtceu:astraltitanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_astraltitanium_fluid', inItem: null, inFluid: 'gtceu:astraltitanium 1000',
            outItems: ['1x gtceu:astraltitanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_celestialtungsten_dust', inItem: '1x gtceu:celestialtungsten_dust', inFluid: null,
            outItems: ['1x gtceu:celestialtungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_celestialtungsten_fluid', inItem: null, inFluid: 'gtceu:celestialtungsten 1000',
            outItems: ['1x gtceu:celestialtungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enderite_dust', inItem: '1x gtceu:enderite_dust', inFluid: null,
            outItems: ['3x gtceu:enderium_dust', '2x gtceu:beryllium_dust', '8x gtceu:potassium_dust', '1x gtceu:manganese_dust', '1x gtceu:phosphorus_dust', '1x gtceu:magnesium_dust', '2x gtceu:boron_dust', '4x gtceu:barium_dust', '2x gtceu:calcium_dust', '10x gtceu:copper_dust', '1x gtceu:uranium_dust', '3x gtceu:platinum_dust', '1x gtceu:samarium_dust', '1x gtceu:iron_dust', '1x gtceu:arsenic_dust', '4x gtceu:indium_dust', '2x gtceu:tin_dust', '1x gtceu:titanium_dust'],
            outFluids: ['gtceu:nitrogen 10000', 'gtceu:mercury 1000', 'gtceu:oxygen 23000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enderite_fluid', inItem: null, inFluid: 'gtceu:enderite 1000',
            outItems: ['3x gtceu:enderium_dust', '2x gtceu:beryllium_dust', '8x gtceu:potassium_dust', '1x gtceu:manganese_dust', '1x gtceu:phosphorus_dust', '1x gtceu:magnesium_dust', '2x gtceu:boron_dust', '4x gtceu:barium_dust', '2x gtceu:calcium_dust', '10x gtceu:copper_dust', '1x gtceu:uranium_dust', '3x gtceu:platinum_dust', '1x gtceu:samarium_dust', '1x gtceu:iron_dust', '1x gtceu:arsenic_dust', '4x gtceu:indium_dust', '2x gtceu:tin_dust', '1x gtceu:titanium_dust'],
            outFluids: ['gtceu:nitrogen 10000', 'gtceu:mercury 1000', 'gtceu:oxygen 23000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadriatictaranium_dust', inItem: '1x gtceu:naquadriatictaranium_dust', inFluid: null,
            outItems: ['1x gtceu:naquadria_dust', '1x gtceu:taranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadriatictaranium_fluid', inItem: null, inFluid: 'gtceu:naquadriatictaranium 1000',
            outItems: ['1x gtceu:naquadria_dust', '1x gtceu:taranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_highurabilityompoundteel_dust', inItem: '1x gtceu:highurabilityompoundteel_dust', inFluid: null,
            outItems: ['320x gtceu:iron_dust', '312x gtceu:tungsten_dust', '60x gtceu:chromium_dust', '120x gtceu:molybdenum_dust', '60x gtceu:vanadium_dust', '21x gtceu:iridium_dust', '9x gtceu:osmium_dust', '6x gtceu:ruthenium_dust', '30x gtceu:silicon_dust', '24x gtceu:bismuth_dust', '36x gtceu:tellurium_dust', '2x gtceu:zirconium_dust', '1x gtceu:plutonium_dust'],
            outFluids: ['gtceu:oxygen 64000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_highurabilityompoundteel_fluid', inItem: null, inFluid: 'gtceu:highurabilityompoundteel 1000',
            outItems: ['320x gtceu:iron_dust', '312x gtceu:tungsten_dust', '60x gtceu:chromium_dust', '120x gtceu:molybdenum_dust', '60x gtceu:vanadium_dust', '21x gtceu:iridium_dust', '9x gtceu:osmium_dust', '6x gtceu:ruthenium_dust', '30x gtceu:silicon_dust', '24x gtceu:bismuth_dust', '36x gtceu:tellurium_dust', '2x gtceu:zirconium_dust', '1x gtceu:plutonium_dust'],
            outFluids: ['gtceu:oxygen 64000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_germaniumtungstennitride_dust', inItem: '1x gtceu:germaniumtungstennitride_dust', inFluid: null,
            outItems: ['3x gtceu:germanium_dust', '3x gtceu:tungsten_dust'],
            outFluids: ['gtceu:nitrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_germaniumtungstennitride_fluid', inItem: null, inFluid: 'gtceu:germaniumtungstennitride 1000',
            outItems: ['3x gtceu:germanium_dust', '3x gtceu:tungsten_dust'],
            outFluids: ['gtceu:nitrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_black_titanium_dust', inItem: '1x gtceu:black_titanium_dust', inFluid: null,
            outItems: ['26x gtceu:titanium_dust', '6x gtceu:lanthanum_dust', '4x gtceu:tungsten_dust', '3x gtceu:cobalt_dust', '2x gtceu:manganese_dust', '2x gtceu:phosphorus_dust', '2x gtceu:palladium_dust', '1x gtceu:niobium_dust'],
            outFluids: ['gtceu:argon 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_black_titanium_fluid', inItem: null, inFluid: 'gtceu:black_titanium 1000',
            outItems: ['26x gtceu:titanium_dust', '6x gtceu:lanthanum_dust', '4x gtceu:tungsten_dust', '3x gtceu:cobalt_dust', '2x gtceu:manganese_dust', '2x gtceu:phosphorus_dust', '2x gtceu:palladium_dust', '1x gtceu:niobium_dust'],
            outFluids: ['gtceu:argon 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trinium_titanium_dust', inItem: '1x gtceu:trinium_titanium_dust', inFluid: null,
            outItems: ['2x gtceu:trinium_dust', '1x gtceu:titanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trinium_titanium_fluid', inItem: null, inFluid: 'gtceu:trinium_titanium 1000',
            outItems: ['2x gtceu:trinium_dust', '1x gtceu:titanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cinobite_dust', inItem: '1x gtceu:cinobite_dust', inFluid: null,
            outItems: ['80x gtceu:iron_dust', '16x gtceu:nickel_dust', '16x gtceu:tungsten_dust', '8x gtceu:niobium_dust', '8x gtceu:cobalt_dust', '4x gtceu:naquadria_dust', '3x gtceu:terbium_dust', '2x gtceu:aluminium_dust', '1x gtceu:tin_dust', '6x gtceu:titanium_dust', '3x gtceu:iridium_dust', '1x gtceu:osmium_dust'],
            outFluids: ['gtceu:mercury 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cinobite_fluid', inItem: null, inFluid: 'gtceu:cinobite 1000',
            outItems: ['80x gtceu:iron_dust', '16x gtceu:nickel_dust', '16x gtceu:tungsten_dust', '8x gtceu:niobium_dust', '8x gtceu:cobalt_dust', '4x gtceu:naquadria_dust', '3x gtceu:terbium_dust', '2x gtceu:aluminium_dust', '1x gtceu:tin_dust', '6x gtceu:titanium_dust', '3x gtceu:iridium_dust', '1x gtceu:osmium_dust'],
            outFluids: ['gtceu:mercury 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloyx_78_dust', inItem: '1x gtceu:hastelloyx_78_dust', inFluid: null,
            outItems: ['20x gtceu:naquadah_dust', '30x gtceu:iridium_dust', '10x gtceu:osmium_dust', '10x gtceu:trinium_dust', '5x gtceu:rhenium_dust', '4x gtceu:naquadria_dust', '4x gtceu:tritanium_dust', '1x gtceu:tungsten_dust', '1x gtceu:carbon_dust', '1x gtceu:promethium_dust', '1x gtceu:mendelevium_dust', '1x gtceu:praseodymium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloyx_78_fluid', inItem: null, inFluid: 'gtceu:hastelloyx_78 1000',
            outItems: ['20x gtceu:naquadah_dust', '30x gtceu:iridium_dust', '10x gtceu:osmium_dust', '10x gtceu:trinium_dust', '5x gtceu:rhenium_dust', '4x gtceu:naquadria_dust', '4x gtceu:tritanium_dust', '1x gtceu:tungsten_dust', '1x gtceu:carbon_dust', '1x gtceu:promethium_dust', '1x gtceu:mendelevium_dust', '1x gtceu:praseodymium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloyk_243_dust', inItem: '1x gtceu:hastelloyk_243_dust', inFluid: null,
            outItems: ['100x gtceu:naquadah_dust', '150x gtceu:iridium_dust', '50x gtceu:osmium_dust', '50x gtceu:trinium_dust', '25x gtceu:rhenium_dust', '20x gtceu:naquadria_dust', '24x gtceu:tritanium_dust', '9x gtceu:tungsten_dust', '9x gtceu:carbon_dust', '6x gtceu:promethium_dust', '6x gtceu:mendelevium_dust', '6x gtceu:praseodymium_dust', '2x gtceu:niobium_dust', '1x gtceu:holmium_dust'],
            outFluids: ['gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloyk_243_fluid', inItem: null, inFluid: 'gtceu:hastelloyk_243 1000',
            outItems: ['100x gtceu:naquadah_dust', '150x gtceu:iridium_dust', '50x gtceu:osmium_dust', '50x gtceu:trinium_dust', '25x gtceu:rhenium_dust', '20x gtceu:naquadria_dust', '24x gtceu:tritanium_dust', '9x gtceu:tungsten_dust', '9x gtceu:carbon_dust', '6x gtceu:promethium_dust', '6x gtceu:mendelevium_dust', '6x gtceu:praseodymium_dust', '2x gtceu:niobium_dust', '1x gtceu:holmium_dust'],
            outFluids: ['gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vibramantium_dust', inItem: '1x gtceu:vibramantium_dust', inFluid: null,
            outItems: ['1x gtceu:vibranium_dust', '3x gtceu:adamantium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vibramantium_fluid', inItem: null, inFluid: 'gtceu:vibramantium 1000',
            outItems: ['1x gtceu:vibranium_dust', '3x gtceu:adamantium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_eglin_steel_dust', inItem: '1x gtceu:eglin_steel_dust', inFluid: null,
            outItems: ['15x gtceu:iron_dust', '1x gtceu:aluminium_dust', '1x gtceu:chromium_dust', '5x gtceu:nickel_dust', '1x gtceu:sulfur_dust', '1x gtceu:silicon_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_eglin_steel_fluid', inItem: null, inFluid: 'gtceu:eglin_steel 1000',
            outItems: ['15x gtceu:iron_dust', '1x gtceu:aluminium_dust', '1x gtceu:chromium_dust', '5x gtceu:nickel_dust', '1x gtceu:sulfur_dust', '1x gtceu:silicon_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_inconel_792_dust', inItem: '1x gtceu:inconel_792_dust', inFluid: null,
            outItems: ['6x gtceu:nickel_dust', '1x gtceu:niobium_dust', '2x gtceu:aluminium_dust', '1x gtceu:chromium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_inconel_792_fluid', inItem: null, inFluid: 'gtceu:inconel_792 1000',
            outItems: ['6x gtceu:nickel_dust', '1x gtceu:niobium_dust', '2x gtceu:aluminium_dust', '1x gtceu:chromium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pikyonium_dust', inItem: '1x gtceu:pikyonium_dust', inFluid: null,
            outItems: ['73x gtceu:nickel_dust', '8x gtceu:niobium_dust', '21x gtceu:aluminium_dust', '13x gtceu:chromium_dust', '79x gtceu:iron_dust', '5x gtceu:sulfur_dust', '5x gtceu:silicon_dust', '5x gtceu:carbon_dust', '4x gtceu:enriched_naquadah_dust', '3x gtceu:cerium_dust', '2x gtceu:antimony_dust', '2x gtceu:platinum_dust', '1x gtceu:ytterbium_dust', '4x gtceu:tungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pikyonium_fluid', inItem: null, inFluid: 'gtceu:pikyonium 1000',
            outItems: ['73x gtceu:nickel_dust', '8x gtceu:niobium_dust', '21x gtceu:aluminium_dust', '13x gtceu:chromium_dust', '79x gtceu:iron_dust', '5x gtceu:sulfur_dust', '5x gtceu:silicon_dust', '5x gtceu:carbon_dust', '4x gtceu:enriched_naquadah_dust', '3x gtceu:cerium_dust', '2x gtceu:antimony_dust', '2x gtceu:platinum_dust', '1x gtceu:ytterbium_dust', '4x gtceu:tungsten_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloy_n_dust', inItem: '1x gtceu:hastelloy_n_dust', inFluid: null,
            outItems: ['2x gtceu:iridium_dust', '4x gtceu:molybdenum_dust', '2x gtceu:chromium_dust', '2x gtceu:titanium_dust', '15x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hastelloy_n_fluid', inItem: null, inFluid: 'gtceu:hastelloy_n 1000',
            outItems: ['2x gtceu:iridium_dust', '4x gtceu:molybdenum_dust', '2x gtceu:chromium_dust', '2x gtceu:titanium_dust', '15x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aluminium_bronze_dust', inItem: '1x gtceu:aluminium_bronze_dust', inFluid: null,
            outItems: ['1x gtceu:aluminium_dust', '6x gtceu:tin_dust', '18x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aluminium_bronze_fluid', inItem: null, inFluid: 'gtceu:aluminium_bronze 1000',
            outItems: ['1x gtceu:aluminium_dust', '6x gtceu:tin_dust', '18x gtceu:copper_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lafium_dust', inItem: '1x gtceu:lafium_dust', inFluid: null,
            outItems: ['16x gtceu:iridium_dust', '32x gtceu:molybdenum_dust', '16x gtceu:chromium_dust', '16x gtceu:titanium_dust', '122x gtceu:nickel_dust', '4x gtceu:naquadah_dust', '2x gtceu:samarium_dust', '4x gtceu:tungsten_dust', '6x gtceu:aluminium_dust', '2x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lafium_fluid', inItem: null, inFluid: 'gtceu:lafium 1000',
            outItems: ['16x gtceu:iridium_dust', '32x gtceu:molybdenum_dust', '16x gtceu:chromium_dust', '16x gtceu:titanium_dust', '122x gtceu:nickel_dust', '4x gtceu:naquadah_dust', '2x gtceu:samarium_dust', '4x gtceu:tungsten_dust', '6x gtceu:aluminium_dust', '2x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grisium_dust', inItem: '1x gtceu:grisium_dust', inFluid: null,
            outItems: ['9x gtceu:titanium_dust', '9x gtceu:carbon_dust', '9x gtceu:potassium_dust', '9x gtceu:lithium_dust', '9x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grisium_fluid', inItem: null, inFluid: 'gtceu:grisium 1000',
            outItems: ['9x gtceu:titanium_dust', '9x gtceu:carbon_dust', '9x gtceu:potassium_dust', '9x gtceu:lithium_dust', '9x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_stellite_dust', inItem: '1x gtceu:stellite_dust', inFluid: null,
            outItems: ['9x gtceu:cobalt_dust', '9x gtceu:chromium_dust', '5x gtceu:manganese_dust', '2x gtceu:titanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_stellite_fluid', inItem: null, inFluid: 'gtceu:stellite 1000',
            outItems: ['9x gtceu:cobalt_dust', '9x gtceu:chromium_dust', '5x gtceu:manganese_dust', '2x gtceu:titanium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silicon_carbide_dust', inItem: '1x gtceu:silicon_carbide_dust', inFluid: null,
            outItems: ['1x gtceu:silicon_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silicon_carbide_fluid', inItem: null, inFluid: 'gtceu:silicon_carbide 1000',
            outItems: ['1x gtceu:silicon_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluxed_electrum_dust', inItem: '1x gtceu:fluxed_electrum_dust', inFluid: null,
            outItems: ['6x gtceu:tin_dust', '3x gtceu:lead_dust', '1x gtceu:antimony_dust', '16x gtceu:gold_dust', '1x gtceu:naquadah_dust', '18x gtceu:silver_dust', '34x gtceu:copper_dust', '1x gtceu:bismuth_dust', '2x gtceu:zinc_dust', '28x gtceu:iron_dust', '8x gtceu:nickel_dust', '2x gtceu:infused_gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluxed_electrum_fluid', inItem: null, inFluid: 'gtceu:fluxed_electrum 1000',
            outItems: ['6x gtceu:tin_dust', '3x gtceu:lead_dust', '1x gtceu:antimony_dust', '16x gtceu:gold_dust', '1x gtceu:naquadah_dust', '18x gtceu:silver_dust', '34x gtceu:copper_dust', '1x gtceu:bismuth_dust', '2x gtceu:zinc_dust', '28x gtceu:iron_dust', '8x gtceu:nickel_dust', '2x gtceu:infused_gold_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tanmolyium_dust', inItem: '1x gtceu:tanmolyium_dust', inFluid: null,
            outItems: ['5x gtceu:titanium_dust', '5x gtceu:molybdenum_dust', '2x gtceu:vanadium_dust', '3x gtceu:chromium_dust', '1x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tanmolyium_fluid', inItem: null, inFluid: 'gtceu:tanmolyium 1000',
            outItems: ['5x gtceu:titanium_dust', '5x gtceu:molybdenum_dust', '2x gtceu:vanadium_dust', '3x gtceu:chromium_dust', '1x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dalisenite_dust', inItem: '1x gtceu:dalisenite_dust', inFluid: null,
            outItems: ['3x gtceu:erbium_dust', '10x gtceu:tungsten_dust', '1x gtceu:naquadah_dust', '9x gtceu:niobium_dust', '14x gtceu:titanium_dust', '7x gtceu:quantanium_dust', '42x gtceu:palladium_dust', '14x gtceu:rhodium_dust', '5x gtceu:molybdenum_dust', '2x gtceu:vanadium_dust', '3x gtceu:chromium_dust', '1x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dalisenite_fluid', inItem: null, inFluid: 'gtceu:dalisenite 1000',
            outItems: ['3x gtceu:erbium_dust', '10x gtceu:tungsten_dust', '1x gtceu:naquadah_dust', '9x gtceu:niobium_dust', '14x gtceu:titanium_dust', '7x gtceu:quantanium_dust', '42x gtceu:palladium_dust', '14x gtceu:rhodium_dust', '5x gtceu:molybdenum_dust', '2x gtceu:vanadium_dust', '3x gtceu:chromium_dust', '1x gtceu:aluminium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_superheavy_l_alloy_dust', inItem: '1x gtceu:superheavy_l_alloy_dust', inFluid: null,
            outItems: ['1x gtceu:rutherfordium_dust', '1x gtceu:dubnium_dust', '1x gtceu:seaborgium_dust', '1x gtceu:bohrium_dust', '1x gtceu:hassium_dust', '1x gtceu:meitnerium_dust', '1x gtceu:darmstadtium_dust', '1x gtceu:roentgenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_superheavy_l_alloy_fluid', inItem: null, inFluid: 'gtceu:superheavy_l_alloy 1000',
            outItems: ['1x gtceu:rutherfordium_dust', '1x gtceu:dubnium_dust', '1x gtceu:seaborgium_dust', '1x gtceu:bohrium_dust', '1x gtceu:hassium_dust', '1x gtceu:meitnerium_dust', '1x gtceu:darmstadtium_dust', '1x gtceu:roentgenium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_superheavy_h_alloy_dust', inItem: '1x gtceu:superheavy_h_alloy_dust', inFluid: null,
            outItems: ['1x gtceu:copernicium_dust', '1x gtceu:nihonium_dust', '1x gtceu:flerovium_dust', '1x gtceu:moscovium_dust', '1x gtceu:livermorium_dust', '1x gtceu:tennessine_dust', '1x gtceu:oganesson_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_superheavy_h_alloy_fluid', inItem: null, inFluid: 'gtceu:superheavy_h_alloy 1000',
            outItems: ['1x gtceu:copernicium_dust', '1x gtceu:nihonium_dust', '1x gtceu:flerovium_dust', '1x gtceu:moscovium_dust', '1x gtceu:livermorium_dust', '1x gtceu:tennessine_dust', '1x gtceu:oganesson_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zirconium_carbide_dust', inItem: '1x gtceu:zirconium_carbide_dust', inFluid: null,
            outItems: ['1x gtceu:zirconium_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zirconium_carbide_fluid', inItem: null, inFluid: 'gtceu:zirconium_carbide 1000',
            outItems: ['1x gtceu:zirconium_dust', '1x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mar_m_200_steel_dust', inItem: '1x gtceu:mar_m_200_steel_dust', inFluid: null,
            outItems: ['2x gtceu:niobium_dust', '9x gtceu:chromium_dust', '5x gtceu:aluminium_dust', '2x gtceu:titanium_dust', '10x gtceu:cobalt_dust', '13x gtceu:tungsten_dust', '18x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mar_m_200_steel_fluid', inItem: null, inFluid: 'gtceu:mar_m_200_steel 1000',
            outItems: ['2x gtceu:niobium_dust', '9x gtceu:chromium_dust', '5x gtceu:aluminium_dust', '2x gtceu:titanium_dust', '10x gtceu:cobalt_dust', '13x gtceu:tungsten_dust', '18x gtceu:nickel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tantalloy_61_dust', inItem: '1x gtceu:tantalloy_61_dust', inFluid: null,
            outItems: ['13x gtceu:tantalum_dust', '12x gtceu:tungsten_dust', '6x gtceu:titanium_dust', '4x gtceu:yttrium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tantalloy_61_fluid', inItem: null, inFluid: 'gtceu:tantalloy_61 1000',
            outItems: ['13x gtceu:tantalum_dust', '12x gtceu:tungsten_dust', '6x gtceu:titanium_dust', '4x gtceu:yttrium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_reactor_steel_dust', inItem: '1x gtceu:reactor_steel_dust', inFluid: null,
            outItems: ['15x gtceu:iron_dust', '1x gtceu:niobium_dust', '4x gtceu:vanadium_dust', '2x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_reactor_steel_fluid', inItem: null, inFluid: 'gtceu:reactor_steel 1000',
            outItems: ['15x gtceu:iron_dust', '1x gtceu:niobium_dust', '4x gtceu:vanadium_dust', '2x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lanthanoids_1_dust', inItem: '1x gtceu:lanthanoids_1_dust', inFluid: null,
            outItems: ['1x gtceu:lanthanum_dust', '1x gtceu:cerium_dust', '1x gtceu:praseodymium_dust', '1x gtceu:neodymium_dust', '1x gtceu:promethium_dust', '1x gtceu:samarium_dust', '1x gtceu:europium_dust', '1x gtceu:gadolinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lanthanoids_2_dust', inItem: '1x gtceu:lanthanoids_2_dust', inFluid: null,
            outItems: ['1x gtceu:terbium_dust', '1x gtceu:dysprosium_dust', '1x gtceu:holmium_dust', '1x gtceu:erbium_dust', '1x gtceu:thulium_dust', '1x gtceu:ytterbium_dust', '1x gtceu:lutetium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rareearth_dust', inItem: '1x gtceu:rareearth_dust', inFluid: null,
            outItems: ['1x gtceu:scandium_dust', '1x gtceu:yttrium_dust', '1x gtceu:lanthanum_dust', '1x gtceu:cerium_dust', '1x gtceu:praseodymium_dust', '1x gtceu:neodymium_dust', '1x gtceu:promethium_dust', '1x gtceu:samarium_dust', '1x gtceu:europium_dust', '1x gtceu:gadolinium_dust', '1x gtceu:terbium_dust', '1x gtceu:dysprosium_dust', '1x gtceu:holmium_dust', '1x gtceu:erbium_dust', '1x gtceu:thulium_dust', '1x gtceu:ytterbium_dust', '1x gtceu:lutetium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rareearth_fluid', inItem: null, inFluid: 'gtceu:rareearth 1000',
            outItems: ['1x gtceu:scandium_dust', '1x gtceu:yttrium_dust', '1x gtceu:lanthanum_dust', '1x gtceu:cerium_dust', '1x gtceu:praseodymium_dust', '1x gtceu:neodymium_dust', '1x gtceu:promethium_dust', '1x gtceu:samarium_dust', '1x gtceu:europium_dust', '1x gtceu:gadolinium_dust', '1x gtceu:terbium_dust', '1x gtceu:dysprosium_dust', '1x gtceu:holmium_dust', '1x gtceu:erbium_dust', '1x gtceu:thulium_dust', '1x gtceu:ytterbium_dust', '1x gtceu:lutetium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_actinoids_1_dust', inItem: '1x gtceu:actinoids_1_dust', inFluid: null,
            outItems: ['1x gtceu:actinium_dust', '1x gtceu:thorium_dust', '1x gtceu:protactinium_dust', '1x gtceu:uranium_dust', '1x gtceu:neptunium_dust', '1x gtceu:plutonium_dust', '1x gtceu:americium_dust', '1x gtceu:curium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_actinoids_2_dust', inItem: '1x gtceu:actinoids_2_dust', inFluid: null,
            outItems: ['1x gtceu:berkelium_dust', '1x gtceu:californium_dust', '1x gtceu:einsteinium_dust', '1x gtceu:fermium_dust', '1x gtceu:mendelevium_dust', '1x gtceu:nobelium_dust', '1x gtceu:lawrencium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_actinoids_dust', inItem: '1x gtceu:actinoids_dust', inFluid: null,
            outItems: ['1x gtceu:actinium_dust', '1x gtceu:thorium_dust', '1x gtceu:protactinium_dust', '1x gtceu:uranium_dust', '1x gtceu:neptunium_dust', '1x gtceu:plutonium_dust', '1x gtceu:americium_dust', '1x gtceu:curium_dust', '1x gtceu:berkelium_dust', '1x gtceu:californium_dust', '1x gtceu:einsteinium_dust', '1x gtceu:fermium_dust', '1x gtceu:mendelevium_dust', '1x gtceu:nobelium_dust', '1x gtceu:lawrencium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_alkaline_dust', inItem: '1x gtceu:alkaline_dust', inFluid: null,
            outItems: ['1x gtceu:lithium_dust', '1x gtceu:sodium_dust', '1x gtceu:potassium_dust', '1x gtceu:rubidium_dust', '1x gtceu:caesium_dust', '1x gtceu:francium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_alkaline_earth_dust', inItem: '1x gtceu:alkaline_earth_dust', inFluid: null,
            outItems: ['1x gtceu:beryllium_dust', '1x gtceu:magnesium_dust', '1x gtceu:calcium_dust', '1x gtceu:strontium_dust', '1x gtceu:barium_dust', '1x gtceu:radium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_transition_1_dust', inItem: '1x gtceu:transition_1_dust', inFluid: null,
            outItems: ['1x gtceu:titanium_dust', '1x gtceu:vanadium_dust', '1x gtceu:chromium_dust', '1x gtceu:manganese_dust', '1x gtceu:iron_dust', '1x gtceu:cobalt_dust', '1x gtceu:nickel_dust', '1x gtceu:copper_dust', '1x gtceu:zinc_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_transition_2_dust', inItem: '1x gtceu:transition_2_dust', inFluid: null,
            outItems: ['1x gtceu:zirconium_dust', '1x gtceu:niobium_dust', '1x gtceu:molybdenum_dust', '1x gtceu:technetium_dust', '1x gtceu:ruthenium_dust', '1x gtceu:rhodium_dust', '1x gtceu:palladium_dust', '1x gtceu:silver_dust', '1x gtceu:cadmium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_transition_3_dust', inItem: '1x gtceu:transition_3_dust', inFluid: null,
            outItems: ['1x gtceu:hafnium_dust', '1x gtceu:tantalum_dust', '1x gtceu:tungsten_dust', '1x gtceu:rhenium_dust', '1x gtceu:osmium_dust', '1x gtceu:iridium_dust', '1x gtceu:platinum_dust', '1x gtceu:gold_dust'],
            outFluids: ['gtceu:mercury 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_transition_dust', inItem: '1x gtceu:transition_dust', inFluid: null,
            outItems: ['1x gtceu:titanium_dust', '1x gtceu:vanadium_dust', '1x gtceu:chromium_dust', '1x gtceu:manganese_dust', '1x gtceu:iron_dust', '1x gtceu:cobalt_dust', '1x gtceu:nickel_dust', '1x gtceu:copper_dust', '1x gtceu:zinc_dust', '1x gtceu:zirconium_dust', '1x gtceu:niobium_dust', '1x gtceu:molybdenum_dust', '1x gtceu:technetium_dust', '1x gtceu:ruthenium_dust', '1x gtceu:rhodium_dust', '1x gtceu:palladium_dust', '1x gtceu:silver_dust', '1x gtceu:cadmium_dust', '1x gtceu:hafnium_dust', '1x gtceu:tantalum_dust', '1x gtceu:tungsten_dust', '1x gtceu:rhenium_dust', '1x gtceu:osmium_dust', '1x gtceu:iridium_dust', '1x gtceu:platinum_dust', '1x gtceu:gold_dust'],
            outFluids: ['gtceu:mercury 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_transition_fluid', inItem: null, inFluid: 'gtceu:transition 1000',
            outItems: ['1x gtceu:titanium_dust', '1x gtceu:vanadium_dust', '1x gtceu:chromium_dust', '1x gtceu:manganese_dust', '1x gtceu:iron_dust', '1x gtceu:cobalt_dust', '1x gtceu:nickel_dust', '1x gtceu:copper_dust', '1x gtceu:zinc_dust', '1x gtceu:zirconium_dust', '1x gtceu:niobium_dust', '1x gtceu:molybdenum_dust', '1x gtceu:technetium_dust', '1x gtceu:ruthenium_dust', '1x gtceu:rhodium_dust', '1x gtceu:palladium_dust', '1x gtceu:silver_dust', '1x gtceu:cadmium_dust', '1x gtceu:hafnium_dust', '1x gtceu:tantalum_dust', '1x gtceu:tungsten_dust', '1x gtceu:rhenium_dust', '1x gtceu:osmium_dust', '1x gtceu:iridium_dust', '1x gtceu:platinum_dust', '1x gtceu:gold_dust'],
            outFluids: ['gtceu:mercury 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_poor_dust', inItem: '1x gtceu:poor_dust', inFluid: null,
            outItems: ['1x gtceu:aluminium_dust', '1x gtceu:gallium_dust', '1x gtceu:indium_dust', '1x gtceu:tin_dust', '1x gtceu:thallium_dust', '1x gtceu:lead_dust', '1x gtceu:bismuth_dust', '1x gtceu:polonium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_metalloid_dust', inItem: '1x gtceu:metalloid_dust', inFluid: null,
            outItems: ['1x gtceu:boron_dust', '1x gtceu:silicon_dust', '1x gtceu:germanium_dust', '1x gtceu:arsenic_dust', '1x gtceu:antimony_dust', '1x gtceu:tellurium_dust', '1x gtceu:astatine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_not_found_fluid', inItem: null, inFluid: 'gtceu:not_found 1000',
            outItems: ['1x gtceu:carbon_dust', '1x gtceu:phosphorus_dust', '1x gtceu:sulfur_dust', '1x gtceu:selenium_dust', '1x gtceu:iodine_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000', 'gtceu:fluorine 1000', 'gtceu:chlorine 1000', 'gtceu:bromine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_noble_gas_fluid', inItem: null, inFluid: 'gtceu:noble_gas 1000',
            outItems: [],
            outFluids: ['gtceu:helium 1000', 'gtceu:neon 1000', 'gtceu:argon 1000', 'gtceu:krypton 1000', 'gtceu:xenon 1000', 'gtceu:radon 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_periodicium_dust', inItem: '1x gtceu:periodicium_dust', inFluid: null,
            outItems: ['1x gtceu:carbon_dust', '1x gtceu:phosphorus_dust', '1x gtceu:sulfur_dust', '1x gtceu:selenium_dust', '1x gtceu:iodine_dust', '1x gtceu:boron_dust', '1x gtceu:silicon_dust', '1x gtceu:germanium_dust', '1x gtceu:arsenic_dust', '1x gtceu:antimony_dust', '1x gtceu:tellurium_dust', '1x gtceu:astatine_dust', '1x gtceu:aluminium_dust', '1x gtceu:gallium_dust', '1x gtceu:indium_dust', '1x gtceu:tin_dust', '1x gtceu:thallium_dust', '1x gtceu:lead_dust', '1x gtceu:bismuth_dust', '1x gtceu:polonium_dust', '1x gtceu:titanium_dust', '1x gtceu:vanadium_dust', '1x gtceu:chromium_dust', '1x gtceu:manganese_dust', '1x gtceu:iron_dust', '1x gtceu:cobalt_dust', '1x gtceu:nickel_dust', '1x gtceu:copper_dust', '1x gtceu:zinc_dust', '1x gtceu:zirconium_dust', '1x gtceu:niobium_dust', '1x gtceu:molybdenum_dust', '1x gtceu:technetium_dust', '1x gtceu:ruthenium_dust', '1x gtceu:rhodium_dust', '1x gtceu:palladium_dust', '1x gtceu:silver_dust', '1x gtceu:cadmium_dust', '1x gtceu:hafnium_dust', '1x gtceu:tantalum_dust', '1x gtceu:tungsten_dust', '1x gtceu:rhenium_dust', '1x gtceu:osmium_dust', '1x gtceu:iridium_dust', '1x gtceu:platinum_dust', '1x gtceu:gold_dust', '1x gtceu:beryllium_dust', '1x gtceu:magnesium_dust', '1x gtceu:calcium_dust', '1x gtceu:strontium_dust', '1x gtceu:barium_dust', '1x gtceu:radium_dust', '1x gtceu:scandium_dust', '1x gtceu:yttrium_dust', '1x gtceu:lanthanum_dust', '1x gtceu:cerium_dust', '1x gtceu:praseodymium_dust', '1x gtceu:neodymium_dust', '1x gtceu:promethium_dust', '1x gtceu:samarium_dust', '1x gtceu:europium_dust', '1x gtceu:gadolinium_dust', '1x gtceu:terbium_dust', '1x gtceu:dysprosium_dust', '1x gtceu:holmium_dust', '1x gtceu:erbium_dust', '1x gtceu:thulium_dust', '1x gtceu:ytterbium_dust', '1x gtceu:lutetium_dust', '1x gtceu:lithium_dust', '1x gtceu:sodium_dust', '1x gtceu:potassium_dust', '1x gtceu:rubidium_dust', '1x gtceu:caesium_dust', '1x gtceu:francium_dust', '1x gtceu:actinium_dust', '1x gtceu:thorium_dust', '1x gtceu:protactinium_dust', '1x gtceu:uranium_dust', '1x gtceu:neptunium_dust', '1x gtceu:plutonium_dust', '1x gtceu:americium_dust', '1x gtceu:curium_dust', '1x gtceu:berkelium_dust', '1x gtceu:californium_dust', '1x gtceu:einsteinium_dust', '1x gtceu:fermium_dust', '1x gtceu:mendelevium_dust', '1x gtceu:nobelium_dust', '1x gtceu:lawrencium_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000', 'gtceu:fluorine 1000', 'gtceu:chlorine 1000', 'gtceu:bromine 1000', 'gtceu:helium 1000', 'gtceu:neon 1000', 'gtceu:argon 1000', 'gtceu:krypton 1000', 'gtceu:xenon 1000', 'gtceu:radon 1000', 'gtceu:mercury 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_periodicium_fluid', inItem: null, inFluid: 'gtceu:periodicium 1000',
            outItems: ['1x gtceu:carbon_dust', '1x gtceu:phosphorus_dust', '1x gtceu:sulfur_dust', '1x gtceu:selenium_dust', '1x gtceu:iodine_dust', '1x gtceu:boron_dust', '1x gtceu:silicon_dust', '1x gtceu:germanium_dust', '1x gtceu:arsenic_dust', '1x gtceu:antimony_dust', '1x gtceu:tellurium_dust', '1x gtceu:astatine_dust', '1x gtceu:aluminium_dust', '1x gtceu:gallium_dust', '1x gtceu:indium_dust', '1x gtceu:tin_dust', '1x gtceu:thallium_dust', '1x gtceu:lead_dust', '1x gtceu:bismuth_dust', '1x gtceu:polonium_dust', '1x gtceu:titanium_dust', '1x gtceu:vanadium_dust', '1x gtceu:chromium_dust', '1x gtceu:manganese_dust', '1x gtceu:iron_dust', '1x gtceu:cobalt_dust', '1x gtceu:nickel_dust', '1x gtceu:copper_dust', '1x gtceu:zinc_dust', '1x gtceu:zirconium_dust', '1x gtceu:niobium_dust', '1x gtceu:molybdenum_dust', '1x gtceu:technetium_dust', '1x gtceu:ruthenium_dust', '1x gtceu:rhodium_dust', '1x gtceu:palladium_dust', '1x gtceu:silver_dust', '1x gtceu:cadmium_dust', '1x gtceu:hafnium_dust', '1x gtceu:tantalum_dust', '1x gtceu:tungsten_dust', '1x gtceu:rhenium_dust', '1x gtceu:osmium_dust', '1x gtceu:iridium_dust', '1x gtceu:platinum_dust', '1x gtceu:gold_dust', '1x gtceu:beryllium_dust', '1x gtceu:magnesium_dust', '1x gtceu:calcium_dust', '1x gtceu:strontium_dust', '1x gtceu:barium_dust', '1x gtceu:radium_dust', '1x gtceu:scandium_dust', '1x gtceu:yttrium_dust', '1x gtceu:lanthanum_dust', '1x gtceu:cerium_dust', '1x gtceu:praseodymium_dust', '1x gtceu:neodymium_dust', '1x gtceu:promethium_dust', '1x gtceu:samarium_dust', '1x gtceu:europium_dust', '1x gtceu:gadolinium_dust', '1x gtceu:terbium_dust', '1x gtceu:dysprosium_dust', '1x gtceu:holmium_dust', '1x gtceu:erbium_dust', '1x gtceu:thulium_dust', '1x gtceu:ytterbium_dust', '1x gtceu:lutetium_dust', '1x gtceu:lithium_dust', '1x gtceu:sodium_dust', '1x gtceu:potassium_dust', '1x gtceu:rubidium_dust', '1x gtceu:caesium_dust', '1x gtceu:francium_dust', '1x gtceu:actinium_dust', '1x gtceu:thorium_dust', '1x gtceu:protactinium_dust', '1x gtceu:uranium_dust', '1x gtceu:neptunium_dust', '1x gtceu:plutonium_dust', '1x gtceu:americium_dust', '1x gtceu:curium_dust', '1x gtceu:berkelium_dust', '1x gtceu:californium_dust', '1x gtceu:einsteinium_dust', '1x gtceu:fermium_dust', '1x gtceu:mendelevium_dust', '1x gtceu:nobelium_dust', '1x gtceu:lawrencium_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000', 'gtceu:fluorine 1000', 'gtceu:chlorine 1000', 'gtceu:bromine 1000', 'gtceu:helium 1000', 'gtceu:neon 1000', 'gtceu:argon 1000', 'gtceu:krypton 1000', 'gtceu:xenon 1000', 'gtceu:radon 1000', 'gtceu:mercury 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fall_king_dust', inItem: '1x gtceu:fall_king_dust', inFluid: null,
            outItems: ['1x gtceu:lithium_dust', '1x gtceu:cobalt_dust', '1x gtceu:platinum_dust', '1x gtceu:erbium_dust'],
            outFluids: ['gtceu:helium 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fall_king_fluid', inItem: null, inFluid: 'gtceu:fall_king 1000',
            outItems: ['1x gtceu:lithium_dust', '1x gtceu:cobalt_dust', '1x gtceu:platinum_dust', '1x gtceu:erbium_dust'],
            outFluids: ['gtceu:helium 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_woods_glass_dust', inItem: '1x gtceu:woods_glass_dust', inFluid: null,
            outItems: ['12x gtceu:sodium_dust', '6x gtceu:carbon_dust', '3x gtceu:silicon_dust', '3x gtceu:nickel_dust', '1x gtceu:barium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 27000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_woods_glass_fluid', inItem: null, inFluid: 'gtceu:woods_glass 1000',
            outItems: ['12x gtceu:sodium_dust', '6x gtceu:carbon_dust', '3x gtceu:silicon_dust', '3x gtceu:nickel_dust', '1x gtceu:barium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 27000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyetheretherketone_dust', inItem: '1x gtceu:polyetheretherketone_dust', inFluid: null,
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyetheretherketone_fluid', inItem: null, inFluid: 'gtceu:polyetheretherketone 1000',
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zylon_dust', inItem: '1x gtceu:zylon_dust', inFluid: null,
            outItems: ['14x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zylon_fluid', inItem: null, inFluid: 'gtceu:zylon 1000',
            outItems: ['14x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_purified_tengam_dust', inItem: '1x gtceu:purified_tengam_dust', inFluid: null,
            outItems: ['1x gtceu:purified_tengam_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_attuned_tengam_dust', inItem: '1x gtceu:attuned_tengam_dust', inFluid: null,
            outItems: ['1x gtceu:attuned_tengam_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pre_zylon_dust', inItem: '1x gtceu:pre_zylon_dust', inFluid: null,
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 22000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_terephthalaldehyde_dust', inItem: '1x gtceu:terephthalaldehyde_dust', inFluid: null,
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_oxide_dust', inItem: '1x gtceu:sodium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_germanium_containing_precipitate_dust', inItem: '1x gtceu:germanium_containing_precipitate_dust', inFluid: null,
            outItems: ['1x gtceu:germanium_containing_precipitate_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_germanium_ash_dust', inItem: '1x gtceu:germanium_ash_dust', inFluid: null,
            outItems: ['1x gtceu:germanium_ash_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_germanium_dioxide_dust', inItem: '1x gtceu:germanium_dioxide_dust', inFluid: null,
            outItems: ['1x gtceu:germanium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_durene_dust', inItem: '1x gtceu:durene_dust', inFluid: null,
            outItems: ['10x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pyromellitic_dianhydride_dust', inItem: '1x gtceu:pyromellitic_dianhydride_dust', inFluid: null,
            outItems: ['10x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_calcium_carbide_dust', inItem: '1x gtceu:calcium_carbide_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust', '2x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_difluorobenzophenone_dust', inItem: '1x gtceu:difluorobenzophenone_dust', inFluid: null,
            outItems: ['13x gtceu:carbon_dust'],
            outFluids: ['gtceu:fluorine 2000', 'gtceu:hydrogen 8000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_fluoride_dust', inItem: '1x gtceu:sodium_fluoride_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_seaborgate_dust', inItem: '1x gtceu:sodium_seaborgate_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '1x gtceu:seaborgium_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gold_depleted_molybdenite_dust', inItem: '1x gtceu:gold_depleted_molybdenite_dust', inFluid: null,
            outItems: ['1x gtceu:molybdenum_dust', '2x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_molybdenum_trioxide_dust', inItem: '1x gtceu:molybdenum_trioxide_dust', inFluid: null,
            outItems: ['1x gtceu:molybdenum_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dichlorocyclooctadieneplatinium_dust', inItem: '1x gtceu:dichlorocyclooctadieneplatinium_dust', inFluid: null,
            outItems: ['8x gtceu:carbon_dust', '1x gtceu:platinum_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diiodobiphenyl_dust', inItem: '1x gtceu:diiodobiphenyl_dust', inFluid: null,
            outItems: ['12x gtceu:carbon_dust', '2x gtceu:iodine_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_boron_trioxide_dust', inItem: '1x gtceu:boron_trioxide_dust', inFluid: null,
            outItems: ['2x gtceu:boron_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithium_niobate_nanoparticles_dust', inItem: '1x gtceu:lithium_niobate_nanoparticles_dust', inFluid: null,
            outItems: ['2x gtceu:lithium_dust', '1x gtceu:niobium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hexanitrohexaaxaisowurtzitane_dust', inItem: '1x gtceu:hexanitrohexaaxaisowurtzitane_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:nitrogen 12000', 'gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_crude_hexanitrohexaaxaisowurtzitane_dust', inItem: '1x gtceu:crude_hexanitrohexaaxaisowurtzitane_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:nitrogen 12000', 'gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tetraacetyldinitrosohexaazaisowurtzitane_dust', inItem: '1x gtceu:tetraacetyldinitrosohexaazaisowurtzitane_dust', inFluid: null,
            outItems: ['14x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 18000', 'gtceu:nitrogen 8000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitronium_tetrafluoroborate_dust', inItem: '1x gtceu:nitronium_tetrafluoroborate_dust', inFluid: null,
            outItems: ['1x gtceu:boron_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 2000', 'gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrosonium_tetrafluoroborate_dust', inItem: '1x gtceu:nitrosonium_tetrafluoroborate_dust', inFluid: null,
            outItems: ['1x gtceu:boron_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 1000', 'gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dibenzyltetraacetylhexaazaisowurtzitane_dust', inItem: '1x gtceu:dibenzyltetraacetylhexaazaisowurtzitane_dust', inFluid: null,
            outItems: ['28x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 32000', 'gtceu:nitrogen 6000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_succinimidyl_acetate_dust', inItem: '1x gtceu:succinimidyl_acetate_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hexabenzylhexaazaisowurtzitane_dust', inItem: '1x gtceu:hexabenzylhexaazaisowurtzitane_dust', inFluid: null,
            outItems: ['48x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 48000', 'gtceu:nitrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_n_hydroxysuccinimide_dust', inItem: '1x gtceu:n_hydroxysuccinimide_dust', inFluid: null,
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_succinic_anhydride_dust', inItem: '1x gtceu:succinic_anhydride_dust', inFluid: null,
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_succinic_acid_dust', inItem: '1x gtceu:succinic_acid_dust', inFluid: null,
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acetonitrile_dust', inItem: '1x gtceu:acetonitrile_dust', inFluid: null,
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hexamethylenetetramine_dust', inItem: '1x gtceu:hexamethylenetetramine_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:nitrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_palladium_fullerene_matrix_dust', inItem: '1x gtceu:palladium_fullerene_matrix_dust', inFluid: null,
            outItems: ['1x gtceu:palladium_dust', '73x gtceu:carbon_dust', '1x gtceu:iron_dust'],
            outFluids: ['gtceu:hydrogen 15000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fullerene_dust', inItem: '1x gtceu:fullerene_dust', inFluid: null,
            outItems: ['60x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_unfolded_fullerene_dust', inItem: '1x gtceu:unfolded_fullerene_dust', inFluid: null,
            outItems: ['60x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 30000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_methylbenzophenanthrene_dust', inItem: '1x gtceu:methylbenzophenanthrene_dust', inFluid: null,
            outItems: ['19x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sarcosine_dust', inItem: '1x gtceu:sarcosine_dust', inFluid: null,
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diphenylmethane_diisocyanate_dust', inItem: '1x gtceu:diphenylmethane_diisocyanate_dust', inFluid: null,
            outItems: ['15x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pentaerythritol_dust', inItem: '1x gtceu:pentaerythritol_dust', inFluid: null,
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ytterbium_178_fluid', inItem: null, inFluid: 'gtceu:ytterbium_178 1000',
            outItems: [],
            outFluids: ['gtceu:ytterbium_178 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enriched_potassium_iodide_slurry_fluid', inItem: null, inFluid: 'gtceu:enriched_potassium_iodide_slurry 1000',
            outItems: ['1x gtceu:potassium_dust', '1x gtceu:iodine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iodine_containing_slurry_fluid', inItem: null, inFluid: 'gtceu:iodine_containing_slurry 1000',
            outItems: ['1x gtceu:iodine_dust', '1x gtceu:potassium_dust'],
            outFluids: ['gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tannic_fluid', inItem: null, inFluid: 'gtceu:tannic 1000',
            outItems: ['76x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 52000', 'gtceu:oxygen 46000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_germanium_tetrachloride_solution_fluid', inItem: null, inFluid: 'gtceu:germanium_tetrachloride_solution 1000',
            outItems: ['1x gtceu:germanium_dust'],
            outFluids: ['gtceu:chlorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyimide_fluid', inItem: null, inFluid: 'gtceu:polyimide 1000',
            outItems: ['22x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aniline_fluid', inItem: null, inFluid: 'gtceu:aniline 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_oxydianiline_fluid', inItem: null, inFluid: 'gtceu:oxydianiline 1000',
            outItems: ['12x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_boric_acide_fluid', inItem: null, inFluid: 'gtceu:boric_acide 1000',
            outItems: ['1x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluoroboric_acide_fluid', inItem: null, inFluid: 'gtceu:fluoroboric_acide 1000',
            outItems: ['1x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_benzenediazonium_tetrafluoroborate_fluid', inItem: null, inFluid: 'gtceu:benzenediazonium_tetrafluoroborate 1000',
            outItems: ['6x gtceu:carbon_dust', '1x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:fluorine 4000', 'gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluoro_benzene_fluid', inItem: null, inFluid: 'gtceu:fluoro_benzene 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluorotoluene_fluid', inItem: null, inFluid: 'gtceu:fluorotoluene 1000',
            outItems: ['7x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydroquinone_fluid', inItem: null, inFluid: 'gtceu:hydroquinone 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_resorcinol_fluid', inItem: null, inFluid: 'gtceu:resorcinol 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_nitrate_dust', inItem: '1x gtceu:sodium_nitrate_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_nitrate_solution_fluid', inItem: null, inFluid: 'gtceu:sodium_nitrate_solution 1000',
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 4000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acetylene_fluid', inItem: null, inFluid: 'gtceu:acetylene 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_cyanide_fluid', inItem: null, inFluid: 'gtceu:sodium_cyanide 1000',
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gold_cyanide_fluid', inItem: null, inFluid: 'gtceu:gold_cyanide 1000',
            outItems: ['1x gtceu:gold_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhenium_sulfuric_solution_fluid', inItem: null, inFluid: 'gtceu:rhenium_sulfuric_solution 1000',
            outItems: ['1x gtceu:rhenium_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ammonium_perrhenate_fluid', inItem: null, inFluid: 'gtceu:ammonium_perrhenate 1000',
            outItems: ['1x gtceu:rhenium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 3000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trimethyltin_chloride_fluid', inItem: null, inFluid: 'gtceu:trimethyltin_chloride 1000',
            outItems: ['1x gtceu:tin_dust', '3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 9000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silver_tetrafluoroborate_fluid', inItem: null, inFluid: 'gtceu:silver_tetrafluoroborate 1000',
            outItems: ['1x gtceu:silver_dust', '1x gtceu:boron_dust'],
            outFluids: ['gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_boron_fluoride_fluid', inItem: null, inFluid: 'gtceu:boron_fluoride 1000',
            outItems: ['1x gtceu:boron_dust'],
            outFluids: ['gtceu:fluorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_1_octene_fluid', inItem: null, inFluid: 'gtceu:1_octene 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 16000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pyridine_fluid', inItem: null, inFluid: 'gtceu:pyridine 1000',
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acetaldehyde_fluid', inItem: null, inFluid: 'gtceu:acetaldehyde 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cyclooctadiene_fluid', inItem: null, inFluid: 'gtceu:cyclooctadiene 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethylenediamine_fluid', inItem: null, inFluid: 'gtceu:ethylenediamine 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethanolamine_fluid', inItem: null, inFluid: 'gtceu:ethanolamine 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethylene_oxide_fluid', inItem: null, inFluid: 'gtceu:ethylene_oxide 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_benzaldehyde_fluid', inItem: null, inFluid: 'gtceu:benzaldehyde 1000',
            outItems: ['7x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydroxylamine_hydrochloride_fluid', inItem: null, inFluid: 'gtceu:hydroxylamine_hydrochloride 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_maleic_anhydride_fluid', inItem: null, inFluid: 'gtceu:maleic_anhydride 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_benzylamine_fluid', inItem: null, inFluid: 'gtceu:benzylamine 1000',
            outItems: ['7x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 9000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_glyoxal_fluid', inItem: null, inFluid: 'gtceu:glyoxal 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_benzyl_chloride_fluid', inItem: null, inFluid: 'gtceu:benzyl_chloride 1000',
            outItems: ['7x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mana_fluid', inItem: null, inFluid: 'gtceu:mana 1000',
            outItems: [],
            outFluids: ['gtceu:mana 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_stearic_acid_fluid', inItem: null, inFluid: 'gtceu:stearic_acid 1000',
            outItems: ['18x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 36000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tricotylphosphine_fluid', inItem: null, inFluid: 'gtceu:tricotylphosphine 1000',
            outItems: ['24x gtceu:carbon_dust', '1x gtceu:phosphorus_dust'],
            outFluids: ['gtceu:hydrogen 51000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iridium_trichloride_solution_fluid', inItem: null, inFluid: 'gtceu:iridium_trichloride_solution 1000',
            outItems: ['1x gtceu:iridium_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_liquid_hydrogen_fluid', inItem: null, inFluid: 'gtceu:liquid_hydrogen 1000',
            outItems: [],
            outFluids: ['gtceu:liquid_hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vibranium_unstable_fluid', inItem: null, inFluid: 'gtceu:vibranium_unstable 1000',
            outItems: [],
            outFluids: ['gtceu:vibranium_unstable 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_taranium_enriched_liquid_helium_3_fluid', inItem: null, inFluid: 'gtceu:taranium_enriched_liquid_helium_3 1000',
            outItems: ['1x gtceu:taranium_dust'],
            outFluids: ['gtceu:helium 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_taranium_rich_liquid_helium_4_fluid', inItem: null, inFluid: 'gtceu:taranium_rich_liquid_helium_4 1000',
            outItems: ['1x gtceu:taranium_dust'],
            outFluids: ['gtceu:helium 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_free_electron_gas_fluid', inItem: null, inFluid: 'gtceu:free_electron_gas 1000',
            outItems: [],
            outFluids: ['gtceu:free_electron_gas 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_free_alpha_gas_fluid', inItem: null, inFluid: 'gtceu:free_alpha_gas 1000',
            outItems: [],
            outFluids: ['gtceu:free_alpha_gas 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_free_proton_gas_fluid', inItem: null, inFluid: 'gtceu:free_proton_gas 1000',
            outItems: [],
            outFluids: ['gtceu:free_proton_gas 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_heavy_quarks_fluid', inItem: null, inFluid: 'gtceu:heavy_quarks 1000',
            outItems: [],
            outFluids: ['gtceu:heavy_quarks 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_light_quarks_fluid', inItem: null, inFluid: 'gtceu:light_quarks 1000',
            outItems: [],
            outFluids: ['gtceu:light_quarks 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gluons_fluid', inItem: null, inFluid: 'gtceu:gluons 1000',
            outItems: [],
            outFluids: ['gtceu:gluons 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_tetrafluoride_fluid', inItem: null, inFluid: 'gtceu:titanium_tetrafluoride 1000',
            outItems: ['1x gtceu:titanium_dust'],
            outFluids: ['gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_50_dust', inItem: '1x gtceu:titanium_50_dust', inFluid: null,
            outItems: ['1x gtceu:titanium_50_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_50_fluid', inItem: null, inFluid: 'gtceu:titanium_50 1000',
            outItems: ['1x gtceu:titanium_50_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_50_tetrafluoride_fluid', inItem: null, inFluid: 'gtceu:titanium_50_tetrafluoride 1000',
            outItems: ['1x gtceu:titanium_50_dust'],
            outFluids: ['gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanium_50_tetrachloride_fluid', inItem: null, inFluid: 'gtceu:titanium_50_tetrachloride 1000',
            outItems: ['1x gtceu:titanium_50_dust'],
            outFluids: ['gtceu:chlorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hot_oganesson_fluid', inItem: null, inFluid: 'gtceu:hot_oganesson 1000',
            outItems: [],
            outFluids: ['gtceu:hot_oganesson 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ferrocene_fluid', inItem: null, inFluid: 'gtceu:ferrocene 1000',
            outItems: ['10x gtceu:carbon_dust', '1x gtceu:iron_dust'],
            outFluids: ['gtceu:hydrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_scandium_titanium_50_mixture_fluid', inItem: null, inFluid: 'gtceu:scandium_titanium_50_mixture 1000',
            outItems: ['1x gtceu:scandium_dust', '1x gtceu:titanium_50_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dragon_element_fluid', inItem: null, inFluid: 'gtceu:dragon_element 1000',
            outItems: [],
            outFluids: ['gtceu:dragon_element 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_heavy_lepton_mixture_fluid', inItem: null, inFluid: 'gtceu:heavy_lepton_mixture 1000',
            outItems: [],
            outFluids: ['gtceu:heavy_lepton_mixture 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_heavy_quark_enriched_mixture_fluid', inItem: null, inFluid: 'gtceu:heavy_quark_enriched_mixture 1000',
            outItems: [],
            outFluids: ['gtceu:heavy_quark_enriched_mixture 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cosmic_computing_mixture_fluid', inItem: null, inFluid: 'gtceu:cosmic_computing_mixture 1000',
            outItems: [],
            outFluids: ['gtceu:gluons 1000', 'gtceu:heavy_quarks 1000', 'gtceu:heavy_lepton_mixture 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_liquid_starlight_fluid', inItem: null, inFluid: 'gtceu:liquid_starlight 1000',
            outItems: [],
            outFluids: ['gtceu:liquid_starlight 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_starlight_fluid', inItem: null, inFluid: 'gtceu:starlight 1000',
            outItems: [],
            outFluids: ['gtceu:starlight 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ammonium_nitrate_solution_fluid', inItem: null, inFluid: 'gtceu:ammonium_nitrate_solution 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 2000', 'gtceu:hydrogen 4000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadah_fuel_fluid', inItem: null, inFluid: 'gtceu:naquadah_fuel 1000',
            outItems: [],
            outFluids: ['gtceu:naquadah_fuel 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enriched_naquadah_fuel_fluid', inItem: null, inFluid: 'gtceu:enriched_naquadah_fuel 1000',
            outItems: [],
            outFluids: ['gtceu:enriched_naquadah_fuel 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hyper_fuel_1_fluid', inItem: null, inFluid: 'gtceu:hyper_fuel_1 1000',
            outItems: ['1x gtceu:naquadah_dust', '1x gtceu:enriched_naquadah_dust', '1x gtceu:naquadria_dust', '1x gtceu:thorium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hyper_fuel_2_fluid', inItem: null, inFluid: 'gtceu:hyper_fuel_2 1000',
            outItems: ['1x gtceu:naquadah_dust', '1x gtceu:enriched_naquadah_dust', '1x gtceu:naquadria_dust', '1x gtceu:thorium_dust', '1x gtceu:uranium_dust', '1x gtceu:dubnium_dust', '1x gtceu:fermium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hyper_fuel_3_fluid', inItem: null, inFluid: 'gtceu:hyper_fuel_3 1000',
            outItems: ['1x gtceu:naquadah_dust', '1x gtceu:enriched_naquadah_dust', '1x gtceu:naquadria_dust', '1x gtceu:thorium_dust', '1x gtceu:uranium_dust', '1x gtceu:dubnium_dust', '1x gtceu:fermium_dust', '1x gtceu:plutonium_dust', '1x gtceu:adamantine_dust', '1x gtceu:lawrencium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hyper_fuel_4_fluid', inItem: null, inFluid: 'gtceu:hyper_fuel_4 1000',
            outItems: ['1x gtceu:naquadah_dust', '1x gtceu:enriched_naquadah_dust', '1x gtceu:naquadria_dust', '1x gtceu:thorium_dust', '1x gtceu:uranium_dust', '1x gtceu:dubnium_dust', '1x gtceu:fermium_dust', '1x gtceu:plutonium_dust', '1x gtceu:adamantine_dust', '1x gtceu:lawrencium_dust', '1x gtceu:nobelium_dust', '1x gtceu:neutronium_dust', '1x gtceu:taranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_concentration_mixing_hyper_fuel_1_fluid', inItem: null, inFluid: 'gtceu:concentration_mixing_hyper_fuel_1 1000',
            outItems: ['1x gtceu:naquadah_dust', '1x gtceu:enriched_naquadah_dust', '1x gtceu:naquadria_dust', '1x gtceu:thorium_dust', '1x gtceu:uranium_dust', '1x gtceu:dubnium_dust', '1x gtceu:fermium_dust', '1x gtceu:plutonium_dust', '1x gtceu:adamantine_dust', '1x gtceu:lawrencium_dust', '1x gtceu:nobelium_dust', '1x gtceu:neutronium_dust', '1x gtceu:taranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_concentration_mixing_hyper_fuel_2_fluid', inItem: null, inFluid: 'gtceu:concentration_mixing_hyper_fuel_2 1000',
            outItems: ['1x gtceu:naquadah_dust', '1x gtceu:enriched_naquadah_dust', '1x gtceu:naquadria_dust', '1x gtceu:thorium_dust', '1x gtceu:uranium_dust', '1x gtceu:dubnium_dust', '1x gtceu:fermium_dust', '1x gtceu:plutonium_dust', '1x gtceu:adamantine_dust', '1x gtceu:lawrencium_dust', '1x gtceu:nobelium_dust', '1x gtceu:neutronium_dust', '1x gtceu:taranium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cosmic_element_fluid', inItem: null, inFluid: 'gtceu:cosmic_element 1000',
            outItems: [],
            outFluids: ['gtceu:free_electron_gas 1000', 'gtceu:free_alpha_gas 1000', 'gtceu:free_proton_gas 1000', 'gtceu:quark_gluon 1000', 'gtceu:heavy_quarks 1000', 'gtceu:light_quarks 1000', 'gtceu:gluons 1000', 'gtceu:heavy_lepton_mixture 1000', 'gtceu:high_energy_quark_gluon 2000', 'gtceu:dense_neutron 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_spatialfluid_fluid', inItem: null, inFluid: 'gtceu:spatialfluid 1000',
            outItems: [],
            outFluids: ['gtceu:spatialfluid 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_temporalfluid_fluid', inItem: null, inFluid: 'gtceu:temporalfluid 1000',
            outItems: [],
            outFluids: ['gtceu:temporalfluid 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_isochloropropane_fluid', inItem: null, inFluid: 'gtceu:isochloropropane 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dinitrodipropanyloxybenzene_fluid', inItem: null, inFluid: 'gtceu:dinitrodipropanyloxybenzene 1000',
            outItems: ['12x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 16000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dibromomethylbenzene_fluid', inItem: null, inFluid: 'gtceu:dibromomethylbenzene 1000',
            outItems: ['7x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:bromine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phosgene_fluid', inItem: null, inFluid: 'gtceu:phosgene 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 1000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethyleneglycol_fluid', inItem: null, inFluid: 'gtceu:ethyleneglycol 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pcbs_fluid', inItem: null, inFluid: 'gtceu:pcbs 1000',
            outItems: ['80x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 21000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dmap_dust', inItem: '1x gtceu:dmap_dust', inFluid: null,
            outItems: ['7x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000', 'gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phenylpentanoic_acid_fluid', inItem: null, inFluid: 'gtceu:phenylpentanoic_acid 1000',
            outItems: ['11x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dichloromethane_fluid', inItem: null, inFluid: 'gtceu:dichloromethane 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dimethyl_sulfide_fluid', inItem: null, inFluid: 'gtceu:dimethyl_sulfide 1000',
            outItems: ['2x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cosmic_mesh_fluid', inItem: null, inFluid: 'gtceu:liquid_cosmic_mesh 1000',
            outItems: [],
            outFluids: ['gtceu:cosmic_mesh 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydrobromic_acid_fluid', inItem: null, inFluid: 'gtceu:hydrobromic_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:bromine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_benzophenanthrenylacetonitrile_dust', inItem: '1x gtceu:benzophenanthrenylacetonitrile_dust', inFluid: null,
            outItems: ['20x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 13000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bromo_succinimide_dust', inItem: '1x gtceu:bromo_succinimide_dust', inFluid: null,
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:bromine 1000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_bromide_dust', inItem: '1x gtceu:potassium_bromide_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust'],
            outFluids: ['gtceu:bromine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_succinimide_dust', inItem: '1x gtceu:succinimide_dust', inFluid: null,
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_francium_caesium_cadmium_bromide_dust', inItem: '1x gtceu:francium_caesium_cadmium_bromide_dust', inFluid: null,
            outItems: ['1x gtceu:francium_dust', '1x gtceu:caesium_dust', '2x gtceu:cadmium_dust'],
            outFluids: ['gtceu:bromine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_strontium_europium_aluminate_dust', inItem: '1x gtceu:strontium_europium_aluminate_dust', inFluid: null,
            outItems: ['1x gtceu:strontium_dust', '1x gtceu:europium_dust', '2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dibismuthhydroborat_dust', inItem: '1x gtceu:dibismuthhydroborat_dust', inFluid: null,
            outItems: ['2x gtceu:bismuth_dust', '1x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_circuit_compound_dust', inItem: '1x gtceu:circuit_compound_dust', inFluid: null,
            outItems: ['1x gtceu:indium_dust', '1x gtceu:gallium_dust', '1x gtceu:phosphorus_dust', '10x gtceu:bismuth_dust', '3x gtceu:boron_dust', '6x gtceu:tellurium_dust'],
            outFluids: ['gtceu:hydrogen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_caesium_iodide_dust', inItem: '1x gtceu:caesium_iodide_dust', inFluid: null,
            outItems: ['1x gtceu:caesium_dust', '1x gtceu:iodine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acrylic_acid_fluid', inItem: null, inFluid: 'gtceu:acrylic_acid 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethyl_acrylate_fluid', inItem: null, inFluid: 'gtceu:ethyl_acrylate 1000',
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trichloroflerane_fluid', inItem: null, inFluid: 'gtceu:trichloroflerane 1000',
            outItems: ['1x gtceu:flerovium_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bisethylenedithiotetraselenafulvalene_perrhenate_dust', inItem: '1x gtceu:bisethylenedithiotetraselenafulvalene_perrhenate_dust', inFluid: null,
            outItems: ['1x gtceu:rhenium_dust', '10x gtceu:carbon_dust', '4x gtceu:sulfur_dust', '4x gtceu:selenium_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bisethylenedithiotetraselenafulvalene_dust', inItem: '1x gtceu:bisethylenedithiotetraselenafulvalene_dust', inFluid: null,
            outItems: ['10x gtceu:carbon_dust', '4x gtceu:sulfur_dust', '4x gtceu:selenium_dust'],
            outFluids: ['gtceu:hydrogen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithiumthiinediselenide_dust', inItem: '1x gtceu:lithiumthiinediselenide_dust', inFluid: null,
            outItems: ['4x gtceu:carbon_dust', '2x gtceu:sulfur_dust', '2x gtceu:lithium_dust', '2x gtceu:selenium_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cyclopentadienyl_titanium_trichloride_dust', inItem: '1x gtceu:cyclopentadienyl_titanium_trichloride_dust', inFluid: null,
            outItems: ['10x gtceu:carbon_dust', '1x gtceu:titanium_dust'],
            outFluids: ['gtceu:hydrogen 10000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_butyl_lithium_fluid', inItem: null, inFluid: 'gtceu:butyl_lithium 1000',
            outItems: ['4x gtceu:carbon_dust', '1x gtceu:lithium_dust'],
            outFluids: ['gtceu:hydrogen 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bromodihydrothiine_fluid', inItem: null, inFluid: 'gtceu:bromodihydrothiine 1000',
            outItems: ['4x gtceu:carbon_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:bromine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dibromoacrolein_fluid', inItem: null, inFluid: 'gtceu:dibromoacrolein 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:bromine 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_thiosulfate_dust', inItem: '1x gtceu:sodium_thiosulfate_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithium_fluoride_dust', inItem: '1x gtceu:lithium_fluoride_dust', inFluid: null,
            outItems: ['1x gtceu:lithium_dust'],
            outFluids: ['gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_high_purity_calcium_carbonate_dust', inItem: '1x gtceu:high_purity_calcium_carbonate_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bromobutane_fluid', inItem: null, inFluid: 'gtceu:bromobutane 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 9000', 'gtceu:bromine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_propadiene_fluid', inItem: null, inFluid: 'gtceu:propadiene 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_astatide_solution_fluid', inItem: null, inFluid: 'gtceu:astatide_solution 1000',
            outItems: ['1x gtceu:astatine_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_mixed_astatide_salts_dust', inItem: '1x gtceu:mixed_astatide_salts_dust', inFluid: null,
            outItems: ['1x gtceu:holmium_dust', '1x gtceu:thulium_dust', '1x gtceu:copernicium_dust', '1x gtceu:flerovium_dust', '3x gtceu:astatine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_boron_francium_carbide_dust', inItem: '1x gtceu:boron_francium_carbide_dust', inFluid: null,
            outItems: ['4x gtceu:francium_dust', '4x gtceu:boron_dust', '7x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_borocarbide_dust', inItem: '1x gtceu:borocarbide_dust', inFluid: null,
            outItems: ['1x gtceu:holmium_dust', '1x gtceu:thulium_dust', '1x gtceu:copernicium_dust', '1x gtceu:flerovium_dust', '3x gtceu:astatine_dust', '4x gtceu:francium_dust', '4x gtceu:boron_dust', '7x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_francium_carbide_dust', inItem: '1x gtceu:francium_carbide_dust', inFluid: null,
            outItems: ['2x gtceu:francium_dust', '2x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_boron_carbide_dust', inItem: '1x gtceu:boron_carbide_dust', inFluid: null,
            outItems: ['4x gtceu:boron_dust', '3x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lanthanum_embedded_fullerene_dust', inItem: '1x gtceu:lanthanum_embedded_fullerene_dust', inFluid: null,
            outItems: ['1x gtceu:lanthanum_dust', '60x gtceu:carbon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lanthanum_fullerene_mix_dust', inItem: '1x gtceu:lanthanum_fullerene_mix_dust', inFluid: null,
            outItems: ['1x gtceu:lanthanum_dust', '60x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 30000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_californium_trichloride_dust', inItem: '1x gtceu:californium_trichloride_dust', inFluid: null,
            outItems: ['1x gtceu:californium_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_californium_cyclopentadienide_fluid', inItem: null, inFluid: 'gtceu:californium_cyclopentadienide 1000',
            outItems: ['15x gtceu:carbon_dust', '1x gtceu:californium_dust'],
            outFluids: ['gtceu:hydrogen 15000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cyclopentadiene_fluid', inItem: null, inFluid: 'gtceu:cyclopentadiene 1000',
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithium_cyclopentadienide_fluid', inItem: null, inFluid: 'gtceu:lithium_cyclopentadienide 1000',
            outItems: ['1x gtceu:lithium_dust', '5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dimethylether_fluid', inItem: null, inFluid: 'gtceu:dimethylether 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dimethoxyethane_fluid', inItem: null, inFluid: 'gtceu:dimethoxyethane 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_photopolymer_fluid', inItem: null, inFluid: 'gtceu:photopolymer 1000',
            outItems: ['149x gtceu:carbon_dust', '1x gtceu:titanium_dust', '1x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 97000', 'gtceu:nitrogen 10000', 'gtceu:oxygen 2000', 'gtceu:fluorine 20000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silver_perchlorate_dust', inItem: '1x gtceu:silver_perchlorate_dust', inFluid: null,
            outItems: ['1x gtceu:silver_dust'],
            outFluids: ['gtceu:chlorine 1000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silver_chloride_dust', inItem: '1x gtceu:silver_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:silver_dust'],
            outFluids: ['gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_bromide_dust', inItem: '1x gtceu:sodium_bromide_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:bromine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silver_oxide_dust', inItem: '1x gtceu:silver_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:silver_dust'],
            outFluids: ['gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phthalic_anhydride_dust', inItem: '1x gtceu:phthalic_anhydride_dust', inFluid: null,
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_hypochlorite_dust', inItem: '1x gtceu:sodium_hypochlorite_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:chlorine 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethylanthraquinone_fluid', inItem: null, inFluid: 'gtceu:ethylanthraquinone 1000',
            outItems: ['16x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethylanthrahydroquinone_fluid', inItem: null, inFluid: 'gtceu:ethylanthrahydroquinone 1000',
            outItems: ['16x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_anthracene_fluid', inItem: null, inFluid: 'gtceu:anthracene 1000',
            outItems: ['14x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phenylsodium_fluid', inItem: null, inFluid: 'gtceu:phenylsodium 1000',
            outItems: ['6x gtceu:carbon_dust', '1x gtceu:sodium_dust'],
            outFluids: ['gtceu:hydrogen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_n_difluorophenylpyrrole_fluid', inItem: null, inFluid: 'gtceu:n_difluorophenylpyrrole 1000',
            outItems: ['10x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:fluorine 2000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_difluoroaniline_fluid', inItem: null, inFluid: 'gtceu:difluoroaniline 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:fluorine 2000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_succinaldehyde_fluid', inItem: null, inFluid: 'gtceu:succinaldehyde 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tetraethylammonium_bromide_fluid', inItem: null, inFluid: 'gtceu:tetraethylammonium_bromide 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 20000', 'gtceu:nitrogen 1000', 'gtceu:bromine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhodium_rhenium_naquadah_catalyst_dust', inItem: '1x gtceu:rhodium_rhenium_naquadah_catalyst_dust', inFluid: null,
            outItems: ['1x gtceu:rhodium_dust', '1x gtceu:rhenium_dust', '1x gtceu:naquadah_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iodine_monochloride_fluid', inItem: null, inFluid: 'gtceu:iodine_monochloride 1000',
            outItems: ['1x gtceu:iodine_dust'],
            outFluids: ['gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dimethylnaphthalene_fluid', inItem: null, inFluid: 'gtceu:dimethylnaphthalene 1000',
            outItems: ['12x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dihydroiodotetracene_fluid', inItem: null, inFluid: 'gtceu:dihydroiodotetracene 1000',
            outItems: ['18x gtceu:carbon_dust', '1x gtceu:iodine_dust'],
            outFluids: ['gtceu:hydrogen 13000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acetylating_reagent_fluid', inItem: null, inFluid: 'gtceu:acetylating_reagent 1000',
            outItems: ['9x gtceu:carbon_dust', '1x gtceu:silicon_dust', '2x gtceu:magnesium_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:bromine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_magnesium_chloride_bromide_dust', inItem: '1x gtceu:magnesium_chloride_bromide_dust', inFluid: null,
            outItems: ['1x gtceu:magnesium_dust'],
            outFluids: ['gtceu:chlorine 1000', 'gtceu:bromine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_isopropyl_alcohol_fluid', inItem: null, inFluid: 'gtceu:isopropyl_alcohol 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dichlorodicyanobenzoquinone_fluid', inItem: null, inFluid: 'gtceu:dichlorodicyanobenzoquinone 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:chlorine 2000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dichlorodicyanohydroquinone_fluid', inItem: null, inFluid: 'gtceu:dichlorodicyanohydroquinone 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:chlorine 2000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 2000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tetracene_dust', inItem: '1x gtceu:tetracene_dust', inFluid: null,
            outItems: ['18x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polycyclic_aromatic_mixture_dust', inItem: '1x gtceu:polycyclic_aromatic_mixture_dust', inFluid: null,
            outItems: ['18x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhenium_hassium_thallium_isophtaloylbisdiethylthiourea_hexaf_dust', inItem: '1x gtceu:rhenium_hassium_thallium_isophtaloylbisdiethylthiourea_hexaf_dust', inFluid: null,
            outItems: ['1x gtceu:rhenium_dust', '1x gtceu:hassium_dust', '1x gtceu:thallium_dust', '60x gtceu:carbon_dust', '1x gtceu:phosphorus_dust', '6x gtceu:sulfur_dust'],
            outFluids: ['gtceu:nitrogen 12000', 'gtceu:hydrogen 84000', 'gtceu:oxygen 12000', 'gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thallium_chloride_dust', inItem: '1x gtceu:thallium_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:thallium_dust'],
            outFluids: ['gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hassium_chloride_dust', inItem: '1x gtceu:hassium_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:hassium_dust'],
            outFluids: ['gtceu:chlorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhenium_chloride_dust', inItem: '1x gtceu:rhenium_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:rhenium_dust'],
            outFluids: ['gtceu:chlorine 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_isophthaloylbis_fluid', inItem: null, inFluid: 'gtceu:isophthaloylbis 1000',
            outItems: ['18x gtceu:carbon_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 26000', 'gtceu:nitrogen 4000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hexafluorophosphoric_acid_fluid', inItem: null, inFluid: 'gtceu:hexafluorophosphoric_acid 1000',
            outItems: ['1x gtceu:phosphorus_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diethylthiourea_fluid', inItem: null, inFluid: 'gtceu:diethylthiourea 1000',
            outItems: ['5x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thionyl_chloride_fluid', inItem: null, inFluid: 'gtceu:thionyl_chloride 1000',
            outItems: ['1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 1000', 'gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phenylenedioxydiacetic_acid_fluid', inItem: null, inFluid: 'gtceu:phenylenedioxydiacetic_acid 1000',
            outItems: ['10x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_thiocyanate_fluid', inItem: null, inFluid: 'gtceu:sodium_thiocyanate 1000',
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:sulfur_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phosphorus_trichloride_fluid', inItem: null, inFluid: 'gtceu:phosphorus_trichloride 1000',
            outItems: ['1x gtceu:phosphorus_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_antimony_pentafluoride_fluid', inItem: null, inFluid: 'gtceu:antimony_pentafluoride 1000',
            outItems: ['1x gtceu:antimony_dust'],
            outFluids: ['gtceu:fluorine 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_antimony_trichloride_dust', inItem: '1x gtceu:antimony_trichloride_dust', inFluid: null,
            outItems: ['1x gtceu:antimony_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_charged_caesium_cerium_cobalt_indium_dust', inItem: '1x gtceu:charged_caesium_cerium_cobalt_indium_dust', inFluid: null,
            outItems: ['1x gtceu:caesium_dust', '1x gtceu:cerium_dust', '2x gtceu:cobalt_dust', '10x gtceu:indium_dust'],
            outFluids: ['gtceu:gluons 1000', 'gtceu:heavy_quarks 1000', 'gtceu:heavy_lepton_mixture 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_actinium_superhydride_dust', inItem: '1x gtceu:actinium_superhydride_dust', inFluid: null,
            outItems: ['1x gtceu:actinium_dust'],
            outFluids: ['gtceu:hydrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cosmic_superconductor_fluid', inItem: null, inFluid: 'gtceu:cosmic_superconductor 1000',
            outItems: ['1x gtceu:rhenium_dust', '1x gtceu:hassium_dust', '1x gtceu:thallium_dust', '60x gtceu:carbon_dust', '1x gtceu:phosphorus_dust', '6x gtceu:sulfur_dust', '1x gtceu:actinium_dust', '1x gtceu:caesium_dust', '1x gtceu:cerium_dust', '2x gtceu:cobalt_dust', '10x gtceu:indium_dust'],
            outFluids: ['gtceu:nitrogen 12000', 'gtceu:hydrogen 96000', 'gtceu:oxygen 12000', 'gtceu:fluorine 6000', 'gtceu:gluons 1000', 'gtceu:heavy_quarks 1000', 'gtceu:heavy_lepton_mixture 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethylamine_fluid', inItem: null, inFluid: 'gtceu:ethylamine 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_toluene_diisocyanate_fluid', inItem: null, inFluid: 'gtceu:toluene_diisocyanate 1000',
            outItems: ['9x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_polyurethane_fluid', inItem: null, inFluid: 'gtceu:polyurethane 1000',
            outItems: ['17x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 16000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_viscoelastic_polyurethane_fluid', inItem: null, inFluid: 'gtceu:viscoelastic_polyurethane 1000',
            outItems: ['20x gtceu:carbon_dust', '1x gtceu:calcium_dust'],
            outFluids: ['gtceu:hydrogen 22000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_viscoelastic_polyurethane_foam_fluid', inItem: null, inFluid: 'gtceu:viscoelastic_polyurethane_foam 1000',
            outItems: ['20x gtceu:carbon_dust', '1x gtceu:calcium_dust'],
            outFluids: ['gtceu:hydrogen 22000', 'gtceu:nitrogen 80000', 'gtceu:oxygen 30000', 'gtceu:argon 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_glucose_dust', inItem: '1x gtceu:glucose_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_glucose_iron_solution_fluid', inItem: null, inFluid: 'gtceu:glucose_iron_solution 1000',
            outItems: ['6x gtceu:carbon_dust', '1x gtceu:iron_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:oxygen 6000', 'gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_graphene_oxide_dust', inItem: '1x gtceu:graphene_oxide_dust', inFluid: null,
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_graphene_gel_suspension_dust', inItem: '1x gtceu:graphene_gel_suspension_dust', inFluid: null,
            outItems: ['1x gtceu:graphene_gel_suspension_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dry_graphene_gel_dust', inItem: '1x gtceu:dry_graphene_gel_dust', inFluid: null,
            outItems: ['1x gtceu:dry_graphene_gel_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_supercritical_carbon_dioxide_fluid', inItem: null, inFluid: 'gtceu:supercritical_carbon_dioxide 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_bisulfite_dust', inItem: '1x gtceu:potassium_bisulfite_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_hydroxylaminedisulfonate_dust', inItem: '1x gtceu:potassium_hydroxylaminedisulfonate_dust', inFluid: null,
            outItems: ['2x gtceu:potassium_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 1000', 'gtceu:oxygen 7000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydroxylammonium_sulfate_dust', inItem: '1x gtceu:hydroxylammonium_sulfate_dust', inFluid: null,
            outItems: ['1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:nitrogen 2000', 'gtceu:hydrogen 8000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_barium_chloride_dust', inItem: '1x gtceu:barium_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:barium_dust'],
            outFluids: ['gtceu:chlorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrous_acid_fluid', inItem: null, inFluid: 'gtceu:nitrous_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_atinium_hydride_dust', inItem: '1x gtceu:atinium_hydride_dust', inFluid: null,
            outItems: ['1x gtceu:actinium_dust'],
            outFluids: ['gtceu:hydrogen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_1_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_1_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_2_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_2_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_3_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_3_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_4_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_4_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_5_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_5_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_6_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_6_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_7_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_7_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_8_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_8_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_9_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_9_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_10_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_10_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_11_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_11_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_12_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_12_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_13_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_13_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_14_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_14_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_15_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_15_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grade_16_purified_water_fluid', inItem: null, inFluid: 'gtceu:grade_16_purified_water 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_kerosene_fluid', inItem: null, inFluid: 'gtceu:kerosene 1000',
            outItems: ['12x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 24000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydrazine_fluid', inItem: null, inFluid: 'gtceu:hydrazine 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 2000', 'gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_monomethylhydrazine_fluid', inItem: null, inFluid: 'gtceu:monomethylhydrazine 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_la_nd_oxides_solution_fluid', inItem: null, inFluid: 'gtceu:la_nd_oxides_solution 1000',
            outItems: ['2x gtceu:lanthanum_dust', '2x gtceu:praseodymium_dust', '2x gtceu:neodymium_dust', '2x gtceu:cerium_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sm_gd_oxides_solution_fluid', inItem: null, inFluid: 'gtceu:sm_gd_oxides_solution 1000',
            outItems: ['2x gtceu:scandium_dust', '2x gtceu:europium_dust', '2x gtceu:gadolinium_dust', '2x gtceu:samarium_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tb_ho_oxides_solution_fluid', inItem: null, inFluid: 'gtceu:tb_ho_oxides_solution 1000',
            outItems: ['2x gtceu:yttrium_dust', '2x gtceu:terbium_dust', '2x gtceu:dysprosium_dust', '2x gtceu:holmium_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_er_lu_oxides_solution_fluid', inItem: null, inFluid: 'gtceu:er_lu_oxides_solution 1000',
            outItems: ['2x gtceu:erbium_dust', '2x gtceu:thulium_dust', '2x gtceu:ytterbium_dust', '2x gtceu:lutetium_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lanthanum_oxide_dust', inItem: '1x gtceu:lanthanum_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:lanthanum_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_praseodymium_oxide_dust', inItem: '1x gtceu:praseodymium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:praseodymium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_neodymium_oxide_dust', inItem: '1x gtceu:neodymium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:neodymium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cerium_oxide_dust', inItem: '1x gtceu:cerium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:cerium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_europium_oxide_dust', inItem: '1x gtceu:europium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:europium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gadolinium_oxide_dust', inItem: '1x gtceu:gadolinium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:gadolinium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_oxide_dust', inItem: '1x gtceu:samarium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:samarium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_terbium_oxide_dust', inItem: '1x gtceu:terbium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:terbium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dysprosium_oxide_dust', inItem: '1x gtceu:dysprosium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:dysprosium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_holmium_oxide_dust', inItem: '1x gtceu:holmium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:holmium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_erbium_oxide_dust', inItem: '1x gtceu:erbium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:erbium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thulium_oxide_dust', inItem: '1x gtceu:thulium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:thulium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ytterbium_oxide_dust', inItem: '1x gtceu:ytterbium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:ytterbium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lutetium_oxide_dust', inItem: '1x gtceu:lutetium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:lutetium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_scandium_oxide_dust', inItem: '1x gtceu:scandium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:scandium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_yttrium_oxide_dust', inItem: '1x gtceu:yttrium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:yttrium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_promethium_oxide_dust', inItem: '1x gtceu:promethium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:promethium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zircon_chlorinating_residue_fluid', inItem: null, inFluid: 'gtceu:zircon_chlorinating_residue 1000',
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:chlorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zirconium_hafnium_chloride_fluid', inItem: null, inFluid: 'gtceu:zirconium_hafnium_chloride 1000',
            outItems: ['1x gtceu:zirconium_dust', '1x gtceu:hafnium_dust'],
            outFluids: ['gtceu:chlorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zirconiu_hafnium_oxychloride_fluid', inItem: null, inFluid: 'gtceu:zirconiu_hafnium_oxychloride 1000',
            outItems: ['1x gtceu:hafnium_dust', '1x gtceu:zirconium_dust'],
            outFluids: ['gtceu:chlorine 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hafnium_oxide_dust', inItem: '1x gtceu:hafnium_oxide_dust', inFluid: null,
            outItems: ['1x gtceu:hafnium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zirconium_oxide_dust', inItem: '1x gtceu:zirconium_oxide_dust', inFluid: null,
            outItems: ['1x gtceu:zirconium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hafnium_chloride_dust', inItem: '1x gtceu:hafnium_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:hafnium_dust'],
            outFluids: ['gtceu:chlorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tellurium_oxide_dust', inItem: '1x gtceu:tellurium_oxide_dust', inFluid: null,
            outItems: ['1x gtceu:tellurium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_ethylate_dust', inItem: '1x gtceu:sodium_ethylate_dust', inFluid: null,
            outItems: ['2x gtceu:carbon_dust', '1x gtceu:sodium_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_ethylxanthate_dust', inItem: '1x gtceu:sodium_ethylxanthate_dust', inFluid: null,
            outItems: ['3x gtceu:carbon_dust', '1x gtceu:sodium_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_ethylxanthate_dust', inItem: '1x gtceu:potassium_ethylxanthate_dust', inFluid: null,
            outItems: ['3x gtceu:carbon_dust', '1x gtceu:potassium_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_ethylate_dust', inItem: '1x gtceu:potassium_ethylate_dust', inFluid: null,
            outItems: ['2x gtceu:carbon_dust', '1x gtceu:potassium_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nmethylpyrolidone_fluid', inItem: null, inFluid: 'gtceu:nmethylpyrolidone 1000',
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 9000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gammabutyrolactone_fluid', inItem: null, inFluid: 'gtceu:gammabutyrolactone 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_butane_1_4_diol_fluid', inItem: null, inFluid: 'gtceu:butane_1_4_diol 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_methylamine_fluid', inItem: null, inFluid: 'gtceu:methylamine 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_p_phenylenediamine_dust', inItem: '1x gtceu:p_phenylenediamine_dust', inFluid: null,
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_p_nitroaniline_fluid', inItem: null, inFluid: 'gtceu:p_nitroaniline 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_terephthalicacid_fluid', inItem: null, inFluid: 'gtceu:terephthalicacid 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dimethylterephthalate_fluid', inItem: null, inFluid: 'gtceu:dimethylterephthalate 1000',
            outItems: ['10x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_terephthaloyl_chloride_dust', inItem: '1x gtceu:terephthaloyl_chloride_dust', inFluid: null,
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 4000', 'gtceu:chlorine 2000', 'gtceu:nitrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhugnor_fluid', inItem: null, inFluid: 'gtceu:rhugnor 1000',
            outItems: [],
            outFluids: ['gtceu:rhugnor 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hot_sodium_potassium_fluid', inItem: null, inFluid: 'gtceu:hot_sodium_potassium 1000',
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:potassium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_supercritical_sodium_potassium_fluid', inItem: null, inFluid: 'gtceu:supercritical_sodium_potassium 1000',
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:potassium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_copper76_dust', inItem: '1x gtceu:copper76_dust', inFluid: null,
            outItems: ['1x gtceu:copper76_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cadmium_sulfide_dust', inItem: '1x gtceu:cadmium_sulfide_dust', inFluid: null,
            outItems: ['1x gtceu:cadmium_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cadmium_tungstate_dust', inItem: '1x gtceu:cadmium_tungstate_dust', inFluid: null,
            outItems: ['1x gtceu:cadmium_dust', '1x gtceu:tungsten_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bismuth_germanate_dust', inItem: '1x gtceu:bismuth_germanate_dust', inFluid: null,
            outItems: ['12x gtceu:bismuth_dust', '1x gtceu:germanium_dust'],
            outFluids: ['gtceu:oxygen 20000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bismuth_nitrate_solution_fluid', inItem: null, inFluid: 'gtceu:bismuth_nitrate_solution 1000',
            outItems: ['1x gtceu:bismuth_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 10000', 'gtceu:nitrogen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_paa_fluid', inItem: null, inFluid: 'gtceu:paa 1000',
            outItems: ['22x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 14000', 'gtceu:nitrogen 2000', 'gtceu:oxygen 7000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_turpentine_fluid', inItem: null, inFluid: 'gtceu:turpentine 1000',
            outItems: ['10x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 16000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_almandine_front_fluid', inItem: null, inFluid: 'gtceu:almandine_front 1000',
            outItems: ['2x gtceu:aluminium_dust', '3x gtceu:iron_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_chalcopyrite_front_fluid', inItem: null, inFluid: 'gtceu:chalcopyrite_front 1000',
            outItems: ['1x gtceu:copper_dust', '1x gtceu:iron_dust', '2x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_grossular_front_fluid', inItem: null, inFluid: 'gtceu:grossular_front 1000',
            outItems: ['3x gtceu:calcium_dust', '2x gtceu:aluminium_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nickel_front_fluid', inItem: null, inFluid: 'gtceu:nickel_front 1000',
            outItems: [],
            outFluids: ['gtceu:nickel_front 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_platinum_front_fluid', inItem: null, inFluid: 'gtceu:platinum_front 1000',
            outItems: [],
            outFluids: ['gtceu:platinum_front 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pyrope_front_fluid', inItem: null, inFluid: 'gtceu:pyrope_front 1000',
            outItems: ['2x gtceu:aluminium_dust', '3x gtceu:magnesium_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_redstone_front_fluid', inItem: null, inFluid: 'gtceu:redstone_front 1000',
            outItems: ['1x gtceu:silicon_dust', '5x gtceu:iron_dust', '10x gtceu:sulfur_dust', '1x gtceu:chromium_dust', '2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 3000', 'gtceu:mercury 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_spessartine_front_fluid', inItem: null, inFluid: 'gtceu:spessartine_front 1000',
            outItems: ['2x gtceu:aluminium_dust', '3x gtceu:manganese_dust', '3x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sphalerite_front_fluid', inItem: null, inFluid: 'gtceu:sphalerite_front 1000',
            outItems: ['1x gtceu:zinc_dust', '1x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_pentlandite_front_fluid', inItem: null, inFluid: 'gtceu:pentlandite_front 1000',
            outItems: ['9x gtceu:nickel_dust', '8x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_enriched_naquadah_front_fluid', inItem: null, inFluid: 'gtceu:enriched_naquadah_front 1000',
            outItems: [],
            outFluids: ['gtceu:enriched_naquadah_front 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_carbon_disulfide_fluid', inItem: null, inFluid: 'gtceu:carbon_disulfide 1000',
            outItems: ['1x gtceu:carbon_dust', '2x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydroiodic_acid_fluid', inItem: null, inFluid: 'gtceu:hydroiodic_acid 1000',
            outItems: ['1x gtceu:iodine_dust'],
            outFluids: ['gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acrylonitrile_fluid', inItem: null, inFluid: 'gtceu:acrylonitrile 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithium_iodide_dust', inItem: '1x gtceu:lithium_iodide_dust', inFluid: null,
            outItems: ['1x gtceu:lithium_dust', '1x gtceu:iodine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tert_butanol_fluid', inItem: null, inFluid: 'gtceu:tert_butanol 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ditertbutyl_dicarbonate_dust', inItem: '1x gtceu:ditertbutyl_dicarbonate_dust', inFluid: null,
            outItems: ['10x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 18000', 'gtceu:oxygen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tertbuthylcarbonylazide_fluid', inItem: null, inFluid: 'gtceu:tertbuthylcarbonylazide 1000',
            outItems: ['5x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 9000', 'gtceu:nitrogen 3000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_toluenesulfonate_fluid', inItem: null, inFluid: 'gtceu:sodium_toluenesulfonate 1000',
            outItems: ['7x gtceu:carbon_dust', '3x gtceu:sulfur_dust', '1x gtceu:sodium_dust'],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_azide_dust', inItem: '1x gtceu:sodium_azide_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:nitrogen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_azanide_dust', inItem: '1x gtceu:sodium_azanide_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrogen_pentoxide_fluid', inItem: null, inFluid: 'gtceu:nitrogen_pentoxide 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 2000', 'gtceu:oxygen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aminated_fullerene_fluid', inItem: null, inFluid: 'gtceu:aminated_fullerene 1000',
            outItems: ['60x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:nitrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_azafullerene_fluid', inItem: null, inFluid: 'gtceu:azafullerene 1000',
            outItems: ['60x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 12000', 'gtceu:nitrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_absolute_ethanol_fluid', inItem: null, inFluid: 'gtceu:absolute_ethanol 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_pyrosulfate_dust', inItem: '1x gtceu:potassium_pyrosulfate_dust', inFluid: null,
            outItems: ['2x gtceu:potassium_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 7000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_zinc_sulfate_dust', inItem: '1x gtceu:zinc_sulfate_dust', inFluid: null,
            outItems: ['1x gtceu:zinc_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_reprecipitated_rhodium_dust', inItem: '1x gtceu:reprecipitated_rhodium_dust', inFluid: null,
            outItems: ['1x gtceu:rhodium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_rhodium_salt_solution_fluid', inItem: null, inFluid: 'gtceu:rhodium_salt_solution 1000',
            outItems: ['1x gtceu:rhodium_dust', '2x gtceu:sodium_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_rutheniate_dust', inItem: '1x gtceu:sodium_rutheniate_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '1x gtceu:ruthenium_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_iridium_dioxide_dust', inItem: '1x gtceu:iridium_dioxide_dust', inFluid: null,
            outItems: ['1x gtceu:iridium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_formate_fluid', inItem: null, inFluid: 'gtceu:sodium_formate 1000',
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 2000', 'gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_sulfate_dust', inItem: '1x gtceu:sodium_sulfate_dust', inFluid: null,
            outItems: ['2x gtceu:sodium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hexafluoride_enriched_naquadah_solution_fluid', inItem: null, inFluid: 'gtceu:hexafluoride_enriched_naquadah_solution 1000',
            outItems: ['1x gtceu:enriched_naquadah_dust'],
            outFluids: ['gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_xenon_hexafluoro_enriched_naquadate_fluid', inItem: null, inFluid: 'gtceu:xenon_hexafluoro_enriched_naquadate 1000',
            outItems: ['1x gtceu:enriched_naquadah_dust'],
            outFluids: ['gtceu:xenon 1000', 'gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gold_trifluoride_dust', inItem: '1x gtceu:gold_trifluoride_dust', inFluid: null,
            outItems: ['1x gtceu:gold_dust'],
            outFluids: ['gtceu:fluorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_xenoauric_fluoroantimonic_acid_fluid', inItem: null, inFluid: 'gtceu:xenoauric_fluoroantimonic_acid 1000',
            outItems: ['1x gtceu:gold_dust', '1x gtceu:antimony_dust'],
            outFluids: ['gtceu:xenon 1000', 'gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_gold_chloride_fluid', inItem: null, inFluid: 'gtceu:gold_chloride 1000',
            outItems: ['2x gtceu:gold_dust'],
            outFluids: ['gtceu:chlorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_bromine_trifluoride_fluid', inItem: null, inFluid: 'gtceu:bromine_trifluoride 1000',
            outItems: [],
            outFluids: ['gtceu:bromine 1000', 'gtceu:fluorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hexafluoride_naquadria_solution_fluid', inItem: null, inFluid: 'gtceu:hexafluoride_naquadria_solution 1000',
            outItems: ['1x gtceu:naquadria_dust'],
            outFluids: ['gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_radon_difluoride_fluid', inItem: null, inFluid: 'gtceu:radon_difluoride 1000',
            outItems: [],
            outFluids: ['gtceu:radon 1000', 'gtceu:fluorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_radon_naquadria_octafluoride_fluid', inItem: null, inFluid: 'gtceu:radon_naquadria_octafluoride 1000',
            outItems: ['1x gtceu:naquadria_dust'],
            outFluids: ['gtceu:radon 1000', 'gtceu:fluorine 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_caesium_fluoride_fluid', inItem: null, inFluid: 'gtceu:caesium_fluoride 1000',
            outItems: ['1x gtceu:caesium_dust'],
            outFluids: ['gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_xenon_trioxide_fluid', inItem: null, inFluid: 'gtceu:xenon_trioxide 1000',
            outItems: [],
            outFluids: ['gtceu:xenon 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_caesium_xenontrioxide_fluoride_fluid', inItem: null, inFluid: 'gtceu:caesium_xenontrioxide_fluoride 1000',
            outItems: ['1x gtceu:caesium_dust'],
            outFluids: ['gtceu:xenon 1000', 'gtceu:oxygen 3000', 'gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadria_caesium_xenonnonfluoride_fluid', inItem: null, inFluid: 'gtceu:naquadria_caesium_xenonnonfluoride 1000',
            outItems: ['1x gtceu:naquadria_dust', '1x gtceu:caesium_dust'],
            outFluids: ['gtceu:xenon 1000', 'gtceu:fluorine 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_radon_trioxide_fluid', inItem: null, inFluid: 'gtceu:radon_trioxide 1000',
            outItems: [],
            outFluids: ['gtceu:radon 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_naquadria_caesiumfluoride_fluid', inItem: null, inFluid: 'gtceu:naquadria_caesiumfluoride 1000',
            outItems: ['1x gtceu:naquadria_dust', '1x gtceu:caesium_dust'],
            outFluids: ['gtceu:fluorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitrosonium_octafluoroxenate_fluid', inItem: null, inFluid: 'gtceu:nitrosonium_octafluoroxenate 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 2000', 'gtceu:oxygen 4000', 'gtceu:xenon 1000', 'gtceu:fluorine 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_nitryl_fluoride_fluid', inItem: null, inFluid: 'gtceu:nitryl_fluoride 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 2000', 'gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acidic_naquadria_caesiumfluoride_fluid', inItem: null, inFluid: 'gtceu:acidic_naquadria_caesiumfluoride 1000',
            outItems: ['1x gtceu:naquadria_dust', '1x gtceu:caesium_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:fluorine 3000', 'gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_supercritical_steam_fluid', inItem: null, inFluid: 'gtceu:supercritical_steam 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tungsten_trioxide_dust', inItem: '1x gtceu:tungsten_trioxide_dust', inFluid: null,
            outItems: ['1x gtceu:tungsten_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_spacetime_dust', inItem: '1x gtceu:spacetime_dust', inFluid: null,
            outItems: ['1x gtceu:spacetime_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_spacetime_fluid', inItem: null, inFluid: 'gtceu:spacetime 1000',
            outItems: ['1x gtceu:spacetime_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_infinity_dust', inItem: '1x gtceu:infinity_dust', inFluid: null,
            outItems: ['1x gtceu:infinity_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_infinity_fluid', inItem: null, inFluid: 'gtceu:infinity 1000',
            outItems: ['1x gtceu:infinity_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trinium_compound_dust', inItem: '1x gtceu:trinium_compound_dust', inFluid: null,
            outItems: ['3x gtceu:trinium_dust', '3x gtceu:actinium_dust', '4x gtceu:selenium_dust', '4x gtceu:astatine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_crystalline_nitric_acid_dust', inItem: '1x gtceu:crystalline_nitric_acid_dust', inFluid: null,
            outItems: [],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_chlorate_dust', inItem: '1x gtceu:sodium_chlorate_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:chlorine 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_perchlorate_dust', inItem: '1x gtceu:sodium_perchlorate_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:chlorine 1000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_actinium_trinium_hydroxides_dust', inItem: '1x gtceu:actinium_trinium_hydroxides_dust', inFluid: null,
            outItems: ['3x gtceu:trinium_dust', '2x gtceu:actinium_dust'],
            outFluids: ['gtceu:oxygen 12000', 'gtceu:hydrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_selenium_oxide_dust', inItem: '1x gtceu:selenium_oxide_dust', inFluid: null,
            outItems: ['1x gtceu:selenium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trinium_tetrafluoride_dust', inItem: '1x gtceu:trinium_tetrafluoride_dust', inFluid: null,
            outItems: ['1x gtceu:trinium_dust'],
            outFluids: ['gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluorocarborane_dust', inItem: '1x gtceu:fluorocarborane_dust', inFluid: null,
            outItems: ['1x gtceu:carbon_dust', '11x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:fluorine 11000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_caesium_nitrate_dust', inItem: '1x gtceu:caesium_nitrate_dust', inFluid: null,
            outItems: ['1x gtceu:caesium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cesium_carborane_dust', inItem: '1x gtceu:cesium_carborane_dust', inFluid: null,
            outItems: ['1x gtceu:caesium_dust', '1x gtceu:carbon_dust', '11x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 12000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silver_iodide_dust', inItem: '1x gtceu:silver_iodide_dust', inFluid: null,
            outItems: ['1x gtceu:silver_dust', '1x gtceu:iodine_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_silver_nitrate_dust', inItem: '1x gtceu:silver_nitrate_dust', inFluid: null,
            outItems: ['1x gtceu:silver_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trifluoroacetic_phosphate_ester_dust', inItem: '1x gtceu:trifluoroacetic_phosphate_ester_dust', inFluid: null,
            outItems: ['8x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:fluorine 3000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_radium_nitrate_dust', inItem: '1x gtceu:radium_nitrate_dust', inFluid: null,
            outItems: ['1x gtceu:radium_dust'],
            outFluids: ['gtceu:nitrogen 2000', 'gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_actinium_nitrate_dust', inItem: '1x gtceu:actinium_nitrate_dust', inFluid: null,
            outItems: ['1x gtceu:actinium_dust'],
            outFluids: ['gtceu:nitrogen 3000', 'gtceu:oxygen 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_potassium_fluoride_dust', inItem: '1x gtceu:potassium_fluoride_dust', inFluid: null,
            outItems: ['1x gtceu:potassium_dust'],
            outFluids: ['gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_hydride_dust', inItem: '1x gtceu:sodium_hydride_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cesium_carborane_precursor_dust', inItem: '1x gtceu:cesium_carborane_precursor_dust', inFluid: null,
            outItems: ['1x gtceu:caesium_dust', '10x gtceu:boron_dust', '4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 21000', 'gtceu:nitrogen 1000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithium_aluminium_hydride_dust', inItem: '1x gtceu:lithium_aluminium_hydride_dust', inFluid: null,
            outItems: ['1x gtceu:lithium_dust', '1x gtceu:aluminium_dust'],
            outFluids: ['gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lithium_aluminium_fluoride_dust', inItem: '1x gtceu:lithium_aluminium_fluoride_dust', inFluid: null,
            outItems: ['1x gtceu:aluminium_dust', '1x gtceu:lithium_dust'],
            outFluids: ['gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aluminium_trifluoride_dust', inItem: '1x gtceu:aluminium_trifluoride_dust', inFluid: null,
            outItems: ['1x gtceu:aluminium_dust'],
            outFluids: ['gtceu:fluorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_aluminium_hydride_dust', inItem: '1x gtceu:sodium_aluminium_hydride_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:aluminium_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_aluminium_hydride_dust', inItem: '1x gtceu:aluminium_hydride_dust', inFluid: null,
            outItems: ['1x gtceu:aluminium_dust'],
            outFluids: ['gtceu:hydrogen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_alumina_dust', inItem: '1x gtceu:alumina_dust', inFluid: null,
            outItems: ['2x gtceu:aluminium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_caesium_hydroxide_dust', inItem: '1x gtceu:caesium_hydroxide_dust', inFluid: null,
            outItems: ['1x gtceu:caesium_dust'],
            outFluids: ['gtceu:oxygen 1000', 'gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_decaborane_dust', inItem: '1x gtceu:decaborane_dust', inFluid: null,
            outItems: ['10x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 14000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_tetrafluoroborate_dust', inItem: '1x gtceu:sodium_tetrafluoroborate_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:boron_dust'],
            outFluids: ['gtceu:fluorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_borohydride_dust', inItem: '1x gtceu:sodium_borohydride_dust', inFluid: null,
            outItems: ['1x gtceu:sodium_dust', '1x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phosphorus_pentasulfide_dust', inItem: '1x gtceu:phosphorus_pentasulfide_dust', inFluid: null,
            outItems: ['4x gtceu:phosphorus_dust', '10x gtceu:sulfur_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ammonium_bifluoride_dust', inItem: '1x gtceu:ammonium_bifluoride_dust', inFluid: null,
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 5000', 'gtceu:fluorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fuming_nitric_acid_fluid', inItem: null, inFluid: 'gtceu:fuming_nitric_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 1000', 'gtceu:nitrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_perfluorobenzene_fluid', inItem: null, inFluid: 'gtceu:perfluorobenzene 1000',
            outItems: ['6x gtceu:carbon_dust'],
            outFluids: ['gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trimethylsilane_fluid', inItem: null, inFluid: 'gtceu:trimethylsilane 1000',
            outItems: ['3x gtceu:carbon_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 10000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trimethylchlorosilane_fluid', inItem: null, inFluid: 'gtceu:trimethylchlorosilane 1000',
            outItems: ['3x gtceu:carbon_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 9000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethylene_sulfide_fluid', inItem: null, inFluid: 'gtceu:ethylene_sulfide 1000',
            outItems: ['6x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethyl_trifluoroacetate_fluid', inItem: null, inFluid: 'gtceu:ethyl_trifluoroacetate 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 5000', 'gtceu:fluorine 3000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_acetyl_chloride_fluid', inItem: null, inFluid: 'gtceu:acetyl_chloride 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:oxygen 1000', 'gtceu:chlorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_krypton_difluoride_fluid', inItem: null, inFluid: 'gtceu:krypton_difluoride 1000',
            outItems: [],
            outFluids: ['gtceu:krypton 1000', 'gtceu:fluorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_trimethylamine_fluid', inItem: null, inFluid: 'gtceu:trimethylamine 1000',
            outItems: ['3x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 9000', 'gtceu:nitrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_borane_dimethyl_sulfide_fluid', inItem: null, inFluid: 'gtceu:borane_dimethyl_sulfide 1000',
            outItems: ['1x gtceu:boron_dust', '2x gtceu:carbon_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:hydrogen 9000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tetrahydrofuran_fluid', inItem: null, inFluid: 'gtceu:tetrahydrofuran 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 8000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diborane_fluid', inItem: null, inFluid: 'gtceu:diborane 1000',
            outItems: ['2x gtceu:boron_dust'],
            outFluids: ['gtceu:hydrogen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_diethyl_ether_fluid', inItem: null, inFluid: 'gtceu:diethyl_ether 1000',
            outItems: ['4x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 10000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_boron_trifluoride_acetate_fluid', inItem: null, inFluid: 'gtceu:boron_trifluoride_acetate 1000',
            outItems: ['1x gtceu:boron_dust', '4x gtceu:carbon_dust'],
            outFluids: ['gtceu:fluorine 3000', 'gtceu:hydrogen 10000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_hexafluoroaluminate_fluid', inItem: null, inFluid: 'gtceu:sodium_hexafluoroaluminate 1000',
            outItems: ['3x gtceu:sodium_dust', '1x gtceu:aluminium_dust'],
            outFluids: ['gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dilute_hexafluorosilicic_acid_fluid', inItem: null, inFluid: 'gtceu:dilute_hexafluorosilicic_acid 1000',
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 6000', 'gtceu:oxygen 2000', 'gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluorosilicic_acid_fluid', inItem: null, inFluid: 'gtceu:fluorosilicic_acid 1000',
            outItems: ['1x gtceu:silicon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:fluorine 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ammonium_fluoride_fluid', inItem: null, inFluid: 'gtceu:ammonium_fluoride 1000',
            outItems: [],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 4000', 'gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ammonium_bifluoride_solution_fluid', inItem: null, inFluid: 'gtceu:ammonium_bifluoride_solution 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 7000', 'gtceu:oxygen 1000', 'gtceu:nitrogen 1000', 'gtceu:fluorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_sodium_hydroxide_solution_fluid', inItem: null, inFluid: 'gtceu:sodium_hydroxide_solution 1000',
            outItems: ['1x gtceu:sodium_dust'],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_titanyl_sulfate_fluid', inItem: null, inFluid: 'gtceu:titanyl_sulfate 1000',
            outItems: ['1x gtceu:titanium_dust', '1x gtceu:sulfur_dust'],
            outFluids: ['gtceu:oxygen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dioxygen_difluoride_fluid', inItem: null, inFluid: 'gtceu:dioxygen_difluoride 1000',
            outItems: [],
            outFluids: ['gtceu:fluorine 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_helium_iii_hydride_fluid', inItem: null, inFluid: 'gtceu:helium_iii_hydride 1000',
            outItems: [],
            outFluids: ['gtceu:helium 1000', 'gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_xenic_acid_fluid', inItem: null, inFluid: 'gtceu:xenic_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:xenon 1000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_dilute_hydrofluoric_acid_fluid', inItem: null, inFluid: 'gtceu:dilute_hydrofluoric_acid 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 3000', 'gtceu:oxygen 1000', 'gtceu:fluorine 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tritium_hydride_fluid', inItem: null, inFluid: 'gtceu:tritium_hydride 1000',
            outItems: ['1x gtceu:tear_dust'],
            outFluids: ['gtceu:hydrogen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ozone_fluid', inItem: null, inFluid: 'gtceu:ozone 1000',
            outItems: [],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_hydrogen_peroxide_fluid', inItem: null, inFluid: 'gtceu:hydrogen_peroxide 1000',
            outItems: [],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluorite_dust', inItem: '1x gtceu:fluorite_dust', inFluid: null,
            outItems: ['1x gtceu:calcium_dust'],
            outFluids: ['gtceu:fluorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_fluorite_fluid', inItem: null, inFluid: 'gtceu:fluorite 1000',
            outItems: ['1x gtceu:calcium_dust'],
            outFluids: ['gtceu:fluorine 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_thorite_powder_dust', inItem: '1x gtceu:thorite_powder_dust', inFluid: null,
            outItems: ['1x gtceu:thorium_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_red_zircon_powder_dust', inItem: '1x gtceu:red_zircon_powder_dust', inFluid: null,
            outItems: ['1x gtceu:zirconium_dust', '1x gtceu:silicon_dust'],
            outFluids: ['gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cerium_chloride_powder_dust', inItem: '1x gtceu:cerium_chloride_powder_dust', inFluid: null,
            outItems: ['1x gtceu:cerium_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_oxalic_acid_fluid', inItem: null, inFluid: 'gtceu:oxalic_acid 1000',
            outItems: ['2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_vanadium_pentoxide_powder_dust', inItem: '1x gtceu:vanadium_pentoxide_powder_dust', inFluid: null,
            outItems: ['2x gtceu:vanadium_dust'],
            outFluids: ['gtceu:oxygen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cerium_oxalate_powder_dust', inItem: '1x gtceu:cerium_oxalate_powder_dust', inFluid: null,
            outItems: ['1x gtceu:cerium_dust', '2x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 2000', 'gtceu:oxygen 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_concentrated_cerium_chloride_solution_fluid', inItem: null, inFluid: 'gtceu:concentrated_cerium_chloride_solution 1000',
            outItems: ['1x gtceu:cerium_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_precipitate_powder_dust', inItem: '1x gtceu:samarium_precipitate_powder_dust', inFluid: null,
            outItems: ['2x gtceu:samarium_dust', '1x gtceu:gadolinium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_rare_earth_diluted_solution_fluid', inItem: null, inFluid: 'gtceu:samarium_rare_earth_diluted_solution 1000',
            outItems: ['1x gtceu:samarium_dust'],
            outFluids: ['gtceu:chlorine 1000', 'gtceu:hydrogen 4000', 'gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_oxalate_with_impurities_dust', inItem: '1x gtceu:samarium_oxalate_with_impurities_dust', inFluid: null,
            outItems: ['1x gtceu:samarium_dust', '2x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 6000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_chloride_with_impurities_dust', inItem: '1x gtceu:samarium_chloride_with_impurities_dust', inFluid: null,
            outItems: ['1x gtceu:samarium_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_chloride_with_impurities_fluid', inItem: null, inFluid: 'gtceu:samarium_chloride_with_impurities 1000',
            outItems: ['1x gtceu:samarium_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_chloride_sodium_chloride_mixture_powder_dust', inItem: '1x gtceu:samarium_chloride_sodium_chloride_mixture_powder_dust', inFluid: null,
            outItems: ['1x gtceu:samarium_dust', '1x gtceu:sodium_dust'],
            outFluids: ['gtceu:chlorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_phosphorus_free_samarium_concentrate_powder_dust', inItem: '1x gtceu:phosphorus_free_samarium_concentrate_powder_dust', inFluid: null,
            outItems: ['1x gtceu:phosphorus_free_samarium_concentrate_powder_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_samarium_chloride_concentrate_solution_fluid', inItem: null, inFluid: 'gtceu:samarium_chloride_concentrate_solution 1000',
            outItems: ['1x gtceu:samarium_dust'],
            outFluids: ['gtceu:chlorine 3000', 'gtceu:hydrogen 10000', 'gtceu:oxygen 5000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_lanthanum_chloride_dust', inItem: '1x gtceu:lanthanum_chloride_dust', inFluid: null,
            outItems: ['1x gtceu:lanthanum_dust'],
            outFluids: ['gtceu:chlorine 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_cerium_oxide_rare_earth_oxide_powder_dust', inItem: '1x gtceu:cerium_oxide_rare_earth_oxide_powder_dust', inFluid: null,
            outItems: ['1x gtceu:cerium_dust'],
            outFluids: ['gtceu:oxygen 2000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_terbium_nitrate_powder_dust', inItem: '1x gtceu:terbium_nitrate_powder_dust', inFluid: null,
            outItems: ['1x gtceu:terbium_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_carbon_tetrachloride_fluid', inItem: null, inFluid: 'gtceu:carbon_tetrachloride 1000',
            outItems: ['1x gtceu:carbon_dust'],
            outFluids: ['gtceu:chlorine 4000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_actinium_oxalate_dust', inItem: '1x gtceu:actinium_oxalate_dust', inFluid: null,
            outItems: ['1x gtceu:actinium_dust', '4x gtceu:carbon_dust'],
            outFluids: ['gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_ethyl_hexanol_fluid', inItem: null, inFluid: 'gtceu:ethyl_hexanol 1000',
            outItems: ['8x gtceu:carbon_dust'],
            outFluids: ['gtceu:hydrogen 18000', 'gtceu:oxygen 1000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_p507_fluid', inItem: null, inFluid: 'gtceu:p507 1000',
            outItems: ['18x gtceu:carbon_dust', '1x gtceu:phosphorus_dust'],
            outFluids: ['gtceu:hydrogen 36000', 'gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tear_dust', inItem: '1x gtceu:tear_dust', inFluid: null,
            outItems: ['1x gtceu:tear_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_tear_fluid', inItem: null, inFluid: 'gtceu:tear 1000',
            outItems: ['1x gtceu:tear_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_instability_dust', inItem: '1x gtceu:instability_dust', inFluid: null,
            outItems: ['1x gtceu:instability_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_instability_fluid', inItem: null, inFluid: 'gtceu:instability 1000',
            outItems: ['1x gtceu:instability_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_celestial_secret_dust', inItem: '1x gtceu:celestial_secret_dust', inFluid: null,
            outItems: ['1x gtceu:celestial_secret_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtceu_celestial_secret_fluid', inItem: null, inFluid: 'gtceu:celestial_secret 1000',
            outItems: ['1x gtceu:celestial_secret_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtladditions_gallium_oxide_dust', inItem: '1x gtladditions:gallium_oxide_dust', inFluid: null,
            outItems: ['2x gtceu:gallium_dust'],
            outFluids: ['gtceu:oxygen 3000']
        }
        ,{
            id: 'shanhai:deconstruct/gtladditions_ammonium_gallium_sulfate_dust', inItem: '1x gtladditions:ammonium_gallium_sulfate_dust', inFluid: null,
            outItems: ['1x gtceu:gallium_dust', '2x gtceu:sulfur_dust'],
            outFluids: ['gtceu:nitrogen 1000', 'gtceu:hydrogen 4000', 'gtceu:oxygen 8000']
        }
        ,{
            id: 'shanhai:deconstruct/gtladditions_ruridit_dust', inItem: '1x gtladditions:ruridit_dust', inFluid: null,
            outItems: ['2x gtceu:ruthenium_dust', '1x gtceu:iridium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtladditions_liquid_ruridit_fluid', inItem: null, inFluid: 'gtladditions:liquid_ruridit 1000',
            outItems: ['2x gtceu:ruthenium_dust', '1x gtceu:iridium_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtladditions_creon_dust', inItem: '1x gtladditions:creon_dust', inFluid: null,
            outItems: ['1x gtladditions:creon_dust'],
            outFluids: []
        }
        ,{
            id: 'shanhai:deconstruct/gtladditions_creon_fluid', inItem: null, inFluid: 'gtladditions:creon 1000',
            outItems: ['1x gtladditions:creon_dust'],
            outFluids: []
        }
    ]
    var applySet = function (sym, v) {
        var mid = SYM2MAT[sym] || ('gtceu:' + sym);
        var isF = false, num = v;
        if (typeof v === 'string') {
            if (v.indexOf('mB') >= 0) { isF = true; num = parseInt(v.replace(/[^0-9]/g, ''), 10) }
            else { num = parseInt(v, 10) }
        }
        var src = isF ? fluids : items, out = [], q;
        for (q = 0; q < src.length; q++) { if (src[q].indexOf(mid + ' ') !== 0) { out.push(src[q]) } }
        out.push(isF ? (mid + ' ' + num) : (num + 'x ' + mid + '_dust'));
        if (isF) { fluids = out } else { items = out }
    };
    var j, k
    for (j = 0; j < JOBS.length; j++) {
        var J = JOBS[j], items = J.outItems.slice(), fluids = J.outFluids.slice()
        var ex = EXTRA[J.id] || EXTRA[J.id.replace(/_dust$/, '').replace(/_fluid$/, '')]
        if (ex) {
            if (ex.add) { for (k = 0; k < ex.add.length; k++) { if (ex.add[k].indexOf('mB') >= 0) { fluids.push(ex.add[k].replace(/\s*mB\s*/, '')) } else { items.push(ex.add[k]) } } }
            if (ex.set) { for (k in ex.set) { applySet(k, ex.set[k]) } }
            if (ex.setFluid) { for (k in ex.setFluid) { applySet(k, ex.setFluid[k] + ' mB') } }
        }
        try {
            var b = gtr[T](J.id)
            if (J.inItem !== null) { b = b.itemInputs(J.inItem) }
            if (J.inFluid !== null) { b = b.inputFluids(J.inFluid) }
            if (items.length) { b = b.itemOutputs(items) }
            if (fluids.length) { b = b.outputFluids(fluids) }
            b = b.duration(200).EUt(32)
            ok = ok + 1
            if (ShanhaiStats) ShanhaiStats.addResult(true)
        } catch (e) { bad = bad + 1; if (errList.length < 1500) { errList = errList + J.id + ' => ' + e + ' | ' } ; if (ShanhaiStats) ShanhaiStats.addResult(false) }
    }
    console.info('[SHANHAI-DECON] jobs=' + JOBS.length + ' => ok=' + ok + ' failed=' + bad + ' EUt=32')
    // 🔴 2026-09-27：给本批留一次 summary —— 横幅【一行一批】需要它进 BY_SCOPE。
    //    本脚本【不 reset()】（跑最前，累加器本来就是 0）⇒ 这里的 total 就是本批的条数。
    if (ShanhaiStats) {
        try { ShanhaiStats.reportSummary('shanhai_deconstruct') } catch (eR3) { console.info('[SHANHAI-DECON] reportSummary FAILED: ' + eR3) }
    }
    if (bad > 0) { console.info('[SHANHAI-DECON] fail: ' + errList) }
})
    // 本脚本【不】在这里报 summary —— 汇总由 [server_scripts]shanhai_recipes.js 的 ServerEvents.loaded 打，
    //    那时三批都已上报完（靠 lifetime 字段，与调用时机无关）。

// ═══ 手写区 开始（生成器不会动这一段）═══
// ─── 手写区内容来源（2026-09-27 用户点单第 3 条）─────────────────────────
//   1) 原 shanhai_deconstruct_extra.js —— global.SD_EXTRA 人工补充表（生成部分运行期读它）
//   2) 原 shanhai_symbol_deconstruct.js —— 9 个特殊符号所在材质的解构配方

// =============================================================================
// ── 原 shanhai_deconstruct_extra.js -- 「原初物质解构」人工补充表【用户独占区】
//   · 生成器【永不写这个文件】⇒ 你随便改，不会被覆盖。
//   · 改完 /reload 立即注入进配方，不需要重跑生成器、不需要重建 jar。
//   · 这个文件【可以整个删掉】—— 主脚本有兜底，删了不报错、只是没有补充。
//
//   键 = 材质 id（例如 'gtceu:sheldonite'），加 '#dust' / '#fluid' 只对那个形态生效。
//
//   ---- 改数量：set（值带 mB 就是流体，写数字就是物品）——【覆盖】该元素原有的那条输出 ----
//     'gtceu:iron_dust':      { set: { Fe: 3 } },            // 物品：Fe 改成 3 个
//     'gtceu:water_fluid':    { set: { H: '2000 mB', O: '1000 mB' } },   // 流体：单位必须写 mB
//     'gtceu:water_fluid':    { setFluid: { H: 2000 } },     // 等价写法（setFluid 的数字一律当 mB）
//
//   ---- 加元素：add（字符串带 mB 就是流体，否则物品）----
//     'gtceu:sheldonite':     { add: ['Rh 4'] },            // 追加 4 个铑粉
//     'gtceu:sheldonite':     { add: ['Rh 4000 mB'] },      // 追加 4000 mB 铑流体
//
//   ---- 合起来 ----
//     'gtladditions:proto_halkonite': { '#dust':  { add: ['Rh 2'], set: { O: 16 } },
//                                        '#fluid': { add: ['Rh 2'] } }
// =============================================================================
global.SD_EXTRA = {
};

// =============================================================================
// ── 原 shanhai_symbol_deconstruct.js -- 9 个特殊符号所在材质的【原初物质解构】配方
//
// 🔴 状态：已落地 `…\GTL山海9.10test\kubejs\server_scripts\`
// 🔴 依赖：类型 `primordial_matter_deconstruction` 的 fluid-out 上限已放宽到 16
//          （jar `9ed75442…`：setMaxIOSize(1, 20, 1, 16) + .rtui 190×114 / fluid_out 16 格）。
//
// -----------------------------------------------------------------------------
// 2026-09-26 第三次修：流体写法改成【包内先例】的 `'<id> <数量>'`
// -----------------------------------------------------------------------------
// 🔴 **用户报：「流体的id全错，你看那个0mb是典型的id写错的表现」** ⇒ **用户是对的**。
//    **真因 = 我用了【物品的语法】去写流体。**
//      · 原来：`inputFluids('3000x gtladditions:phonon_crystal_solution')`
//        —— `'<数量>x <id>'` 是**物品**的语法。
//      · 实测（专服，带对照）：
//            Fluid.of('1000x minecraft:water')  => id=minecraft:1000x  ❌ 垃圾 id
//            Fluid.of('1000mB gtceu:infinity')  => ResourceLocationException（非法路径字符）
//        ⇒ KubeJS 的 `FluidStackJS.of(String)` **把第一个空格之前的整段当作 id**
//          ⇒ id 变成 `minecraft:1000x` ⇒ **JEI 槽渲染成 `0mB`**（正是用户看到的现象）。
//      · **正确写法（包内先例，实测通过）**：`'<流体id> <数量>'` —— **id 在前、空格、数量**：
//            Fluid.of('minecraft:water 1000')   => id=minecraft:water , amount=1000 ✅
//        🔴 这包装配里这个写法有 **1,306 处 `inputFluids` + 575 处 `outputFluids`** 在用，例如
//           `gtceu.js`  L938 `.inputFluids("gtceu:glue 20")`
//           `gtceu.js`  L987 `.inputFluids("gtceu:sodium_potassium 10000", "gtceu:soldering_alloy 2880")`
//           `shanhai_pf_recipes.js` L379 `inputFluids: ['gtceu:glue 16000'],`
//           `shanhai_pf_recipes.js` L402 `outputFluids: ['shanhai:zero_point_energy 32000', …],`
//        ⚠️ 更正我早先说错的一句：**"本包没有流体先例"是【假否定】** —— 我当初搜的是
//           `fluidInputs|fluidOutputs`（词序写反了），所以才 0 命中。**包内先例一直都有，而且很多。**
//    ⇒ **用户给的线索("id 写错了")方向正确，但更精确的说法是【写法用错了语法】。**
//
// 🔴 **用户裁决②**：「都把编程电路给写进去了」⇒ 电路是**工具/催化剂，不是物质** ⇒ **不吐**。
//    ⇒ 规则：**吐【有物质形态的】（尘／锭／流体），不吐【电路／催化剂／容器】**。
//    ⇒ #4 输出里那个 `gtceu:programmed_circuit{Configuration:5}` **已删**（5 条里只有这一个是非物质）。
//
// 🔴 **用户裁决③**：「电压改成LV吧」⇒ 5 条**全部** `EUt(1920)`(EV) ⇒ **`EUt(32)`(LV)**。
//
// -----------------------------------------------------------------------------
// 用户交办原话（逐字）
// -----------------------------------------------------------------------------
//   「对，就按照这些配方把产物拆回元素，这9个符号并不是一种元素，而是一种化合物的简写形式…」
//   「就先拆成这个化合物吧，一路拆到底因为有很多条路，你可能搞不清」
//   裁决：「1：按照配比，若有小数则可以整体倍增配方，2：放宽上限到 16，3：把它们注册成元素，4：就用占位的」
//   后续：「有一些问题，好像是物品id写错了，然后还有的都把编程电路给写进去了，电压改成LV吧」
//         「流体的id全错，你看那个0mb是典型的id写错的表现」
//         「算了你先查，但是要我的经验看，就是流体物品id写错了，不过我也不能100%保证」
//
// -----------------------------------------------------------------------------
// 输出量口径 = **按配比，遇小数整条等比放大**（裁决第 1 条）
// -----------------------------------------------------------------------------
//   做法：把「产线一次投料的整批」当输入 ⇒ 输出正好 = 产线整批输入 ⇒ **天然全整数、配比不变**。
//   与用户给的例子逐字一致（用户写「3000mB 溶液 ⇒ 2 晶种+8+8+73728mB」⇒ 本文件 #2 正是 `3000`）✅
//
//   ⚠️ **倍数（相对 1000mB / 1x 基准）**，无一条爆 int、无天文数字：
//     #  材质                          产线一次产出   本条输入    倍数
//     1  proto_halkonite             1x            1x          ×1
//     2  phonon_crystal_solution    3000 mB       3000 mB     **×3**
//     3  star_gate_crystal_slurry   1000 mB       1000 mB     ×1
//     4  phonon_medium              1000 mB       1000 mB     ×1
//     5  proto_halkonite_base       1152 mB       1152 mB     **×1.152**
//
// -----------------------------------------------------------------------------
// 只挂 5 条 —— 公式里含符号的材质只有 5 个（证据见 shanhai_symbol_elements.js）
// -----------------------------------------------------------------------------
//   1  gtladditions:proto_halkonite          Ж ⊕ ☄ ⚛   ← #1 chemical_bath
//   2  gtladditions:phonon_crystal_solution  〄          ← #2 electric_blast_furnace
//   3  gtladditions:star_gate_crystal_slurry ✟✵✟      ← #3 dimensionally_transcendent_mixer
//   4  gtladditions:phonon_medium            ⌘ ☯ 〄     ← #6 chaotic_alchemy
//   5  gtladditions:molten_proto_halkonite_base Ж ⊕ ☄ ⚛ ← #8 alloy_blast_smelter
//   🔴 `gtceu:draconiumawakened`（公式 `✵Dc✵`）**用户 2026-09-26 裁定：不做**
//      （原话逐字：「那个需要容器的先不要写，那个是游戏里最复杂的部分」）。
//      ⚠️ 不是"没产线"：全量扫描确认有 132 条配方产出它，最正的一条带【容器物品】(cell 换 cell)。
//      **本文件故意不写这条，别以为是漏了** —— 详见文件末尾「已裁定不做」第 1 条。
//
// ⚠️ KubeJS = Rhino：只用 var；无 let/const、无箭头、无模板串、无 ?.、无解构。
// =============================================================================

ServerEvents.recipes(function (event) {
    var gtr = event.recipes.gtceu
    var T = 'primordial_matter_deconstruction'

    var ok = 0
    var bad = 0
    var errList = ''
    var rb = ''

    // 每条：输入 = 产线一次投料的整批；输出 = 产线一次投料的全部输入
    // 物品用 `'Nx id'`（物品语法）；流体用 `'<id> 数量'`（**包内流体语法，id 在前**）。
    var JOBS = [

        // ── 1. proto_halkonite（Ж ⊕ ☄ ⚛）← #1 chemical_bath ──────────────────
        //    产线原样：1x avaritia:infinity_ingot + 1440mB molten_proto_halkonite_base
        //              → 1x gtladditions:hot_proto_halkonite_ingot
        {
            name: 'proto_halkonite_from_chemical_bath',
            inItem: '1x gtladditions:proto_halkonite_dust',
            inFluid: null,
            outItems: ['1x avaritia:infinity_ingot'],
            outFluids: ['gtladditions:molten_proto_halkonite_base 1440']
        },

        // ── 2. phonon_crystal_solution（〄）← #2 electric_blast_furnace ────────
        //    产线原样：2x phononic_seed_crystal + 8x eternity_dust + 8x shirabon_dust
        //              + 73728mB mellion → 3000mB phonon_crystal_solution
        //    ⚠️ 用户点名的例子就是这条 ⇒ 输入写 3000（×3），全整数
        {
            name: 'phonon_crystal_solution_from_ebf',
            inItem: null,
            inFluid: 'gtladditions:phonon_crystal_solution 3000',
            outItems: ['2x gtladditions:phononic_seed_crystal',
                       '8x gtceu:eternity_dust',
                       '8x gtceu:shirabon_dust'],
            outFluids: ['gtladditions:mellion 73728']
        },

        // ── 3. star_gate_crystal_slurry（✟✵✟）← #3 DTM ───────────────────────
        //    产线原样：9×4096x kubejs:*_matter + 16×1000000mB 流体 → 1000mB 浆液
        //    🔴 这条是「放宽上限到 16」的起因：16 种流体输出。
        {
            name: 'star_gate_crystal_slurry_from_dtm',
            inItem: null,
            inFluid: 'gtladditions:star_gate_crystal_slurry 1000',
            outItems: ['4096x kubejs:void_matter',
                       '4096x kubejs:temporal_matter',
                       '4096x kubejs:omni_matter',
                       '4096x kubejs:kinetic_matter',
                       '4096x kubejs:essentia_matter',
                       '4096x kubejs:corporeal_matter',
                       '4096x kubejs:amorphous_matter',
                       '4096x kubejs:proto_matter',
                       '4096x kubejs:dark_matter'],
            outFluids: ['gtceu:infinity 1000000',
                        'gtceu:eternity 1000000',
                        'gtceu:chaos 1000000',
                        'gtceu:cosmic 1000000',
                        'gtceu:miracle 1000000',
                        'gtceu:spatialfluid 1000000',
                        'gtceu:cosmicneutronium 1000000',
                        'gtceu:magnetohydrodynamicallyconstrainedstarmatter 1000000',
                        'gtceu:magmatter 1000000',
                        'gtceu:primordialmatter 1000000',
                        'gtceu:spacetime 1000000',
                        'gtceu:temporalfluid 1000000',
                        'gtceu:shirabon 1000000',
                        'gtladditions:phonon_medium 1000000',
                        'gtceu:exciteddtec 1000000',
                        'gtceu:exciteddtsc 1000000']
        },

        // ── 4. phonon_medium（⌘ ☯ 〄）← #6 chaotic_alchemy ────────────────────
        //    产线原样：15/47/35/60x 尘 + 1x programmed_circuit{Configuration:5}
        //              + 4000mB phonon_crystal_solution → 1000mB phonon_medium
        //    🔴 **那个编程电路【已删】**（用户裁决：电路是工具/催化剂，不是物质）。
        {
            name: 'phonon_medium_from_chaotic_alchemy',
            inItem: null,
            inFluid: 'gtladditions:phonon_medium 1000',
            outItems: ['15x gtceu:magneto_resonatic_dust',
                       '47x gtceu:metastable_oganesson_dust',
                       '35x gtceu:praseodymium_dust',
                       '60x gtceu:echoite_dust'],
            outFluids: ['gtladditions:phonon_crystal_solution 4000']
        },

        // ── 5. proto_halkonite_base（Ж ⊕ ☄ ⚛）← #8 alloy_blast_smelter ───────
        //    产线原样：4/4/4/2/2x 尘 + 576mB dimensionallytranscendentresidue
        //              → 1152mB molten_proto_halkonite_base（一次出 1152，不是 1000 ⇒ ×1.152）
        {
            name: 'proto_halkonite_base_from_alloy_blast_smelter',
            inItem: null,
            inFluid: 'gtladditions:molten_proto_halkonite_base 1152',
            outItems: ['4x gtceu:transcendentmetal_dust',
                       '4x gtceu:tairitsu_dust',
                       '4x gtceu:tartarite_dust',
                       '2x gtceu:titan_precision_steel_dust',
                       '2x gtceu:eternity_dust'],
            outFluids: ['gtceu:dimensionallytranscendentresidue 576']
        }
    ]

    var j
    for (j = 0; j < JOBS.length; j++) {
        var J = JOBS[j]
        var rid = 'shanhai:deconstruct/' + J.name
        try {
            var b = gtr[T](rid)
            if (J.inItem !== null) { b = b.itemInputs(J.inItem) }
            if (J.inFluid !== null) { b = b.inputFluids(J.inFluid) }
            b = b.itemOutputs(J.outItems)
            b = b.outputFluids(J.outFluids)
            // 🔴 用户裁决③：电压改成 LV（原 EV=1920）
            b = b.duration(200).EUt(32)
            ok = ok + 1
        } catch (e) {
            bad = bad + 1
            if (errList.length < 1500) { errList = errList + J.name + ' => ' + e + ' | ' }
        }
    }

    // 判据行：期望 ok=5 failed=0
    console.info('[SHANHAI-SYMDECON] type=' + T + ' jobs=' + JOBS.length
        + ' => ok=' + ok + ' failed=' + bad + ' EUt=32 duration=200')
    if (bad > 0) { console.info('[SHANHAI-SYMDECON] 失败清单: ' + errList) }

    // 🔴 「读回来再写」：把每条流体字符串**用 KubeJS 自己的解析器解一遍**，
    //    打印解出来的 id＋数量 —— 只有 id 正确、数量正确才算过。
    //    （上次就是因为只验了 ok=5 没读内容，把 `0mB` 放过去了。）
    for (j = 0; j < JOBS.length; j++) {
        var J2 = JOBS[j]
        var line = J2.name + ' | IN='
        if (J2.inFluid === null) { line = line + '(none)' }
        else {
            var fi = Fluid.of(J2.inFluid)
            line = line + fi.getId() + ':' + fi.getAmount()
        }
        line = line + ' | OUT='
        var k
        for (k = 0; k < J2.outFluids.length; k++) {
            var fo = Fluid.of(J2.outFluids[k])
            line = line + fo.getId() + ':' + fo.getAmount() + ' '
        }
        rb = rb + line + ' ;; '
    }
    console.info('[SHANHAI-SYMDECON] fluid parsed readback => ' + rb)
})

// =============================================================================
// 遗留 / 已裁定不做（别以为是漏了）
// =============================================================================
// 1. 🔴 **`gtceu:draconiumawakened`（公式 `✵Dc✵`，含符号 `✵`）—— 用户 2026-09-26 裁定：不做。**
//    用户原话（逐字）：「那个需要容器的先不要写，那个是游戏里最复杂的部分」
//    ⇒ 理由 = 那条产线带【容器物品】（plasma_condenser：
//        `100000mB gtceu:liquid_helium` + `1x kubejs:draconiumawakened_plasma_containment_cell`
//        → `1000mB gtceu:draconiumawakened` + `100000mB gtceu:helium` + `1x kubejs:plasma_containment_cell`
//        —— cell 换 cell），用户认为那是游戏里最复杂的部分，**暂不做**。
//    ⚠️ 更正我早先说错的话：**不是"无产线可拆"** —— 全量扫 65,646 个配方文件后确认
//       **有 132 条配方产出它**。用户给的 8 条里没有而已。
// 2. `duration(200)` = 占位值（用户裁决第 4 条：就用占位的）；`EUt(32)` = 用户指定 LV。
// 3. 本文件已落地 `…\GTL山海9.10test\kubejs\server_scripts\shanhai_symbol_deconstruct.js`。
// =============================================================================
// ═══ 手写区 结束 ═══
