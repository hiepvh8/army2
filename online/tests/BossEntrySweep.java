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

/** Observe first-level boss entry on a fresh original game character. */
public final class BossEntrySweep extends EmulatorClient {
  static Class<?> canvas;
  static Object cur() throws Exception{return canvas.getField("curScr").get(null);}
  static Object dlg() throws Exception{return canvas.getField("currentDialog").get(null);}
  static String info(Object o){try{return Arrays.toString((String[])BotAudit.get(o,"info"));}catch(Exception e){return o==null?"":o.getClass().getName();}}
  static String clean(String x){return x.replace('\t',' ').replace('\n',' ').replace('\r',' ');}
  public static void main(String[] a){try{runBoss(a);System.exit(0);}catch(Throwable t){
    while(t instanceof InvocationTargetException && t.getCause()!=null)t=t.getCause();
    System.out.println("BOSS_ERROR\t"+t.getClass().getName()+"\t"+clean(String.valueOf(t.getMessage())));t.printStackTrace();System.exit(2);}}
  static void runBoss(String[] a) throws Exception{
    Headless h=new Headless();Common e=(Common)BotAudit.get(h,"emulator");ArrayList<String> p=new ArrayList<String>();
    p.add("--rms");p.add("memory");p.add(a[0]);e.initParams(p,new DeviceEntry("Default device",null,"org/microemu/device/default/device.xml",true,false),J2SEDevice.class);
    J2SEDeviceDisplay d=(J2SEDeviceDisplay)DeviceFactory.getDevice().getDeviceDisplay();d.setDisplayRectangle(new Rectangle(0,0,480,800));d.setDisplayPaintable(new Rectangle(0,0,480,800));
    e.initMIDlet(true);app=MIDletBridge.getCurrentMIDlet();ClassLoader cl=app.getClass().getClassLoader();canvas=cl.loadClass("coreLG.CCanvas");
    await(()->cur()!=null,"launcher");Class<?> game=cl.loadClass("com.teamobi.mobiarmy2.GameMidlet");
    game.getMethod("startNewOfflineGame",String.class).invoke(null,"BossTester");
    Class<?> safe=cl.loadClass("com.teamobi.mobiarmy2.OfflineSaveSafe");if(!((Boolean)safe.getMethod("newGameSucceeded").invoke(null)))throw new AssertionError("save creation");
    game.getMethod("enterOfflineMenu").invoke(null);game.getMethod("openOfflineBossRoomList").invoke(null);
    await(()->cur().getClass().getName().equals("screen.RoomListScr2"),"boss list");
    Object list=cur();Vector rooms=(Vector)BotAudit.get(list,"roomList");int idx=Integer.parseInt(a[2]),found=-1;String name="";
    for(int i=0;i<rooms.size();i++){Object room=rooms.elementAt(i);int id=((Byte)BotAudit.get(room,"id")).intValue(),lv=((Byte)BotAudit.get(room,"lv")).intValue() & 255;
      if(id!=-1 && lv==idx){found=i;name=String.valueOf(BotAudit.get(room,"name"));break;}}
    if(found<0){System.out.println("BOSS_RESULT\t"+idx+"\tNOT_LISTED\t\t\t");return;}
    list.getClass().getField("selected").setInt(list,found);System.out.println("BOSS_SELECTED\t"+idx+"\t"+clean(name));
    BotAudit.act(list,"center");Thread.sleep(800);Object dialog=dlg();
    int prompts=0;
    while(dialog!=null && dialog.getClass().getName().equals("model.InputDlg") && prompts<3){
      System.out.println("BOSS_INPUT\t"+idx+"\t"+clean(info(dialog))+"\t1");
      Object input=dialog.getClass().getField("tfInput").get(dialog);Display disp=Display.getDisplay((javax.microedition.midlet.MIDlet)app);
      if(disp.getCurrent() instanceof TextBox){Method close=input.getClass().getDeclaredMethod("closeNativeTextBox");close.setAccessible(true);close.invoke(input);}
      input.getClass().getMethod("setText",String.class).invoke(input,"1");BotAudit.act(dialog,"center");Thread.sleep(1600);
      prompts++;dialog=dlg();
    }
    if(cur().getClass().getName().equals("screen.PrepareScr") && dlg()==null){
      Object button=BotAudit.get(cur(),"cmdStart"),handler=button.getClass().getField("action").get(button);
      Method perform=handler.getClass().getMethod("perform");perform.setAccessible(true);perform.invoke(handler);Thread.sleep(2500);
    }
    String screen=cur()==null?"null":cur().getClass().getName();dialog=dlg();String message=clean(info(dialog));
    String status=dialog!=null?"DIALOG":(screen.equals("screen.GameScr")?"BATTLE":"SCREEN");
    screenshot(a[1]+"/screen.png");
    System.out.println("BOSS_RESULT\t"+idx+"\t"+status+"\t"+clean(name)+"\t"+screen+"\t"+message);
  }
}
