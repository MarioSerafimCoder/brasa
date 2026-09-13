@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaybackErrorPolicyTest {
    private val spec = DataSpec.Builder().setUri("http://localhost/video").build()
    private fun http(status: Int) = HttpDataSource.InvalidResponseCodeException(status, "test", null, mapOf("Retry-After" to listOf("8")), spec, byteArrayOf())
    @Test fun authenticationAndMissingOriginalAreNotRetried() {
        for (status in listOf(401, 403, 404, 410, 400)) {
            val error = PlaybackException("test", http(status), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
            assertEquals(PlaybackErrorAction.STOP, PlaybackErrorPolicy.decide(error, false).action)
        }
    }
    @Test fun temporaryServerFailuresRespectRetryAfter() {
        for (status in listOf(408, 429, 500, 502, 503, 504)) {
            val decision = PlaybackErrorPolicy.decide(PlaybackException("test", http(status), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS), false)
            assertEquals(PlaybackErrorAction.RETRY, decision.action)
            assertEquals(8000, decision.delayMs)
        }
    }
    @Test fun hlsMissingSegmentIsRenewedOnlyOnceAcrossPlayerRecreations() {
        assertEquals(PlaybackErrorAction.RENEW_STREAM, PlaybackErrorPolicy.http(404, true).action)
        val recovery = PlaybackRecovery()
        assertTrue(recovery.beginSourceRenewal())
        recovery.resetSampling()
        assertFalse(recovery.beginSourceRenewal())
    }
    @Test fun malformedFilesAreNotSentThroughRepeatedConversions() {
        assertEquals(PlaybackErrorAction.STOP, PlaybackErrorPolicy.decide(PlaybackException("test", null, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED), false).action)
        assertEquals(PlaybackErrorAction.TRANSCODE, PlaybackErrorPolicy.decide(PlaybackException("test", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED), false).action)
        assertEquals(PlaybackErrorAction.STOP, PlaybackErrorPolicy.decide(PlaybackException("test", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED), true).action)
        assertEquals(PlaybackErrorAction.RETRY, PlaybackErrorPolicy.decide(PlaybackException("test", SocketException("reset"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED), false).action)
    }
    @Test fun loaderStopsPermanentErrorsAndBoundsMissingSegmentRetries() {
        fun info(status: Int, count: Int) = LoadErrorHandlingPolicy.LoadErrorInfo(LoadEventInfo(1, spec, 0), MediaLoadData(C.DATA_TYPE_MEDIA), http(status), count)
        val original = TvLoadErrorHandlingPolicy(false)
        for (status in listOf(401, 403, 404, 410)) assertEquals(C.TIME_UNSET, original.getRetryDelayMsFor(info(status, 1)))
        assertEquals(8000, original.getRetryDelayMsFor(info(503, 1)))
        assertEquals(C.TIME_UNSET, original.getRetryDelayMsFor(info(503, 4)))
        val hls = TvLoadErrorHandlingPolicy(true)
        assertEquals(1000, hls.getRetryDelayMsFor(info(404, 1)))
        assertEquals(C.TIME_UNSET, hls.getRetryDelayMsFor(info(404, 3)))
    }
}
