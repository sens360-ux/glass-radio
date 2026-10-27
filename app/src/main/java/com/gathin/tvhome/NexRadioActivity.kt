package com.gathin.tvhome

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

private data class Station(val name:String,val url:String)
private val cyan=Color(0xFF22D3EE)
private val white=Color(0xFFF1F5F9)
private val muted=Color(0xFF98A9C1)
private val glass=Color(0xB20B1A33)

class NexRadioActivity:ComponentActivity(){
 private var engine:ExoPlayer?=null
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  engine=ExoPlayer.Builder(this).build()
  val prefs=getSharedPreferences("nexradio",MODE_PRIVATE)
  val legacy=getSharedPreferences("RadioActivity",MODE_PRIVATE)
  setContent {
   var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
   var selected by remember { mutableStateOf<Station?>(null) }
   var playing by remember { mutableStateOf(false) }
   var status by remember { mutableStateOf("Loading Malaysian stations…") }
   var favourites by remember { mutableStateOf((prefs.getStringSet("favourites",null) ?: legacy.getStringSet("favorites",emptySet()) ?: emptySet()).toSet()) }
   var recent by remember { mutableStateOf(prefs.getString("recent","")!!.split("\n").filter { it.isNotBlank() }) }
   fun fav(name:String){favourites=if(name in favourites) favourites-name else favourites+name;prefs.edit().putStringSet("favourites",favourites).apply()}
   fun play(s:Station){selected=s;status="Connecting…";recent=(listOf(s.name)+recent.filter { it!=s.name }).take(12);prefs.edit().putString("recent",recent.joinToString("\n")).apply()
    engine?.apply {stop();setMediaItem(MediaItem.fromUri(s.url));prepare();play()};playing=true}
   DisposableEffect(Unit){val listener=object:Player.Listener{
    override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_READY)status="● LIVE";if(state==Player.STATE_BUFFERING)status="Buffering…"}
    override fun onPlayerError(error:androidx.media3.common.PlaybackException){status="Stream unavailable";playing=false}
    override fun onIsPlayingChanged(isPlaying:Boolean){playing=isPlaying}
   };engine?.addListener(listener);onDispose {engine?.removeListener(listener)}}
   LaunchedEffect(Unit){stations=withContext(Dispatchers.IO){try{
    val arr=JSONArray(URL("https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/MY?hidebroken=true&order=votes&reverse=true&limit=300").openText())
    (0 until arr.length()).mapNotNull {i->val o=arr.getJSONObject(i);val name=o.optString("name").trim();val url=o.optString("url_resolved",o.optString("url"));if(name.isBlank()||!url.startsWith("http"))null else Station(name,url)}.distinctBy {it.name.lowercase()}
   }catch(e:Exception){emptyList()}};status=if(stations.isEmpty())"Directory unavailable" else "Select a station"}
   val transition=rememberInfiniteTransition(label="radio")
   val pulse by transition.animateFloat(0.35f,1f,infiniteRepeatable(tween(700),RepeatMode.Reverse),label="pulse")
   Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF050B1A),Color(0xFF112744),Color(0xFF050B1A)))).padding(20.dp)){
    Column {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
      Text("◈  NexTV  /  Radio",color=white,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
      Text("HOME",color=cyan,modifier=Modifier.clickable {finish()}.padding(12.dp))
     }
     Spacer(Modifier.height(16.dp))
     Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(16.dp)){
      Column(Modifier.weight(0.34f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(glass).border(1.dp,Color(0x554FA3FF),RoundedCornerShape(22.dp)).padding(16.dp)){
       Text("RADIO STATIONS",color=white,fontSize=17.sp,fontWeight=FontWeight.Bold)
       Spacer(Modifier.height(8.dp))
       Text("${stations.size} stations  •  Hold OK to favourite",color=muted,fontSize=12.sp)
       Spacer(Modifier.height(10.dp))
       LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp)){
        items(stations,key={it.name}){s->
         var focused by remember {mutableStateOf(false)}
         var held by remember {mutableStateOf(false)}
         Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(13.dp))
          .background(if(focused||selected?.name==s.name)Color(0xFF135477) else Color(0xFF162942))
          .onFocusChanged{focused=it.isFocused}
          .onPreviewKeyEvent {ev->
           if(ev.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_DPAD_CENTER||ev.nativeKeyEvent.keyCode==KeyEvent.KEYCODE_ENTER){
            if(ev.type==KeyEventType.KeyDown&&ev.nativeKeyEvent.repeatCount>0&&!held){held=true;fav(s.name);true}
            else if(ev.type==KeyEventType.KeyUp){val wasHeld=held;held=false;if(!wasHeld)play(s);true}
            else true
           }else false
          }.clickable {play(s)}.focusable().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){
          Text("♫",color=cyan,fontSize=22.sp);Spacer(Modifier.width(12.dp))
          Text(s.name,color=white,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
          Text(if(s.name in favourites)"♥" else "♡",color=cyan,fontSize=19.sp)
         }
        }
       }
      }
      Column(Modifier.weight(0.66f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(glass).border(1.dp,Color(0x554FA3FF),RoundedCornerShape(22.dp)).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
       Text("NOW PLAYING",color=cyan,fontSize=16.sp,fontWeight=FontWeight.Bold)
       Spacer(Modifier.height(16.dp))
       Box(Modifier.size(150.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFE5B5),Color(0xFFBD8C56)))),contentAlignment=Alignment.Center){
        Text("♫",fontSize=82.sp,color=Color(0xFF24364B))
       }
       Spacer(Modifier.height(14.dp))
       Text(selected?.name?:"Choose your station",fontSize=29.sp,fontWeight=FontWeight.Bold,color=white,maxLines=1,overflow=TextOverflow.Ellipsis)
       Text(status,color=if(playing)cyan else muted,fontSize=14.sp)
       Spacer(Modifier.height(14.dp))
       Row(Modifier.height(44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
        repeat(30){i->val h=if(playing)(8+(kotlin.math.abs(kotlin.math.sin(i*0.71+pulse*3.1))*32)).dp else 5.dp
         Box(Modifier.width(5.dp).height(h).clip(RoundedCornerShape(3.dp)).background(cyan.copy(alpha=if(playing)0.9f else 0.35f)))
        }
       }
       Spacer(Modifier.height(12.dp))
       Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
        RadioControl("◀◀"){val i=stations.indexOfFirst{it.name==selected?.name};if(stations.isNotEmpty())play(stations[(i-1+stations.size)%stations.size])}
        RadioControl(if(playing)"Ⅱ  PAUSE" else "▶  PLAY"){if(selected==null){stations.firstOrNull()?.let(::play)}else if(playing)engine?.pause() else engine?.play()}
        RadioControl("▶▶"){val i=stations.indexOfFirst{it.name==selected?.name};if(stations.isNotEmpty())play(stations[(i+1+stations.size)%stations.size])}
        RadioControl(if(selected?.name in favourites)"♥" else "♡"){selected?.let{fav(it.name)}}
       }
       Spacer(Modifier.height(15.dp))
       Text("FAVOURITES  ${favourites.size}    •    RECENTLY PLAYED  ${recent.size}",color=muted,fontSize=13.sp)
       Spacer(Modifier.height(6.dp))
       Text("♥  "+favourites.take(4).joinToString("   •   ").ifBlank { "Hold OK on a station to save it" },color=cyan,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
       Text("◷  "+recent.take(4).joinToString("   •   ").ifBlank { "No recently played stations" },color=muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
      }
     }
     Spacer(Modifier.height(10.dp))
     Text("D-PAD  Navigate     OK  Play     HOLD OK  Favourite     BACK  Home",color=muted,fontSize=12.sp)
    }
   }
  }
 }
 override fun onDestroy(){engine?.release();engine=null;super.onDestroy()}
}
@Composable private fun RadioControl(label:String,onClick:()->Unit){
 var focused by remember {mutableStateOf(false)}
 Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if(focused)cyan else Color(0xFF1D4264))
  .onFocusChanged{focused=it.isFocused}.clickable(onClick=onClick).focusable().padding(horizontal=20.dp,vertical=13.dp),contentAlignment=Alignment.Center){
  Text(label,color=if(focused)Color(0xFF06182A) else white,fontWeight=FontWeight.Bold,fontSize=15.sp)
 }
}
