import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.*;
import org.microemu.app.Headless;
import org.microemu.app.Common;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.device.DeviceFactory;
import org.microemu.device.impl.Rectangle;
import org.microemu.MIDletBridge;

/** Drives the packaged MIDlet in MicroEmulator, with real HTTP and real Canvas input. */
public class EmulatorClient {
    static Object app;
    static Method send;
    static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static void set(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value);
    }
    static Object state() throws Exception { return field(app, "state"); }
    static int n(Object o, String name) throws Exception { return ((Integer) field(o,name)).intValue(); }
    static String text(Object o, String name) throws Exception { return (String) field(o,name); }
    static void command(String op, String args) throws Exception { send.invoke(app,op,args); }
    interface Check { boolean ok() throws Exception; }
    static void await(Check c, String description) throws Exception {
        long deadline = System.currentTimeMillis()+30000;
        while (System.currentTimeMillis()<deadline) {
            if(c.ok()) return;
            Thread.sleep(80);
        }
        throw new AssertionError("Timeout: "+description+"; network="+field(app,"network"));
    }
    static void screenshot(String path) throws Exception {
        Thread.sleep(300);
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(480,800,java.awt.image.BufferedImage.TYPE_INT_RGB);
        J2SEDeviceDisplay dd=(J2SEDeviceDisplay)DeviceFactory.getDevice().getDeviceDisplay();
        dd.paintDisplayable(image.getGraphics(),0,0,480,800);
        ImageIO.write(image,"png",new File(path));
    }
    public static void main(String[] args) {
        try {
            run(args);
            System.out.println("EMULATOR_CLIENT_PASS " + args[3]);
            System.exit(0);
        } catch(Throwable e) { e.printStackTrace(); System.exit(1); }
    }
    static void run(String[] args) throws Exception {
        String jar=args[0], url=args[1], output=args[2], role=args[3], mode=args[4];
        boolean host=role.equals("host");
        Headless headless=new Headless();
        Common emulator=(Common)field(headless,"emulator");
        ArrayList<String> params=new ArrayList<String>();
        params.add("--rms");params.add("memory");params.add(jar);
        DeviceEntry device=new DeviceEntry("Default device",null,"org/microemu/device/default/device.xml",true,false);
        emulator.initParams(params,device,J2SEDevice.class);
        J2SEDeviceDisplay dd=(J2SEDeviceDisplay)DeviceFactory.getDevice().getDeviceDisplay();
        dd.setDisplayRectangle(new Rectangle(0,0,480,800));
        dd.setDisplayPaintable(new Rectangle(0,0,480,800));
        emulator.initMIDlet(true);
        app=MIDletBridge.getCurrentMIDlet();
        if(app==null) throw new AssertionError("MIDlet did not start");
        send=app.getClass().getMethod("send",String.class,String.class);
        set(app,"base",url);
        set(app,"token","");
        screenshot(output+"/"+role+"-home.png");
        if(mode.equals("offline")) {
            List menu=(List)field(app,"home");menu.setSelectedIndex(2,true);
            ((CommandListener)app).commandAction(List.SELECT_COMMAND,menu);
            await(()->((Boolean)app.getClass().getSuperclass().getField("isStartGame").get(null)).booleanValue(),"original offline entry");
            Thread.sleep(5000);
            Displayable current=Display.getDisplay((javax.microedition.midlet.MIDlet)app).getCurrent();
            if(!(current instanceof Canvas) || current==field(app,"arena"))throw new AssertionError("Original Canvas did not open");
            screenshot(output+"/"+role+"-offline.png");
            Files.write(new File(output,role+"-result.txt").toPath(),("mode=offline current="+current.getClass().getName()).getBytes("UTF-8"));
            return;
        }
        // Register through the real login form and its CommandListener.
        List home=(List)field(app,"home"); home.setSelectedIndex(0,true);
        ((CommandListener)app).commandAction(List.SELECT_COMMAND,home);
        ((TextField)field(app,"username")).setString(host?"EmuHost":"EmuGuest");
        ((TextField)field(app,"password")).setString("emulator-test-password");
        ((CommandListener)app).commandAction((Command)field(app,"register"),(Form)field(app,"login"));
        await(()->state()!=null,"register and decode binary response");
        if(host) {
            command("create","&mode="+mode);
            await(()->n(state(),"room")>0,"create room");
            Files.write(new File(output,"room.txt").toPath(),String.valueOf(n(state(),"room")).getBytes("UTF-8"));
        } else {
            await(()->new File(output,"room.txt").exists(),"host room file");
            String room=new String(Files.readAllBytes(new File(output,"room.txt").toPath()),"UTF-8");
            command("join","&room="+room);
            await(()->n(state(),"room")>0,"join room");
        }
        command("ready","");
        await(()->{
            Object[] players=(Object[])field(state(),"players");
            if(players.length<2)return false;
            for(Object p:players)if(!((Boolean)field(p,"ready")))return false;
            return true;
        },"both players ready");
        screenshot(output+"/"+role+"-room.png");
        if(host)command("start","");
        await(()->text(state(),"phase").equals("battle"),"server started battle");
        screenshot(output+"/"+role+"-battle.png");
        long end=System.currentTimeMillis()+90000;
        int lastSeq=-1;
        while(System.currentTimeMillis()<end) {
            Object s=state();
            if(text(s,"phase").equals("finished"))break;
            int uid=n(s,"uid"),seq=n(s,"seq");
            if(n(s,"turn")==uid && seq!=lastSeq) {
                lastSeq=seq;
                if(mode.equals("pvp") && !host) command("pass","&seq="+seq);
                else {
                    Object arena=field(app,"arena");
                    set(arena,"angle",Integer.valueOf(45));
                    set(arena,"power",Integer.valueOf(mode.equals("pvp")?72:(host?78:71)));
                    // Exercise both iOS-style touch and keypad handlers in the shipped Canvas.
                    if(!host && mode.equals("boss")) {
                        Method touch=arena.getClass().getDeclaredMethod("pointerReleased",int.class,int.class);
                        touch.setAccessible(true);
                        touch.invoke(arena,Integer.valueOf(((Canvas)arena).getWidth()-10),Integer.valueOf(((Canvas)arena).getHeight()-60));
                    } else {
                        Method key=arena.getClass().getDeclaredMethod("keyPressed",int.class);
                        key.setAccessible(true); key.invoke(arena,Integer.valueOf(53));
                    }
                }
            }
            Thread.sleep(150);
        }
        if(!text(state(),"phase").equals("finished"))throw new AssertionError("Battle unfinished");
        if(mode.equals("boss") && n(state(),"bossHp")!=0)throw new AssertionError("Boss survived");
        if(mode.equals("boss") && n(state(),"coins")!=100)throw new AssertionError("Coop reward missing");
        if(mode.equals("pvp") && n(state(),"coins")!=(host?50:0))throw new AssertionError("PvP reward wrong");
        screenshot(output+"/"+role+"-finished.png");
        Files.write(new File(output,role+"-result.txt").toPath(),
            ("mode="+mode+" coins="+n(state(),"coins")+" result="+text(state(),"result")).getBytes("UTF-8"));
    }
}
