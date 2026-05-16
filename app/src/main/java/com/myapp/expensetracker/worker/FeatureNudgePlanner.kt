package com.myapp.expensetracker.worker

import java.util.concurrent.TimeUnit
import kotlin.random.Random

object FeatureNudgePlanner {
    const val UNIQUE_WORK_NAME = "feature_nudge_worker"
    const val FEATURE_CHANNEL_ID = "feature_suggestions"
    const val TIPS_CHANNEL_ID = "usage_tips"

    const val PREF_NEXT_AFTER_MS = "nudge_next_after_ms"
    const val PREF_LAST_SENT_MS = "nudge_last_sent_ms"
    const val PREF_LAST_KIND = "nudge_last_kind"

    private val minDelayMillis = TimeUnit.DAYS.toMillis(10)
    private val maxDelayMillis = TimeUnit.DAYS.toMillis(15)
    private val repeatSuppressionMillis = TimeUnit.DAYS.toMillis(90)

    enum class Kind {
        FEATURE,
        TIP
    }

    data class FeatureUsageState(
        val setupComplete: Boolean,
        val notificationsAllowed: Boolean,
        val hasBudget: Boolean,
        val cloudSyncConfigured: Boolean,
        val backgroundSmsMonitoringEnabled: Boolean,
        val notificationListenerEnabled: Boolean,
        val aiModelDownloaded: Boolean,
        val homeWidgetPinned: Boolean,
        val transactionCount: Int
    )

    data class Candidate(
        val id: String,
        val kind: Kind,
        val channelId: String,
        val notificationId: Int,
        val title: String,
        val text: String,
        val targetArea: String
    )

    fun randomDelayMillis(random: Random = Random.Default): Long {
        return random.nextLong(minDelayMillis, maxDelayMillis + 1)
    }

    fun candidateLastSentKey(candidate: Candidate): String {
        val prefix = when (candidate.kind) {
            Kind.FEATURE -> "nudge_feature"
            Kind.TIP -> "nudge_tip"
        }
        return "${prefix}_${candidate.id}_last_sent_ms"
    }

    fun eligibleFeatures(
        state: FeatureUsageState,
        nowMillis: Long,
        lastSentLookup: (String) -> Long
    ): List<Candidate> {
        if (!state.setupComplete || !state.notificationsAllowed) return emptyList()

        return buildList {
            if (!state.hasBudget) {
                add(
                    featureCandidate(
                        id = "budget",
                        notificationId = 3101,
                        title = "Set a monthly budget",
                        text = "Add a budget to see how your spending is tracking this month.",
                        targetArea = "settings_budget"
                    )
                )
            }
            if (!state.cloudSyncConfigured) {
                add(
                    featureCandidate(
                        id = "cloud_sync",
                        notificationId = 3102,
                        title = "Back up expenses with Cloud Sync",
                        text = "Connect your sheet to keep transactions backed up across devices.",
                        targetArea = "settings_cloud_sync"
                    )
                )
            }
            if (!state.backgroundSmsMonitoringEnabled) {
                add(
                    featureCandidate(
                        id = "background_sms",
                        notificationId = 3103,
                        title = "Capture new SMS expenses automatically",
                        text = "Turn on background monitoring so new bank messages can be detected.",
                        targetArea = "settings_sms_monitoring"
                    )
                )
            }
            if (!state.notificationListenerEnabled) {
                add(
                    featureCandidate(
                        id = "notification_tracking",
                        notificationId = 3104,
                        title = "Track notification and RCS transactions",
                        text = "Enable notification access to catch transactions that do not arrive as SMS.",
                        targetArea = "settings_notification_access"
                    )
                )
            }
            if (!state.aiModelDownloaded) {
                add(
                    featureCandidate(
                        id = "ai_lazy_sync",
                        notificationId = 3105,
                        title = "Try AI Lazy Sync",
                        text = "Download the local AI model to scan older messages for missed expenses.",
                        targetArea = "settings_ai_lazy_sync"
                    )
                )
            }
            if (!state.homeWidgetPinned) {
                add(
                    featureCandidate(
                        id = "home_widget",
                        notificationId = 3106,
                        title = "Add the expense widget",
                        text = "Pin the home widget for a quick glance at your monthly spending.",
                        targetArea = "settings_widget"
                    )
                )
            }
        }.filter { isRepeatAllowed(it, nowMillis, lastSentLookup) }
    }

    fun eligibleTips(
        state: FeatureUsageState,
        nowMillis: Long,
        lastSentLookup: (String) -> Long
    ): List<Candidate> {
        if (!state.setupComplete || !state.notificationsAllowed) return emptyList()

        return buildList {
            if (state.transactionCount >= 3) {
                add(
                    tipCandidate(
                        id = "analytics_review",
                        notificationId = 3201,
                        title = "Review your spending rhythm",
                        text = "Analytics can show where this month's expenses are clustering.",
                        targetArea = "analytics"
                    )
                )
            }
            if (state.transactionCount >= 5) {
                add(
                    tipCandidate(
                        id = "category_trends",
                        notificationId = 3202,
                        title = "Check category trends",
                        text = "A quick category review can reveal which habits changed recently.",
                        targetArea = "analytics_categories"
                    )
                )
            }
            if (state.hasBudget && state.transactionCount >= 1) {
                add(
                    tipCandidate(
                        id = "budget_review",
                        notificationId = 3203,
                        title = "Compare spending with your budget",
                        text = "Open your budget view to see how much room is left this month.",
                        targetArea = "home_budget"
                    )
                )
            }
            if (state.homeWidgetPinned && state.transactionCount >= 1) {
                add(
                    tipCandidate(
                        id = "widget_glance",
                        notificationId = 3204,
                        title = "Use the widget for quick checks",
                        text = "Your widget can give you a fast spending glance without opening the app.",
                        targetArea = "home"
                    )
                )
            }
        }.filter { isRepeatAllowed(it, nowMillis, lastSentLookup) }
    }

    fun chooseCandidate(
        state: FeatureUsageState,
        nowMillis: Long,
        lastSentLookup: (String) -> Long,
        random: Random = Random.Default
    ): Candidate? {
        val features = eligibleFeatures(state, nowMillis, lastSentLookup)
        if (features.isNotEmpty()) return features.random(random)

        val tips = eligibleTips(state, nowMillis, lastSentLookup)
        if (tips.isNotEmpty()) return tips.random(random)

        return null
    }

    private fun featureCandidate(
        id: String,
        notificationId: Int,
        title: String,
        text: String,
        targetArea: String
    ) = Candidate(
        id = id,
        kind = Kind.FEATURE,
        channelId = FEATURE_CHANNEL_ID,
        notificationId = notificationId,
        title = title,
        text = text,
        targetArea = targetArea
    )

    private fun tipCandidate(
        id: String,
        notificationId: Int,
        title: String,
        text: String,
        targetArea: String
    ) = Candidate(
        id = id,
        kind = Kind.TIP,
        channelId = TIPS_CHANNEL_ID,
        notificationId = notificationId,
        title = title,
        text = text,
        targetArea = targetArea
    )

    private fun isRepeatAllowed(
        candidate: Candidate,
        nowMillis: Long,
        lastSentLookup: (String) -> Long
    ): Boolean {
        val lastSentMillis = lastSentLookup(candidateLastSentKey(candidate))
        return lastSentMillis <= 0L || nowMillis - lastSentMillis >= repeatSuppressionMillis
    }
}
