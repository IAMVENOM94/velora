package app.velora.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.velora.data.local.ResponseCache
import app.velora.data.remote.VeloraApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit

@RunWith(RobolectricTestRunner::class)
class DatingRepositoryImplTest {
    @Test fun `discover maps API response and falls back to cache`()=runTest{
        val server=MockWebServer().apply{start()};val json=Json{ignoreUnknownKeys=true;explicitNulls=false};val context=ApplicationProvider.getApplicationContext<Context>();val cache=ResponseCache(context).apply{clear()}
        val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(VeloraApi::class.java)
        val repository=DatingRepositoryImpl(api,cache,json)
        server.enqueue(MockResponse().setHeader("content-type","application/json").setBody("""{"items":[{"user_id":"person-1","name":"Maya","age":28,"city":"Lisbon","bio":"Slow mornings","dating_goal":"relationship","distance_km":5,"onlineStatus":"ONLINE","interests":["Coffee","Travel"]}],"nextCursor":null}"""))
        val live=repository.discover()
        assertEquals("Maya",(live as app.velora.domain.model.DataResult.Success).value.single().name);assertFalse(live.fromCache);assertEquals(listOf("Coffee","Travel"),live.value.single().interests)
        server.shutdown()
        val cached=repository.discover()
        assertTrue(cached is app.velora.domain.model.DataResult.Success);assertTrue((cached as app.velora.domain.model.DataResult.Success).fromCache);assertEquals("person-1",cached.value.single().id)
    }

    @Test fun `public profile cache is offline-only and purged after authoritative denial`()=runTest{
        val server=MockWebServer().apply{start()};val json=Json{ignoreUnknownKeys=true;explicitNulls=false};val context=ApplicationProvider.getApplicationContext<Context>();val cache=ResponseCache(context).apply{clear()}
        val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(VeloraApi::class.java)
        val repository=DatingRepositoryImpl(api,cache,json);val userId="00000000-0000-4000-8000-000000000010"
        server.enqueue(MockResponse().setHeader("content-type","application/json").setBody("""{"userId":"$userId","displayName":"Bianca","age":29,"gender":"woman","bio":"Visible bio","relationshipGoal":"relationship","photos":[],"interests":["Coffee"]}"""))
        val live=repository.publicProfile(userId) as app.velora.domain.model.DataResult.Success
        assertEquals("Bianca",live.value.displayName);assertFalse(live.fromCache)
        server.enqueue(MockResponse().setResponseCode(503).setHeader("content-type","application/json").setBody("""{"code":"SERVICE_UNAVAILABLE","message":"Temporary"}"""))
        val offlineFallback=repository.publicProfile(userId) as app.velora.domain.model.DataResult.Success
        assertTrue(offlineFallback.fromCache)
        server.enqueue(MockResponse().setResponseCode(404).setHeader("content-type","application/json").setBody("""{"code":"PROFILE_UNAVAILABLE","message":"Profile unavailable"}"""))
        val denied=repository.publicProfile(userId)
        assertTrue(denied is app.velora.domain.model.DataResult.Failure);assertNull(cache.get("public_profile_$userId"))
        server.shutdown()
        assertTrue(repository.publicProfile(userId) is app.velora.domain.model.DataResult.Failure)
    }
}
