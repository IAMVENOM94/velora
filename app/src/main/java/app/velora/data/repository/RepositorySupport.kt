package app.velora.data.repository

import app.velora.domain.model.DataResult
import retrofit2.HttpException
import java.io.IOException

suspend fun <T> remoteCall(block:suspend()->T):DataResult<T> = try{
    DataResult.Success(block())
}catch(e:HttpException){
    val body=runCatching{e.response()?.errorBody()?.string()}.getOrNull().orEmpty()
    val backendMessage=Regex("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.getOrNull(1)
    val fallback=when(e.code()){400->"Check the entered data";401->"Your session expired";402->"This feature requires Premium";404->"The requested item no longer exists";409->"The requested change conflicts with current data";413->"The selected file is too large";429->"Daily limit reached";else->"Server error (${e.code()})"}
    DataResult.Failure(backendMessage?:fallback,e.code()>=500||e.code()==429)
}catch(_:IOException){
    DataResult.Failure("No connection. Check your network and retry.")
}catch(e:Exception){
    DataResult.Failure(e.message?:"Unexpected error",false)
}
