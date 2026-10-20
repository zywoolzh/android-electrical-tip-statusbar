package com.zywoolzh.electricaltip

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Removes any remaining v1 notification after an in-place upgrade.
        getSystemService(NotificationManager::class.java).cancel(1001)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF365E43), onPrimary = Color.White,
                primaryContainer = Color(0xFFE1EADB), surface = Color(0xFFFAF8F2),
                background = Color(0xFFFAF8F2), onSurface = Color(0xFF223A2D),
            )) { KnowledgeApp(intent.getStringExtra("tip_id")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeApp(initialId: String?) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tips by remember { mutableStateOf<List<KnowledgeTip>>(emptyList()) }
    var current by remember { mutableStateOf<KnowledgeTip?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf(initialId) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTip by remember { mutableStateOf<KnowledgeTip?>(null) }
    var pendingImport by remember { mutableStateOf<List<KnowledgeTip>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var rotation by remember { mutableStateOf(prefs.rotationEnabled) }
    var interval by rememberSaveable { mutableStateOf(prefs.intervalValue.toString()) }
    var unit by remember { mutableStateOf(prefs.intervalUnit) }
    var category by remember { mutableStateOf(prefs.category) }

    suspend fun refresh() {
        val loaded = withContext(Dispatchers.IO) {
            val all = KnowledgeStore(context).use { it.all() }
            val selected = KnowledgeRepository.current(context)
            KnowledgeWidget.updateAll(context)
            all to selected
        }
        tips = loaded.first
        current = loaded.second
        initialized = true
    }
    fun perform(message: String = "", onSuccess: () -> Unit = {}, task: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { task() }
                refresh()
                onSuccess()
                if (message.isNotBlank()) scope.launch { snackbar.showSnackbar(message) }
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar("操作未完成：${e.message ?: "请重试"}") }
            } finally { busy = false }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // A file-picker result can arrive immediately after ON_RESUME. Refresh must
        // not mark the UI busy and accidentally discard that import/export result.
        scope.launch {
            try {
                refresh()
                withContext(Dispatchers.IO) { RotationScheduler.schedule(context) }
            } catch (e: Exception) { snackbar.showSnackbar("载入未完成：${e.message}") }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    pendingImport = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                require(output.size() + count <= KnowledgeCsv.MAX_BYTES) { "文件不能超过 2 MB" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: error("无法读取文件")
                        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString()
                        KnowledgeCsv.decode(text)
                    }
                } catch (e: Exception) {
                    scope.launch { snackbar.showSnackbar("导入未完成，请使用 UTF-8 CSV：${e.message}") }
                } finally { busy = false }
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) perform("知识库已导出，可用 Excel 编辑后重新导入") {
            val text = KnowledgeCsv.encode(KnowledgeStore(context).use { it.all() })
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("无法写入文件")
        }
    }
    fun pinWidget() {
        val manager = AppWidgetManager.getInstance(context)
        val accepted = manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(
            ComponentName(context, KnowledgeWidget::class.java), null, null,
        )
        scope.launch {
            snackbar.showSnackbar(if (accepted) "请在系统弹窗中确认添加" else "请长按桌面空白处 → 小组件 → 电气知识大卡片")
        }
    }

    BackHandler(editorId != null || detailId != null) {
        if (editorId != null) editorId = null else detailId = null
    }

    Scaffold(
        topBar = { TopAppBar(title = {
            Column {
                Text("电气笔记", fontWeight = FontWeight.Bold)
                Text("把知识放在桌面上", style = MaterialTheme.typography.labelMedium)
            }
        }, actions = {
            if (editorId != null || detailId != null) TextButton(onClick = {
                if (editorId != null) editorId = null else detailId = null
            }) { Text("返回") }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (editorId == null && detailId == null) NavigationBar {
                listOf("卡片", "知识库", "设置").forEachIndexed { index, name ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Text(listOf("▤", "▦", "◷")[index], fontSize = 24.sp) }, label = { Text(name) })
                }
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                editorId != null -> KnowledgeEditor(
                    id = editorId!!, original = tips.find { it.id == editorId }, busy = busy,
                    onSave = { tip ->
                        perform("已保存，桌面卡片已更新", onSuccess = { editorId = null; detailId = tip.id }) {
                            KnowledgeStore(context).use { it.save(tip) }
                            prefs.clearPages()
                        }
                    },
                )
                detailId != null -> {
                    val tip = tips.find { it.id == detailId }
                    if (tip == null) Text(if (initialized) "此知识已不存在，请返回知识库" else "正在载入…", Modifier.padding(24.dp))
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Text(tip.category, color = MaterialTheme.colorScheme.primary) }
                        item { Text(tip.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                        item { SelectionContainer { Text(tip.body, fontSize = 17.sp, lineHeight = 29.sp) } }
                        item { Text("来源：${tip.source.ifBlank { "未填写" }}", style = MaterialTheme.typography.bodySmall) }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = { editorId = tip.id }) { Text("编辑") }
                            OutlinedButton(enabled = !busy, onClick = { perform { KnowledgeStore(context).use { it.toggleFavorite(tip.id) } } }) {
                                Text(if (tip.favorite) "取消收藏" else "收藏")
                            }
                        } }
                    }
                }
                tab == 0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("今日一读", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0EBDF))) {
                            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("电气笔记 / ${current?.category ?: "知识库"}", color = MaterialTheme.colorScheme.primary)
                                Text(current?.title ?: "这里将展示你的知识", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(current?.summary ?: if (initialized) "当前分类没有内容。请在知识库添加，或到设置切换分类。" else "正在载入…", fontSize = 16.sp, lineHeight = 26.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = current != null, onClick = { detailId = current?.id }) { Text("阅读全文 →") }
                                    TextButton(enabled = !busy && current != null, onClick = { perform { current?.let { tip -> KnowledgeStore(context).use { it.toggleFavorite(tip.id) } } } }) { Text(if (current?.favorite == true) "★ 已收藏" else "☆ 收藏") }
                                }
                            }
                        }
                    }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(enabled = !busy && current != null, modifier = Modifier.weight(1f), onClick = { perform { KnowledgeRepository.move(context, -1) } }) { Text("上一条") }
                        Button(enabled = !busy && current != null, modifier = Modifier.weight(1f), onClick = { perform { KnowledgeRepository.move(context, 1) } }) { Text("下一条") }
                    } }
                    item { Button(onClick = ::pinWidget, modifier = Modifier.fillMaxWidth()) { Text("添加桌面大卡片") } }
                    item { Text("也可长按桌面空白处 → 小组件 → 电气知识大卡片。长按卡片拖动边框可调整大小；卡片内可翻页读摘要，点标题看全文。", style = MaterialTheme.typography.bodyMedium) }
                    item { Text("${tips.size} 条知识 · 离线可读 · ${if (rotation) "每 ${prefs.intervalValue} ${if (prefs.intervalUnit == IntervalUnit.HOURS) "小时" else "天"}轮换" else "轮换已暂停"}", style = MaterialTheme.typography.labelMedium) }
                }
                tab == 1 -> {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(query, { query = it }, label = { Text("搜索标题、分类或正文") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = { editorId = UUID.randomUUID().toString() }) { Text("新增") }
                            OutlinedButton(enabled = !busy, onClick = { importer.launch(arrayOf("*/*")) }) { Text("导入 CSV") }
                            OutlinedButton(enabled = !busy, onClick = { exporter.launch("电气知识库.csv") }) { Text("导出") }
                        }
                        FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("只看收藏") })
                    }
                    val filtered = tips.filter {
                        (!favoritesOnly || it.favorite) && (query.isBlank() || "${it.title} ${it.category} ${it.body}".contains(query, ignoreCase = true))
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("${filtered.size} 条知识", style = MaterialTheme.typography.labelLarge) }
                        if (filtered.isEmpty()) item { Text("没有匹配内容，可新增知识或调整搜索条件。") }
                        items(filtered, key = { it.id }) { tip ->
                            OutlinedCard(onClick = { detailId = tip.id }) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${tip.category}${if (tip.favorite) " · ★" else ""}", style = MaterialTheme.typography.labelMedium)
                                    Text(tip.title, fontWeight = FontWeight.Bold)
                                    Text(tip.summary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Row {
                                        TextButton(enabled = !busy, onClick = { editorId = tip.id }) { Text("编辑") }
                                        TextButton(enabled = !busy, onClick = { perform("已显示在桌面卡片") {
                                            prefs.currentId = tip.id
                                            if (prefs.category.isNotEmpty() && prefs.category != tip.category) prefs.category = tip.category
                                            prefs.clearPages()
                                        }; category = if (category.isNotEmpty() && category != tip.category) tip.category else category }) { Text("放到桌面") }
                                        TextButton(enabled = !busy, onClick = { deleteTip = tip }) { Text("删除") }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("桌面轮换", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("自动轮换", modifier = Modifier.padding(top = 12.dp))
                        Switch(rotation, enabled = !busy, onCheckedChange = { enabled ->
                            rotation = enabled
                            perform { prefs.rotationEnabled = enabled; RotationScheduler.schedule(context, restart = true) }
                        })
                    }
                    OutlinedTextField(interval, { if (it.length <= 3 && it.all(Char::isDigit)) interval = it },
                        label = { Text("轮换间隔，1—365") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IntervalUnit.entries.forEach { option ->
                            FilterChip(unit == option, onClick = { unit = option }, label = { Text(if (option == IntervalUnit.HOURS) "小时" else "天") })
                        }
                    }
                    Button(enabled = !busy && interval.toIntOrNull() in 1..365, onClick = { perform("轮换间隔已保存，从现在重新计时") {
                        prefs.intervalValue = interval.toInt(); prefs.intervalUnit = unit
                        RotationScheduler.schedule(context, restart = true)
                    } }) { Text("保存间隔") }
                    Text("轮换分类", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("") + tips.map { it.category }.distinct() + listOfNotNull(category.takeIf { it.isNotEmpty() })).distinct().forEach { value ->
                            FilterChip(category == value, enabled = !busy, onClick = {
                                category = value
                                perform { prefs.category = value; prefs.clearPages() }
                            }, label = { Text(value.ifEmpty { "全部" }) })
                        }
                    }
                    Text("省电模式或熄屏时，自动更新可能延迟。暂停轮换后会保留当前卡片。多个桌面卡片共享当前知识和分类，分页位置各自独立。")
                    HorizontalDivider()
                    Text("自己的知识库", style = MaterialTheme.typography.titleLarge)
                    Text("在知识库点“新增”即可编辑。批量维护时，先导出 CSV，用 Excel 打开编辑，再保存为 CSV UTF-8 后导入。相同编号更新内容，新编号添加内容；留空编号会创建新条目。")
                    Text("必填列：category（分类）、title（标题）、summary（卡片摘要）、body（全文）。可选：id（编号）、source（来源）、favorite（收藏，0/1）。支持中文表头。每次最多 2000 条、2 MB。")
                    Text("知识保存在本机。更换手机或卸载前请导出备份；此版本不需要账户或云端数据库。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    deleteTip?.let { tip -> AlertDialog(onDismissRequest = { deleteTip = null },
        title = { Text("删除这条知识？") }, text = { Text("${tip.title}\n删除后将从知识库和轮换列表中移除。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            deleteTip = null
            perform("已删除") { KnowledgeStore(context).use { it.delete(tip.id) }; prefs.clearPages() }
        }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteTip = null }) { Text("取消") } }) }
    pendingImport?.let { imported ->
        val existing = tips.map { it.id }.toSet()
        AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("确认导入知识库") },
            text = { Text("新增 ${imported.count { it.id !in existing }} 条，更新 ${imported.count { it.id in existing }} 条。\n\n同编号条目的文字会更新，已有收藏状态保留。其他条目不会删除。\n\n预览：${imported.take(3).joinToString("；") { it.title }}") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                pendingImport = null
                perform("已导入 ${imported.size} 条知识") { KnowledgeStore(context).use { it.importTips(imported) }; prefs.clearPages() }
            }) { Text("确认导入") } }, dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } })
    }
}

@Composable
private fun KnowledgeEditor(id: String, original: KnowledgeTip?, busy: Boolean, onSave: (KnowledgeTip) -> Unit) {
    var title by rememberSaveable(id) { mutableStateOf(original?.title.orEmpty()) }
    var category by rememberSaveable(id) { mutableStateOf(original?.category ?: "自定义") }
    var summary by rememberSaveable(id) { mutableStateOf(original?.summary.orEmpty()) }
    var body by rememberSaveable(id) { mutableStateOf(original?.body.orEmpty()) }
    var source by rememberSaveable(id) { mutableStateOf(original?.source.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (original == null) "新增知识" else "编辑知识", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { title = it }, label = { Text("标题（必填）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(category, { category = it }, label = { Text("分类（必填，可自定义）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(summary, { summary = it }, label = { Text("桌面卡片摘要（必填）") }, minLines = 5, modifier = Modifier.fillMaxWidth())
        Text("建议 150—250 字。长摘要会自动分页。", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(body, { body = it }, label = { Text("详细正文（必填）") }, minLines = 7, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(source, { source = it }, label = { Text("来源 / 标准版本（可选）") }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            val tip = KnowledgeTip(id, category.trim(), title.trim(), summary.trim(), body.trim(), source.trim(), original?.favorite ?: false)
            try { tip.validate(); error = null; onSave(tip) } catch (e: IllegalArgumentException) { error = e.message }
        }) { Text("保存知识") }
        Text("编号：$id\n导出后保留这个编号，即可通过 CSV 更新同一条知识。", style = MaterialTheme.typography.bodySmall)
    }
}
