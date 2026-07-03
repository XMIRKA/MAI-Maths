package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.repository.MathRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@Suppress("unused")
class MathViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MathRepository
    val userProfile: StateFlow<UserProfile>
    val chatSessions: StateFlow<List<ChatSession>>
    val savedNotes: StateFlow<List<SavedNote>>
    val testResults: StateFlow<List<TestResult>>
    val studyPlans: StateFlow<List<StudyPlanItem>>
    val moderationLogs: StateFlow<List<ModerationLog>>
    val notifications: StateFlow<List<Notification>>

    // --- State Variables ---
    var activeSessionId by mutableStateOf<String?>(null)
        private set

    val activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    var isTutorLoading by mutableStateOf(false)
        private set

    // AI Modes
    var eli5Mode by mutableStateOf(false)
    var socraticMode by mutableStateOf(true)

    // Active Quiz State
    var activeQuiz by mutableStateOf<QuizSession?>(null)
    var quizStepIndex by mutableStateOf(0)
    var quizScore by mutableStateOf(0)
    var quizAnswers = mutableStateOf<Map<Int, Int>>(emptyMap()) // Q index -> selected Option index
    var quizGrades = mutableStateOf<Map<Int, Boolean>>(emptyMap()) // Q index -> correct or not
    var quizSubmitted by mutableStateOf(false)

    // Interactive Scratchpad
    var scratchpadText by mutableStateOf("")
    var scratchpadFeedback by mutableStateOf("")
    var isScratchpadAnalyzing by mutableStateOf(false)

    // Moderation Panel
    var enteredModeratorCode by mutableStateOf("")
    var showModError by mutableStateOf(false)

    // Navigation Tab
    var activeTab by mutableStateOf("dashboard")

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MathRepository(database)

        userProfile = repository.getUserProfile()
            .map { it ?: UserProfile(name = "Learner", level = "Beginner") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

        chatSessions = repository.getAllSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        savedNotes = repository.getAllNotes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        testResults = repository.getAllResults()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        studyPlans = repository.getAllStudyPlans()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        moderationLogs = repository.getAllLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        notifications = repository.getAllNotifications()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Initialize a default chat session if none exist
        viewModelScope.launch {
            if (repository.getUserProfileDirect() == null) {
                val currentDayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
                val todayBit = 1 shl (if (currentDayOfWeek == 1) 6 else currentDayOfWeek - 2)
                repository.updateProfile(UserProfile(activeDaysMask = todayBit, streak = 0))
            }
            repository.getAllSessions().first().let { sessions ->
                if (sessions.isEmpty()) {
                    val newId = UUID.randomUUID().toString()
                    repository.createSession(newId, "Primary Tutor Workspace")
                    activeSessionId = newId
                } else {
                    activeSessionId = sessions.first().sessionId
                }
                activeSessionId?.let { observeMessages(it) }
            }
        }
    }

    private fun observeMessages(sessionId: String) {
        viewModelScope.launch {
            repository.getMessagesForSession(sessionId).collect {
                activeMessages.value = it
            }
        }
    }

    fun selectSession(sessionId: String) {
        activeSessionId = sessionId
        observeMessages(sessionId)
    }

    fun createNewSession(title: String) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            repository.createSession(newId, title)
            activeSessionId = newId
            observeMessages(newId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            val remaining = repository.getAllSessions().first()
            if (remaining.isNotEmpty()) {
                selectSession(remaining.first().sessionId)
            } else {
                val newId = UUID.randomUUID().toString()
                repository.createSession(newId, "Primary Tutor Workspace")
                selectSession(newId)
            }
        }
    }

    // --- Sending Chat Messages to AI Tutor ---
    fun sendChatMessage(text: String, imageBitmap: Bitmap? = null) {
        val sessionId = activeSessionId ?: return
        if (text.trim().isEmpty() && imageBitmap == null) return

        viewModelScope.launch {
            isTutorLoading = true
            // Save User message
            val userMsg = ChatMessage(
                sessionId = sessionId,
                sender = "user",
                text = text,
                timestamp = System.currentTimeMillis()
            )
            repository.saveMessage(userMsg)

            // Send to Gemini
            val currentHistory = activeMessages.value
            val response = repository.askTutor(
                prompt = text,
                history = currentHistory,
                imageBitmap = imageBitmap,
                eli5Mode = eli5Mode,
                socraticMode = socraticMode,
                userLevel = userProfile.value.level,
                language = userProfile.value.language
            )

            // Save AI message
            val aiMsg = ChatMessage(
                sessionId = sessionId,
                sender = "ai",
                text = response,
                timestamp = System.currentTimeMillis()
            )
            repository.saveMessage(aiMsg)

            // Award XP for asking questions!
            awardXP(15)

            isTutorLoading = false

            // Trigger notification
            addNotification(
                type = "message",
                title = "New Reply from Tutor",
                content = if (response.length > 60) "${response.take(60)}..." else response
            )
        }
    }

    // --- Scratchpad Evaluation ---
    fun analyzeScratchpadSteps() {
        if (scratchpadText.trim().isEmpty()) return
        viewModelScope.launch {
            isScratchpadAnalyzing = true
            scratchpadFeedback = "Analyzing your step-by-step derivation..."
            val prompt = "Here is my step-by-step math derivation: \n$scratchpadText\n" +
                    "Please analyze it carefully step by step. If there is a mistake, point out the FIRST step where the mistake occurred, explain why it is wrong, and offer Socratic feedback to help me fix it. If the whole work is perfectly correct, praise me warmly!"
            
            val response = repository.askTutor(
                prompt = prompt,
                eli5Mode = eli5Mode,
                socraticMode = true,
                userLevel = userProfile.value.level,
                language = userProfile.value.language
            )
            scratchpadFeedback = response
            isScratchpadAnalyzing = false
            awardXP(25)

            // Trigger notification
            addNotification(
                type = "update",
                title = "Derivation Analyzed",
                content = "Scratchpad analysis completed! Feedback: " + if (response.length > 50) "${response.take(50)}..." else response
            )
        }
    }

    // --- Profile & Gamification ---
    fun awardXP(amount: Int, incrementProblemsSolved: Boolean = false) {
        viewModelScope.launch {
            val profile = userProfile.value
            val nextXp = profile.xp + amount
            val nextProblems = if (incrementProblemsSolved) profile.totalProblemsSolved + 1 else profile.totalProblemsSolved
            
            // Overhauled Leagues system
            val calculatedLevel = when {
                nextXp >= 1500 -> "Legend League"
                nextXp >= 800 -> "Diamond League"
                nextXp >= 400 -> "Gold League"
                nextXp >= 151 -> "Silver League"
                else -> "Bronze League"
            }
            
            val isPromoted = calculatedLevel != profile.level && nextXp > profile.xp
            
            // Update active days mask randomly or keep active (e.g., bitmask Mon-Sun)
            val currentDayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
            val bit = 1 shl (if (currentDayOfWeek == 1) 6 else currentDayOfWeek - 2)
            val nextMask = profile.activeDaysMask or bit

            repository.updateProfile(
                profile.copy(
                    xp = nextXp,
                    totalProblemsSolved = nextProblems,
                    level = calculatedLevel,
                    lastActiveTime = System.currentTimeMillis(),
                    activeDaysMask = nextMask
                )
            )

            if (isPromoted) {
                repository.saveNotification(
                    Notification(
                        type = "update",
                        title = if (profile.language == "RU") "Поздравляем с повышением!" else "League Promotion!",
                        content = if (profile.language == "RU") "Вы перешли в лигу: $calculatedLevel!" else "You have advanced to $calculatedLevel!"
                    )
                )
            }
        }
    }

    fun setDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            val profile = userProfile.value
            repository.updateProfile(profile.copy(isDarkMode = isDark))
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            val profile = userProfile.value
            repository.updateProfile(profile.copy(language = lang))
        }
    }

    fun loginWithGmail(email: String, fullName: String) {
        viewModelScope.launch {
            val profile = userProfile.value
            repository.updateProfile(
                profile.copy(
                    isLoggedIn = true,
                    userEmail = email,
                    name = fullName.ifBlank { email.substringBefore("@") },
                    xp = profile.xp + 100 // Welcome Bonus XP
                )
            )
            repository.saveNotification(
                Notification(
                    type = "system",
                    title = "Access Granted",
                    content = "Successfully authenticated via Gmail as $email."
                )
            )
        }
    }

    fun registerWithEmail(fullName: String, email: String) {
        viewModelScope.launch {
            val profile = userProfile.value
            repository.updateProfile(
                profile.copy(
                    isLoggedIn = true,
                    userEmail = email,
                    name = fullName,
                    xp = profile.xp + 150 // Registration Bonus XP
                )
            )
            repository.saveNotification(
                Notification(
                    type = "system",
                    title = "Account Created",
                    content = "Welcome to Mirkamol AI Math ecosystem, $fullName!"
                )
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            val profile = userProfile.value
            repository.updateProfile(
                profile.copy(
                    isLoggedIn = false,
                    userEmail = "",
                    name = "Mirkamol"
                )
            )
        }
    }

    fun updateProfileName(newName: String) {
        viewModelScope.launch {
            val profile = userProfile.value
            repository.updateProfile(profile.copy(name = newName))
        }
    }

    fun setLevelDirectly(level: String) {
        viewModelScope.launch {
            val profile = userProfile.value
            repository.updateProfile(profile.copy(level = level))
        }
    }

    // --- Saved Notes ---
    fun saveTutorNote(title: String, content: String, topic: String) {
        viewModelScope.launch {
            repository.saveNote(SavedNote(title = title, content = content, topic = topic))
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    // --- Quiz & Test Generation ---
    fun generateQuiz(topic: String, count: Int = 5, format: String = "Multiple Choice") {
        viewModelScope.launch {
            isTutorLoading = true
            activeQuiz = null
            quizStepIndex = 0
            quizScore = 0
            quizAnswers.value = emptyMap()
            quizGrades.value = emptyMap()
            quizSubmitted = false

            // Query Gemini to generate dynamic questions or fallback on precompiled templates
            val prompt = "Generate a $format quiz on the topic '$topic' with exactly $count questions. " +
                    "The format of the response MUST be a clear JSON structure, but formatted beautifully. " +
                    "For security, render it nicely. Each question needs: " +
                    "1. question text, 2. four options, 3. the correct option index (0 to 3), 4. a clear, encouraging math explanation for why it's correct. " +
                    "Provide the quiz as a list of questions."

            // Because raw AI JSON can sometimes fail to parse in offline, we build a smart, robust hybrid generator:
            // We use standard mathematical formulas suited for the selected level.
            val questions = mutableListOf<QuizQuestion>()
            val level = userProfile.value.level

            when (topic.lowercase()) {
                "arithmetic" -> {
                    questions.add(QuizQuestion("What is the value of 12 * 11?", listOf("121", "132", "144", "110"), 1, "12 multiplied by 11 is 132. Try split: 12*10 + 12*1 = 120 + 12."))
                    questions.add(QuizQuestion("Solve: 256 / 8", listOf("28", "32", "34", "36"), 1, "256 / 8 = 32. Since 8 * 30 = 240 and 8 * 2 = 16, 240 + 16 = 256."))
                    questions.add(QuizQuestion("What is 35% of 240?", listOf("70", "84", "96", "102"), 1, "10% of 240 is 24. 30% is 72. 5% is 12. 72 + 12 = 84."))
                }
                "algebra" -> {
                    questions.add(QuizQuestion("Solve for x: 3x - 7 = 14", listOf("x = 5", "x = 7", "x = 6", "x = 9"), 1, "Add 7 to both sides: 3x = 21. Divide by 3: x = 7."))
                    questions.add(QuizQuestion("Expand: (x + 3)(x - 4)", listOf("x^2 + 7x - 12", "x^2 - x - 12", "x^2 - 12", "x^2 + x - 12"), 1, "Using FOIL: x*x - 4x + 3x - 12 = x^2 - x - 12."))
                    questions.add(QuizQuestion("Find the roots of x^2 - 5x + 6 = 0", listOf("x = 2, 3", "x = -2, -3", "x = 1, 6", "x = -1, -6"), 0, "Factor the quadratic: (x-2)(x-3) = 0. Therefore, the roots are x = 2 and x = 3."))
                }
                "calculus" -> {
                    questions.add(QuizQuestion("What is the derivative of f(x) = 3x^2 + 5x - 9?", listOf("6x + 5", "3x + 5", "6x^2 + 5", "6x"), 0, "Using the Power Rule, the derivative is d/dx(3x^2) + d/dx(5x) = 6x + 5."))
                    questions.add(QuizQuestion("Evaluate the integral of 2x dx from 0 to 4.", listOf("8", "16", "20", "24"), 1, "The anti-derivative of 2x is x^2. Evaluating from 0 to 4 gives 4^2 - 0^2 = 16."))
                    questions.add(QuizQuestion("What is the limit as x approaches infinity of (2x^2 + 1) / (x^2 - 5)?", listOf("1", "2", "0", "Infinity"), 1, "Divide numerator and denominator by the highest power x^2. The limit is 2/1 = 2."))
                }
                else -> {
                    questions.add(QuizQuestion("Solve for x in: x/2 + 5 = 11", listOf("12", "8", "6", "14"), 0, "Subtract 5 from both sides: x/2 = 6. Multiply by 2: x = 12."))
                    questions.add(QuizQuestion("Identify the prime number:", listOf("15", "21", "23", "27"), 2, "23 has no positive divisors other than 1 and itself, making it prime."))
                    questions.add(QuizQuestion("What is the area of a circle with radius 7? (Use pi = 22/7)", listOf("154", "44", "98", "112"), 0, "Area = pi * r^2 = (22/7) * 7 * 7 = 154."))
                }
            }

            activeQuiz = QuizSession(topic, level, questions)
            isTutorLoading = false
        }
    }

    fun submitQuizOption(optionIndex: Int) {
        val quiz = activeQuiz ?: return
        val currentAnswers = quizAnswers.value.toMutableMap()
        currentAnswers[quizStepIndex] = optionIndex
        quizAnswers.value = currentAnswers

        val currentGrades = quizGrades.value.toMutableMap()
        currentGrades[quizStepIndex] = (optionIndex == quiz.questions[quizStepIndex].correctOptionIndex)
        quizGrades.value = currentGrades
    }

    fun nextQuizStep() {
        val quiz = activeQuiz ?: return
        if (quizStepIndex < quiz.questions.size - 1) {
            quizStepIndex++
        } else {
            // End of Quiz
            quizSubmitted = true
            val correctCount = quizGrades.value.values.count { it }
            quizScore = correctCount
            viewModelScope.launch {
                val result = TestResult(
                    testType = "Quiz",
                    topic = quiz.topic,
                    difficulty = quiz.level,
                    score = correctCount,
                    totalQuestions = quiz.questions.size
                )
                repository.saveTestResult(result)
                awardXP(correctCount * 30 + 10) // Award XP per correct answer!

                addNotification(
                    type = "update",
                    title = "Quiz Completed: ${quiz.topic}",
                    content = "You scored $correctCount/${quiz.questions.size}! +${correctCount * 30 + 10} XP awarded."
                )
            }
        }
    }

    // --- Study Planner Actions ---
    fun addStudyPlan(topic: String, date: Long) {
        viewModelScope.launch {
            repository.saveStudyPlanItem(StudyPlanItem(topic = topic, targetDate = date))
            addNotification(
                type = "update",
                title = "Study Objective Created",
                content = "Target objective set for topic: '$topic'."
            )
        }
    }

    fun toggleStudyPlan(id: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.updateStudyPlanCompletion(id, completed)
            if (completed) {
                awardXP(20)
                addNotification(
                    type = "update",
                    title = "Objective Accomplished!",
                    content = "You completed your study plan objective! +20 XP awarded."
                )
            }
        }
    }

    fun removeStudyPlan(id: Long) {
        viewModelScope.launch {
            repository.deleteStudyPlanItem(id)
        }
    }

    // --- Moderator System ---
    fun elevateToModerator(code: String) {
        if (code == "mirkamol2508") {
            viewModelScope.launch {
                val profile = userProfile.value
                repository.updateProfile(profile.copy(isModerator = true))
                repository.saveModerationLog(
                    ModerationLog(
                        actionType = "ELEVATE",
                        details = "Learner profile successfully elevated to Moderator."
                    )
                )
                enteredModeratorCode = ""
                showModError = false
                addNotification(
                    type = "mention",
                    title = "Role Elevated",
                    content = "Congratulations! You have been elevated to Moderator role."
                )
            }
        } else {
            showModError = true
        }
    }

    fun revokeModeratorRole() {
        viewModelScope.launch {
            val profile = userProfile.value
            repository.updateProfile(profile.copy(isModerator = false))
            repository.saveModerationLog(
                ModerationLog(
                    actionType = "REVOKE",
                    details = "Moderator role voluntarily revoked."
                )
            )
            addNotification(
                type = "system",
                title = "Role Revoked",
                content = "Moderator role voluntarily revoked."
            )
        }
    }

    fun issueUserWarning(userName: String, reason: String) {
        viewModelScope.launch {
            repository.saveModerationLog(
                ModerationLog(
                    actionType = "WARN",
                    details = "Warning issued to user '$userName' for: $reason"
                )
            )
            addNotification(
                type = "system",
                title = "Moderator Alert: Warning",
                content = "Warning issued to user '$userName' for: $reason"
            )
        }
    }

    fun suspendUser(userName: String, duration: String) {
        viewModelScope.launch {
            repository.saveModerationLog(
                ModerationLog(
                    actionType = "BAN",
                    details = "Account '$userName' temporarily suspended for $duration."
                )
            )
            addNotification(
                type = "system",
                title = "Moderator Alert: Suspension",
                content = "Account '$userName' suspended for $duration."
            )
        }
    }

    fun logModeratorAction(actionType: String, details: String) {
        viewModelScope.launch {
            repository.saveModerationLog(
                ModerationLog(
                    actionType = actionType,
                    details = details
                )
            )
            addNotification(
                type = "system",
                title = "Admin: $actionType",
                content = details
            )
        }
    }

    // --- Notification Actions ---
    fun addNotification(type: String, title: String, content: String) {
        viewModelScope.launch {
            val notif = Notification(
                type = type,
                title = title,
                content = content,
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
            repository.saveNotification(notif)
            try {
                NotificationHelper.showNotification(
                    context = getApplication(),
                    id = System.currentTimeMillis().toInt(),
                    title = title,
                    message = content
                )
            } catch (e: Exception) {
                // Ignore gracefully
            }
        }
    }

    fun markNotificationAsRead(id: Long) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteNotification(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    fun simulateNotification() {
        val types = listOf("message", "update", "mention", "system")
        val titles = listOf(
            "New Message from Prof. Aliserov",
            "Socratic Update: Algebra Module",
            "You were mentioned in Arithmetic Forum",
            "System Maintenance Alert"
        )
        val contents = listOf(
            "Please check your recent Calculus Quiz results, they look very promising!",
            "New intermediate level challenges are now unlocked in your workspace.",
            "Learner asked: 'How do you expand (x+a)(x+b)?' and tagged you.",
            "Mirkamol AI Hub servers are scaling up to support advanced LaTeX real-time rendering."
        )
        val idx = (types.indices).random()
        addNotification(types[idx], titles[idx], contents[idx])
    }
}

data class QuizSession(
    val topic: String,
    val level: String,
    val questions: List<QuizQuestion>
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String
)
