@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.velora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.velora.domain.model.PublicProfile
import app.velora.domain.model.PublicProfilePhoto
import app.velora.ui.theme.*
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlin.math.abs

@Composable
fun UserProfileScreen(
    profile: PublicProfile?,
    loading: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onSuperLike: () -> Unit,
    onMessage: () -> Unit,
    onUnmatch: () -> Unit,
    onBlock: () -> Unit,
    onReport: (String, String) -> Unit,
) {
    if (loading && profile == null) {
        AuroraBackground { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Pink); Spacer(Modifier.height(14.dp)); Text(stringResource(R.string.profile_loading), color = Muted) } } }
        return
    }
    if (profile == null) {
        AuroraBackground { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Outlined.Person, null, tint = Muted, modifier = Modifier.size(62.dp)); Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.profile_unavailable), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center); Spacer(Modifier.height(20.dp)); GlowButton(stringResource(R.string.try_again), onRetry, Modifier.fillMaxWidth()); TextButton(onBack) { Text(stringResource(R.string.back)) } } }
        return
    }

    var fullscreenPage by remember(profile.userId) { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmUnmatch by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    val pager = rememberPagerState(pageCount = { profile.photos.size.coerceAtLeast(1) })

    AuroraBackground {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 116.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(560.dp)) {
                        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                            val photo = profile.photos.getOrNull(page)
                            ProfileImage(photo?.mediumUrl, profile.displayName, Modifier.fillMaxSize().clickable(enabled = photo != null) { fullscreenPage = page })
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000),Color.Transparent,Ink))))
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onBack, Modifier.background(Color(0xAA17121E),CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.back)) }
                            Spacer(Modifier.weight(1f))
                            Box {
                                IconButton({ menu=true },Modifier.background(Color(0xAA17121E),CircleShape)) { Icon(Icons.Default.MoreVert,stringResource(R.string.safety_actions)) }
                                DropdownMenu(menu,{menu=false}) {
                                    DropdownMenuItem({Text(stringResource(R.string.report_profile))},{menu=false;reportOpen=true},leadingIcon={Icon(Icons.Outlined.Flag,null)})
                                    DropdownMenuItem({Text(stringResource(R.string.block_profile))},{menu=false;confirmBlock=true},leadingIcon={Icon(Icons.Outlined.Block,null)})
                                }
                            }
                        }
                        if(profile.photos.size>1) Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=72.dp).background(Color(0x88100C15),CircleShape).padding(horizontal=10.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            profile.photos.indices.forEach { index -> Box(Modifier.size(if(index==pager.currentPage)18.dp else 6.dp,6.dp).background(if(index==pager.currentPage)White else White.copy(.42f),CircleShape)) }
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) { Text("${profile.displayName}, ${profile.age}",style=MaterialTheme.typography.headlineLarge); if(profile.verified){Spacer(Modifier.width(8.dp));Icon(Icons.Default.Verified,stringResource(R.string.verified_profile),tint=Violet,modifier=Modifier.size(23.dp))} }
                            Spacer(Modifier.height(5.dp))
                            Row(verticalAlignment=Alignment.CenterVertically) { val online=profile.onlineStatus=="ONLINE";Box(Modifier.size(8.dp).background(if(online)Color(0xFF55E6A5)else Muted,CircleShape));Spacer(Modifier.width(7.dp));Text(presenceText(profile),color=White.copy(.78f),style=MaterialTheme.typography.labelLarge) }
                            val place=listOfNotNull(profile.city?.takeIf(String::isNotBlank),profile.distanceKm?.let{distanceText(it)}).joinToString("  •  ")
                            if(place.isNotBlank()){Spacer(Modifier.height(6.dp));Text(place,color=Muted)}
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal=22.dp)) {
                        if(profile.relationshipState=="MATCHED") RelationshipPill(Icons.Default.Favorite,stringResource(R.string.matched_label),Pink)
                        else if(profile.relationshipState=="LIKED") RelationshipPill(Icons.Default.Check,stringResource(R.string.liked_label),Violet)
                        else if(profile.relationshipState=="LIKED_ME") RelationshipPill(Icons.Default.FavoriteBorder,stringResource(R.string.likes_you_label),Pink)
                        Spacer(Modifier.height(22.dp));Text(stringResource(R.string.about),style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(10.dp));Text(profile.bio.ifBlank{stringResource(R.string.no_bio)},color=White.copy(.86f),style=MaterialTheme.typography.bodyLarge)
                        if(profile.interests.isNotEmpty()){Spacer(Modifier.height(26.dp));Text(stringResource(R.string.interests),style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(11.dp));FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){profile.interests.forEach{interest->SurfaceCard(padding=10.dp){Text(localizedInterest(interest),style=MaterialTheme.typography.labelLarge)}}}}
                        Spacer(Modifier.height(26.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){ProfileFact(stringResource(R.string.looking_for),localizedGoal(profile.relationshipGoal),Modifier.weight(1f));ProfileFact(stringResource(R.string.gender),localizedGender(profile.gender),Modifier.weight(1f))}
                        profile.socialProfiles.forEach { social -> Spacer(Modifier.height(10.dp));SurfaceCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AlternateEmail,null,tint=Pink);Spacer(Modifier.width(10.dp));Text("@${social.username}",fontWeight=FontWeight.SemiBold)}} }
                    }
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),color=Color(0xF2110D16),shadowElevation=20.dp) {
                if(profile.relationshipState=="MATCHED") Row(Modifier.navigationBarsPadding().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) { OutlinedButton({confirmUnmatch=true},Modifier.weight(1f).height(56.dp),shape=RoundedCornerShape(18.dp)){Icon(Icons.Default.HeartBroken,null);Spacer(Modifier.width(7.dp));Text(stringResource(R.string.unmatch))};GlowButton(stringResource(R.string.send_message),onMessage,Modifier.weight(1.35f),Icons.AutoMirrored.Filled.Chat) }
                else Row(Modifier.navigationBarsPadding().padding(horizontal=20.dp,vertical=13.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) { Action(Icons.Default.Close,Color(0xFF9B93A5),54.dp,stringResource(R.string.pass),onPass);Action(Icons.Default.Star,Violet,62.dp,stringResource(R.string.super_like),onSuperLike);if(profile.relationshipState=="LIKED")Action(Icons.Default.Check,Muted,70.dp,stringResource(R.string.liked_label),{})else Action(Icons.Default.Favorite,Pink,70.dp,stringResource(R.string.like),onLike) }
            }
        }
    }
    fullscreenPage?.let { FullscreenPhotoViewer(profile.photos,it,profile.displayName){fullscreenPage=null} }
    if(confirmBlock) AlertDialog({confirmBlock=false},title={Text(stringResource(R.string.block_person_title,profile.displayName))},text={Text(stringResource(R.string.block_person_body,profile.displayName))},confirmButton={TextButton({confirmBlock=false;onBlock()}){Text(stringResource(R.string.block),color=Pink)}},dismissButton={TextButton({confirmBlock=false}){Text(stringResource(R.string.cancel))}})
    if(confirmUnmatch) AlertDialog({confirmUnmatch=false},title={Text(stringResource(R.string.unmatch_title))},text={Text(stringResource(R.string.unmatch_body))},confirmButton={TextButton({confirmUnmatch=false;onUnmatch()}){Text(stringResource(R.string.unmatch),color=Pink)}},dismissButton={TextButton({confirmUnmatch=false}){Text(stringResource(R.string.cancel))}})
    if(reportOpen) ReportProfileDialog(profile.displayName,{reportOpen=false}){reason,details->reportOpen=false;onReport(reason,details)}
}

@Composable private fun ProfileImage(url:String?,name:String,modifier:Modifier){if(url==null)Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF392743),Color(0xFF17131E)))),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Person,stringResource(R.string.person_profile_photo,name),tint=Muted,modifier=Modifier.size(86.dp))}else SubcomposeAsyncImage(url,stringResource(R.string.person_profile_photo,name),modifier,contentScale=ContentScale.Crop,loading={Box(Modifier.fillMaxSize().background(Color(0xFF17121E)),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Pink,strokeWidth=2.dp)}},error={Box(Modifier.fillMaxSize().background(Color(0xFF17121E)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.BrokenImage,null,tint=Muted);Text(stringResource(R.string.photo_load_failed),color=Muted)}}},success={SubcomposeAsyncImageContent()})}

@Composable private fun RelationshipPill(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String,color:Color){Row(Modifier.background(color.copy(.14f),CircleShape).border(1.dp,color.copy(.35f),CircleShape).padding(horizontal=13.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=color,modifier=Modifier.size(17.dp));Spacer(Modifier.width(7.dp));Text(text,color=color,fontWeight=FontWeight.SemiBold)}}
@Composable private fun ProfileFact(label:String,value:String,modifier:Modifier){SurfaceCard(modifier){Text(label,color=Pink,style=MaterialTheme.typography.labelMedium);Spacer(Modifier.height(7.dp));Text(value,fontWeight=FontWeight.SemiBold)}}
@Composable private fun localizedGender(value:String)=stringResource(when(value){"woman"->R.string.gender_woman;"man"->R.string.gender_man;"non_binary"->R.string.gender_non_binary;else->R.string.gender_self_describe})
@Composable private fun presenceText(profile:PublicProfile)=when(profile.onlineStatus){"ONLINE"->stringResource(R.string.online);"RECENTLY_ONLINE"->stringResource(R.string.recently_active);"LAST_SEEN"->stringResource(R.string.last_seen);"HIDDEN"->stringResource(R.string.status_hidden);else->stringResource(R.string.offline_status)}

@Composable private fun FullscreenPhotoViewer(photos:List<PublicProfilePhoto>,initialPage:Int,name:String,onDismiss:()->Unit){
    val pager=rememberPagerState(initialPage=initialPage,pageCount={photos.size});var verticalOffset by remember{mutableFloatStateOf(0f)}
    val dismissThreshold=with(LocalDensity.current){96.dp.toPx()}
    Dialog(onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(onDismiss,dismissThreshold){
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                var totalX=0f;var totalY=0f;var verticalDrag=false
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    if(event.changes.size==1){
                        val change=event.changes.first();val delta=change.position-change.previousPosition
                        totalX+=delta.x;totalY+=delta.y
                        if(!verticalDrag&&totalY>viewConfiguration.touchSlop&&abs(totalY)>abs(totalX)*1.2f)verticalDrag=true
                        if(verticalDrag)verticalOffset=totalY.coerceAtLeast(0f)
                    }else if(event.changes.size>1){verticalDrag=false;verticalOffset=0f}
                }while(event.changes.any{it.pressed})
                if(verticalDrag&&verticalOffset>dismissThreshold)onDismiss()else verticalOffset=0f
            }
        }.graphicsLayer{translationY=verticalOffset;alpha=1f-(verticalOffset/900f).coerceIn(0f,.35f)}){
            HorizontalPager(pager,Modifier.fillMaxSize()){page->var scale by remember(page){mutableFloatStateOf(1f)};var offsetX by remember(page){mutableFloatStateOf(0f)};var offsetY by remember(page){mutableFloatStateOf(0f)};val transform=rememberTransformableState{zoom,pan,_->scale=(scale*zoom).coerceIn(1f,5f);if(scale>1f){offsetX+=pan.x;offsetY+=pan.y}else{offsetX=0f;offsetY=0f}};SubcomposeAsyncImage(photos[page].fullUrl,stringResource(R.string.profile_photo_number,page+1),Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=offsetX;translationY=offsetY}.transformable(transform),contentScale=ContentScale.Fit,loading={Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Pink)}},error={Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(stringResource(R.string.photo_load_failed),color=Muted)}},success={SubcomposeAsyncImageContent()})}
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(14.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onDismiss,Modifier.background(Color(0xAA211B2B),CircleShape)){Icon(Icons.Default.Close,stringResource(R.string.close_photo))};Spacer(Modifier.weight(1f));Text(stringResource(R.string.photo_counter,pager.currentPage+1,photos.size),modifier=Modifier.background(Color(0xAA211B2B),CircleShape).padding(horizontal=12.dp,vertical=7.dp));Spacer(Modifier.weight(1f));Spacer(Modifier.size(48.dp))}
            if(abs(verticalOffset)<1f)Text(stringResource(R.string.swipe_down_close),color=White.copy(.58f),style=MaterialTheme.typography.labelMedium,modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp))
        }
    }
}

@Composable private fun ReportProfileDialog(name:String,onDismiss:()->Unit,onSubmit:(String,String)->Unit){val reasons=listOf("spam" to R.string.report_spam,"fake" to R.string.report_fake,"harassment" to R.string.report_harassment,"inappropriate" to R.string.report_inappropriate,"underage" to R.string.report_underage,"other" to R.string.report_other);var selected by remember{mutableStateOf("spam")};var details by remember{mutableStateOf("")};AlertDialog(onDismiss,title={Text(stringResource(R.string.report_person,name))},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())){reasons.forEach{(value,label)->Row(Modifier.fillMaxWidth().clickable{selected=value}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected==value,{selected=value});Text(stringResource(label))}};Spacer(Modifier.height(8.dp));OutlinedTextField(details,{details=it.take(1000)},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.report_comment))},minLines=3)}},confirmButton={TextButton({onSubmit(selected,details.trim())}){Text(stringResource(R.string.submit_report),color=Pink)}},dismissButton={TextButton(onDismiss){Text(stringResource(R.string.cancel))}})}
