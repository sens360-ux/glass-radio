package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.media.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import android.view.View;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class RadioActivity extends Activity {
    private final ArrayList<String[]> stations=new ArrayList<>();
    private final ArrayList<String[]> visible=new ArrayList<>();
    private final LinkedHashSet<String> favorites=new LinkedHashSet<>();
    private final LinkedList<String> recent=new LinkedList<>();
    private ExoPlayer player;
    private LinearLayout stationList, favoriteList, recentList;
    private TextView status, nowName, nowLogo, countLabel;
    private Button playButton, favButton;
    private String[] current;
    private String filter="";
    private boolean playing=false;
    private static final String API="https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/MY?hidebroken=true&order=votes&reverse=true&limit=500";
    private final int WHITE=0xfff2f8ff, MUTED=0xffa5bed8, CYAN=0xff4bdcff;
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0x554e9bd1);return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(WHITE);b.setTextSize(15);b.setBackgroundTintList(ColorStateList.valueOf(0xff203c5b));b.setFocusable(true);b.setOnFocusChangeListener((v,f)->{b.setBackgroundTintList(ColorStateList.valueOf(f?0xff078bb8:0xff203c5b));b.setTextColor(f?Color.WHITE:WHITE);b.setScaleX(f?1.025f:1f);b.setScaleY(f?1.025f:1f);});return b;}
    private LinearLayout panel(String heading){LinearLayout p=column();pad(p,15,14);p.setBackground(bg(0xe0162c48,20));TextView h=text(heading,18,WHITE,true);p.addView(h,new LinearLayout.LayoutParams(-1,dp(38)));return p;}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);favorites.addAll(getPreferences(0).getStringSet("favorites",new HashSet<>()));
        recent.addAll(Arrays.asList(getPreferences(0).getString("recent","").split("\\n")));recent.removeIf(String::isEmpty);
        LinearLayout root=column();root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff07172b,0xff10385a,0xff071426}));pad(root,24,15);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("◉  GLASS TV  /  LIVE RADIO MALAYSIA",24,WHITE,true);header.addView(title,new LinearLayout.LayoutParams(0,dp(55),1));Button back=button("⌂  BACK TO GLASS TV");back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(205),dp(50)));root.addView(header);
        LinearLayout body=new LinearLayout(this);LinearLayout.LayoutParams bodyP=new LinearLayout.LayoutParams(-1,0,1);bodyP.topMargin=dp(12);root.addView(body,bodyP);
        LinearLayout left=panel("RADIO STATIONS");LinearLayout.LayoutParams leftP=new LinearLayout.LayoutParams(0,-1,34);leftP.rightMargin=dp(12);body.addView(left,leftP);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("⌕  Search Malaysian radio...");search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setTextSize(15);search.setBackground(bg(0xff0b2039,12));pad(search,12,6);left.addView(search,new LinearLayout.LayoutParams(-1,dp(46)));search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){filter=s.toString().toLowerCase(Locale.ROOT);renderStations();}public void afterTextChanged(Editable e){}});
        countLabel=text("Loading stations...",12,MUTED,false);left.addView(countLabel,new LinearLayout.LayoutParams(-1,dp(32)));
        ScrollView scroll=new ScrollView(this);stationList=column();scroll.addView(stationList);left.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout center=panel("NOW PLAYING");center.setGravity(Gravity.CENTER_HORIZONTAL);LinearLayout.LayoutParams centerP=new LinearLayout.LayoutParams(0,-1,39);centerP.rightMargin=dp(12);body.addView(center,centerP);
        nowLogo=text("♫",68,CYAN,true);nowLogo.setGravity(Gravity.CENTER);nowLogo.setBackground(bg(0xff10395a,22));LinearLayout.LayoutParams logoP=new LinearLayout.LayoutParams(dp(170),dp(155));logoP.topMargin=dp(15);center.addView(nowLogo,logoP);
        nowName=text("Select a station",27,WHITE,true);nowName.setGravity(Gravity.CENTER);LinearLayout.LayoutParams nameP=new LinearLayout.LayoutParams(-1,dp(58));nameP.topMargin=dp(8);center.addView(nowName,nameP);
        status=text("Malaysia online radio • Ready",15,MUTED,false);status.setGravity(Gravity.CENTER);center.addView(status,new LinearLayout.LayoutParams(-1,dp(35)));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);Button prev=button("◀◀");playButton=button("▶  PLAY");Button next=button("▶▶");controls.addView(prev,new LinearLayout.LayoutParams(0,dp(56),1));controls.addView(playButton,new LinearLayout.LayoutParams(0,dp(56),2));controls.addView(next,new LinearLayout.LayoutParams(0,dp(56),1));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(56));cp.topMargin=dp(15);center.addView(controls,cp);prev.setOnClickListener(v->skip(-1));next.setOnClickListener(v->skip(1));playButton.setOnClickListener(v->toggle());
        favButton=button("♡  ADD TO FAVOURITES");LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52));fp.topMargin=dp(12);center.addView(favButton,fp);favButton.setOnClickListener(v->toggleFavorite());
        LinearLayout right=panel("MY RADIO");body.addView(right,new LinearLayout.LayoutParams(0,-1,27));TextView ftitle=text("♡  FAVOURITE STATIONS",15,CYAN,true);right.addView(ftitle,new LinearLayout.LayoutParams(-1,dp(34)));ScrollView fs=new ScrollView(this);favoriteList=column();fs.addView(favoriteList);right.addView(fs,new LinearLayout.LayoutParams(-1,0,1));TextView rtitle=text("◷  RECENTLY PLAYED",15,CYAN,true);right.addView(rtitle,new LinearLayout.LayoutParams(-1,dp(38)));ScrollView rs=new ScrollView(this);recentList=column();rs.addView(recentList);right.addView(rs,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=text("REMOTE  ◀ ▶ ▲ ▼ Navigate      OK Select / Play      BACK Return to Glass TV",13,MUTED,false);footer.setGravity(Gravity.CENTER);root.addView(footer,new LinearLayout.LayoutParams(-1,dp(37)));
        setContentView(root);renderSides();load();
    }
    private void load(){new Thread(()->{ArrayList<String[]> found=new ArrayList<>();try{HttpURLConnection c=(HttpURLConnection)new URL(API).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","GlassTV/4.0");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);JSONArray arr=new JSONArray(out.toString("UTF-8"));HashSet<String> seen=new HashSet<>();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String name=o.optString("name").trim();String url=o.optString("url_resolved",o.optString("url")).trim();if(name.isEmpty()||!url.startsWith("https://")||!seen.add(name.toLowerCase(Locale.ROOT)))continue;found.add(new String[]{name,url});}}}catch(Exception ignored){}runOnUiThread(()->{stations.clear();stations.addAll(found);renderStations();if(found.isEmpty())status.setText("Station directory unavailable • Check internet");else if(stationList.getChildCount()>0)stationList.getChildAt(0).requestFocus();});}).start();}
    private void renderStations(){if(stationList==null)return;stationList.removeAllViews();visible.clear();for(String[] s:stations){if(!s[0].toLowerCase(Locale.ROOT).contains(filter))continue;visible.add(s);Button b=button("♫  "+s[0]+(favorites.contains(s[0])?"  ♥":""));b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(53));p.bottomMargin=dp(5);stationList.addView(b,p);b.setOnClickListener(v->play(s));if(visible.size()>=300)break;}countLabel.setText(stations.isEmpty()?"Connecting to radio directory...":visible.size()+" stations shown • "+stations.size()+" available");}
    private void renderSides(){if(favoriteList==null)return;favoriteList.removeAllViews();recentList.removeAllViews();for(String name:favorites)addSide(favoriteList,name);for(String name:recent)addSide(recentList,name);if(favorites.isEmpty())favoriteList.addView(text("Press ♡ to save a station",13,MUTED,false));if(recent.isEmpty())recentList.addView(text("Your played stations appear here",13,MUTED,false));}
    private void addSide(LinearLayout parent,String name){Button b=button("♫  "+name);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);parent.addView(b,new LinearLayout.LayoutParams(-1,dp(47)));b.setOnClickListener(v->{for(String[] s:stations)if(s[0].equals(name)){play(s);break;}});}
    private void toggleFavorite(){if(current==null)return;String name=current[0];if(!favorites.add(name))favorites.remove(name);getPreferences(0).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();favButton.setText(favorites.contains(name)?"♥  REMOVE FAVOURITE":"♡  ADD TO FAVOURITES");renderSides();renderStations();}
    private void skip(int dir){if(stations.isEmpty())return;int index=-1;for(int i=0;i<stations.size();i++)if(current!=null&&stations.get(i)[0].equals(current[0])){index=i;break;}play(stations.get((index+dir+stations.size())%stations.size()));}
    private void toggle(){
        if(current==null){if(!stations.isEmpty())play(stations.get(0));return;}
        if(player==null){play(current);return;}
        if(player.isPlaying()){player.pause();playing=false;playButton.setText("▶  PLAY");status.setText("Paused • "+current[0]);}
        else {player.play();playing=true;playButton.setText("Ⅱ  PAUSE");status.setText("● LIVE • "+current[0]);}
    }
    private void play(String[] s){
        stopPlayer();current=s;nowName.setText(s[0]);
        nowLogo.setText(s[0].substring(0,Math.min(3,s[0].length())).toUpperCase(Locale.ROOT));
        status.setText("Connecting to "+s[0]+"...");playButton.setText("… CONNECTING");
        favButton.setText(favorites.contains(s[0])?"♥  REMOVE FAVOURITE":"♡  ADD TO FAVOURITES");
        recent.remove(s[0]);recent.addFirst(s[0]);while(recent.size()>8)recent.removeLast();
        getPreferences(0).edit().putString("recent",android.text.TextUtils.join("\n",recent)).apply();
        renderSides();
        try{
            ExoPlayer exo=new ExoPlayer.Builder(this).build();player=exo;
            exo.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){
                    if(player!=exo)return;
                    if(state==Player.STATE_READY){playing=true;playButton.setText("Ⅱ  PAUSE");status.setText("● LIVE • "+s[0]);}
                    else if(state==Player.STATE_BUFFERING){status.setText("Buffering • "+s[0]);}
                }
                @Override public void onPlayerError(PlaybackException error){
                    if(player==exo){playing=false;playButton.setText("▶  RETRY");status.setText("Stream unavailable • Try another station");}
                }
            });
            exo.setMediaItem(MediaItem.fromUri(s[1]));exo.prepare();exo.play();
        }catch(Exception e){stopPlayer();status.setText("Unable to play this station");playButton.setText("▶  RETRY");}
    }
    private void stopPlayer(){playing=false;if(player!=null){player.release();player=null;}}
    @Override protected void onDestroy(){stopPlayer();super.onDestroy();}
}
