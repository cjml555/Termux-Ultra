package com.termux.app.compose


import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.termux.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

import com.termux.app.compose.pagePaddingWithoutTop

// Claves internas de estilo para las tarjetas de paso. No son texto visible:
// el encabezado visible sale de un recurso, así que el color no puede deducirse
// del texto (dependería del idioma activo).
private const val STEP_KIND = "step"
private const val QUESTION_KIND = "question"
private const val ANSWER_KIND = "answer"
private const val CRITIQUE_KIND = "critique"
private const val ROUND_DONE_KIND = "round_done"
private const val RATING_KIND = "rating"
private const val ERROR_KIND = "error"
private const val PAUSED_KIND = "paused"

@Composable
fun AiLocalTrainerScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val onlineReady = remember { mutableStateOf(false) }
    val hasLocal = remember { mutableStateOf(AiTermuxPrefs.getConfig(ctx).providerConfig.provider == "local") }
    // 本页在 MainScreen 取景层之外，自建一层供玻璃顶栏折射页面内容
    val glassPage = rememberGlassPageBackdrop()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(Unit) {
        onlineReady.value = AiTermuxPrefs.isFallbackOnlineConfigReady(ctx)
    }

    KiTerminalTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                GlassTopAppBar(
                    title = stringResource(R.string.train_local_model),
                    backdrop = glassPage.backdrop,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        GlassIconButton(onClick = { onBack() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back),
                                modifier = Modifier.size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        ) { padding ->
            if (!hasLocal.value) {
                // 居中提示按整屏居中，让位交给 pagePaddingWithoutTop 之外的那一份顶部空间。
                NoLocalModelHint(Modifier.padding(top = topBarClearance(padding)))
            } else {
                TrainerBody(
                    Modifier
                        .fillMaxSize()
                        // 顶部的 TabBar 是固定条，必须停在玻璃顶栏下方；各 tab 的列表仍会滚到顶栏之下。
                        .padding(top = topBarClearance(padding))
                        .padding(pagePaddingWithoutTop(padding))
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ctx, onlineReady
                )
            }
        }
    }
}

@Composable
private fun NoLocalModelHint(modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.trainer_no_model), fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.trainer_no_model_hint), fontSize = 12.sp)
    }
}

// ========== 主体 ==========
@Composable
private fun TrainerBody(
    modifier: Modifier,
    ctx: Context,
    onlineReady: MutableState<Boolean>
) {
    val scope = rememberCoroutineScope()
    val session = remember { mutableStateOf(AiTermuxPrefs.getLastTrainSession(ctx) ?: LocalTrainSession()) }
    val steps = remember { mutableStateListOf<Triple<Int, String, String>>() }
    val waitingStartText = stringResource(R.string.trainer_waiting_start)
    val notStartedText = stringResource(R.string.trainer_not_started)
    val pausedText = stringResource(R.string.trainer_paused_resume)
    val etaText = remember { mutableStateOf(waitingStartText) }
    val currentTab = rememberSaveable { mutableStateOf(0) }
    val statusMsg = remember { mutableStateOf(session.value.status.ifBlank { notStartedText }) }
    val waitingRating = remember { mutableStateOf<LocalTrainerEvent.WaitingForUserRating?>(null) }
    val refreshTeacherChat = remember { mutableStateOf(0) }

    var job by remember { mutableStateOf<Job?>(null) }
    val cancelled = remember { mutableStateOf(false) }

    fun startOrResume() {
        if (job?.isActive == true) return
        if (session.value.status == "finished" || session.value.rounds.size >= session.value.targetRounds) {
            session.value = LocalTrainSession(targetRounds = session.value.targetRounds)
            AiTermuxPrefs.saveLastTrainSession(ctx, session.value)
        }
        if (session.value.teacher.isBlank()) {
            session.value = session.value.copy(teacher = if (onlineReady.value) "online_fallback" else "manual")
        }
        cancelled.value = false
        job = scope.launch(Dispatchers.IO) {
            AiLocalTrainer.runTraining(ctx, session.value) { cancelled.value }.collect { evt ->
                withContext(Dispatchers.Main.immediate) {
                    handleTrainerEvent(evt, steps, etaText, statusMsg, session, waitingRating, ctx)
                }
            }
        }
    }

    fun pause() {
        cancelled.value = true
        session.value.status = "paused"
        AiTermuxPrefs.saveLastTrainSession(ctx, session.value)
        statusMsg.value = pausedText
        scope.launch {
            kotlinx.coroutines.delay(80)
            job?.cancelAndJoin()
            job = null
        }
    }

    fun resetAll() {
        scope.launch { job?.cancelAndJoin(); job = null }
        session.value = LocalTrainSession(targetRounds = session.value.targetRounds)
        steps.clear()
        etaText.value = waitingStartText
        statusMsg.value = notStartedText
        waitingRating.value = null
        AiTermuxPrefs.saveLastTrainSession(ctx, session.value)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_DESTROY) scope.launch { job?.cancelAndJoin(); job = null }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        TabBar(currentTab)
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f)) {
            when (currentTab.value) {
                0 -> StepsTab(
                    steps = steps,
                    session = session.value,
                    jobActive = job?.isActive == true,
                    statusMsg = statusMsg.value,
                    etaText = etaText.value,
                    onlineReady = onlineReady.value,
                    onTargetRoundsChange = { n ->
                        session.value = session.value.copy(targetRounds = n)
                        AiTermuxPrefs.saveLastTrainSession(ctx, session.value)
                    },
                    onTeacherToggle = { t -> session.value = session.value.copy(teacher = t) },
                    onStart = { startOrResume() }, onPause = { pause() }, onReset = { resetAll() },
                    onClearMemory = { AiTermuxPrefs.clearLearnedMemory(ctx) },
                    onClearTeacherChat = { 
                        AiLocalTrainer.clearTeacherChatHistory(ctx)
                        refreshTeacherChat.value += 1
                    }
                )
                1 -> ConversationTab(session)
                2 -> TeacherChatTab(ctx, onlineReady, refreshTeacherChat.value)
                3 -> LessonsTab(ctx, refreshTeacherChat.value)
                else -> TeacherChatTab(ctx, onlineReady, refreshTeacherChat.value)
            }
        }
    }

    ManualRatingDialog(
        data = waitingRating.value ?: LocalTrainerEvent.WaitingForUserRating(0, "", "", 10.0, 60.0, "", ""),
        show = waitingRating.value != null,
        setShow = { show -> if (!show) waitingRating.value = null },
        onConfirm = { score, critique, patch ->
            val data = waitingRating.value ?: return@ManualRatingDialog
            AiLocalTrainer.provideUserRating(data.roundIndex, data.suggestedMaxScore, score, critique, patch)
            waitingRating.value = null
        },
        onDismiss = {
            // 用户选择跳过，用启发式建议继续
            val data = waitingRating.value ?: return@ManualRatingDialog
            AiLocalTrainer.provideUserRating(data.roundIndex, data.suggestedMaxScore, data.suggestedScore, data.suggestedCritique, data.suggestedMemoryPatch)
            waitingRating.value = null
        }
    )
}

// ========== 事件处理 ==========
private fun handleTrainerEvent(
    evt: LocalTrainerEvent,
    steps: androidx.compose.runtime.snapshots.SnapshotStateList<Triple<Int, String, String>>,
    etaText: MutableState<String>,
    statusMsg: MutableState<String>,
    session: MutableState<LocalTrainSession>,
    waitingRating: MutableState<LocalTrainerEvent.WaitingForUserRating?>,
    ctx: Context
) {
    when (evt) {
        is LocalTrainerEvent.StatusChanged -> {
            session.value.status = evt.status
            evt.message?.let { statusMsg.value = it }
        }
        is LocalTrainerEvent.EtaUpdated -> etaText.value = if (evt.remainingRounds <= 0)
            ctx.getString(R.string.trainer_done_word)
        else {
            val sec = evt.avgRoundMs * evt.remainingRounds / 1000
            ctx.getString(
                R.string.trainer_eta,
                sec / 60, sec % 60, evt.remainingRounds, evt.avgRoundMs / 1000
            )
        }
        is LocalTrainerEvent.Step -> steps.add(
            Triple(evt.roundIndex, STEP_KIND, ctx.getString(R.string.trainer_step_header, evt.title, evt.detail))
        )
        is LocalTrainerEvent.TeacherQuestion -> steps.add(
            Triple(evt.roundIndex, QUESTION_KIND, ctx.getString(R.string.trainer_question_header, evt.roundIndex, evt.text))
        )
        is LocalTrainerEvent.StudentAnswer -> steps.add(
            Triple(evt.roundIndex, ANSWER_KIND, ctx.getString(R.string.trainer_student_answer_header, evt.roundIndex, evt.durationMs / 1000, evt.text))
        )
        is LocalTrainerEvent.TeacherCritique -> {
            val scoreStr = "%.1f".format(evt.score)
            val maxStr = "%.1f".format(evt.maxScore)
            val header = if (session.value.teacher == "online_fallback")
                ctx.getString(R.string.trainer_online_score_header, evt.roundIndex, scoreStr, maxStr)
            else
                ctx.getString(R.string.trainer_user_score_header, evt.roundIndex, scoreStr, maxStr)
            val bodySb = StringBuilder()
            bodySb.appendLine(evt.critique)
            if (evt.memoryPatch.isNotBlank()) {
                bodySb.appendLine()
                bodySb.appendLine(ctx.getString(R.string.trainer_memory_patch_append))
                bodySb.append(evt.memoryPatch)
            }
            val body = bodySb.toString()
            steps.add(Triple(evt.roundIndex, CRITIQUE_KIND, "$header\n$body"))
        }
        is LocalTrainerEvent.RoundDone -> steps.add(
            Triple(
                evt.round.roundIndex, ROUND_DONE_KIND,
                ctx.getString(R.string.trainer_round_done, evt.round.roundIndex, evt.round.score, evt.learnedNowCount)
            )
        )
        is LocalTrainerEvent.ErrorOccurred -> {
            steps.add(Triple(evt.roundIndex, ERROR_KIND, ctx.getString(R.string.trainer_error_header, evt.roundIndex, evt.message)))
            statusMsg.value = ctx.getString(R.string.trainer_error_status, evt.message)
        }
        is LocalTrainerEvent.SessionSnapshot -> {
            // 给 UI 一份独立快照：LocalTrainSession.rounds 是与训练引擎共享的
            // MutableList，引擎在 Dispatchers.IO 上 add/修改，UI 在 Main 上遍历，
            // 直接用同一个实例会 ConcurrentModificationException 或读到撕裂状态
            val snapshot = evt.session.copy(rounds = evt.session.rounds.toMutableList())
            session.value = snapshot
            AiTermuxPrefs.saveLastTrainSession(ctx, evt.session)
        }
        is LocalTrainerEvent.WaitingForUserRating -> {
            val ss = "%.1f".format(evt.suggestedScore)
            val sm = "%.1f".format(evt.suggestedMaxScore)
            steps.add(
                Triple(
                    evt.roundIndex, RATING_KIND,
                    ctx.getString(R.string.trainer_waiting_rating, evt.roundIndex, ss, sm, evt.suggestedCritique)
                )
            )
            waitingRating.value = evt
        }
        is LocalTrainerEvent.TeacherFollowup -> steps.add(
            Triple(evt.roundIndex, QUESTION_KIND, ctx.getString(R.string.trainer_teacher_followup_header, evt.roundIndex, evt.followupText))
        )
        is LocalTrainerEvent.StudentFollowupAnswer -> steps.add(
            Triple(evt.roundIndex, ANSWER_KIND, ctx.getString(R.string.trainer_student_followup_answer_header, evt.roundIndex, evt.answerText))
        )
    }
}

// ========== 顶部信息卡（进度/状态/ETA/老师/轮数） ==========
@Composable
private fun TopInfoCard(
    session: LocalTrainSession, statusMsg: String, etaText: String, onlineReady: Boolean,
    onTargetRoundsChange: (Int) -> Unit, onTeacherToggle: (String) -> Unit
) {
    val doneRounds = session.rounds.count { it.status == "done" }
    val total = session.targetRounds
    val progress = (doneRounds.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(R.string.trainer_progress), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Text("$doneRounds/$total", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f))
            Spacer(Modifier.height(8.dp))
            InfoRow(stringResource(R.string.trainer_label_status), statusMsg, true)
            InfoRow(stringResource(R.string.trainer_label_eta), etaText, false)
            if (session.avgRoundMs > 0L) InfoRow(stringResource(R.string.trainer_label_avg_round), "${session.avgRoundMs/1000}s", false)
            val doneRoundsData = session.rounds.filter { it.status == "done" }
            if (doneRoundsData.isNotEmpty()) {
                val sc = session.totalScore
                InfoRow(stringResource(R.string.trainer_label_total_score), "${"%.2f".format(sc)} / 100", false)
                val maxW = doneRoundsData.sumOf { it.maxScore }
                if (maxW > 0.0) {
                    val pct = (sc / maxW * 100.0)
                    InfoRow(stringResource(R.string.trainer_label_normalized), "${"%.1f".format(pct)}%", false)
                }
            }
            if (session.status == "finished" && doneRoundsData.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val totalFinal = session.totalScore
                Text(stringResource(R.string.trainer_finished_total, "%.2f".format(totalFinal)), fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.primary, fontSize = 13.sp)
                if (session.finalSummary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(session.finalSummary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.trainer_teacher_label), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val online = session.teacher == "online_fallback" && onlineReady
                val manual = session.teacher == "manual" || !onlineReady
                FilterChip2(
                    label = stringResource(if (onlineReady) R.string.trainer_teacher_online else R.string.trainer_teacher_online_missing),
                    selected = online, enabled = onlineReady,
                    onClick = { if (onlineReady) onTeacherToggle("online_fallback") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip2(label = stringResource(R.string.trainer_teacher_manual), selected = manual, onClick = { onTeacherToggle("manual") })
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.trainer_label_total_rounds), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                (5..30 step 5).forEach { n ->
                    FilterChip2(
                        label = stringResource(R.string.trainer_rounds_chip, n), selected = total == n,
                        modifier = Modifier.padding(end = 6.dp),
                        onClick = { onTargetRoundsChange(n) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, bold: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp)
        Text(value, fontSize = 12.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ========== Tab ==========
@Composable
private fun TabBar(currentTab: MutableState<Int>) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            R.string.trainer_tab_steps to 0,
            R.string.trainer_tab_conversation to 1,
            R.string.trainer_tab_teacher to 2,
            R.string.trainer_tab_lessons to 3
        ).forEach { (resId, i) ->
            val t = stringResource(resId)
            val selected = currentTab.value == i
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MiuixTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { currentTab.value = i },
                contentAlignment = Alignment.Center
            ) {
                Text(t,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp)
            }
        }
    }
}

// ========== 控制按钮 ==========
@Composable
private fun ControlBar(
    session: LocalTrainSession, jobActive: Boolean, onlineReady: Boolean, currentTab: Int,
    onStart: () -> Unit, onPause: () -> Unit, onReset: () -> Unit, onClearMemory: () -> Unit,
    onClearTeacherChat: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (currentTab == 2) {
            // 老师对话 tab：隐藏继续/暂停/重开/清空教训，显示清空老师对话历史
            Button(
                onClick = onClearTeacherChat, modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(stringResource(R.string.trainer_clear_teacher_chat))
            }
        } else if (!jobActive) {
            val disabled = session.teacher == "online_fallback" && !onlineReady
            val btnText = when {
                session.rounds.isNotEmpty() && session.status != "finished" ->
                    stringResource(R.string.trainer_resume_training, session.rounds.size, session.targetRounds)
                else -> stringResource(R.string.trainer_start_training)
            }
            Button(
                onClick = onStart, modifier = Modifier.weight(1f).height(48.dp),
                enabled = !disabled
            ) {
                Text(btnText)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(text = stringResource(R.string.trainer_restart), onClick = onReset, modifier = Modifier.height(48.dp))
            Spacer(Modifier.width(2.dp))
            TextButton(
                text = stringResource(R.string.trainer_clear_lessons), onClick = onClearMemory, modifier = Modifier.height(48.dp)
            )
        } else {
            Button(
                onClick = onPause, modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(stringResource(R.string.trainer_pause))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(text = stringResource(R.string.trainer_restart), onClick = onReset, modifier = Modifier.height(48.dp))
            Spacer(Modifier.width(2.dp))
            TextButton(
                text = stringResource(R.string.trainer_clear_lessons), onClick = onClearMemory, modifier = Modifier.height(48.dp)
            )
        }
    }
}

// ========== 流程 Tab ==========
@Composable
private fun StepsTab(
    steps: androidx.compose.runtime.snapshots.SnapshotStateList<Triple<Int, String, String>>,
    session: LocalTrainSession,
    jobActive: Boolean,
    statusMsg: String,
    etaText: String,
    onlineReady: Boolean,
    onTargetRoundsChange: (Int) -> Unit,
    onTeacherToggle: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onClearMemory: () -> Unit,
    onClearTeacherChat: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
    ) {
        item(key = "top_info") {
            TopInfoCard(
                session = session, statusMsg = statusMsg, etaText = etaText,
                onlineReady = onlineReady,
                onTargetRoundsChange = onTargetRoundsChange,
                onTeacherToggle = onTeacherToggle
            )
        }
        item(key = "control_bar") {
            ControlBar(
                session = session, jobActive = jobActive, onlineReady = onlineReady, currentTab = 0,
                onStart = onStart, onPause = onPause, onReset = onReset,
                onClearMemory = onClearMemory, onClearTeacherChat = onClearTeacherChat
            )
        }
        if (steps.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.trainer_empty_steps_title))
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.trainer_empty_steps_hint), fontSize = 12.sp,
                         color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        } else {
            itemsIndexed(steps, key = { i, _ -> "step_${i}_${steps[i].first}" }) { _, step ->
                StepCard(step.first, step.second, step.third)
            }
        }
    }
}

@Composable
private fun StepCard(roundIdx: Int, kind: String, text: String) {
    val header = text.takeWhile { it != '\n' }
    val body = text.drop(header.length).trim('\n')
    val bg = when (kind) {
        ERROR_KIND -> MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        PAUSED_KIND -> MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ANSWER_KIND -> MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        CRITIQUE_KIND -> MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        else -> MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(12.dp)).background(bg)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(header, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(body, fontSize = 12.sp)
            }
        }
    }
}

// ========== 完整对话 Tab ==========
@Composable
private fun ConversationTab(session: MutableState<LocalTrainSession>) {
    val rounds = session.value.rounds
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
    ) {
        if (rounds.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.trainer_empty_conversation))
                }
            }
        } else {
            itemsIndexed(rounds, key = { _, r -> "round_${r.roundIndex}" }) { _, round ->
                RoundConversationCard(round, session.value.teacher)
            }
        }
    }
}

@Composable
private fun RoundConversationCard(round: LocalTrainRound, teacher: String) {
    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.trainer_round_label, round.roundIndex), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                val scoreColor = when {
                    round.score >= 85 -> MiuixTheme.colorScheme.primary
                    round.score >= 60 -> MiuixTheme.colorScheme.secondary
                    else -> MiuixTheme.colorScheme.error
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(scoreColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.trainer_score_label, "%.1f".format(round.score), "%.1f".format(round.maxScore)), fontSize = 12.sp, color = scoreColor, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trainer_duration_label, round.durationMs / 1000), fontSize = 11.sp)
            }
            Bubble(stringResource(R.string.trainer_bubble_teacher), round.question, "teacher")
            Bubble(stringResource(R.string.trainer_bubble_student), round.studentAnswer, "student")
            if (round.critique.isNotBlank()) {
                val title = stringResource(
                    if (teacher == "online_fallback") R.string.trainer_bubble_online_critique
                    else R.string.trainer_bubble_user_critique
                )
                val contentSb = StringBuilder()
                contentSb.append(round.critique)
                if (round.memoryPatch.isNotBlank())
                    contentSb.append("\n\n")
                        .append(stringResource(R.string.trainer_memory_patch_section))
                        .append(round.memoryPatch)
                val content = contentSb.toString()
                Bubble(title, content, "critique")
            }
        }
    }
}

@Composable
private fun Bubble(title: String, body: String, role: String) {
    val (bg, align) = when (role) {
        "teacher" -> MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) to Alignment.Start
        "student" -> MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) to Alignment.End
        else      -> MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) to Alignment.Start
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (align == Alignment.End) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.wrapContentHeight().width(310.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(10.dp)
        ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(body, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
        }
    }
}

// ========== 与在线老师对话 Tab ==========
@Composable
private fun TeacherChatTab(ctx: Context, onlineReady: MutableState<Boolean>, refreshTrigger: Int) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<Pair<String, String>>() } // (role, content)
    val input = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }

    // 加载历史（同时监听清空触发）
    LaunchedEffect(Unit, refreshTrigger) {
        val history = AiLocalTrainer.getTeacherChatHistory(ctx)
        messages.clear()
        history.forEach { messages.add(it.role to it.content) }
    }

    Column(
        Modifier.fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (!onlineReady.value) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.trainer_need_online_title), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.trainer_need_online_hint), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            return@Column
        }

        if (messages.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.trainer_online_teacher), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.trainer_chat_empty_hint), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.trainer_chat_empty_example), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(messages) { idx, (role, content) ->
                    val isUser = role == "user"
                    val bg = if (isUser) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    val textColor = if (isUser) Color.White else MiuixTheme.colorScheme.onSurface
                    val corners = if (isUser) {
                        RoundedCornerShape(14.dp, 14.dp, 2.dp, 14.dp)
                    } else {
                        RoundedCornerShape(14.dp, 14.dp, 14.dp, 2.dp)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .then(if (isUser) Modifier.padding(start = 40.dp) else Modifier.padding(end = 40.dp))
                                .clip(corners)
                                .background(bg)
                        ) {
                            Text(
                                text = content,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                fontSize = 13.sp,
                                color = textColor
                            )
                        }
                    }
                }
                if (isLoading.value) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            ) {
                                Text(stringResource(R.string.trainer_teacher_thinking), modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
        }

        // 输入区
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input.value,
                onValueChange = { input.value = it },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                label = stringResource(R.string.trainer_chat_input_label)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val msg = input.value.trim()
                    if (msg.isBlank() || isLoading.value) return@Button
                    input.value = ""
                    messages.add("user" to msg)
                    isLoading.value = true
                    scope.launch(Dispatchers.IO) {
                        val reply = runCatching { AiLocalTrainer.chatWithTeacher(ctx, msg) }
                            .getOrElse { ctx.getString(R.string.trainer_chat_failed, it.message ?: "") }
                        withContext(Dispatchers.Main) {
                            messages.add("assistant" to reply)
                            isLoading.value = false
                        }
                    }
                },
                modifier = Modifier.height(44.dp)
            ) {
                Text(stringResource(R.string.vnc_send_button))
            }
        }

    }
}


// ========== 经验教训 Tab ==========
@Composable
private fun LessonsTab(ctx: Context, refreshKey: Int) {
    val lessons = remember(refreshKey) { mutableStateOf(AiTermuxPrefs.getLessons(ctx)) }

    // 每次显示时重新加载
    LaunchedEffect(refreshKey) {
        lessons.value = AiTermuxPrefs.getLessons(ctx)
    }

    fun refresh() {
        lessons.value = AiTermuxPrefs.getLessons(ctx)
    }

    // 编辑对话框状态
    var editingLesson by remember { mutableStateOf<Lesson?>(null) }
    var editingText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        if (lessons.value.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.trainer_lessons_empty_title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.trainer_lessons_empty_hint),
                         fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.trainer_lessons_count, lessons.value.size), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                TextButton(
                    text = stringResource(R.string.trainer_clear_all),
                    onClick = {
                        AiTermuxPrefs.clearLearnedMemory(ctx)
                        refresh()
                    }
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(lessons.value) { index, lesson ->
                    val sourceLabel = when (lesson.source) {
                        "auto" -> stringResource(R.string.trainer_source_auto)
                        "teacher" -> stringResource(R.string.trainer_source_teacher)
                        "manual" -> stringResource(R.string.trainer_source_manual)
                        "migrated" -> stringResource(R.string.trainer_source_migrated)
                        else -> lesson.source
                    }
                    val sourceColor = when (lesson.source) {
                        "auto" -> Color(0xFF16A34A)
                        "teacher" -> Color(0xFF2563EB)
                        "manual" -> Color(0xFF9333EA)
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("#${index + 1}", fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(sourceColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(sourceLabel, fontSize = 10.sp, color = sourceColor)
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    text = stringResource(R.string.action_file_received_edit),
                                    onClick = {
                                        editingLesson = lesson
                                        editingText = lesson.content
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                TextButton(
                                    text = stringResource(R.string.delete),
                                    onClick = {
                                        AiTermuxPrefs.deleteLesson(ctx, lesson.id)
                                        refresh()
                                    }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(lesson.content, fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    // 编辑对话框
    if (editingLesson != null) {
        WindowDialog(
            show = true,
            onDismissRequest = { editingLesson = null },
            title = stringResource(R.string.trainer_edit_lesson_title),
            summary = stringResource(R.string.trainer_edit_lesson_summary),
            content = {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    TextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        label = stringResource(R.string.trainer_lesson_content_label)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { editingLesson = null },
                            modifier = Modifier.height(40.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val id = editingLesson?.id ?: return@Button
                                AiTermuxPrefs.updateLesson(ctx, id, editingText)
                                editingLesson = null
                                refresh()
                            },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        )
    }
}

// ========== 手动评分 Dialog ==========
@Composable
private fun ManualRatingDialog(
    data: LocalTrainerEvent.WaitingForUserRating,
    show: Boolean,
    setShow: (Boolean) -> Unit,
    onConfirm: (score: Double, critique: String, patch: String) -> Unit,
    onDismiss: () -> Unit
) {
    var score by remember(show) { mutableStateOf(data.suggestedScore.toFloat()) }
    var critique by remember(show) { mutableStateOf(data.suggestedCritique) }
    var patch by remember(show) { mutableStateOf(data.suggestedMemoryPatch) }
    val maxScoreValue = data.suggestedMaxScore.toFloat()

    WindowDialog(
        show = show,
        onDismissRequest = { setShow(false); onDismiss() },
        title = stringResource(R.string.trainer_rating_title),
        summary = stringResource(R.string.trainer_rating_summary),
        content = {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                Text(stringResource(R.string.trainer_rating_body), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.trainer_rating_question_label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(data.question, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.trainer_rating_student_label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)).padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) { Text(data.studentAnswer, fontSize = 12.sp) }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.trainer_rating_score_label, "%.1f".format(score.toDouble()), "%.1f".format(data.suggestedMaxScore)) + "  ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = score, onValueChange = { score = it },
                        valueRange = 0f..maxScoreValue, steps = ((maxScoreValue * 10) - 1).toInt().coerceAtLeast(0),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.trainer_rating_critique_label), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                TextField(
                    value = critique, onValueChange = { critique = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    label = stringResource(R.string.trainer_rating_critique_hint)
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.trainer_rating_patch_label), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                TextField(
                    value = patch, onValueChange = { patch = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    label = stringResource(R.string.trainer_rating_patch_hint)
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        text = stringResource(R.string.trainer_rating_use_suggested), onClick = { setShow(false); onDismiss() },
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { setShow(false); onConfirm(score.toDouble(), critique, patch) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(stringResource(R.string.trainer_rating_submit))
                    }
                }
            }
        }
    )
}

// ========== Chip（复用 LogViewerScreen.kt 中的风格，内部实现一份同名） ==========
@Composable
private fun FilterChip2(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(100.dp)
    val bg = when {
        selected -> MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        else -> MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val tc = when {
        selected -> MiuixTheme.colorScheme.onPrimaryContainer
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = if (enabled) 1f else 0.35f)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .border(1.2.dp, if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, color = tc)
    }
}
