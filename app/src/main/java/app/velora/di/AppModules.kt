package app.velora.di

import app.velora.BuildConfig
import app.velora.data.remote.*
import app.velora.data.repository.*
import app.velora.domain.repository.*
import app.velora.data.notification.DevelopmentPushRegistration
import app.velora.domain.notification.PushRegistration
import android.content.Context
import coil.ImageLoader
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun json()=Json{ignoreUnknownKeys=true;isLenient=true;explicitNulls=false}
    @Provides @Singleton fun http(interceptor:AccessTokenInterceptor,authenticator:TokenAuthenticator)=OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor(HttpLoggingInterceptor().apply{level=if(BuildConfig.DEBUG)HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE}).authenticator(authenticator).connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()
    @Provides @Singleton fun retrofit(client:OkHttpClient,json:Json)=Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    @Provides @Singleton fun api(retrofit:Retrofit)=retrofit.create(VeloraApi::class.java)
    @Provides @Singleton fun imageLoader(@ApplicationContext context:Context,client:OkHttpClient)=ImageLoader.Builder(context).okHttpClient(client).crossfade(true).build()
}

@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun auth(value:AuthRepositoryImpl):AuthRepository
    @Binds @Singleton abstract fun profile(value:ProfileRepositoryImpl):ProfileRepository
    @Binds @Singleton abstract fun dating(value:DatingRepositoryImpl):DatingRepository
    @Binds @Singleton abstract fun chat(value:ChatRepositoryImpl):ChatRepository
    @Binds @Singleton abstract fun photos(value:PhotoRepositoryImpl):PhotoRepository
    @Binds @Singleton abstract fun push(value:DevelopmentPushRegistration):PushRegistration
}
