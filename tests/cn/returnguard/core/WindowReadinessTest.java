package cn.returnguard.core;
public final class WindowReadinessTest {
 public static void main(String[] args)throws Exception {
  Class<?> c;try{c=Class.forName("cn.returnguard.core.WindowReadiness");}catch(ClassNotFoundException e){throw new AssertionError("Android 15 animation gaps currently erase the protected source");}
  Object gate=c.getConstructor().newInstance();java.lang.reflect.Method wait=c.getMethod("shouldDefer",boolean.class,long.class);
  GuardEngine engine=new GuardEngine(10000,false);engine.update("source",false,true,false,1000);
  check((boolean)wait.invoke(gate,true,1100L),"first animation gap must defer");
  check((boolean)wait.invoke(gate,true,2000L),"900ms Android 15 gap must defer");
  check(engine.source().equals("source"),"waiting does not clear verified source");
  check(!(boolean)wait.invoke(gate,false,2100L),"known target exits wait");
  check(engine.update("target",false,false,false,2100)==GuardEngine.Action.BACK,"confirmed target after gap still returns");
  check((boolean)wait.invoke(gate,true,2200L),"return animation also waits");
  check(!(boolean)wait.invoke(gate,true,3700L),"gap timeout is bounded at 1500ms");
  check(!(boolean)wait.invoke(gate,true,3800L),"duplicate unknown events cannot renew grace");
  check(engine.update(null,false,false,false,3800)==GuardEngine.Action.CANCELLED,"expired ambiguity cancels recovery");
  check(!(boolean)wait.invoke(gate,false,3900L),"system focus cancels immediately");
  check((boolean)wait.invoke(gate,true,4000L),"new transition can start after reset");
  check(!(boolean)wait.invoke(gate,true,3999L),"clock rollback does not extend wait");
  System.out.println("Window readiness: 12 checks passed");
 }
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
