package app.velora.domain.usecase

import app.velora.domain.model.*
import app.velora.domain.repository.*
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

private val emailPattern=Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",RegexOption.IGNORE_CASE)
private fun credentialsError(email:String,password:String)=when{!emailPattern.matches(email.trim())->DataResult.Failure("Enter a valid email",false);password.length !in 8..128->DataResult.Failure("Password must contain 8 to 128 characters",false);else->null}

class LoginUseCase @Inject constructor(private val repository:AuthRepository){suspend operator fun invoke(email:String,password:String)=credentialsError(email,password)?:repository.login(email.trim(),password)}
class RegisterUseCase @Inject constructor(private val repository:AuthRepository){suspend operator fun invoke(email:String,password:String)=credentialsError(email,password)?:repository.register(email.trim(),password)}
class CompleteOnboardingUseCase @Inject constructor(private val repository:ProfileRepository){
    suspend operator fun invoke(profile:ProfileDraft,interests:List<String>,showGenders:List<String>):DataResult<MyProfile>{
        if(profile.name.isBlank())return DataResult.Failure("Name is required",false)
        if(profile.city.isBlank())return DataResult.Failure("City is required",false)
        val adult=try{LocalDate.parse(profile.birthDate)<=LocalDate.now().minusYears(18)}catch(_:DateTimeParseException){false}
        if(!adult)return DataResult.Failure("You must be 18 or older",false)
        if(interests.distinct().size<3)return DataResult.Failure("Choose at least three interests",false)
        if(showGenders.isEmpty())return DataResult.Failure("Choose who you would like to meet",false)
        return repository.completeOnboarding(profile,interests.distinct(),showGenders.distinct())
    }
}
class LikeProfileUseCase @Inject constructor(private val repository:DatingRepository){suspend operator fun invoke(userId:String,superLike:Boolean=false)=repository.like(userId,superLike)}
class SendMessageUseCase @Inject constructor(private val repository:ChatRepository){suspend operator fun invoke(conversationId:String,body:String,replyToId:String?=null)=if(body.isBlank())DataResult.Failure("Message is empty",false)else repository.send(conversationId,body.trim(),replyToId)}
