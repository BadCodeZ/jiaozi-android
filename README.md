# 综合教资备考平台

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com/about/versions/8.0)
[![Target SDK](https://img.shields.io/badge/targetSdk-34-brightgreen)](https://developer.android.com/about/versions/14)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Release](https://img.shields.io/github/v/release/BadCodeZ/jiaozi-android?label=Release)](https://github.com/BadCodeZ/jiaozi-android/releases)
[![Author](https://img.shields.io/badge/Author-BadCodeZ-181717?logo=github)](https://github.com/BadCodeZ)

**Comprehensive Teacher Certification Exam Preparation Platform — Android App**  
面向教师资格证考试（科一/科二/科三）的离线优先安卓备考应用，零网络依赖，数据自持。

**Language**: [中文](#中文版) · [English](#english)

---

## 中文版

### 目录

- [概览](#概览)
- [相关项目](#相关项目)
- [版本对比](#版本对比)
- [功能特性](#功能特性)
- [截图](#截图)
- [下载与安装](#下载与安装)
- [构建指南（源码）](#构建指南源码)
- [快速上手](#快速上手)
- [技术架构](#技术架构)
- [技术规格](#技术规格)
- [数据安全与隐私](#数据安全与隐私)
- [常见问题](#常见问题)
- [版本历史](#版本历史)
- [贡献指南](#贡献指南)
- [许可证](#许可证)

### 概览

**综合教资备考平台（安卓端）** 是网页端「综合教资备考工作台」的手机端正式版，完整移植了核心练习闭环，并针对移动端体验进行了深度优化。

| 项目 | 说明 |
|------|------|
| 正式版 | V1.1（内部版本 V2.75） |
| 内置题量 | 3,342 道（覆盖科一/科二全章节 + 科三 17 个学科） |
| 安装包 | `JiaoziAPP v2.75.apk`（18.3 MB） |
| 文件完整性 | MD5: `ad3995bc3fe35b45b86bc4707a3c074f` |
| 运行环境 | Android 8.0 (API 26) 及以上 |
| 网络依赖 | 核心功能全部离线可用，仅 AI 讲评和同步需联网 |
| 源码 | 61 个 Kotlin 文件，完整开源 |
| 构建工具 | Gradle 8.9 + AGP 8.4.2 + Kotlin 1.9.24 |
| 作者 | BadCodeZ |

### 相关项目

- **网页端**：`jiazi-practice-platform` — 在线版教资备考工作台，与安卓端数据互通
  - 仓库：https://github.com/BadCodeZ/jiazi-practice-platform
  - 在线使用：https://badcodez.github.io/Jiazi-Practice-Platform/

### 版本对比

| 项目 | 正式版 V1 | 正式版 V1.1（本版本） |
|------|-----------|------------------------|
| 内部版本 | V2.35.5 | **V2.75** |
| 安装包 | `JiaoziAPP v2.35.5.apk`（17.7 MB） | **`JiaoziAPP v2.75.apk`（18.3 MB）** |
| 源码 | 未提供 | **完整 Kotlin 源码（61 个文件）** |
| 发布时间 | 2026-08-16 | **2026-08-23** |

本版本相对 V1 经历了大量迭代演进，并首次开放完整 Android 源码，既用于开源分享，也作为源码备份。

### 功能特性

#### 练习系统

- **多种练习模式**：随机全科 / 按科目 / 章节练习 / 薄弱优先 / 仅复习错题，支持自定义题量（10/20/30/50）
- **客观题即时判**：点选选项即显示正确答案，答错后需标记错因（半强制，至少选一项），用于后续智能组卷
- **主观题两步流**：先写草稿要点，再展开参考答案自评"答对了/还不会"，作答记录可追溯回看
- **专注计时**：练习会话内顶部显示"专注 mm:ss · 第 x/y 题"，切换 tab 计时不中断，可中途退出（已做题自动落盘）
- **全科模考**：按章节权重加权随机抽题，模拟真实考试比例；带倒计时，最后 60/30/10 秒震动提醒，归零强制交卷
- **结算报告**：分科正确率（科一/科二/科三）、分数预估（百分制）

#### 错题与校订

- **智能错题本**：答错题自动归类，按错因（5 类）筛选分组，支持导出 PDF 打印
- **薄弱度排序**：按错误频次降序排列，seed 种子确保可复现
- **章节归类**：手动指派章节归属，辅助题库维护
- **AI 复核**：自动检测解析过短或缺失答案的题目，供人工复核

#### 知识管理

- **知识卡片**：按章节组织考点，支持收藏标记和搜索
- **知识图谱**：可缩放平移的图形化知识图谱导航（GraphScreen）
- **备课模块**：十二要素教案编辑器 + 模板库（科一/科二/科三模板）+ 课标库/教材库上传引用
- **科三教学设计训练**：抽题写简案，六维评分（目标/过程/策略/评价/重难点/板书）

#### AI 辅助（可选，需联网）

- **AI 讲评**：练习结算页可生成错因分析 + 纵向回顾（历史正确率、同类对比、薄弱提示）
- **AI 出题**：基于已有知识点生成新题辅助练习
- **多服务商支持**：DeepSeek / OpenAI / Moonshot，配置 API Key 后使用
- **离线兜底**：无 Key 时自动降级，不予联网报错，展示通用备考建议

#### 移动端专属

- **灵动岛（上岛）**：系统级悬浮胶囊，常驻顶部显示学习统计（实验性特性，部分功能尚在完善中）
- **桌面组件**：系统原生小组件，支持添加到桌面（实验性特性，部分功能尚在完善中）
- **主题系统**：浅色/深色/跟随系统；美术主题包（墨绿/小米蓝）；字体大小（sm/md/lg/xl）；跟随系统壁纸取色

#### 多端同步

- **WebDAV 同步**：与网页端「综合教资备考工作台」数据互通，支持仅上传/仅下载/双向合并三种方向
- **加密传输**：AES-GCM 零知识加密（SYNCPKG1 格式），服务器不可读
- **离线备份**：导出同步包文本，换设备粘贴导入即可合并

---

### 截图

| 今日 | 练习 | 题库 | 统计 | 我的 |
|------|------|------|------|------|
| 今日 | 练习 | 题库 | 统计 | 我的 |

> 截图待补充。欢迎提交 Pull Request 添加截图，建议尺寸 1080x2400 或等比例截图，放置于 `_screenshots/` 目录。

---

### 下载与安装

#### 下载

从 [Releases 页面](https://github.com/BadCodeZ/jiaozi-android/releases) 下载最新 APK 安装包。

#### 安装步骤

1. 将 APK 传输到安卓手机（微信文件传输 / USB 连接 / 网盘均可）
2. 在手机上点击 APK 文件，按系统提示开启"允许安装未知来源应用"
3. 安装完成后桌面出现"综合教资备考平台"图标
4. 首次启动设置目标考试日，即可开始使用

#### 版本更新

覆盖安装新版 APK（同包名 `com.jiaozi.sz`）**不会清空用户数据**——Room 数据库内建版本迁移机制，进度与错题记录全部保留。

---

### 构建指南（源码）

#### 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17 |
| Android SDK | API 34 |
| Gradle | 8.9 |
| AGP | 8.4.2 |
| Kotlin | 1.9.24 |

#### 构建步骤

```bash
# 1. 克隆仓库
git clone https://github.com/BadCodeZ/jiaozi-android.git
cd jiaozi-android

# 2. （可选）配置 local.properties 指向 SDK
# sdk.dir=C:/Users/<你的用户名>/AppData/Local/Android/Sdk

# 3. 构建 Debug 包
./gradlew assembleDebug

# 4. 输出 APK
# app/build/outputs/apk/debug/app-debug.apk
```

#### 注意事项

- `app/build.gradle` 中 `versionCode/versionName` 为工程默认值（1.0），实际发布版本由 APK 文件名及内部逻辑确定
- 测试用 `debug.keystore` 不随源码分发，需自行生成或改用官方签名
- 题库与知识库内置在 `assets/`，不随同步包导出

---

### 快速上手

#### 三步开始

1. **设置目标日**：首次启动引导或「我的 → 设置 → 教资目标考试日」设定笔试日期，首页出现倒计时
2. **开始练习**：首页点击「开始练习」（默认随机全科 20 题），或进入「练习」选项卡选择模式/题量/科目
3. **复习错题**：答错题自动进入「错题本/仅复习」，在「校订 → 错题本」查看，或在「练习 → 仅复习」按间隔重练

#### 数据迁移（换设备）

旧设备导出 → 新设备导入，两步完成：

1. 旧设备：设置 → 数据同步 → "导出同步包" → 复制文本
2. 新设备：安装 APK → 设置 → 数据同步 → "导入同步包" → 粘贴文本 + 输入口令 → 解密合并

---

### 技术架构

#### 应用架构

```
┌─────────────────────────────────────────────────────────┐
│                        UI Layer                         │
│   Jetpack Compose · Material 3 · DataBinding           │
│   ViewModel · StateFlow · Navigation Compose           │
├─────────────────────────────────────────────────────────┤
│                    Domain Layer                         │
│   Use Cases · Repository Interfaces · Domain Models    │
│   WeaknessScorer · SpacedRepetition · MergeEngine      │
├─────────────────────────────────────────────────────────┤
│                     Data Layer                          │
│   Room Database · DataStore · Repository Impl         │
│   WebDAV Client · AI Provider Adapter · SyncCrypto     │
└─────────────────────────────────────────────────────────┘
```

#### 技术栈

| 类别 | 技术选型 |
|------|----------|
| 语言 | Kotlin 1.9.24 |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository Pattern |
| 数据持久化 | Room（SQLite） |
| 异步 | Kotlin Coroutines + Flow |
| 依赖注入 | Hilt |
| 导航 | Compose Navigation |
| 网络 | OkHttp / Retrofit |
| 序列化 | Kotlin Serialization |
| 构建工具 | Gradle 8.9 + AGP 8.4.2 |
| 最低 SDK | 26（Android 8.0） |
| 目标 SDK | 34（Android 14） |

---

### 技术规格

| 项目 | 值 |
|------|------|
| 安装包 | `JiaoziAPP v2.75.apk` |
| 大小 | 18.3 MB |
| MD5 | `ad3995bc3fe35b45b86bc4707a3c074f` |
| 内部版本 | V2.75 |
| 正式版 | V1.1 |
| 包名 | com.jiaozi.sz |
| 内置题量 | 3,342 道 |
| 科三学科 | 17 个 |
| 源码文件数 | 61 个 Kotlin 文件 |
| 最低系统 | Android 8.0 (API 26) |
| 目标系统 | Android 14 (API 34) |
| 数据存储 | Room 数据库（Android 沙箱） |
| 同步格式 | v2 信封（AES-GCM，与网页端互通） |
| AI Key 存储 | 独立存储，不进入同步包，不随备份外泄 |

---

### 数据安全与隐私

- **零服务器数据收集**：所有练习记录、错题、自建题、备课数据只存于本机 Room 数据库，不上传任何第三方服务器
- **AI Key 单独存储**：API Key 独立存储，不进入同步包，不随备份外泄
- **同步端到端加密**：WebDAV 同步采用 AES-GCM 零知识加密，同步服务方不可读取数据内容
- **最小权限原则**：App 不申请通讯录、定位、相册等无关权限；仅灵动岛功能需悬浮窗权限（在系统设置中可随时关闭）

---

### 常见问题

#### 安装与运行

- **安装被拦截**：确认已开启"允许安装未知来源"。安装包 MD5 为 `ad3995bc3fe35b45b86bc4707a3c074f`，与发布一致即未被篡改
- **白屏/卡在启动**：首次启动需从内置题库建索引（约 1-2 秒），属正常；若长时间空白，杀进程重开
- **鸿蒙系统兼容**：部分国产 ROM 需手动授权安装权限

#### 数据与备份

- **数据丢失怎么办**：只要未卸载 App、未清数据，记录都在 Room 库。卸载/清数据会丢失，请先导出同步包备份
- **换手机如何迁移**：用导出/导入同步包或 WebDAV 互通，无需重练
- **覆盖安装会清数据吗**：不会，Room 数据库按版本迁移（ALTER TABLE ADD COLUMN），保留全部进度

#### 功能相关

- **灵动岛开不了**：灵动岛为实验性特性，功能尚在完善中。开启后系统引导至应用详情页 → 权限管理 → 显示悬浮窗授权；未授权不会崩溃，仅显示红色引导卡
- **桌面组件不显示**：桌面组件为实验性特性，部分功能尚在完善中，不同机型表现可能有差异
- **科三切换后章节仍显示"美术"**：设置切到目标学科后，题库/图谱章节名应带学科标识，如仍异常请反馈
- **AI 功能无法使用**：确认已在设置页配置服务商和 API Key。无 Key 时自动使用离线通用备考建议兜底
- **是否需要联网**：核心刷题/错题/模考/备课/知识库全部离线；仅 AI 讲评/出题/帮手/WebDAV 同步需联网

---

### 版本历史

#### V1.1（V2.75）— 开源版

- V2.35.5 之后的多轮迭代稳定版
- 首次公开完整 Kotlin 源码（61 个文件，UI/Domain/Data 三层）
- 功能增强：AI 模块、知识图谱、薄弱项引擎、间隔重复、数据合并引擎
- 可复现构建：Gradle 8.9 + AGP 8.4.2 + Kotlin 1.9.24

#### V1（V2.35.5）— 首发版

- 首发正式版归档，内置 3,342 道题，覆盖科一/科二全章节 + 科三 17 个学科
- 完整练习系统（随机/按科目/章节/薄弱优先/仅复习）
- 智能错题本（5 类错因 + 薄弱度排序 + 组卷复习）
- 全科模考（加权抽题 + 倒计时 + 震动提醒 + 结算报告）
- 知识卡片 + 备课模块（十二要素教案编辑器 + 模板库）
- AI 讲评/出题（多服务商支持 + 离线兜底）
- 灵动岛 + 桌面组件（实验性特性）+ 主题系统
- WebDAV 多端同步（与网页端互通，AES-GCM 加密）
- 科三教学设计训练（六维评分）

---

### 贡献指南

欢迎提交 Issue 或 Pull Request。请确保：

1. 提交 Issue 前搜索是否已有类似问题
2. 描述清晰，包含复现步骤（如适用）
3. 代码提交前通过当前层级的测试

---

### 许可证

```
Copyright 2026 BadCodeZ

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## English

### Overview

**jiaozi-android** is an **offline-first Android app** for Chinese Teacher Certification (教师资格证) exam preparation. It is the mobile companion of the web-based workbench, porting the complete practice loop to Android with native mobile features.

| Item | Value |
|------|-------|
| Release | V1.1 (internal V2.75) |
| Built-in Questions | 3,342 (covering Subject 1/2 all chapters + Subject 3 across 17 subjects) |
| Package | `JiaoziAPP v2.75.apk` (18.3 MB) |
| File Integrity | MD5: `ad3995bc3fe35b45b86bc4707a3c074f` |
| Min OS | Android 8.0 (API 26) |
| Network | Core features are fully offline; only AI commentary and sync require internet |
| Source Code | 61 Kotlin files, fully open source |
| Build Tools | Gradle 8.9 + AGP 8.4.2 + Kotlin 1.9.24 |
| Author | BadCodeZ |

### Related Projects

- **Web Version**: `jiazi-practice-platform` — Online teacher certification workbench, data-compatible with this Android app
  - Repository: https://github.com/BadCodeZ/jiazi-practice-platform
  - Live Demo: https://badcodez.github.io/Jiazi-Practice-Platform/

### Key Features

- **Practice Modes**: Random, by subject, by chapter, weak-point priority, error-only review (10/20/30/50 questions)
- **Smart Error Tracking**: Auto-categorize mistakes with 5 error types, weighted review sessions
- **Mock Exams**: Weighted random sampling simulating real exam proportions with countdown timer
- **Knowledge Cards**: Chapter-based review cards with bookmarking and search
- **Knowledge Graph**: Zoomable, pannable knowledge graph navigation (GraphScreen)
- **AI Commentary** (optional): Integration with DeepSeek / OpenAI / Moonshot for question explanations
- **Lesson Planning**: Built-in 12-element lesson plan editor with template library and knowledge base integration
- **Skill 3 Training**: Teaching design exercises with 6-dimension scoring
- **Living Island**: System-level floating capsule showing real-time study stats (experimental, in development)
- **Home Screen Widget**: Native widget support (experimental, in development)
- **Theme System**: Light/Dark/System theme, accent color packs, adjustable font sizes
- **WebDAV Sync**: Encrypted cross-device sync with the web version (AES-GCM)

### Download & Install

Download the latest APK from the [Releases page](https://github.com/BadCodeZ/jiaozi-android/releases).

**Installation**:
1. Transfer the APK to your Android device
2. Tap the APK file and enable "Install from unknown sources" when prompted
3. Set your target exam date on first launch and start practicing

### Build from Source

```bash
git clone https://github.com/BadCodeZ/jiaozi-android.git
cd jiaozi-android
./gradlew assembleDebug
```

### Technical Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository Pattern |
| Persistence | Room (SQLite) |
| Async | Kotlin Coroutines + Flow |
| DI | Hilt |
| Navigation | Compose Navigation |
| Network | OkHttp / Retrofit |
| Build | Gradle 8.9 + AGP 8.4.2 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

### Version History

#### V1.1 (V2.75) — Open Source Release

- Stable release after multiple iterations from V2.35.5
- First public release with complete Kotlin source code (61 files, 3-layer architecture)
- Enhancements: AI module, knowledge graph, weakness scoring engine, spaced repetition, data merge engine
- Reproducible build: Gradle 8.9 + AGP 8.4.2 + Kotlin 1.9.24

#### V1 (V2.35.5) — Initial Release

- First stable release with 3,342 built-in questions
- Full practice system, error notebook, mock exams, knowledge cards
- AI commentary, lesson planning, teaching design training
- Living Island + Home Screen Widget (experimental, in development)
- WebDAV sync with web version

### License

```
Copyright 2026 BadCodeZ
Licensed under the Apache License, Version 2.0.
```

---

**综合教资备考平台**  
作者：BadCodeZ  
仓库：https://github.com/BadCodeZ/jiaozi-android  
相关网页端：https://github.com/BadCodeZ/jiazi-practice-platform  
在线使用：https://badcodez.github.io/Jiazi-Practice-Platform/