package com.termux.app.plugin

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.termux.R
import com.termux.app.compose.AiLocalModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 插件分享与导出。
 *
 * .tup 就是改了后缀的 ZIP，这里把已安装插件目录重新打包回 .tup 供分享，
 * 因为卸载会直接删除磁盘文件，用户想把自己装的插件转给别人时没有别的途径。
 */
object PluginShare {

    /**
     * Texto localizado del recurso [res] usando el context que AiLocalModel.init()
     * guarda desde la Activity. Evita propagar un Context por las firmas de sharing.
     */
    private fun str(@StringRes res: Int, vararg args: Any): String {
        val c = AiLocalModel.context() ?: return "-"
        return if (args.isEmpty()) c.getString(res) else c.getString(res, *args)
    }

    /** 分享插件元信息（纯文本） */
    fun shareMeta(context: Context, plugin: InstalledPlugin) {
        val text = buildMetaText(plugin)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, plugin.manifest.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, str(R.string.plugin_share_meta)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 把插件目录重新打包成 .tup 并分享出去 */
    fun sharePackage(context: Context, plugin: InstalledPlugin): Result<File> {
        return runCatching {
            val file = exportPackage(context, plugin).getOrThrow()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, buildMetaText(plugin))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, str(R.string.plugin_share_package)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            file
        }
    }

    /** 导出插件为 .tup 文件（放到 cacheDir/exports 下） */
    fun exportPackage(context: Context, plugin: InstalledPlugin): Result<File> {
        return runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeId = plugin.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val file = File(dir, "$safeId-${plugin.manifest.version}.tup")
            zipDirectory(PluginLoader.getPluginDir(context, plugin.id), file)
            file
        }
    }

    fun buildMetaText(plugin: InstalledPlugin): String {
        val m = plugin.manifest
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(plugin.installedAt))
        val sep = str(R.string.plugin_meta_list_sep)
        return buildString {
            appendLine(str(R.string.plugin_meta_name, m.name))
            appendLine(str(R.string.plugin_meta_version, m.version))
            appendLine(str(R.string.plugin_meta_author, m.author.ifBlank { str(R.string.plugin_author_unknown) }))
            appendLine(str(R.string.plugin_meta_id, m.id))
            if (m.description.isNotBlank()) {
                appendLine()
                appendLine(m.description)
            }
            val permissions = m.getParsedPermissions()
            if (permissions.isNotEmpty()) {
                appendLine()
                appendLine(str(R.string.plugin_meta_permissions, permissions.joinToString(sep) { it.name }))
            }
            val abilities = mutableListOf<String>()
            if (!m.entryPoints?.resourceCards.isNullOrEmpty()) abilities.add(str(R.string.plugin_ability_resource_cards))
            if (!m.entryPoints?.agentSkills.isNullOrEmpty()) abilities.add(str(R.string.plugin_skill_card))
            if (m.entryPoints?.h5Home?.enabled == true) abilities.add(str(R.string.plugin_h5_home))
            if (m.systemPrompt != null) abilities.add("System Prompt")
            if (abilities.isNotEmpty()) {
                appendLine(str(R.string.plugin_meta_abilities, abilities.joinToString(sep)))
            }
            appendLine(str(R.string.plugin_meta_installed_at, date))
        }
    }

    /** 读取插件声明的图标，读取失败时返回 null 由调用方兜底 */
    fun loadIcon(context: Context, plugin: InstalledPlugin): ImageBitmap? {
        val iconPath = plugin.manifest.icon?.takeIf { it.isNotBlank() } ?: return null
        val file = runCatching { PluginLoader.getPluginFile(context, plugin.id, iconPath) }.getOrNull()
            ?: return null
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    }

    private fun zipDirectory(sourceDir: File, outFile: File) {
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isDirectory || !file.exists()) return@forEach
                val name = file.relativeTo(sourceDir).path.replace(File.separatorChar, '/')
                zos.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}
