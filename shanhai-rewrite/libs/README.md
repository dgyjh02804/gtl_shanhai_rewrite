# `libs/` —— 编译依赖（**jar 本身不入 git**）

本目录是 `shanhai-rewrite` 的**编译依赖**（Gradle 用 `implementation fileTree(dir: 'libs', include: '*.jar')` 引入）。

🔴 **33 个 jar ≈ 105 MB，被 `.gitignore` 排除** —— 仓库里只提交**这份清单**，jar 请自行准备。

## 怎么准备

从任意一份 **GTL 整合包实例**的 `mods/` 目录里，把下表这 33 个 jar 拷进本目录即可。
（参照实例 `mods/` 里约有 **90 个 jar**，其余 57 个是**运行期**才需要的，编译不需要。）

拷完之后：

```bash
cd shanhai-rewrite

# JDK 17（本工程用 corretto-17）
export JAVA_HOME=/path/to/jdk-17

# -D 参数走 GRADLE_OPTS，别直接写在命令行（PowerShell 会把 -D 当任务名）
export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME -Dorg.gradle.java.installations.paths=$JAVA_HOME"

gradle build -x test --no-daemon
```

产物：`shanhai-rewrite/build/libs/shanhai-0.1.0.jar`

> ⚠️ 本工程**没有 gradle wrapper**，请自备 Gradle 8.8。
> ⚠️ 本工程**没有远程 mod maven**，所以依赖只能这样 vendored 进来。

## 清单（33 个 ／ 合计 105.0 MB）

| # | jar | MB | sha256 前 12 位 |
|---|---|---|---|
| 1 | `ae2wtlib-15.3.3-forge.jar` | 0.22 | `39E5CEDE37D5` |
| 2 | `appliedenergistics2-forge-15.4.10.jar` | 8.10 | `FBFEE05C6674` |
| 3 | `architectury-9.2.14-forge.jar` | 0.55 | `218B471D0B8A` |
| 4 | `cloth-config-11.1.136-forge.jar` | 1.13 | `1E895E85CF5B` |
| 5 | `curios-forge-5.14.1+1.20.1.jar` | 0.38 | `1E817919A35B` |
| 6 | `extendedae_plus-1.5.4.1.jar` | 1.20 | `5AA20AA41D72` |
| 7 | `ExtendedAE-1.20-1.4.19-forge.jar` | 2.83 | `89FC6D01D979` |
| 8 | `ftb-library-forge-2001.2.13.jar` | 0.74 | `41810281B653` |
| 9 | `ftb-quests-forge-2001.4.22.jar` | 1.20 | `9EA9D159A78A` |
| 10 | `ftb-teams-forge-2001.3.2.jar` | 0.24 | `B1CB4D82EA9F` |
| 11 | `Glodium-1.20-1.5-forge.jar` | 0.06 | `FC419CB5BF51` |
| 12 | `gtceu-1.20.1-1.4.4.jar` | 14.69 | `E7B5239E1FED` |
| 13 | 🔴 `gtladditions-3.2.8Custom-fix1.jar` | 38.83 | `3D76AC180564` |
| 14 | `gtlcore-1.2.3.2.jar` | 9.92 | `6A0F2F6D7199` |
| 15 | `gtmthings-1.3.5.b.jar` | 0.46 | `B230DE569B0F` |
| 16 | `guideme-20.1.15.jar` | 8.98 | `CF8052AB3DA1` |
| 17 | `Jade-1.20.1-Forge-11.13.3.jar` | 0.53 | `A632C77975E5` |
| 18 | `jei-1.20.1-forge-15.49.0.188.jar` | 1.59 | `070229014841` |
| 19 | `kubejs-forge-2001.6.5-build.26.jar` | 1.58 | `1769312192FB` |
| 20 | `ldlib-forge-1.20.1-1.0.33.b.jar` | 2.88 | `5B1C73E63CD6` |
| 21 | `merequester-forge-1.20.1-1.1.5.jar` | 0.16 | `119C23431FF0` |
| 22 | `mixinextras-forge-0.2.0.jar` | 0.13 | `3B1CDBD088CD` |
| 23 | `polylib-forge-2000.0.3-build.143.jar` | 1.28 | `ABF42A3240B0` |
| 24 | `Registrate-MC1.20-1.3.3.jar` | 0.16 | `226862D4638B` |
| 25 | `resourcefulconfig-forge-1.20.1-2.1.3.jar` | 0.13 | `C404B9E5CF85` |
| 26 | `resourcefullib-forge-1.20.1-2.1.29.jar` | 0.41 | `B13AFB95231D` |
| 27 | `rhino-forge-2001.2.3-build.10.jar` | 1.71 | `FED221142930` |
| 28 | `Shrink-1.20.1-1.4.5.jar` | 0.08 | `A7AF6DC3BC08` |
| 29 | `sophisticatedbackpacks-1.20.1-3.24.65.2073.jar` | 1.11 | `673362C7C31C` |
| 30 | `sophisticatedcore-1.20.1-1.3.79.2250.jar` | 1.56 | `D6DEE35CFAED` |
| 31 | `sophisticatedstorage-1.20.1-1.4.79.2078.jar` | 1.77 | `B547712EC3CB` |
| 32 | `ToolBelt-1.20.1-1.20.03.jar` | 0.24 | `9C1B8C52D848` |
| 33 | `wildcard_pattern-0.1.2-gtl.jar` | 0.09 | `A18745EBD18E` |

## ⚠️ 两个必须对齐的

**`gtladditions` 版本必须一致**（高危）—— 旧工程的 `gtladditions-3.2.1Custom_SubSpace.jar` 与本工程用的
`3.2.8Custom-fix1` **内容不同**，混用会 `NoSuchMethodError`。
`3.2.8Custom-fix1.jar`：`40,712,273 B` ／ sha256 `3D76AC18…`

**`gtlcore` 只需一份** —— 实例里可能出现同 jar 换名的两份副本
（如 `[GTLCore]gtlcore-1.2.3.1-fix9.jar` 与 `gtlcore-1.2.3.2.jar`，**字节完全相同**），
只放一份，避免 classpath 重复。
