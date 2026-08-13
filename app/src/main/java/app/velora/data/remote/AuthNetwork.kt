package app.velora.data.remote

import app.velora.BuildConfig
import app.velora.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class AccessTokenInterceptor @Inject constructor(private val store:TokenStore):Interceptor{
    override fun intercept(chain:Interceptor.Chain):Response{val request=chain.request();if(request.url.encodedPath.startsWith("/auth/"))return chain.proceed(request);val token=runBlocking{store.current()?.access};return chain.proceed(if(token==null)request else request.newBuilder().header("Authorization","Bearer $token").build())}
}

@Singleton class TokenAuthenticator @Inject constructor(private val store:TokenStore,private val json:Json):Authenticator{
    @Synchronized override fun authenticate(route:Route?,response:Response):Request?{if(responseCount(response)>=2)return null;val current=runBlocking{store.current()}?:return null;val failedToken=response.request.header("Authorization")?.removePrefix("Bearer ");if(failedToken!=null&&failedToken!=current.access)return response.request.newBuilder().header("Authorization","Bearer ${current.access}").build();val body=json.encodeToString(RefreshDto.serializer(),RefreshDto(current.refresh)).toRequestBody("application/json".toMediaType());val refreshRequest=Request.Builder().url(BuildConfig.API_BASE_URL+"auth/refresh").post(body).build();return try{OkHttpClient().newCall(refreshRequest).execute().use{r->if(!r.isSuccessful){if(r.code==400||r.code==401)runBlocking{store.clear()};null}else{val tokens=json.decodeFromString(TokenPairDto.serializer(),r.body?.string().orEmpty());runBlocking{store.save(tokens.accessToken,tokens.refreshToken)};response.request.newBuilder().header("Authorization","Bearer ${tokens.accessToken}").build()}}}catch(_:Exception){null}}
    private fun responseCount(response:Response):Int{var count=1;var prior=response.priorResponse;while(prior!=null){count++;prior=prior.priorResponse};return count}
}
