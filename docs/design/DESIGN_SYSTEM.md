# 教资备考平台 · 设计系统（Design System）

> 版本：**V2.8.0 / versionCode 82**（UI 重写版）
> 最后更新：2026-10-01 —— 十七校「C-1 页底压深 `#121212`→`#0A0A0A`」（承 十六校 规范收编 / 十五校 深色对齐 UFIPanel）
> 实现位置：`app/src/main/java/com/jiaozi/sz/ui/**`
> 定位：这是一份**可执行的**设计系统。所有色值 / 尺寸 / 参数都是**工程里的真实 token**，不是建议稿；
> 改动请直接改对应 token，不要在新代码里写魔数。

---

## 0. 五条底层原则（任何改动的裁决依据）

| # | 原则 | 一句话 | 违反时的典型症状 |
|---|---|---|---|
| 1 | **全站一蓝** | 蓝色只有一个：亮 `#3B8CF7` / 暗 `#4C9DF8`。主题 `primary` 与语义 `AppColors.blue` **必须恒等** | 深色档出现"浅色是鲜蓝、深色是灰蓝"的断链 |
| 2 | **深色是浮起，不是凹陷** | 深色下玻璃控件 = 比页面底**亮一档**的浮层，不是更黑的坑 | 控件在近黑页面上"融化"、读不出边界 |
| 3 | **克制的光** | 受光带是**边缘跃变**（≈1.5dp），不是 5dp 以上的渐变坡 | 玻璃变成"蒙了一层光"，观感刻意 |
| 4 | **层级靠底色差，不靠描边** | 层级 = 底色阶差 + **两级**阴影（卡 2dp / 悬浮 8dp）；描边只做分隔 | 全屏靠 outline 画框，卡片糊在页底上 |
| 5 | **玻璃是一层薄白 + 真折射** | 半透明填充只有 10%（暗）/ 60%（亮），厚度感来自 `innerShadow` 与 `lens`，不靠加厚 | 选中态"闷"成一块实心浅灰板 |

---

## 1. 色彩系统

### 1.1 主题色板（Material3 ColorScheme，暗态为当前焦点）

**暗色（`HyperDark`）**

| Token | 值 | 说明 |
|---|---|---|
| `primary` | **`#4C9DF8`** | 全站唯一蓝；对比度 6.68:1（原 `#6E96BF` 6.05:1） |
| `onPrimary` | `#071B2E` | 蓝底上的深字 |
| `primaryContainer` | `#14334F` | 深蓝底（徽章 / 选中 chip 底） |
| `onPrimaryContainer` | `#D6E6F2` | 深蓝底上的浅字 |
| `secondary` / `secondaryContainer` | `#505050` / **`#4C9DF8`** | 选中 chip 容器 = 主色（白/深字） |
| `tertiary` | `#84A6C8` | 三级强调 |
| `background` / `surface` | **`#0A0A0A`** | 页底（🔴 十七校 C-1 裁定：由 `#121212` 压深，距参考纯黑仅 10 阶） |
| `onBackground` / `onSurface` | **`#F2F2F2`** | 主内容前景（原 `#E0E0E0`，+18 阶） |
| `surfaceVariant` / `onSurfaceVariant` | `#1F1F1F` / `#B0B0B0` | 次级文本 |
| `surfaceContainerLowest` | `#000000` | 最低层 |
| `surfaceContainerLow` | **`#0A0A0A`** | 低层（与页底同值） |
| `surfaceContainer` | **`#242424`** | **卡片层**（取 UFIPanel 同值；页底→卡片反差 8 → 18 → **26 阶**） |
| `surfaceContainerHigh` | `#2C2C2C` | 高层 |
| `surfaceContainerHighest` | `#363636` | 最高层（三档间距保持 8 阶） |
| `outline` | `#404040` | 强描边 |
| `outlineVariant` | **`#333333`** | **分隔线**；= 卡片底 +15 阶（旧 `#2A2A2A` 与卡片只剩 6 阶，线会消失） |
| `error` | `#FF6B61` | 错误 |

**亮色（`HyperLight`）**

| Token | 值 |
|---|---|
| `primary` / `onPrimary` | **`#3B8CF7`** / `#FFFFFF` |
| `primaryContainer` / `onPrimaryContainer` | `#EAF1FE` / `#0B2E5C` |
| `secondary` / `secondaryContainer` | `#E6E6E6` / `#3B8CF7` |
| `background` / `surface` | `#F7F7F7` |
| `onSurface` / `onSurfaceVariant` | `#1A1A1A` / `#666666` |
| `surfaceContainerLowest` | `#FFFFFF` |
| `surfaceContainerLow` / `surfaceContainer` | `#F2F2F2` / `#FFFFFF` |
| `surfaceContainerHigh` / `Highest` | `#E8E8E8` / `#E8E8E8` |
| `outline` / `outlineVariant` | `#D9D9D9` / `#ECECEC` |

### 1.2 语义色板（`AppSemantic` / `AppColors`，明暗双值）

统一入口 `AppColors.xxx`（自动随 `isDark` 切换）；**两套色板必须同步主题 primary**，否则"一蓝"会断。

| 语义 | 亮 | 暗 | 用途 |
|---|---|---|---|
| `blue` | `#3B8CF7` | `#4C9DF8` | ≡ 主题 primary |
| `blueLight` | `#EAF1FE` | `#14334F` | 深蓝底（**暗态只压深，不提亮**） |
| `blueBg` | `#F1F6FE` | `#13283F` | 快捷入口浅蓝卡面 |
| `success` | `#2F9E6E` | `#4DBE8C` | 掌握 / 正确 |
| `warning` | `#D98A1F` | `#E0A94A` | 待处理 |
| `danger` | `#D64B3F` | `#E97266` | 薄弱 / 错误 |
| `purple` | `#7C6BB0` | `#9E8FCB` | EPUB 题型色 |
| `teal` | `#2AA294` | `#4FC9BA` | 知识卡 menu 前导徽章 / TXT 类型 |
| `purpleBg` / `greenBg` / `redBg` / `warningBg` | `#F2F0F8` / `#EDF7F2` / `#FCF0EE` / `#FDF6EC` | `#232030` / `#14291F` / `#2E1A18` / `#2A2118` | 四类语义浅底（卡面） |
| `textPrimary` | `#1A1A1A` | `#F2F2F2` | ≡ 主题 onSurface |
| `textSecondary` | `#666666` | `#B0B0B0` | 次级文本 |
| `trackGray` | `#E8E8E8` | `#363636` | 进度轨道 |
| `capsuleBg` / `capsuleFg` | `#1A1A1A` / 白 | `#E0E0E0` / `#121212` | 浮空胶囊（明暗反相保对比） |

### 1.3 硬编码色登记（`AppPalette`）

工程内所有 `Color(0xFF…)` 字面量的**唯一登记处**。新增颜色必须先在 `AppPalette` 命名登记，再在 `Theme` / `AppSemantic` 引用；
纯白/黑用 `Color.White/Black`。当前登记 **57 支**，含十五校新增的 `c_ff4c9df8` / `c_ff333333` / `c_ff363636`。

### 1.4 Hero 渐变（`AppGradients.hero`）

- 亮：`#5EA8FB → #3B8CF7`（左上亮 → 右下暗）
- 暗：`#14375F → #2A5B96`
- 不依赖 ColorScheme（避免 primaryContainer 色相漂移导致渐变发灰）；文字恒为白。

---

## 2. 排版（Typography）

`HyperTypography` 对齐 Miuix / HyperOS 刻度，**Medium(500) 字重**，标题带字重、正文不带：

| 档位 | size / lineHeight | 典型用途 |
|---|---|---|
| `displaySmall` | 32 / 40 | 极少数大标题 |
| `headlineLarge` · `Medium` | 28 / 36 | 页面主标题 |
| `headlineMedium` · `Medium` | 24 / 32 | 大节标题 |
| `headlineSmall` · `titleLarge` · `Medium` | 20 / 28 | 卡片主标题 |
| `titleMedium` · `Medium` | 18 / 26 | 小节标题 |
| `titleSmall` · `bodyLarge` · `labelLarge` · `Medium` | 16 / 24 | 正文 / 按钮 |
| `bodyMedium` | 14 / 20 | 次级正文 |
| `bodySmall` · `labelMedium` | 13 / 18 | 辅助说明 |
| `labelSmall` | 11 / 16 | 角标 / 徽章 |

**字体缩放护栏（强制）**：系统缩放钳到 `0.85~1.15`，系统 × App 档位（sm .9 / md 1 / lg 1.12 / xl 1.28）总倍率封顶 **1.60**。
背景：`sp` 会被 `LocalDensity.fontScale` 二次相乘，而定高容器（主按钮 52dp / Hero / 图标 20dp）不跟放 ⇒ 文字撑破容器。
调用点请读 `JiaoziTheme` 注入的 guarded density，不要自己取 `LocalDensity.current`。

---

## 3. 形状（Radius / Shapes）

`Radius` token（`Glass.kt`）：`tiny 4` · `xs 8` · `sm 12` · `md 16` · `lg 20` · `xl 28` · `pill 999`

`MaterialTheme.shapes`：`extraSmall 8` · `small 12` · `medium 20` · `large 24` · `extraLarge 28`

**规则**：卡片默认 **20dp**；容器内元素 12dp；芯片 8dp；胶囊/圆钮 999（玻璃导航栏 28dp、选中胶囊 29dp）。
孤值（9/10/14/6/3/2dp）见 §7 差异清单 `B-2`。

---

## 4. 间距与层级

| Token | 值 | 用途 |
|---|---|---|
| `NavTokens.Radius` / `Height` | 28dp / 60dp（玻璃态 64） | 底部导航药丸 |
| `NavTokens.SideInset` | **23dp** | 导航左右离屏（对齐 UFIPanel 实测 954px 容器） |
| `NavTokens.ContentInset` | **4dp** | 容器内壁 ↔ 首/末格边界（参考 ≈3.9dp，旧 0 导致"挤在一起"） |
| `NavTokens.BottomInset` | 14dp | 距屏底（+ 系统手势 14.5 ≈ 参考 28.4dp） |
| `NavTokens.ContentBottomPad` | **92dp** | 一级页内容底部留白（全站 8 处统一引用） |
| `NavTokens.Elevation` | 8dp | **悬浮件级**阴影（导航 / 浮空返回钮 / 上岛胶囊） |
| `CardTokens.Elevation` | **2dp** | **卡片级**轻阴影（与悬浮件拉开两级层级） |

网格：小米 13 = 1080×2400 @**440dpi**（1dp = **2.75px**）——任何 px→dp 换算**必须先 `adb shell wm density`**。

---

## 5. 材质：iOS 26 Liquid Glass

依赖：`com.github.Kyant0:AndroidLiquidGlass:1.0.0-rc01`（+ Compose BOM 2025.08 / Kotlin 2.1.20 / AGP 8.7.0）。
> 2.0.x 已变 CMP 库且要求 Compose 1.12，**不可用**。

### 5.1 几何

| Token | 值 |
|---|---|
| `LiquidGlass.Height` | 64dp |
| `LiquidGlass.Elevation` | 16dp |
| `LiquidGlass.AlphaLight` / `AlphaDark` | **0.60** / **0.10** |
| `LiquidGlass.Highlight` | 1.25dp 发丝边 |
| `LiquidGlass.HighlightLightScale` | 1.0f（十三校后两侧不再分档） |
| `IndicatorHeight` / `Radius` / `Inset` | 58dp / 29dp / **0dp** |

### 5.2 三套 GlassSpec

| 参数 | `NavSpec`（主胶囊） | `IndicatorSpec`（选中底） | `ButtonSpec`（圆钮） |
|---|---|---|---|
| `blur` | 22dp | 22dp | = NavSpec |
| `lens` | 28dp | 16dp | = NavSpec |
| `innerShadowRadius` / `Alpha` | 7dp / 0.70 | 4dp / 0.55 | = NavSpec |
| `highlight` | `Default(w 1.5 / blur 2.5 / α .5, style Default(intensity .5, **angle −90°**, falloff 1))` | `Plain(α 0)` | = NavSpec |
| `shadow` | `Shadow.Default` | `Shadow.Default(α .10)` | = NavSpec |

三条**必须记住**的库事实（踩过坑）：

1. `HighlightStyle.Default` = `pow(abs(dot),falloff)` ⇒ **上下缘同时起光**（双高光）；
   `Ambient` = `step(0,d)*pow(abs(d))` ⇒ **只留 d≥0 一侧**（单侧光，会抹掉下缘反射）；
   `Plain` = 平面 ⇒ **四边等亮**（一圈轮廓）。
2. 库默认 `angle = 45°`（对角光，横长条上不搭）⇒ **必须显式给 `angle = −90°`**（y 轴朝下，光轴朝上 = 上缘主光 + 下缘反射）。
3. `InnerShadow.offset` 在本工程尺度**完全不生效**；`lens` 因为 `ContentBottomPad = 92dp` 让列表末项停在栏上方，背后恒为纯色 ⇒ 实际可见的是**受光带 + 内阴影 + 投影**。

### 5.3 深色玻璃公式（反推档位用）

```
内部灰度 = 页底 + (255 − 页底) × α        （页底 10 ⇒ 10 + 245α）
compositeOver 会累加 alpha：白 .10 ⊕ 白 .10 = 白 .19
```

实测谱（α 为总白不透明度；页底已于十七校压至 `#0A0A0A` = 10）：

| 总 α | 内部灰度（页底 10） | 透光 | 观感 |
|---|---|---|---|
| 0.19 | 57 | 81% | 实心浅灰板，**闷** |
| 0.127 | 41 | 87% | 仍偏白 |
| **0.1135（当前）** | **38** | **88.7%** | 灰而透光（当前档） |
| 0.10 | 34 | 90% | 与导航栏同色，选中只靠图标变色 |

> 注：页底压深**不改变**玻璃的"浮起量"—— α 决定的是叠加量 `(255−页底)×α`，
> 页底 18→10 时浮起量 24 基本不变（G-4「深色玻璃必须比页底亮」依然成立）。

---

## 6. 动效（Motion）

| Token | 值 | 说明 |
|---|---|---|
| `IndicatorSpring` | ζ **0.78**, k **520**, vt **0.02** | 选中胶囊**位移**：2% 过冲，≈0.23s（稳准快） |
| `JellySpring` | ζ **0.38**, k 520, vt **0.002** | **形变**：27.5% 过冲，两拍半，"停下了还在晃" |
| `BubbleStretchX` / `BubbleSquashY` | 0.075 / 0.05 | 体积守恒：横 +7.5% / 纵 −5% |
| `BubbleSpeedRef` | 1200dp/s（**dp 量纲**） | 换算成 `Animatable` 的「格/秒」前必须 × 格宽，否则 440dpi 低估 186× |
| `PressScaleUp` / `PressJellyStretch` / `PressJellySquash` | 0.10 / 0.06 / 0.06 | 按压反馈 |
| `PressDownMs` / `PressReleaseSpring(vt 0.002)` / `PressReboundGain` | 90ms / — / 2.0 | 按压时序 |

红线：形变**必须走真实布局尺寸**（`Modifier.width/height`），**禁止** `graphicsLayer{scaleX}`——
`drawBackdrop` 按屏幕坐标采样背景，scale 会把已捕获的背景一起缩放 ⇒ 折射与玻璃尺寸错配。
系统「减少动态效果」开启时走 `snapTo`，不读上述 spec。

---

## 7. 组件清单（`ui/components/RedesignComponents.kt`）

`StatCard` · `StatCardCompact` · `HeroStatCell` · `StatBand` · `QuickActionCard` · `ChapterRow` ·
`SectionTitleDot` · `NavRowCard` · `MiniBadge` · `GroupTitle` · `SettingRow` · `SettingSelectRow` ·
`SettingSwitchRow` · `HubChip` · `RecommendCard` · `IconBadge` · `MasteryBadge` · `knowledgeTypeColor`

辅助：`NavSurface`（旧实色导航，向后兼容）· `GlassSurface`（deprecated 别名）· `GlassPanel` / `GlassIconButton`（玻璃族）。
入场动效：`rememberPressFeedback`。状态枚举：`MasteryState`。

---

## 8. 图标与内容色（tint）规则 —— 十六校定稿

> **核心判据**：底部导航的图标色是 `lerp(onSurface, primary, sel)`——**只有"选中"那一格才是主色**，
> 其余四格恒为 `onSurface`。因此**圆钮图标默认必须用 `onSurface`，不是 `primary`**。

| 位置 | 默认 tint | 说明 |
|---|---|---|
| 玻璃圆钮（设置 / 返回 / 关闭 / 任意 `GlassIconButton`） | **`MaterialTheme.colorScheme.onSurface`** | 黑底近白 / 白底近黑，自动跟随明暗 |
| 底部导航未选中 | `onSurface` | 基准 |
| 底部导航选中 | `primary` | 蓝 + 图标放大，是选中态的**主识别手段** |
| 选中胶囊底色 | 基底 + `ALPHA_DARK_SELECT(0.015)` | 色块只承担辅助层级，不抢识别 |
| 快捷入口图标（`practice/mock/wrong/weak`） | `AppColors.blue` | 四入口专属蓝，≡ 主题 primary |

特例清零：`PracticeSetupSheet` 里那处全站唯一的显式 `tint = AppColors.textPrimary` 已删除（默认值本身已正确）。

---

## 9. 判定口径（所有改动必须能被测量复现）

1. **同场景 diff = 0**：改材质时，玻璃上方内容区逐像素 diff 必须为 0.0（验收基线：12 组全 0.0）。
2. **受光带宽度** = 峰值衰减到「内部基线 + 30% 余量」的像素数（当前 14px ≈ 5.1dp）。
3. **过曝比** = 区域 `≥254` 像素占比（浅色玻璃填充易饱和；当前 6.8%，旧 13.1%）。
4. **层级反差** = 页底与卡片的灰阶差（当前 **26 阶**；UFIPanel 参考 36 阶）。十七校前为 18 阶。
5. **对比度** = 前景 on 背景，正文/图标按 AA 4.5:1、大字 3:1。
6. **改完必须**：编译无 error、警告数不高于基线（22 条）、装机真机截图、跨构建同场景逐像素 diff。

---

## 10. 文件索引（改哪里）

| 内容 | 文件 |
|---|---|
| 主题色板 / 字阶 / 圆角 / Hero 渐变 / 字体护栏 | `ui/theme/Theme.kt` |
| 硬编码色登记 | `ui/components/AppPalette.kt` |
| 语义色（明暗） | `ui/components/RedesignComponents.kt`（`AppSemantic` / `AppColors`） |
| 玻璃 token / 动效 / `Radius` / `NavTokens` / `CardTokens` | `ui/components/Glass.kt` |
| 导航栏 / 选中态 / 浮空返回钮挂载 | `ui/AppNav.kt` |
| 圆钮组件本体 | `ui/components/FloatingBackButton.kt` |
