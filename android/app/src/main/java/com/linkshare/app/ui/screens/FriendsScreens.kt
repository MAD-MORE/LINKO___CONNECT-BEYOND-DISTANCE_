package com.linkshare.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.network.FriendSearchResult
import com.linkshare.app.network.LinkoFriendsApi
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(api: LinkoFriendsApi, onFindFriends:()->Unit, onFriendTap:()->Unit) {
    var friends by remember { mutableStateOf<List<FriendSearchResult>>(emptyList()) }
    LaunchedEffect(Unit) { runCatching { friends = api.friends().optJSONArray("friends")?.let { array -> buildList { for (i in 0 until array.length()) { val o = array.optJSONObject(i) ?: continue; add(FriendSearchResult(o.optString("user_id"), o.optString("linko_id"), o.optString("display_name"), null, null, false)) } } } ?: emptyList() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Friends",color=TextPrimary,fontSize=22.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(4.dp)); Text("Your LINKO connection network",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono); Spacer(Modifier.height(16.dp))
        if (friends.isEmpty()) LinkoCard { Text("NO FRIENDS YET",color=TextMuted,fontSize=11.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Find a real LINKO user by their LINKO ID or name.",color=TextSub,fontSize=12.sp,fontFamily=JetBrainsMono) }
        else friends.forEach { friend -> LinkoCard { Column(Modifier.fillMaxWidth().clickable { onFriendTap() }) { Text(friend.displayName,color=TextPrimary,fontSize=15.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold); Text(friend.linkoId,color=Blue,fontSize=11.sp,fontFamily=JetBrainsMono); Text("FRIEND",color=Green,fontSize=10.sp,fontFamily=JetBrainsMono) } } ; Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.weight(1f)); PrimaryButton("+ FIND FRIENDS",onFindFriends); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FindFriendsScreen(api: LinkoFriendsApi, onSelect:(FriendSearchResult)->Unit) {
    var query by remember { mutableStateOf("") }; var results by remember { mutableStateOf<List<FriendSearchResult>>(emptyList()) }; var searching by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Find Friends",color=TextPrimary,fontSize=22.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Search real LINKO users by ID or name",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono); Spacer(Modifier.height(20.dp))
        LinkoInput("SEARCH",query,{query=it},"LNK-XXXXXXXX","Enter a LINKO ID or name")
        Spacer(Modifier.height(14.dp)); if (message != null) Text(message!!,color=Red,fontSize=11.sp,fontFamily=JetBrainsMono)
        results.forEach { result -> LinkoCard { Column(Modifier.fillMaxWidth().clickable { onSelect(result) }) { Text(result.displayName,color=TextPrimary,fontSize=15.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold); Text(result.linkoId,color=Blue,fontSize=11.sp,fontFamily=JetBrainsMono); Text(if (result.isSharing) "SHARING NOW" else "ONLINE / NOT SHARING",color=if (result.isSharing) Green else TextSub,fontSize=10.sp,fontFamily=JetBrainsMono) } }; Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.weight(1f)); PrimaryButton(if (searching) "SEARCHING..." else "SEARCH",{ if (!searching) { searching=true; message=null; kotlinx.coroutines.GlobalScope.launch { try { results=api.search(query); if (results.isEmpty()) message="No LINKO users found." } catch (e:Exception) { message=e.message ?: "Search failed" } finally { searching=false } } } }); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FriendProfileScreen(friend: FriendSearchResult?, api: LinkoFriendsApi, onSendRequest:()->Unit) {
    var sending by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp)); Ring(Blue,120.dp,label="DEVICE"); Spacer(Modifier.height(16.dp)); Text(friend?.displayName ?: "LINKO USER",color=TextPrimary,fontSize=20.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(friend?.linkoId ?: "No friend selected",color=Blue,fontSize=13.sp,fontFamily=JetBrainsMono); Spacer(Modifier.height(28.dp))
        LinkoCard { InfoRow("STATUS",if (friend?.isSharing == true) "SHARING" else "AVAILABLE",friend?.deviceName ?: "Real LINKO account",accent=if (friend?.isSharing == true) Green else Blue) }
        if (message != null) { Spacer(Modifier.height(10.dp)); Text(message!!,color=Red,fontSize=11.sp,fontFamily=JetBrainsMono) }
        Spacer(Modifier.weight(1f)); PrimaryButton(if (sending) "SENDING..." else "SEND REQUEST",{ if (!sending && friend != null) { sending=true; scope.launch { try { api.sendRequest(friend.userId); onSendRequest() } catch (e:Exception) { message=e.message ?: "Request failed"; sending=false } } } },color=if (friend != null) Blue else TextMuted,outline=friend == null); Spacer(Modifier.height(24.dp))
    }
}

@Composable fun RequestSentScreen(onCancel:()->Unit){Column(Modifier.fillMaxSize().padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.weight(1f));Ring(Yellow,180.dp,pulse=true,label="PENDING",onClick=onCancel);Spacer(Modifier.height(20.dp));Text("Request Sent",color=TextPrimary,fontSize=18.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text("Waiting for the selected user to respond",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono);Spacer(Modifier.weight(1f));PrimaryButton("CANCEL REQUEST",onCancel,color=Red,outline=true);Spacer(Modifier.height(24.dp))}}
@Composable fun IncomingRequestScreen(onAccept:()->Unit,onReject:()->Unit){Column(Modifier.fillMaxSize().padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(8.dp));Text("Incoming Request",color=TextPrimary,fontSize=22.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(4.dp));Text("A LINKO user is requesting to become a friend",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono,modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(28.dp));Ring(Yellow,120.dp,label="REQUEST");Spacer(Modifier.height(20.dp));LinkoCard{InfoRow("REQUEST","Pending","You control whether the friendship is accepted")};Spacer(Modifier.weight(1f));PrimaryButton("ACCEPT",onAccept,color=Green);Spacer(Modifier.height(8.dp));PrimaryButton("REJECT",onReject,color=Red,outline=true);Spacer(Modifier.height(24.dp))}}

@Composable fun BlockedRemovedScreen(onManage:()->Unit){var tab by remember{mutableStateOf("BLOCKED")};Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){Spacer(Modifier.height(8.dp));Text("Trust Boundaries",color=TextPrimary,fontSize=22.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold);Spacer(Modifier.height(4.dp));Text("Manage devices you have blocked or removed",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono);Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth()){PrimaryButton("BLOCKED",{tab="BLOCKED"},color=if(tab=="BLOCKED")Blue else TextMuted,outline=tab!="BLOCKED");Spacer(Modifier.width(8.dp));PrimaryButton("REMOVED",{tab="REMOVED"},color=if(tab=="REMOVED")Blue else TextMuted,outline=tab!="REMOVED")};Spacer(Modifier.height(14.dp));LinkoCard{if(tab=="BLOCKED"){Text("NO BLOCKED DEVICES",color=TextMuted,fontSize=11.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)}else{Text("NO REMOVED DEVICES",color=TextMuted,fontSize=11.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)}};Spacer(Modifier.weight(1f));Text("Use the back button to return to Settings.",color=TextMuted,fontSize=10.sp,fontFamily=JetBrainsMono);Spacer(Modifier.height(24.dp))}}
