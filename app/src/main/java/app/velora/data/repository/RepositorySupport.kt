package app.velora.data.repository

import app.velora.domain.model.DataResult
import retrofit2.HttpException
import java.io.IOException

suspend fun <T> remoteCall(block:suspend()->T):DataResult<T> = try{
    DataResult.Success(block())
}catch(e:HttpException){
    val body=runCatching{e.response()?.errorBody()?.string()}.getOrNull().orEmpty()
    val backendCode=Regex("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.getOrNull(1)?.lowercase()
    val fallback=when(e.code()){400->"bad_data";401->"session_expired";402->"premium_required";404->"not_found";409->"conflict";413->"file_large";429->"rate_limit";else->"server_error"}
    DataResult.Failure(backendCode?:fallback,e.code()>=500||e.code()==429)
}catch(_:IOException){
    DataResult.Failure("offline")
}catch(e:Exception){
    DataResult.Failure("unexpected",false)
}
