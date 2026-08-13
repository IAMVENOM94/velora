package app.velora.domain.usecase

import app.velora.domain.model.*
import app.velora.domain.repository.*
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

private val emailPattern=Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",RegexOption.IGNORE_CASE)
private fun credentialsError(email:String,password:String)=when{!emailPattern.matches(email.trim())->DataResult.Failure("invalid_email",false);password.length !in 8..128->DataResult.Failure("invalid_password_length",false);else->null}

class LoginUseCase @Inject constructor(private val repository:AuthRepository){suspend operator fun invoke(email:String,password:String)=credentialsError(email,password)?:repository.login(email.trim(),password)}
class RegisterUseCase @Inject constructor(private val repository:AuthRepository){suspend operator fun invoke(email:String,password:String)=credentialsError(email,password)?:repository.register(email.trim(),password)}
class CompleteOnboardingUseCase @Inject constructor(private val repository:ProfileRepository){
    suspend operator fun invoke(profile:ProfileDraft,interests:List<String>,showGenders:List<String>):DataResult<MyProfile>{
        if(profile.name.isBlank())return DataResult.Failure("name_required",false)
        if(profile.city.isBlank())return DataResult.Failure("city_required",false)
        val adult=try{LocalDate.parse(profile.birthDate)<=LocalDate.now().minusYears(18)}catch(_:DateTimeParseException){false}
        if(!adult)return DataResult.Failure("adult_required",false)
        if(interests.distinct().size<3)return DataResult.Failure("interests_required",false)
        if(showGenders.isEmpty())return DataResult.Failure("preferences_required",false)
        return repository.completeOnboarding(profile,interests.distinct(),showGenders.distinct())
    }
}
class LikeProfileUseCase @Inject constructor(private val repository:DatingRepository){suspend operator fun invoke(userId:String,superLike:Boolean=false)=repository.like(userId,superLike)}
class SendMessageUseCase @Inject constructor(private val repository:ChatRepository){suspend operator fun invoke(conversationId:String,body:String,replyToId:String?=null)=if(body.isBlank())DataResult.Failure("message_empty",false)else repository.send(conversationId,body.trim(),replyToId)}
