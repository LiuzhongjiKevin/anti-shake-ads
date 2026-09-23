package cn.returnguard.core;
public final class ProtectionStateTest {
 public static void main(String[] args) throws Exception {
  Class<?> type;
  try { type=Class.forName("cn.returnguard.core.ProtectionState"); }
  catch(ClassNotFoundException e){throw new AssertionError("Missing protection state: cannot distinguish connected service from enabled preference");}
  java.lang.reflect.Method method=type.getMethod("resolve",boolean.class,boolean.class,boolean.class,boolean.class);
  String[] expected={"OFF","OFF","OFF","OFF","OFF","OFF","OFF","OFF","NEEDS_ACCESSIBILITY","NEEDS_ACCESSIBILITY","NEEDS_ACCESSIBILITY","NEEDS_ACCESSIBILITY","NEEDS_APPS","NEEDS_APPS","READY","PAUSED"};
  for(int n=0;n<16;n++){
   String actual=method.invoke(null,(n&8)!=0,(n&4)!=0,(n&2)!=0,(n&1)!=0).toString();
   if(!actual.equals(expected[n]))throw new AssertionError("State "+n+": expected "+expected[n]+", got "+actual);
  }
  System.out.println("Protection state: 16 combinations passed");
 }
}
