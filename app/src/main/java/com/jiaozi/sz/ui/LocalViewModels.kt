package com.jiaozi.sz.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * 活动级共享 ViewModel（由 AppRoot 以 activity 作用域创建并提供）。
 *
 * 关键修复：Jetpack Navigation 中，各 composable 目的地里直接调用 viewModel()
 * 默认作用域是「当前 NavBackStackEntry」，于是每个页面都会 new 出一份独立的
 * AppViewModel / PracticeViewModel。这会导致：设置页保存的目标日 / 主题 / AI Key
 * 只写进「设置页那一份」实例，而首页 / 练习页 / MainActivity 观察的是「各自的另一份」，
 * 表现就是「设置没反应、主题不实时、AI Key 调不通」。
 *
 * 统一在此提供一份 activity 级实例，所有页面共享，设置改动即时互通。
 */
val LocalAppVm = compositionLocalOf<AppViewModel> { error("LocalAppVm 未提供：必须在 AppRoot 的 CompositionLocalProvider 内使用") }
val LocalPracticeVm = compositionLocalOf<PracticeViewModel> { error("LocalPracticeVm 未提供：必须在 AppRoot 的 CompositionLocalProvider 内使用") }
