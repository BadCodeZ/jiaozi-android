package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.AppColors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.LessonEntity
import com.jiaozi.sz.data.model.LessonFields
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.screens.lesson.LessonEditor
import com.jiaozi.sz.ui.screens.lesson.LessonHub
import com.jiaozi.sz.ui.screens.lesson.LessonTemplateLibrary

/**
 * 备课模块（对标网页端「备课中枢 + 十二要素结构化编辑器 + 模板库」）。
 * 内部视图状态机：hub（中枢）/ edit（结构化编辑器）/ templates（模板库）。
 *
 * 拆分说明（09-13）：原 557 行上帝对象拆为 5 文件：
 * - LessonScreen.kt（本文件，顶层状态机）
 * - lesson/LessonHub.kt（中枢页）
 * - lesson/LessonEditor.kt（十二要素编辑器）
 * - lesson/LessonTemplateLibrary.kt（模板库）
 * - lesson/LessonWidgets.kt（SegmentedRow/ZoneCard/LessonField 通用组件）
 */
@Composable
fun LessonScreen(nav: NavHostController) {
    var view by remember { mutableStateOf("hub") }
    // 编辑器目标：null = 新建；非 null = 编辑既有
    var editTarget by remember { mutableStateOf<LessonEntity?>(null) }
    // 模板库应用：非空表示用模板预填后进入编辑
    var tplSeed by remember { mutableStateOf<LessonFields?>(null) }

    when (view) {
        "edit" -> LessonEditor(
            appVm = LocalAppVm.current,
            target = editTarget,
            seed = tplSeed,
            onBack = { view = "hub"; editTarget = null; tplSeed = null },
            onSaved = { view = "hub"; editTarget = null; tplSeed = null }
        )
        "templates" -> LessonTemplateLibrary(
            appVm = LocalAppVm.current,
            onBack = { view = "hub" },
            onUse = { seed -> tplSeed = seed; editTarget = null; view = "edit" }
        )
        else -> LessonHub(
            appVm = LocalAppVm.current,
            onBack = { nav.navigateUp() },
            onNew = { editTarget = null; tplSeed = null; view = "edit" },
            onEdit = { l -> editTarget = l; tplSeed = null; view = "edit" },
            onTemplates = { view = "templates" },
            onCurric = { nav.navigate("curric") },
            onBooks = { nav.navigate("books") }
        )
    }
}
