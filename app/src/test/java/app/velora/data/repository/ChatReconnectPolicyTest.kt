package app.velora.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatReconnectPolicyTest {
    @Test fun `reconnect backoff grows exponentially and is capped`() {
        assertEquals(listOf(1_000L,2_000L,4_000L,8_000L,16_000L,30_000L,30_000L),(0L..6L).map(::reconnectDelayMillis))
    }
}
