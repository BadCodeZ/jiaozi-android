package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.SectionTitle
import com.jiaozi.sz.ui.components.StatTile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.model.Knowledge
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm

/**
 * 知识库（对齐网页端 `VIEW.knowledge`）：分类 chips + 搜索 + 卡片列表。
 * 知识卡为静态种子（knowledge.json），支持按分类筛选与全文搜索；
 * 收藏/到期复习标记通过 meta 持久化（轻量，不影响题库进度）。
 * 重构（V2.60）：渐变 Hero 头部 + 统计格 + surfaceVariant 浏览卡。
 */
@Composable
fun KnowledgeScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val repo = appVm.repo
    val all = repo.knowledge

    var query by remember { mutableStateOf(TextFieldValue("")) }
    val cats = remember(all) { listOf("全部") + all.map { it.cat }.distinct() }
    var cat by remember { mutableStateOf("全部") }

    val favState by appVm.knowledgeFav.collectAsStateWithLifecycle()

    val filtered = remember(all, query.text, cat) {
        all.filter { k ->
            (cat == "全部" || k.cat == cat) &&
                (query.text.isBlank() || "${k.title} ${k.content} ${k.tags}".contains(query.text, ignoreCase = true))
        }
    }

    val favCount = favState.size
    val catCount = (cats.size - 1).coerceAtLeast(0)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroHeader(title = "知识库", subtitle = "先学知识，再练题目——备考的输入侧", icon = appPainter("book"))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("知识卡", "${all.size}", Modifier.weight(1f))
            StatTile("分类", "$catCount", Modifier.weight(1f))
            StatTile("已收藏", "$favCount", Modifier.weight(1f))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(appPainter("search"), contentDescription = null) },
            placeholder = { Text("搜索知识卡…") },
            textStyle = MaterialTheme.typography.bodyMedium
        )

        // 分类 chips（横向滚动，避免嵌套滚动）
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cats.forEach { c ->
                FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) })
            }
        }

        SectionTitle("知识卡")
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()
        ) {
            if (all.isEmpty()) {
                item { EmptyHint("book", "知识库为空", "导入或收藏知识卡，备考随手查。") }
            } else if (filtered.isEmpty()) {
                item { Text("没有匹配的知识卡。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
            } else {
                items(filtered, contentType = { "knowledge" }) { k -> KnowledgeBrowseCard(k, favState.contains(k.id)) { appVm.toggleKnowledgeFav(k.id) } }
            }
        }
    }
}

@Composable
private fun KnowledgeBrowseCard(k: Knowledge, fav: Boolean, onFav: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(k.cat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onFav) {
                    Icon(
                        if (fav) appPainter("star") else appPainter("star"),
                        contentDescription = "收藏",
                        tint = if (fav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
            }
            Text(k.title, style = MaterialTheme.typography.titleMedium)
            Text(k.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (k.tags.isNotBlank()) Text("标签：${k.tags}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
