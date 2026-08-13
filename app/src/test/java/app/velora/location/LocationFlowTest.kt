package app.velora.location

import app.velora.domain.model.ProfileLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFlowTest {
    @Test fun `granted approximate permission produces a gps profile location`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Located(ProfileLocation("Russia", "Tatarstan", "Kazan", 55.796, 49.109, "gps", 250)))
        val result = provider.current()
        assertTrue(result is LocationResult.Located)
        assertEquals("gps", (result as LocationResult.Located).value.locationSource)
        assertEquals(250, result.value.accuracyMeters)
    }

    @Test fun `denied permission leaves the app usable and manual fallback omits coordinates`() = runTest {
        val provider = FakeLocationProvider(LocationResult.PermissionDenied)
        assertEquals(LocationResult.PermissionDenied, provider.current())
        val manual = provider.manual("Poland", "Warsaw")
        assertEquals("manual", manual.locationSource)
        assertEquals("Warsaw", manual.city)
        assertNull(manual.latitude)
        assertNull(manual.longitude)
    }

    @Test fun `unavailable sensor also supports manual fallback`() = runTest {
        val provider = FakeLocationProvider(LocationResult.Unavailable)
        assertEquals(LocationResult.Unavailable, provider.current())
        assertEquals("Lisbon", provider.manual(null, " Lisbon ").city)
    }
}

private class FakeLocationProvider(private val result: LocationResult) : ForegroundLocationProvider {
    override suspend fun current(): LocationResult = result
}
