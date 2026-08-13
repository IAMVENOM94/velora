package app.velora.domain.usecase

import app.velora.domain.model.*
import app.velora.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UseCasesTest {
    @Test fun `login validates input before repository`()=runTest{
        val repository=FakeAuth();val useCase=LoginUseCase(repository)
        val invalid=useCase("not-an-email","short")
        assertTrue(invalid is DataResult.Failure);assertEquals(0,repository.loginCalls)
        assertTrue(useCase("person@example.com","x".repeat(129)) is DataResult.Failure);assertEquals(0,repository.loginCalls)
        assertTrue(useCase("person@example.com","password123") is DataResult.Success);assertEquals(1,repository.loginCalls)
    }

    @Test fun `onboarding enforces adulthood and three interests`()=runTest{
        val repository=FakeProfile();val useCase=CompleteOnboardingUseCase(repository)
        val underage=ProfileDraft("Nova",LocalDate.now().minusYears(17).toString(),"woman","Lisbon","Bio","dating")
        assertTrue(useCase(underage,listOf("Travel","Coffee","Design"),listOf("man")) is DataResult.Failure)
        val adult=underage.copy(birthDate=LocalDate.now().minusYears(25).toString())
        assertTrue(useCase(adult,listOf("Travel","Coffee"),listOf("man")) is DataResult.Failure)
        assertTrue(useCase(adult,listOf("Travel","Coffee","Design"),listOf("man")) is DataResult.Success)
        assertEquals(listOf("Travel","Coffee","Design"),repository.interests)
    }

    @Test fun `send message trims content and rejects blank`()=runTest{
        val repository=FakeChat();val useCase=SendMessageUseCase(repository)
        assertTrue(useCase("conversation","   ") is DataResult.Failure)
        assertTrue(useCase("conversation","  hello  ") is DataResult.Success)
        assertEquals("hello",repository.lastBody)
    }
}

private class FakeAuth:AuthRepository{
    var loginCalls=0;override val hasSession=flowOf(false)
    override suspend fun register(email:String,password:String)=DataResult.Success(Unit)
    override suspend fun login(email:String,password:String):DataResult<Unit>{loginCalls++;return DataResult.Success(Unit)}
    override suspend fun recover(email:String)=DataResult.Success(RecoveryRequest(true))
    override suspend fun resetPassword(token:String,password:String)=DataResult.Success(Unit)
    override suspend fun logout()=Unit
}

private class FakeProfile:ProfileRepository{
    var interests:List<String> = emptyList();private val me=MyProfile("id","person@example.com","Nova",true)
    override suspend fun me()=DataResult.Success(me)
    override suspend fun completeOnboarding(profile:ProfileDraft,interests:List<String>,showGenders:List<String>):DataResult<MyProfile>{this.interests=interests;return DataResult.Success(me)}
    override suspend fun saveProfile(profile:ProfileDraft)=DataResult.Success(me)
    override suspend fun saveInterests(interests:List<String>)=DataResult.Success(interests)
    override suspend fun setHidden(hidden:Boolean)=DataResult.Success(Unit)
    override suspend fun deleteAccount()=DataResult.Success(Unit)
}

private class FakeChat:ChatRepository{
    var lastBody:String?=null
    override suspend fun conversations()=DataResult.Success(emptyList<Conversation>())
    override suspend fun messages(conversationId:String)=DataResult.Success(emptyList<ChatMessage>())
    override suspend fun send(conversationId:String,body:String,replyToId:String?):DataResult<ChatMessage>{lastBody=body;return DataResult.Success(ChatMessage("id",conversationId,"me",body,null,replyToId,"now",null))}
    override suspend fun sendPhoto(conversationId:String,bytes:ByteArray,mimeType:String,replyToId:String?)=DataResult.Failure("unused")
    override suspend fun markRead(conversationId:String)=DataResult.Success(Unit)
    override fun sendTyping(conversationId:String,active:Boolean)=Unit
    override fun realtime(conversationId:String):Flow<ChatEvent> = emptyFlow()
}
