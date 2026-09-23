package cn.returnguard.core;
public final class GuardEngineTest {
 private static int count;private static GuardEngine g;
 private static void fresh(){g=new GuardEngine(10000,false);g.update("source",false,true,false,0);}
 private static void eq(Object e,Object a){if(!e.equals(a))throw new AssertionError("expected "+e+" got "+a);}
 private static void test(String n,Runnable r){r.run();count++;System.out.println("PASS "+n);}
 private static GuardEngine.Action target(long t){return g.update("target",false,false,false,t);}
 public static void main(String[] args){
  test("automatic or clicked cross-app transition returns",()->{fresh();eq(GuardEngine.Action.BACK,target(100));});
  test("same-package navigation ignored",()->{fresh();eq(GuardEngine.Action.NONE,g.update("source",false,true,false,200));});
  test("outside window not blocked",()->{fresh();eq(GuardEngine.Action.NONE,target(10001));});
  test("deadline inclusive",()->{fresh();eq(GuardEngine.Action.BACK,target(10000));});
  test("duplicate source events never extend deadline",()->{fresh();g.update("source",false,true,false,9000);eq(GuardEngine.Action.NONE,target(10001));});
  test("allowed target not blocked",()->{fresh();eq(GuardEngine.Action.NONE,g.update("target",false,false,true,200));});
  test("unprotected source ignored",()->{g=new GuardEngine(10000,false);g.update("a",false,false,false,0);eq(GuardEngine.Action.NONE,target(100));});
  test("back accepted alone not success",()->{fresh();target(100);eq(GuardEngine.Action.NONE,g.tick("target",200));});
  test("source must be stable",()->{fresh();target(100);eq(GuardEngine.Action.NONE,g.tick("source",200));eq(GuardEngine.Action.RETURNED,g.tick("source",350));});
  test("transient source not success",()->{fresh();target(100);g.tick("source",200);eq(GuardEngine.Action.NONE,g.tick("target",250));});
  test("retries capped at three",()->{fresh();target(100);eq(GuardEngine.Action.BACK,g.tick("target",600));eq(GuardEngine.Action.BACK,g.tick("target",1100));eq(GuardEngine.Action.FAILED,g.tick("target",1600));eq(3,g.attempts());});
  test("no actions after failure",()->{fresh();target(100);g.tick("target",600);g.tick("target",1100);g.tick("target",1600);eq(GuardEngine.Action.NONE,g.tick("target",3000));});
  test("unexpected third party cancels",()->{fresh();target(100);eq(GuardEngine.Action.CANCELLED,g.tick("other",600));eq(GuardEngine.Action.NONE,g.tick("target",1000));});
  test("home cancels recovery",()->{fresh();target(100);eq(GuardEngine.Action.CANCELLED,g.update("home",true,false,false,200));});
  test("home cancels protection",()->{fresh();g.update("home",true,false,false,50);eq(GuardEngine.Action.NONE,target(100));});
  test("disable cancels pending retry",()->{fresh();target(100);g.reset();eq(GuardEngine.Action.NONE,g.tick("target",600));});
  test("unknown active window cancels",()->{fresh();target(100);eq(GuardEngine.Action.CANCELLED,g.tick(null,600));});
  test("recovery does not renew window",()->{fresh();target(9000);g.tick("source",9100);g.tick("source",9300);eq(GuardEngine.Action.NONE,target(10001));});
  test("retry stops at deadline",()->{fresh();target(9900);eq(GuardEngine.Action.CANCELLED,g.tick("target",10400));});
  test("fallback separate result",()->{g=new GuardEngine(10000,true);g.update("source",false,true,false,0);target(100);g.tick("target",600);g.tick("target",1100);eq(GuardEngine.Action.RELAUNCH,g.tick("target",1600));g.tick("source",1700);eq(GuardEngine.Action.REOPENED,g.tick("source",1900));});
  test("fallback failure cannot loop",()->{g=new GuardEngine(10000,true);g.update("source",false,true,false,0);target(100);g.tick("target",600);g.tick("target",1100);g.tick("target",1600);eq(GuardEngine.Action.FAILED,g.tick("target",2400));eq(GuardEngine.Action.NONE,g.tick("target",3200));});
  test("protected target cannot replace source",()->{fresh();target(100);g.update("target",false,true,false,200);eq("source",g.source());});
  test("clock rollback drops session",()->{fresh();target(100);eq(GuardEngine.Action.CANCELLED,g.tick("target",50));});
  test("repeated bounce session budget",()->{fresh();long t=100;for(int i=0;i<6;i++){eq(GuardEngine.Action.BACK,target(t));g.tick("source",t+20);eq(GuardEngine.Action.RETURNED,g.tick("source",t+160));t+=200;}eq(GuardEngine.Action.FAILED,target(t));eq(6,g.totalAttempts());});
  test("allow change cancels recovery",()->{fresh();target(100);eq(GuardEngine.Action.CANCELLED,g.update("target",false,false,true,200));});
  test("unknown initial window no invented source",()->{g=new GuardEngine(10000,false);g.update(null,false,true,false,0);eq(GuardEngine.Action.NONE,target(100));});
  System.out.println("All "+count+" core behavior tests passed.");
 }
}
