@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.core.playback

import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.SocketException

enum class PlaybackErrorAction { RETRY, RENEW_STREAM, TRANSCODE, STOP }
data class PlaybackErrorDecision(val action: PlaybackErrorAction, val message: String, val delayMs: Long = 1500)

object PlaybackErrorPolicy {
    fun decide(error: PlaybackException, hls: Boolean): PlaybackErrorDecision {
        val http = cause<HttpDataSource.InvalidResponseCodeException>(error)
        if (http != null) return http(http.responseCode, hls, retryAfter(http))
        if (cause<FileNotFoundException>(error) != null || error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
            return missing(hls)
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> temporary()
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlaybackErrorDecision(PlaybackErrorAction.RENEW_STREAM, "O trecho de streaming expirou. Preparando novamente no ponto assistido.")
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackErrorDecision(PlaybackErrorAction.STOP, "A TV não tem permissão para acessar este vídeo. Confira o pareamento e as permissões no computador.")
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> PlaybackErrorDecision(PlaybackErrorAction.STOP, "O servidor enviou uma resposta que não é um vídeo válido. Confira o servidor BRasa e atualize a biblioteca.")
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> PlaybackErrorDecision(PlaybackErrorAction.STOP, "O arquivo ou a lista de reprodução contém dados inválidos. Confira a integridade do vídeo no computador.")
            PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                PlaybackErrorDecision(if (hls) PlaybackErrorAction.STOP else PlaybackErrorAction.TRANSCODE, "A TV não conseguiu decodificar o vídeo. Confira o formato e a capacidade do aparelho.")
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> if (cause<SocketException>(error) != null || cause<SocketTimeoutException>(error) != null || cause<UnknownHostException>(error) != null) temporary()
                else PlaybackErrorDecision(PlaybackErrorAction.STOP, "Falha ao ler o vídeo. Confira o arquivo e a conexão com o computador antes de tentar novamente.")
            else -> PlaybackErrorDecision(PlaybackErrorAction.STOP, "Não foi possível reproduzir esta mídia. Confira o arquivo no computador.")
        }
    }
    fun http(status: Int, hls: Boolean, retryMs: Long = 1500): PlaybackErrorDecision = when (status) {
        401, 403 -> PlaybackErrorDecision(PlaybackErrorAction.STOP, "Acesso não autorizado. Confira se a TV continua pareada e se este perfil pode assistir ao título.")
        404, 410 -> missing(hls)
        416 -> PlaybackErrorDecision(PlaybackErrorAction.RENEW_STREAM, "O servidor não aceitou o ponto solicitado. Preparando novamente a reprodução.")
        408, 429, 500, 502, 503, 504 -> temporary(retryMs)
        else -> PlaybackErrorDecision(PlaybackErrorAction.STOP, "O servidor respondeu com um erro ($status). Confira o servidor BRasa antes de tentar novamente.")
    }
    private fun missing(hls: Boolean) = PlaybackErrorDecision(if (hls) PlaybackErrorAction.RENEW_STREAM else PlaybackErrorAction.STOP,
        if (hls) "Um trecho do streaming não está mais disponível. Preparando novamente no ponto assistido." else "Arquivo não encontrado. Confira se o vídeo foi movido ou removido e atualize a biblioteca no computador.")
    private fun temporary(delay: Long = 1500) = PlaybackErrorDecision(PlaybackErrorAction.RETRY, "A conexão com o computador foi interrompida ou o servidor está ocupado. Tente novamente quando a conexão estabilizar.", delay.coerceIn(1000, 30_000))
    internal inline fun <reified T : Throwable> cause(error: Throwable): T? {
        var current: Throwable? = error
        repeat(12) { if (current is T) return current; current = current?.cause }
        return null
    }
    internal fun retryAfter(error: HttpDataSource.InvalidResponseCodeException): Long =
        (error.headerFields.entries.find { it.key.equals("Retry-After", true) }?.value?.firstOrNull()?.toLongOrNull()?.coerceIn(1, 30)?.times(1000)) ?: 1500
}

/** Media3's own loader must also stop retrying permanent failures. */
class TvLoadErrorHandlingPolicy(private val hls: Boolean) : DefaultLoadErrorHandlingPolicy(3) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (info.errorCount > 3) return C.TIME_UNSET
        val error = info.exception
        val http = PlaybackErrorPolicy.cause<HttpDataSource.InvalidResponseCodeException>(error)
        if (http != null) {
            if (hls && http.responseCode == 404 && info.errorCount <= 2) return 1000
            val decision = PlaybackErrorPolicy.http(http.responseCode, hls, PlaybackErrorPolicy.retryAfter(http))
            return if (decision.action == PlaybackErrorAction.RETRY) (decision.delayMs * info.errorCount).coerceAtMost(30_000) else C.TIME_UNSET
        }
        if (error is FileNotFoundException || error is ParserException || error is HttpDataSource.InvalidContentTypeException) return C.TIME_UNSET
        return super.getRetryDelayMsFor(info)
    }
}
