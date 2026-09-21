package com.brasa.tv.core.network

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException

class ConnectionProblemTest {
    @Test fun distinguishesNetworkComputerAndAuthorization() {
        assertEquals(ConnectionProblem.NO_NETWORK, connectionProblem(ConnectException(), false))
        assertEquals(ConnectionProblem.SERVER_UNAVAILABLE, connectionProblem(ConnectException(), true))
        assertEquals(ConnectionProblem.REVOKED, connectionProblem(DeviceRevokedException(), true))
        assertEquals(ConnectionProblem.FORBIDDEN, connectionProblem(BrasaApiException(403, "blocked"), true))
        assertEquals(ConnectionProblem.INCOMPATIBLE, connectionProblem(BrasaApiException(200, "bad data"), true))
        assertEquals(ConnectionProblem.SERVER_ERROR, connectionProblem(BrasaApiException(503, "busy"), true))
    }
    @Test(expected = CancellationException::class) fun cancellationIsNotAConnectionError() {
        connectionProblem(CancellationException(), true)
    }
}
