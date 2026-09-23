package cn.returnguard;
import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.*;
final class Prefs {
 final SharedPreferences data;private final Context context;
 Prefs(Context c){context=c;data=c.getSharedPreferences("guard_settings",Context.MODE_PRIVATE);}
 boolean enabled(){return data.getBoolean("enabled",false);}
 void stop(){data.edit().putBoolean("enabled",false).putBoolean("setup_requested",false).apply();resume();}
 boolean active(){return enabled()&&!paused();}
 private int boot(){return Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
 boolean paused(){long now=SystemClock.elapsedRealtime();return boot()==data.getInt("pauseBoot",-2)&&now>=data.getLong("pauseStart",0)&&now<data.getLong("pauseUntil",0);}
 void pause(){long n=SystemClock.elapsedRealtime();data.edit().putLong("pauseStart",n).putLong("pauseUntil",n+60000).putInt("pauseBoot",boot()).apply();}
 void resume(){data.edit().remove("pauseUntil").remove("pauseStart").remove("pauseBoot").apply();}
 long window(){int s=data.getInt("seconds",10);return (s==5||s==10||s==20||s==30?s:10)*1000L;}
 boolean fallback(){return data.getBoolean("fallback",false);}
 Set<String> sources(){return new HashSet<>(data.getStringSet("sources",new HashSet<>()));}
 Set<String> allowed(String s){return new HashSet<>(data.getStringSet("allow:"+s,new HashSet<>()));}
 boolean allowed(String s,String t){return s!=null&&allowed(s).contains(t);}
 void saveSources(Set<String> v){data.edit().putStringSet("sources",new HashSet<>(v)).apply();}
 void saveAllowed(String s,Set<String> v){data.edit().putStringSet("allow:"+s,new HashSet<>(v)).apply();}
}
