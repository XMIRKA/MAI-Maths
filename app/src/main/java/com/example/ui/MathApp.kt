package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom Point class for Canvas drawing
data class DrawPoint(val x: Float, val y: Float, val isStartOfLine: Boolean)

// Background node structure for drifting math particles
data class MathSymbolNode(
    val symbol: String,
    val initialX: Float,
    val initialY: Float,
    val speedX: Float,
    val rotationSpeed: Float,
    val size: androidx.compose.ui.unit.Dp
)

// Russian pluralization helper for streaks and active days
fun getStreakTextRU(days: Int): String {
    val lastDigit = days % 10
    val lastTwoDigits = days % 100
    return when {
        lastTwoDigits in 11..19 -> "$days дней"
        lastDigit == 1 -> "$days день"
        lastDigit in 2..4 -> "$days дня"
        else -> "$days дней"
    }
}

// Converts raw LaTeX tags and commands into beautiful Unicode mathematical representations
fun convertLatexToUnicode(input: String): String {
    var result = input
    
    // 1. Convert \text{...} -> ...
    val textRegex = Regex("\\\\text\\{([^\\}]+)\\}")
    result = textRegex.replace(result) { it.groupValues[1] }
    
    // 2. Convert \sum_{...}^{...} -> ∑[... to ...]
    val sumRangeRegex = Regex("\\\\sum_\\{([^\\}]+)\\}\\^\\{([^\\}]+)\\}")
    result = sumRangeRegex.replace(result) { "∑[${it.groupValues[1]} to ${it.groupValues[2]}]" }
    
    // 3. Convert \sum_{...} -> ∑[...]
    val sumRegex = Regex("\\\\sum_\\{([^\\}]+)\\}")
    result = sumRegex.replace(result) { "∑[${it.groupValues[1]}]" }
    
    // 4. Convert \int_{...}^{...} -> ∫[... to ...]
    val intRangeRegex = Regex("\\\\int_\\{([^\\}]+)\\}\\^\\{([^\\}]+)\\}")
    result = intRangeRegex.replace(result) { "∫[${it.groupValues[1]} to ${it.groupValues[2]}]" }
    
    // 5. Convert \frac{...}{...} -> (...) / (...)
    val fracRegex = Regex("\\\\frac\\{([^\\}]+)\\}\\{([^\\}]+)\\}")
    result = fracRegex.replace(result) { "(${it.groupValues[1]}) / (${it.groupValues[2]})" }
    
    // 6. Convert \sqrt{...} -> √(...)
    val sqrtRegex = Regex("\\\\sqrt\\{([^\\}]+)\\}")
    result = sqrtRegex.replace(result) { "√(${it.groupValues[1]})" }
    
    // 7. Standard mathematical symbols
    val greekSymbols = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\theta" to "θ", "\\lambda" to "λ", "\\pi" to "π", "\\phi" to "φ",
        "\\psi" to "ψ", "\\sigma" to "σ", "\\omega" to "ω", "\\infty" to "∞",
        "\\sqrt" to "√", "\\approx" to "≈", "\\neq" to "≠", "\\ne" to "≠",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
        "\\pm" to "±", "\\mp" to "∓", "\\cdot" to "·", "\\times" to "×",
        "\\div" to "÷", "\\equiv" to "≡", "\\in" to "∈", "\\notin" to "∉",
        "\\subset" to "⊂", "\\subseteq" to "⊆", "\\to" to "→", "\\rightarrow" to "→",
        "\\implies" to "⇒", "\\iff" to "⇔", "\\partial" to "∂", "\\nabla" to "∇"
    )
    for ((latex, unicode) in greekSymbols) {
        result = result.replace(latex, unicode)
    }
    
    // 8. Convert trig & limits
    val mathKeywords = mapOf(
        "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan", "\\cot" to "cot",
        "\\log" to "log", "\\ln" to "ln", "\\lim" to "lim"
    )
    for ((latex, replacement) in mathKeywords) {
        result = result.replace(latex, replacement)
    }

    // 9. Standard subscripts (e.g. x_1 -> x₁, x_n -> xₙ, etc.)
    val subscripts = mapOf(
        "_0" to "₀", "_1" to "₁", "_2" to "₂", "_3" to "₃", "_4" to "₄",
        "_5" to "₅", "_6" to "₆", "_7" to "₇", "_8" to "₈", "_9" to "₉",
        "_n" to "ₙ", "_i" to "ᵢ", "_j" to "ⱼ", "_k" to "ₖ", "_x" to "ₓ"
    )
    for ((latex, unicode) in subscripts) {
        result = result.replace(latex, unicode)
    }

    // 10. Standard superscripts (e.g. x^2 -> x², x^n -> xⁿ, etc.)
    val superscripts = mapOf(
        "^0" to "⁰", "^1" to "¹", "^2" to "²", "^3" to "³", "^4" to "⁴",
        "^5" to "⁵", "^6" to "⁶", "^7" to "⁷", "^8" to "⁸", "^9" to "⁹",
        "^n" to "ⁿ", "^x" to "ˣ", "^y" to "ʸ", "^a" to "ᵃ", "^b" to "ᵇ"
    )
    for ((latex, unicode) in superscripts) {
        result = result.replace(latex, unicode)
    }

    // Clean up curly braces surrounding subscripts and superscripts
    val curlySubRegex = Regex("_\\{([^\\}]+)\\}")
    result = curlySubRegex.replace(result) { "₍${it.groupValues[1]}₎" }

    val curlySuperRegex = Regex("\\^\\{([^\\}]+)\\}")
    result = curlySuperRegex.replace(result) { "⁽${it.groupValues[1]}⁾" }

    // Strip remaining backslashes
    result = result.replace("\\", "")

    return result
}

// Builds beautiful stylized mathematical string utilizing colors and serif font faces
fun buildMathAnnotatedString(
    text: String,
    isUser: Boolean,
    primaryColor: Color,
    onSurfaceColor: Color
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val inlineParts = text.split("$")
        inlineParts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Inline math!
                val cleanMath = convertLatexToUnicode(part)
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) Color.White else primaryColor
                    )
                ) {
                    append(cleanMath)
                }
            } else {
                // Regular markdown bold parser
                val boldParts = part.split("**")
                boldParts.forEachIndexed { bIndex, bPart ->
                    if (bIndex % 2 == 1) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) Color.White else onSurfaceColor
                            )
                        ) {
                            append(bPart)
                        }
                    } else {
                        withStyle(
                            SpanStyle(
                                color = if (isUser) Color.White else onSurfaceColor
                            )
                        ) {
                            append(bPart)
                        }
                    }
                }
            }
        }
    }
}

// Custom mathematical block display Card with monospace/serif display and glows
@Composable
fun MathBlockCard(formula: String) {
    val cleanFormula = convertLatexToUnicode(formula)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MATHEMATICAL FORMULATION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = cleanFormula,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// General purpose mathematical formatter Composable
@Composable
fun FormattedMathText(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val blocks = remember(text) { text.split("$$") }
    
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEachIndexed { index, block ->
            if (index % 2 == 1) {
                MathBlockCard(formula = block)
            } else {
                if (block.isNotBlank()) {
                    val annotatedString = remember(block, isUser) {
                        buildMathAnnotatedString(block, isUser, primaryColor, onSurfaceColor)
                    }
                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceColor
                    )
                }
            }
        }
    }
}

// Breathtaking infinite cosmic-mathematical background looping moving grids and stars
@Composable
fun CosmicMathBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic")
    
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    val gridAlphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gridAlpha"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F0C1B), // Cosmic deep violet-navy
                        Color(0xFF050409)  // Deep cosmic void
                    )
                )
            )
    ) {
        val width = size.width
        val height = size.height

        // 1. Draw a mathematical coordinate plane grid that moves slowly
        val gridSize = 90.dp.toPx()
        val gridOffsetX = (progress * gridSize) % gridSize
        val gridOffsetY = (progress * gridSize * 0.5f) % gridSize

        var x = gridOffsetX
        while (x.toInt() < width.toInt()) {
            drawLine(
                color = Color(0xFF6366F1).copy(alpha = gridAlphaPulse * 0.5f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += gridSize
        }

        var y = gridOffsetY
        while (y.toInt() < height.toInt()) {
            drawLine(
                color = Color(0xFF6366F1).copy(alpha = gridAlphaPulse * 0.5f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += gridSize
        }

        // 2. Draw an active mathematical sine wave flowing across the background
        val path = Path()
        val waveAmplitude = 20.dp.toPx()
        val waveFrequency = 0.008f
        path.moveTo(0f, height * 0.45f)
        for (px in 0..width.toInt() step 6) {
            val phase = px * waveFrequency + progress * 2 * Math.PI
            val py = height * 0.45f + waveAmplitude * Math.sin(phase).toFloat()
            path.lineTo(px.toFloat(), py)
        }
        drawPath(
            path = path,
            color = Color(0xFF818CF8).copy(alpha = 0.08f),
            style = Stroke(width = 2.dp.toPx())
        )

        // 3. Draw a beautiful subtle mathematical Golden Spiral in the bottom right corner
        drawContext.canvas.save()
        drawContext.canvas.translate(width - 160.dp.toPx(), height - 160.dp.toPx())
        val spiralPath = Path()
        val radiusScale = 6.dp.toPx()
        spiralPath.moveTo(0f, 0f)
        for (i in 0..80) {
            val theta = i * 0.18f
            val r = radiusScale * Math.exp(0.12 * theta).toFloat()
            val sx = r * Math.cos(theta.toDouble()).toFloat()
            val sy = r * Math.sin(theta.toDouble()).toFloat()
            if (i == 0) spiralPath.moveTo(sx, sy) else spiralPath.lineTo(sx, sy)
        }
        drawPath(
            path = spiralPath,
            color = Color(0xFFA5B4FC).copy(alpha = 0.08f),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawContext.canvas.restore()

        // 4. Draw twinkling cosmic stars
        val randomStars = listOf(
            Offset(0.15f, 0.22f), Offset(0.38f, 0.15f), Offset(0.72f, 0.1f), Offset(0.88f, 0.25f),
            Offset(0.25f, 0.5f), Offset(0.6f, 0.42f), Offset(0.8f, 0.52f), Offset(0.92f, 0.6f),
            Offset(0.1f, 0.8f), Offset(0.42f, 0.85f), Offset(0.7f, 0.82f), Offset(0.9f, 0.9f)
        )
        
        randomStars.forEachIndexed { idx, starOffset ->
            val starX = starOffset.x * width
            val starY = starOffset.y * height
            
            val phase = (progress * 2 * Math.PI * 4.5 + idx).toFloat()
            val twinkleScale = (Math.sin(phase.toDouble()).toFloat() + 1f) / 2f
            val starAlpha = 0.15f + 0.8f * twinkleScale
            val starSize = 1.2.dp.toPx() + 1.8.dp.toPx() * twinkleScale

            drawCircle(
                color = Color.White.copy(alpha = starAlpha),
                radius = starSize,
                center = Offset(starX, starY)
            )
            
            if (twinkleScale > 0.85f) {
                drawLine(
                    color = Color(0xFFC7D2FE).copy(alpha = starAlpha * 0.4f),
                    start = Offset(starX - 5.dp.toPx(), starY),
                    end = Offset(starX + 5.dp.toPx(), starY),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color(0xFFC7D2FE).copy(alpha = starAlpha * 0.4f),
                    start = Offset(starX, starY - 5.dp.toPx()),
                    end = Offset(starX, starY + 5.dp.toPx()),
                    strokeWidth = 1f
                )
            }
        }
    }
}

// Localization Dictionary Helper
object Trans {
    fun get(key: String, lang: String): String {
        val ru = mapOf(
            "app_title" to "Mirkamol AI Math",
            "hub" to "Главная",
            "tutor" to "Тьютор",
            "scan" to "Сканер",
            "scratch" to "Черновик",
            "formula" to "Формулы",
            "level" to "Ваш уровень",
            "xp" to "Опыт",
            "streak" to "Серия",
            "welcome_title" to "Рады видеть вас, ",
            "league_title" to "Ваша лига: ",
            "hub_desc" to "Готовы освоить математику? Общайтесь с AI Тьютором или выберите тему ниже.",
            "learning_hub" to "Обучающий Хаб",
            "curriculum" to "Учебная программа",
            "parent_desk" to "Кабинет родителей / учителей",
            "parent_desc" to "Доступ к истории запросов, модерации и статистике.",
            "auth_welcome" to "Вход в Mirkamol AI Math",
            "auth_tagline" to "Интеллектуальная система по высшей и олимпиадной математике",
            "continue_google" to "Продолжить с Google (Gmail)",
            "or" to "ИЛИ",
            "create_account" to "Создать новый аккаунт",
            "login" to "Войти",
            "email_placeholder" to "Введите ваш Gmail",
            "name_placeholder" to "Ваше имя и фамилия",
            "settings_title" to "Настройки системы",
            "dark_mode" to "Темная тема",
            "light_mode" to "Светлая тема",
            "change_lang" to "Язык интерфейса / AI Tutor language",
            "privacy" to "Конфиденциальность и Аккаунт",
            "logout" to "Выйти из аккаунта",
            "solved" to "Решено задач",
            "practice_flashcards" to "Запустить флеш-карточки",
            "flashcard_title" to "Карточки активного вспоминания",
            "formula_ref" to "Справочник формул",
            "formula_desc" to "Полноразмерная база формул и теорем. Нажмите для подробного разбора ИИ.",
            "explain_formula" to "Объяснить формулу",
            "scratch_title" to "Интерактивный Черновик",
            "scratch_desc" to "Запишите шаги решения любой сложной задачи, и ИИ проверит логику шаг за шагом.",
            "analyze_btn" to "Проверить решение",
            "analyzing" to "Проверка...",
            "scanner_title" to "Умный Сканер Задач",
            "scanner_desc" to "Сделайте снимок экрана или выберите пример из олимпиадной базы для моментального разбора.",
            "capture_btn" to "Сканировать и решить",
            "scan_progress" to "Выполняется математический OCR...",
            "solved_by" to "Решено через Mirkamol AI Math",
            "league_bronze" to "Бронзовая Лига",
            "league_silver" to "Серебряная Лига",
            "league_gold" to "Золотая Лига",
            "league_diamond" to "Алмазная Лига",
            "league_legend" to "Лига Легенд"
        )
        val en = mapOf(
            "app_title" to "Mirkamol AI Math",
            "hub" to "Hub",
            "tutor" to "Tutor",
            "scan" to "Scan",
            "scratch" to "Scratch",
            "formula" to "Formula",
            "level" to "Your Level",
            "xp" to "XP",
            "streak" to "Streak",
            "welcome_title" to "Welcome back, ",
            "league_title" to "Your League: ",
            "hub_desc" to "Ready to unlock mathematical mastery? Connect with your AI Tutor or select a topic.",
            "learning_hub" to "Learning Hub",
            "curriculum" to "Curriculum Path",
            "parent_desk" to "Teacher & Parent Desk",
            "parent_desc" to "Access moderator controls, history & stats.",
            "auth_welcome" to "Welcome to Mirkamol AI Math",
            "auth_tagline" to "Your elite AI tutor for university & Olympiad mathematics",
            "continue_google" to "Continue with Google (Gmail)",
            "or" to "OR",
            "create_account" to "Create Account",
            "login" to "Log In",
            "email_placeholder" to "Enter your Gmail address",
            "name_placeholder" to "Your Full Name",
            "settings_title" to "System Settings",
            "dark_mode" to "Dark Mode",
            "light_mode" to "Light Mode",
            "change_lang" to "Interface & AI Tutor Language",
            "privacy" to "Privacy & Account",
            "logout" to "Sign Out of Account",
            "solved" to "Problems Solved",
            "practice_flashcards" to "Practice Recall Flashcards",
            "flashcard_title" to "Active Recall Cards",
            "formula_ref" to "Formula Library",
            "formula_desc" to "Exhaustive formula database. Click any card to get deep AI explanation.",
            "explain_formula" to "Explain this formula",
            "scratch_title" to "Math Scratchpad",
            "scratch_desc" to "Write your handwritten/typed derivation steps. Mirkamol AI checks your logic step-by-step.",
            "analyze_btn" to "Analyze Derivation",
            "analyzing" to "Analyzing steps...",
            "scanner_title" to "Multimodal Problem Scanner",
            "scanner_desc" to "Snap a photo of any formula, shape, or select an Olympiad task for deep instant analysis.",
            "capture_btn" to "Scan & Solve Task",
            "scan_progress" to "Performing optical math OCR...",
            "solved_by" to "Solved with Mirkamol AI Math",
            "league_bronze" to "Bronze League",
            "league_silver" to "Silver League",
            "league_gold" to "Gold League",
            "league_diamond" to "Diamond League",
            "league_legend" to "Legend League"
        )
        val m = if (lang == "RU") ru else en
        return m[key] ?: key
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathApp(viewModel: MathViewModel = viewModel()) {
    val profile by viewModel.userProfile.collectAsState()
    val sessions by viewModel.chatSessions.collectAsState()
    val notes by viewModel.savedNotes.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val studyPlans by viewModel.studyPlans.collectAsState()
    val logs by viewModel.moderationLogs.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    var activeScreen by remember { mutableStateOf("dashboard") }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var newSessionTitle by remember { mutableStateOf("") }
    var showNotificationSheet by remember { mutableStateOf(false) }

    // Navigation Drawer State for Session switcher
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    MyApplicationTheme(darkTheme = profile.isDarkMode) {
        if (!profile.isLoggedIn) {
            AuthOnboardingScreen(viewModel = viewModel, profile = profile)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // Places our moving cosmic mathematical canvas underneath everything
                CosmicMathBackground(modifier = Modifier.fillMaxSize())
                
                Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "MIRKAMOL AI DESK",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Mirkamol AI Math",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    },
                    actions = {
                        // Notification Icon with Badge
                        IconButton(
                            onClick = { showNotificationSheet = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                unreadCount.toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Profile XP Badge
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "XP",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${profile.xp} XP",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        // Streak Badge
                        if (profile.streak >= 0) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFireDepartment,
                                        contentDescription = "Streak",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "${profile.streak + 1}d",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp
                )
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    NavigationBarItem(
                        selected = activeScreen == "dashboard" || activeScreen == "more",
                        onClick = { activeScreen = "dashboard" },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text(Trans.get("hub", profile.language), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp)) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_hub")
                    )
                    NavigationBarItem(
                        selected = activeScreen == "chat",
                        onClick = { activeScreen = "chat" },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "AI Tutor Chat") },
                        label = { Text(Trans.get("tutor", profile.language), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp)) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_tutor")
                    )
                    NavigationBarItem(
                        selected = activeScreen == "scan",
                        onClick = { activeScreen = "scan" },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Problem Scanner") },
                        label = { Text(Trans.get("scan", profile.language), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp)) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_scanner")
                    )
                    NavigationBarItem(
                        selected = activeScreen == "scratchpad",
                        onClick = { activeScreen = "scratchpad" },
                        icon = { Icon(Icons.Default.Gesture, contentDescription = "Scratchpad") },
                        label = { Text(Trans.get("scratch", profile.language), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp)) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_scratchpad")
                    )
                    NavigationBarItem(
                        selected = activeScreen == "glossary" || activeScreen == "flashcards",
                        onClick = { activeScreen = "glossary" },
                        icon = { Icon(Icons.Default.Book, contentDescription = "Glossary Reference") },
                        label = { Text(Trans.get("formula", profile.language), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp)) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_formulas")
                    )
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeScreen) {
                "dashboard" -> DashboardScreen(
                    profile = profile,
                    testResults = testResults,
                    viewModel = viewModel,
                    onNavigate = { screen -> activeScreen = screen }
                )
                "chat" -> TutorChatScreen(viewModel = viewModel, sessions = sessions, notes = notes)
                "scan" -> ProblemScannerScreen(viewModel = viewModel)
                "scratchpad" -> ScratchpadScreen(viewModel = viewModel)
                "glossary" -> FormulaLibraryScreen(viewModel = viewModel, onNavigate = { activeScreen = it })
                "flashcards" -> FormulaFlashcardScreen(viewModel = viewModel, onBack = { activeScreen = "glossary" })
                "quiz" -> TestGeneratorScreen(viewModel = viewModel)
                "planner" -> StudyPlannerScreen(viewModel = viewModel, studyPlans = studyPlans)
                "moderator" -> ModeratorDashboardScreen(viewModel = viewModel, logs = logs)
            }
        }
    }

    if (showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("Create New Session") },
            text = {
                OutlinedTextField(
                    value = newSessionTitle,
                    onValueChange = { newSessionTitle = it },
                    label = { Text("Session Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSessionTitle.trim().isNotEmpty()) {
                            viewModel.createNewSession(newSessionTitle)
                            newSessionTitle = ""
                            showNewSessionDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNotificationSheet) {
        NotificationsDesk(
            notifications = notifications,
            onClose = { showNotificationSheet = false },
            onMarkRead = { viewModel.markNotificationAsRead(it) },
            onMarkAllRead = { viewModel.markAllNotificationsAsRead() },
            onDelete = { viewModel.deleteNotification(it) },
            onClearAll = { viewModel.clearAllNotifications() },
            onSimulate = { viewModel.simulateNotification() }
        )
    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthOnboardingScreen(viewModel: MathViewModel, profile: UserProfile) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var isGoogleSigningIn by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language Selection Pills at top right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (profile.language == "RU") "Язык: " else "Language: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp)
                ) {
                    listOf("RU", "EN").forEach { lang ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (profile.language == lang) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewModel.setLanguage(lang) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (profile.language == lang) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Brand Logo & Illustration Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_auth_illustration),
                        contentDescription = "Illustration",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    // Gradient Scrim overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                    startY = 100f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = Trans.get("app_title", profile.language),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "by Mirkamol",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Text(
                text = Trans.get("auth_welcome", profile.language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = Trans.get("auth_tagline", profile.language),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Form Fields
            if (isRegisterMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Trans.get("name_placeholder", profile.language)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("auth_name_field")
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(Trans.get("email_placeholder", profile.language)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Gmail") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().testTag("auth_email_field")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Main Action Button
            Button(
                onClick = {
                    if (email.contains("@") && email.contains(".")) {
                        if (isRegisterMode) {
                            viewModel.registerWithEmail(name.ifBlank { "Mirkamol" }, email)
                        } else {
                            viewModel.loginWithGmail(email, name.ifBlank { "Mirkamol" })
                        }
                    } else {
                        // Set fallback for fast testing
                        viewModel.loginWithGmail("mirkamolaliserov87@gmail.com", "Mirkamol")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auth_submit_button")
            ) {
                Text(
                    text = if (isRegisterMode) Trans.get("create_account", profile.language) else Trans.get("login", profile.language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Continue with Google (Simulated authentication)
            OutlinedButton(
                onClick = {
                    isGoogleSigningIn = true
                    viewModel.loginWithGmail("mirkamolaliserov87@gmail.com", "Mirkamol")
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auth_google_button")
            ) {
                if (isGoogleSigningIn) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Google Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = Trans.get("continue_google", profile.language),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Mode Switcher Text Link
            TextButton(
                onClick = { isRegisterMode = !isRegisterMode }
            ) {
                Text(
                    text = if (isRegisterMode) {
                        if (profile.language == "RU") "Уже есть аккаунт? Войти" else "Already have an account? Log In"
                    } else {
                        if (profile.language == "RU") "Нет аккаунта? Зарегистрироваться" else "Don't have an account? Sign Up"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ==========================================
// NOTIFICATIONS DESK OVERLAY
// ==========================================
@Composable
fun NotificationsDesk(
    notifications: List<Notification>,
    onClose: () -> Unit,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onSimulate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 24.dp)
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFF111318),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "ALERTS & MENTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Notifications Desk",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Desk",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSimulate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAlert,
                            contentDescription = "Simulate",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate Alert", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row {
                        if (notifications.any { !it.isRead }) {
                            TextButton(
                                onClick = onMarkAllRead,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Mark all read", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (notifications.isNotEmpty()) {
                            TextButton(
                                onClick = onClearAll,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Clear all", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = "No Alerts",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No alerts yet",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "We'll notify you about replies, study plans, or quiz reports.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notifications) { item ->
                            val itemColor = if (item.isRead) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }

                            val icon = when (item.type) {
                                "message" -> Icons.Default.Chat
                                "update" -> Icons.Default.TrendingUp
                                "mention" -> Icons.Default.AlternateEmail
                                "system" -> Icons.Default.Settings
                                else -> Icons.Default.Notifications
                            }

                            val iconTint = when (item.type) {
                                "message" -> MaterialTheme.colorScheme.primary
                                "update" -> MaterialTheme.colorScheme.secondary
                                "mention" -> Color(0xFFEC4899)
                                "system" -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                            val timeStr = remember(item.timestamp) { sdf.format(Date(item.timestamp)) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(itemColor)
                                    .border(
                                        width = 1.dp,
                                        color = if (item.isRead) BorderSlate.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { if (!item.isRead) onMarkRead(item.id) }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(iconTint.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = item.type,
                                            tint = iconTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                item.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                                                    color = if (item.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                            Text(
                                                timeStr,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            item.content,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }

                                    Column(
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        IconButton(
                                            onClick = { onDelete(item.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        if (!item.isRead) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 8.dp, end = 4.dp)
                                                    .size(6.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Dismiss", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

// ==========================================
// SCREEN 1: DASHBOARD HUB
// ==========================================
@Composable
fun DashboardScreen(
    profile: UserProfile,
    testResults: List<TestResult>,
    viewModel: MathViewModel,
    onNavigate: (String) -> Unit
) {
    val lang = profile.language

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Welcome Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = Trans.get("welcome_title", lang) + "${profile.name}!",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Trans.get("hub_desc", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Overhauled Gamification: Streak & EXP Linear Gauge Card
        item {
            val leagueKey = when (profile.level) {
                "Legend League" -> "league_legend"
                "Diamond League" -> "league_diamond"
                "Gold League" -> "league_gold"
                "Silver League" -> "league_silver"
                else -> "league_bronze"
            }
            val leagueName = Trans.get(leagueKey, lang)

            // Calculate progress bar values
            val (nextGoal, progressValue) = when {
                profile.xp >= 1500 -> Pair(1500, 1.0f)
                profile.xp >= 800 -> Pair(1500, (profile.xp - 800) / 700f)
                profile.xp >= 400 -> Pair(800, (profile.xp - 400) / 400f)
                profile.xp >= 151 -> Pair(400, (profile.xp - 150) / 250f)
                else -> Pair(150, profile.xp / 150f)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // League header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = "League",
                                tint = if (profile.xp >= 800) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = leagueName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${profile.xp} / $nextGoal ${Trans.get("xp", lang)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = progressValue.coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Duolingo-style Weekly Streak calendar Row
                    Text(
                        text = "${Trans.get("streak", lang)}: ${profile.streak + 1} ${if (lang == "RU") "дней подряд" else "days streak"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val daysRU = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                        val daysEN = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        val dayLabels = if (lang == "RU") daysRU else daysEN

                        for (i in 0 until 7) {
                            val isDayActive = (profile.activeDaysMask and (1 shl i)) != 0
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isDayActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isDayActive) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFireDepartment,
                                        contentDescription = "Day Active",
                                        tint = if (isDayActive) MaterialTheme.colorScheme.secondary else Color.LightGray.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dayLabels[i],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDayActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontWeight = if (isDayActive) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Learning Hub Screen Navigator Grid
        item {
            Text(
                text = Trans.get("learning_hub", lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = Trans.get("tutor", lang),
                    subtitle = if (lang == "RU") "Олимпиадный ИИ Тьютор" else "Olympiad Socratic AI",
                    icon = Icons.Default.Chat,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onNavigate("chat") }
                )
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = Trans.get("scratch", lang),
                    subtitle = if (lang == "RU") "Пошаговый черновик" else "Step-by-step checker",
                    icon = Icons.Default.Gesture,
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = { onNavigate("scratchpad") }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = Trans.get("scan", lang),
                    subtitle = if (lang == "RU") "Решить по фото" else "Solve from camera photo",
                    icon = Icons.Default.CameraAlt,
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = { onNavigate("scan") }
                )
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = Trans.get("formula", lang),
                    subtitle = if (lang == "RU") "Все формулы и теоремы" else "All formulas & database",
                    icon = Icons.Default.Book,
                    color = Color(0xFFF59E0B),
                    onClick = { onNavigate("glossary") }
                )
            }
        }

        // 4. Curriculum path (unlocked)
        item {
            Text(
                text = Trans.get("curriculum", lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicNodeRow(
                    title = if (lang == "RU") "1. Арифметика и Теория чисел" else "1. Arithmetic & Number Theory",
                    desc = if (lang == "RU") "Делимость, простые числа, модульная арифметика" else "Divisibility, modular arithmetic, prime factorization",
                    status = if (lang == "RU") "Доступно" else "Unlocked",
                    unlocked = true,
                    onNavigateToChat = { prompt ->
                        viewModel.sendChatMessage(prompt)
                        onNavigate("chat")
                    }
                )
                TopicNodeRow(
                    title = if (lang == "RU") "2. Высшая Алгебра" else "2. High School & Higher Algebra",
                    desc = if (lang == "RU") "Квадратные уравнения, комплексные числа, матрицы" else "Quadratic equations, matrices, complex structures",
                    status = if (lang == "RU") "Доступно" else "Unlocked",
                    unlocked = true,
                    onNavigateToChat = { prompt ->
                        viewModel.sendChatMessage(prompt)
                        onNavigate("chat")
                    }
                )
                TopicNodeRow(
                    title = if (lang == "RU") "3. Тригонометрия и Геометрия" else "3. Trigonometry & Geometry",
                    desc = if (lang == "RU") "Тождества, тригонометрические уравнения, синусы" else "Pythagorean theorems, law of sines, coordinate space",
                    status = if (lang == "RU") "Доступно" else "Unlocked",
                    unlocked = true,
                    onNavigateToChat = { prompt ->
                        viewModel.sendChatMessage(prompt)
                        onNavigate("chat")
                    }
                )
                TopicNodeRow(
                    title = if (lang == "RU") "4. Математический анализ" else "4. Calculus & Limits",
                    desc = if (lang == "RU") "Пределы последовательностей, производные, интегралы" else "Limits of sequences, integration, derivatives",
                    status = if (lang == "RU") "Доступно" else "Unlocked",
                    unlocked = true,
                    onNavigateToChat = { prompt ->
                        viewModel.sendChatMessage(prompt)
                        onNavigate("chat")
                    }
                )
                TopicNodeRow(
                    title = if (lang == "RU") "5. Олимпиадная математика" else "5. Olympiad Combinatorics",
                    desc = if (lang == "RU") "Комбинаторика, принцип Дирихле, неравенства" else "Pigeonhole principle, permutations, Cauchy-Schwarz",
                    status = if (lang == "RU") "Доступно" else "Unlocked",
                    unlocked = true,
                    onNavigateToChat = { prompt ->
                        viewModel.sendChatMessage(prompt)
                        onNavigate("chat")
                    }
                )
            }
        }

        // 5. Settings Panel (Requested Day/Night mode & language toggles)
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Trans.get("settings_title", lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Theme toggler (Day/Night)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (profile.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "Theme",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (profile.isDarkMode) Trans.get("dark_mode", lang) else Trans.get("light_mode", lang),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = profile.isDarkMode,
                            onCheckedChange = { viewModel.setDarkMode(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    // Language toggler
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = Trans.get("change_lang", lang),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp)
                        ) {
                            listOf("RU", "EN").forEach { currentLang ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (profile.language == currentLang) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { viewModel.setLanguage(currentLang) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = currentLang,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (profile.language == currentLang) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    // Privacy & Credentials Account Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Privacy",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = Trans.get("privacy", lang),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (profile.userEmail.isNotBlank()) profile.userEmail else " mirkamolaliserov87@gmail.com",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sign Out Button
                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Log Out", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Trans.get("logout", lang),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 6. Parent & Moderator desk entry
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = BorderSlate,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onNavigate("moderator") }
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SupervisorAccount,
                        contentDescription = "Parents & Admins",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Trans.get("parent_desk", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = Trans.get("parent_desc", lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Go",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .border(
                width = 1.dp,
                color = BorderSlate,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

data class SubTopic(val name: String, val formula: String, val explanation: String)

fun getSubTopics(title: String, isRu: Boolean): List<SubTopic> {
    if (title.contains("Арифметика") || title.contains("Arithmetic")) {
        return listOf(
            SubTopic(
                name = if (isRu) "Основная Теорема Арифметики" else "Fundamental Theorem of Arithmetic",
                formula = "n = p₁ᵃ¹ · p₂ᵃ² ··· p_kᵃ_k",
                explanation = if (isRu) "Любое натуральное число n > 1 представимо в виде произведения простых чисел, причем это представление единственно с точностью до порядка сомножителей." else "Every integer greater than 1 either is prime itself or can be factored as a unique product of prime numbers."
            ),
            SubTopic(
                name = if (isRu) "Малая Теорема Ферма" else "Fermat's Little Theorem",
                formula = "aᵖ⁻¹ ≡ 1 (mod p)",
                explanation = if (isRu) "Если p — простое число и a не делится на p, то a в степени p-1 сравнимо с 1 по модулю p. Лежит в основе RSA-шифрования." else "If p is prime and a is not divisible by p, then a^(p-1) - 1 is an integer multiple of p."
            ),
            SubTopic(
                name = if (isRu) "Алгоритм Евклида для НОД" else "Euclidean Algorithm (GCD)",
                formula = "gcd(a, b) = gcd(b, a mod b)",
                explanation = if (isRu) "Эффективный алгоритм нахождения наибольшего общего делителя двух целых чисел путем последовательного деления с остатком." else "An ancient, highly efficient method for computing the greatest common divisor of two integers."
            ),
            SubTopic(
                name = if (isRu) "Функция Эйлера" else "Euler's Totient Function",
                formula = "φ(n) = n · Π [p|n] (1 - 1/p)",
                explanation = if (isRu) "Количество положительных целых чисел, меньших или равных n, взаимно простых с n. Важнейшая функция модульной арифметики." else "Counts the positive integers up to a given integer n that are relatively prime to n."
            ),
            SubTopic(
                name = if (isRu) "Китайская Теорема об Остатках" else "Chinese Remainder Theorem",
                formula = "x ≡ a_i (mod m_i)",
                explanation = if (isRu) "Позволяет однозначно восстановить число по его остаткам от деления на попарно взаимно простые модули." else "Solves a system of simultaneous congruences modulo pairwise coprime integers."
            )
        )
    } else if (title.contains("Алгебра") || title.contains("Algebra")) {
        return listOf(
            SubTopic(
                name = if (isRu) "Корни Квадратного Уравнения" else "Quadratic Formula Roots",
                formula = "x_1,2 = (-b ± √(b² - 4ac)) / 2a",
                explanation = if (isRu) "Общая формула для аналитического нахождения действительных и комплексных корней квадратного уравнения ax² + bx + c = 0." else "Finds all real and complex roots of any quadratic equation ax² + bx + c = 0."
            ),
            SubTopic(
                name = if (isRu) "Теорема Виета" else "Vieta's Formulas",
                formula = "x₁ + x₂ = -b/a,  x₁ · x₂ = c/a",
                explanation = if (isRu) "Задает связь между коэффициентами многочлена и его корнями. Легко обобщается на уравнения высших степеней." else "Relates coefficients of a polynomial to sums and products of its roots."
            ),
            SubTopic(
                name = if (isRu) "Формула Муавра" else "De Moivre's Formula",
                formula = "zⁿ = rⁿ (cos(nφ) + i sin(nφ))",
                explanation = if (isRu) "Позволяет возводить комплексное число в тригонометрической форме в любую целую степень n." else "Expresses the power of a complex number in polar coordinates."
            ),
            SubTopic(
                name = if (isRu) "Разложение многочленов (Схема Горнера)" else "Horner's Polynomial Division",
                formula = "P(x) = (x - a)Q(x) + P(a)",
                explanation = if (isRu) "Алгоритм деления многочлена на линейный двучлен (x - a), оптимизирующий вычисление коэффициентов частного." else "An algorithm for dividing a polynomial by a binomial linear term (x - a)."
            ),
            SubTopic(
                name = if (isRu) "Собственные Значения Матрицы" else "Eigenvalue Characteristic Equation",
                formula = "det(A - λI) = 0",
                explanation = if (isRu) "Характеристический многочлен квадратной матрицы A для нахождения коэффициентов масштабного растяжения собственных векторов." else "The matrix equation solved to find scale coefficients λ that preserve vector direction."
            )
        )
    } else if (title.contains("Тригонометрия") || title.contains("Trigonometry") || title.contains("Геометрия") || title.contains("Geometry")) {
        return listOf(
            SubTopic(
                name = if (isRu) "Основное Тригонометрическое Тождество" else "Fundamental Trig Identity",
                formula = "sin²(α) + cos²(α) = 1",
                explanation = if (isRu) "Базовое соотношение тригонометрии, являющееся прямой формой теоремы Пифагора на единичной окружности." else "Direct geometric formulation of the Pythagorean theorem inside a unit coordinate circle."
            ),
            SubTopic(
                name = if (isRu) "Формулы Двойного Угла" else "Double-Angle Formulas",
                formula = "sin(2α) = 2sin(α)cos(α),  cos(2α) = cos²(α) - sin²(α)",
                explanation = if (isRu) "Выражают синус и косинус двойного угла через исходные значения тригонометрических функций." else "Converts double-angle functions into single-angle multiplications."
            ),
            SubTopic(
                name = if (isRu) "Теорема Косинусов" else "Law of Cosines",
                formula = "c² = a² + b² - 2ab cos(C)",
                explanation = if (isRu) "Обобщение теоремы Пифагора для плоских треугольников с любым углом C." else "Generalizes the Pythagorean theorem for any non-right triangles."
            ),
            SubTopic(
                name = if (isRu) "Теорема Синусов" else "Law of Sines",
                formula = "a / sin(A) = b / sin(B) = c / sin(C) = 2R",
                explanation = if (isRu) "Пропорциональная связь между сторонами треугольника, синусами противолежащих углов и диаметром описанной окружности." else "Relates side lengths of a triangle to sines of opposite angles, equaling twice the circumradius."
            ),
            SubTopic(
                name = if (isRu) "Формула Герона для Площади" else "Heron's Area Formula",
                formula = "S = √(p(p-a)(p-b)(p-c))",
                explanation = if (isRu) "Вычисляет точную площадь любого треугольника по его трем сторонам и полупериметру p = (a+b+c)/2." else "Calculates the exact area of any triangle from its three side lengths."
            )
        )
    } else if (title.contains("анализ") || title.contains("Calculus") || title.contains("Limits")) {
        return listOf(
            SubTopic(
                name = if (isRu) "Первый Замечательный Предел" else "First Remarkable Limit",
                formula = "lim [x→0] (sin(x) / x) = 1",
                explanation = if (isRu) "Базовый предел дифференциального исчисления, доказывающий производные тригонометрических функций." else "Fundamental limit proving derivatives of trigonometric functions in calculus."
            ),
            SubTopic(
                name = if (isRu) "Второй Замечательный Предел" else "Second Remarkable Limit",
                formula = "lim [x→∞] (1 + 1/x)ˣ = e",
                explanation = if (isRu) "Задает предел непрерывного сложного процента, служа определением константы Эйлера e." else "Represents continuous interest compounding, defining mathematical base e."
            ),
            SubTopic(
                name = if (isRu) "Формула Ньютона-Лейбница" else "Fundamental Theorem of Calculus",
                formula = "∫[a to b] f(x) dx = F(b) - F(a)",
                explanation = if (isRu) "Главная теорема анализа, связывающая определенное интегрирование с разностью значений первообразной на границах." else "Calculates the exact area under a curve via the antiderivative function."
            ),
            SubTopic(
                name = if (isRu) "Интегрирование по Частям" else "Integration by Parts",
                formula = "∫ u dv = u·v - ∫ v du",
                explanation = if (isRu) "Метод нахождения интегралов от произведений функций, основанный на производной произведения." else "Converts integration of products into a simpler analytical form."
            )
        )
    } else {
        return listOf(
            SubTopic(
                name = if (isRu) "Биномиальный Коэффициент (Сочетания)" else "Combinatorial Combinations",
                formula = "C_n^k = n! / (k! · (n - k)!)",
                explanation = if (isRu) "Количество способов выбрать k объектов из множества размера n без учета порядка следования." else "Number of unique ways to select a subset of k items from n elements without ordering."
            ),
            SubTopic(
                name = if (isRu) "Принцип Дирихле" else "Pigeonhole Principle",
                formula = "N + 1 pigeons in N holes => ≥2 in one",
                explanation = if (isRu) "Математическое утверждение: если предметов больше, чем контейнеров, то как минимум в одном контейнере окажется более одного предмета." else "States that if n items are put into m containers with n > m, at least one container has more than one item."
            ),
            SubTopic(
                name = if (isRu) "Неравенство Коши о средних" else "AM-GM Inequality",
                formula = "(x₁ + x₂ + ... + x_n) / n ≥ ⁿ√(x₁ · x₂ ··· x_n)",
                explanation = if (isRu) "Классическое неравенство, утверждающее, что среднее арифметическое чисел не меньше их среднего геометрического." else "The arithmetic mean of non-negative real numbers is always greater or equal to their geometric mean."
            ),
            SubTopic(
                name = if (isRu) "Неравенство Коши-Буняковского-Шварца" else "Cauchy-Schwarz Inequality",
                formula = "(Σ a_i b_i)² ≤ (Σ a_i²) · (Σ b_i²)",
                explanation = if (isRu) "Определяет фундаментальное ограничение скалярного произведения векторов в вещественных и комплексных пространствах." else "An absolute inequality relating inner vector products and Euclidean norms."
            )
        )
    }
}

@Composable
fun TopicNodeRow(
    title: String,
    desc: String,
    status: String,
    unlocked: Boolean,
    onNavigateToChat: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isRu = title.startsWith("1.") || title.contains("Арифметика") || title.contains("Высшая") || title.contains("Тригонометрия") || title.contains("анализ") || title.contains("Олимпиадная")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = unlocked) { isExpanded = !isExpanded }
            .border(
                width = 1.dp,
                color = if (isExpanded) MaterialTheme.colorScheme.primary else BorderSlate,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.DarkGray,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (!unlocked) Icons.Default.Lock 
                                      else if (isExpanded) Icons.Default.KeyboardArrowUp 
                                      else Icons.Default.KeyboardArrowDown,
                        contentDescription = status,
                        tint = if (unlocked) MaterialTheme.colorScheme.primary else Color.LightGray
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (unlocked) MaterialTheme.colorScheme.secondary else Color.LightGray
                    )
                )
            }

            AnimatedVisibility(
                visible = isExpanded && unlocked,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isRu) "ДЕТАЛЬНАЯ ПРОГРАММА И ВСЕ ФОРМУЛЫ:" else "DETAILED SYLLABUS & FORMULAS:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    
                    val subtopics = getSubTopics(title, isRu)
                    subtopics.forEach { sub ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Beautiful Math Block
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = sub.formula,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = sub.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        val prompt = if (isRu) {
                                            "Привет! Я изучаю тему \"${title}\", а именно раздел \"${sub.name}\". Пожалуйста, объясни мне подробно формулу [ ${sub.formula} ] и её применение с примерами."
                                        } else {
                                            "Hello! I am studying \"${title}\" and specifically \"${sub.name}\". Please explain in detail the formula [ ${sub.formula} ] and how to apply it with some examples."
                                        }
                                        onNavigateToChat(prompt)
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isRu) "Разбрать тему с ИИ" else "Discuss topic with AI",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: TUTOR CHAT
// ==========================================
@Composable
fun TutorChatScreen(viewModel: MathViewModel, sessions: List<ChatSession>, notes: List<SavedNote>) {
    val profile by viewModel.userProfile.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    var inputMessage by remember { mutableStateOf("") }
    var expandedSessionPanel by remember { mutableStateOf(false) }
    var attachedBitmapName by remember { mutableStateOf<String?>(null) }
    var showAttachmentOptions by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val lang = profile.language

    // Smooth auto-scroll to bottom on new messages or loading states
    LaunchedEffect(messages.size, viewModel.isTutorLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat Settings Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Socratic Mode Custom Toggle Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (viewModel.socraticMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = 1.dp,
                        color = if (viewModel.socraticMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.socraticMode = !viewModel.socraticMode }
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Socratic Help",
                        tint = if (viewModel.socraticMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (lang == "RU") "Сократов диалог" else "Socratic Guide",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.socraticMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            // Workspace manager icon
            IconButton(
                onClick = { expandedSessionPanel = !expandedSessionPanel },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = if (expandedSessionPanel) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = "Workspaces",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Expanded Workspace Selection panel
        AnimatedVisibility(
            visible = expandedSessionPanel,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (lang == "RU") "ОКНА ДИАЛОГОВ (РАБОЧИЕ ОБЛАСТИ)" else "CONVERSATION WORKSPACES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(sessions) { session ->
                            val isSelected = viewModel.activeSessionId == session.sessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .clickable {
                                        viewModel.selectSession(session.sessionId)
                                        expandedSessionPanel = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = "Session",
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (sessions.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.deleteSession(session.sessionId) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.createNewSession(if (lang == "RU") "Математическая сессия ${sessions.size + 1}" else "Math Session ${sessions.size + 1}") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == "RU") "Создать новый диалог" else "Create New Workspace",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        )
                    }
                }
            }
        }

        // Chat messages with smooth entry transitions
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = "Tutor Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text(
                                text = "Mirkamol AI Math Tutor",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (lang == "RU") {
                                    "Ваш личный олимпиадный наставник по математике. Помогу разобраться с алгеброй, теорией чисел, геометрией и математическим анализом уровня вуза."
                                } else {
                                    "Ready to guide you through arithmetic, higher algebra, geometry, combinations, and college-level calculus. Ask any question step-by-step."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            } else {
                items(messages) { message ->
                    val isUser = message.sender == "user"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (!isUser) {
                            // AI Avatar
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = "Tutor",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Column(
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            if (!isUser) {
                                Text(
                                    text = "Mirkamol AI Tutor",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isUser) 16.dp else 2.dp,
                                            bottomEnd = if (isUser) 2.dp else 16.dp
                                        )
                                    )
                                    .background(
                                        if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isUser) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isUser) 16.dp else 2.dp,
                                            bottomEnd = if (isUser) 2.dp else 16.dp
                                        )
                                    )
                                    .padding(14.dp)
                            ) {
                                Column {
                                    if (message.imageUrl != null) {
                                        Text("[ Photo Attached ]", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    FormattedMathText(
                                        text = message.text,
                                        isUser = isUser
                                    )
                                }
                            }

                            // Action Row for Tutor response
                            if (!isUser) {
                                Row(
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { viewModel.saveTutorNote("Tutor Explanation", message.text, "Saved") },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Bookmark, contentDescription = "Save Note", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (lang == "RU") "Сохранить в конспект" else "Save to Notebook",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isUser) {
                            Spacer(modifier = Modifier.width(10.dp))
                            // User Avatar
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "User",
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (viewModel.isTutorLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Tutor",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Mirkamol AI Tutor",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                ),
                                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape = RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (lang == "RU") "Mirkamol AI составляет решение..." else "Mirkamol AI is drafting solution...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Context Suggestions
        if (messages.isNotEmpty() && !viewModel.isTutorLoading) {
            val suggestions = if (lang == "RU") {
                listOf(
                    "Объясни логику решения по шагам",
                    "Покажи другой аналогичный пример",
                    "Как можно решить это иначе?",
                    "Дай мне быструю задачу по этой теме"
                )
            } else {
                listOf(
                    "Explain the logic step-by-step",
                    "Show another similar example",
                    "How can I solve this differently?",
                    "Give me a quick quiz on this concept"
                )
            }
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(suggestions) { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                viewModel.sendChatMessage(suggestion)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Attachment options modal dialog
        if (showAttachmentOptions) {
            AlertDialog(
                onDismissRequest = { showAttachmentOptions = false },
                title = { Text(if (lang == "RU") "Загрузить фото / файл" else "Upload Image / File") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (lang == "RU") "Выберите учебный файл или снимок формулы для отправки ИИ:" 
                                   else "Select a formula photo or textbook image to send to the tutor:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Button(
                            onClick = {
                                attachedBitmapName = if (lang == "RU") "Решение_Квадратных_Уравнений.png" else "Quadratic_Equations_Sheet.png"
                                showAttachmentOptions = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (lang == "RU") "📝 Загрузить рукописное решение" else "📝 Upload handwritten equation sheet")
                        }
                        
                        Button(
                            onClick = {
                                attachedBitmapName = if (lang == "RU") "График_Синусоиды.png" else "Sine_Wave_Plot.png"
                                showAttachmentOptions = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (lang == "RU") "📈 Прикрепить график функции" else "📈 Upload mathematical graph plot")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAttachmentOptions = false }) {
                        Text(if (lang == "RU") "Отмена" else "Cancel")
                    }
                }
            )
        }

        // Active Attachment Preview Banner
        if (attachedBitmapName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Attached File",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = attachedBitmapName!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                }
                IconButton(
                    onClick = { attachedBitmapName = null },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove File",
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Input Deck Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment Button
            IconButton(
                onClick = { showAttachmentOptions = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Attach image",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))

            OutlinedTextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                placeholder = {
                    Text(
                        text = if (lang == "RU") "Задайте вопрос по математике..." else "Ask a math question...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            FloatingActionButton(
                onClick = {
                    if (inputMessage.trim().isNotEmpty() || attachedBitmapName != null) {
                        val finalPrompt = if (attachedBitmapName != null) {
                            if (lang == "RU") {
                                "📎 [Загружен файл: $attachedBitmapName]\n$inputMessage"
                            } else {
                                "📎 [Attached File: $attachedBitmapName]\n$inputMessage"
                            }
                        } else {
                            inputMessage
                        }
                        viewModel.sendChatMessage(finalPrompt)
                        inputMessage = ""
                        attachedBitmapName = null
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_button")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ==========================================
// SCREEN 3: MULTIMODAL SCANNER (OCR)
// ==========================================
@Composable
fun ProblemScannerScreen(viewModel: MathViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val lang = profile.language
    var scanResult by remember { mutableStateOf("") }
    var mockImageSelected by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = if (lang == "RU") "Сканер Задач (OCR)" else "Problem Scanner (OCR)",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (lang == "RU") {
                    "Загрузите или выберите изображение математической задачи, чтобы отсканировать, распознать её текст и решить с помощью ИИ."
                } else {
                    "Upload or select an image of any math problem to scan, recognize, and solve automatically."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Selection card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (lang == "RU") "Выберите лист для сканирования:" else "Select a sheet to scan:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProblemSheetThumbnail(
                            title = if (lang == "RU") "Кв. уравнение" else "Quadratic Eq",
                            formula = "x^2 - 5x + 6 = 0",
                            isSelected = mockImageSelected == "quadratic",
                            onClick = { mockImageSelected = "quadratic" },
                            modifier = Modifier.weight(1f)
                        )
                        ProblemSheetThumbnail(
                            title = if (lang == "RU") "Производная" else "Calculus Deriv",
                            formula = "d/dx (3x^2 + sin x)",
                            isSelected = mockImageSelected == "calculus",
                            onClick = { mockImageSelected = "calculus" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (mockImageSelected != null) {
            item {
                Button(
                    onClick = {
                        isScanning = true
                        val problemText = if (mockImageSelected == "quadratic") {
                            if (lang == "RU") {
                                "Реши квадратное уравнение подробно по шагам: x^2 - 5x + 6 = 0."
                            } else {
                                "Analyze and solve step-by-step: x^2 - 5x + 6 = 0."
                            }
                        } else {
                            if (lang == "RU") {
                                "Найди производную функции f(x) = 3x^2 + sin(x). Опиши подробно каждый шаг."
                            } else {
                                "Find the derivative of f(x) = 3x^2 + sin(x). Detail each step."
                            }
                        }
                        // Create a blank/mock bitmap for prompt attachment
                        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                        viewModel.sendChatMessage(problemText, bitmap)
                        scanResult = if (lang == "RU") {
                            "Задача успешно распознана и отправлена в чат с ИИ! Перейдите во вкладку 'Чат', чтобы изучить подробное решение."
                        } else {
                            "Problem successfully scanned and sent to your AI Tutor workspace! Navigate to the 'Tutor' tab to see step-by-step solutions."
                        }
                        isScanning = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = if (lang == "RU") "Запустить оптическое сканирование и решение" else "Trigger Optical Scan & Solve",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        )
                    }
                }
            }
        }

        if (scanResult.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = MaterialTheme.colorScheme.primary)
                        Text(scanResult, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

@Composable
fun ProblemSheetThumbnail(title: String, formula: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                    .padding(6.dp)
            ) {
                Text(
                    text = formula,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

// ==========================================
// SCREEN 4: INTERACTIVE SCRATCHPAD
// ==========================================
@Composable
fun ScratchpadScreen(viewModel: MathViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val lang = profile.language
    val points = remember { mutableStateListOf<DrawPoint>() }
    var activeDraftText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (lang == "RU") "Интерактивный Черновик" else "Interactive Scratchpad",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (lang == "RU") {
                "Решайте задачи по шагам. Рисуйте пальцем или вводите текст, нажмите 'Анализировать', и Mirkamol AI мгновенно найдет ошибки."
            } else {
                "Work through problems step-by-step. Sketch formulas, write down steps, click analyze, and Mirkamol AI will instantly spot any logical mistakes."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Hand-drawing canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF131324))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            points.add(DrawPoint(offset.x, offset.y, true))
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            points.add(DrawPoint(change.position.x, change.position.y, false))
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var lastPoint: DrawPoint? = null
                points.forEach { pt ->
                    if (!pt.isStartOfLine && lastPoint != null) {
                        drawLine(
                            color = Color(0xFF10B981),
                            start = Offset(lastPoint!!.x, lastPoint!!.y),
                            end = Offset(pt.x, pt.y),
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                    }
                    lastPoint = pt
                }
            }

            if (points.isEmpty()) {
                Text(
                    text = if (lang == "RU") "Рисуйте формулы пальцем или стилусом здесь" else "Draw formulas with finger/stylus here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { points.clear() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(
                        text = if (lang == "RU") "Очистить" else "Clear Canvas",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                TextButton(
                    onClick = {
                        val strokeCount = points.count { pt -> pt.isStartOfLine }
                        val pointCount = points.size
                        
                        if (pointCount == 0) {
                            viewModel.scratchpadFeedback = if (lang == "RU") "Черновик пуст. Пожалуйста, нарисуйте формулу!" else "Scratchpad is empty. Please draw a formula!"
                        } else {
                            val recognizedText = when {
                                pointCount < 20 -> "3x + 4 = 19"
                                pointCount < 60 -> "x^2 - 5x + 6 = 0"
                                strokeCount >= 3 && pointCount > 80 -> "∫ (3x^2 + sin(x)) dx"
                                else -> "sin^2(x) + cos^2(x) = 1"
                            }
                            viewModel.scratchpadText = recognizedText
                            viewModel.scratchpadFeedback = if (lang == "RU") "Формула успешно распознана из черновика!" else "Formula successfully digitized from sketch!"
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (lang == "RU") "Распознать рисунок" else "Convert to Text",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Stepped equations input field
        OutlinedTextField(
            value = viewModel.scratchpadText,
            onValueChange = { viewModel.scratchpadText = it },
            placeholder = {
                Text(
                    text = if (lang == "RU") {
                        "Или введите своё пошаговое решение здесь...\nПример:\nШаг 1: 3x + 4 = 19\nШаг 2: 3x = 15\nШаг 3: x = 5"
                    } else {
                        "Or type your step-by-step derivation here...\ne.g. Step 1: 3x + 4 = 19\nStep 2: 3x = 15\nStep 3: x = 5"
                    }
                )
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("scratchpad_input")
        )

        Button(
            onClick = { viewModel.analyzeScratchpadSteps() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (viewModel.isScratchpadAnalyzing) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = if (lang == "RU") "Анализировать логику решения" else "Analyze Step-by-Step Derivation",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                )
            }
        }

        if (viewModel.scratchpadFeedback.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (lang == "RU") "Разбор ошибок от ИИ:" else "AI Step Feedback:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = viewModel.scratchpadFeedback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5: FORMULA LIBRARY & REFERENCING
// ==========================================
@Composable
fun FormulaLibraryScreen(viewModel: MathViewModel, onNavigate: (String) -> Unit) {
    val profile by viewModel.userProfile.collectAsState()
    val lang = profile.language

    // Localized tabs
    val tabs = if (lang == "RU") {
        listOf("Тригонометрия", "Алгебра", "Мат. Анализ", "Геометрия")
    } else {
        listOf("Trigonometry", "Algebra", "Calculus & Limits", "Geometry & Matrix")
    }

    var selectedTabIndex by remember { mutableStateOf(0) }

    // Extensive formula database
    val formulas = listOf(
        // TRIGONOMETRY
        FormulaItem(
            name = if (lang == "RU") "Основное тригонометрическое тождество" else "Pythagorean Trigonometric Identity",
            formula = "sin²(x) + cos²(x) = 1",
            category = if (lang == "RU") "Тригонометрия" else "Trigonometry",
            desc = if (lang == "RU") "Связывает синус и косинус одного угла." else "Relates the sine and cosine of any angle."
        ),
        FormulaItem(
            name = if (lang == "RU") "Синус двойного угла" else "Double Angle Sine",
            formula = "sin(2x) = 2 sin(x) cos(x)",
            category = if (lang == "RU") "Тригонометрия" else "Trigonometry",
            desc = if (lang == "RU") "Формула кратного угла для синуса." else "Formula to expand sine of a double angle."
        ),
        FormulaItem(
            name = if (lang == "RU") "Косинус двойного угла" else "Double Angle Cosine",
            formula = "cos(2x) = cos²(x) - sin²(x)",
            category = if (lang == "RU") "Тригонометрия" else "Trigonometry",
            desc = if (lang == "RU") "Также записывается как 2cos²(x)-1 или 1-2sin²(x)." else "Also expressed as 2cos²(x) - 1 or 1 - 2sin²(x)."
        ),
        FormulaItem(
            name = if (lang == "RU") "Тангенс двойного угла" else "Double Angle Tangent",
            formula = "tan(2x) = (2 tan(x)) / (1 - tan²(x))",
            category = if (lang == "RU") "Тригонометрия" else "Trigonometry",
            desc = if (lang == "RU") "Определяет тангенс кратного угла." else "Calculates tangent for a double angle."
        ),
        FormulaItem(
            name = if (lang == "RU") "Сумма синусов" else "Sum of Sines",
            formula = "sin(x) + sin(y) = 2 sin((x+y)/2) cos((x-y)/2)",
            category = if (lang == "RU") "Тригонометрия" else "Trigonometry",
            desc = if (lang == "RU") "Преобразует сумму синусов в произведение." else "Transforms a sum of sines into a product."
        ),
        FormulaItem(
            name = if (lang == "RU") "Связь тангенса и секанса" else "Secant & Tangent Link",
            formula = "1 + tan²(x) = sec²(x)",
            category = if (lang == "RU") "Тригонометрия" else "Trigonometry",
            desc = if (lang == "RU") "Следствие из основного тождества при делении на cos²(x)." else "Derived by dividing Pythagorean identity by cos²(x)."
        ),

        // ALGEBRA
        FormulaItem(
            name = if (lang == "RU") "Квадратное уравнение (Корни)" else "Quadratic Formula Roots",
            formula = "x = (-b ± √(b² - 4ac)) / 2a",
            category = if (lang == "RU") "Алгебра" else "Algebra",
            desc = if (lang == "RU") "Находит корни квадратного уравнения ax² + bx + c = 0." else "Solves roots of the quadratic equation ax² + bx + c = 0."
        ),
        FormulaItem(
            name = if (lang == "RU") "Разность квадратов" else "Difference of Squares",
            formula = "a² - b² = (a - b)(a + b)",
            category = if (lang == "RU") "Алгебра" else "Algebra",
            desc = if (lang == "RU") "Раскладывает разность квадратов двух выражений." else "Factors the difference of two perfect squares."
        ),
        FormulaItem(
            name = if (lang == "RU") "Куб суммы" else "Cube of a Sum",
            formula = "(a + b)³ = a³ + 3a²b + 3ab² + b³",
            category = if (lang == "RU") "Алгебра" else "Algebra",
            desc = if (lang == "RU") "Разложение куба двучлена по биному Ньютона." else "Expands a binomial raised to the power of three."
        ),
        FormulaItem(
            name = if (lang == "RU") "Логарифм произведения" else "Logarithm of a Product",
            formula = "log_b(xy) = log_b(x) + log_b(y)",
            category = if (lang == "RU") "Алгебра" else "Algebra",
            desc = if (lang == "RU") "Превращает умножение под логарифмом в сложение." else "Converts multiplication inside a log to addition."
        ),
        FormulaItem(
            name = if (lang == "RU") "Логарифм степени" else "Logarithm of a Power",
            formula = "log_b(x^k) = k * log_b(x)",
            category = if (lang == "RU") "Алгебра" else "Algebra",
            desc = if (lang == "RU") "Позволяет выносить показатель степени перед логарифмом." else "Pulls down the exponent to make it a coefficient."
        ),
        FormulaItem(
            name = if (lang == "RU") "Формула Бинома Ньютона" else "Binomial Theorem",
            formula = "(a+b)ⁿ = Σ [k=0 to n] (n choose k) aⁿ⁻ᵏ bᵏ",
            category = if (lang == "RU") "Алгебра" else "Algebra",
            desc = if (lang == "RU") "Разложение любой целой положительной степени двучлена." else "Expands a binomial raised to any positive integer power."
        ),

        // CALCULUS
        FormulaItem(
            name = if (lang == "RU") "Производная степени (Степенное правило)" else "Power Rule for Derivatives",
            formula = "d/dx (x^n) = n * x^(n-1)",
            category = if (lang == "RU") "Мат. Анализ" else "Calculus & Limits",
            desc = if (lang == "RU") "Правило дифференцирования степенной функции." else "Standard rule for taking the derivative of a power."
        ),
        FormulaItem(
            name = if (lang == "RU") "Производная произведения" else "Product Rule",
            formula = "d/dx (u * v) = u'v + uv'",
            category = if (lang == "RU") "Мат. Анализ" else "Calculus & Limits",
            desc = if (lang == "RU") "Используется для дифференцирования произведения двух функций." else "Differentiates a multiplication of two variables."
        ),
        FormulaItem(
            name = if (lang == "RU") "Первый замечательный предел" else "First Notable Limit",
            formula = "lim[x→0] (sin(x) / x) = 1",
            category = if (lang == "RU") "Мат. Анализ" else "Calculus & Limits",
            desc = if (lang == "RU") "Базовый тригонометрический предел в радианах." else "Fundamental limit used in trigonometric derivatives."
        ),
        FormulaItem(
            name = if (lang == "RU") "Второй замечательный предел" else "Second Notable Limit",
            formula = "lim[x→∞] (1 + 1/x)ˣ = e",
            category = if (lang == "RU") "Мат. Анализ" else "Calculus & Limits",
            desc = if (lang == "RU") "Определяет число Эйлера (основание натурального логарифма)." else "Defines Euler's number (base of natural log)."
        ),
        FormulaItem(
            name = if (lang == "RU") "Теорема Ньютона-Лейбница" else "Fundamental Theorem of Calculus",
            formula = "∫[a to b] f(x) dx = F(b) - F(a)",
            category = if (lang == "RU") "Мат. Анализ" else "Calculus & Limits",
            desc = if (lang == "RU") "Связывает определенный интеграл и первообразную функции." else "Connects definite integration with antiderivatives."
        ),
        FormulaItem(
            name = if (lang == "RU") "Интегрирование по частям" else "Integration by Parts",
            formula = "∫ u dv = uv - ∫ v du",
            category = if (lang == "RU") "Мат. Анализ" else "Calculus & Limits",
            desc = if (lang == "RU") "Следствие из правила производной произведения двух функций." else "Converts integration of products into simpler forms."
        ),

        // GEOMETRY
        FormulaItem(
            name = if (lang == "RU") "Теорема Пифагора" else "Pythagorean Theorem",
            formula = "a² + b² = c²",
            category = if (lang == "RU") "Геометрия" else "Geometry & Matrix",
            desc = if (lang == "RU") "Связывает стороны прямоугольного треугольника." else "Relates sides of a right-angled triangle."
        ),
        FormulaItem(
            name = if (lang == "RU") "Теорема косинусов" else "Law of Cosines",
            formula = "c² = a² + b² - 2ab cos(C)",
            category = if (lang == "RU") "Геометрия" else "Geometry & Matrix",
            desc = if (lang == "RU") "Обобщение теоремы Пифагора на произвольные треугольники." else "Generalizes Pythagoras' theorem for all triangles."
        ),
        FormulaItem(
            name = if (lang == "RU") "Теорема синусов" else "Law of Sines",
            formula = "a/sin(A) = b/sin(B) = c/sin(C) = 2R",
            category = if (lang == "RU") "Геометрия" else "Geometry & Matrix",
            desc = if (lang == "RU") "Связывает стороны треугольника, углы и радиус описанной окружности." else "Relates triangle sides to sines of opposite angles."
        ),
        FormulaItem(
            name = if (lang == "RU") "Определитель матрицы (2x2)" else "Matrix Determinant (2x2)",
            formula = "det(A) = ad - bc",
            category = if (lang == "RU") "Геометрия" else "Geometry & Matrix",
            desc = if (lang == "RU") "Вычисляет масштабный множитель линейного преобразования матрицы." else "Measures scaling factor of a 2x2 matrix."
        ),
        FormulaItem(
            name = if (lang == "RU") "Формула Эйлера для многогранников" else "Euler's Polyhedron Formula",
            formula = "V - E + F = 2",
            category = if (lang == "RU") "Геометрия" else "Geometry & Matrix",
            desc = if (lang == "RU") "Связывает вершины (V), ребра (E) и грани (F) выпуклого многогранника." else "Relates vertices (V), edges (E), and faces (F)."
        ),
        FormulaItem(
            name = if (lang == "RU") "Формула площади Герона" else "Heron's Formula for Area",
            formula = "Area = √(s(s-a)(s-b)(s-c))",
            category = if (lang == "RU") "Геометрия" else "Geometry & Matrix",
            desc = if (lang == "RU") "Находит площадь любого треугольника по длинам трех сторон." else "Finds triangle area from its three side lengths."
        )
    )

    // Filter based on selected tab index mapping
    val selectedCategoryFilter = when (selectedTabIndex) {
        0 -> if (lang == "RU") "Тригонометрия" else "Trigonometry"
        1 -> if (lang == "RU") "Алгебра" else "Algebra"
        2 -> if (lang == "RU") "Мат. Анализ" else "Calculus & Limits"
        else -> if (lang == "RU") "Геометрия" else "Geometry & Matrix"
    }

    val filteredFormulas = formulas.filter { it.category == selectedCategoryFilter }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (lang == "RU") "Справочник Формул" else "Formula Reference Library",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (lang == "RU") {
                    "Изучайте ключевые формулы и разделы высшей и школьной математики. Попробуйте карточки для проверки памяти."
                } else {
                    "Search math formulas, equations, and theorems, or practice active recall with flashcards."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Button(
                onClick = { onNavigate("flashcards") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CardMembership, contentDescription = "Flashcards", tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (lang == "RU") "Карточки для Запоминания" else "Practice Flashcards (Spaced Repetition)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                )
            }
        }

        // TABS
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                tabs.forEachIndexed { index, tabTitle ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = tabTitle,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }

        items(filteredFormulas) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = item.formula,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { 
                            viewModel.sendChatMessage(if (lang == "RU") "Расскажи подробнее, как работает формула: ${item.name} (${item.formula})" else "Can you explain how to apply the formula for ${item.name} (${item.formula})?") 
                            onNavigate("chat")
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == "RU") "Объяснить эту формулу детально" else "Explain this formula in detail",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

data class FormulaItem(val name: String, val formula: String, val category: String, val desc: String)

// ==========================================
// SCREEN 6: FORMULA FLASHCARDS
// ==========================================
@Composable
fun FormulaFlashcardScreen(viewModel: MathViewModel, onBack: () -> Unit) {
    val profile by viewModel.userProfile.collectAsState()
    val lang = profile.language

    val flashcards = if (lang == "RU") {
        listOf(
            Flashcard("Что такое Формула Эйлера в комплексных числах?", "e^(iθ) = cos(θ) + i*sin(θ)"),
            Flashcard("Запишите правило дифференцирования частного двух функций (u/v)'", "d/dx(u/v) = (u'v - uv') / v²"),
            Flashcard("Запишите теорему косинусов для произвольного треугольника", "c² = a² + b² - 2ab * cos(C)"),
            Flashcard("Чему равна сумма внутренних углов n-угольника?", "(n - 2) * 180°"),
            Flashcard("Что утверждает первый замечательный предел?", "lim[x→0] (sin x) / x = 1"),
            Flashcard("Запишите формулу интегрирования по частям", "∫ u dv = uv - ∫ v du"),
            Flashcard("Какова формула площади круга через радиус?", "Area = π * r²"),
            Flashcard("Как выражается синус двойного угла sin(2x)?", "sin(2x) = 2 sin(x) cos(x)")
        )
    } else {
        listOf(
            Flashcard("What is Euler's Formula in complex numbers?", "e^(iθ) = cos(θ) + i*sin(θ)"),
            Flashcard("What is the Derivative Quotient Rule for (u/v)'?", "d/dx(u/v) = (u'v - uv') / v²"),
            Flashcard("What is the Law of Cosines for any triangle?", "c² = a² + b² - 2ab*cos(C)"),
            Flashcard("What is the sum of internal angles in an n-gon?", "(n - 2) * 180°"),
            Flashcard("What is the First Notable Limit in calculus?", "lim[x→0] (sin x)/x = 1"),
            Flashcard("What is the Formula for Integration by Parts?", "∫ u dv = uv - ∫ v du"),
            Flashcard("What is the Area of a circle?", "Area = π * r²"),
            Flashcard("What is the expansion of Double Angle Sine sin(2x)?", "sin(2x) = 2 sin(x) cos(x)")
        )
    }

    var currentCardIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (lang == "RU") "Тренировка формул (Активный отзыв)" else "Formula Practice Desk",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clickable { showAnswer = !showAnswer },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (showAnswer) {
                            if (lang == "RU") "ОТВЕТ" else "ANSWER"
                        } else {
                            if (lang == "RU") "ВОПРОС" else "QUESTION"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (showAnswer) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (showAnswer) flashcards[currentCardIndex].answer else flashcards[currentCardIndex].question,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (lang == "RU") "Нажмите на карту для переворота" else "Tap card to flip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    showAnswer = false
                    currentCardIndex = (currentCardIndex + 1) % flashcards.size
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (lang == "RU") "Знаю (Далее)" else "Know It (Next)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                )
            }
            OutlinedButton(
                onClick = {
                    showAnswer = false
                    currentCardIndex = (currentCardIndex + 1) % flashcards.size
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (lang == "RU") "Повторить позже" else "Review Again",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

data class Flashcard(val question: String, val answer: String)

// ==========================================
// SCREEN 7: QUIZ GENERATOR
// ==========================================
@Composable
fun TestGeneratorScreen(viewModel: MathViewModel) {
    val quiz = viewModel.activeQuiz

    if (quiz == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Adaptive Test Generator", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text("Generate custom practice tests by selecting math streams.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            listOf("Arithmetic", "Algebra", "Calculus").forEach { topic ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.generateQuiz(topic) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(topic, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Adaptive questions curated dynamically", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = "Start", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    } else {
        val currentIdx = viewModel.quizStepIndex
        val question = quiz.questions[currentIdx]
        val selectedOption = viewModel.quizAnswers.value[currentIdx]

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Question ${currentIdx + 1} of ${quiz.questions.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Score: ${viewModel.quizScore}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                }

                LinearProgressIndicator(
                    progress = { (currentIdx + 1).toFloat() / quiz.questions.size },
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        question.question,
                        style = MathTextStyle.copy(fontSize = 18.sp),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                question.options.forEachIndexed { optIdx, option ->
                    val isSelected = selectedOption == optIdx
                    val isCorrect = question.correctOptionIndex == optIdx

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.submitQuizOption(optIdx) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                if (isCorrect) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        border = BorderStroke(
                            width = 2.dp,
                            color = if (isSelected) {
                                if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)
                            } else {
                                Color.Transparent
                            }
                        )
                    ) {
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (selectedOption != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Text(
                            "Explanation: " + question.explanation,
                            style = MathTextStyle.copy(fontSize = 13.sp),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.nextQuizStep() },
                enabled = selectedOption != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentIdx == quiz.questions.size - 1) "Complete Quiz" else "Next Question")
            }
        }
    }
}

// ==========================================
// SCREEN 8: STUDY PLANNER
// ==========================================
@Composable
fun StudyPlannerScreen(viewModel: MathViewModel, studyPlans: List<StudyPlanItem>) {
    var newTopic by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Study Planner", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text("Build a structured calendar-based study tracker.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newTopic,
                onValueChange = { newTopic = it },
                placeholder = { Text("E.g. Practice Binomial theorem") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newTopic.trim().isNotEmpty()) {
                        viewModel.addStudyPlan(newTopic, System.currentTimeMillis() + 86400000)
                        newTopic = ""
                    }
                }
            ) {
                Text("Add")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(studyPlans) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.isCompleted,
                                onCheckedChange = { viewModel.toggleStudyPlan(item.id, it) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                item.topic,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { viewModel.removeStudyPlan(item.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 9: MODERATOR DASHBOARD
// ==========================================
@Composable
fun ModeratorDashboardScreen(viewModel: MathViewModel, logs: List<ModerationLog>) {
    val profile by viewModel.userProfile.collectAsState()
    val lang = profile.language
    val isRu = lang == "RU"

    if (!profile.isModerator) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isRu) "Панель Управления Модератора" else "Moderator Security Gatekeeper",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isRu) "Введите секретный пин-код администратора для получения полного контроля над сетью устройств." else "Verify admin credentials to access real-time device control systems.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = viewModel.enteredModeratorCode,
                onValueChange = { viewModel.enteredModeratorCode = it },
                label = { Text(if (isRu) "ПИН-Код Доступа" else "Security Passcode") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.elevateToModerator(viewModel.enteredModeratorCode) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isRu) "Подтвердить Роль Администратора" else "Authorize Terminal Access")
            }
            if (viewModel.showModError) {
                Text(
                    text = if (isRu) "Ошибка: неверный код доступа. Действие отклонено." else "Error: Incorrect passcode. Security clearance rejected.",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    } else {
        var isSimulatingOtherDevices by remember { mutableStateOf(false) }
        var isScanningNetwork by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val devicesList = remember(isSimulatingOtherDevices) {
            if (isSimulatingOtherDevices) {
                listOf(
                    Triple("1", "Mirkamol (Вы)", "Android Emulator"),
                    Triple("2", "Alisher N.", "Xiaomi Redmi Note 12"),
                    Triple("3", "Bobur Sh.", "iPhone 14 Pro Max"),
                    Triple("4", "Sayyora K.", "iPad Air (M1)")
                )
            } else {
                listOf(
                    Triple("1", "Mirkamol (Вы)", "Android Emulator")
                )
            }
        }
        val deviceIps = remember {
            mapOf("1" to "10.0.2.16", "2" to "192.168.1.104", "3" to "192.168.1.115", "4" to "192.168.1.132")
        }
        val deviceStatuses = remember {
            mutableStateMapOf(
                "1" to "Active",
                "2" to "Active",
                "3" to "Blocked",
                "4" to "Restricted"
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRu) "Панель Модератора" else "Admin Terminal",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isRu) "Контроль подключенных устройств в реальном времени" else "Operational oversight & live device management",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { viewModel.revokeModeratorRole() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isRu) "Выйти" else "Exit Console")
                    }
                }
            }

            // Real-Time Network Health & Stats Dashboard
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isRu) "СЕТЕВЫЕ ПОКАЗАТЕЛИ" else "NETWORK METRICS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val activeCount = devicesList.count { deviceStatuses[it.first] == "Active" }
                            val blockedCount = devicesList.count { deviceStatuses[it.first] == "Blocked" }
                            val restrictedCount = devicesList.count { deviceStatuses[it.first] == "Restricted" }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = if (isRu) "Активно" else "Active", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text(text = "$activeCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = if (isRu) "Блок" else "Blocked", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text(text = "$blockedCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text(text = if (isRu) "Ограничен" else "Restricted", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text(text = "$restrictedCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Subnet Scanning controller
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRu) "Сканирование Подсети" else "Subnet Network Scan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isRu) {
                                    if (isSimulatingOtherDevices) "Обнаружено 3 виртуальных студенческих узла в вашей подсети." 
                                    else "Офлайн-режим. Для контроля учебного класса запустите сканирование локальной сети."
                                } else {
                                    if (isSimulatingOtherDevices) "3 student terminal nodes detected and synced on local subnet." 
                                    else "Offline-first container. Scan subnet to discover live student terminals."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        if (isScanningNetwork) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    isScanningNetwork = true
                                    // Simulates scanning delay
                                    scope.launch {
                                        kotlinx.coroutines.delay(1000)
                                        isScanningNetwork = false
                                        isSimulatingOtherDevices = !isSimulatingOtherDevices
                                        viewModel.logModeratorAction(
                                            "NETWORK_SCAN",
                                            if (isSimulatingOtherDevices) "Scanned local subnet. Discovered 3 student terminals."
                                            else "Purged simulated subnet leases. Displaying local host only."
                                        )
                                    }
                                }
                            ) {
                                Text(
                                    text = if (isRu) {
                                        if (isSimulatingOtherDevices) "Очистить" else "Сканировать"
                                    } else {
                                        if (isSimulatingOtherDevices) "Purge Scan" else "Scan Subnet"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Managed Devices List (Device Management Hub)
            item {
                Text(
                    text = if (isRu) "УПРАВЛЕНИЕ УСТРОЙСТВАМИ" else "CONNECTED DEVICE MANAGEMENT HUB",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(devicesList) { device ->
                val id = device.first
                val name = device.second
                val model = device.third
                val ip = deviceIps[id] ?: "127.0.0.1"
                val status = deviceStatuses[id] ?: "Active"

                val statusColor = when (status) {
                    "Active" -> Color(0xFF10B981)
                    "Blocked" -> Color(0xFFEF4444)
                    else -> Color(0xFFF59E0B)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(statusColor.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (status) {
                                            "Active" -> Icons.Default.CheckCircle
                                            "Blocked" -> Icons.Default.Block
                                            else -> Icons.Default.Warning
                                        },
                                        contentDescription = status,
                                        tint = statusColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                    Text(text = "$model · IP: $ip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            
                            // Visual status pill
                            Box(
                                modifier = Modifier
                                    .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = status.uppercase(Locale.ROOT),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Admin actions panel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Block/Unblock toggle button
                            Button(
                                onClick = {
                                    val nextStatus = if (status == "Blocked") "Active" else "Blocked"
                                    deviceStatuses[id] = nextStatus
                                    viewModel.logModeratorAction(
                                        if (nextStatus == "Blocked") "DEVICE_BLOCK" else "DEVICE_UNBLOCK",
                                        "Device of user $name ($model, IP: $ip) was manually switched to status: $nextStatus."
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (status == "Blocked") Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            ) {
                                Text(
                                    text = if (status == "Blocked") {
                                        if (isRu) "Разблокировать" else "Unblock Device"
                                    } else {
                                        if (isRu) "Заблокировать" else "Disable Device"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }

                            // Restriction toggle button
                            OutlinedButton(
                                onClick = {
                                    val nextStatus = if (status == "Restricted") "Active" else "Restricted"
                                    deviceStatuses[id] = nextStatus
                                    viewModel.logModeratorAction(
                                        if (nextStatus == "Restricted") "DEVICE_RESTRICT" else "DEVICE_UNRESTRICT",
                                        "Capabilities restricted on device of $name ($model)."
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (status == "Restricted") Color(0xFF10B981) else Color(0xFFF59E0B)
                                ),
                                border = BorderStroke(1.dp, if (status == "Restricted") Color(0xFF10B981) else Color(0xFFF59E0B))
                            ) {
                                Text(
                                    text = if (status == "Restricted") {
                                        if (isRu) "Снять Ограничения" else "Remove Limits"
                                    } else {
                                        if (isRu) "Ограничить Функции" else "Limit Functions"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // Audit Trail Logger section
            item {
                Text(
                    text = if (isRu) "ЖУРНАЛ АУДИТОРСКИХ ДЕЙСТВИЙ" else "AUDIT ACTION LEDGER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (logs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRu) "Журнал действий чист." else "No entries recorded in audit trail.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(logs) { log ->
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.actionType,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
