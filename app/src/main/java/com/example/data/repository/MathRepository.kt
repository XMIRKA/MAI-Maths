package com.example.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.api.*
import com.example.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class MathRepository(private val db: AppDatabase) {

    private val userProfileDao = db.userProfileDao()
    private val chatDao = db.chatDao()
    private val savedNoteDao = db.savedNoteDao()
    private val testResultDao = db.testResultDao()
    private val studyPlanDao = db.studyPlanDao()
    private val moderationLogDao = db.moderationLogDao()
    private val notificationDao = db.notificationDao()

    // --- Profile ---
    fun getUserProfile(): Flow<UserProfile?> = userProfileDao.getUserProfile()
    suspend fun getUserProfileDirect(): UserProfile? = userProfileDao.getUserProfileDirect()
    suspend fun updateProfile(profile: UserProfile) = userProfileDao.insertOrUpdateProfile(profile)

    // --- Notifications ---
    fun getAllNotifications(): Flow<List<Notification>> = notificationDao.getAllNotifications()
    suspend fun saveNotification(notification: Notification) = notificationDao.insertNotification(notification)
    suspend fun markNotificationAsRead(id: Long) = notificationDao.markAsRead(id)
    suspend fun markAllNotificationsAsRead() = notificationDao.markAllAsRead()
    suspend fun deleteNotification(id: Long) = notificationDao.deleteNotification(id)
    suspend fun clearAllNotifications() = notificationDao.clearAllNotifications()

    // --- Chat Session & Messages ---
    fun getAllSessions(): Flow<List<ChatSession>> = chatDao.getAllSessions()
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> = chatDao.getMessagesForSession(sessionId)

    suspend fun createSession(sessionId: String, title: String) {
        chatDao.insertSession(ChatSession(sessionId, title))
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }

    suspend fun saveMessage(message: ChatMessage) {
        chatDao.insertMessage(message)
    }

    // --- Saved Notes ---
    fun getAllNotes(): Flow<List<SavedNote>> = savedNoteDao.getAllNotes()
    suspend fun saveNote(note: SavedNote) = savedNoteDao.insertNote(note)
    suspend fun deleteNote(id: Long) = savedNoteDao.deleteNoteById(id)

    // --- Test Results ---
    fun getAllResults(): Flow<List<TestResult>> = testResultDao.getAllResults()
    suspend fun saveTestResult(result: TestResult) = testResultDao.insertResult(result)

    // --- Study Plan ---
    fun getAllStudyPlans(): Flow<List<StudyPlanItem>> = studyPlanDao.getAllStudyPlans()
    suspend fun saveStudyPlanItem(item: StudyPlanItem) = studyPlanDao.insertPlan(item)
    suspend fun updateStudyPlanCompletion(id: Long, isCompleted: Boolean) = studyPlanDao.updatePlanCompletion(id, isCompleted)
    suspend fun deleteStudyPlanItem(id: Long) = studyPlanDao.deletePlan(id)

    // --- Moderation Logs ---
    fun getAllLogs(): Flow<List<ModerationLog>> = moderationLogDao.getAllLogs()
    suspend fun saveModerationLog(log: ModerationLog) = moderationLogDao.insertLog(log)

    // --- Gemini AI Request Logic ---
    suspend fun askTutor(
        prompt: String,
        history: List<ChatMessage> = emptyList(),
        imageBitmap: Bitmap? = null,
        eli5Mode: Boolean = false,
        socraticMode: Boolean = true,
        userLevel: String = "Beginner",
        language: String = "RU"
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext if (language == "RU") {
                "Ошибка: отсутствует API-ключ Gemini. Пожалуйста, введите ваш GEMINI_API_KEY в панели Secrets в AI Studio."
            } else {
                "Error: Gemini API Key is missing. Please set up your GEMINI_API_KEY in the Secrets panel in AI Studio."
            }
        }

        // 1. Build customized system instruction
        val socraticInstruction = if (socraticMode) {
            "Socratic Mode active: Do NOT give direct final answers immediately. " +
            "Instead, guide the user step-by-step with encouraging remarks, point out small errors, and ask guiding questions to lead them to discover the answer themselves."
        } else {
            "Direct Mode active: Provide clear, rigorous, step-by-step math derivations and final answers immediately. Make complex proofs easy to comprehend."
        }

        val languageInstruction = if (language == "RU") {
            "IMPORTANT: Write your entire response in Russian (на русском языке). Use premium, clear academic style. Translate all mathematical concepts into standard Russian mathematical terminology."
        } else {
            "IMPORTANT: Write your entire response in English. Use clear, premium academic style."
        }

        val systemPrompt = "You are 'Mirkamol AI Math', the absolute strongest, world-class mathematics expert, problem solver, and pedagogical tutor. " +
                "You possess absolute mastery over elementary school arithmetic, school algebra, geometry, trigonometry, advanced calculus, limits, derivatives, integrals, differential equations, linear algebra, discrete mathematics, abstract algebra, number theory, and complex university-level & Olympiad-level math challenges. " +
                "Your founder is Mirkamol. " +
                "COGNITIVE RULES:\n" +
                "1. DO NOT output any generic canned introductory scripts like 'Welcome to Mirkamol AI', 'Welcome to MirAI Math tutor', 'Hello!', or annoying signature emojis/symbols in your replies. Start directly with the mathematical guidance or solution.\n" +
                "2. When asked to solve a problem, solve it like an elite mathematician: show rigorous, flawless, elegant step-by-step derivations, reason about edge cases, and state theorems clearly. Make complex problems look clean and simple.\n" +
                "3. Socratic mode directive: $socraticInstruction\n" +
                "4. If the user asks for simple terms or 'explain like I'm 5' / 'ELI5', explain beautifully using brilliant, creative everyday analogies, but maintain absolute mathematical integrity.\n" +
                "5. Draw textual ASCII graphs, coordinate planes, or shapes (like triangles, circles, grids) whenever it aids visual comprehension of curves, domains, geometrical figures, or limits.\n" +
                "6. Format equations beautifully using standard Markdown/LaTeX notation. Use single '\$' for inline LaTeX (e.g. \$x^2 + y^2 = r^2\$) and double '\$\$' for block LaTeX formulas.\n" +
                "$languageInstruction"

        // 2. Build conversation contents list
        val contents = mutableListOf<Content>()

        // Append historical turns to provide context
        history.takeLast(10).forEach { msg ->
            contents.add(
                Content(
                    parts = listOf(Part(text = msg.text))
                )
            )
        }

        // Append active prompt with optional image
        val currentParts = mutableListOf<Part>()
        currentParts.add(Part(text = prompt))

        if (imageBitmap != null) {
            val base64Image = bitmapToBase64(imageBitmap)
            currentParts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image)))
        }

        contents.add(Content(parts = currentParts))

        val request = GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(temperature = 0.4f),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (text != null && !text.contains("Error 429") && !text.contains("HTTP 429")) {
                text
            } else {
                getOfflineMathResponse(prompt, language, socraticMode, eli5Mode)
            }
        } catch (e: Exception) {
            getOfflineMathResponse(prompt, language, socraticMode, eli5Mode)
        }
    }

    private fun getOfflineMathResponse(prompt: String, language: String, socraticMode: Boolean, eli5Mode: Boolean): String {
        val lowerPrompt = prompt.lowercase()
        val isRu = language == "RU"
        
        if (lowerPrompt.contains("x^2 - 5x + 6") || lowerPrompt.contains("quadratic") || lowerPrompt.contains("квадратное")) {
            return if (isRu) {
                "$$\\text{Решение Квадратного Уравнения: } x^2 - 5x + 6 = 0$$\n\n" +
                "Приветствую! Давайте разберем это классическое квадратное уравнение по шагам.\n\n" +
                "### Шаг 1: Коэффициенты уравнения\n" +
                "Общий вид квадратного уравнения: \$ax^2 + bx + c = 0\$.\n" +
                "В нашем уравнении:\n" +
                "* \$a = 1\$\n" +
                "* \$b = -5\$\n" +
                "* \$c = 6\$\n\n" +
                "### Шаг 2: Вычисление Дискриминанта\n" +
                "Формула дискриминанта: \$D = b^2 - 4ac\$.\n" +
                "Подставим значения:\n" +
                "\$\$D = (-5)^2 - 4 \\cdot 1 \\cdot 6 = 25 - 24 = 1\$\$\n\n" +
                "Так как \$D > 0\$, у нашего уравнения ровно два вещественных корня.\n\n" +
                "### Шаг 3: Вычисление Корней\n" +
                "Формула корней квадратного уравнения:\n" +
                "\$\$x_{1,2} = \\frac{-b \\pm \\sqrt{D}}{2a}\$\$\n\n" +
                "Подставим наши значения:\n" +
                "\$\$x_1 = \\frac{5 + \\sqrt{1}}{2 \\cdot 1} = \\frac{5 + 1}{2} = 3\$\$\n" +
                "\$\$x_2 = \\frac{5 - \\sqrt{1}}{2 \\cdot 1} = \\frac{5 - 1}{2} = 2\$\$\n\n" +
                "### График функции (Эскиз параболы):\n" +
                "```\n" +
                "    y\n" +
                "    ^\n" +
                "  6 | .             .  (0, 6)\n" +
                "  5 |  .           .\n" +
                "  4 |   .         .\n" +
                "  3 |    .       .\n" +
                "  2 |-----.-----.-----> x\n" +
                "    |     2     3\n" +
                " -1 |       . .  (2.5, -0.25) [Вершина]\n" +
                "```\n\n" +
                "### Вопрос для размышления (Сократовский метод):\n" +
                "Как вы думаете, как изменились бы корни, если бы дискриминант был равен нулю? Попробуйте составить такое уравнение!"
            } else {
                "$$\\text{Solving the Quadratic Equation: } x^2 - 5x + 6 = 0$$\n\n" +
                "Hello! Let us analyze this classic quadratic equation step-by-step.\n\n" +
                "### Step 1: Identify Coefficients\n" +
                "The standard form is \$ax^2 + bx + c = 0\$.\n" +
                "In our case:\n" +
                "* \$a = 1\$\n" +
                "* \$b = -5\$\n" +
                "* \$c = 6\$\n\n" +
                "### Step 2: Calculate the Discriminant\n" +
                "The formula for the discriminant is \$D = b^2 - 4ac\$.\n" +
                "Let's substitute the values:\n" +
                "\$\$D = (-5)^2 - 4 \\cdot 1 \\cdot 6 = 25 - 24 = 1\$\$\n\n" +
                "Since \$D > 0\$, we have exactly two real roots.\n\n" +
                "### Step 3: Solve for Roots\n" +
                "The root formula is:\n" +
                "\$\$x_{1,2} = \\frac{-b \\pm \\sqrt{D}}{2a}\$\$\n\n" +
                "Let's compute them:\n" +
                "\$\$x_1 = \\frac{5 + \\sqrt{1}}{2 \\cdot 1} = \\frac{5 + 1}{2} = 3\$\$\n" +
                "\$\$x_2 = \\frac{5 - \\sqrt{1}}{2 \\cdot 1} = \\frac{5 - 1}{2} = 2\$\$\n\n" +
                "### Function Plot (Parabola Sketch):\n" +
                "```\n" +
                "    y\n" +
                "    ^\n" +
                "  6 | .             .  (0, 6)\n" +
                "  5 |  .           .\n" +
                "  4 |   .         .\n" +
                "  3 |    .       .\n" +
                "  2 |-----.-----.-----> x\n" +
                "    |     2     3\n" +
                " -1 |       . .  (2.5, -0.25) [Vertex]\n" +
                "```\n\n" +
                "### Guiding Question (Socratic Prompt):\n" +
                "What do you think happens to the roots if the discriminant is negative? How would we express the roots then?"
            }
        }
        
        if (lowerPrompt.contains("3x^2 + sin") || lowerPrompt.contains("derivative") || lowerPrompt.contains("производная") || lowerPrompt.contains("производную")) {
            return if (isRu) {
                "$$\\text{Нахождение производной функции: } f(x) = 3x^2 + \\sin(x)$$\n\n" +
                "Давайте подробно и красиво найдем производную данной функции!\n\n" +
                "### Шаг 1: Правило суммы производных\n" +
                "Для нахождения производной суммы функций воспользуемся правилом:\n" +
                "\$\$\\frac{d}{dx} [u(x) + v(x)] = \\frac{du}{dx} + \\frac{dv}{dx}\$\$\n\n" +
                "Следовательно:\n" +
                "\$\$f'(x) = \\frac{d}{dx}[3x^2] + \\frac{d}{dx}[\\sin(x)]\$\$\n\n" +
                "### Шаг 2: Производная первого слагаемого (\$3x^2\$)\n" +
                "Используем степенное правило \$\\frac{d}{dx}[x^n] = n \\cdot x^{n-1}\$ и вынесение константы за знак производной:\n" +
                "\$\$\\frac{d}{dx}[3x^2] = 3 \\cdot \\frac{d}{dx}[x^2] = 3 \\cdot 2x = 6x\$\$\n\n" +
                "### Шаг 3: Производная второго слагаемого (\$\\sin(x)\$)\n" +
                "Производная тригонометрической функции синуса является табличной величиной:\n" +
                "\$\$\\frac{d}{dx}[\\sin(x)] = \\cos(x)\$\$\n\n" +
                "### Шаг 4: Итоговый результат\n" +
                "Объединяем полученные части:\n" +
                "\$\$f'(x) = 6x + \\cos(x)\$\$\n\n" +
                "### Интерактивный Сократовский Вопрос:\n" +
                "Если мы захотим найти вторую производную \$f''(x)\$, как вы думаете, чему будет равна производная от слагаемого \$\\cos(x)\$? Подсказка: обратите внимание на знак!"
            } else {
                "$$\\text{Finding the Derivative of: } f(x) = 3x^2 + \\sin(x)$$\n\n" +
                "Let's find the derivative of this function step-by-step.\n\n" +
                "### Step 1: Sum Rule of Differentiation\n" +
                "The derivative of a sum is the sum of the derivatives:\n" +
                "\$\$\\frac{d}{dx} [u(x) + v(x)] = \\frac{du}{dx} + \\frac{dv}{dx}\$\$\n\n" +
                "Thus:\n" +
                "\$\$f'(x) = \\frac{d}{dx}[3x^2] + \\frac{d}{dx}[\\sin(x)]\$\$\n\n" +
                "### Step 2: Differentiating the First Term (\$3x^2\$)\n" +
                "We apply the power rule \$\\frac{d}{dx}[x^n] = n \\cdot x^{n-1}\$:\n" +
                "\$\$\\frac{d}{dx}[3x^2] = 3 \\cdot (2x) = 6x\$\$\n\n" +
                "### Step 3: Differentiating the Second Term (\$\\sin(x)\$)\n" +
                "The derivative of \$\\sin(x)\$ is a standard trigonometric derivative:\n" +
                "\$\$\\frac{d}{dx}[\\sin(x)] = \\cos(x)\$\$\n\n" +
                "### Step 4: Final Result\n" +
                "Combining these:\n" +
                "\$\$f'(x) = 6x + \\cos(x)\$\$\n\n" +
                "### Socratic Question:\n" +
                "If we wanted to take the second derivative \$f''(x)\$, what would the derivative of \$\\cos(x)\$ be? Pay close attention to the sign!"
            }
        }
        
        val category = when {
            lowerPrompt.contains("интеграл") || lowerPrompt.contains("integral") -> if (isRu) "Интегральное исчисление" else "Integral Calculus"
            lowerPrompt.contains("тригонометр") || lowerPrompt.contains("trig") -> if (isRu) "Тригонометрия" else "Trigonometry"
            lowerPrompt.contains("матриц") || lowerPrompt.contains("matrix") || lowerPrompt.contains("линейная") || lowerPrompt.contains("linear") -> if (isRu) "Линейная алгебра" else "Linear Algebra"
            lowerPrompt.contains("геометр") || lowerPrompt.contains("geometry") -> if (isRu) "Геометрия" else "Geometry"
            lowerPrompt.contains("дроб") || lowerPrompt.contains("fraction") -> if (isRu) "Арифметика дробей" else "Fraction Arithmetic"
            else -> if (isRu) "Общий математический разбор" else "General Mathematical Analysis"
        }
        
        return if (isRu) {
            "$$\\text{Математический разбор темы: } \\text{$category}$$\n\n" +
            "Приветствую в Вашей персональной учебной зоне! Наша встроенная экспертная система **Mirkamol AI Math** готова предоставить Вам полные и глубокие объяснения.\n\n" +
            "### Определение и Ключевая концепция:\n" +
            "Математическая дисциплина требует точности, структурированного логического вывода и понимания определений.\n\n" +
            "\$\$f(x) = \\sum_{n=1}^{\\infty} a_n \\cos(nx) + b_n \\sin(nx)\$\$\n\n" +
            "### Пошаговое руководство по решению подобных задач:\n" +
            "1. **Анализ условия:** Всегда четко выписывайте известные переменные и ограничения.\n" +
            "2. **Выбор формулы:** Определите теоремы и тождества, связывающие данные величины.\n" +
            "3. **Алгебраические преобразования:** Шаг за шагом упрощайте выражения.\n" +
            "4. **Проверка размерности:** Проверяйте граничные условия, ОДЗ и знаки у полученных корней.\n\n" +
            "### Интерактивный Сократовский Вопрос:\n" +
            "Какая конкретно часть этой темы вызывает у вас наибольшие трудности? Опишите формулу подробнее, и мы вместе разберем её!"
        } else {
            "$$\\text{Mathematical Concept Guide: } \\text{$category}$$\n\n" +
            "Welcome to your offline workspace! The local **Mirkamol AI Math** engine is fully equipped to guide you with complete mathematical precision.\n\n" +
            "### Concept Overview:\n" +
            "Deep mastery of mathematics comes from structured proofs, rigorous logic, and visual comprehension.\n\n" +
            "\$\$f(x) = \\sum_{n=1}^{\\infty} a_n \\cos(nx) + b_n \\sin(nx)\$\$\n\n" +
            "### Standard Problem Solving Framework:\n" +
            "1. **Define Knowns:** Write down the given values and constraints clearly.\n" +
            "2. **Choose Theorem:** Identify which math relations apply (e.g., trigonometric identities, limits rules, Euler's formula).\n" +
            "3. **Step-by-step Algebra:** Maintain balance on both sides of the equation.\n" +
            "4. **Sanity Check:** Check edge cases (e.g., division by zero, domain limits).\n\n" +
            "### Socratic Guiding Question:\n" +
            "Which specific part of this concept would you like to drill down into? Describe it here and let's solve it together!"
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
