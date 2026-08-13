package app.velora.domain.notification

import app.velora.domain.model.DataResult

/** Boundary for FCM/APNs registration. The development implementation uses a non-secret device token. */
interface PushRegistration {
    suspend fun register():DataResult<Unit>
    suspend fun unregister():DataResult<Unit>
}
