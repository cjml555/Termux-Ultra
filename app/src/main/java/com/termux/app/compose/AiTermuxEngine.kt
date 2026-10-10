package com.termux.app.compose
import androidx.annotation.StringRes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.location.LocationManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.termux.app.TermuxService
import com.termux.app.TermuxActivity
import com.termux.app.activities.QemuVmActivity
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.TermuxConstants
import com.termux.app.ssh.SshConnection
import com.termux.app.ssh.SshConnectionManager
import com.termux.app.vnc.VncConnection
import com.termux.app.vnc.VncConnectionManager
import com.termux.app.vortex.VorteXSandbox
import com.termux.shared.compat.ShellEnvironmentCompat
import com.termux.shared.compat.TermuxTaskCompat
import com.gaurav.avnc.ui.vnc.VncActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import com.termux.R

/** ---------- 技能执行器 ---------- */

data class SkillExecutionResult(
    val success: Boolean,
    val message: String,
    val skillCard: SkillCardData? = null,
    val status: String = if (success) "success" else "failed"
)

/** AI 回复假输出检测结果 */
data class FakeOutputCheck(
    val isFake: Boolean,       // 是否检测到假输出
    val violations: List<String> // 违反的禁令条目
)

object SkillExecutor {
    // ---- Todo 任务管理 ----
    private val taskLock = Any()
    val taskList = mutableListOf<TaskItem>()
    private val _tasksFlow = kotlinx.coroutines.flow.MutableStateFlow<List<TaskItem>>(emptyList())
    val tasksFlow: kotlinx.coroutines.flow.StateFlow<List<TaskItem>> = _tasksFlow
    fun getTasks(): List<TaskItem> = synchronized(taskLock) { taskList.toList() }
    private fun emitTaskUpdate() { _tasksFlow.value = getTasks() }
    fun clearTasks() {
        synchronized(taskLock) { taskList.clear() }
        emitTaskUpdate()
    }


    private const val TERMUX_ROOT = "/data/data/com.termux"
    private val HOME_DIR = "$TERMUX_ROOT/files/home"

    /** 检查无限制模式是否激活 */
    private fun isUnlimitedMode(context: Context): Boolean {
        return AiTermuxPrefs.isUnlimitedModeActive(context)
    }

    /** 危险操作检测：返回危险原因，不危险返回 null。
     * 无限制模式下跳过所有危险检测。 */
    fun checkDangerous(context: Context, skillType: SkillType, params: JsonObject): String? {
        // 无限制模式：跳过所有危险检测
        if (isUnlimitedMode(context)) return null

        return when (skillType) {
            SkillType.FILE_DELETE -> {
                val path = if (params.has("path")) params.get("path").asString else ""
                when {
                    path.isBlank() -> null
                    path == "/" || path == TERMUX_ROOT -> context.getString(R.string.danger_delete_root)
                    File(path).canonicalPath.let { it == TERMUX_ROOT || it == "/data/data" } ->
                        context.getString(R.string.danger_delete_termux_root)
                    path.endsWith("/*") || path.endsWith("/.*") ->
                        context.getString(R.string.danger_recursive_delete)
                    else -> null
                }
            }
            SkillType.RUN_COMMAND,
            SkillType.CUSTOM_COMMAND,
            SkillType.CAPTURE_OUTPUT,
            SkillType.COMPILE_CODE,
            SkillType.SUB_AGENT -> detectCommandDanger(commandOf(skillType, params))
            SkillType.CLOSE_ALL_SESSIONS -> context.getString(R.string.danger_close_all_sessions)
            SkillType.EXIT_TERMUX -> context.getString(R.string.danger_exit_termux)
            else -> null
        }
    }

    /** 取出技能参数中真正会被执行的命令（不同技能字段名不同） */
    private fun commandOf(skillType: SkillType, params: JsonObject): String {
        val keys = if (skillType == SkillType.SUB_AGENT) {
            listOf("commands", "command")
        } else {
            listOf("command")
        }
        for (key in keys) {
            if (params.has(key)) return params.get(key).asString
        }
        return ""
    }

    /** 命令风险检测：su/sudo 会继续检查其包装的子命令 */
    private fun detectCommandDanger(command: String): String? {
        if (command.isBlank()) return null
        val result = RiskCommandDetector.detect(command)
        if (!result.isDangerous) return null
        if (result.riskType == RiskCommandDetector.RiskType.SU_SUDO) {
            val wrapped = extractWrappedCommandForCheck(command)
            if (wrapped != null) {
                val wrappedResult = RiskCommandDetector.detect(wrapped)
                if (wrappedResult.isDangerous && wrappedResult.riskType != RiskCommandDetector.RiskType.SU_SUDO) {
                    return wrappedResult.description
                }
            }
        }
        return result.description
    }

    /** 从 su/sudo 命令中提取被包装的子命令（用于 Agent 危险检测） */
    private fun extractWrappedCommandForCheck(command: String): String? {
        val trimmed = command.trim()
        val sudoPattern = Regex("""^\s*sudo\s+(.*)""", RegexOption.DOT_MATCHES_ALL)
        val sudoMatch = sudoPattern.find(trimmed)
        if (sudoMatch != null) {
            return sudoMatch.groupValues[1].trim()
        }
        val suPattern = Regex("""^\s*su\s+-c\s+['"]?(.+?)['"]?\s*$""", RegexOption.DOT_MATCHES_ALL)
        val suMatch = suPattern.find(trimmed)
        if (suMatch != null) {
            return suMatch.groupValues[1].trim()
        }
        return null
    }

    /** 从 AI 回复内容中解析技能块（支持多种代码块标记、直接 JSON、以及行业标准 tool_call 格式） */
    fun parseSkillBlocks(content: String): List<Pair<String, JsonObject>> {
        val results = mutableListOf<Pair<String, JsonObject>>()
        val seen = mutableSetOf<String>()

        fun add(type: String?, params: JsonObject?) {
            val raw = type?.trim()?.uppercase() ?: return
            if (!isValidSkillType(raw)) return
            val p = params ?: JsonObject()
            val key = canonicalSkillKey(raw, p)
            if (seen.add(key)) results.add(raw to p)
        }

        // 策略 1: 匹配 ```skill / ```json / ```javascript 等代码块包裹的 JSON
        // 宽容的正则：允许 closing ``` 前没有换行符
        val blockPattern = Regex(
            """```[a-zA-Z]*\s*\r?\n([\s\S]*?)[\r\n]*```""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (match in blockPattern.findAll(content)) {
            val jsonStr = match.groupValues.getOrNull(1)?.trim() ?: continue
            try {
                val json = JsonParser.parseString(jsonStr).asJsonObject
                add(json.get("skillType")?.asString, json.getAsJsonObject("params"))
            } catch (_: Exception) { }
        }

        // 策略 2: 解析行业标准 <tool_call> XML 格式
        val toolCallPattern = Regex("""<tool_call>([\s\S]*?)</tool_call>""", RegexOption.DOT_MATCHES_ALL)
        val namePattern = Regex("""<tool_name>\s*([\s\S]*?)\s*</tool_name>""", RegexOption.DOT_MATCHES_ALL)
        val paramPattern = Regex(
            """<parameter\s+name\s*=\s*["']([^"']+)["']\s*>([\s\S]*?)</parameter>""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (match in toolCallPattern.findAll(content)) {
            val block = match.groupValues.getOrNull(1)?.trim() ?: continue
            val nameMatch = namePattern.find(block) ?: continue
            val params = JsonObject()
            paramPattern.findAll(block).forEach { pm ->
                val pname = pm.groupValues[1].trim()
                val pval = pm.groupValues[2].trim()
                runCatching { params.add(pname, JsonPrimitive(pval)) }
            }
            add(nameMatch.groupValues[1], params)
        }

        // 策略 3: 直接在文本中查找包含 skillType 的 JSON 对象（兜底）
        if (results.isEmpty()) {
            val jsonPattern = Regex("""\{[^{}]*"skillType"[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
            for (match in jsonPattern.findAll(content)) {
                try {
                    val json = JsonParser.parseString(match.value.trim()).asJsonObject
                    add(json.get("skillType")?.asString, json.getAsJsonObject("params"))
                } catch (_: Exception) { }
            }
        }

        return results
    }

    /**
     * 技能去重键：XML 格式的参数全是字符串、JSON 格式可能带数字/布尔，
     * 直接比较 JsonObject 会让同一调用的两种写法算出不同键，因此统一归一化。
     */
    private fun canonicalSkillKey(skillType: String, params: JsonObject): String {
        val sb = StringBuilder(skillType)
        params.entrySet().sortedBy { it.key }.forEach { (key, value) ->
            sb.append('|').append(key).append('=')
            sb.append(if (value.isJsonPrimitive) value.asString else value.toString())
        }
        return sb.toString()
    }

    /** 解析 <new_tool> 块，提取 AI 自主创造的新技能 */
    fun parseNewToolBlocks(content: String): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val pattern = Regex(
            """<new_tool>([\s\S]*?)</new_tool>""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (match in pattern.findAll(content)) {
            val block = match.groupValues.getOrNull(1)?.trim() ?: continue
            val tool = mutableMapOf<String, String>()
            val tagPattern = Regex(
                """<(tool_name|description|system_prompt|skill_json|implementation_type)>[\s\S]*?</\1>""",
                RegexOption.DOT_MATCHES_ALL
            )
            for (tagMatch in tagPattern.findAll(block)) {
                val tagName = tagMatch.groupValues[1].trim()
                val tagStart = tagMatch.value.indexOf(">") + 1
                val tagEnd = tagMatch.value.lastIndexOf("</")
                val tagValue = if (tagStart >= 0 && tagEnd > tagStart) {
                    tagMatch.value.substring(tagStart, tagEnd).trim()
                } else ""
                tool[tagName] = tagValue
            }
            if (tool.containsKey("tool_name") && tool["tool_name"]!!.isNotBlank()) {
                results.add(tool)
            }
        }
        return results
    }

    private fun isValidSkillType(type: String): Boolean {
        return try {
            SkillType.valueOf(type.uppercase())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 提取技能块以外的纯文本回复，过滤 AI 编造的伪造结果 */
    fun stripSkillBlocks(content: String): String {
        var result = content

        // 1. 移除所有包含 skillType 的代码块（使用宽容的正则）
        val blockPattern = Regex(
            """```[a-zA-Z]*\s*\r?\n([\s\S]*?)[\r\n]*```""",
            RegexOption.DOT_MATCHES_ALL
        )
        result = blockPattern.replace(result) { match ->
            val jsonStr = match.groupValues.getOrNull(1)?.trim() ?: return@replace match.value
            try {
                val json = JsonParser.parseString(jsonStr).asJsonObject
                val skillType = json.get("skillType")?.asString
                if (skillType != null && skillType.isNotBlank()) "" else match.value
            } catch (_: Exception) {
                match.value
            }
        }

        // 2. 移除所有 <tool_call> 行业标准格式块
        val toolCallStrip = Regex(
            """<tool_call>([\s\S]*?)</tool_call>""",
            RegexOption.DOT_MATCHES_ALL
        )
        result = toolCallStrip.replace(result, "").trim()

        // 3. 移除直接内联的 JSON 技能对象
        val jsonPattern = Regex("""\{[^{}]*"skillType"[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
        result = jsonPattern.replace(result, "").trim()

        // 4. 过滤 AI 编造的伪造结果段落
        //    删除以 "技能执行结果"、"技能名称:"、"操作:"、"状态:" 开头的伪造结果块
        val fakeResultPattern = Regex(
            """[\r\n]*[*_#\s]*技能执行结果[*_#\s]*[\r\n]+[\s\S]*?(?=[\r\n]{2,}|$)""",
            RegexOption.IGNORE_CASE
        )
        result = fakeResultPattern.replace(result, "")

        // 5. 过滤以 "技能名称:"、"操作:"、"状态:" 等开头的连续伪造行
        val fakeLinePattern = Regex(
            """^[*_#\s]*(技能名称|操作(?:说明)?|状态(?:说明)?|详细信息)[:：].*$""",
            RegexOption.MULTILINE
        )
        result = fakeLinePattern.replace(result, "")

        // 6. 过滤 AI 虚构的 "---" 分隔线（在伪造结果上下文中）
        val separatorPattern = Regex("""^[*_#\s]*-{3,}[*_#\s]*$""", RegexOption.MULTILINE)
        result = separatorPattern.replace(result, "")

        // 7. 清理残留的空行
        result = result.replace(Regex("""\n{3,}"""), "\n\n")

        return result.trim()
    }

    /**
     * 检测 AI 回复中的假输出（幻觉）。
     * 基于 System Prompt 中的绝对禁令，检测以下模式：
     * - AI 声称操作已执行/已完成
     * - AI 编造了执行结果/输出文本
     * - AI 假装看到了文件列表/进程信息等
     * - AI 在技能卡片后添加了伪造的结果段落
     *
     * @param content AI 回复的完整文本
     * @param preParsedSkillCount 预先解析到的技能块数量（用于交叉验证，避免正则遗漏导致误判）
     */
    fun detectFakeOutput(content: String, preParsedSkillCount: Int = -1, context: Context? = null): FakeOutputCheck {
        val violations = mutableListOf<String>()
        val lower = content.lowercase()

        // ---------- 统一的技能块检测（与 parseSkillBlocks 保持一致）----------

        // 策略 1: 匹配 ```skill / ```json 等代码块包裹的 JSON（更宽容的正则）
        // 允许 AI 输出中 closing ``` 前没有换行符的情况
        val blockRegex = Regex(
            """```[a-zA-Z]*\s*\r?\n([\s\S]*?)[\r\n]*```""",
            RegexOption.DOT_MATCHES_ALL
        )
        val blockMatches = blockRegex.findAll(content).toList()

        // 策略 2: 直接在文本中查找包含 skillType 的 JSON 对象（兜底）
        val inlineRegex = Regex("""\{[^{}]*"skillType"[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
        val inlineMatches = inlineRegex.findAll(content).toList()

        // 合并去重的技能块范围
        data class BlockRange(val first: Int, val last: Int)
        val allRanges = mutableListOf<BlockRange>()

        for (m in blockMatches) {
            val jsonStr = m.groupValues.getOrNull(1)?.trim() ?: continue
            try {
                val json = JsonParser.parseString(jsonStr).asJsonObject
                val skillType = json.get("skillType")?.asString
                if (skillType != null && skillType.isNotBlank()) {
                    allRanges.add(BlockRange(m.range.first, m.range.last))
                }
            } catch (_: Exception) { }
        }
        for (m in inlineMatches) {
            try {
                val json = JsonParser.parseString(m.value).asJsonObject
                val skillType = json.get("skillType")?.asString
                if (skillType != null && skillType.isNotBlank()) {
                    // 内联 JSON 的范围就是它自身
                    allRanges.add(BlockRange(m.range.first, m.range.last))
                }
            } catch (_: Exception) { }
        }

        val hasSkillBlocks = allRanges.isNotEmpty() || preParsedSkillCount > 0

        // 如果预先解析到技能块但本函数正则没匹配到，以预先解析为准
        val effectiveHasSkills = if (preParsedSkillCount >= 0 && preParsedSkillCount > 0) true else hasSkillBlocks

        // ---------- 禁令 1-3：检查技能卡片之后是否有伪造结果 ----------
        if (hasSkillBlocks && allRanges.isNotEmpty()) {
            // 获取最后一个技能块的结束位置
            val lastBlockEnd = allRanges.maxOf { it.last }
            val afterBlock = content.substring(lastBlockEnd.coerceAtMost(content.length - 1)).trim()

            if (afterBlock.isNotBlank()) {
                // 禁令 1：伪造的执行结果描述
                // 仅检查真正可疑的模式（捏造具体数据），排除 AI 对技能执行的合法描述
                val fabricatedDataPatterns = listOf(
                    """共\s+\d+\s+个""",
                    """\d+\s+个会话""",
                    """\d+\s+个文件""",
                    """\d+\s+个进程""",
                    "共找到",
                    "运行中进程",
                )
                var foundFake = false
                for (pattern in fabricatedDataPatterns) {
                    if (Regex(pattern).containsMatchIn(afterBlock)) {
                        violations.add("【禁令1】技能卡片后存在伪造的执行结果描述（包含「$pattern」）")
                        foundFake = true
                        break
                    }
                }
                if (!foundFake) {
                    val commonDescPatterns = listOf(
                        "执行结果", "已执行", "已完成", "执行成功", "执行完毕",
                        "技能结果", "skill result", "详细信息",
                        "操作说明", "结果说明",
                        "正在读取", "正在检查", "正在扫描",
                        "输出", "output", "结果如下"
                    )
                    for (pattern in commonDescPatterns) {
                        if (afterBlock.contains(pattern, ignoreCase = true)) {
                            val skillTypesInBlock = mutableListOf<String>()
                            for (r in allRanges) {
                                try {
                                    val json = JsonParser.parseString(
                                        content.substring(r.first, r.last).trim()
                                    ).asJsonObject
                                    skillTypesInBlock.add(json.get("skillType")?.asString ?: "")
                                } catch (_: Exception) { }
                            }
                            if (skillTypesInBlock.isEmpty()) {
                                violations.add("【禁令1】技能卡片后存在可疑的执行结果描述（包含「$pattern」但未识别到技能类型）")
                            }
                            break
                        }
                    }
                }

                // 禁令 2：伪造的系统/文件信息
                val fakeInfoPatterns = listOf(
                    "/proc/", "/data/data/com.termux", "qemu-system",
                    "进程列表", "内存使用", "磁盘空间",
                    "uptime", "load average", "cpu usage"
                )
                for (pattern in fakeInfoPatterns) {
                    if (afterBlock.contains(pattern, ignoreCase = true)) {
                        violations.add("【禁令2】技能卡片后存在伪造的系统/文件信息（包含「$pattern」）")
                        break
                    }
                }

                // 禁令 3：伪造的分隔线 + 结果段落
                if (Regex("""-{3,}""").containsMatchIn(afterBlock) &&
                    (afterBlock.contains("技能") || afterBlock.contains("结果") || afterBlock.contains("输出"))) {
                    violations.add("【禁令3】技能卡片后存在伪造的分隔线和结果段落")
                }
            }
        }

        // ---------- 禁令 4/6/7/8：仅在确认无技能块时才检查 ----------
        // 使用 effectiveHasSkills 进行交叉验证：如果预解析有技能，即使正则没匹配到也跳过这些检查
        if (!effectiveHasSkills) {
            val noSkillViolations = mutableListOf<String>()

            // 禁令 4：假装执行了操作但无技能卡片
            // 使用更具体的模式组合，避免误判 AI 对技能执行的合法描述
            val fakeActionPatterns = listOf(
                "已创建.*(文件|会话|进程|目录)",
                "已连接.*(SSH|VNC|远程)",
                "已安装.*(软件|包|package)",
                "已发送.*(命令|请求)",
                "读取了.*(文件|目录|内容)",
                "写入了.*(文件|内容)",
                "删除了.*(文件|目录)",
                "执行结果.*(显示|为|是)",
                "列出了.*(文件|目录|会话|进程)",
                "操作成功.*(创建|连接|安装|写入|删除)",
            )
            for (pattern in fakeActionPatterns) {
                if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(content)) {
                    noSkillViolations.add("【禁令4】回复声称操作「$pattern」但未输出任何技能卡片")
                    break
                }
            }

            // 禁令 6：自信式幻觉
            val confidentHollowPatterns = listOf(
                "不存在", "没有这个", "无法直接执行", "无法为你执行",
                "不支持", "不允许", "权限不足", "无法访问",
                "当前环境中没有", "该功能尚未", "暂时不支持",
                "没有权限", "不在允许的路径", "禁用的文件系统",
                "我目前没有", "我没有这个功能", "我无法执行",
            )
            for (pattern in confidentHollowPatterns) {
                if (lower.contains(pattern)) {
                    noSkillViolations.add("【禁令6】自信式幻觉：声称「$pattern」但未提供任何技能或真实依据")
                    break
                }
            }

            // 禁令 7：捏造技能
            val fakeSkillPatterns = listOf(
                "技能卡片", "智能日程", "天气查询", "外卖下单",
                "快递查询", "语音助手", "翻译技能", "搜索技能",
            )
            for (pattern in fakeSkillPatterns) {
                if (lower.contains(pattern) && lower.contains("已")) {
                    noSkillViolations.add("【禁令7】捏造不存在的技能：「$pattern」")
                    break
                }
            }

            // 禁令 8：提供解决方案式幻觉
            val solutionPattern = Regex("(解决方案|解决方法|解决办法)[\\s\\S]*?(请在|您可以|建议你|建议您)", RegexOption.IGNORE_CASE)
            if (solutionPattern.containsMatchIn(content) && content.length > 200) {
                noSkillViolations.add("【禁令8】逃避执行：不使用技能执行操作，反而提供用户自行操作的\"解决方案\"")
            }

            violations.addAll(noSkillViolations)
        }

        // ---------- 禁令 5：虚构的命令/系统输出 ----------
        // 即使有技能块，也要检查技能块之外是否有伪造输出
        // 无限制模式下跳过禁令5，由用户自行评判
        if (context != null && isUnlimitedMode(context)) {
            // 无限制模式：跳过禁令5检测
        } else {
        val fakeOutputPatterns = listOf(
            """\[D\]\s+\S+""",
            """\[F\]\s+\S+""",
            """total\s+\d+""",
            """^\s*\d+\s+\S+\s+\S+""",
            """PID\s+USER\s+COMMAND""",
            """/proc/\d+/status""",
            """MemTotal|MemFree|MemAvailable""",
            """[<>]\s*[0-9a-fA-F]+\s*bytes""",
            """\d+\s+imported""",
            """\d+\s+not upgraded""",
            """Setting up\s+""",
            """Processing triggers""",
        )
        for (pattern in fakeOutputPatterns) {
            if (Regex(pattern, RegexOption.MULTILINE).containsMatchIn(content)) {
                // 如果有技能块，检查伪造输出是否在技能块范围内
                if (hasSkillBlocks && allRanges.isNotEmpty()) {
                    val firstBlockStart = allRanges.minOf { it.first }
                    val lastBlockEnd = allRanges.maxOf { it.last }
                    val insideBlocks = content.substring(
                        firstBlockStart.coerceAtLeast(0),
                        lastBlockEnd.coerceAtMost(content.length - 1)
                    ).contains(pattern)
                    if (!insideBlocks) {
                        violations.add("【禁令5】回复包含虚构的命令/系统输出")
                        break
                    }
                } else {
                    violations.add("【禁令5】回复包含虚构的命令/系统输出")
                    break
                }
            }
        }
        }

        return FakeOutputCheck(violations.isNotEmpty(), violations)
    }

    /** 执行单个技能 */
    /**
     * Texto localizado de un recurso, resuelto con el context de la app.
     *
     * Las 41 funciones exec* ya reciben un Context en su mayoria, pero cinco de
     * ellas no (las que no tocan el sistema) y los textos de las tarjetas de
     * habilidad se arman en el dispatcher. El context ya lo inicializa
     * AiLocalModel.init() desde la Activity, asi que se resuelve por ahi en vez
     * de propagar un parametro por las 41 firmas.
     */
    private fun str(@StringRes res: Int, vararg args: Any): String {
        val c = AiLocalModel.context() ?: return "-"
        return if (args.isEmpty()) c.getString(res) else c.getString(res, *args)
    }

    /** Estado de un interruptor (ubicacion, etc.) ya localizado. */
    private fun switchState(enabled: Boolean): String =
        if (enabled) str(R.string.eng_switch_enabled) else str(R.string.eng_switch_disabled)

    suspend fun executeSkill(
        context: Context,
        termuxService: TermuxService?,
        skillTypeStr: String,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        val skillType = try { SkillType.valueOf(skillTypeStr) } catch (_: Exception) { null }

        // ASK_USER 是交互类技能，直接生成卡片返回
        if (skillType == SkillType.ASK_USER) {
            return@withContext execAskUser(context, params)
        }

        // 未知技能类型但包含 command —— 兜底为 RUN_COMMAND 执行
        if (skillType == null) {
            val hasCommand = params.has("command")
            if (hasCommand) {
                android.util.Log.w("AiTermux", "未知技能类型 '$skillTypeStr'，兜底为 RUN_COMMAND 执行")
                return@withContext execRunCommand(context, termuxService, params)
            }
            return@withContext SkillExecutionResult(false, str(R.string.eng_unknown_skill_type, skillTypeStr))
        }

        when (skillType) {
            SkillType.NEW_SESSION -> execNewSession(context, termuxService, params)
            SkillType.CLOSE_SESSION -> execCloseSession(context, termuxService, params)
            SkillType.CLOSE_ALL_SESSIONS -> execCloseAllSessions(context, termuxService)
            SkillType.EXIT_TERMUX -> execExitTermux(context)
            SkillType.GET_SESSION_INFO -> execGetSessionInfo(context, termuxService)
            SkillType.RUN_VM_QEMU -> execRunVmQemu(context, params)
            SkillType.CREATE_VM_QEMU -> execCreateVmQemu(context, params)
            SkillType.VM_LIST -> execVmList(context, termuxService, params)
            SkillType.CONNECT_VNC -> execConnectVnc(context, params)
            SkillType.CONNECT_SSH -> execConnectSsh(context, termuxService, params)
            SkillType.LIST_REMOTE_CONNECTIONS -> execListRemoteConnections(context)
            SkillType.CONNECT_REMOTE_CONNECTION -> execConnectRemoteConnection(context, termuxService, params)
            SkillType.FILE_LIST -> execFileList(context, params)
            SkillType.FILE_READ -> execFileRead(context, params)
            SkillType.FILE_WRITE -> execFileWrite(context, params)
            SkillType.FILE_DELETE -> execFileDelete(context, params)
            SkillType.FILE_GENERATE -> execFileGenerate(context, params)
            SkillType.FILE_MODIFY -> execFileModify(context, params)
            SkillType.RUN_COMMAND -> execRunCommand(context, termuxService, params)
            SkillType.RUN_COMMAND_SANDBOX -> execRunCommandInSandbox(context, params)
            SkillType.CAPTURE_OUTPUT -> execCaptureOutput(context, termuxService, params)
            SkillType.PACKAGE_INSTALL -> execPackageInstall(context, termuxService, params)
            SkillType.PACKAGE_UNINSTALL -> execPackageUninstall(context, termuxService, params)
            SkillType.APP_INSTALL -> execAppInstall(context, termuxService, params)
            SkillType.APP_UNINSTALL -> execAppUninstall(context, termuxService, params)
            SkillType.COMPILE_CODE -> execCompileCode(context, termuxService, params)
            SkillType.SUB_AGENT -> execSubAgent(context, termuxService, params)
            SkillType.SEARCH_AGENT -> execSearchAgent(context, termuxService, params)
            SkillType.WEB_SEARCH -> execWebSearch(context, termuxService, params)
            SkillType.CONFIRM_DANGEROUS -> SkillExecutionResult(false, "危险操作需在 UI 中确认后执行")
            SkillType.CONFIRM_DUPLICATE -> SkillExecutionResult(false, "重复操作确认需在 UI 中处理")
            SkillType.SCHEDULE_TASK -> execScheduleTask(context, params)
            SkillType.GET_DEVICE_STATUS -> execGetDeviceStatus(context, termuxService, params)
            SkillType.GET_CURRENT_SESSION -> execGetCurrentSession(context, termuxService)
            SkillType.TASK_ADD -> execTaskAdd(context, params)
            SkillType.TASK_UPDATE -> execTaskUpdate(context, params)
            SkillType.TASK_DELETE -> execTaskDelete(context, params)
            SkillType.TASK_LIST -> execTaskList(context)
            SkillType.CLIPBOARD_READ -> execClipboardRead(context)
            SkillType.CLIPBOARD_WRITE -> execClipboardWrite(context, params)
            SkillType.CUSTOM_COMMAND -> {
                when {
                    params.has("activityClass") -> execOpenActivity(context, params)
                    params.has("action") -> execSendBroadcast(context, params)
                    params.has("command") -> execRunCommand(context, termuxService, params)
                    else -> execRunCommand(context, termuxService, params)
                }
            }
        }
    }

    // ---- 交互类技能 ----

    private fun execAskUser(context: Context, params: JsonObject): SkillExecutionResult {
        val question = if (params.has("question")) params.get("question").asString else ""
        val type = if (params.has("type")) params.get("type").asString else "text"
        val placeholder = if (params.has("placeholder")) params.get("placeholder").asString else null
        val options = if (params.has("options")) {
            try {
                val arr = params.getAsJsonArray("options")
                (0 until arr.size()).map { arr[it].asString }
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val typeTitle = when (type) {
            "single" -> str(R.string.eng_ask_type_single)
            "multi" -> str(R.string.eng_ask_type_multi)
            else -> str(R.string.eng_ask_type_text)
        }
        return SkillExecutionResult(true, str(R.string.eng_ask_question_sent, question),
            SkillCardData(
                skillType = SkillType.ASK_USER,
                title = str(R.string.eng_ask_card_title, typeTitle),
                description = question,
                status = SkillStatus.RUNNING,
                askQuestion = question,
                askType = type,
                askOptions = options.ifEmpty { null },
                askPlaceholder = placeholder
            )
        )
    }

    
    private fun execTaskAdd(context: Context, params: JsonObject): SkillExecutionResult {
        val rawTitle = if (params.has("title")) params.get("title").asString.trim() else ""
        if (rawTitle.isBlank()) return SkillExecutionResult(false, str(R.string.eng_task_add_needs_title))
        val requestedStatus = if (params.has("status")) params.get("status").asString else "pending"
        val titles = rawTitle.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        synchronized(taskLock) {
            for (t in titles) {
                taskList.add(TaskItem(
                    id = "task_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}",
                    title = t,
                    status = requestedStatus
                ))
            }
        }
        emitTaskUpdate()
        val cnt = synchronized(taskLock) { taskList.size }
        val summary = if (titles.size > 1) {
            str(R.string.eng_task_add_batch_summary, titles.size, cnt)
        } else {
            str(R.string.eng_task_add_single_summary, titles.first(), cnt)
        }
        return SkillExecutionResult(true, summary,
            SkillCardData(skillType = SkillType.TASK_ADD,
                title = if (titles.size > 1) str(R.string.eng_task_add_batch_title, titles.size)
                        else str(R.string.eng_task_add_title),
                description = rawTitle, status = SkillStatus.COMPLETED, output = summary))
    }

    private fun execTaskUpdate(context: Context, params: JsonObject): SkillExecutionResult {
        val taskId = if (params.has("taskId")) params.get("taskId").asString else ""
        val ns = if (params.has("status")) params.get("status").asString else null
        val nc = if (params.has("comment")) params.get("comment").asString else null
        if (taskId.isBlank() && ns == null && nc == null)
            return SkillExecutionResult(false, str(R.string.eng_task_update_needs_args))
        var targetTitle: String? = null
        synchronized(taskLock) {
            val idx = if (taskId.isNotBlank())
                taskList.indexOfFirst { it.id == taskId }
            else
                taskList.indexOfLast { it.status == "pending" || it.status == "in_progress" }
            if (idx < 0) return SkillExecutionResult(false, str(R.string.eng_task_not_found))
            val old = taskList[idx]
            taskList[idx] = old.copy(
                status = ns ?: old.status,
                comment = nc ?: old.comment,
                completedAt = if (ns == "done") System.currentTimeMillis() else old.completedAt
            )
            targetTitle = old.title
        }
        emitTaskUpdate()
        return SkillExecutionResult(true, str(R.string.eng_task_updated, targetTitle ?: taskId),
            SkillCardData(skillType = SkillType.TASK_UPDATE, title = str(R.string.eng_task_update_title),
                description = targetTitle ?: taskId.ifBlank { str(R.string.eng_task_recent) }, status = SkillStatus.COMPLETED))
    }

    private fun execTaskList(context: Context): SkillExecutionResult {
        val tasks = synchronized(taskLock) { taskList.toList() }
        if (tasks.isEmpty())
            return SkillExecutionResult(true, str(R.string.tasks_none),
                SkillCardData(skillType = SkillType.TASK_LIST, title = str(R.string.task_list),
                    description = str(R.string.empty), status = SkillStatus.COMPLETED, output = str(R.string.tasks_none)))
        val sb = StringBuilder()
        for (t in tasks) {
            val icon = when (t.status) { "done" -> "[OK]"; "in_progress" -> "[...]"; "cancelled" -> "[X]" else -> "[ ]" }
            sb.append("$icon ${t.title}")
            if (!t.comment.isNullOrBlank()) sb.append(" - ${t.comment}")
            sb.append("\n")
        }
        val out = sb.toString().trimEnd()
        emitTaskUpdate()
        return SkillExecutionResult(true, str(R.string.eng_tasks_count, tasks.size),
            SkillCardData(skillType = SkillType.TASK_LIST, title = str(R.string.task_list),
                description = str(R.string.eng_tasks_count, tasks.size), status = SkillStatus.COMPLETED, output = out))
    }

    private fun execTaskDelete(context: Context, params: JsonObject): SkillExecutionResult {
        val taskId = if (params.has("taskId")) params.get("taskId").asString else ""
        val title = if (params.has("title")) params.get("title").asString else ""
        var removedTitle: String? = null
        synchronized(taskLock) {
            val idx = when {
                taskId.isNotBlank() -> taskList.indexOfFirst { it.id == taskId }
                title.isNotBlank() -> taskList.indexOfFirst { it.title.contains(title, ignoreCase = true) }
                else -> taskList.indexOfLast { it.status == "done" || it.status == "cancelled" }
            }
            if (idx < 0) {
                return SkillExecutionResult(false, str(R.string.eng_task_delete_not_found),
                    SkillCardData(skillType = SkillType.TASK_DELETE, title = str(R.string.eng_task_delete_title),
                        description = str(R.string.eng_task_no_matching), status = SkillStatus.FAILED))
            }
            removedTitle = taskList[idx].title
            taskList.removeAt(idx)
        }
        emitTaskUpdate()
        return SkillExecutionResult(true, str(R.string.eng_task_deleted, removedTitle ?: taskId),
            SkillCardData(skillType = SkillType.TASK_DELETE, title = str(R.string.eng_task_delete_title),
                description = removedTitle ?: taskId.ifBlank { str(R.string.eng_task_recent_done) }, status = SkillStatus.COMPLETED))
    }

// ---- 会话管理（全部需要 Main 线程 —— TermuxService/TerminalSession 内部使用 Handler）----

    private suspend fun execNewSession(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        val name = if (params.has("name")) params.get("name").asString else null
        val unlimited = isUnlimitedMode(context)
        return@withContext try {
            val session = termuxService.createTermuxSession(
                null, null, null, null, false, name
            )
            if (session != null) {
                val ts = session.getTerminalSession()
                val handle = ts.mHandle.toString()
                val displayName = ts.mSessionName ?: "Terminal"
                // 无限制模式：自动将会话转到前台
                if (unlimited) {
                    val i = Intent(context, TermuxActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    i.putExtra("sessionHandle", handle)
                    context.startActivity(i)
                }
                SkillExecutionResult(true, if (unlimited) str(R.string.eng_session_created_activated) else str(R.string.eng_session_card_generated),
                    SkillCardData(
                        skillType = SkillType.NEW_SESSION,
                        title = if (unlimited) str(R.string.eng_new_session_activated_title) else str(R.string.eng_new_session_title),
                        description = if (unlimited) str(R.string.eng_new_session_desc, displayName)
                        else str(R.string.eng_new_session_desc_tap, displayName),
                        status = SkillStatus.COMPLETED,
                        sessionId = handle,
                        sessionName = displayName
                    )
                )
            } else {
                SkillExecutionResult(false, str(R.string.eng_new_session_failed_empty))
            }
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_new_session_error, e.message ?: ""),
                skillCard = SkillCardData(
                    skillType = SkillType.NEW_SESSION,
                    title = str(R.string.eng_new_session_failed_title),
                    description = e.message ?: str(R.string.eng_unknown_error),
                    status = SkillStatus.FAILED
                )
            )
        }
    }

    private suspend fun execCloseSession(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        val sessionId = if (params.has("sessionId")) params.get("sessionId").asString else ""
        if (sessionId.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_missing_session_id))
        return@withContext try {
            val sessions = termuxService.getTermuxSessions()
            val target = sessions.find {
                val ts = it.getTerminalSession()
                ts.mHandle.toString() == sessionId || ts.mSessionName == sessionId
            }
            if (target != null) {
                val ts = target.getTerminalSession()
                val displayName = ts.mSessionName ?: "Terminal"
                termuxService.removeTermuxSession(ts)
                SkillExecutionResult(true, str(R.string.eng_session_closed, displayName),
                    SkillCardData(
                        skillType = SkillType.CLOSE_SESSION,
                        title = str(R.string.eng_session_closed_title),
                        description = str(R.string.eng_session_label, displayName),
                        status = SkillStatus.COMPLETED,
                        sessionName = displayName
                    )
                )
            } else {
                SkillExecutionResult(false, str(R.string.eng_session_not_found, sessionId))
            }
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_close_session_error, e.message ?: ""))
        }
    }

    private suspend fun execCloseAllSessions(
        context: Context,
        termuxService: TermuxService?
    ): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        return@withContext try {
            val sessions = termuxService.getTermuxSessions().toList()
            sessions.forEach { termuxService.removeTermuxSession(it.getTerminalSession()) }
            SkillExecutionResult(true, str(R.string.eng_close_all_sessions_count, sessions.size),
                SkillCardData(
                    skillType = SkillType.CLOSE_ALL_SESSIONS,
                    title = str(R.string.sessions_closed),
                    description = str(R.string.eng_close_all_sessions_desc, sessions.size),
                    status = SkillStatus.COMPLETED
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_close_all_sessions_error, e.message ?: ""))
        }
    }

    private suspend fun execExitTermux(context: Context): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        return@withContext try {
            val activity = context as? Activity
            if (activity != null) {
                activity.finishAffinity()
            } else {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            }
            SkillExecutionResult(true, str(R.string.eng_termux_exited),
                SkillCardData(
                    skillType = SkillType.EXIT_TERMUX,
                    title = str(R.string.eng_termux_exited),
                    description = str(R.string.eng_app_exited),
                    status = SkillStatus.COMPLETED
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_exit_termux_error, e.message ?: ""))
        }
    }

    private suspend fun execGetSessionInfo(
        context: Context,
        termuxService: TermuxService?
    ): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        return@withContext try {
            val sessions = termuxService.getTermuxSessions()
            val info = sessions.joinToString("\n") {
                val ts = it.getTerminalSession()
                str(R.string.eng_session_line, ts.mSessionName ?: "Terminal", ts.mHandle.toString(), ts.isRunning)
            }
            val desc = if (sessions.isEmpty()) str(R.string.eng_no_running_sessions)
            else str(R.string.eng_session_count, sessions.size)
            SkillExecutionResult(true, info.ifBlank { str(R.string.eng_no_sessions) },
                SkillCardData(
                    skillType = SkillType.GET_SESSION_INFO,
                    title = str(R.string.eng_session_info_title),
                    description = desc,
                    status = SkillStatus.COMPLETED,
                    output = info.ifBlank { str(R.string.eng_no_active_sessions) }
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_session_info_error, e.message ?: ""))
        }
    }

    // ---- 虚拟机管理（startActivity 切到 Main 更稳妥）----

    private suspend fun execRunVmQemu(context: Context, params: JsonObject): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        val vmName = if (params.has("vmName")) params.get("vmName").asString else ""
        return@withContext try {
            val intent = Intent(context, QemuVmActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (vmName.isNotBlank()) intent.putExtra("vmName", vmName)
            context.startActivity(intent)
            SkillExecutionResult(true, str(R.string.eng_vm_qemu_page_opened),
                SkillCardData(
                    skillType = SkillType.RUN_VM_QEMU,
                    title = str(R.string.eng_vm_qemu_open_title),
                    description = if (vmName.isNotBlank()) str(R.string.eng_vm_desc, vmName) else str(R.string.eng_vm_manager_page),
                    status = SkillStatus.COMPLETED,
                    vmName = vmName.ifBlank { null }
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_vm_open_failed, e.message ?: ""))
        }
    }

    private suspend fun execCreateVmQemu(context: Context, params: JsonObject): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        return@withContext try {
            val intent = Intent(context, QemuVmActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("createNew", true)
            if (params.has("vmName")) intent.putExtra("vmName", params.get("vmName").asString)
            if (params.has("memoryMB")) intent.putExtra("memoryMB", params.get("memoryMB").asInt)
            if (params.has("diskGB")) intent.putExtra("diskGB", params.get("diskGB").asInt)
            if (params.has("cpuCores")) intent.putExtra("cpuCores", params.get("cpuCores").asInt)
            context.startActivity(intent)
            SkillExecutionResult(true, str(R.string.eng_vm_create_config_opened),
                SkillCardData(
                    skillType = SkillType.CREATE_VM_QEMU,
                    title = str(R.string.eng_vm_create_title),
                    description = str(R.string.eng_vm_configuring),
                    status = SkillStatus.COMPLETED,
                    vmName = if (params.has("vmName")) params.get("vmName").asString else null
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_vm_create_failed, e.message ?: ""))
        }
    }

    // ---- 虚拟机管理 ----

    private suspend fun execVmList(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        val command = if (params.has("command")) params.get("command").asString else ""
        val description = if (params.has("description")) params.get("description").asString else str(R.string.eng_vm_list_desc)
        if (command.isBlank()) {
            return@withContext SkillExecutionResult(false, str(R.string.eng_vm_list_needs_command))
        }
        val cmdParams = JsonObject().apply {
            addProperty("command", command)
            if (params.has("sessionName")) addProperty("sessionName", params.get("sessionName").asString)
            else addProperty("sessionName", "VM-List")
        }
        val result = execRunCommand(context, termuxService, cmdParams)
        if (result.success) {
            result.copy(
                skillCard = result.skillCard?.copy(
                    skillType = SkillType.VM_LIST,
                    title = description
                ) ?: SkillCardData(
                    skillType = SkillType.VM_LIST,
                    title = description,
                    description = command,
                    status = SkillStatus.COMPLETED
                )
            )
        } else {
            result
        }
    }

    // ---- 命令执行（带输出捕获） ----

    private fun resolveTermuxShell(): String? {
        val binDir = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
        if (binDir.isNotEmpty()) {
            for (shellBinary in arrayOf("bash", "login", "zsh", "sh")) {
                val shellFile = java.io.File(binDir, shellBinary)
                if (shellFile.canExecute()) return shellFile.absolutePath
            }
        }
        val systemShell = java.io.File("/system/bin/sh")
        if (systemShell.canExecute()) return systemShell.absolutePath
        return null
    }

    private suspend fun execCaptureOutput(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        val command = if (params.has("command")) params.get("command").asString else ""
        if (command.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_command_empty))
        val timeoutSeconds = (if (params.has("timeout")) params.get("timeout").asInt else 30).coerceIn(5, 120)
        val description = if (params.has("description")) params.get("description").asString else command

        // 无限制模式下跳过危险命令检测
        if (!isUnlimitedMode(context)) {
            val danger = Regex("""rm\s+-[a-zA-Z]*r[a-zA-Z]*\s+/\b|mkfs|dd\s+if=.*/dev/block|:\(\)\{ :\|:\& \};:""")
            if (danger.containsMatchIn(command)) {
                return@withContext SkillExecutionResult(false, str(R.string.eng_dangerous_command_blocked))
            }
        }

        return@withContext try {
            val shellPath = resolveTermuxShell()
                ?: return@withContext SkillExecutionResult(false, str(R.string.eng_no_shell_found))

            val executionCommand = ExecutionCommand(
                System.currentTimeMillis().toInt(),
                shellPath,
                arrayOf("-c", command),
                null,
                null,
                "app-shell",
                false
            )

            val shellEnvClient = ShellEnvironmentCompat(TermuxShellEnvironment())
            val termuxTask = TermuxTaskCompat.execute(
                context,
                executionCommand,
                null,
                shellEnvClient,
                false
            )

            if (termuxTask == null) {
                val errCode = executionCommand.resultData.getErrCode()
                val errMsg = executionCommand.resultData.errorsList
                    ?.joinToString("; ") { it.message ?: "" }
                    ?.ifBlank { str(R.string.eng_task_create_failed_code, errCode) }
                    ?: str(R.string.eng_task_create_failed)
                return@withContext SkillExecutionResult(false, errMsg)
            }

            val resultData = executionCommand.resultData
            val startTime = System.currentTimeMillis()
            val timeoutMs = timeoutSeconds * 1000L
            var completed = false

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                delay(200)
                if (executionCommand.hasExecuted() || resultData.exitCode != null) {
                    completed = true
                    break
                }
            }

            if (!completed) {
                runCatching {
                    termuxTask.killIfExecuting(context, false)
                }
            }

            val stdout = resultData.stdout.toString()
            val stderr = resultData.stderr.toString()
            val combined = buildString {
                if (stdout.isNotBlank()) append(stdout.trim())
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr.trim())
                }
            }
            val output = if (combined.isBlank()) {
                if (completed) str(R.string.eng_output_empty_completed) else str(R.string.eng_output_empty_timeout)
            } else {
                combined
            }

            val truncatedOutput = if (output.length > 30000) output.take(30000) + "\n..." + str(R.string.eng_output_truncated_over, 30000) else output
            val exitCode = resultData.exitCode

            SkillExecutionResult(
                success = completed || output.isNotBlank(),
                message = if (completed) { if (exitCode != null) str(R.string.eng_command_completed_exit, exitCode) else str(R.string.eng_command_completed) } else str(R.string.eng_command_may_still_running),
                skillCard = SkillCardData(
                    skillType = SkillType.CAPTURE_OUTPUT,
                    title = description,
                    description = command,
                    status = if (completed) SkillStatus.COMPLETED else SkillStatus.RUNNING,
                    command = command,
                    output = truncatedOutput,
                    partialOutput = !completed
                )
            )
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            SkillExecutionResult(false, str(R.string.eng_execution_error, msg))
        }
    }

    // ---- 远程连接 ----

    private suspend fun execConnectVnc(context: Context, params: JsonObject): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        val address = if (params.has("address")) params.get("address").asString else ""
        if (address.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_vnc_no_address))
        val password = if (params.has("password")) params.get("password").asString else ""
        return@withContext try {
            val intent = Intent(context, VncActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val parts = address.split(":")
            val host = parts.getOrNull(0) ?: address
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 5900
            intent.putExtra("host", host)
            intent.putExtra("port", port)
            if (password.isNotBlank()) intent.putExtra("password", password)
            context.startActivity(intent)
            SkillExecutionResult(true, str(R.string.eng_vnc_card_generated),
                SkillCardData(
                    skillType = SkillType.CONNECT_VNC,
                    title = str(R.string.eng_vnc_connection),
                    description = str(R.string.eng_vnc_desc_tap, address),
                    status = SkillStatus.COMPLETED,
                    connectionAddress = address
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_vnc_connect_failed, e.message ?: ""))
        }
    }

    private suspend fun execConnectSsh(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        val host = if (params.has("host")) params.get("host").asString else ""
        if (host.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_ssh_no_host))
        val port = if (params.has("port")) params.get("port").asInt else 22
        val username = if (params.has("username")) params.get("username").asString else "root"
        val password = if (params.has("password")) params.get("password").asString else ""
        val unlimited = isUnlimitedMode(context)
        return@withContext try {
            if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
            val sshCmd = buildString {
                append("ssh ")
                if (port != 22) append("-p $port ")
                append("$username@$host")
                if (password.isNotBlank()) {
                    insert(0, "sshpass -p '${password.replace("'", "'\\''")}' ")
                }
            }
            val session = termuxService.createTermuxSession(
                null, arrayOf("-c", sshCmd), null, null, false, "SSH-$host"
            )
            if (session != null) {
                val ts = session.getTerminalSession()
                val handle = ts.mHandle.toString()
                // 无限制模式：自动将 SSH 会话转到前台
                if (unlimited) {
                    val i = Intent(context, TermuxActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    i.putExtra("sessionHandle", handle)
                    context.startActivity(i)
                }
                SkillExecutionResult(true, if (unlimited) str(R.string.eng_ssh_session_created) else str(R.string.eng_ssh_card_generated),
                    SkillCardData(
                        skillType = SkillType.CONNECT_SSH,
                        title = str(R.string.eng_ssh_connection),
                        description = if (unlimited) str(R.string.eng_ssh_conn_addr, username, host, port)
                                       else str(R.string.eng_ssh_conn_addr_tap, username, host, port),
                        status = SkillStatus.COMPLETED,
                        sessionId = handle,
                        sessionName = ts.mSessionName ?: "SSH-$host",
                        connectionAddress = "$host:$port"
                    )
                )
            } else {
                SkillExecutionResult(false, str(R.string.eng_session_create_failed))
            }
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_ssh_connect_failed, e.message ?: ""))
        }
    }

    private suspend fun execListRemoteConnections(context: Context): SkillExecutionResult = withContext(Dispatchers.IO) {
        val sshMgr = SshConnectionManager(context)
        val vncMgr = VncConnectionManager(context)
        val sshList = sshMgr.connections
        val vncList = vncMgr.connections

        val sb = StringBuilder()
        sb.appendLine(str(R.string.eng_remote_saved_header))
        sb.appendLine()

        if (sshList.isNotEmpty()) {
            sb.appendLine(str(R.string.eng_remote_ssh_label))
            for (ssh in sshList) {
                sb.appendLine("- [SSH] ${ssh.name} (${ssh.host}:${ssh.port}) [id: ${ssh.id}]")
            }
            sb.appendLine()
        }

        if (vncList.isNotEmpty()) {
            sb.appendLine(str(R.string.eng_remote_vnc_label))
            for (vnc in vncList) {
                sb.appendLine("- [VNC] ${vnc.name} (${vnc.host}:${vnc.port}) [id: ${vnc.id}]")
            }
            sb.appendLine()
        }

        val total = sshList.size + vncList.size
        if (total == 0) {
            sb.appendLine(str(R.string.eng_remote_none_saved))
        } else {
            sb.appendLine(str(R.string.eng_remote_total_hint, total))
        }

        SkillExecutionResult(true, str(R.string.eng_remote_listed),
            SkillCardData(
                skillType = SkillType.LIST_REMOTE_CONNECTIONS,
                title = str(R.string.eng_remote_list_title),
                description = str(R.string.eng_remote_list_summary, sshList.size, vncList.size),
                status = SkillStatus.COMPLETED,
                output = sb.toString()
            )
        )
    }

    private suspend fun execConnectRemoteConnection(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        val connectionId = if (params.has("connectionId")) params.get("connectionId").asString else ""
        val typeFilter = if (params.has("type")) params.get("type").asString else ""
        if (connectionId.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_remote_no_id))
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))

        val sshMgr = SshConnectionManager(context)
        val vncMgr = VncConnectionManager(context)

        var foundSsh: SshConnection? = null
        var foundVnc: VncConnection? = null

        if (typeFilter.isBlank() || typeFilter == "ssh") {
            foundSsh = sshMgr.connections.firstOrNull {
                it.id == connectionId || it.name == connectionId
            }
        }
        if (typeFilter.isBlank() || typeFilter == "vnc") {
            foundVnc = vncMgr.connections.firstOrNull {
                it.id == connectionId || it.name == connectionId
            }
        }

        when {
            foundSsh != null -> {
                val ssh = foundSsh
                val sshCmd = buildString {
                    append("ssh ")
                    if (ssh.port != 22) append("-p ${ssh.port} ")
                    append("${ssh.username}@${ssh.host}")
                    if (ssh.privateKeyPath.isNotBlank()) {
                        insert(0, "ssh -i '${ssh.privateKeyPath}' ")
                    } else if (ssh.password.isNotBlank()) {
                        insert(0, "sshpass -p '${ssh.password.replace("'", "'\\''")}' ")
                    }
                }
                val session = termuxService.createTermuxSession(
                    null, arrayOf("-c", sshCmd), null, null, false, "SSH-${ssh.name}"
                )
                if (session != null) {
                    val ts = session.getTerminalSession()
                    val handle = ts.mHandle.toString()
                    SkillExecutionResult(true, str(R.string.eng_ssh_card_generated),
                        SkillCardData(
                            skillType = SkillType.CONNECT_REMOTE_CONNECTION,
                            title = str(R.string.eng_ssh_connection_named, ssh.name),
                            description = str(R.string.eng_ssh_conn_addr_tap, ssh.username, ssh.host, ssh.port),
                            status = SkillStatus.COMPLETED,
                            sessionId = handle,
                            sessionName = ts.mSessionName ?: "SSH-${ssh.name}",
                            connectionAddress = "${ssh.host}:${ssh.port}"
                        )
                    )
                } else {
                    SkillExecutionResult(false, str(R.string.eng_session_create_failed))
                }
            }
            foundVnc != null -> {
                val vnc = foundVnc
                SkillExecutionResult(true, str(R.string.eng_vnc_card_generated),
                    SkillCardData(
                        skillType = SkillType.CONNECT_REMOTE_CONNECTION,
                        title = str(R.string.eng_vnc_connection_named, vnc.name),
                        description = str(R.string.eng_vnc_conn_addr, vnc.host, vnc.port),
                        status = SkillStatus.COMPLETED,
                        connectionAddress = "${vnc.host}:${vnc.port}"
                    )
                )
            }
            else -> SkillExecutionResult(false, str(R.string.eng_remote_not_found, connectionId))
        }
    }

    // ---- 文件操作 ----

    private fun resolvePath(raw: String?): String {
        if (raw.isNullOrBlank()) return HOME_DIR
        var path = raw.replace("~", HOME_DIR)
        if (!path.startsWith("/")) path = "$HOME_DIR/$path"
        val canonical = File(path).canonicalPath
        // 限制在 /data/data/com.termux/ 范围内
        return if (canonical.startsWith(TERMUX_ROOT)) canonical else HOME_DIR
    }

    private suspend fun execFileList(context: Context, params: JsonObject): SkillExecutionResult {
        val pathParam = if (params.has("path")) params.get("path").asString else ""
        val path = resolvePath(pathParam)
        return try {
            val dir = File(path)
            if (!dir.exists()) return SkillExecutionResult(false, str(R.string.eng_dir_not_found, path))
            if (!dir.isDirectory) return SkillExecutionResult(false, str(R.string.eng_not_a_directory, path))
            val entries = dir.listFiles()?.map { f ->
                val type = if (f.isDirectory) "[D]" else if (f.isFile) "[F]" else "[?]"
                val size = if (f.isFile) humanSize(f.length()) else ""
                "$type ${f.name}  $size"
            }?.sorted() ?: emptyList()
            val output = entries.joinToString("\n").ifBlank { str(R.string.empty) }
            SkillExecutionResult(true, str(R.string.eng_dir_listed, path),
                SkillCardData(
                    skillType = SkillType.FILE_LIST,
                    title = str(R.string.eng_dir_list_title),
                    description = path,
                    status = SkillStatus.COMPLETED,
                    filePath = path,
                    output = output
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_dir_list_failed, e.message ?: ""),
                SkillCardData(
                    skillType = SkillType.FILE_LIST,
                    title = str(R.string.eng_dir_list_failed_title),
                    description = str(R.string.eng_path_error, path, e.message ?: ""),
                    status = SkillStatus.FAILED,
                    filePath = path
                )
            )
        }
    }

    private suspend fun execFileRead(context: Context, params: JsonObject): SkillExecutionResult {
        val pathParam = if (params.has("path")) params.get("path").asString else ""
        if (pathParam.isBlank()) return SkillExecutionResult(false, str(R.string.eng_path_required))
        val path = resolvePath(pathParam)
        return try {
            val file = File(path)
            if (!file.exists()) return SkillExecutionResult(false, str(R.string.eng_file_not_found, path))
            if (!file.isFile) return SkillExecutionResult(false, str(R.string.eng_not_a_file, path))
            if (file.length() > 1024 * 1024) {
                return SkillExecutionResult(false, str(R.string.eng_file_too_large))
            }
            val content = file.readText()
            SkillExecutionResult(true, str(R.string.eng_file_read_ok),
                SkillCardData(
                    skillType = SkillType.FILE_READ,
                    title = str(R.string.eng_file_read_title),
                    description = path,
                    status = SkillStatus.COMPLETED,
                    filePath = path,
                    output = content
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_file_read_failed, e.message ?: ""))
        }
    }

    private suspend fun execFileWrite(context: Context, params: JsonObject): SkillExecutionResult {
        val pathParam = if (params.has("path")) params.get("path").asString else ""
        if (pathParam.isBlank()) return SkillExecutionResult(false, str(R.string.eng_path_required))
        val content = if (params.has("content")) params.get("content").asString else ""
        val append = if (params.has("append")) params.get("append").asBoolean else false
        val path = resolvePath(pathParam)
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            if (append) file.appendText(content) else file.writeText(content)
            SkillExecutionResult(true, if (append) str(R.string.eng_file_append_ok) else str(R.string.eng_file_write_ok),
                SkillCardData(
                    skillType = SkillType.FILE_WRITE,
                    title = if (append) str(R.string.eng_file_append_title) else str(R.string.eng_file_write_title),
                    description = str(R.string.eng_file_written_desc, path, content.length),
                    status = SkillStatus.COMPLETED,
                    filePath = path
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_file_write_failed, e.message ?: ""))
        }
    }

    private suspend fun execFileDelete(context: Context, params: JsonObject): SkillExecutionResult {
        val pathParam = if (params.has("path")) params.get("path").asString else ""
        if (pathParam.isBlank()) return SkillExecutionResult(false, str(R.string.eng_path_required))
        val path = resolvePath(pathParam)
        val canonical = File(path).canonicalPath
        if (canonical == TERMUX_ROOT) return SkillExecutionResult(false, context.getString(R.string.danger_delete_termux_root2))
        if (canonical == HOME_DIR) return SkillExecutionResult(false, context.getString(R.string.danger_delete_home))
        return try {
            val file = File(path)
            if (!file.exists()) return SkillExecutionResult(false, str(R.string.eng_file_not_found, path))
            val ok = file.deleteRecursively()
            if (ok) {
                SkillExecutionResult(true, str(R.string.eng_file_deleted, path),
                    SkillCardData(
                        skillType = SkillType.FILE_DELETE,
                        title = str(R.string.eng_file_deleted_title),
                        description = path,
                        status = SkillStatus.COMPLETED,
                        filePath = path
                    )
                )
            } else {
                SkillExecutionResult(false, str(R.string.eng_delete_failed, path))
            }
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_file_delete_failed, e.message ?: ""))
        }
    }

    // ---- 命令与包（切到 Main 线程 —— 涉及 TerminalSession.write / createTermuxSession，内部用 Handler）----

    private suspend fun execRunCommand(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        val command = if (params.has("command")) params.get("command").asString else ""
        if (command.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_command_empty))
        val sessionId = if (params.has("sessionId")) params.get("sessionId").asString else ""
        val sessionName = if (params.has("sessionName")) params.get("sessionName").asString else null

        // 高危命令二次确认（如果已经由 Agent 流程确认过则跳过）
        // 无限制模式下跳过所有风险确认
        if (!RiskConfirmManager.shouldSkipRiskCheck() && !isUnlimitedMode(context)) {
            val confirmed = RiskConfirmManager.requestConfirmation(context, command)
            if (!confirmed) {
                return@withContext SkillExecutionResult(
                    false,
                    context.getString(com.termux.R.string.access_denied)
                )
            }
        }

        return@withContext try {
            val unlimited = isUnlimitedMode(context)
            // 优先用 sessionId 或 sessionName 查找已有会话
            val lookUpKey = sessionId.ifBlank { sessionName ?: "" }
            if (lookUpKey.isNotBlank()) {
                val sessions = termuxService.getTermuxSessions()
                val target = sessions.find {
                    val ts = it.getTerminalSession()
                    ts.mHandle.toString() == lookUpKey || ts.mSessionName == lookUpKey
                }
                if (target != null) {
                    val ts = target.getTerminalSession()
                    // 确保会话在前台可见
                    val i = Intent(context, TermuxActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    i.putExtra("sessionHandle", ts.mHandle)
                    context.startActivity(i)
                    if (!ts.isRunning) delay(1500)
                    // 写入命令（追加换行）
                    ts.write(command + "\n")
                    SkillExecutionResult(true, if (unlimited) str(R.string.eng_command_auto_executed) else str(R.string.eng_command_card_created),
                        SkillCardData(
                            skillType = SkillType.RUN_COMMAND,
                            title = str(R.string.eng_card_run_command),
                            description = command,
                            status = SkillStatus.COMPLETED,
                            sessionId = ts.mHandle.toString(),
                            sessionName = ts.mSessionName,
                            command = command
                        )
                    )
                } else {
                    SkillExecutionResult(false, str(R.string.eng_session_not_found, lookUpKey))
                }
            } else {
                val newSession = termuxService.createTermuxSession(
                    null, arrayOf("-c", command), null, null, false, sessionName
                )
                if (newSession != null) {
                    val ts = newSession.getTerminalSession()
                    val handle = ts.mHandle.toString()
                    // 无限制模式：自动将会话转到前台
                    if (unlimited) {
                        val i = Intent(context, TermuxActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        i.putExtra("sessionHandle", handle)
                        context.startActivity(i)
                    }
                    SkillExecutionResult(true, if (unlimited) str(R.string.eng_command_auto_executed) else str(R.string.eng_command_card_created),
                        SkillCardData(
                            skillType = SkillType.RUN_COMMAND,
                            title = str(R.string.eng_card_run_command_new_session),
                            description = command,
                            status = SkillStatus.COMPLETED,
                            sessionId = handle,
                            sessionName = ts.mSessionName ?: "Terminal",
                            command = command
                        )
                    )
                } else {
                    SkillExecutionResult(false, str(R.string.eng_session_create_failed))
                }
            }
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_run_command_error, e.message ?: ""))
        }
    }

    /**
     * 在 VorteX 沙箱中预演一条命令。
     *
     * 用途是**危险脚本的预演**：Agent 判断某条命令风险较高时，先在沙箱里跑一遍，
     * 把真实输出拿给用户看，由用户决定是否在真实环境执行。
     *
     * 之所以必须走引导脚本的 `--run` 而不是复用 [execCaptureOutput]：
     * 后者是在真实 Termux 会话里执行，只有 `execRunCommand` 走沙箱才谈得上隔离。
     *
     * 无论成功失败，结束前都会回收影子空间（约 95MB）——沙箱是临时占用空间。
     */
    private suspend fun execRunCommandInSandbox(
        context: Context,
        params: JsonObject
    ): SkillExecutionResult {
        val appCtx = context.applicationContext
        // 总开关或 Agent 授权任一关闭即不可用——不允许绕过授权用沙箱。
        if (!VorteXSandbox.isAgentUsingSandbox(appCtx)) {
            return SkillExecutionResult(
                false,
                "VorteX 沙箱未开启或未授权 Termux Agent 使用，无法在沙箱中预演。" +
                    "请到「设置 → 安全设置 → 授权 Termux Agent 使用沙箱」开启后重试；" +
                    "若只想在真实环境执行，请改用 RUN_COMMAND。"
            )
        }

        val command = try {
            params.get("command").asString
        } catch (_: Exception) {
            return SkillExecutionResult(false, "RUN_COMMAND_SANDBOX 缺少 command 参数")
        }
        if (command.isBlank()) return SkillExecutionResult(false, "command 为空")

        VorteXSandbox.ensureInitialized(appCtx)
        val bootstrap = VorteXSandbox.getBootstrapExecutable(appCtx).absolutePath
        val vortexRoot = VorteXSandbox.getRootDir(appCtx).absolutePath
        val realHome = File(TermuxConstants.TERMUX_HOME_DIR_PATH).absolutePath

        return try {
            // 后台准备影子 prefix：首次要拷 ~95MB，不放这里会让本函数阻塞过久。
            // 预热完成后脚本内部会秒级启动；这里不额外阻塞 UI（Agent 回合内执行）。
            VorteXSandbox.prepareShadowPrefix(appCtx)

            val t0 = System.currentTimeMillis()
            val process = ProcessBuilder(
                File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "bash").absolutePath,
                bootstrap, "--run", command
            ).apply {
                environment()["VORTEX_ROOT"] = vortexRoot
                environment()["VORTEX_REAL_HOME"] = realHome
                redirectErrorStream(true)
            }.start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            val elapsed = System.currentTimeMillis() - t0

            SkillExecutionResult(
                true,
                "已在 VorteX 沙箱中预演完成（${elapsed}ms，退出码 $exitCode）。" +
                    "沙箱内所有改动均未触及真实环境，且已随预演结束清除。" +
                    "请把下面的实际输出如实转述给用户，并明确询问：" +
                    "是否要在此结果基础上，于真实环境执行同一条命令。" +
                    "\n\n--- 沙箱实际输出 ---\n$output",
                SkillCardData(
                    skillType = SkillType.RUN_COMMAND_SANDBOX,
                    title = "沙箱预演结果",
                    description = command,
                    status = SkillStatus.COMPLETED,
                    command = command,
                    output = output
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, "沙箱预演失败: ${e.message}")
        } finally {
            // 一轮预演结束即回收影子空间（含 ~95MB 的 $PREFIX 拷贝）。
            VorteXSandbox.onEphemeralRunEnded(appCtx)
        }
    }

    private suspend fun execPackageInstall(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult {
        if (termuxService == null) return SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        val pkgs = try {
            val arr = params.getAsJsonArray("packages")
            (0 until arr.size()).map { arr[it].asString }
        } catch (_: Exception) { emptyList() }
        if (pkgs.isEmpty()) return SkillExecutionResult(false, str(R.string.eng_no_packages_specified))
        val useContainer = if (params.has("useContainer")) params.get("useContainer").asBoolean else false
        val command = if (useContainer) {
            val runInContainer = "$HOME_DIR/run_in_container.sh"
            "apt-get update && apt-get install -y ${pkgs.joinToString(" ")}"
                .let { if (File(runInContainer).exists()) "bash $runInContainer \"$it\"" else "pkg install -y ${pkgs.joinToString(" ")}" }
        } else {
            "pkg update && pkg install -y ${pkgs.joinToString(" ")}"
        }
        val wrappedParams = JsonObject().apply {
            addProperty("command", command)
            addProperty("sessionName", str(R.string.eng_session_install_name, pkgs.first()))
        }
        return execRunCommand(context, termuxService, wrappedParams)
    }

    private suspend fun execPackageUninstall(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult {
        if (termuxService == null) return SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        val pkgs = try {
            val arr = params.getAsJsonArray("packages")
            (0 until arr.size()).map { arr[it].asString }
        } catch (_: Exception) { emptyList() }
        if (pkgs.isEmpty()) return SkillExecutionResult(false, str(R.string.eng_no_packages_to_remove))
        val command = "pkg remove -y ${pkgs.joinToString(" ")}"
        val wrappedParams = JsonObject().apply {
            addProperty("command", command)
            addProperty("sessionName", str(R.string.eng_session_uninstall_name, pkgs.first()))
        }
        return execRunCommand(context, termuxService, wrappedParams)
    }

    private suspend fun execFileGenerate(context: Context, params: JsonObject): SkillExecutionResult {
        val pathParam = if (params.has("path")) params.get("path").asString else ""
        if (pathParam.isBlank()) return SkillExecutionResult(false, str(R.string.eng_path_required))
        val content = if (params.has("content")) params.get("content").asString else ""
        val path = resolvePath(pathParam)
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            SkillExecutionResult(true, str(R.string.eng_file_generate_ok),
                SkillCardData(
                    skillType = SkillType.FILE_GENERATE,
                    title = str(R.string.eng_file_generate_title),
                    description = str(R.string.eng_file_written_desc, path, content.length),
                    status = SkillStatus.COMPLETED,
                    filePath = path
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_file_generate_failed, e.message ?: ""))
        }
    }

    private suspend fun execFileModify(context: Context, params: JsonObject): SkillExecutionResult {
        val pathParam = if (params.has("path")) params.get("path").asString else ""
        if (pathParam.isBlank()) return SkillExecutionResult(false, str(R.string.eng_path_required))
        val path = resolvePath(pathParam)
        val operations = try {
            val arr = params.getAsJsonArray("operations")
            (0 until arr.size()).map { arr[it].asJsonObject }
        } catch (_: Exception) { emptyList() }
        if (operations.isEmpty()) return SkillExecutionResult(false, str(R.string.eng_no_modify_operations))

        return try {
            val file = File(path)
            if (!file.exists()) return SkillExecutionResult(false, str(R.string.eng_file_not_found, path))
            if (!file.isFile) return SkillExecutionResult(false, str(R.string.eng_not_a_file, path))
            if (file.length() > 1024 * 1024) return SkillExecutionResult(false, str(R.string.eng_file_too_large_modify))

            var content = file.readText()
            val lines = content.lines().toMutableList()

            for (op in operations) {
                when (op.get("type")?.asString) {
                    "replace" -> {
                        val search = op.get("search")?.asString ?: ""
                        val replace = op.get("replace")?.asString ?: ""
                        content = content.replace(search, replace)
                    }
                    "insert" -> {
                        val line = op.get("line")?.asInt ?: 0
                        val insertContent = op.get("content")?.asString ?: ""
                        val safeLine = line.coerceIn(0, lines.size)
                        lines.add(safeLine, insertContent)
                    }
                    "delete" -> {
                        val line = op.get("line")?.asInt ?: 0
                        if (line in 1..lines.size) {
                            lines.removeAt(line - 1)
                        }
                    }
                }
            }

            if (operations.any { it.get("type")?.asString == "insert" || it.get("type")?.asString == "delete" }) {
                content = lines.joinToString("\n")
            }

            file.writeText(content)
            SkillExecutionResult(true, str(R.string.eng_file_modify_ok),
                SkillCardData(
                    skillType = SkillType.FILE_MODIFY,
                    title = str(R.string.eng_file_modify_title),
                    description = str(R.string.eng_file_modify_desc, path, operations.size),
                    status = SkillStatus.COMPLETED,
                    filePath = path,
                    output = content
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_file_modify_failed, e.message ?: ""))
        }
    }

    private suspend fun execAppInstall(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        if (!AiTermuxPrefs.isRootAvailable()) return@withContext SkillExecutionResult(false, str(R.string.eng_app_install_needs_root))
        val apkPath = if (params.has("apkPath")) params.get("apkPath").asString else ""
        if (apkPath.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_no_apk_path))
        val resolvedPath = resolvePath(apkPath)
        val file = File(resolvedPath)
        if (!file.exists()) return@withContext SkillExecutionResult(false, str(R.string.eng_apk_not_found, resolvedPath))

        val command = "su -c pm install -r \"$resolvedPath\""
        val wrappedParams = JsonObject().apply {
            addProperty("command", command)
            addProperty("description", str(R.string.eng_desc_install_apk_root))
        }
        execCaptureOutput(context, termuxService, wrappedParams)
    }

    private suspend fun execAppUninstall(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        if (!AiTermuxPrefs.isRootAvailable()) return@withContext SkillExecutionResult(false, str(R.string.eng_app_uninstall_needs_root))
        val packageName = if (params.has("packageName")) params.get("packageName").asString else ""
        if (packageName.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_no_package_name))

        val command = "su -c pm uninstall \"$packageName\""
        val wrappedParams = JsonObject().apply {
            addProperty("command", command)
            addProperty("description", str(R.string.eng_desc_uninstall_app_root))
        }
        execCaptureOutput(context, termuxService, wrappedParams)
    }

    private suspend fun execCompileCode(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        val command = if (params.has("command")) params.get("command").asString else ""
        if (command.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_compile_no_command))
        val description = if (params.has("description")) params.get("description").asString else str(R.string.eng_compile_default_desc)
        val timeout = if (params.has("timeout")) params.get("timeout").asInt else 60

        val wrappedParams = JsonObject().apply {
            addProperty("command", command)
            addProperty("description", description)
            addProperty("timeout", timeout)
        }
        val result = execCaptureOutput(context, termuxService, wrappedParams)

        // 分析编译结果
        val rawOutput = result.skillCard?.output ?: ""
        val isSuccess = result.success && !rawOutput.contains("error", ignoreCase = true)
        val hasErrors = rawOutput.contains("error:", ignoreCase = true) ||
                rawOutput.contains("Error:", ignoreCase = true) ||
                rawOutput.contains("FAILED", ignoreCase = true)

        val compileStatus = buildString {
            appendLine(str(R.string.eng_compile_header))
            appendLine(str(R.string.eng_compile_project, description))
            appendLine(str(R.string.eng_compile_status_line, if (isSuccess && !hasErrors) str(R.string.eng_compile_status_ok) else str(R.string.eng_compile_status_fail)))
            if (result.message.contains("exit=")) {
                appendLine(str(R.string.eng_compile_exit_code, result.message.substringAfter("exit=").trim()))
            }
            appendLine()

            if (hasErrors) {
                // 提取错误信息
                val errorLines = rawOutput.lines()
                    .filter { it.contains("error:", ignoreCase = true) ||
                            it.contains("Error:", ignoreCase = true) ||
                            it.contains("FAILED", ignoreCase = true) }
                    .take(20)

                if (errorLines.isNotEmpty()) {
                    appendLine(str(R.string.eng_compile_errors_marker))
                    for (line in errorLines) {
                        appendLine("  $line")
                    }
                    appendLine()
                }

                // 提取警告信息（仅显示前5条）
                val warningLines = rawOutput.lines()
                    .filter { it.contains("warning:", ignoreCase = true) ||
                            it.contains("Warning:", ignoreCase = true) }
                    .take(5)
                if (warningLines.isNotEmpty()) {
                    appendLine(str(R.string.eng_compile_warnings_marker, warningLines.size))
                    for (line in warningLines) {
                        appendLine("  $line")
                    }
                    appendLine()
                }
            } else {
                // 成功时显示统计信息
                val warnCount = rawOutput.lines().count {
                    it.contains("warning:", ignoreCase = true)
                }
                if (warnCount > 0) {
                    appendLine(str(R.string.eng_compile_ok_with_warnings, warnCount))
                } else {
                    appendLine(str(R.string.eng_compile_ok_clean))
                }
            }

            appendLine()
            appendLine(str(R.string.eng_compile_full_output))
            appendLine(rawOutput.take(5000))
        }

        SkillExecutionResult(
            success = isSuccess && !hasErrors,
            message = if (isSuccess && !hasErrors) str(R.string.eng_compile_success) else str(R.string.eng_compile_failed),
            skillCard = SkillCardData(
                skillType = SkillType.COMPILE_CODE,
                title = str(R.string.eng_compile_card_title, description),
                description = if (isSuccess && !hasErrors) str(R.string.eng_compile_success) else str(R.string.eng_compile_failed),
                status = if (isSuccess && !hasErrors) SkillStatus.COMPLETED else SkillStatus.FAILED,
                output = compileStatus
            )
        )
    }

    private suspend fun execSubAgent(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        val task = if (params.has("task")) params.get("task").asString else str(R.string.eng_subagent_default_task)
        val instructions = if (params.has("instructions")) params.get("instructions").asString else ""
        val agentContext = if (params.has("context")) params.get("context").asString else ""
        val commandsParam = if (params.has("commands")) params.get("commands").asString else ""

        val fullInstructions = buildString {
            appendLine("# 子 Agent 任务: $task")
            if (agentContext.isNotBlank()) {
                appendLine("## 上下文: $agentContext")
            }
            appendLine("## 指令:")
            appendLine(instructions.ifBlank { "请根据任务描述执行相应操作。" })
        }

        if (commandsParam.isNotBlank()) {
            // 自动执行模式：直接执行指定命令，返回结果
            val script = buildString {
                appendLine("echo '=== ${str(R.string.eng_subagent_start)} ==='")
                appendLine("echo \"${str(R.string.eng_subagent_task_line, task)}\"")
                appendLine("echo '${str(R.string.eng_subagent_task_desc)}'")
                appendLine("cat << 'SUBAGENTEOF'")
                appendLine(fullInstructions)
                appendLine("SUBAGENTEOF")
                appendLine("echo '${str(R.string.eng_subagent_start_exec)}'")
                appendLine(commandsParam)
                appendLine("echo '${str(R.string.eng_subagent_end_exec)}'")
                appendLine("echo \"${str(R.string.eng_subagent_task_done, task)}\"")
            }

            val wrappedParams = JsonObject().apply {
                addProperty("command", script)
                addProperty("description", task)
                addProperty("timeout", 120)
            }
            val result = execCaptureOutput(context, termuxService, wrappedParams)

            // 格式化结果，添加子 Agent 任务摘要
            val formattedOutput = buildString {
                appendLine(str(R.string.eng_subagent_result_header))
                appendLine(str(R.string.eng_subagent_task_line, task))
                appendLine(str(R.string.eng_subagent_status_line, if (result.success) str(R.string.common_success) else str(R.string.common_failed)))
                if (result.message.isNotBlank()) appendLine(str(R.string.eng_subagent_detail_line, result.message))
                appendLine()
                appendLine(str(R.string.eng_subagent_output_marker))
                appendLine(result.skillCard?.output ?: str(R.string.eng_subagent_no_output))
            }

            SkillExecutionResult(
                result.success,
                result.message,
                SkillCardData(
                    skillType = SkillType.SUB_AGENT,
                    title = str(R.string.eng_subagent_card_title, task),
                    description = if (result.success) str(R.string.eng_subagent_done_desc) else str(R.string.eng_subagent_task_failed),
                    status = if (result.success) SkillStatus.COMPLETED else SkillStatus.FAILED,
                    output = formattedOutput
                )
            )
        } else {
            // 非自动执行模式：创建子会话，写入指令
            val script = buildString {
                appendLine("cat > /tmp/sub_agent_task.md << 'SUBAGENTEOF'")
                appendLine(fullInstructions)
                appendLine("SUBAGENTEOF")
                appendLine("echo '=== ${str(R.string.eng_subagent_created_header)} ==='")
                appendLine("echo \"${str(R.string.eng_subagent_task_line, task)}\"")
                appendLine("echo \"${str(R.string.eng_subagent_instructions_written)}\"")
                appendLine("echo '${str(R.string.eng_subagent_reply_hint)}'")
            }

            val wrappedParams = JsonObject().apply {
                addProperty("command", script)
                addProperty("description", task)
                addProperty("sessionName", "SubAgent-$task")
            }
            val result = execCaptureOutput(context, termuxService, wrappedParams)

            SkillExecutionResult(
                result.success,
                result.message,
                SkillCardData(
                    skillType = SkillType.SUB_AGENT,
                    title = str(R.string.eng_subagent_card_title, task),
                    description = str(R.string.eng_subagent_subsession_created),
                    status = SkillStatus.COMPLETED,
                    output = result.skillCard?.output ?: str(R.string.eng_subagent_task_created)
                )
            )
        }
    }

    private suspend fun execSearchAgent(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, "Termux 服务未连接")
        val searchType = if (params.has("searchType")) params.get("searchType").asString else "name"
        val query = if (params.has("query")) params.get("query").asString else ""
        val searchPath = if (params.has("path")) params.get("path").asString else "~"
        val fileType = if (params.has("fileType")) params.get("fileType").asString else ""
        val resolvedPath = resolvePath(searchPath)

        val command = when (searchType) {
            "name" -> {
                val namePattern = if (query.isBlank()) "*" else "*${query}*"
                "find '$resolvedPath' -maxdepth 5 -name '$namePattern' -type f 2>/dev/null | head -200"
            }
            "content" -> {
                val typeFilter = if (fileType.isNotBlank()) "-name '*.$fileType'" else "-type f"
                "find '$resolvedPath' -maxdepth 5 $typeFilter -exec grep -l '$query' {} \\; 2>/dev/null | head -200"
            }
            "type" -> {
                "find '$resolvedPath' -maxdepth 5 -name '*.$fileType' -type f 2>/dev/null | head -200"
            }
            else -> "find '$resolvedPath' -maxdepth 5 -name '*${query}*' -type f 2>/dev/null | head -200"
        }

        val description = when (searchType) {
            "name" -> "搜索文件名: $query"
            "content" -> "搜索文件内容: $query"
            "type" -> "搜索文件类型: .$fileType"
            else -> "搜索: $query"
        }

        val wrappedParams = JsonObject().apply {
            addProperty("command", command)
            addProperty("description", description)
        }
        val result = execCaptureOutput(context, termuxService, wrappedParams)

        // 解析搜索结果并添加摘要
        val rawOutput = result.skillCard?.output ?: ""
        // execCaptureOutput inserta un aviso "(...)" cuando el comando no
        // devuelve salida. Ese aviso salio de un recurso traducible, asi que un
        // startsWith en chino solo filtraba en zh y en en/es se colaba en el
        // conteo de resultados. Se compara contra los propios recursos: asi el
        // filtro es correcto en cualquier locale sin depender del idioma.
        val noOutputNotices = setOf(
            str(R.string.eng_output_empty_completed),
            str(R.string.eng_output_empty_timeout)
        )
        val lines = rawOutput.lines().filter { it.isNotBlank() && it !in noOutputNotices }
        val resultCount = lines.size

        // El nombre del tipo de búsqueda se resuelve FUERA del appendLine: meter
        // `str(...)` dentro de ${...} obliga a escapar comillas dobles en el
        // código de Kotlin y el string queda ilegible.
        val searchTypeLabel = str(
            when (searchType) {
                "name" -> R.string.eng_search_type_name
                "content" -> R.string.eng_search_type_content
                "type" -> R.string.eng_search_type_filetype
                else -> R.string.eng_search_type_generic
            }
        )

        val searchSummary = buildString {
            appendLine(str(R.string.eng_search_summary_header))
            appendLine(str(R.string.eng_search_type_label, searchTypeLabel))
            appendLine(str(R.string.eng_search_path_label, resolvedPath))
            appendLine(str(R.string.eng_search_keyword_label, query.ifBlank { str(R.string.eng_search_all) }))
            if (fileType.isNotBlank()) appendLine(str(R.string.eng_search_filter_type, fileType))
            appendLine(str(R.string.eng_search_result_count, resultCount))
            appendLine()
            if (resultCount > 0) {
                appendLine(str(R.string.eng_search_results_marker))
                appendLine(rawOutput)
                appendLine()
                appendLine(str(R.string.eng_search_analysis_marker))
                when {
                    resultCount == 0 -> appendLine(str(R.string.eng_search_none_found))
                    resultCount <= 10 -> appendLine(str(R.string.eng_search_found_few, resultCount))
                    else -> appendLine(str(R.string.eng_search_found_truncated, resultCount))
                }
            } else {
                appendLine(str(R.string.eng_search_none_found_try))
                appendLine(str(R.string.eng_search_hint_path))
                appendLine(str(R.string.eng_search_hint_broader))
                appendLine(str(R.string.eng_search_hint_exists))
            }
        }

        SkillExecutionResult(
            result.success,
            result.message,
            SkillCardData(
                skillType = SkillType.SEARCH_AGENT,
                title = str(R.string.eng_search_card_title, query),
                description = str(R.string.eng_search_card_count, resultCount),
                status = if (result.success) SkillStatus.COMPLETED else SkillStatus.FAILED,
                output = searchSummary
            )
        )
    }

    private suspend fun execWebSearch(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        val query = if (params.has("query")) params.get("query").asString else ""
        val mode = if (params.has("mode")) params.get("mode").asString else "search"
        if (query.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_web_no_query))
        val maxResults = if (params.has("maxResults")) params.get("maxResults").asInt else 5

        val command = when (mode) {
            "fetch" -> {
                "curl -sL --max-time 30 '$query' 2>/dev/null | head -500"
            }
            else -> {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                "curl -sL --max-time 15 \"https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1\" 2>/dev/null | python3 -c \"" +
                        "import sys,json;" +
                        "d=json.load(sys.stdin);" +
                        "print(d.get('AbstractText','') or d.get('Heading','No results'));" +
                        "for t in d.get('RelatedTopics',[])[:$maxResults]:" +
                        " print(t.get('Text','') if isinstance(t,dict) else t)" +
                        "\" 2>/dev/null || echo '" + str(R.string.eng_web_search_failed) + "'"
            }
        }

        val description = if (mode == "fetch") str(R.string.eng_web_fetch_label, query) else str(R.string.eng_search_card_title, query)
        val wrappedParams = JsonObject().apply {
            addProperty("command", command)
            addProperty("description", description)
        }
        execCaptureOutput(context, termuxService, wrappedParams)
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1fK".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1fM".format(mb)
        return "%.1fG".format(mb / 1024.0)
    }

    /** 点击技能卡片时跳转（会话卡片跳转到对应会话等） */
    fun onSkillCardClick(context: Context, card: SkillCardData) {
        when (card.skillType) {
            SkillType.NEW_SESSION,
            SkillType.CLOSE_SESSION,
            SkillType.RUN_COMMAND,
            SkillType.CAPTURE_OUTPUT,
            SkillType.CUSTOM_COMMAND,
            SkillType.VM_LIST,
            SkillType.APP_INSTALL,
            SkillType.APP_UNINSTALL,
            SkillType.PACKAGE_UNINSTALL,
            SkillType.SUB_AGENT,
            SkillType.SEARCH_AGENT,
            SkillType.WEB_SEARCH,
            SkillType.CONNECT_REMOTE_CONNECTION -> {
                card.sessionId?.let { handle ->
                    try {
                        val intent = Intent(context, TermuxActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra("sessionHandle", handle.toLongOrNull() ?: 0L)
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
                if (card.sessionId.isNullOrBlank()) {
                    card.connectionAddress?.let { addr ->
                        try {
                            val intent = Intent(context, VncActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            val parts = addr.split(":")
                            intent.putExtra("host", parts.getOrNull(0) ?: addr)
                            intent.putExtra("port", parts.getOrNull(1)?.toIntOrNull() ?: 5900)
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }
                }
            }
            SkillType.CONNECT_VNC -> {
                card.connectionAddress?.let { addr ->
                    try {
                        val intent = Intent(context, VncActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val parts = addr.split(":")
                        intent.putExtra("host", parts.getOrNull(0) ?: addr)
                        intent.putExtra("port", parts.getOrNull(1)?.toIntOrNull() ?: 5900)
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            }
            SkillType.RUN_VM_QEMU,
            SkillType.CREATE_VM_QEMU -> {
                val intent = Intent(context, QemuVmActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                card.vmName?.let { intent.putExtra("vmName", it) }
                if (card.skillType == SkillType.CREATE_VM_QEMU) intent.putExtra("createNew", true)
                context.startActivity(intent)
            }
            SkillType.EXIT_TERMUX -> {
                val activity = context as? Activity
                if (activity != null) {
                    activity.finishAffinity()
                } else {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                }
            }
            else -> { }
        }
    }

    // ---- 自定义技能实现 ----

    private suspend fun execOpenActivity(context: Context, params: JsonObject): SkillExecutionResult =
        withContext(Dispatchers.Main.immediate) {
            return@withContext try {
                val activityClass = if (params.has("activityClass")) params.get("activityClass").asString else ""
                if (activityClass.isBlank()) {
                    return@withContext SkillExecutionResult(false, str(R.string.eng_activity_class_empty))
                }
                val extras = if (params.has("extras")) {
                    runCatching { params.getAsJsonObject("extras") }.getOrNull()
                } else null

                val intent = Intent()
                intent.setClassName(context, activityClass)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                extras?.entrySet()?.forEach { (key, value) ->
                    when {
                        value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                            intent.putExtra(key, value.asString)
                        value.isJsonPrimitive && value.asJsonPrimitive.isNumber ->
                            intent.putExtra(key, value.asLong)
                        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean ->
                            intent.putExtra(key, value.asBoolean)
                    }
                }
                context.startActivity(intent)
                SkillExecutionResult(true, str(R.string.eng_activity_opened, activityClass),
                    SkillCardData(
                        skillType = SkillType.CUSTOM_COMMAND,
                        title = str(R.string.eng_card_open_activity),
                        description = activityClass,
                        status = SkillStatus.COMPLETED
                    )
                )
            } catch (e: Exception) {
                SkillExecutionResult(false, str(R.string.eng_open_activity_error, e.message ?: ""))
            }
        }

    private suspend fun execSendBroadcast(context: Context, params: JsonObject): SkillExecutionResult =
        withContext(Dispatchers.Main.immediate) {
            return@withContext try {
                val action = if (params.has("action")) params.get("action").asString else ""
                if (action.isBlank()) {
                    return@withContext SkillExecutionResult(false, str(R.string.eng_broadcast_action_empty))
                }
                val extras = if (params.has("extras")) {
                    runCatching { params.getAsJsonObject("extras") }.getOrNull()
                } else null

                val intent = Intent(action)
                extras?.entrySet()?.forEach { (key, value) ->
                    when {
                        value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                            intent.putExtra(key, value.asString)
                        value.isJsonPrimitive && value.asJsonPrimitive.isNumber ->
                            intent.putExtra(key, value.asLong)
                        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean ->
                            intent.putExtra(key, value.asBoolean)
                    }
                }
                context.sendBroadcast(intent)
                SkillExecutionResult(true, str(R.string.eng_broadcast_sent, action),
                    SkillCardData(
                        skillType = SkillType.CUSTOM_COMMAND,
                        title = str(R.string.eng_card_send_broadcast),
                        description = action,
                        status = SkillStatus.COMPLETED
                    )
                )
            } catch (e: Exception) {
                SkillExecutionResult(false, str(R.string.eng_send_broadcast_error, e.message ?: ""))
            }
        }

    private suspend fun execScheduleTask(context: Context, params: JsonObject): SkillExecutionResult =
        withContext(Dispatchers.IO) {
            val task = if (params.has("task")) params.get("task").asString else ""
            val delayMinutes = if (params.has("delayMinutes")) params.get("delayMinutes").asLong else 0
            val repeat = if (params.has("repeat")) params.get("repeat").asString else "once"
            val command = if (params.has("command")) params.get("command").asString else null

            if (task.isBlank()) {
                return@withContext SkillExecutionResult(false, str(R.string.eng_schedule_missing_task))
            }

            val prefs: SharedPreferences = context.getSharedPreferences("ai_scheduled_tasks", Context.MODE_PRIVATE)
            val taskId = System.currentTimeMillis().toString()
            val taskJson = """{"id":"$taskId","task":"$task","delayMinutes":$delayMinutes,"repeat":"$repeat","command":${if (command != null) "\"$command\"" else "null"},"createdAt":${System.currentTimeMillis()}}"""

            prefs.edit().putString("task_$taskId", taskJson).apply()

            val repeatLabel = when (repeat) {
                "hourly" -> str(R.string.eng_repeat_hourly)
                "daily" -> str(R.string.eng_repeat_daily)
                else -> str(R.string.eng_repeat_once)
            }
            val delayLabel = if (delayMinutes > 0) str(R.string.eng_delay_minutes, delayMinutes) else str(R.string.eng_delay_now)

            SkillExecutionResult(true,
                str(R.string.eng_schedule_created, task, delayLabel, repeatLabel),
                SkillCardData(
                    skillType = SkillType.SCHEDULE_TASK,
                    title = str(R.string.eng_card_scheduled_task),
                    description = "$delayLabel · $repeatLabel · $task",
                    status = SkillStatus.COMPLETED
                )
            )
        }

    private suspend fun execGetCurrentSession(
        context: Context,
        termuxService: TermuxService?
    ): SkillExecutionResult = withContext(Dispatchers.Main.immediate) {
        if (termuxService == null) return@withContext SkillExecutionResult(false, str(R.string.eng_service_not_connected))
        return@withContext try {
            val sessions = termuxService.getTermuxSessions()
            val activity = context as? Activity
            val currentHandle = activity?.intent?.getLongExtra("sessionHandle", -1L) ?: -1L
            val currentSession = sessions.find {
                it.getTerminalSession().mHandle.toString() == currentHandle.toString()
            }
            val currentInfo = if (currentSession != null) {
                val ts = currentSession.getTerminalSession()
                str(R.string.eng_session_line_active, ts.mSessionName ?: "Terminal", ts.mHandle.toString(), ts.isRunning.toString())
            } else {
                str(R.string.eng_no_specific_session)
            }
            val allInfo = sessions.joinToString("\n") {
                val ts = it.getTerminalSession()
                val marker = if (ts.mHandle.toString() == currentHandle.toString()) str(R.string.eng_marker_current) else ""
                str(R.string.eng_session_line, ts.mSessionName ?: "Terminal", ts.mHandle.toString(), ts.isRunning.toString()) + marker
            }
            val output = if (sessions.isEmpty()) str(R.string.eng_no_running_sessions)
                         else str(R.string.eng_current_session_report, currentInfo, allInfo)
            SkillExecutionResult(true, output,
                SkillCardData(
                    skillType = SkillType.GET_CURRENT_SESSION,
                    title = str(R.string.eng_current_session_title),
                    description = if (currentSession != null) currentSession.getTerminalSession().mSessionName ?: "Terminal"
                                   else str(R.string.eng_no_active_sessions),
                    status = SkillStatus.COMPLETED,
                    output = output,
                    sessionId = currentHandle.toString().takeIf { it != "-1" }
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_current_session_error, e.message ?: ""))
        }
    }

    private suspend fun execClipboardRead(context: Context): SkillExecutionResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (text.isBlank()) {
                SkillExecutionResult(true, str(R.string.eng_clipboard_empty),
                    SkillCardData(
                        skillType = SkillType.CLIPBOARD_READ,
                        title = str(R.string.eng_card_clipboard),
                        description = str(R.string.eng_clipboard_currently_empty),
                        status = SkillStatus.COMPLETED,
                        output = str(R.string.eng_clipboard_empty_paren),
                        clipboardContent = ""
                    )
                )
            } else {
                val truncated = if (text.length > 5000) text.take(5000) + "..." + str(R.string.eng_truncated_short) else text
                SkillExecutionResult(true, str(R.string.eng_clipboard_read),
                    SkillCardData(
                        skillType = SkillType.CLIPBOARD_READ,
                        title = str(R.string.eng_card_clipboard_content),
                        description = str(R.string.eng_length_chars, text.length),
                        status = SkillStatus.COMPLETED,
                        output = truncated,
                        clipboardContent = text
                    )
                )
            }
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_clipboard_read_failed, e.message ?: ""))
        }
    }

    private suspend fun execClipboardWrite(
        context: Context,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        val content = if (params.has("content")) params.get("content").asString else ""
        if (content.isBlank()) return@withContext SkillExecutionResult(false, str(R.string.eng_clipboard_content_empty))
        return@withContext try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Termux Agent", content))
            SkillExecutionResult(true, str(R.string.eng_clipboard_written),
                SkillCardData(
                    skillType = SkillType.CLIPBOARD_WRITE,
                    title = str(R.string.eng_clipboard_written),
                    description = str(R.string.eng_length_chars, content.length),
                    status = SkillStatus.COMPLETED,
                    clipboardWriteContent = content.take(200)
                )
            )
        } catch (e: Exception) {
            SkillExecutionResult(false, str(R.string.eng_clipboard_write_failed, e.message ?: ""))
        }
    }

    private suspend fun execGetDeviceStatus(
        context: Context,
        termuxService: TermuxService?,
        params: JsonObject
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        val infoType = if (params.has("infoType")) params.get("infoType").asString else "all"

        val build = StringBuilder()
        var hasAnyData = false

        if (infoType == "battery" || infoType == "all") {
            val batteryInfo = queryBatteryStatus(context)
            if (batteryInfo.isNotBlank()) hasAnyData = true
            build.appendLine(str(R.string.eng_device_status_section_battery))
            build.appendLine(batteryInfo.ifBlank { str(R.string.eng_status_unavailable) })
            build.appendLine()
        }

        if (infoType == "network" || infoType == "all") {
            val networkInfo = queryNetworkStatus(context)
            if (networkInfo.isNotBlank()) hasAnyData = true
            build.appendLine(str(R.string.eng_device_status_section_network))
            build.appendLine(networkInfo.ifBlank { str(R.string.eng_status_unavailable) })
            build.appendLine()
        }

        if (infoType == "location" || infoType == "all") {
            val locationInfo = queryLocationStatus(context)
            if (locationInfo.isNotBlank()) hasAnyData = true
            build.appendLine(str(R.string.eng_device_status_section_location))
            build.appendLine(locationInfo.ifBlank { str(R.string.eng_status_unavailable_location_permission) })
            build.appendLine()
        }

        if (infoType != "battery" && infoType != "network" && infoType != "location" && infoType != "all") {
            return@withContext SkillExecutionResult(
                false,
                str(R.string.eng_device_status_unknown_info_type, infoType)
            )
        }

        val result = build.toString().trimEnd()

        SkillExecutionResult(
            success = hasAnyData,
            message = if (hasAnyData) str(R.string.eng_device_status_query_ok) else str(R.string.eng_device_status_query_empty),
            SkillCardData(
                skillType = SkillType.GET_DEVICE_STATUS,
                title = str(R.string.eng_device_status_title),
                description = result.take(300),
                output = result,
                status = if (hasAnyData) SkillStatus.COMPLETED else SkillStatus.FAILED
            )
        )
    }

    private fun queryBatteryStatus(context: Context): String {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent == null) return str(R.string.eng_battery_info_unavailable)

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)

            val pct = if (level >= 0 && scale > 0) "%.1f%%".format(level * 100.0 / scale) else str(R.string.main_state_unknown)
            val statusStr = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> str(R.string.eng_battery_status_charging)
                BatteryManager.BATTERY_STATUS_DISCHARGING -> str(R.string.eng_battery_status_discharging)
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> str(R.string.eng_battery_status_not_charging)
                BatteryManager.BATTERY_STATUS_FULL -> str(R.string.eng_battery_status_full)
                else -> str(R.string.main_state_unknown)
            }
            val pluggedStr = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_AC -> str(R.string.eng_battery_power_ac)
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> str(R.string.eng_battery_power_wireless)
                else -> str(R.string.eng_battery_power_none)
            }

            buildString {
                appendLine(str(R.string.eng_battery_level_line, pct))
                appendLine(str(R.string.eng_battery_status_line, statusStr))
                appendLine(str(R.string.eng_battery_power_line, pluggedStr))
                if (temperature >= 0) appendLine(str(R.string.eng_battery_temp_line, temperature / 10.0))
                if (voltage >= 0) appendLine(str(R.string.eng_battery_voltage_line, voltage))
                if (!technology.isNullOrBlank()) appendLine(str(R.string.eng_battery_tech_line, technology))
            }
        } catch (e: Exception) {
            str(R.string.eng_battery_query_failed, e.message ?: "")
        }
    }

    @Suppress("DEPRECATION")
    private fun queryNetworkStatus(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            if (caps == null) return str(R.string.eng_network_no_connection)

            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> str(R.string.eng_network_type_cellular)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> str(R.string.eng_network_type_ethernet)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> str(R.string.eng_network_type_bluetooth)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> str(R.string.eng_network_type_other)
            }
            val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val yesNo = { on: Boolean ->
                if (on) str(R.string.action_yes) else str(R.string.action_no)
            }

            buildString {
                appendLine(str(R.string.eng_network_type_line, type))
                appendLine(str(R.string.eng_network_connected_line, yesNo(connected)))
                appendLine(str(R.string.eng_network_available_line, yesNo(validated)))
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                    appendLine(str(R.string.eng_network_unmetered_line, yesNo(true)))
            }
        } catch (e: Exception) {
            str(R.string.eng_network_query_failed, e.message ?: "")
        }
    }

    private fun queryLocationStatus(context: Context): String {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            buildString {
                appendLine(str(R.string.eng_location_gps_line, switchState(gpsEnabled)))
                appendLine(str(R.string.eng_location_network_toggle_line, switchState(networkEnabled)))

                val lastKnown = try {
                    lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } catch (_: SecurityException) { null }

                if (lastKnown != null) {
                    appendLine(str(R.string.eng_location_last_line, lastKnown.latitude, lastKnown.longitude))
                    appendLine(str(R.string.eng_location_accuracy_line, lastKnown.accuracy))
                    val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(lastKnown.time))
                    appendLine(str(R.string.eng_location_time_line, time))
                } else {
                    appendLine(str(R.string.eng_location_no_known))
                }
            }
        } catch (e: SecurityException) {
            str(R.string.eng_location_permission_missing)
        } catch (e: Exception) {
            str(R.string.eng_location_query_failed, e.message ?: "")
        }
    }
}

/** ---------- AI API 调用器 ---------- */

object AiApiClient {

    /**
     * Texto localizado de un recurso para los mensajes de error que llegan
     * al usuario. Mismo criterio que el `str()` de SkillExecutor: se resuelve
     * con el context que AiLocalModel.init() guarda desde la Activity.
     */
    private fun str(@StringRes res: Int, vararg args: Any): String {
        val c = AiLocalModel.context() ?: return "-"
        return if (args.isEmpty()) c.getString(res) else c.getString(res, *args)
    }

    /** 在线获取可用模型列表（GET {baseUrl}/models） */
    suspend fun fetchOnlineModels(baseUrl: String, apiKey: String): Pair<List<String>, String?> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("${baseUrl.trimEnd('/')}/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }
            connection = conn
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                return@withContext Pair(emptyList(), err)
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            val json = gson.fromJson(body, Map::class.java)
            val data = (json["data"] as? List<*>) ?: emptyList<Any>()
            val models = data.mapNotNull { item ->
                val m = item as? Map<*, *>
                (m?.get("id") as? String)
            }
            Pair(models, null)
        } catch (e: Exception) {
            Pair(emptyList(), e.message ?: str(R.string.eng_unknown_error))
        } finally {
            // 只 close 流不 disconnect 的话，keep-alive 连接不会及时回收
            connection?.disconnect()
        }
    }

    /** 内部：在线 HTTP 非流式调用（供 chat() 与 fallback 复用） */
    private suspend fun callOnlineChat(
        config: AiProviderConfig,
        messages: List<OpenAiMessage>
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val baseUrl = config.apiBaseUrl.trimEnd('/')
            val url = URL("$baseUrl/chat/completions")
            val bodyObj = ChatCompletionRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                max_tokens = config.maxTokens
            )
            val body = Gson().toJson(bodyObj)

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 60_000
                readTimeout = 120_000
                setRequestProperty("Content-Type", "application/json")
                if (config.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                }
                doOutput = true
                doInput = true
            }
            connection = conn

            DataOutputStream(conn.outputStream).use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val respText = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            try {
                Gson().fromJson(respText, ChatCompletionResponse::class.java)
                    ?: ChatCompletionResponse(error = ChatCompletionResponse.ApiError(str(R.string.eng_empty_response)))
            } catch (parseE: Exception) {
                ChatCompletionResponse(
                    error = ChatCompletionResponse.ApiError(
                        str(R.string.eng_parse_response_failed, code.toString(), respText.take(200))
                    )
                )
            }
        } catch (e: Exception) {
            ChatCompletionResponse(
                error = ChatCompletionResponse.ApiError(
                    str(R.string.eng_request_failed, e.message ?: str(R.string.eng_unknown_error))
                )
            )
        } finally {
            // 与流式路径保持一致：只 close 流不 disconnect 会让连接留在 keep-alive 池里
            connection?.disconnect()
        }
    }

    /** 内部：在线 SSE 流式调用（供 chatStream() 与 fallback 复用） */
    private fun callOnlineChatStream(
        config: AiProviderConfig,
        messages: List<OpenAiMessage>,
        isCancelled: () -> Boolean
    ): Flow<StreamChunk> = flow {
        try {
            val baseUrl = config.apiBaseUrl.trimEnd('/')
            val url = URL("$baseUrl/chat/completions")
            val bodyObj = ChatCompletionRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                stream = true,
                max_tokens = config.maxTokens
            )
            val body = Gson().toJson(bodyObj)

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 60_000
                readTimeout = 120_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                if (config.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                }
                doOutput = true
                doInput = true
            }

            DataOutputStream(conn.outputStream).use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errorText = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                emit(StreamChunk.Error("HTTP $code: ${errorText.take(200)}"))
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            var line: String?
            var fullContent = StringBuilder()
            var fullReasoning = StringBuilder()
            val rawResponse = StringBuilder()
            var hasContent = false
            var inThinkBlock = false
            var thinkTagBuffer = StringBuilder()
            var hasSentReasoningDone = false

            while (true) {
                if (isCancelled()) {
                    conn.disconnect()
                    emit(StreamChunk.Cancelled)
                    break
                }
                line = reader.readLine()
                if (line == null) {
                    android.util.Log.d("AiTermux", "Connection closed: contentLen=${fullContent.length}, reasoningLen=${fullReasoning.length}")
                    emit(StreamChunk.Done(
                        fullContent.toString(),
                        fullReasoning.toString(),
                        rawResponse.toString()
                    ))
                    break
                }
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (!trimmed.startsWith("data:")) continue
                val dataStr = trimmed.removePrefix("data:").trim()
                rawResponse.append(line).append("\n")

                if (dataStr == "[DONE]") {
                    android.util.Log.d("AiTermux", "[DONE] -> Done (hasContent=$hasContent, reasoningLen=${fullReasoning.length})")
                    if (fullReasoning.isNotEmpty() && !hasSentReasoningDone) {
                        emit(StreamChunk.ReasoningDone)
                        hasSentReasoningDone = true
                    }
                    emit(StreamChunk.Done(
                        fullContent.toString(),
                        fullReasoning.toString(),
                        rawResponse.toString()
                    ))
                    break
                }

                try {
                    val json = JsonParser.parseString(dataStr).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val delta = choices.get(0).asJsonObject.getAsJsonObject("delta")
                        val reasoningElement = delta?.get("reasoning_content")
                        val reasoningContent = if (reasoningElement != null && !reasoningElement.isJsonNull) {
                            reasoningElement.asString?.takeIf { it.isNotBlank() }
                        } else null

                        if (reasoningContent != null) {
                            fullReasoning.append(reasoningContent)
                            emit(StreamChunk.Reasoning(reasoningContent))
                        }

                        val contentPart = delta?.get("content")?.asString
                        if (contentPart != null && contentPart.isNotBlank()) {
                            android.util.Log.d("AiTermux", "content delta (len=${contentPart.length}): ${contentPart.take(80)}${if (contentPart.length > 80) "..." else ""}")

                            if (reasoningContent != null) {
                                if (fullReasoning.isNotEmpty() && !hasSentReasoningDone) {
                                    hasSentReasoningDone = true
                                    emit(StreamChunk.ReasoningDone)
                                }
                                fullContent.append(contentPart)
                                hasContent = true
                                emit(StreamChunk.Content(contentPart))
                            } else {
                                val processed = processContentDelta(contentPart, inThinkBlock, thinkTagBuffer)
                                inThinkBlock = processed.inThink
                                if (processed.reasoning.isNotEmpty()) {
                                    fullReasoning.append(processed.reasoning)
                                    emit(StreamChunk.Reasoning(processed.reasoning))
                                }
                                if (processed.reasoningDone) {
                                    hasSentReasoningDone = true
                                    emit(StreamChunk.ReasoningDone)
                                }
                                if (processed.content.isNotEmpty()) {
                                    fullContent.append(processed.content)
                                    hasContent = true
                                    emit(StreamChunk.Content(processed.content))
                                }
                            }
                        }

                        val finishReason = choices.get(0).asJsonObject.get("finish_reason")?.asString
                        if (finishReason != null && finishReason != "null") {
                            android.util.Log.d("AiTermux", "finish_reason=$finishReason, hasContent=$hasContent")
                            if (fullReasoning.isNotEmpty() && !hasSentReasoningDone) {
                                emit(StreamChunk.ReasoningDone)
                                hasSentReasoningDone = true
                            }
                            emit(StreamChunk.Done(
                                fullContent.toString(),
                                fullReasoning.toString(),
                                rawResponse.toString(),
                                finishReason
                            ))
                            break
                        }
                    }
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            emit(StreamChunk.Error(str(R.string.eng_request_failed, e.message ?: str(R.string.eng_unknown_error))))
        }
    }.flowOn(Dispatchers.IO)

    // ---------- 公开 API ----------

    suspend fun chat(
        context: android.content.Context,
        config: AiProviderConfig,
        messages: List<OpenAiMessage>
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        if (config.provider == "local") {
            val localResp = AiLocalModel.completeLocal(config, messages)
            if (localResp.error == null) return@withContext localResp
            val canFallback = AiTermuxPrefs.isFallbackOnlineEnabled(context)
                && AiTermuxPrefs.isFallbackOnlineConfigReady(context)
            if (!canFallback) return@withContext localResp
            val fbCfg = AiTermuxPrefs.getFallbackOnlineConfig(context)
            val fbProvider = AiProviderConfig(
                provider = "custom",
                apiKey = fbCfg.apiKey,
                apiBaseUrl = fbCfg.baseUrl,
                model = fbCfg.model,
                temperature = fbCfg.temperature
            )
            val onlineResp = callOnlineChat(fbProvider, messages)
            if (onlineResp.error == null) {
                val warnText = str(R.string.eng_local_fallback_warn, localResp.error!!.message) + "\n\n"
                val newChoices = onlineResp.choices.map { c ->
                    c.copy(message = c.message?.copy(content = warnText + (c.message?.content ?: "")))
                }
                return@withContext onlineResp.copy(choices = newChoices)
            } else {
                val mergedErr = str(
                    R.string.eng_local_fallback_failed, localResp.error!!.message, onlineResp.error.message
                )
                return@withContext ChatCompletionResponse(error = ChatCompletionResponse.ApiError(mergedErr))
            }
        }
        callOnlineChat(config, messages)
    }

    fun chatStream(
        context: android.content.Context,
        config: AiProviderConfig,
        messages: List<OpenAiMessage>,
        isCancelled: () -> Boolean,
        forceLocal: Boolean = false,
        forceOnline: Boolean = false
    ): Flow<StreamChunk> {
        val useLocal = if (forceOnline) false else if (forceLocal) true else config.provider == "local"
        if (useLocal) {
            val engineType = AiTermuxPrefs.getLocalEngineType(context)
            return flow {
                var localErrMsg: String? = null
                var localFinishedOk = false
                var contentEmitted = false

                // 根据引擎类型选择推理方式
                val localFlow = if (engineType == "ollama") {
                    val ollamaModel = AiTermuxPrefs.getSelectedOllamaModel(context)
                    if (ollamaModel.isBlank()) {
                        // 检查是否有已安装的模型
                        val installedModels = AiTermuxPrefs.getInstalledOllamaModels(context)
                        if (installedModels.isNotEmpty()) {
                            // 自动选择第一个已安装的模型
                            AiTermuxPrefs.setSelectedOllamaModel(context, installedModels.first())
                            emit(StreamChunk.Prepare(
                                str(R.string.eng_ollama_auto_selected, installedModels.first()), null
                            ))
                            AiOllamaManager.chatStreamOllama(installedModels.first(), messages, config.temperature, isCancelled)
                        } else {
                            emit(StreamChunk.Error(str(R.string.eng_ollama_no_model)))
                            return@flow
                        }
                    } else {
                        // 检查 Ollama 服务是否已安装
                        if (!AiOllamaManager.isOllamaInstalled()) {
                            emit(StreamChunk.Error(str(R.string.eng_ollama_not_installed_chat)))
                            return@flow
                        }
                        AiOllamaManager.chatStreamOllama(ollamaModel, messages, config.temperature, isCancelled)
                    }
                } else {
                    AiLocalModel.chatStreamLocal(config, messages, isCancelled)
                }

                localFlow.collect { chunk ->
                    when (chunk) {
                        is StreamChunk.Error -> localErrMsg = chunk.message
                        is StreamChunk.Done -> {
                            localFinishedOk = true
                            emit(chunk)
                        }
                        is StreamChunk.Content -> {
                            contentEmitted = true
                            emit(chunk)
                        }
                        else -> emit(chunk)
                    }
                }
                if (localFinishedOk) return@flow
                if (!contentEmitted && localErrMsg == null) {
                    android.util.Log.w("AiTermuxEngine", "Local flow completed without content or error - may be abnormal")
                    localErrMsg = str(R.string.eng_local_no_output)
                }
                if (localErrMsg == null) return@flow
                val canFallback = !forceLocal && AiTermuxPrefs.isFallbackOnlineEnabled(context)
                    && AiTermuxPrefs.isFallbackOnlineConfigReady(context)
                if (!canFallback) {
                    emit(StreamChunk.Error(localErrMsg!!))
                    return@flow
                }
                val fbCfg = AiTermuxPrefs.getFallbackOnlineConfig(context)
            val fbProvider = AiProviderConfig(
                provider = "custom",
                apiKey = fbCfg.apiKey,
                apiBaseUrl = fbCfg.baseUrl,
                model = fbCfg.model,
                temperature = fbCfg.temperature
            )
                val warnText = str(R.string.eng_local_fallback_warn_stream, localErrMsg) + "\n\n"
                emit(StreamChunk.Content(warnText))
                callOnlineChatStream(fbProvider, messages, isCancelled).collect { inner ->
                    emit(inner)
                }
            }.flowOn(Dispatchers.IO)
        }
        return callOnlineChatStream(config, messages, isCancelled)
    }
}

/**
 * 处理 content 字段中的 <think> 标签。
 * 模型可能把思考内容放在 content 字段里，用 <think> 和 </think> 标签包装。
 * 此函数将内容分离为思考部分和实际回复部分。
 */
private data class ProcessedContent(
    val reasoning: String = "",
    val content: String = "",
    val reasoningDone: Boolean = false,
    val inThink: Boolean = false
)

private fun processContentDelta(
    delta: String,
    inThink: Boolean,
    tagBuffer: StringBuilder
): ProcessedContent {
    var reasoning = StringBuilder()
    var content = StringBuilder()
    var currentInThink = inThink
    var reasoningDone = false
    var remaining = delta

    // 如果之前有未完成的标签（如 "<thi" 被截断），先拼接
    if (tagBuffer.isNotEmpty()) {
        remaining = tagBuffer.toString() + remaining
        tagBuffer.clear()
    }

    while (remaining.isNotEmpty()) {
        if (currentInThink) {
            // 正在思考区域，查找 </think> 结束标签
            val endIdx = remaining.indexOf("</think>")
            if (endIdx >= 0) {
                // 找到结束标签
                val thinkPart = remaining.substring(0, endIdx)
                if (thinkPart.isNotEmpty()) {
                    reasoning.append(thinkPart)
                }
                currentInThink = false
                reasoningDone = true
                remaining = remaining.substring(endIdx + 7) // 跳过 </think>
                // 跳过标签后的空白
                remaining = remaining.trimStart()
                if (remaining.isEmpty()) break
            } else {
                // 检查是否是被截断的 </think> 标签（以 "<" 开头且与 "</think" 前缀匹配）
                if (remaining.startsWith("<") && "</think".startsWith(remaining)) {
                    // 可能是被截断的 </think> 标签，保存到缓冲区
                    tagBuffer.append(remaining)
                    remaining = ""
                } else {
                    reasoning.append(remaining)
                    remaining = ""
                }
            }
        } else {
            // 不在思考区域，查找 <think> 开始标签
            val startIdx = remaining.indexOf("<think>")
            if (startIdx >= 0) {
                // 找到开始标签
                if (startIdx > 0) {
                    content.append(remaining.substring(0, startIdx))
                }
                currentInThink = true
                remaining = remaining.substring(startIdx + 7) // 跳过 <think>
                remaining = remaining.trimStart()
                if (remaining.isEmpty()) break
            } else {
                // 检查是否是被截断的 <think> 标签（以 "<" 开头且与 "<think" 前缀匹配）
                if (remaining.startsWith("<") && "<think".startsWith(remaining)) {
                    // 可能是被截断的 <think> 标签，保存到缓冲区
                    tagBuffer.append(remaining)
                    remaining = ""
                } else {
                    content.append(remaining)
                    remaining = ""
                }
            }
        }
    }

    return ProcessedContent(
        reasoning = reasoning.toString(),
        content = content.toString(),
        reasoningDone = reasoningDone,
        inThink = currentInThink
    )
}

/** 流式响应的分片类型 */
sealed class StreamChunk {
    data class Content(val delta: String) : StreamChunk()
    data class Reasoning(val delta: String) : StreamChunk()
    data object ReasoningDone : StreamChunk()
    /** 本地模型准备阶段（加载模型、启动进程等），一旦有 Reasoning 或 Content 到达就自动清除 UI */
    data class Prepare(val status: String, val detailLine: String? = null) : StreamChunk()
    data class Done(val fullText: String, val fullReasoning: String = "", val rawResponse: String = "", val finishReason: String? = null) : StreamChunk()
    data class Error(val message: String) : StreamChunk()
    object Cancelled : StreamChunk()
}
