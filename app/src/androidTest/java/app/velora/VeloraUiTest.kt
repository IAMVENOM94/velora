package app.velora

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.velora.domain.model.*
import app.velora.ui.theme.VeloraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class VeloraUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun emptyDiscover_exposesRecoveryActions(){
        compose.setContent{VeloraTheme{DiscoverEmpty(loaded=true,premium=true,onRefresh={},onSettings={},onRewind={})}}
        compose.onNodeWithText("You’re all caught up").assertExists()
        compose.onNodeWithText("Adjust discovery filters").assertExists()
        compose.onNodeWithText("Rewind last pass").assertExists()
    }

    @Test fun premiumScreen_showsUsageAndActivatesDevelopmentPlan(){
        var activated=false
        compose.setContent{VeloraTheme{PremiumConnected(Subscription(actionLimit=20,actionsUsed=6,superLikeLimit=1,superLikesUsed=1),ReceivedLikes(count=3),onBack={},onActivate={activated=true},onBoost={})}}
        compose.onNodeWithText("6 of 20 discovery actions").assertExists()
        compose.onNodeWithText("3 waiting · Premium unlocks the list").assertExists()
        compose.onNodeWithText("Activate Premium").performClick()
        assertTrue(activated)
    }

    @Test fun matchScreen_usesRealProfileName(){
        compose.setContent{VeloraTheme{MatchConnected(MyProfile("me","me@example.com","Nova",true),DiscoverProfile("other","River",31,"Lisbon","Bio","relationship",5,null,true),onChat={},onContinue={})}}
        compose.onNodeWithText("You and River felt the same spark.").assertExists()
    }

    @Test fun registrationOnboardingDiscoverMatchAndChat_areWiredEndToEnd(){
        var stage by mutableStateOf("register")
        var registeredEmail=""
        var completedProfile:ProfileDraft?=null
        var sentMessage=""
        val person=DiscoverProfile("other","River",31,"Lisbon","A thoughtful profile","relationship",5,null,true,listOf("Coffee","Design","Travel"))
        compose.setContent{VeloraTheme{when(stage){
            "register"->Register(false,onBack={},onContinue={email,_->registeredEmail=email;stage="onboarding"})
            "onboarding"->OnboardingConnected(onDone={profile,_,_->completedProfile=profile;stage="discover"},onPhoto={_,_,_->},initialUploadedPositions=setOf(0))
            "discover"->DiscoverConnected(person.toPerson(),onDetail={},onSkip={},onLike={stage="match"},onSuper={})
            "match"->MatchConnected(MyProfile("me","me@example.com","Nova",true),person,onChat={stage="chat"},onContinue={})
            else->ChatConnected(emptyList(),"me",Conversation("conversation","River",null,null,null,0,"match","other"),false,true,onBack={},onSend={body,_->sentMessage=body},onPhoto={_,_,_->},onTyping={},onUnmatch={},onBlock={},onReport={})
        }}}

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("flow@test.velora")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("SecurePass123!")
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("SecurePass123!")
        compose.onNodeWithText("Continue").performClick()

        compose.onNode(hasSetTextAction()).performTextInput("Nova")
        compose.onNodeWithText("Continue").performClick() // birth date
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Woman").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Men").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNode(hasSetTextAction() and hasText("City")).performTextInput("Lisbon")
        compose.onNodeWithText("Continue").performClick() // location
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick() // pre-supplied photo
        compose.onNodeWithText("Continue").performClick() // optional bio
        compose.onNodeWithText("Travel").performClick()
        compose.onNodeWithText("Coffee").performClick()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Enter Velora").performClick()

        compose.onNodeWithContentDescription("River profile photo").performTouchInput{swipeRight()}
        compose.onNodeWithText("You and River felt the same spark.").assertExists()
        compose.onNodeWithText("Send a message").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Hello River")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle{assertEquals("flow@test.velora",registeredEmail);assertEquals("Nova",completedProfile?.name);assertEquals("Hello River",sentMessage)}
    }

    @Test fun authProviderButtons_reflectConfigurationState(){
        compose.setContent{VeloraTheme{Login(false,AuthProviders(phone=true,google=false,x=false,vk=false),false,false,{},{},{_,_->},{},{},{},{})}}
        compose.onNodeWithText("Continue with phone").assertIsEnabled()
        compose.onNodeWithText("Continue with Google").assertIsNotEnabled()
        compose.onAllNodesWithText("Unavailable in this build").assertCountEquals(3)
    }

    @Test fun connectedAccounts_andProviderActions_areVisible(){
        var unlinked=""
        compose.setContent{VeloraTheme{SettingsConnected(SearchPreferences(),false,emptyList(),{},{},{},{},{},{},providers=AuthProviders(phone=true),identities=listOf(AuthIdentity("email-id","email","p***@example.com"),AuthIdentity("google-id","google","g***@example.com")),onUnlink={unlinked=it})}}
        compose.onNodeWithText("CONNECTED ACCOUNTS").assertExists()
        compose.onNodeWithText("g***@example.com").assertExists()
        compose.onNodeWithText("Phone").assertExists()
        compose.onAllNodesWithText("Unlink")[1].performClick()
        compose.onNodeWithText("Unlink Google?").assertExists()
        compose.onAllNodesWithText("Unlink")[2].performClick()
        compose.runOnIdle{assertEquals("google",unlinked)}
    }

    @Test fun settingsLanguageChoice_isWired(){
        var selected:app.velora.localization.AppLanguage?=null
        compose.setContent{VeloraTheme{SettingsConnected(SearchPreferences(),false,emptyList(),{},{},{},{},{},{},onLanguage={selected=it})}}
        compose.onNodeWithText("Русский").performClick()
        compose.runOnIdle{assertEquals(app.velora.localization.AppLanguage.Russian,selected)}
    }
}
