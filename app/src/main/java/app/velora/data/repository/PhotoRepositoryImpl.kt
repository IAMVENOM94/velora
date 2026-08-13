package app.velora.data.repository

import app.velora.data.remote.VeloraApi
import app.velora.BuildConfig
import app.velora.domain.model.*
import app.velora.domain.repository.PhotoRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepositoryImpl @Inject constructor(private val api:VeloraApi):PhotoRepository {
    override suspend fun list()=when(val r=remoteCall{api.photos()}){is DataResult.Success->DataResult.Success(r.value.map{it.domain()});is DataResult.Failure->r}
    override suspend fun upload(position:Int,bytes:ByteArray,mimeType:String):DataResult<ProfilePhoto>{
        if(position !in 0..8)return DataResult.Failure("Invalid photo position",false)
        if(bytes.size>10*1024*1024)return DataResult.Failure("Photo must be under 10 MB",false)
        val safeMime=if(mimeType in listOf("image/jpeg","image/png","image/webp"))mimeType else "image/jpeg"
        val extension=when(safeMime){"image/png"->"png";"image/webp"->"webp";else->"jpg"}
        val body=bytes.toRequestBody(safeMime.toMediaType())
        return when(val r=remoteCall{api.uploadPhoto(position,MultipartBody.Part.createFormData("file","profile.$extension",body))}){is DataResult.Success->DataResult.Success(r.value.domain());is DataResult.Failure->r}
    }
    override suspend fun delete(id:String)=when(val r=remoteCall{api.deletePhoto(id)}){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->r}
    private fun app.velora.data.remote.PhotoResultDto.domain()=ProfilePhoto(id,position,if(url.startsWith("http"))url else BuildConfig.API_BASE_URL.removeSuffix("/")+url)
}
