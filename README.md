# 综合教资备考平台（安卓端）· 正式版 V1.1

**Comprehensive Teacher Certification Exam Preparation Platform — Android App V1.1**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com/about/versions/8.0)
[![Target SDK](https://img.shields.io/badge/targetSdk-34-brightgreen)](https://developer.android.com/about/versions/14)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)

---

## 版本说明

| 项目 | 正式版 V1 | **正式版 V1.1（本版本）** |
|------|---------------------------|--------------------------|
| 内部版本 | V2.35.5 | **V2.75** |
| 安装包 | `JiaoziAPP v2.35.5.apk`（17.7 MB） | **`JiaoziAPP v2.75.apk`（18.3 MB）** |
| 源码 | 未提供 | **完整 Kotlin 源码（61 个文件）** |
| 发布时间 | 2026-08-16 | **2026-08-23** |

> **本目录是 V2.75 版的开源归档**，相比 V1（V2.35.5）经历了大量迭代演进，并首次**开放完整 Android 源码**，既用于开源分享，也作为源码备份。

---

## 更新说明（V1.1 / V2.75 vs V1 / V2.35.5）

相比首发版 V1（V2.35.5），V1.1（V2.75）在功能与架构上有显著演进，主要体现在以下方面（完整细节以源码为准）：

### 功能增强
- **倒计时显示修复**：修正了 V2.7 遗留的目标考试日倒计时显示问题
- **AI 能力扩展**：AI 讲评 / 出题 / 帮手能力持续增强，多服务商支持（DeepSeek / OpenAI / Moonshot）
- **全科模考优化**：按章节权重加权抽题的进一步完善
- **知识图谱**：增加可缩放平移的图形化知识图谱（GraphScreen）
- **多端同步加固**：WebDAV 同步与网页端字节级互通，AES-GCM 加密（`SYNCPKG1`）

### 架构演进

- **UI 层**：Compose + Material 3，新增灵动岛（IslandBus）、玻璃拟态（Glass）组件
- **领域层**：新增 `WeaknessScorer`（薄弱度评分）、`SpacedRepetition`（间隔重复）、`MergeEngine`（数据合并）等引擎
- **数据层**：Room 数据库随版本迁移，`data/local`与`data/remote`分层清晰

### 开源交付

- **首次开放完整源码**：61 个 Kotlin 文件，涵盖 UI / Domain / Data 三层
- **可复现构建**：Gradle 8.9 + AGP 8.4.2 + Kotlin 1.9.24 + KSP

---

## 目录结构

```
手机版V1.1/
├── JiaoziAPP v2.75.apk        # 安装包（18.3 MB）
├── LICENSE                     # Apache 2.0 许可证
├── README.md                   # 本说明文件
├── build.gradle                # 根工程 Gradle 配置
├── settings.gradle             # 工程包含模块配置
├── gradle.properties           # Gradle 全局配置
├── gradlew / gradlew.bat       # Gradle Wrapper（构建入口）
├── gradle/                     # Wrapper 元数据
└── app/                        # 主模块源码
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/             # 内置题库 / 大纲 / 知识库
        │   ├── bank.json（3342 题）
        │   ├── auto_syll.json
        │   ├── default_syllabus.json
        │   ├── knowledge.json
        │   └── syllabus.json
        └── java/com/jiaozi/sz/
            ├── App.kt
            ├── MainActivity.kt
            ├── data/           # 数据层（Room / WebDAV / Repository）
            ├── domain/         # 领域层（Practice / Merge / Sync 等引擎）
            ├── ui/             # 界面层（Compose / ViewModel / Screens）
            └── uia/            # 辅助 UI
```

---

## 产品功能（核心能力）

### 练习系统

- **多种练习模式**：随机全科 / 按科目 / 章节练习 / 薄弱优先 / 仅复习错题（10/20/30/50）
- **客观题即时判**：点选即判，答错标记错因（≥1 项）用于智能组卷
- **主观题两步流**：先写草稿要点，再展开参考答案自评，作答可追溯
- **专注计时**：会话计时连续不归零，可中途退出（已做落盘）
- **全科模考**：章节权重加权抽题，倒计时 + 震动提醒 + 强制交卷 + 分科结算

### 错题与校订

- **智能错题本**：错因自动归类（5 类），进行薄弱度排序，导出 PDF 打印
- **章节归类**：手动指派章节，辅助维护
- **AI 复核**：检测解析过短 / 缺失答案的题目

### 知识管理与备课

- **知识卡片**：按章节考点，收藏 + 搜索
- **知识图谱**：可缩放平移的知识图谱导航（GraphScreen）
- **备课模块**：十二要素教案编辑器 + 模板库 + 课标库/教材库上传引用
- **科三教学设计训练**：六维评分（目标/过程/策略/评价/重难点/板书）

### AI 辅助（可选，需联网）

- **AI 讲评**：错因分析 + 纵向回顾
- **AI 出题**：基于知识点生成新题
- **多服务商**：DeepSeek / OpenAI / Moonshot，可配置 Key
- **离线兜底**：无 Key 时自动降级通用建议

### 移动端专属（实验性）

- **灵动岛（上岛）**：系统级悬浮胶囊，实时学习统计（实验性特性，部分功能尚在完善中）
- **桌面组件**：系统原生小组件（实验性特性）
- **主题系统**：浅/深/跟随系统；墨绿/小米蓝主题包；字体大小调节；跟随壁纸取色

### 多端同步

- **WebDAV 同步**：与网页端互通，仅上传/仅下载/双向合并
- **加密**：AES-GCM 零知识加密（`SYNCPKG1`）
- **离线备份**：导出/导入同步包，换设备合并

---

## 构建指南（源码）

### 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17 |
| Android SDK | API 34 |
| Gradle | 8.9 |
| AGP | 8.4.2 |
| Kotlin | 1.9.24 |

### 构建步骤

```bash
# 1. 克隆 / 进入源码目录
git clone <仓库地址>
cd jiaozi-app

# 2. （可选）配置 local.properties 指向 SDK
# sdk.dir=C:/Users/<你的用户名>/AppData/Local/Android/Sdk

# 3. 构建 Debug 包
./gradlew assembleDebug

# 4. 输出 APK
# app/build/outputs/apk/debug/app-debug.apk
```

### 注意事项

- `app/build.gradle` 中 `versionCode/versionName` 为工程默认值（1.0），实际发布版本由 APK 文件名及内部逻辑确定
- 测试用 `debug.keystore` 不随源码分发，需自行生成或改用官方签名
- 题库与知识库内置在 `assets/`，不随同步包导出

---

## 安装方式

1. 将 `JiaoziAPP v2.75.apk` 传到安卓手机（微信 / USB / 网盘）
2. 点击安装，开启「允许安装未知来源」
3. 首次启动设置目标考试日
4. 覆盖安装（同包名 `com.jiaozi.sz`）不清数据，Room 数据库自动迁移

---

## 数据安全与隐私

- 所有数据存于本机 Room 数据库，不上传任何第三方服务器
- AI Key 独立存储，不进同步包、不随备份外泄
- WebDAV 同步 AES-GCM 零知识加密
- 最小权限原则（INTERNET / VIBRATE / 通知 / 前台服务，悬浮窗仅灵动岛需要）

---

## 技术规格

| 项目 | 值 |
|------|------|
| 安装包 | `JiaoziAPP v2.75.apk` |
| 大小 | 18.3 MB |
| MD5 | `ad3995bc3fe35b45b86bc4707a3c074f` |
| 内部版本 | V2.75 |
| 正式版 | V1.1 |
| 包名 | `com.jiaozi.sz` |
| 源码位置 | `app/src/main/java/com/jiaozi/sz/` |
| Kotlin 文件数 | 61 |
| 最低系统 | Android 8.0 (API 26) |
| 目标系统 | Android 14 (API 34) |
| 数据存储 | Room（SQLite，Android 沙箱） |
| 同步格式 | `SYNCPKG1`（AES-GCM，与网页端互通） |

---

## 版本历史

### V1.1（V2.75）— 开源版

- V2.35.5 之后的多轮迭代稳定版
- 首次公开完整 Kotlin 源码
- 功能增强：AI 模块、知识图谱、薄弱项引擎、间隔重复、数据合并等

### V1（V2.35.5）— 首发版

- 首发正式版，内置 3342 题，核心练习闭环
- WebDAV 同步与网页端互通

---

## 许可证

本项目采用 **Apache License 2.0** 开源。版权归 **BadCodeZ** 所有。

```
Copyright 2026 BadCodeZ
Licensed under the Apache License, Version 2.0
```

---

**综合教资备考平台 · 安卓端 V1.1**  
作者：BadCodeZ  
网页端仓库：https://github.com/BadCodeZ/jiazi-practice-platform  
网页端在线使用：https://badcodez.github.io/jiazi-practice-platform/