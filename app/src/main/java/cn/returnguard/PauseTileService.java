package cn.returnguard;
import android.service.quicksettings.*;
public final class PauseTileService extends TileService {
 @Override public void onStartListening(){refresh();}
 @Override public void onClick(){Prefs p=new Prefs(this);if(!p.enabled())return;if(p.paused())p.resume();else p.pause();refresh();}
 private void refresh(){Tile t=getQsTile();if(t==null)return;Prefs p=new Prefs(this);t.setState(p.active()?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);t.setLabel(!p.enabled()?"保护未开启":p.paused()?"已暂停 · 点此恢复":"保护中 · 点此暂停");t.updateTile();}
}
