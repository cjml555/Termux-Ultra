package com.termux.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.termux.R
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * Termux 内部文本编辑器主界面。
 *
 * @param initialContent 初始文本
 * @param filePath 文件路径（null=新建）
 * @param readOnly 只读模式
 * @param onSave 保存回调，返回 true 表示成功
 * @param onClose 关闭回调
 */
@Composable
fun TextEditorScreen(
    initialContent: String,
    filePath: String?,
    readOnly: Boolean = false,
    onSave: (path: String, content: String) -> Boolean,
    onClose: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    // 本页在 MainScreen 取景层之外，自建一层供玻璃顶栏折射页面内容
    val glassPage = rememberGlassPageBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    var currentFilePath by remember { mutableStateOf(filePath) }
    val file = remember(currentFilePath) { currentFilePath?.let { File(it) } }
    val lang = remember(file) { SyntaxHighlighter.detectLanguage(file) }
    var content by remember { mutableStateOf(initialContent) }
    var modified by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showConfirmExit by remember { mutableStateOf(false) }

    // 保存位置选择器状态
    var showDirPicker by remember { mutableStateOf(false) }
    var showFileNameDialog by remember { mutableStateOf(false) }
    var pickedDir by remember { mutableStateOf<String?>(null) }
    var fileNameInput by remember { mutableStateOf("") }

    val fileName = file?.name ?: stringResource(R.string.editor_unsaved_name)
    val fileInfo = SyntaxHighlighter.fileInfo(file)
    val perms = SyntaxHighlighter.permissions(file)

    fun doSave() {
        val path = currentFilePath
        if (path != null) {
            val ok = onSave(path, content)
            if (ok) modified = false
        } else {
            // 新建文件 → 先选目录
            showDirPicker = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GlassTopAppBar(
                title = fileName,
                backdrop = glassPage.backdrop,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GlassIconButton(onClick = {
                        if (modified && !readOnly) showConfirmExit = true
                        else onClose()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    if (!readOnly) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(onClick = { doSave() }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_save),
                                    contentDescription = stringResource(R.string.save),
                                    modifier = Modifier.size(22.dp),
                                    tint = MiuixTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(onClick = { showPermissionDialog = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_terminal),
                                    contentDescription = stringResource(R.string.file_info_permissions),
                                    modifier = Modifier.size(22.dp),
                                    tint = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            )
        },

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .then(glassPage.contentModifier)
                .fillMaxSize()
                .padding(pagePaddingWithoutTop(innerPadding))
                .verticalScroll(rememberScrollState())
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            // 这页是 Column + verticalScroll，让位落在首个 item 上；滚动页才把它折进 contentPadding。
            Spacer(Modifier.height(topBarClearance(innerPadding)))

            // 文件信息条
            if (file != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val infoParts = fileInfo.split(" · ")
                    // 第一行：语言 · 扩展名 · 大小 · 权限 + 保存状态
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        infoParts.getOrNull(0)?.let { ext ->
                            Text(
                                text = "· $ext",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        infoParts.getOrNull(1)?.let { size ->
                            Text(
                                text = "· $size",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (perms.isNotBlank()) {
                            Text(
                                text = "· [$perms]",
                                fontSize = 12.sp,
                                color = if (perms.contains('w')) Color(0xFFFF9F0A) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (modified) stringResource(R.string.editor_unsaved) else stringResource(R.string.editor_saved),
                            fontSize = 11.sp,
                            color = if (modified) Color(0xFFFF9F0A) else MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    // 第二行：修改时间
                    infoParts.getOrNull(2)?.let { date ->
                        Text(
                            text = stringResource(R.string.editor_modified_at, date),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 编辑器主体
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(
                        if (isDark) Color(0xFF0A0A0C) else Color(0xFFFFFFFF)
                    )
                    .padding(8.dp)
            ) {
                val textFieldState = rememberTextFieldState(content)
                LaunchedEffect(textFieldState) {
                    snapshotFlow { textFieldState.text.toString() }.collectLatest { newText ->
                        if (newText != content) {
                            content = newText
                            modified = true
                        }
                    }
                }
                val lineCount = if (content.isEmpty()) 1 else content.count { it == '\n' } + 1
                Text(
                    text = (1..lineCount).joinToString("\n"),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(end = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(start = 40.dp)
                ) {
                    if (textFieldState.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.editor_placeholder),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    BasicTextField(
                        state = textFieldState,
                        readOnly = readOnly,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E),
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }
        }

        if (showConfirmExit) {
            OverlayDialog(
                show = showConfirmExit,
                onDismissRequest = { showConfirmExit = false },
                title = stringResource(R.string.unsaved_changes),
                summary = stringResource(R.string.editor_unsaved_confirm),
                content = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { showConfirmExit = false },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = stringResource(R.string.editor_save_and_exit),
                            onClick = {
                                doSave()
                                showConfirmExit = false
                                onClose()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            )
        }

        if (showPermissionDialog && file != null) {
            FilePermissionDialog(
                file = file,
                onDismiss = { showPermissionDialog = false }
            )
        }

        // 保存位置选择器（新建文件时）
        TermuxInternalFilePicker(
            show = showDirPicker,
            title = stringResource(R.string.editor_select_dir),
            allowFolders = true,
            onDismiss = { showDirPicker = false },
            onFileSelected = { path ->
                pickedDir = path
                fileNameInput = "untitled"
                showDirPicker = false
                showFileNameDialog = true
            }
        )

        if (showFileNameDialog && pickedDir != null) {
            OverlayDialog(
                show = true,
                onDismissRequest = { showFileNameDialog = false },
                title = stringResource(R.string.editor_save_file),
                summary = stringResource(R.string.editor_save_to),
                content = {
                    androidx.compose.material3.OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it },
                        label = { Text(stringResource(R.string.fm_file_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { showFileNameDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = stringResource(R.string.save),
                            onClick = {
                                if (fileNameInput.isNotBlank()) {
                                    val targetPath = File(pickedDir, fileNameInput).absolutePath
                                    val ok = onSave(targetPath, content)
                                    if (ok) {
                                        currentFilePath = targetPath
                                        modified = false
                                    }
                                }
                                showFileNameDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            )
        }
    }
}



@Composable
private fun FilePermissionDialog(file: File, onDismiss: () -> Unit) {
    var readable by remember { mutableStateOf(file.canRead()) }
    var writable by remember { mutableStateOf(file.canWrite()) }
    var executable by remember { mutableStateOf(file.canExecute()) }

    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.editor_permissions),
        summary = file.absolutePath,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PermissionRow(stringResource(R.string.editor_readable), readable) { readable = it }
                PermissionRow(stringResource(R.string.editor_writable), writable) { writable = it }
                PermissionRow(stringResource(R.string.editor_executable), executable) { executable = it }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.app_settings),
                        onClick = {
                            try {
                                file.setReadable(readable)
                                file.setWritable(writable)
                                file.setExecutable(executable)
                            } catch (_: Exception) {}
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

@Composable
private fun PermissionRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle
        )
    }
}
