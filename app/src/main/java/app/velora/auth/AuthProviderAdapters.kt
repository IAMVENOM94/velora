package app.velora.auth

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import app.velora.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.vk.id.AccessToken
import com.vk.id.VKID
import com.vk.id.VKIDAuthFail
import com.vk.id.auth.VKIDAuthCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface ProviderCredentialResult {
    data class Success(val credential: String) : ProviderCredentialResult
    data object Cancelled : ProviderCredentialResult
    data object Unavailable : ProviderCredentialResult
    data class Failure(val cause: Throwable? = null) : ProviderCredentialResult
}

class GoogleCredentialManagerAdapter {
    val configured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun acquire(activity: Activity): ProviderCredentialResult {
        if (!configured) return ProviderCredentialResult.Unavailable
        return try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                ProviderCredentialResult.Failure()
            } else {
                ProviderCredentialResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            }
        } catch (error: androidx.credentials.exceptions.GetCredentialCancellationException) {
            ProviderCredentialResult.Cancelled
        } catch (error: NoCredentialException) {
            ProviderCredentialResult.Unavailable
        } catch (error: Throwable) {
            ProviderCredentialResult.Failure(error)
        }
    }
}

/** All official VK ID SDK calls stay behind this adapter. */
class VkIdAuthProviderAdapter {
    val configured: Boolean get() = BuildConfig.VK_CONFIGURED

    suspend fun acquire(activity: androidx.lifecycle.LifecycleOwner): ProviderCredentialResult {
        if (!configured) return ProviderCredentialResult.Unavailable
        return suspendCancellableCoroutine { continuation ->
            VKID.instance.authorize(activity, object : VKIDAuthCallback {
                override fun onAuth(accessToken: AccessToken) {
                    if (continuation.isActive) continuation.resume(ProviderCredentialResult.Success(accessToken.token))
                }

                override fun onFail(fail: VKIDAuthFail) {
                    if (continuation.isActive) continuation.resume(ProviderCredentialResult.Failure())
                }
            })
        }
    }
}

class BrowserOAuthAdapter {
    fun launch(activity: Activity, authorizationUrl: String) {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, Uri.parse(authorizationUrl))
    }

    fun callback(uri: Uri?): OAuthCallback? {
        if (uri?.scheme != "velora" || uri.host != "auth") return null
        val provider = uri.pathSegments.firstOrNull()?.lowercase() ?: return null
        if (provider !in setOf("x", "vk")) return null
        val code = uri.getQueryParameter("code") ?: return null
        val state = uri.getQueryParameter("state") ?: return null
        return OAuthCallback(provider, code, state, uri.getQueryParameter("device_id"))
    }
}

data class OAuthCallback(val provider: String, val code: String, val state: String, val deviceId: String?)
