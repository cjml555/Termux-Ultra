package com.termux.app.plugin

import android.content.Context
import androidx.annotation.StringRes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.termux.R
import com.termux.app.compose.AiLocalModel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object PluginLoader {

    /**
     * Los mensajes de instalacion se muestran como error en la UI deGestion de
     * plugins, asi que se resuelven con el context de la app: extractZip y
     * validatePluginContents no reciben Context.
     */
    private fun str(@StringRes res: Int, vararg args: Any): String {
        val c = AiLocalModel.context() ?: return "-"
        return if (args.isEmpty()) c.getString(res) else c.getString(res, *args)
    }

    private const val PLUGINS_DIR = "plugins"
    private const val TUP_EXTENSION = ".tup"
    private const val ZIP_EXTENSION = ".zip"
    private const val MANIFEST_FILE = "manifest.json"
    private val gson = Gson()

    fun getPluginsDir(context: Context): File {
        val dir = File(context.filesDir, PLUGINS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPluginDir(context: Context, pluginId: String): File {
        val dir = File(getPluginsDir(context), pluginId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun installPlugin(context: Context, sourceFile: File): Result<PluginManifest> {
        return try {
            val ext = sourceFile.extension.lowercase()
            val isValidExt = ext == TUP_EXTENSION.removePrefix(".") || ext == ZIP_EXTENSION.removePrefix(".")
            val isValidZip = try {
                val buf = ByteArray(2)
                sourceFile.inputStream().use { it.read(buf) }
                buf.contentEquals(byteArrayOf(0x50, 0x4B))
            } catch (_: Exception) { false }

            if (!isValidExt && !isValidZip) {
                return Result.failure(IllegalArgumentException(str(R.string.plugin_err_unsupported_format)))
            }

            val tempDir = File(context.cacheDir, "plugin_install_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                extractZip(sourceFile, tempDir)

                val manifestFile = File(tempDir, MANIFEST_FILE)
                if (!manifestFile.exists()) {
                    return Result.failure(IllegalArgumentException(str(R.string.plugin_err_manifest_missing)))
                }

                val manifestJson = manifestFile.readText()
                val manifestResult = PluginManifestParser.parse(manifestJson)

                if (manifestResult.isFailure) {
                    return Result.failure(manifestResult.exceptionOrNull() ?: Exception(str(R.string.plugin_err_manifest_parse)))
                }

                val manifest = manifestResult.getOrThrow()

                checkHostVersion(context, manifest.minHostVersion)

                val targetDir = getPluginDir(context, manifest.id)

                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
                targetDir.mkdirs()

                copyDirectoryContents(tempDir, targetDir)

                validatePluginContents(targetDir, manifest)

                saveInstallRecord(context, manifest)

                Result.success(manifest)
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractZip(sourceFile: File, targetDir: File) {
        val root = targetDir.canonicalFile
        ZipFile(sourceFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                // Zip Slip 防护：entry.name 可能带 ../ 或绝对路径，必须确认解压后仍在插件目录内
                val destFile = File(targetDir, entry.name)
                val canonical = destFile.canonicalFile
                if (canonical != root && !canonical.path.startsWith(root.path + File.separator)) {
                    throw SecurityException(str(R.string.plugin_err_zip_slip, entry.name))
                }
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun copyDirectoryContents(sourceDir: File, targetDir: File) {
        sourceDir.walkTopDown().forEach { file ->
            val relativePath = file.relativeTo(sourceDir)
            val destFile = File(targetDir, relativePath.path)
            if (file.isDirectory) {
                destFile.mkdirs()
            } else {
                destFile.parentFile?.mkdirs()
                file.copyTo(destFile, overwrite = true)
            }
        }
    }

    private fun validatePluginContents(pluginDir: File, manifest: PluginManifest) {
        manifest.getAllH5Entries().forEach { (title, entry) ->
            if (entry.isNotBlank()) {
                val entryFile = File(pluginDir, entry)
                if (!entryFile.exists()) {
                    throw IllegalStateException(
                        str(R.string.plugin_err_h5_entry_missing, entry, title)
                    )
                }
            }
        }

        // Compose DSL 页面入口文件校验（pages 子页 + compose 主页）
        manifest.entryPoints?.pages?.forEach { page ->
            val entry = page.effectiveEntry
            if (page.type == "compose" && !entry.isNullOrBlank()) {
                val entryFile = File(pluginDir, entry)
                if (!entryFile.exists()) {
                    throw IllegalStateException(str(R.string.plugin_err_compose_page_missing, entry))
                }
            }
        }
        val home = manifest.entryPoints?.h5Home
        if (home?.enabled == true && home.type == "compose") {
            val entry = home.effectiveEntry
            if (entry.isNotBlank()) {
                val homeFile = File(pluginDir, entry)
                if (!homeFile.exists()) {
                    throw IllegalStateException(str(R.string.plugin_err_compose_home_missing, entry))
                }
            }
        }
    }

    private fun checkHostVersion(context: Context, minVersion: String) {
        try {
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.2.0"
            val cleanCurrent = currentVersion.substringBeforeLast(".RB")
            val cleanMin = minVersion.substringBeforeLast(".RB")

            val minParts = cleanMin.split(".")
            val curParts = cleanCurrent.split(".")

            for (i in 0 until minParts.size.coerceAtLeast(curParts.size)) {
                val minPart = minParts.getOrNull(i)?.toIntOrNull() ?: 0
                val curPart = curParts.getOrNull(i)?.toIntOrNull() ?: 0
                if (curPart > minPart) break
                if (curPart < minPart) {
                    throw IllegalStateException(
                        str(R.string.plugin_err_host_version, minVersion, currentVersion)
                    )
                }
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
        }
    }

    private fun saveInstallRecord(context: Context, manifest: PluginManifest) {
        val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
        val key = "plugin_${manifest.id}"
        val record = InstallRecord(
            id = manifest.id,
            version = manifest.version,
            installedAt = System.currentTimeMillis(),
            state = PluginState.INSTALLED.name
        )
        prefs.edit().putString(key, gson.toJson(record)).apply()
    }

    fun loadInstalledPlugins(context: Context): List<InstalledPlugin> {
        val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
        val plugins = mutableListOf<InstalledPlugin>()

        prefs.all.forEach { (key, value) ->
            if (key.startsWith("plugin_") && value is String) {
                try {
                    val type = object : TypeToken<InstallRecord>() {}.type
                    val record = gson.fromJson<InstallRecord>(value, type)
                    val pluginDir = getPluginDir(context, record.id)
                    val manifestFile = File(pluginDir, MANIFEST_FILE)

                    if (manifestFile.exists()) {
                        val manifest = PluginManifestParser.parse(manifestFile.readText()).getOrNull()
                        // manifest 就放在插件目录里，安装后可能被改写：id 与安装目录对不上时
                        // 插件会顶着别人的身份拿权限，这里直接跳过登记。
                        if (manifest != null && PluginSecurity.validatePluginIntegrity(context, record.id)) {
                            val grantedPerms = loadGrantedPermissions(context, record.id)
                            plugins.add(
                                InstalledPlugin(
                                    id = manifest.id,
                                    manifest = manifest,
                                    state = PluginState.valueOf(record.state),
                                    grantedPermissions = grantedPerms,
                                    installPath = pluginDir.absolutePath,
                                    installedAt = record.installedAt,
                                    enabledAt = if (PluginState.valueOf(record.state) == PluginState.ENABLED) record.installedAt else null
                                )
                            )
                        }
                    }
                } catch (_: Exception) { }
            }
        }
        return plugins
    }

    fun loadPluginManifest(context: Context, pluginId: String): PluginManifest? {
        val pluginDir = getPluginDir(context, pluginId)
        val manifestFile = File(pluginDir, MANIFEST_FILE)
        if (!manifestFile.exists()) return null
        return PluginManifestParser.parse(manifestFile.readText()).getOrNull()
    }

    private fun loadGrantedPermissions(context: Context, pluginId: String): Set<PluginPermission> {
        val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
        val key = "perms_$pluginId"
        val json = prefs.getString(key, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val strings = gson.fromJson<List<String>>(json, type)
            strings.mapNotNull { s ->
                try { PluginPermission.valueOf(s) } catch (_: Exception) { null }
            }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun grantPermissions(context: Context, pluginId: String, permissions: Set<PluginPermission>) {
        val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
        val key = "perms_$pluginId"
        val names = permissions.map { it.name }
        prefs.edit().putString(key, gson.toJson(names)).apply()
    }

    fun revokeAllPermissions(context: Context, pluginId: String) {
        val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
        val key = "perms_$pluginId"
        prefs.edit().remove(key).apply()
    }

    fun setPluginState(context: Context, pluginId: String, state: PluginState) {
        val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
        val recordKey = "plugin_$pluginId"
        val json = prefs.getString(recordKey, null) ?: return
        try {
            val type = object : TypeToken<InstallRecord>() {}.type
            val record = gson.fromJson<InstallRecord>(json, type)
            val updatedRecord = record.copy(state = state.name)
            prefs.edit().putString(recordKey, gson.toJson(updatedRecord)).apply()
        } catch (_: Exception) { }
    }

    fun uninstallPlugin(context: Context, pluginId: String) {
        val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("plugin_$pluginId")
            .remove("perms_$pluginId")
            .remove("config_$pluginId")
            .apply()

        val pluginDir = getPluginDir(context, pluginId)
        if (pluginDir.exists()) {
            pluginDir.deleteRecursively()
        }
    }

    fun getPluginConfig(context: Context, pluginId: String): Map<String, Any> {
        val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
        val key = "config_$pluginId"
        val json = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun savePluginConfig(context: Context, pluginId: String, config: Map<String, Any>) {
        val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
        val key = "config_$pluginId"
        prefs.edit().putString(key, gson.toJson(config)).apply()
    }

    /**
 * Resuelve [relativePath] dentro de [pluginDir] garantizando que no se escape.
 *
 * El bug anterior era `file.canonicalPath.startsWith(pluginDir.canonicalPath)`
 * sin separador: con pluginDir = .../plugins/com.foo y relativePath =
 * "../com.foobar/manifest.json", el canónico empieza por ".../plugins/com.foo"
 * y pasaba el filtro — leyendo directorios hermanos. La comparación correcta
 * exige el separador de límite: `startsWith(dir + "/")` o igualdad exacta.
 */
    fun getPluginFile(context: Context, pluginId: String, relativePath: String): File? {
        val pluginDir = getPluginDir(context, pluginId)
        val base = pluginDir.canonicalFile
        // canonicalFile resuelve ".." y los enlaces simbólicos; una ruta
        // relativa que apunte fuera se resuelve antes de comparar.
        val file = File(base, relativePath).canonicalFile
        val within = file == base || file.path.startsWith(base.path + File.separator)
        if (!within) {
            // Intent de traversal: es un ataque o un bug, no un error de uso.
            return null
        }
        return if (file.exists()) file else null
    }

    private data class InstallRecord(
        val id: String = "",
        val version: String = "",
        val installedAt: Long = 0L,
        val state: String = "INSTALLED"
    )
}