package app.velora.data.repository

import app.velora.data.local.ResponseCache
import app.velora.data.local.TokenStore
import app.velora.data.remote.*
import app.velora.domain.model.DataResult
import app.velora.domain.model.RecoveryRequest
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
    private suspend fun authenticate(call:suspend()->TokenPairDto):DataResult<Unit> = when(val result=remoteCall{call()}){is DataResult.Success->{store.save(result.value.accessToken,result.value.refreshToken);DataResult.Success(Unit)};is DataResult.Failure->result}
    override suspend fun logout(){val refresh=store.current()?.refresh;try{if(refresh!=null)api.logout(RefreshDto(refresh))}catch(_:Exception){};store.clear();cache.clear()}
}
