package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "Mirkamol",
    val level: String = "Bronze League", // Bronze, Silver, Gold, Diamond, Legend
    val xp: Int = 0,
    val streak: Int = 0,
    val lastActiveTime: Long = System.currentTimeMillis(),
    val totalProblemsSolved: Int = 0,
    val isModerator: Boolean = false,
    val language: String = "RU", // "RU" or "EN"
    val isDarkMode: Boolean = true,
    val isLoggedIn: Boolean = false,
    val userEmail: String = "",
    val activeDaysMask: Int = 1 // Binary flags for Mon-Sun activity (default today is active)
)

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val sessionId: String,
    val title: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUrl: String? = null // For Problem Scanner upload simulation
)

@Entity(tableName = "saved_notes")
data class SavedNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val topic: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "test_results")
data class TestResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testType: String, // "Quiz" or "Exam"
    val topic: String,
    val difficulty: String,
    val score: Int,
    val totalQuestions: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_plans")
data class StudyPlanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val targetDate: Long,
    val isCompleted: Boolean = false
)

@Entity(tableName = "moderation_logs")
data class ModerationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String, // e.g. "REVOKE", "BAN", "WARN", "ELEVATE"
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "message", "update", "mention", "system"
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

