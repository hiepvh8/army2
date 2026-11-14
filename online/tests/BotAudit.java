import java.lang.reflect.*;
import java.util.*;
import javax.microedition.lcdui.*;
import org.microemu.app.Headless;
import org.microemu.app.Common;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.device.DeviceFactory;
import org.microemu.device.impl.Rectangle;
import org.microemu.MIDletBridge;

/** Isolated test of original offline PvP bot setup and a turn. */
public final class BotAudit extends EmulatorClient {
  static Class<?> canvas;static String out;
  interface Check2 {boolean ok() throws Exception;}
  static void waitFor(Check2 c,String m) throws Exception {long t=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<t){if(c.ok())return;Thread.sleep(100);}throw new AssertionError("Timeout: "+m);}
  static Object get(Object o,String name) throws Exception {Class<?> c=o.getClass();while(c!=null){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
  static Object cur() throws Exception{return canvas.getField("curScr").get(null);}
  static void snapshot(String n) throws Exception{screenshot(out+"/"+n+".png");}
  static void act(Object o,String side) throws Exception {Object cmd=o.getClass().getField(side).get(o);Object action=cmd.getClass().getField("action").get(cmd);Method m=action.getClass().getMethod("perform");m.setAccessible(true);m.invoke(action);}
  public static void main(String[] args){try{runBot(args);System.out.println("BOT_AUDIT_PASS");System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
  static void runBot(String[] a) throws Exception {
    out=a[1];Headless headless=new Headless();Common emu=(Common)get(headless,"emulator");
    ArrayList<String> params=new ArrayList<String>();params.add("--rms");params.add("memory");params.add(a[0]);
    emu.initParams(params,new DeviceEntry("Default device",null,"org/microemu/device/default/device.xml",true,false),J2SEDevice.class);
    J2SEDeviceDisplay dd=(J2SEDeviceDisplay)DeviceFactory.getDevice().getDeviceDisplay();dd.setDisplayRectangle(new Rectangle(0,0,480,800));dd.setDisplayPaintable(new Rectangle(0,0,480,800));
    emu.initMIDlet(true);Object app=MIDletBridge.getCurrentMIDlet();ClassLoader loader=app.getClass().getClassLoader();
    canvas=loader.loadClass("coreLG.CCanvas");waitFor(()->cur()!=null && cur().getClass().getName().equals("army2.online.GameMenu"),"main menu");
    Class<?> game=loader.loadClass("com.teamobi.mobiarmy2.GameMidlet");game.getMethod("startNewOfflineGame",String.class).invoke(null,"BotTester");
    Class<?> safe=loader.loadClass("com.teamobi.mobiarmy2.OfflineSaveSafe");if(!((Boolean)safe.getMethod("newGameSucceeded").invoke(null)))throw new AssertionError("create save");
    game.getMethod("enterOfflineMenu").invoke(null);game.getMethod("openPvpBotSetup").invoke(null);
    waitFor(()->cur().getClass().getName().equals("screen.PvpBotSetupScr"),"bot setup");Object setup=cur();
    int[] maps=(int[])get(setup,"mapIds");String[] names=(String[])get(setup,"mapNames");System.out.println("BOT_MAPS="+Arrays.toString(maps)+" names="+Arrays.toString(names));snapshot("01-bot-setup");
    act(setup,"right");waitFor(()->cur().getClass().getName().equals("screen.PrepareScr"),"bot prepare");snapshot("02-bot-prepare");
    Object start=get(cur(),"cmdStart"),action=start.getClass().getField("action").get(start);Method perform=action.getClass().getMethod("perform");perform.setAccessible(true);perform.invoke(action);
    waitFor(()->cur().getClass().getName().equals("screen.GameScr"),"bot battle");snapshot("03-bot-battle");
    Class<?> pm=loader.loadClass("player.PM"), ai=loader.loadClass("com.teamobi.mobiarmy2.OfflinePvpBotAI");
    Object player=pm.getMethod("getMyPlayer").invoke(null);if(player==null)throw new AssertionError("no local player");
    player.getClass().getField("angle").setInt(player,45);player.getClass().getField("force").setByte(player,(byte)30);
    player.getClass().getMethod("shoot").invoke(player);if(player.getClass().getField("lastForcePoint").getInt(player)!=30)throw new AssertionError("shot not sent");
    snapshot("04-player-shot");Field last=ai.getDeclaredField("lastBotIndex");last.setAccessible(true);
    waitFor(()->last.getInt(null)>=0,"bot started its turn");System.out.println("BOT_TURN_INDEX="+last.getInt(null));snapshot("05-bot-turn");
    Field flying=ai.getDeclaredField("shotInFlight");flying.setAccessible(true);
    long deadline=System.currentTimeMillis()+25000;boolean shot=false;
    while(System.currentTimeMillis()<deadline){if(flying.getBoolean(null)){shot=true;break;}Thread.sleep(50);}
    System.out.println("BOT_SHOT_OBSERVED="+shot);snapshot("06-bot-observed");
    if(!shot)throw new AssertionError("Bot shot not observed during test window");
  }
}
