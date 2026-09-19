package app.velora.data.repository

import app.velora.data.local.ResponseCache
import app.velora.data.local.TokenStore
import app.velora.data.remote.*
import app.velora.domain.model.DataResult
import app.velora.domain.model.RecoveryRequest
import app.velora.domain.model.*
import app.velora.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class AuthRepositoryImpl @Inject constructor(private val api:VeloraApi,private val store:TokenStore,private val cache:ResponseCache):AuthRepository{
    override val hasSession:Flow<Boolean> = store.hasSession
    override suspend fun register(email:String,password:String)=authenticate{api.register(CredentialsDto(email,password))}
    override suspend fun login(email:String,password:String)=authenticate{api.login(CredentialsDto(email,password))}
    override suspend fun recover(email:String)=when(val r=remoteCall{api.recover(RecoverDto(email))}){is DataResult.Success->DataResult.Success(RecoveryRequest(r.value.ok,r.value.developmentToken));is DataResult.Failure->r}
    override suspend fun resetPassword(token:String,password:String)=when(val r=remoteCall{api.resetPassword(ResetPasswordDto(token,password))}){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->r}
    override suspend fun providers()=when(val r=remoteCall{api.providers()}){is DataResult.Success->DataResult.Success(r.value.toDomain());is DataResult.Failure->r}
    override suspend fun google(idToken:String)=authenticate{api.google(GoogleCredentialDto(idToken))}
    override suspend fun vk(accessToken:String)=authenticate{api.vk(VkCredentialDto(accessToken))}
    override suspend fun requestPhone(phone:String,deviceId:String)=when(val r=remoteCall{api.requestPhone(PhoneRequestDto(phone,deviceId))}){is DataResult.Success->DataResult.Success(PhoneOtpChallenge(phone,r.value.expiresInSeconds,r.value.resendAfterSeconds,r.value.developmentCode));is DataResult.Failure->r}
    override suspend fun verifyPhone(phone:String,code:String,link:Boolean)=when(val r=remoteCall{api.verifyPhone(PhoneVerifyDto(phone,code,link))}){is DataResult.Success->consumeUnified(r.value,link);is DataResult.Failure->r}
    override suspend fun oauthStart(provider:String,link:Boolean)=when(val r=remoteCall{api.oauthStart(provider,if(link)"link" else "login")}){is DataResult.Success->DataResult.Success(OAuthAuthorization(provider,r.value.authorizationUrl,r.value.state));is DataResult.Failure->r}
    override suspend fun oauthComplete(provider:String,code:String,state:String,deviceId:String?)=when(val r=remoteCall{api.oauthCallback(provider,OAuthCallbackDto(code,state,deviceId))}){is DataResult.Success->consumeUnified(r.value,false);is DataResult.Failure->r}
    private suspend fun authenticate(call:suspend()->TokenPairDto):DataResult<Unit> = when(val result=remoteCall{call()}){is DataResult.Success->{cache.clear();store.save(result.value.accessToken,result.value.refreshToken);DataResult.Success(Unit)};is DataResult.Failure->result}
    private suspend fun consumeUnified(value:UnifiedAuthResultDto,link:Boolean):DataResult<List<AuthIdentity>?>{if(value.accessToken!=null&&value.refreshToken!=null){cache.clear();store.save(value.accessToken,value.refreshToken);return DataResult.Success(null)};if(link||value.linked)return DataResult.Success(value.identities.map{it.toDomain()});return DataResult.Failure("invalid_auth_response",false)}
    private fun ProviderAvailabilityDto.toDomain()=AuthProviders(email,phone,google,x,vk,instagram)
    private fun AuthIdentityDto.toDomain()=AuthIdentity(id,provider,email,phone,emailVerified,phoneVerified,createdAt,lastUsedAt)
    override suspend fun logout(){val refresh=store.current()?.refresh;try{if(refresh!=null)api.logout(RefreshDto(refresh))}catch(_:Exception){};store.clear();cache.clear()}
}
