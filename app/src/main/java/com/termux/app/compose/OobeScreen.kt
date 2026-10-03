package com.termux.app.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.termux.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.CheckboxPreference

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon as MaterialIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay

@Composable
fun OobeScreen(
    isUpgrade: Boolean,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    eulaAgreed: Boolean,
    onEulaAgreeChange: (Boolean) -> Unit,
    eulaLastModified: String,
    eulaLastStored: String,
    permissionStatus: String,
    isPermissionGranted: Boolean,
    isBootstrapping: Boolean,
    isDownloading: Boolean,
    isInstalling: Boolean,
    bootstrapComplete: Boolean,
    bootstrapError: String?,
    releaseNotes: String?,
    currentVersionName: String,
    onGrantAllPermissions: () -> Unit,
    onStartBootstrap: () -> Unit,
    onRetryBootstrap: () -> Unit,
    onExitApp: () -> Unit,
    onCompleteStart: () -> Unit,   // 启动 MainActivity (动画开始前调用)
    onCompleteFinish: () -> Unit   // finish OOBE Activity (动画结束后调用)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val density = LocalDensity.current
    val darkTheme = isSystemInDarkTheme()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 渐变背景 (和关于页面一致)
    val infiniteTransition = rememberInfiniteTransition(label = "oobeBreathing")
    val gradientFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "oobeGradient"
    )

    val lightGradient = Brush.verticalGradient(
        colors = listOf(
            androidx.compose.ui.graphics.lerp(Color(0xFFF52828), Color(0xFFFF7878), gradientFraction),
            androidx.compose.ui.graphics.lerp(Color(0xFF9AA8F5), Color(0xFFC5CCFC), gradientFraction),
            androidx.compose.ui.graphics.lerp(Color(0xFF4D49D6), Color(0xFF8A87E6), gradientFraction)
        )
    )
    val darkGradient = Brush.verticalGradient(
        colors = listOf(
            androidx.compose.ui.graphics.lerp(Color(0xFF8B1A1A), Color(0xFFF52828), gradientFraction),
            androidx.compose.ui.graphics.lerp(Color(0xFF3A4273), Color(0xFF9AA8F5), gradientFraction),
            androidx.compose.ui.graphics.lerp(Color(0xFF1E1C63), Color(0xFF4D49D6), gradientFraction)
        )
    )

    // 跳过逻辑
    val shouldSkipEula = isUpgrade && eulaLastModified == eulaLastStored && eulaLastStored.isNotEmpty()
    val shouldSkipPermissionsAndInstall = isUpgrade

    // 计算实际页面索引（考虑跳过）
    val actualPage = remember(currentPage, isUpgrade, shouldSkipEula, shouldSkipPermissionsAndInstall) {
        currentPage
    }

    // Circular reveal state (Welcome -> EULA transition)
    var isCircularRevealing by remember { mutableStateOf(false) }
    var revealRadius by remember { mutableStateOf(0f) }
    var revealAlpha by remember { mutableStateOf(1f) }
    var revealCenter by remember { mutableStateOf(Offset.Zero) }
    var revealTargetPage by remember { mutableStateOf<Int?>(null) }

    // REAL button center captured via onGloballyPositioned (not hardcoded!)
    var welcomeBtnCenter by remember { mutableStateOf(Offset.Zero) }
    var completeBtnCenter by remember { mutableStateOf(Offset.Zero) }

    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    // maxRevealRadius large enough so that scaleXY = r/maxHalf covers entire screen
    // Button center is near bottom; need radius >= center.y to cover top of screen
    val maxRevealRadius = with(density) {
        maxOf(
            configuration.screenWidthDp.dp.toPx(),
            configuration.screenHeightDp.dp.toPx()
        ) * 3.0f
    }

    // Trigger circular reveal transition from Welcome to next page
    fun triggerCircularReveal(btnCenter: Offset, targetPage: Int) {
        if (isCircularRevealing) return
        revealCenter = btnCenter
        revealRadius = 0f
        revealTargetPage = targetPage
        isCircularRevealing = true
        coroutineScope.launch {
            val duration = 600
            val steps = 60
            val stepDuration = duration / steps
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                revealRadius = maxRevealRadius * eased
                if (i % 10 == 0) {
                }
                delay(stepDuration.toLong())
            }
            // Animation complete — Layer 2 (clip circle at full size) covers entire screen.
            // Now switch Layer 1 to target, then cleanup Layer 2 (which is already invisible on target page)
            onPageChange(targetPage)
            delay(80)
            isCircularRevealing = false
            revealTargetPage = null
            revealRadius = 0f
        }
    }

    // Complete 按钮 → MainActivity：普通 Activity 切换（无动画）
    fun triggerCompleteReveal(btnCenter: Offset) {
        onCompleteStart()
        onCompleteFinish()
    }

    // 导航到下一页
    fun goNext() {
        val next: Int = if (!isUpgrade) {
            // 全新安装: 0→1→2→3→4→5
            (currentPage + 1).coerceAtMost(5)
        } else {
            // 升级用户:
            //   欢迎 → EULA(如需要) 或 版本日志
            //   EULA → 版本日志
            //   权限(跳过) / 安装(跳过) → 版本日志
            //   版本日志 → 完成
            when (currentPage) {
                0 -> if (shouldSkipEula) 4 else 1
                1 -> 4  // EULA 之后直接到版本日志
                2 -> 4  // 权限页(跳过) → 版本日志
                3 -> 4  // 安装页(跳过) → 版本日志
                4 -> 5  // 版本日志 → 完成
                else -> 5
            }
        }
        onPageChange(next)
    }

    fun goBack() {
        val prev: Int = if (!isUpgrade) {
            (currentPage - 1).coerceAtLeast(0)
        } else {
            when (currentPage) {
                1 -> 0            // EULA → 欢迎
                2 -> if (shouldSkipEula) 0 else 1
                3 -> if (shouldSkipEula) 0 else 1
                4 -> if (shouldSkipEula) 0 else 1  // 版本日志 → EULA 或欢迎
                5 -> 4            // 完成 → 版本日志
                else -> 0
            }
        }
        onPageChange(prev)
    }

    BackHandler {
        if (currentPage > 0) {
            goBack()
        }
    }

    // 第一页和最后一页使用渐变背景
    val useGradient = currentPage == 0 || currentPage == 5

    // Dual-track background: API 33+ RuntimeShader animated, older Brush fallback
    val useShaderBg = android.os.Build.VERSION.SDK_INT >= 31
    var bgController by remember { mutableStateOf<AboutBgEffect.ShaderController?>(null) }
    var bgDarkTheme by remember { mutableStateOf(darkTheme) }

    Box(
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)
    ) {
        if (useGradient) {
            if (useShaderBg) {
                AndroidView(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { alpha = if (darkTheme) 0.5f else 1f }
                        .blur(120.dp)
                        .zIndex(-1f),
                    factory = { ctx ->
                        android.view.View(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            val ctl = AboutBgEffect.createFor(this, darkTheme)
                            bgController = ctl
                            bgDarkTheme = darkTheme
                            ctl?.start()
                        }
                    },
                    update = { v ->
                        if (bgDarkTheme != darkTheme) {
                            bgController?.updateParams(AboutBgEffect.getParams(darkTheme))
                            bgDarkTheme = darkTheme
                        }
                    }
                )
                DisposableEffect(Unit) {
                    onDispose { bgController?.stop() }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (darkTheme) 0.5f else 1f }
                        .background(if (darkTheme) darkGradient else lightGradient)
                )
            }
        }
        // Circular Reveal Transition Box
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 1: Current page — Welcome 独立，中间页(1-5)用 AnimatedContent 做 slide+fade
            Box(modifier = Modifier.fillMaxSize()) {
                if (currentPage == 0) {
                    // Welcome 页：独立显示，Circular Reveal 接管切出动画
                    OobeWelcomePage(
                        isUpgrade = isUpgrade,
                        onNext = { _ ->
                            val nextP = if (!isUpgrade) 1 else if (shouldSkipEula) 4 else 1
                            if (currentPage == 0 && !isCircularRevealing) {
                                if (welcomeBtnCenter != Offset.Zero) {
                                    triggerCircularReveal(welcomeBtnCenter, nextP)
                                } else {
                                    goNext()
                                }
                            } else {
                                goNext()
                            }
                        },
                        darkTheme = darkTheme,
                        onButtonPositioned = { centerPx ->
                            welcomeBtnCenter = centerPx
                        }
                    )
                } else {
                    // 中间页 + Complete 页：HyperCeiler 风格 slide+fade 切换
                    androidx.compose.animation.AnimatedContent(
                        targetState = currentPage,
                        label = "oobeMiddlePages",
                        transitionSpec = {
                            val isForward = targetState > initialState
                            if (isForward) {
                                // 前进：新页从右滑入 + fadeIn，旧页向左滑出小距离 + fadeOut
                                androidx.compose.animation.slideInHorizontally(
                                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                ) { fullWidth -> fullWidth } +
                                androidx.compose.animation.fadeIn(
                                    animationSpec = tween(durationMillis = 200, delayMillis = 60, easing = LinearOutSlowInEasing)
                                ) togetherWith
                                androidx.compose.animation.slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                                ) { fullWidth -> -fullWidth / 5 } +
                                androidx.compose.animation.fadeOut(
                                    animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing)
                                )
                            } else {
                                // 后退：新页从左滑入小距离 + fadeIn，旧页向右滑出 + fadeOut
                                androidx.compose.animation.slideInHorizontally(
                                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                ) { fullWidth -> -fullWidth / 5 } +
                                androidx.compose.animation.fadeIn(
                                    animationSpec = tween(durationMillis = 200, delayMillis = 60, easing = LinearOutSlowInEasing)
                                ) togetherWith
                                androidx.compose.animation.slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                                ) { fullWidth -> fullWidth } +
                                androidx.compose.animation.fadeOut(
                                    animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing)
                                )
                            }
                        }
                    ) { page ->
                        when (page) {
                            1 -> OobeEulaPage(
                                eulaAgreed = eulaAgreed,
                                onEulaAgreeChange = onEulaAgreeChange,
                                onBack = { goBack() },
                                onNext = { goNext() },
                                eulaLastModified = eulaLastModified,
                                darkTheme = darkTheme
                            )
                            2 -> OobePermissionPage(
                                permissionStatus = permissionStatus,
                                isPermissionGranted = isPermissionGranted,
                                onGrantAllPermissions = onGrantAllPermissions,
                                onBack = { goBack() },
                                onNext = { goNext() }
                            )
                            3 -> OobeInstallPage(
                                isBootstrapping = isBootstrapping,
                                isDownloading = isDownloading,
                                isInstalling = isInstalling,
                                bootstrapComplete = bootstrapComplete,
                                bootstrapError = bootstrapError,
                                onStartBootstrap = onStartBootstrap,
                                onRetryBootstrap = onRetryBootstrap,
                                onExitApp = onExitApp,
                                onNext = { goNext() },
                                onBack = { goBack() }
                            )
                            4 -> OobeReleaseNotesPage(
                                releaseNotes = releaseNotes,
                                currentVersionName = currentVersionName,
                                onNext = { goNext() },
                                onBack = { goBack() }
                            )
                            5 -> OobeCompletePage(
                                onComplete = {
                                    onCompleteStart()
                                    onCompleteFinish()
                                },
                                darkTheme = darkTheme,
                                onButtonPositioned = { centerPx ->
                                    completeBtnCenter = centerPx
                                }
                            )
                        }
                    }
                }
            }

            // Layer 2: Circular Reveal — fillMaxSize + 动态 DynamicCircleShape clip
            // 目标页保持正常屏幕尺寸，只在圆形区域内可见
            if (isCircularRevealing && revealTargetPage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(DynamicCircleShape(revealCenter, revealRadius.coerceAtLeast(0f)))
                ) {
                    // Target page fills the clipped box — 正常屏幕尺寸！
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (revealTargetPage) {
                            1 -> OobeEulaPage(
                                eulaAgreed = eulaAgreed,
                                onEulaAgreeChange = onEulaAgreeChange,
                                onBack = { goBack() },
                                onNext = { goNext() },
                                eulaLastModified = eulaLastModified,
                                darkTheme = darkTheme
                            )
                            4 -> OobeReleaseNotesPage(
                                releaseNotes = releaseNotes,
                                currentVersionName = currentVersionName,
                                onNext = { goNext() },
                                onBack = { goBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}


// ==================== 第一页: 欢迎 ====================

@Composable
private fun OobeWelcomePage(
    isUpgrade: Boolean,
    onNext: (Offset) -> Unit,
    darkTheme: Boolean,
    onButtonPositioned: (Offset) -> Unit = {}
) {
    val context = LocalContext.current
    val headerFg = if (darkTheme) Color.White else Color(0xFF333333)
    val fgInt = android.graphics.Color.argb(
        (headerFg.alpha * 255).toInt(),
        (headerFg.red * 255).toInt(),
        (headerFg.green * 255).toInt(),
        (headerFg.blue * 255).toInt()
    )
    val appIcon = remember(darkTheme) {
        val orig = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()
        orig?.let { bm ->
            val copy = bm.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            val pixels = IntArray(copy.width * copy.height)
            copy.getPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val a = (px shr 24) and 0xFF
                val brightness = (r + g + b) / 3f
                if (a > 128 && brightness > 180f) {
                    pixels[i] = 0x00000000
                } else if (a > 128) {
                    pixels[i] = fgInt
                }
            }
            copy.setPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
            copy.asImageBitmap()
        }
    }


    // ========== HyperCeiler OOBE 入场动画 ==========
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animStarted = true }

    // LOGO: 0.5→0.95 (sinOut 440ms) → 1.0 (cubicOut 700ms) - Folme state machine
    var logoScaleState by remember { mutableStateOf(0.5f) }
    LaunchedEffect(animStarted) {
        if (animStarted) {
            logoScaleState = 0.95f
            delay(440)
            logoScaleState = 1.0f
        }
    }
    val logoScale by animateFloatAsState(
        targetValue = logoScaleState,
        animationSpec = tween(
            durationMillis = if (logoScaleState == 0.95f) 440 else 700,
            easing = if (logoScaleState == 0.95f) FastOutSlowInEasing else CubicBezierEasing(0.33f, 0f, 0.67f, 1f)
        ),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 230, delayMillis = 60),
        label = "logoAlpha"
    )

    // TEXT: translationY 100dp→0 + alpha 0→1 (HyperCeiler AnimHelper)
    val textOffsetY by animateFloatAsState(
        targetValue = if (animStarted) 0f else 100f,
        animationSpec = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
        label = "textOffset"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 1400, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "textAlpha"
    )

    // BUTTON: scale 0.9→1.0 + alpha 0→1, delay 1340ms (logo settle first)
    val btnScale by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0.9f,
        animationSpec = tween(durationMillis = 450, delayMillis = 1340,
                              easing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)),
        label = "btnScale"
    )
    val btnAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 1340,
                              easing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)),
        label = "btnAlpha"
    )
    val upgradeAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 1340),
        label = "upgradeAlpha"
    )

    // Glow pulse: infinite breathing behind logo (HyperCeiler GlowController)
    val infiniteTransition = rememberInfiniteTransition(label = "oobeWelcomeGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Real button center captured via onGloballyPositioned (for circular reveal transition)
    

    Box(modifier = Modifier.fillMaxSize()) {
        // Header - vertically centered
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().align(Alignment.Center)
        ) {
            // Glow + Logo
            Box(
                modifier = Modifier.size(160.dp).graphicsLayer {
                    scaleX = logoScale; scaleY = logoScale; alpha = logoAlpha
                },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size((120 * glowPulse).dp).graphicsLayer { alpha = glowAlpha }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    headerFg.copy(alpha = 0.35f),
                                    headerFg.copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                radius = 140f
                            ),
                            shape = CircleShape
                        )
                )
                if (appIcon != null) {
                    Image(bitmap = appIcon, contentDescription = "Logo", modifier = Modifier.size(100.dp))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_terminal),
                        contentDescription = "Logo",
                        modifier = Modifier.size(60.dp),
                        tint = headerFg
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Termux Ultra",
                style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black, color = headerFg),
                modifier = Modifier.graphicsLayer {
                    translationY = textOffsetY
                    alpha = textAlpha
                }
            )

            if (isUpgrade) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.upgrade_complete),
                    style = TextStyle(fontSize = 16.sp, color = headerFg.copy(alpha = 0.7f)),
                    modifier = Modifier.graphicsLayer {
                        alpha = upgradeAlpha
                    }
                )
            }
        }

        // Bottom button - triggers circular reveal transition
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .size(64.dp)
                .onGloballyPositioned { coords ->
                    val topLeft = coords.positionInRoot()
                    val size = coords.size
                    val center = Offset(
                        x = topLeft.x + size.width / 2f,
                        y = topLeft.y + size.height / 2f
                    )
                    onButtonPositioned(center)
                }
                .graphicsLayer { scaleX = btnScale; scaleY = btnScale; alpha = btnAlpha }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White.copy(alpha = if (darkTheme) 0.2f else 0.9f))
                .clickable { onNext(Offset.Zero) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = "Next",
                tint = if (darkTheme) Color.White else Color.Black,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ==================== 第二页: 许可条款 ====================

@Composable
private fun OobeEulaPage(
    eulaAgreed: Boolean,
    onEulaAgreeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    eulaLastModified: String,
    darkTheme: Boolean,
    transparentBackground: Boolean = false
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (transparentBackground) Color.Transparent else MiuixTheme.colorScheme.surface)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                start = 24.dp,
                end = 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.provision_back),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.oobe_terms),
                contentDescription = null,
                modifier = Modifier.size(70.dp)
            )
        }

        Text(
            text = stringResource(R.string.license_agreement),
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.oobe_eula_notice),
            style = TextStyle(
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        MaterialIcon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.oobe_privacy_notice),
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.oobe_eula_title),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.oobe_eula_last_modified, eulaLastModified),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            EulaContent()

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "GNU General Public License v3.0",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Gpl3Summary()
        }

        Column {
            CheckboxPreference(
                title = stringResource(R.string.i_agree_license),
                checked = eulaAgreed,
                onCheckedChange = { onEulaAgreeChange(it) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onNext() },
                    enabled = eulaAgreed,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
                ) {
                    Text(
                        text = stringResource(R.string.critical_force_enable_action_continue),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}



@Composable
private fun EulaContent() {
    val heading1 = stringResource(R.string.oobe_eula_h1)
    val heading2 = stringResource(R.string.oobe_eula_h2)
    val heading3 = stringResource(R.string.oobe_eula_h3)
    val heading4 = stringResource(R.string.oobe_eula_h4)
    val heading5 = stringResource(R.string.oobe_eula_h5)
    val lines = listOf(
        stringResource(R.string.oobe_eula_intro),
        stringResource(R.string.oobe_eula_h1),
        stringResource(R.string.oobe_eula_s1),
        stringResource(R.string.oobe_eula_h2),
        stringResource(R.string.oobe_eula_r1),
        stringResource(R.string.oobe_eula_r2),
        stringResource(R.string.oobe_eula_r3),
        stringResource(R.string.oobe_eula_r4),
        stringResource(R.string.oobe_eula_r5),
        stringResource(R.string.oobe_eula_r6),
        stringResource(R.string.oobe_eula_r7),
        stringResource(R.string.oobe_eula_r8),
        stringResource(R.string.oobe_eula_r9),
        stringResource(R.string.oobe_eula_r10),
        stringResource(R.string.oobe_eula_r11),
        stringResource(R.string.oobe_eula_r12),
        stringResource(R.string.oobe_eula_r13),
        stringResource(R.string.oobe_eula_r14),
        stringResource(R.string.oobe_eula_r15),
        stringResource(R.string.oobe_eula_r16),
        stringResource(R.string.oobe_eula_h3),
        stringResource(R.string.oobe_eula_ip1),
        stringResource(R.string.oobe_eula_ip2),
        stringResource(R.string.oobe_eula_ip3),
        stringResource(R.string.oobe_eula_h4),
        stringResource(R.string.oobe_eula_mod),
        stringResource(R.string.oobe_eula_h5),
        stringResource(R.string.oobe_eula_law),
    )
    
    Column {
        for (line in lines) {
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
            } else if (line == heading1 || line == heading2 || line == heading3 ||
                       line == heading4 || line == heading5) {
                Text(
                    text = line,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            } else {
                Text(
                    text = line,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun Gpl3Summary() {
    val lines = listOf(
        stringResource(R.string.oobe_gpl_intro),
        stringResource(R.string.oobe_gpl_p1),
        stringResource(R.string.oobe_gpl_p2),
        stringResource(R.string.oobe_gpl_p3),
        stringResource(R.string.oobe_gpl_p4),
        stringResource(R.string.oobe_gpl_p5),
        stringResource(R.string.oobe_gpl_p6),
        stringResource(R.string.oobe_gpl_full),
    )
    
    Column {
        for (line in lines) {
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            } else if (line.startsWith("•")) {
                Row(
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = line.take(2),
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    )
                    Text(
                        text = line.drop(2),
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    )
                }
            } else {
                Text(
                    text = line,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

// ==================== 第三页: 权限 ====================

@Composable
private fun OobePermissionPage(
    permissionStatus: String,
    isPermissionGranted: Boolean,
    onGrantAllPermissions: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    transparentBackground: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (transparentBackground) Color.Transparent else MiuixTheme.colorScheme.surface)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                start = 24.dp,
                end = 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.provision_back),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.oobe_service_state),
                contentDescription = null,
                modifier = Modifier.size(70.dp)
            )
        }

        Text(
            text = stringResource(R.string.file_info_permissions),
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.oobe_perm_intro),
            style = TextStyle(
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            PermissionItemCard(
                title = stringResource(R.string.oobe_perm_network_title),
                desc = stringResource(R.string.oobe_perm_network_desc),
                granted = true,
                icon = { MaterialIcon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionItemCard(
                title = stringResource(R.string.oobe_perm_storage_title),
                desc = stringResource(R.string.oobe_perm_storage_desc),
                granted = true,
                icon = { MaterialIcon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionItemCard(
                title = stringResource(R.string.oobe_perm_wakelock_title),
                desc = stringResource(R.string.oobe_perm_wakelock_desc),
                granted = true,
                icon = { MaterialIcon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionItemCard(
                title = stringResource(R.string.oobe_perm_vibrate_title),
                desc = stringResource(R.string.oobe_perm_vibrate_desc),
                granted = true,
                icon = { MaterialIcon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
            )
        }

        Text(
            text = permissionStatus,
            style = TextStyle(
                fontSize = 13.sp,
                color = if (isPermissionGranted) MiuixTheme.colorScheme.primary 
                       else MiuixTheme.colorScheme.onSurfaceVariantSummary
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onGrantAllPermissions() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.oobe_grant_all),
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
            Button(
                onClick = { onNext() },
                enabled = isPermissionGranted,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
            ) {
                Text(
                    text = stringResource(R.string.critical_force_enable_action_continue),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    desc: String,
    granted: Boolean,
    icon: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = desc,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            MaterialIcon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (granted) MiuixTheme.colorScheme.primary 
                       else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}



@Composable
private fun PermissionItem(name: String, desc: String) {
    Column(
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Text(
            text = name,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
        )
        Text(
            text = desc,
            style = TextStyle(
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        )
    }
}

// ==================== 第四页: 安装 ====================

@Composable
private fun OobeInstallPage(
    isBootstrapping: Boolean,
    isDownloading: Boolean,
    isInstalling: Boolean,
    bootstrapComplete: Boolean,
    bootstrapError: String?,
    onStartBootstrap: () -> Unit,
    onRetryBootstrap: () -> Unit,
    onExitApp: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    transparentBackground: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (transparentBackground) Color.Transparent else MiuixTheme.colorScheme.surface)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                start = 24.dp,
                end = 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.provision_back),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.oobe_basic_settings),
                contentDescription = null,
                modifier = Modifier.size(70.dp)
            )
        }

        Text(
            text = stringResource(R.string.action_styling_install),
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.oobe_install_needs_download),
            style = TextStyle(
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isDownloading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.oobe_install_downloading),
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.oobe_install_download_hint),
                            style = TextStyle(fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        )
                    }
                }
                isInstalling -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.oobe_install_installing),
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.oobe_install_unpacking),
                            style = TextStyle(fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        )
                    }
                }
                isBootstrapping -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.oobe_install_configuring),
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.oobe_install_wait),
                            style = TextStyle(fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        )
                    }
                }
                bootstrapComplete -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MiuixTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            MaterialIcon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(text = stringResource(R.string.oobe_install_done_title), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.oobe_install_done_sub), style = TextStyle(fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary))
                    }
                }
                bootstrapError != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MiuixTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            MaterialIcon(imageVector = Icons.Default.Error, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(text = stringResource(R.string.install_failed), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.error))
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // bootstrapError 是完整堆栈 markdown，截断以免撑爆页面；关键信息在前几行。
                                Text(text = bootstrapError, style = TextStyle(fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface),
                                    maxLines = 6, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = stringResource(R.string.oobe_install_failed_reason), style = TextStyle(fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary))
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MiuixTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(text = stringResource(R.string.oobe_install_ready_title), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.oobe_install_ready_sub), style = TextStyle(fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary))
                    }
                }
            }
        }

        when {
            bootstrapComplete -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onNext() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)) {
                        Text(text = stringResource(R.string.critical_force_enable_action_continue), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            bootstrapError != null -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onExitApp() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error)) {
                        Text(text = stringResource(R.string.oobe_exit), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Button(onClick = { onRetryBootstrap() }, modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.bootstrap_error_try_again), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            isBootstrapping -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)) {
                        Text(text = stringResource(R.string.oobe_configuring_ellipsis), fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
            else -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onStartBootstrap() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)) {
                        Text(text = stringResource(R.string.oobe_start_install), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}



// ==================== 第五页: 版本更新日志 ====================

@Composable
private fun OobeReleaseNotesPage(
    releaseNotes: String?,
    currentVersionName: String,
    onNext: () -> Unit,
    onBack: () -> Unit,
    transparentBackground: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (transparentBackground) Color.Transparent else MiuixTheme.colorScheme.surface)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                start = 24.dp,
                end = 24.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.provision_back),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.oobe_terms),
                contentDescription = null,
                modifier = Modifier.size(70.dp)
            )
        }

        Text(
            text = stringResource(R.string.oobe_changelog_title),
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Termux Ultra $currentVersionName",
            style = TextStyle(
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (releaseNotes != null && releaseNotes.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MarkdownContent(text = releaseNotes)
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        MaterialIcon(imageVector = Icons.Default.Info, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = stringResource(R.string.oobe_no_changelog), style = TextStyle(fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { onNext() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)) {
                Text(text = stringResource(R.string.critical_force_enable_action_continue), fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}




// ==================== 第六页: 完成 ====================

@Composable
private fun OobeCompletePage(
    onComplete: () -> Unit,
    darkTheme: Boolean,
    onButtonPositioned: (Offset) -> Unit = {}
) {
    val context = LocalContext.current
    val headerFg = if (darkTheme) Color.White else Color(0xFF333333)
    val fgInt = android.graphics.Color.argb(
        (headerFg.alpha * 255).toInt(),
        (headerFg.red * 255).toInt(),
        (headerFg.green * 255).toInt(),
        (headerFg.blue * 255).toInt()
    )
    val appIcon = remember(darkTheme) {
        val orig = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()
        orig?.let { bm ->
            val copy = bm.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            val pixels = IntArray(copy.width * copy.height)
            copy.getPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val a = (c shr 24) and 0xFF
                val brightness = (r + g + b) / 3f
                if (a > 128 && brightness > 180f) {
                    pixels[i] = 0x00000000
                } else if (a > 128) {
                    pixels[i] = fgInt
                }
            }
            copy.setPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
            copy.asImageBitmap()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header — 真正垂直居中
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().align(Alignment.Center)
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = "Logo",
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Termux Ultra",
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = headerFg
                )
            )

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = stringResource(R.string.oobe_install_done_title),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = headerFg.copy(alpha = 0.85f)
                )
            )
        }

        // 完成按钮 — 底部居中
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = if (darkTheme) 0.15f else 0.9f))
                .clickable { onComplete() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.overview_done),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (darkTheme) Color.White else Color.Black
                )
            )
        }
    }
}

// ==================== 动态圆形揭示 Shape ====================
// 用于 Circular Reveal 动画，圆心和半径都可以动态改变
private class DynamicCircleShape(
    private val center: Offset,  // px - 相对于 Box 左上角
    private val radius: Float    // px
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = radius.coerceAtLeast(0f)
        val path = androidx.compose.ui.graphics.Path()
        val rect = androidx.compose.ui.geometry.Rect(
            left = center.x - r,
            top = center.y - r,
            right = center.x + r,
            bottom = center.y + r
        )
        path.addOval(rect)
        return Outline.Generic(path)
    }
}



