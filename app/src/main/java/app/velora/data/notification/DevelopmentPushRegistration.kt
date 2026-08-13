package app.velora.data.notification

import android.content.Context
import app.velora.BuildConfig
import app.velora.data.remote.DeviceDto
import app.velora.data.remote.VeloraApi
import app.velora.data.repository.remoteCall
import app.velora.domain.model.DataResult
import app.velora.domain.notification.PushRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevelopmentPushRegistration @Inject constructor(
    @ApplicationContext private val context:Context,
    private val api:VeloraApi,
):PushRegistration {
    private val token by lazy {
        val preferences=context.getSharedPreferences("push_installation",Context.MODE_PRIVATE)
        val installationId=preferences.getString("id",null)?:UUID.randomUUID().toString().also{preferences.edit().putString("id",it).apply()}
        val digest=MessageDigest.getInstance("SHA-256").digest(installationId.toByteArray()).joinToString(""){"%02x".format(it)}
        "velora-development-${digest.take(32)}"
    }

    override suspend fun register():DataResult<Unit> = if(!BuildConfig.DEBUG) DataResult.Success(Unit) else unit(remoteCall{api.registerDevice(DeviceDto(token))})
    override suspend fun unregister():DataResult<Unit> = if(!BuildConfig.DEBUG) DataResult.Success(Unit) else unit(remoteCall{api.removeDevice(DeviceDto(token))})
    private fun unit(value:DataResult<*>)=when(value){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->value}
}
