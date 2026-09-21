package com.brasa.tv.core.network

import kotlinx.coroutines.CancellationException
import java.io.IOException

enum class ConnectionProblem(val title: String, val explanation: String) {
    NO_NETWORK("TV sem conexão com a rede", "Conecte a TV ao Wi-Fi ou ao cabo de rede e tente novamente."),
    SERVER_UNAVAILABLE("Computador indisponível", "O computador pode estar desligado, o BRasa pode estar fechado ou o endereço pode ter mudado. Confira o computador e tente novamente. Seu pareamento foi mantido."),
    REVOKED("Autorização da TV revogada", "Autorize esta TV novamente no painel do BRasa no computador."),
    INCOMPATIBLE("Servidor incompatível", "Atualize o servidor BRasa no computador e tente novamente."),
    FORBIDDEN("Acesso não permitido", "Confira se o acesso pela rede está ativado e se este aparelho pode usar o perfil escolhido."),
    SERVER_ERROR("O servidor não concluiu a operação", "O computador respondeu, mas não conseguiu atender ao pedido. Tente novamente."),
}

class ConnectionException(val problem: ConnectionProblem, cause: Throwable? = null) : IOException(problem.explanation, cause)

fun connectionProblem(error: Throwable, networkAvailable: Boolean): ConnectionProblem {
    if (error is CancellationException) throw error
    if (error is ConnectionException) return error.problem
    if (error is DeviceRevokedException) return ConnectionProblem.REVOKED
    if (error is BrasaApiException) return when {
        error.status == 403 -> ConnectionProblem.FORBIDDEN
        error.status in 200..299 || error.status == 404 -> ConnectionProblem.INCOMPATIBLE
        else -> ConnectionProblem.SERVER_ERROR
    }
    if (!networkAvailable) return ConnectionProblem.NO_NETWORK
    return ConnectionProblem.SERVER_UNAVAILABLE
}
