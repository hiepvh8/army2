import java.lang.reflect.*;
import java.util.*;
import org.microemu.app.Headless;
import org.microemu.app.Common;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.device.DeviceFactory;
import org.microemu.device.impl.Rectangle;
import org.microemu.MIDletBridge;

/** Boot one distinct original PvP-bot map in a disposable MIDlet. */
public final class BotMapSweep extends EmulatorClient {
    static Class<?> canvas;
    static Object current() throws Exception{return canvas.getField("curScr").get(null);}
    public static void main(String[] a){
        try{runMap(a);System.out.println("MAP_PASS");System.exit(0);}
        catch(Throwable t){while(t instanceof InvocationTargetException && t.getCause()!=null)t=t.getCause();
            System.out.println("MAP_ERROR\t"+t.getClass().getName()+"\t"+t.getMessage());t.printStackTrace();System.exit(2);}
    }
    static void runMap(String[] a) throws Exception {
        Headless h=new Headless();Common e=(Common)BotAudit.get(h,"emulator");
        ArrayList<String> p=new ArrayList<String>();p.add("--rms");p.add("memory");p.add(a[0]);
        e.initParams(p,new DeviceEntry("Default device",null,"org/microemu/device/default/device.xml",true,false),J2SEDevice.class);
        J2SEDeviceDisplay d=(J2SEDeviceDisplay)DeviceFactory.getDevice().getDeviceDisplay();
        d.setDisplayRectangle(new Rectangle(0,0,480,800));d.setDisplayPaintable(new Rectangle(0,0,480,800));
        e.initMIDlet(true);app=MIDletBridge.getCurrentMIDlet();ClassLoader cl=app.getClass().getClassLoader();
        canvas=cl.loadClass("coreLG.CCanvas");await(()->current()!=null,"launcher");
        Class<?> game=cl.loadClass("com.teamobi.mobiarmy2.GameMidlet");
        game.getMethod("startNewOfflineGame",String.class).invoke(null,"MapTester");
        Class<?> safe=cl.loadClass("com.teamobi.mobiarmy2.OfflineSaveSafe");
        if(!((Boolean)safe.getMethod("newGameSucceeded").invoke(null)))throw new AssertionError("new save failed");
        game.getMethod("enterOfflineMenu").invoke(null);game.getMethod("openPvpBotSetup").invoke(null);
        await(()->current().getClass().getName().equals("screen.PvpBotSetupScr"),"bot setup");
        Object setup=current();int[] ids=(int[])BotAudit.get(setup,"mapIds");String[] names=(String[])BotAudit.get(setup,"mapNames");
        int slot=Integer.parseInt(a[2]);if(slot<0 || slot>=ids.length || ids[slot]<0)throw new AssertionError("invalid map slot");
        int id=ids[slot];System.out.println("MAP_SELECTED\t"+id+"\t"+names[slot]);
        Field select=setup.getClass().getDeclaredField("curMapSlot");select.setAccessible(true);select.setInt(setup,slot);
        BotAudit.act(setup,"right");await(()->current().getClass().getName().equals("screen.PrepareScr"),"bot preparation");
        Object button=BotAudit.get(current(),"cmdStart"),handler=button.getClass().getField("action").get(button);
        Method perform=handler.getClass().getMethod("perform");perform.setAccessible(true);perform.invoke(handler);
        await(()->current().getClass().getName().equals("screen.GameScr"),"bot battle");
        Class<?> mm=cl.loadClass("map.MM");int actual=mm.getField("mapID").getInt(null);
        int width=mm.getField("mapWidth").getInt(null),height=mm.getField("mapHeight").getInt(null);
        Vector maps=(Vector)mm.getField("maps").get(null);int missing=0;
        for(int i=0;i<maps.size();i++){Object tile=maps.elementAt(i);if(BotAudit.get(tile,"map")==null)missing++;}
        System.out.println("MAP_RESULT\t"+id+"\t"+actual+"\t"+width+"\t"+height+"\t"+maps.size()+"\t"+missing);
        screenshot(a[1]+"/battle.png");
        if(actual!=id || width<=0 || height<=0 || maps.size()==0 || missing>0)throw new AssertionError("map resources incomplete");
    }
}
