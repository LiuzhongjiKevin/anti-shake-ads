package cn.returnguard.core;
public final class GuardEngineTest {
 private static int count;private static GuardEngine g;
 private static final String LANDING="com.bytedance.sdk.openadsdk.activity.TTLandingPageActivity";
 private static void fresh(){fresh(false);}
 private static void fresh(boolean fallback){g=new GuardEngine(10000,fallback);g.update("source",false,true,false,0);}
 private static void eq(Object e,Object a){if(!e.equals(a))throw new AssertionError("expected "+e+" got "+a);}
 private static void test(String n,Runnable r){r.run();count++;System.out.println("PASS "+n);}
 private static GuardEngine.Action target(long t){return g.update("target",false,false,false,t);}
 private static GuardEngine.Action ad(long t){return g.update("source",LANDING,false,true,false,t);}
 /** Target appears at 100 and first Back is sent once it has been stable for 400ms. */
 private static void firstBack(){fresh();eq(GuardEngine.Action.NONE,target(100));eq(GuardEngine.Action.BACK,g.tick("target",500));}
 public static void main(String[] args){
  // Cross-app jumps
  test("cross-app jump waits for a stable target before Back",()->{fresh();eq(GuardEngine.Action.NONE,target(100));eq(true,g.recovering());eq(GuardEngine.Action.NONE,g.tick("target",300));eq(GuardEngine.Action.BACK,g.tick("target",500));});
  test("target splash changing Activity delays first Back",()->{fresh();g.update("target","t.Splash",false,false,false,100);eq(GuardEngine.Action.NONE,g.tick("target","t.Splash2",400));eq(GuardEngine.Action.NONE,g.tick("target","t.Main",800));eq(GuardEngine.Action.NONE,g.tick("target","t.Main",1100));eq(GuardEngine.Action.BACK,g.tick("target","t.Main",1200));});
  test("target that never settles still gets Back after 1500ms",()->{fresh();g.update("target","t.A1",false,false,false,100);long t=400;for(int i=2;i<=5;i++){eq(GuardEngine.Action.NONE,g.tick("target","t.A"+i,t));t+=300;}eq(GuardEngine.Action.BACK,g.tick("target","t.A6",1600));});
  test("unknown Activity does not reset target stability",()->{fresh();g.update("target","t.Main",false,false,false,100);eq(GuardEngine.Action.BACK,g.tick("target",null,500));});
  test("same-package navigation ignored",()->{fresh();eq(GuardEngine.Action.NONE,g.update("source","com.app.Detail",false,true,false,200));eq(false,g.recovering());});
  test("outside window not blocked",()->{fresh();eq(GuardEngine.Action.NONE,target(10001));eq(false,g.recovering());});
  test("deadline inclusive",()->{fresh();target(10000);eq(true,g.recovering());});
  test("detection at window end still completes recovery",()->{fresh();target(9900);eq(GuardEngine.Action.BACK,g.tick("target",10300));});
  test("duplicate source events never extend deadline",()->{fresh();g.update("source",false,true,false,9000);target(10001);eq(false,g.recovering());});
  test("allowed target not blocked",()->{fresh();eq(GuardEngine.Action.NONE,g.update("target",false,false,true,200));eq(false,g.recovering());});
  test("unprotected source ignored",()->{g=new GuardEngine(10000,false);g.update("a",false,false,false,0);target(100);eq(false,g.recovering());});
  test("back accepted alone not success",()->{firstBack();eq(GuardEngine.Action.NONE,g.tick("target",600));});
  test("source must be stable",()->{firstBack();eq(GuardEngine.Action.NONE,g.tick("source",600));eq(GuardEngine.Action.RETURNED,g.tick("source",720));});
  test("transient source not success",()->{firstBack();g.tick("source",600);eq(GuardEngine.Action.NONE,g.tick("target",650));});
  test("retries wait for verification and are capped at three",()->{firstBack();eq(GuardEngine.Action.NONE,g.tick("target",1000));eq(GuardEngine.Action.BACK,g.tick("target",1200));eq(GuardEngine.Action.BACK,g.tick("target",1900));eq(GuardEngine.Action.FAILED,g.tick("target",2600));eq(3,g.attempts());});
  test("target navigating after Back must settle before retry",()->{firstBack();eq(GuardEngine.Action.NONE,g.tick("target","t.Home",1100));eq(GuardEngine.Action.NONE,g.tick("target","t.Home",1200));eq(GuardEngine.Action.BACK,g.tick("target","t.Home",1500));});
  test("no actions after failure",()->{firstBack();g.tick("target",1200);g.tick("target",1900);g.tick("target",2600);eq(GuardEngine.Action.NONE,g.tick("target",3500));});
  test("unexpected third party cancels",()->{firstBack();eq(GuardEngine.Action.CANCELLED,g.tick("other",600));eq(GuardEngine.Action.NONE,g.tick("target",1000));});
  test("home cancels recovery",()->{fresh();target(100);eq(GuardEngine.Action.CANCELLED,g.update("home",true,false,false,200));});
  test("home cancels protection",()->{fresh();g.update("home",true,false,false,50);target(100);eq(false,g.recovering());});
  test("disable cancels pending retry",()->{firstBack();g.reset();eq(GuardEngine.Action.NONE,g.tick("target",1200));});
  test("unknown active window cancels",()->{firstBack();eq(GuardEngine.Action.CANCELLED,g.tick(null,600));});
  test("recovery does not renew window",()->{fresh();target(9000);g.tick("target",9400);g.tick("source",9500);eq(GuardEngine.Action.RETURNED,g.tick("source",9620));target(10001);eq(false,g.recovering());});
  test("fallback relaunches source as second action",()->{fresh(true);target(100);eq(GuardEngine.Action.BACK,g.tick("target",500));eq(GuardEngine.Action.RELAUNCH,g.tick("target",1200));g.tick("source",1300);eq(GuardEngine.Action.REOPENED,g.tick("source",1420));});
  test("relaunch gets longer verification, then one more Back",()->{fresh(true);target(100);g.tick("target",500);g.tick("target",1200);eq(GuardEngine.Action.NONE,g.tick("target",1800));eq(GuardEngine.Action.BACK,g.tick("target",2100));});
  test("fallback failure cannot loop",()->{fresh(true);target(100);g.tick("target",500);g.tick("target",1200);g.tick("target",2100);eq(GuardEngine.Action.FAILED,g.tick("target",2800));eq(GuardEngine.Action.NONE,g.tick("target",3500));});
  test("protected target cannot replace source",()->{fresh();target(100);g.update("target",false,true,false,200);eq("source",g.source());});
  test("clock rollback drops session",()->{fresh();target(100);eq(GuardEngine.Action.CANCELLED,g.tick("target",50));});
  test("repeated bounce session budget",()->{fresh();long t=100;for(int i=0;i<6;i++){eq(GuardEngine.Action.NONE,target(t));eq(GuardEngine.Action.BACK,g.tick("target",t+400));g.tick("source",t+420);eq(GuardEngine.Action.RETURNED,g.tick("source",t+560));t+=600;}eq(GuardEngine.Action.FAILED,target(t));eq(6,g.totalAttempts());});
  test("allow change cancels recovery",()->{fresh();target(100);eq(GuardEngine.Action.CANCELLED,g.update("target",false,false,true,200));});
  test("unknown initial window no invented source",()->{g=new GuardEngine(10000,false);g.update(null,false,true,false,0);target(100);eq(false,g.recovering());});
  // Ad landing pages inside the protected app
  test("in-app landing page is closed with Back once settled",()->{fresh();eq(GuardEngine.Action.NONE,ad(200));eq(true,g.inApp());eq(GuardEngine.Action.NONE,g.tick("source",LANDING,500));eq(GuardEngine.Action.BACK,g.tick("source",LANDING,600));eq("source",g.target());});
  test("in-app return needs a reported non-ad page",()->{fresh();ad(200);g.tick("source",LANDING,600);eq(GuardEngine.Action.NONE,g.tick("source","com.app.Main",700));eq(GuardEngine.Action.RETURNED,g.tick("source","com.app.Main",820));eq(false,g.inApp());});
  test("in-app landing handled outside the entry window",()->{fresh();g.update("source","com.app.Main",false,true,false,20000);ad(20100);eq(true,g.recovering());});
  test("ordinary in-app web pages ignored",()->{fresh();g.update("source","com.app.WebActivity",false,true,false,200);eq(false,g.recovering());});
  test("landing page entered directly from launch is handled",()->{g=new GuardEngine(10000,false);g.update("home",true,false,false,0);eq(GuardEngine.Action.NONE,ad(100));eq(true,g.inApp());});
  test("unprotected app landing page ignored",()->{g=new GuardEngine(10000,false);g.update("other",LANDING,false,false,false,0);eq(false,g.recovering());});
  test("landing page still shown is retried",()->{fresh();ad(200);g.tick("source",LANDING,600);eq(GuardEngine.Action.BACK,g.tick("source",LANDING,1300));});
  test("unknown same-app page never triggers Back and times out",()->{fresh();ad(200);g.tick("source",LANDING,600);eq(GuardEngine.Action.NONE,g.tick("source",null,1500));eq(GuardEngine.Action.NONE,g.tick("source",null,5000));eq(GuardEngine.Action.CANCELLED,g.tick("source",null,6300));});
  test("in-app recovery never relaunches",()->{fresh(true);ad(200);eq(GuardEngine.Action.BACK,g.tick("source",LANDING,600));eq(GuardEngine.Action.BACK,g.tick("source",LANDING,1300));eq(GuardEngine.Action.BACK,g.tick("source",LANDING,2000));eq(GuardEngine.Action.FAILED,g.tick("source",LANDING,2700));});
  test("landing page opening another app switches to cross-app",()->{fresh();ad(200);eq(GuardEngine.Action.NONE,g.tick("market",400));eq(false,g.inApp());eq("market",g.target());eq(GuardEngine.Action.BACK,g.tick("market",800));});
  test("Back from target revealing landing page continues in-app",()->{firstBack();eq(GuardEngine.Action.NONE,g.tick("source",LANDING,700));eq(true,g.inApp());eq("source",g.target());eq(GuardEngine.Action.BACK,g.tick("source",LANDING,1200));g.tick("source","com.app.Main",1300);eq(GuardEngine.Action.RETURNED,g.tick("source","com.app.Main",1420));});
  test("exhausted session ignores later landing pages",()->{fresh();ad(200);g.tick("source",LANDING,600);g.tick("source",LANDING,1300);g.tick("source",LANDING,2000);g.tick("source",LANDING,2700);g.update("source","com.app.Main",false,true,false,2800);ad(2900);eq(false,g.recovering());});
  System.out.println("All "+count+" core behavior tests passed.");
 }
}
