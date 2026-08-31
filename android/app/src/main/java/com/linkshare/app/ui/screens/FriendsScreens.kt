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
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(onFindFriends:()->Unit,onFriendTap:()->Unit){
    val api=LinkoFriendsApiHolder.api
    var friends by remember{mutableStateOf<List<FriendSearchResult>>(emptyList())}
    var incoming by remember{mutableStateOf<List<org.json.JSONObject>>(emptyList())}
    var outgoing by remember{mutableStateOf<List<org.json.JSONObject>>(emptyList())}
    var resolved by remember{mutableStateOf<List<org.json.JSONObject>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var message by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()

    fun refresh(){
        scope.launch{
            loading=true
            message=null
            try{
                val f=api.friends().optJSONArray("friends")?:org.json.JSONArray()
                friends=buildList{
                    for(i in 0 until f.length()){
                        val o=f.optJSONObject(i)?:continue
                        add(FriendSearchResult(o.optString("user_id"),o.optString("linko_id"),o.optString("display_name"),null,null,false,"friend"))
                    }
                }
                val r=api.requests().optJSONArray("requests")?:org.json.JSONArray()
                incoming=buildList{
                    for(i in 0 until r.length()){
                        val o=r.optJSONObject(i)?:continue
                        if(o.optBoolean("incoming")&&o.optString("status")=="pending")add(o)
                    }
                }
                outgoing=buildList{
                    for(i in 0 until r.length()){
                        val o=r.optJSONObject(i)?:continue
                        if(!o.optBoolean("incoming")&&o.optString("status")=="pending")add(o)
                    }
                }
                resolved=buildList{
                    for(i in 0 until r.length()){
                        val o=r.optJSONObject(i)?:continue
                        val status=o.optString("status")
                        if(!o.optBoolean("incoming")&&(status=="accepted"||status=="declined"))add(o)
                    }
                }
            }catch(e:Exception){message=e.message?:"Unable to load friends"}
            finally{loading=false}
        }
    }

    LaunchedEffect(Unit){refresh()}

    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
        Spacer(Modifier.height(8.dp))
        Text("Friends",color=TextPrimary,fontSize=22.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Your LINKO connection network",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono)
        Spacer(Modifier.height(16.dp))

        message?.let{Text(it,color=Red,fontSize=11.sp,fontFamily=JetBrainsMono);Spacer(Modifier.height(8.dp))}

        if(incoming.isNotEmpty()){
            Text("FRIEND REQUESTS",color=Yellow,fontSize=11.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            incoming.forEach{request->
                val profile=request.optJSONObject("profile")
                var responding by remember(request.optString("id")){mutableStateOf(false)}
                LinkoCard{
                    Text(profile?.optString("display_name","LINKO User")?:"LINKO User",color=TextPrimary,fontSize=15.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(profile?.optString("linko_id","")?:"",color=Blue,fontSize=11.sp,fontFamily=JetBrainsMono)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()){
                        PrimaryButton(if(responding)"..."else"ACCEPT",{
                            if(!responding){
                                responding=true
                                scope.launch{
                                    try{api.respond(request.optString("id"),true);refresh()}
                                    catch(e:Exception){message=e.message?:"Accept failed";responding=false}
                                }
                            }
                        },color=Green)
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton(if(responding)""else"DECLINE",{
                            if(!responding){
                                responding=true
                                scope.launch{
                                    try{api.respond(request.optString("id"),false);refresh()}
                                    catch(e:Exception){message=e.message?:"Decline failed";responding=false}
                                }
                            }
                        },color=Red,outline=true)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if(outgoing.isNotEmpty()){
            Text("REQUESTS SENT",color=Yellow,fontSize=11.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            outgoing.take(10).forEach{request->
                val profile=request.optJSONObject("profile")
                LinkoCard{
                    Text(profile?.optString("display_name","LINKO User")?:"LINKO User",color=TextPrimary,fontSize=15.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(profile?.optString("linko_id","")?:"",color=Blue,fontSize=11.sp,fontFamily=JetBrainsMono)
                    Spacer(Modifier.height(6.dp))
                    Text("PENDING • WAITING FOR ACCEPTANCE",color=Yellow,fontSize=10.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if(resolved.isNotEmpty()){
            Text("REQUEST HISTORY",color=Yellow,fontSize=11.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            resolved.take(10).forEach{request->
                val profile=request.optJSONObject("profile")
                val status=request.optString("status")
                val accepted=status=="accepted"
                LinkoCard{
                    Text(profile?.optString("display_name","LINKO User")?:"LINKO User",color=TextPrimary,fontSize=15.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(profile?.optString("linko_id","")?:"",color=Blue,fontSize=11.sp,fontFamily=JetBrainsMono)
                    Spacer(Modifier.height(6.dp))
                    Text(if(accepted)"ACCEPTED • YOU ARE NOW FRIENDS"else"DECLINED • REQUEST NOT ACCEPTED",color=if(accepted)Green else Red,fontSize=10.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if(friends.isEmpty()&&!loading){
            LinkoCard{
                Text("NO FRIENDS YET",color=TextMuted,fontSize=11.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Find a real LINKO user by their LINKO ID or username.",color=TextSub,fontSize=12.sp,fontFamily=JetBrainsMono)
            }
        }else{
            friends.forEach{f->
                LinkoCard{
                    Column(Modifier.fillMaxWidth().clickable{LinkoFriendsApiHolder.selected=f;onFriendTap()}){
                        Text(f.displayName,color=TextPrimary,fontSize=15.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
                        Text(f.linkoId,color=Blue,fontSize=11.sp,fontFamily=JetBrainsMono)
                        Text("FRIEND • CONNECT USING LINKO ID OR USERNAME",color=Green,fontSize=10.sp,fontFamily=JetBrainsMono)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("+ FIND FRIENDS",onFindFriends)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FindFriendsScreen(onSearch:()->Unit){
    val api=LinkoFriendsApiHolder.api
    var query by remember{mutableStateOf("")}
    var results by remember{mutableStateOf<List<FriendSearchResult>>(emptyList())}
    var searching by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
        Spacer(Modifier.height(8.dp))
        Text("Find Friends",color=TextPrimary,fontSize=22.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Search real LINKO users by ID or username",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono)
        Spacer(Modifier.height(20.dp))
        LinkoInput("SEARCH",query,{query=it},"LNK-XXXXXXXX","Enter a LINKO ID or username")
        Spacer(Modifier.height(14.dp))
        message?.let{Text(it,color=Red,fontSize=11.sp,fontFamily=JetBrainsMono);Spacer(Modifier.height(8.dp))}
        results.forEach{f->
            LinkoCard{
                Column(Modifier.fillMaxWidth().clickable{LinkoFriendsApiHolder.selected=f;onSearch()}){
                    Text(f.displayName,color=TextPrimary,fontSize=15.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
                    Text(f.linkoId,color=Blue,fontSize=11.sp,fontFamily=JetBrainsMono)
                    val relationshipLabel=when(f.relationshipStatus){
                        "friend"->"FRIENDS"
                        "outgoing_pending"->"REQUEST SENT"
                        "incoming_pending"->"REQUEST RECEIVED"
                        else->if(f.isSharing)"SHARING NOW"else"AVAILABLE"
                    }
                    val relationshipColor=when(f.relationshipStatus){"friend"->Green;"outgoing_pending","incoming_pending"->Yellow;else->TextSub}
                    Text(relationshipLabel,color=relationshipColor,fontSize=10.sp,fontFamily=JetBrainsMono)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(if(searching)"SEARCHING..."else"SEARCH",{
            if(!searching){
                searching=true;message=null
                scope.launch{
                    try{
                        if(query.trim().length<2)throw IllegalArgumentException("Enter at least 2 characters")
                        results=api.search(query)
                        if(results.isEmpty())message="No LINKO users found."
                    }catch(e:Exception){message=e.message?:"Search failed"}
                    finally{searching=false}
                }
            }
        })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FriendProfileScreen(onSendRequest:()->Unit){
    val api=LinkoFriendsApiHolder.api
    val friend=LinkoFriendsApiHolder.selected
    var sending by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val relationship=friend?.relationshipStatus ?: "none"
    val buttonLabel=when(relationship){"friend"->"FRIENDS";"outgoing_pending"->"REQUEST SENT";"incoming_pending"->"REQUEST RECEIVED";else->if(sending)"SENDING..."else"ADD FRIEND"}
    val buttonEnabled=friend!=null&&relationship=="none"&&!sending

    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Spacer(Modifier.height(24.dp))
        Ring(Blue,120.dp,label="USER")
        Spacer(Modifier.height(16.dp))
        Text(friend?.displayName?:"LINKO USER",color=TextPrimary,fontSize=20.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(friend?.linkoId?:"No friend selected",color=Blue,fontSize=13.sp,fontFamily=JetBrainsMono)
        Spacer(Modifier.height(28.dp))
        LinkoCard{InfoRow("STATUS",if(friend?.isSharing==true)"SHARING"else"AVAILABLE",friend?.deviceName?:"Real LINKO account",accent=if(friend?.isSharing==true)Green else Blue)}
        Spacer(Modifier.height(8.dp))
        Text(when(relationship){"friend"->"You are already friends. Connect using this LINKO ID or username.";"outgoing_pending"->"Your friend request is waiting for acceptance.";"incoming_pending"->"This user has already sent you a request. Open Friends to accept it.";else->"Not connected yet. Send a request using this LINKO ID or username."},color=TextSub,fontSize=11.sp,fontFamily=JetBrainsMono)
        message?.let{Spacer(Modifier.height(10.dp));Text(it,color=Red,fontSize=11.sp,fontFamily=JetBrainsMono)}
        Spacer(Modifier.weight(1f))
        PrimaryButton(buttonLabel,{
            if(buttonEnabled&&friend!=null){
                sending=true
                scope.launch{
                    try{
                        val response=api.sendRequest(friend.userId)
                        when(response.optString("state")){
                            "friend"->message="You are already friends."
                            "outgoing_pending"->onSendRequest()
                            else->message="Request is already pending."
                        }
                    }catch(e:Exception){message=e.message?:"Request failed"}
                    finally{sending=false}
                }
            }
        },color=if(buttonEnabled)Blue else TextMuted,outline=!buttonEnabled)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable fun RequestSentScreen(onCancel:()->Unit){Column(Modifier.fillMaxSize().padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.weight(1f));Ring(Yellow,180.dp,pulse=true,label="PENDING",onClick=onCancel);Spacer(Modifier.height(20.dp));Text("Request Sent",color=TextPrimary,fontSize=18.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text("Waiting for the selected user to respond",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono);Spacer(Modifier.weight(1f));PrimaryButton("BACK TO FRIENDS",onCancel,color=Blue,outline=true);Spacer(Modifier.height(24.dp))}}

@Composable fun IncomingRequestScreen(onAccept:()->Unit,onReject:()->Unit){Column(Modifier.fillMaxSize().padding(horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(8.dp));Text("Incoming Request",color=TextPrimary,fontSize=22.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(4.dp));Text("A LINKO user is requesting to become a friend",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono,modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(28.dp));Ring(Yellow,120.dp,label="REQUEST");Spacer(Modifier.height(20.dp));LinkoCard{InfoRow("REQUEST","Pending","You control whether the friendship is accepted")};Spacer(Modifier.weight(1f));PrimaryButton("ACCEPT",onAccept,color=Green);Spacer(Modifier.height(8.dp));PrimaryButton("REJECT",onReject,color=Red,outline=true);Spacer(Modifier.height(24.dp))}}

@Composable fun BlockedRemovedScreen(onManage:()->Unit){var tab by remember{mutableStateOf("BLOCKED")};Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){Spacer(Modifier.height(8.dp));Text("Trust Boundaries",color=TextPrimary,fontSize=22.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold);Spacer(Modifier.height(4.dp));Text("Manage devices you have blocked or removed",color=TextSub,fontSize=13.sp,fontFamily=JetBrainsMono);Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth()){PrimaryButton("BLOCKED",{tab="BLOCKED"},color=if(tab=="BLOCKED")Blue else TextMuted,outline=tab!="BLOCKED");Spacer(Modifier.width(8.dp));PrimaryButton("REMOVED",{tab="REMOVED"},color=if(tab=="REMOVED")Blue else TextMuted,outline=tab!="REMOVED")};Spacer(Modifier.height(14.dp));LinkoCard{Text(if(tab=="BLOCKED")"NO BLOCKED DEVICES"else"NO REMOVED DEVICES",color=TextMuted,fontSize=11.sp,fontFamily=JetBrainsMono,fontWeight=FontWeight.Bold)};Spacer(Modifier.weight(1f));Text("Use the back button to return to Settings.",color=TextMuted,fontSize=10.sp,fontFamily=JetBrainsMono);Spacer(Modifier.height(24.dp))}}
