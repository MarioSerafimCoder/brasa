package com.brasa.tv.core.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun refillingBufferIsNotMistakenForFrozenDecoder() {
        val recovery = PlaybackRecovery()
        for (time in 0L..90_000L step 2_000L) {
            assertFalse(recovery.sample(time, 60_000, true, buffering = true, bufferedPositionMs = 60_000 + time / 2))
        }
        assertFalse(recovery.sample(92_000, 62_000, true))
    }
    @Test fun bufferingWithoutIncomingDataEventuallyReconnects() {
        val recovery = PlaybackRecovery()
        for (time in 0L..46_000L step 2_000L) assertFalse(recovery.sample(time, 60_000, true, true, 61_000))
        assertTrue(recovery.sample(48_000, 60_000, true, true, 61_000))
    }
    @Test fun tricklingConnectionCannotBufferForever() {
        val recovery = PlaybackRecovery()
        for (time in 0L..120_000L step 2_000L) assertFalse(recovery.sample(time, 0, true, true, time / 2))
        assertTrue(recovery.sample(122_000, 0, true, true, 61_000))
    }
    @Test fun pauseDuringBufferingNeverReconnects() {
        val recovery = PlaybackRecovery()
        for (time in 0L..180_000L step 2_000L) assertFalse(recovery.sample(time, 0, false, true, 1_000))
    }
    @Test fun frozenPositionTriggersRecoveryEvenIfPlayerReportsPlaying() {
        val recovery = PlaybackRecovery()
        for (time in 0L..12_000L step 2_000L) assertFalse(recovery.sample(time, 552_104, true))
        assertTrue(recovery.sample(14_000, 552_104, true))
    }
    @Test fun manualPauseAndAudioFocusSuppressionNeverTriggerRecovery() {
        val recovery = PlaybackRecovery()
        for (time in 0L..120_000L step 2_000L) assertFalse(recovery.sample(time, 552_104, false))
        assertFalse(recovery.sample(122_000, 552_104, true))
    }
    @Test fun seekBackwardsRestartsStallClock() {
        val recovery = PlaybackRecovery()
        for (time in 0L..12_000L step 2_000L) recovery.sample(time, 100_000, true)
        assertFalse(recovery.sample(14_000, 50_000, true))
        assertFalse(recovery.sample(16_000, 50_000, true))
    }
    @Test fun briefFirstFrameDoesNotCreateUnlimitedRetries() {
        val recovery = PlaybackRecovery()
        assertTrue(recovery.beginRetry())
        recovery.sample(0, 1_000, true)
        recovery.sample(2_000, 3_000, true)
        assertTrue(recovery.beginRetry())
        assertFalse(recovery.beginRetry())
        for (time in 0L..62_000L step 2_000L) recovery.sample(time, time, true)
        assertTrue(recovery.beginRetry())
    }
    @Test fun truncatedXmenEpisodeIsNotMarkedCompleted() {
        assertTrue(PlaybackRecovery.isPrematureEnd(611_833, 1_745_792))
        assertFalse(PlaybackRecovery.isPrematureEnd(1_740_000, 1_745_792))
        assertFalse(PlaybackRecovery.isPrematureEnd(600_000, 0))
    }
    @Test fun repeatedEarlyEndAtSameDamagedPositionStopsAutomaticLoop() {
        val recovery = PlaybackRecovery()
        assertTrue(recovery.canRecoverPrematureEnd(611_833))
        assertFalse(recovery.canRecoverPrematureEnd(612_000))
        assertTrue(recovery.canRecoverPrematureEnd(900_000))
    }
}
