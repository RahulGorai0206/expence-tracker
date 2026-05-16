package com.myapp.expensetracker

import com.myapp.expensetracker.worker.FeatureNudgePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class FeatureNudgePlannerTest {
    private val now = TimeUnit.DAYS.toMillis(200)

    @Test
    fun randomDelayIsBetweenTenAndFifteenDays() {
        repeat(100) { seed ->
            val delay = FeatureNudgePlanner.randomDelayMillis(Random(seed))
            assertTrue(delay >= TimeUnit.DAYS.toMillis(10))
            assertTrue(delay <= TimeUnit.DAYS.toMillis(15))
        }
    }

    @Test
    fun noNotificationWhenSetupIncomplete() {
        val candidate = FeatureNudgePlanner.chooseCandidate(
            state = unusedFeatureState().copy(setupComplete = false),
            nowMillis = now,
            lastSentLookup = { 0L },
            random = Random(1)
        )

        assertNull(candidate)
    }

    @Test
    fun noNotificationWhenNotificationPermissionMissing() {
        val candidate = FeatureNudgePlanner.chooseCandidate(
            state = unusedFeatureState().copy(notificationsAllowed = false),
            nowMillis = now,
            lastSentLookup = { 0L },
            random = Random(1)
        )

        assertNull(candidate)
    }

    @Test
    fun usedFeaturesAreExcluded() {
        val features = FeatureNudgePlanner.eligibleFeatures(
            state = usedFeatureState(),
            nowMillis = now,
            lastSentLookup = { 0L }
        )

        assertEquals(emptyList<FeatureNudgePlanner.Candidate>(), features)
    }

    @Test
    fun featureNudgesUseFeatureChannelAndTipsUseTipsChannel() {
        val feature = FeatureNudgePlanner.eligibleFeatures(
            state = unusedFeatureState(),
            nowMillis = now,
            lastSentLookup = { 0L }
        ).first()
        val tip = FeatureNudgePlanner.eligibleTips(
            state = usedFeatureState().copy(transactionCount = 5),
            nowMillis = now,
            lastSentLookup = { 0L }
        ).first()

        assertEquals(FeatureNudgePlanner.FEATURE_CHANNEL_ID, feature.channelId)
        assertEquals(FeatureNudgePlanner.TIPS_CHANNEL_ID, tip.channelId)
    }

    @Test
    fun featureNudgesArePrioritizedOverTips() {
        val candidate = FeatureNudgePlanner.chooseCandidate(
            state = unusedFeatureState().copy(transactionCount = 8),
            nowMillis = now,
            lastSentLookup = { 0L },
            random = Random(1)
        )

        assertNotNull(candidate)
        assertEquals(FeatureNudgePlanner.Kind.FEATURE, candidate?.kind)
    }

    @Test
    fun sameNudgeIsSuppressedForNinetyDays() {
        val recent = now - TimeUnit.DAYS.toMillis(30)
        val features = FeatureNudgePlanner.eligibleFeatures(
            state = unusedFeatureState().copy(
                hasBudget = false,
                cloudSyncConfigured = true,
                backgroundSmsMonitoringEnabled = true,
                notificationListenerEnabled = true,
                aiModelDownloaded = true,
                homeWidgetPinned = true
            ),
            nowMillis = now,
            lastSentLookup = { key ->
                if (key == "nudge_feature_budget_last_sent_ms") recent else 0L
            }
        )

        assertEquals(emptyList<FeatureNudgePlanner.Candidate>(), features)
    }

    private fun unusedFeatureState() = FeatureNudgePlanner.FeatureUsageState(
        setupComplete = true,
        notificationsAllowed = true,
        hasBudget = false,
        cloudSyncConfigured = false,
        backgroundSmsMonitoringEnabled = false,
        notificationListenerEnabled = false,
        aiModelDownloaded = false,
        homeWidgetPinned = false,
        transactionCount = 0
    )

    private fun usedFeatureState() = FeatureNudgePlanner.FeatureUsageState(
        setupComplete = true,
        notificationsAllowed = true,
        hasBudget = true,
        cloudSyncConfigured = true,
        backgroundSmsMonitoringEnabled = true,
        notificationListenerEnabled = true,
        aiModelDownloaded = true,
        homeWidgetPinned = true,
        transactionCount = 0
    )
}
