package cn.returnguard;
import android.content.*;
import org.json.*;
final class EventLog {
 private final SharedPreferences store;
 EventLog(Context c){store=c.getSharedPreferences("guard_events",Context.MODE_PRIVATE);}
 synchronized JSONArray read(){try{return new JSONArray(store.getString("events","[]"));}catch(Exception e){return new JSONArray();}}
 synchronized void add(String s,String t,String result,int attempts,long elapsed){try{JSONArray old=read(),next=new JSONArray();next.put(new JSONObject().put("at",System.currentTimeMillis()).put("source",s==null?"?":s).put("target",t==null?"?":t).put("result",result).put("attempts",attempts).put("elapsed",elapsed));for(int i=0;i<Math.min(99,old.length());i++)next.put(old.get(i));store.edit().putString("events",next.toString()).apply();}catch(Exception ignored){}}
 void clear(){store.edit().clear().apply();}
}
