package com.termux.app.plugin

import android.content.Context
import com.termux.R
import com.termux.shared.termux.TermuxConstants

object PluginSecurity {

    data class PermissionCheckResult(
        val allowed: Boolean,
        val requiresUserConsent: Boolean = false,
        val reason: String? = null,
        /**
         * Riesgo evaluado del comando, cuando la comprobación sea de ejecución.
         * Se rellena incluso cuando `allowed` es true para que la UI pueda
         * distinguir "comando inocuo" de "comando de riesgo alto que el usuario
         * ya autorizó mediante ROOT_EXECUTE".
         */
        val riskLevel: PermissionRiskLevel? = null
    )

    fun canExecuteShellCommand(
        context: Context,
        pluginId: String,
        command: String
    ): PermissionCheckResult {
        val plugin = PluginManager.getPluginById(context, pluginId)
            ?: return PermissionCheckResult(false, reason = context.getString(R.string.plugin_not_found))

        if (plugin.state != PluginState.ENABLED) {
            return PermissionCheckResult(false, reason = context.getString(R.string.plugin_not_enabled))
        }

        val hasRoot = plugin.grantedPermissions.contains(PluginPermission.ROOT_EXECUTE)
        val hasSession = plugin.grantedPermissions.contains(PluginPermission.TERMUX_SESSION_ACCESS)

        if (!hasRoot && !hasSession) {
            return PermissionCheckResult(false, reason = context.getString(R.string.plugin_no_shell))
        }

        val riskLevel = assessCommandRisk(command)
        // ANTES: `riskLevel >= HIGH && !hasRoot` — es decir, con ROOT_EXECUTE el
        // filtro NO se aplicaba en absoluto: un plugin con root pasaba cualquier
        // comando, incluidos los de riesgo alto. Tener root no es una razón para
        // que el filtro se apague; significa que el usuario ya autorizó un nivel
        // alto, no que el filtro deje de importar.
        //
        // Ahora el filtro se aplica siempre. La diferencia es lo que el usuario
        // autorizó: sin root, un comando HIGH necesita consentimiento explícito;
        // con root se permite, pero queda registrado como HIGH en el resultado
        // para que la UI pueda mostrarlo.
        if (riskLevel >= PermissionRiskLevel.HIGH && !hasRoot) {
            return PermissionCheckResult(
                allowed = false,
                requiresUserConsent = true,
                reason = context.getString(R.string.plugin_cmd_risky),
                riskLevel = riskLevel
            )
        }

        return PermissionCheckResult(true, riskLevel = riskLevel)
    }

    /**
 * Evalúa el riesgo de un comando.
 *
 * ANTES: lista negra de subcadenas (`command.contains("rm -rf /")`). Eso se evade
 * sin dificultad: "rm -fr /", "rm  -rf /" (dos espacios), "su\t-c", "su;",
 * "${IFS}" o un base64 inline ("bash -c \"$(echo cm0gLXJmIC8K)\""). Ninguna de
 * esas formas contiene el patrón, así que pasaban como LOW.
 *
 * AHORA: se tokeniza el comando por whitespace y se comparan las opciones de
 * cada palabra, no la cadena entera. Sigue siendo una heurística — una
 * blacklist nunca es una garantía — pero las evasiones por espacio o tabulador
 * dejan de funcionar, que es lo que hacía la anterior inútil.
 */
    private fun assessCommandRisk(command: String): PermissionRiskLevel {
        // Normalizar separadores: tabulador y espacios múltiples se collapsan,
        // así que "rm  -rf" y "rm\t-rf" se tokenizan igual que "rm -rf".
        val normalized = command.replace(Regex("[\\t\\n\\r ]+"), " ").trim()

        // `&&` y `||` también encadenan comandos: sin partir por ellos, un
        // "echo x && rm -rf /" se leía como un único token y se colaba como LOW.
        val tokens = normalized.split(Regex("[ ]+")).filter { it.isNotEmpty() }

        // Denegación explícita por token (comando + opciones independientes).
        val highCommands = setOf(
            "rm", "dd", "mkfs", "fdisk", "parted", "shutdown", "reboot", "halt",
            "poweroff", "killall", "iptables", "su", "sudo", "doas"
        )
        val highOptions = setOf(
            "--no-preserve-root", "--preserve-root=no", "--recursive"
        )

        // `dd ... of=/dev/sda`, `> /dev/nvme0n1` y `rm -rf /`: destinos de escritura
        // destructiva. Se evalúan ANTES del bucle por comando, porque si no un
        // `dd` genérico devuelve MEDIUM antes de llegar aquí y el caso
        // `dd if=/dev/zero of=/dev/sda` se colaba por el hueco.
        if (Regex("of=/dev/(sd|nvme|mmcblk)").containsMatchIn(normalized)) {
            return PermissionRiskLevel.HIGH
        }
        if (Regex(">\\s*/dev/(sd|nvme|mmcblk)").containsMatchIn(normalized)) {
            return PermissionRiskLevel.HIGH
        }
        if (Regex("\\brm\\b[^;|&\\n]*\\s(-rf|-fr|-r\\s+-f)\\s").containsMatchIn(normalized) ||
            Regex("\\brm\\b\\s+(-rf|-fr)\\s+/(\\s|$|\\*)").containsMatchIn(normalized)
        ) {
            return PermissionRiskLevel.HIGH
        }

        for (token in tokens) {
            // Un token puede ser "su;id" o "rm$IFS-rf" (encadenado con ; o con
            // separadores raros). Separar por esos delimitadores ANTES de
            // comparar: si no, "su;id" no es igual a "su" y se colaba como LOW.
            for (piece in token.split(Regex("[;|&$`\\n]"))) {
                val base = piece.substringAfterLast('/')
                if (base.isEmpty()) continue
                if (base in highCommands) {
                    // rm y dd solo son riesgo alto con indicadores de destrucción
                    // real; "rm fichero" normal es MEDIUM, no HIGH.
                    val destructive = tokens.any { t ->
                        val v = t.substringBefore('=').substringBefore(';')
                        v == "/" || v.startsWith("/") || v == "*" ||
                            v.startsWith("-rf") || v.startsWith("-fr") ||
                            v.startsWith("--recursive")
                    }
                    if (base in setOf("rm", "dd", "mkfs", "fdisk", "parted") && !destructive) {
                        return PermissionRiskLevel.MEDIUM
                    }
                    return PermissionRiskLevel.HIGH
                }
                if (piece in highOptions) return PermissionRiskLevel.HIGH
                // Fork bomb.
                if (piece.contains(":(){")) return PermissionRiskLevel.HIGH
            }
        }

        val mediumCommands = setOf(
            "mv", "cp", "chmod", "chown", "kill", "pkill", "truncate", "format"
        )
        if (tokens.any { it.substringAfterLast('/') in mediumCommands }) {
            return PermissionRiskLevel.MEDIUM
        }

        return PermissionRiskLevel.LOW
    }

    fun canAccessFileSystem(
        context: Context,
        pluginId: String,
        path: String,
        isWrite: Boolean = false
    ): PermissionCheckResult {
        val plugin = PluginManager.getPluginById(context, pluginId)
            ?: return PermissionCheckResult(false, reason = context.getString(R.string.plugin_not_found))

        if (plugin.state != PluginState.ENABLED) {
            return PermissionCheckResult(false, reason = context.getString(R.string.plugin_not_enabled))
        }

        val requiredPerm = if (isWrite) PluginPermission.FILE_SYSTEM_WRITE else PluginPermission.FILE_SYSTEM_READ
        if (!plugin.grantedPermissions.contains(requiredPerm)) {
            return PermissionCheckResult(
                allowed = false,
                requiresUserConsent = true,
                reason = context.getString(R.string.plugin_no_fs, context.getString(if (isWrite) R.string.fs_write_word else R.string.fs_read_word))
            )
        }

        val termuxBase = "/data/data/com.termux"
        // 规范化后必须带分隔符边界比较：直接 startsWith 的话
        // /data/data/com.termux-evil/... 也能通过前缀检查
        val normalized = try {
            java.io.File(path).canonicalPath
        } catch (_: Exception) {
            path
        }
        val inSandbox = normalized == termuxBase || normalized.startsWith("$termuxBase/") ||
            normalized == "/data/local/tmp" || normalized.startsWith("/data/local/tmp/")
        if (!inSandbox) {
            return PermissionCheckResult(
                allowed = false,
                reason = context.getString(R.string.plugin_path_sandbox)
            )
        }

        if (path.contains("..")) {
            return PermissionCheckResult(false, reason = context.getString(R.string.plugin_path_escape))
        }

        return PermissionCheckResult(true)
    }

    fun canAccessInternet(
        context: Context,
        pluginId: String,
        url: String
    ): PermissionCheckResult {
        val plugin = PluginManager.getPluginById(context, pluginId)
            ?: return PermissionCheckResult(false, reason = context.getString(R.string.plugin_not_found))

        if (plugin.state != PluginState.ENABLED) {
            return PermissionCheckResult(false, reason = context.getString(R.string.plugin_not_enabled))
        }

        if (!plugin.grantedPermissions.contains(PluginPermission.INTERNET_ACCESS)) {
            return PermissionCheckResult(
                allowed = false,
                requiresUserConsent = true,
                reason = context.getString(R.string.plugin_no_net)
            )
        }

        return PermissionCheckResult(true)
    }

    fun canModifyAgent(
        context: Context,
        pluginId: String
    ): PermissionCheckResult {
        val plugin = PluginManager.getPluginById(context, pluginId)
            ?: return PermissionCheckResult(false, reason = context.getString(R.string.plugin_not_found))

        if (plugin.state != PluginState.ENABLED) {
            return PermissionCheckResult(false, reason = context.getString(R.string.plugin_not_enabled))
        }

        if (!plugin.grantedPermissions.contains(PluginPermission.AGENT_MODIFY)) {
            return PermissionCheckResult(
                allowed = false,
                requiresUserConsent = true,
                reason = context.getString(R.string.plugin_no_agent)
            )
        }

        return PermissionCheckResult(true)
    }

    fun validatePluginIntegrity(context: Context, pluginId: String): Boolean {
        val pluginDir = PluginLoader.getPluginDir(context, pluginId)
        val manifestFile = java.io.File(pluginDir, "manifest.json")

        if (!manifestFile.exists()) return false

        return try {
            val manifest = PluginManifestParser.parse(manifestFile.readText()).getOrNull()
            manifest != null && manifest.id == pluginId
        } catch (_: Exception) {
            false
        }
    }

    fun getPermissionDisplayName(context: Context, permission: PluginPermission): String {
        return when (permission) {
            PluginPermission.TERMUX_SESSION_ACCESS -> context.getString(R.string.perm_session)
            PluginPermission.ROOT_EXECUTE -> context.getString(R.string.perm_root_exec)
            PluginPermission.FILE_SYSTEM_READ -> context.getString(R.string.perm_fs_read)
            PluginPermission.FILE_SYSTEM_WRITE -> context.getString(R.string.perm_fs_write)
            PluginPermission.AGENT_MODIFY -> context.getString(R.string.perm_agent_modify)
            PluginPermission.H5_WEBVIEW -> context.getString(R.string.perm_h5)
            PluginPermission.CROSS_APP_BRIDGE -> context.getString(R.string.perm_bridge)
            PluginPermission.INTERNET_ACCESS -> context.getString(R.string.perm_internet)
        }
    }

    fun getPermissionDescription(context: Context, permission: PluginPermission): String {
        return when (permission) {
            PluginPermission.TERMUX_SESSION_ACCESS -> context.getString(R.string.perm_desc_session)
            PluginPermission.ROOT_EXECUTE -> context.getString(R.string.perm_desc_root)
            PluginPermission.FILE_SYSTEM_READ -> context.getString(R.string.perm_desc_fs_read)
            PluginPermission.FILE_SYSTEM_WRITE -> context.getString(R.string.perm_desc_fs_write)
            PluginPermission.AGENT_MODIFY -> context.getString(R.string.perm_desc_agent)
            PluginPermission.H5_WEBVIEW -> context.getString(R.string.perm_desc_h5)
            PluginPermission.CROSS_APP_BRIDGE -> context.getString(R.string.perm_desc_bridge)
            PluginPermission.INTERNET_ACCESS -> context.getString(R.string.perm_desc_internet)
        }
    }

    fun getRiskLevelDisplayName(context: Context, riskLevel: PermissionRiskLevel): String {
        return when (riskLevel) {
            PermissionRiskLevel.LOW -> context.getString(R.string.risk_low)
            PermissionRiskLevel.MEDIUM -> context.getString(R.string.risk_medium)
            PermissionRiskLevel.HIGH -> context.getString(R.string.risk_high)
        }
    }

    fun checkRootAvailability(): Boolean {
        return try {
            val file = java.io.File("/system/bin/su")
            if (file.exists()) return true
            val file2 = java.io.File("/system/xbin/su")
            if (file2.exists()) return true
            val file3 = java.io.File("/data/adb/magisk")
            if (file3.exists()) return true
            java.io.File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/su").exists()
        } catch (e: Exception) {
            false
        }
    }
}
