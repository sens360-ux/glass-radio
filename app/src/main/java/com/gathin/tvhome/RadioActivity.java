package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import androidx.media3.common.MediaItem;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
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
    private int stationRequest=0;
    private float volume=0.8f;
    private LinearLayout stationList, favoriteList, recentList;
    private TextView status, nowName, nowLogo, countLabel;
    private ImageView stationArtwork;
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private int artworkRequest=0;
    private Button playButton, favButton;
    private String[] current;
    private String filter="";
    private boolean playing=false;
    private static final String API="https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/MY?hidebroken=true&order=votes&reverse=true&limit=500";
    private final int WHITE=0xfff5fbff, MUTED=0xff9ab4cf, CYAN=0xff37e4f5;
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0x7757b6e8);return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(WHITE);b.setTextSize(14);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setBackgroundTintList(ColorStateList.valueOf(0xff203c5b));b.setFocusable(true);b.setOnFocusChangeListener((v,f)->{b.animate().cancel();b.setBackgroundTintList(ColorStateList.valueOf(f?0xff0aaccb:0xff203c5b));b.setTextColor(f?0xff061c32:WHITE);b.animate().scaleX(f?1.035f:1f).scaleY(f?1.035f:1f).setDuration(135).start();b.setElevation(dp(f?8:1));});return b;}
    private LinearLayout panel(String heading){LinearLayout p=column();pad(p,18,15);p.setBackground(bg(0xe20c223e,22));TextView h=text(heading,16,WHITE,true);h.setLetterSpacing(0.08f);p.addView(h,new LinearLayout.LayoutParams(-1,dp(38)));return p;}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);favorites.addAll(getPreferences(0).getStringSet("favorites",new HashSet<>()));
        recent.addAll(Arrays.asList(getPreferences(0).getString("recent","").split("\\n")));recent.removeIf(String::isEmpty);
        LinearLayout root=column();root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff061226,0xff123e68,0xff06182e}));pad(root,25,16);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("◉  GLASS TV    /    RADIO MALAYSIA",21,WHITE,true);header.addView(title,new LinearLayout.LayoutParams(0,dp(55),1));Button back=button("⌂  HOME");back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(135),dp(48)));root.addView(header);
        LinearLayout body=new LinearLayout(this);LinearLayout.LayoutParams bodyP=new LinearLayout.LayoutParams(-1,0,1);bodyP.topMargin=dp(12);root.addView(body,bodyP);
        LinearLayout left=panel("♫   EXPLORE STATIONS");LinearLayout.LayoutParams leftP=new LinearLayout.LayoutParams(0,-1,34);leftP.rightMargin=dp(12);body.addView(left,leftP);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("⌕  Search Malaysian radio...");search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setTextSize(15);search.setBackground(bg(0xff0b2039,12));pad(search,12,6);left.addView(search,new LinearLayout.LayoutParams(-1,dp(46)));search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){filter=s.toString().toLowerCase(Locale.ROOT);renderStations();}public void afterTextChanged(Editable e){}});
        countLabel=text("Loading stations...",12,MUTED,false);left.addView(countLabel,new LinearLayout.LayoutParams(-1,dp(32)));
        ScrollView scroll=new ScrollView(this);stationList=column();scroll.addView(stationList);left.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout center=panel("◉   NOW PLAYING");center.setGravity(Gravity.CENTER_HORIZONTAL);LinearLayout.LayoutParams centerP=new LinearLayout.LayoutParams(0,-1,39);centerP.rightMargin=dp(12);body.addView(center,centerP);
        nowLogo=text("♫",76,CYAN,true);nowLogo.setGravity(Gravity.CENTER);nowLogo.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff1765a1,0xff092b50}));nowLogo.setElevation(dp(12));LinearLayout.LayoutParams logoP=new LinearLayout.LayoutParams(dp(176),dp(160));logoP.topMargin=dp(15);android.widget.FrameLayout artFrame=new android.widget.FrameLayout(this);artFrame.setBackground(bg(0xff10395a,22));artFrame.setClipToOutline(true);center.addView(artFrame,logoP);
        android.widget.FrameLayout.LayoutParams fillArt=new android.widget.FrameLayout.LayoutParams(-1,-1);
        artFrame.addView(nowLogo,fillArt);
        stationArtwork=new ImageView(this);stationArtwork.setScaleType(ImageView.ScaleType.FIT_CENTER);stationArtwork.setPadding(dp(14),dp(14),dp(14),dp(14));stationArtwork.setVisibility(View.GONE);artFrame.addView(stationArtwork,new android.widget.FrameLayout.LayoutParams(-1,-1));
        nowName=text("Choose your station",25,WHITE,true);nowName.setGravity(Gravity.CENTER);nowName.setSingleLine(true);nowName.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams nameP=new LinearLayout.LayoutParams(-1,dp(58));nameP.topMargin=dp(8);center.addView(nowName,nameP);
        status=text("READY  •  DISCOVER MALAYSIAN RADIO",13,MUTED,false);status.setGravity(Gravity.CENTER);center.addView(status,new LinearLayout.LayoutParams(-1,dp(35)));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);Button prev=button("⏮");playButton=button("▶  PLAY");Button next=button("⏭");controls.addView(prev,new LinearLayout.LayoutParams(0,dp(56),1));controls.addView(playButton,new LinearLayout.LayoutParams(0,dp(56),2));controls.addView(next,new LinearLayout.LayoutParams(0,dp(56),1));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(56));cp.topMargin=dp(15);center.addView(controls,cp);
        LinearLayout volumeRow=new LinearLayout(this);volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView volumeIcon=text("♫  VOLUME",12,MUTED,true);volumeRow.addView(volumeIcon,new LinearLayout.LayoutParams(dp(105),dp(38)));
        SeekBar volumeSlider=new SeekBar(this);volumeSlider.setMax(100);volumeSlider.setProgress(80);volumeSlider.setProgressTintList(ColorStateList.valueOf(CYAN));volumeSlider.setThumbTintList(ColorStateList.valueOf(CYAN));volumeRow.addView(volumeSlider,new LinearLayout.LayoutParams(0,dp(38),1));
        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user){volume=progress/100f;if(player!=null)player.setVolume(volume);}}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(42));vp.topMargin=dp(7);center.addView(volumeRow,vp);
        prev.setOnClickListener(v->skip(-1));next.setOnClickListener(v->skip(1));playButton.setOnClickListener(v->toggle());
        favButton=button("♡  ADD TO FAVOURITES");LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52));fp.topMargin=dp(12);center.addView(favButton,fp);favButton.setOnClickListener(v->toggleFavorite());
        LinearLayout right=panel("♡   MY COLLECTION");body.addView(right,new LinearLayout.LayoutParams(0,-1,27));TextView ftitle=text("♡   FAVOURITES",14,CYAN,true);right.addView(ftitle,new LinearLayout.LayoutParams(-1,dp(34)));ScrollView fs=new ScrollView(this);favoriteList=column();fs.addView(favoriteList);right.addView(fs,new LinearLayout.LayoutParams(-1,0,1));TextView rtitle=text("◷   RECENTLY PLAYED",14,CYAN,true);right.addView(rtitle,new LinearLayout.LayoutParams(-1,dp(38)));ScrollView rs=new ScrollView(this);recentList=column();rs.addView(recentList);right.addView(rs,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=text("◀ ▶ ▲ ▼  NAVIGATE          OK  SELECT / PLAY          BACK  HOME",12,MUTED,false);footer.setGravity(Gravity.CENTER);root.addView(footer,new LinearLayout.LayoutParams(-1,dp(37)));
        setContentView(root);renderSides();load();
    }
    private void load(){new Thread(()->{ArrayList<String[]> found=new ArrayList<>();try{HttpURLConnection c=(HttpURLConnection)new URL(API).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","GlassTV/4.0");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);JSONArray arr=new JSONArray(out.toString("UTF-8"));HashSet<String> seen=new HashSet<>();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String name=o.optString("name").trim();String url=o.optString("url_resolved",o.optString("url")).trim();String favicon=o.optString("favicon","").trim();if(name.isEmpty()||!url.startsWith("https://")||!seen.add(name.toLowerCase(Locale.ROOT)))continue;found.add(new String[]{name,url,favicon});}}}catch(Exception ignored){}runOnUiThread(()->{stations.clear();stations.addAll(found);renderStations();if(found.isEmpty())status.setText("Station directory unavailable • Check internet");else if(stationList.getChildCount()>0)stationList.getChildAt(0).requestFocus();});}).start();}
    private void renderStations(){if(stationList==null)return;stationList.removeAllViews();visible.clear();for(String[] s:stations){if(!s[0].toLowerCase(Locale.ROOT).contains(filter))continue;visible.add(s);Button b=button("♫  "+s[0]+(favorites.contains(s[0])?"  ♥":""));b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.bottomMargin=dp(7);stationList.addView(b,p);b.setOnClickListener(v->play(s));b.setOnLongClickListener(v->{toggleStationFavorite(s[0]);return true;});if(visible.size()>=300)break;}countLabel.setText(stations.isEmpty()?"Connecting to radio directory...":visible.size()+" stations shown • "+stations.size()+" available");}
    private void renderSides(){if(favoriteList==null)return;favoriteList.removeAllViews();recentList.removeAllViews();for(String name:favorites)addSide(favoriteList,name);for(String name:recent)addSide(recentList,name);if(favorites.isEmpty())favoriteList.addView(text("Press ♡ to save a station",13,MUTED,false));if(recent.isEmpty())recentList.addView(text("Your played stations appear here",13,MUTED,false));}
    private void addSide(LinearLayout parent,String name){Button b=button("♫  "+name);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);parent.addView(b,new LinearLayout.LayoutParams(-1,dp(47)));b.setOnClickListener(v->{for(String[] s:stations)if(s[0].equals(name)){play(s);break;}});}
    private void toggleStationFavorite(String name){boolean added=favorites.add(name);if(!added)favorites.remove(name);getPreferences(0).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();if(current!=null&&name.equals(current[0]))favButton.setText(added?"♥  REMOVE FAVOURITE":"♡  ADD TO FAVOURITES");renderSides();renderStations();Toast.makeText(this,name+(added?" added to favourites":" removed from favourites"),Toast.LENGTH_SHORT).show();}
    private void toggleFavorite(){if(current!=null)toggleStationFavorite(current[0]);}
    private void skip(int dir){if(stations.isEmpty())return;int index=-1;for(int i=0;i<stations.size();i++)if(current!=null&&stations.get(i)[0].equals(current[0])){index=i;break;}play(stations.get((index+dir+stations.size())%stations.size()));}
    private void toggle(){
        if(current==null){if(!stations.isEmpty())play(stations.get(0));return;}
        if(player==null){play(current);return;}
        if(player.isPlaying()){player.pause();playing=false;playButton.setText("▶  PLAY");status.setText("Paused • "+current[0]);}
        else {player.play();playing=true;playButton.setText("Ⅱ  PAUSE");status.setText("● LIVE • "+current[0]);}
    }
    private void play(String[] s){
        if(current!=null&&current[0].equals(s[0])&&player!=null){if(!player.isPlaying())player.play();return;}
        current=s;nowName.setText(s[0]);
        nowLogo.setText(s[0].substring(0,Math.min(2,s[0].length())).toUpperCase(Locale.ROOT));showArtwork(s);
        status.setText("Connecting to "+s[0]+"...");playButton.setText("… CONNECTING");
        favButton.setText(favorites.contains(s[0])?"♥  REMOVE FAVOURITE":"♡  ADD TO FAVOURITES");
        recent.remove(s[0]);recent.addFirst(s[0]);while(recent.size()>8)recent.removeLast();
        getPreferences(0).edit().putString("recent",android.text.TextUtils.join("\n",recent)).apply();
        renderSides();
        try{
            if(player==null){player=new ExoPlayer.Builder(this).build();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);player.setVolume(volume);}
            ExoPlayer exo=player;
            final int request=++stationRequest;
            exo.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){
                    if(player!=exo||request!=stationRequest)return;
                    if(state==Player.STATE_READY){playing=true;playButton.setText("Ⅱ  PAUSE");status.setText("● LIVE • "+s[0]);}
                    else if(state==Player.STATE_BUFFERING){status.setText("Buffering • "+s[0]);}
                }
                @Override public void onPlayerError(PlaybackException error){
                    if(player==exo&&request==stationRequest){playing=false;playButton.setText("▶  RETRY");status.setText("Stream unavailable • Try another station");}
                }
            });
            exo.stop();exo.clearMediaItems();exo.setMediaItem(MediaItem.fromUri(s[1]));exo.prepare();exo.play();
        }catch(Exception e){stopPlayer();status.setText("Unable to play this station");playButton.setText("▶  RETRY");}
    }
    private void showArtwork(String[] s){
        final int request=++artworkRequest;
        stationArtwork.setImageDrawable(null);stationArtwork.setVisibility(View.GONE);nowLogo.setVisibility(View.VISIBLE);
        if(s.length<3||!s[2].startsWith("https://"))return;
        new Thread(()->{android.graphics.Bitmap bitmap=null;try{
            HttpURLConnection conn=(HttpURLConnection)new URL(s[2]).openConnection();conn.setConnectTimeout(5000);conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent","GlassTV-Radio/1.0");
            try(InputStream input=conn.getInputStream()){bitmap=android.graphics.BitmapFactory.decodeStream(input);}
            conn.disconnect();
        }catch(Exception ignored){}
            final android.graphics.Bitmap result=bitmap;
            runOnUiThread(()->{if(request!=artworkRequest||isFinishing()||isDestroyed())return;
                if(result!=null){stationArtwork.setImageBitmap(result);stationArtwork.setVisibility(View.VISIBLE);nowLogo.setVisibility(View.GONE);}
            });
        }).start();
    }
    private void stopPlayer(){artworkRequest++;stationRequest++;playing=false;if(player!=null){player.release();player=null;}}
    @Override protected void onDestroy(){stopPlayer();super.onDestroy();}
}
