import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.ArrayList;
import javax.microedition.lcdui.*;
import org.microemu.app.Headless;
import org.microemu.app.Common;
import org.microemu.app.util.DeviceEntry;
import org.microemu.device.j2se.J2SEDevice;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.device.DeviceFactory;
import org.microemu.device.impl.Rectangle;
import org.microemu.MIDletBridge;

/** Runs the shipped JAR with real RMS and HTTP across independent JVM restarts. */
public final class ProfileEmulatorClient extends EmulatorClient {
    static Class<?> canvas, game, safe, profile, terrain;
    static Object menu;
    static int height;
    static String output;
    static Object cur() throws Exception {return canvas.getField("curScr").get(null);}
    static Object dlg() throws Exception {return canvas.getField("currentDialog").get(null);}
    static void close() throws Exception {canvas.getMethod("endDlg").invoke(null);}
    static String info() throws Exception {
        Object d=dlg();if(d==null)return "";
        String[] lines=(String[])field(d,"info");StringBuilder text=new StringBuilder();
        if(lines!=null)for(String line:lines)text.append(line).append(' ');
        return text.toString();
    }
    static void confirm() throws Exception {
        Object button=dlg().getClass().getField("left").get(dlg());
        Object action=button.getClass().getField("action").get(button);
        Method perform=action.getClass().getMethod("perform");perform.setAccessible(true);perform.invoke(action);
    }
    static void command(Object screen,String name) throws Exception {
        Object button=screen.getClass().getField(name).get(screen);
        Object action=button.getClass().getField("action").get(button);
        Method perform=action.getClass().getMethod("perform");perform.setAccessible(true);perform.invoke(action);
    }
    static void bossBattle(ClassLoader loader) throws Exception {
        openProfile();tap(cur(),0,7);
        await(()->cur()!=menu && cur().getClass().getName().equals("screen.MenuScr"),"original game menu");
        Object gameMenu=cur();Class<?> mc=gameMenu.getClass();
        command(gameMenu,"center");await(()->mc.getField("curMenuLevel").getInt(null)==1,"original start submenu");
        command(gameMenu,"center");await(()->mc.getField("curMenuLevel").getInt(null)==2,"original battle choices");
        command(gameMenu,"center");await(()->cur().getClass().getName().equals("screen.RoomListScr2"),"original boss list");
        command(cur(),"center");await(()->dlg()!=null && dlg().getClass().getName().equals("model.InputDlg"),"boss difficulty");
        Object input=dlg().getClass().getField("tfInput").get(dlg());
        Display display=Display.getDisplay((javax.microedition.midlet.MIDlet)app);
        if(display.getCurrent() instanceof TextBox) {
            Method closeInput=input.getClass().getDeclaredMethod("closeNativeTextBox");closeInput.setAccessible(true);closeInput.invoke(input);
        }
        input.getClass().getMethod("setText",String.class).invoke(input,"1");command(dlg(),"center");
        await(()->cur().getClass().getName().equals("screen.PrepareScr"),"original preparation screen");
        Object start=field(cur(),"cmdStart"), action=start.getClass().getField("action").get(start);
        Method perform=action.getClass().getMethod("perform");perform.setAccessible(true);perform.invoke(action);
        await(()->cur().getClass().getName().equals("screen.GameScr"),"original boss battle");
        screenshot(output+"/05-original-boss.png");
        Class<?> pm=loader.loadClass("player.PM");
        Object player=pm.getMethod("getMyPlayer").invoke(null);
        if(player==null)throw new AssertionError("Original combat player missing");
        player.getClass().getField("angle").setInt(player,45);
        player.getClass().getField("force").setByte(player,(byte)30);
        player.getClass().getMethod("shoot").invoke(player);
        if(player.getClass().getField("lastForcePoint").getInt(player)!=30)
            throw new AssertionError("Original battle did not execute the shot");
        screenshot(output+"/06-original-shot.png");
    }
    static void tap(Object screen,int index,int count) throws Exception {
        int top=(height-((count-1)*36+22))/2;
        screen.getClass().getMethod("onPointerReleased",int.class,int.class,int.class)
            .invoke(screen,240,top+index*36+5,0);
    }
    static void openProfile() throws Exception {
        await(()->cur()==menu && ((Boolean)field(menu,"lobby")),"online lobby");
        tap(menu,4,5);
        await(()->cur().getClass().getName().equals("army2.online.ProfileMenu"),"original profile menu");
    }
    static void action(int index,String expected) throws Exception {
        Object screen=cur();tap(screen,index,7);await(()->dlg()!=null,"confirmation");confirm();
        await(()->dlg()!=null && info().indexOf(expected)>=0,"result " + expected + "; saw " + info());
    }
    static String player() throws Exception {
        Object info=terrain.getField("myInfo").get(null);
        return (String)info.getClass().getField("name").get(info);
    }
    static void newCharacter(String name) throws Exception {
        game.getMethod("startNewOfflineGame",String.class).invoke(null,name);
        if(!((Boolean)safe.getMethod("newGameSucceeded").invoke(null)))throw new AssertionError("Creating original save failed: "+safe.getField("error").get(null));
        if(!name.equals(player()))throw new AssertionError("Original player not created");
    }
    public static void main(String[] args) {
        try {runProfile(args);System.out.println("PROFILE_CLIENT_PASS " + args[3]);System.exit(0);}
        catch(Throwable e){e.printStackTrace();System.exit(1);}
    }
    static void runProfile(String[] args) throws Exception {
        output=args[2];String role=args[3];
        Headless headless=new Headless();Common emulator=(Common)field(headless,"emulator");
        ArrayList<String> params=new ArrayList<String>();params.add("--rms");params.add("file");params.add(args[0]);
        emulator.initParams(params,new DeviceEntry("Default device",null,"org/microemu/device/default/device.xml",true,false),J2SEDevice.class);
        J2SEDeviceDisplay dd=(J2SEDeviceDisplay)DeviceFactory.getDevice().getDeviceDisplay();
        dd.setDisplayRectangle(new Rectangle(0,0,480,800));dd.setDisplayPaintable(new Rectangle(0,0,480,800));
        emulator.initMIDlet(true);app=MIDletBridge.getCurrentMIDlet();set(app,"base",args[1]);
        ClassLoader loader=app.getClass().getClassLoader();
        canvas=loader.loadClass("coreLG.CCanvas");game=loader.loadClass("com.teamobi.mobiarmy2.GameMidlet");
        safe=loader.loadClass("com.teamobi.mobiarmy2.OfflineSaveSafe");
        profile=loader.loadClass("army2.online.Profile");terrain=loader.loadClass("coreLG.TerrainMidlet");
        menu=field(app,"menu");await(()->cur()==menu,"home");height=((Integer)canvas.getField("hieght").get(null)).intValue();
        if(role.equals("host") || role.equals("guest")) {
            app.getClass().getMethod("authenticate",String.class,String.class,boolean.class)
                .invoke(app,"SharedUser","emulator-test-password",role.equals("host"));
        } else {
            if(!((Boolean)app.getClass().getMethod("hasSession").invoke(app)))throw new AssertionError("Session was not retained across restart");
            app.getClass().getMethod("connect").invoke(app);
        }
        await(()->state()!=null && cur()==menu && ((Boolean)field(menu,"lobby")),"login");
        int uid=n(state(),"uid");
        if(role.equals("host")) {
            newCharacter("HostHero");
            openProfile();screenshot(output+"/01-profile-menu.png");
            action(1,"Đã lưu");screenshot(output+"/02-upload.png");close();
            int revision=((Integer)profile.getMethod("revision",String.class,int.class).invoke(null,args[1],uid)).intValue();
            if(revision!=1)throw new AssertionError("Upload revision not stored");
        } else if(role.equals("guest")) {
            newCharacter("GuestHero");
            openProfile();action(2,"Đã tải");screenshot(output+"/03-download.png");close();
            if(!"HostHero".equals(player()))throw new AssertionError("Original host character was not imported");
            if(!((Boolean)profile.getMethod("hasBackup").invoke(null)))throw new AssertionError("Guest character was not backed up");
            int revision=((Integer)profile.getMethod("revision",String.class,int.class).invoke(null,args[1],uid)).intValue();
            if(revision!=1)throw new AssertionError("Download revision not stored");
            // Imported game data is deliberately frozen until a real restart.
            Class<?> guard=loader.loadClass("com.teamobi.mobiarmy2.CloudSaveGuard2342");
            if(((Boolean)guard.getMethod("maySave").invoke(null)))throw new AssertionError("Import did not require restart");
        } else if(role.equals("restore")) {
            if(!((Boolean)game.getMethod("continueOfflineGame").invoke(null)) || !"HostHero".equals(player()))
                throw new AssertionError("Downloaded profile was not retained after restart");
            openProfile();action(3,"Đã phục hồi");screenshot(output+"/04-restore.png");close();
            if(!"GuestHero".equals(player()))throw new AssertionError("Original guest backup was not restored");
            int revision=((Integer)profile.getMethod("revision",String.class,int.class).invoke(null,args[1],uid)).intValue();
            if(revision!=0)throw new AssertionError("Restored local profile must not overwrite remote automatically");
        } else if(role.equals("verify")) {
            if(!((Boolean)game.getMethod("continueOfflineGame").invoke(null)) || !"GuestHero".equals(player()))
                throw new AssertionError("Restored profile was not retained after restart");
            if(!((Boolean)profile.getMethod("hasBackup").invoke(null)))throw new AssertionError("Backup disappeared");
            bossBattle(loader);
        } else if(role.equals("undo")) {
            if(!((Boolean)game.getMethod("continueOfflineGame").invoke(null)) || !"GuestHero".equals(player()))
                throw new AssertionError("Expected restored local character before undo");
            openProfile();action(3,"Đã phục hồi");close();
            if(!"HostHero".equals(player()))throw new AssertionError("Restoring the most recent backup did not undo restoration");
        } else if(role.equals("verifyundo")) {
            if(!((Boolean)game.getMethod("continueOfflineGame").invoke(null)) || !"HostHero".equals(player()))
                throw new AssertionError("Undo was not retained after restart");
        } else if(role.equals("conflict")) {
            newCharacter("ConflictHero");
            openProfile();action(1,"Dữ liệu máy chủ");close();
            if(((Integer)profile.getMethod("revision",String.class,int.class).invoke(null,args[1],uid)).intValue()!=0)
                throw new AssertionError("Rejected write changed local revision");
            action(5,"Đã lưu");close();
            if(((Integer)profile.getMethod("revision",String.class,int.class).invoke(null,args[1],uid)).intValue()!=2)
                throw new AssertionError("Conflict resolution did not upload version 2");
        } else if(role.equals("remoteverify")) {
            if(!((Boolean)game.getMethod("continueOfflineGame").invoke(null)) || !"HostHero".equals(player()))
                throw new AssertionError("Host local character unexpectedly changed");
            openProfile();action(2,"Đã tải");close();
            if(!"ConflictHero".equals(player()))throw new AssertionError("Resolved profile was not transferred");
        } else throw new AssertionError("Unknown role");
        Files.write(new File(output,role+"-result.txt").toPath(),("role="+role+" player="+player()).getBytes("UTF-8"));
    }
}
