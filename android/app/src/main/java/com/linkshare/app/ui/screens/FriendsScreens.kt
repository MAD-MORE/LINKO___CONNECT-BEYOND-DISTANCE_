package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*

/** Prototype trusted peers used by the UI flows until the real friends repository is wired. */
val sampleFriends = listOf(
    Friend("Kwame Mensah", "@kwame", "ONLINE", Green),
    Friend("Ama Owusu", "@ama", "ONLINE", Blue),
    Friend("Kofi Asante", "@kofi", "OFFLINE", TextMuted),
    Friend("Yaa Boateng", "@yaa", "ONLINE", Yellow),
)

@Composable
fun FriendsScreen(onFindFriends: () -> Unit, onFriendTap: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Friends", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Your trusted connection network", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(16.dp)); Text("4 TRUSTED • VERIFIED PEERS", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp); Spacer(Modifier.height(12.dp))
        LinkoCard { sampleFriends.forEachIndexed { i, friend -> FriendRow(friend, onClick = onFriendTap); if (i < sampleFriends.lastIndex) RowDivider() } }
        Spacer(Modifier.weight(1f)); PrimaryButton("+ FIND FRIENDS", onFindFriends); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FindFriendsScreen(onSearch: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Find Friends", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Search for someone to connect with", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp)); LinkoInput("SEARCH", query, { query = it }, "Name or Linko ID", "Discover people to add"); Spacer(Modifier.weight(1f)); PrimaryButton("SEARCH", onSearch); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun FriendProfileScreen(onSendRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp)); Avatar("KM", Green, 80.dp); Spacer(Modifier.height(16.dp)); Text("Kwame Mensah", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("@kwame", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(8.dp)); Text("VERIFIED", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.12.sp); Spacer(Modifier.height(28.dp)); LinkoCard { InfoRow("TRUST", "Pending", "Connection access is not active", accent = Yellow) }; Spacer(Modifier.weight(1f)); PrimaryButton("SEND REQUEST", onSendRequest); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RequestSentScreen(onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f)); Ring(Yellow, 180.dp, pulse = true, label = "PENDING", onClick = onCancel); Spacer(Modifier.height(20.dp)); Text("Request Sent", color = TextPrimary, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("Waiting for trust approval from", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(4.dp)); Text("Kwame Mensah", color = TextPrimary, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); PrimaryButton("CANCEL REQUEST", onCancel, color = Red, outline = true); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun IncomingRequestScreen(onAccept: () -> Unit, onReject: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp)); Text("Incoming Request", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(4.dp)); Text("A trusted connection request arrived", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(28.dp)); Avatar("KM", Blue, 72.dp); Spacer(Modifier.height(12.dp)); Text("Kwame Mensah", color = TextPrimary, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text("@kwame", color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(8.dp)); Text("VERIFIED", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.12.sp); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("PERMISSION REQUESTED", "Connection access", "You control every session") }; Spacer(Modifier.weight(1f)); PrimaryButton("ACCEPT", onAccept, color = Green); Spacer(Modifier.height(8.dp)); PrimaryButton("REJECT", onReject, color = Red, outline = true); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun BlockedRemovedScreen(onManage: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Trust Boundaries", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Manage blocked and removed peers", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp)); LinkoCard { InfoRow("BLOCKED", "0 people", "Blocked peers cannot request connections") }; Spacer(Modifier.height(10.dp)); LinkoCard { InfoRow("REMOVED", "0 people", "Removal ends trust") }; Spacer(Modifier.weight(1f)); PrimaryButton("MANAGE", onManage); Spacer(Modifier.height(24.dp))
    }
}
