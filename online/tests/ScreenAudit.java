import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

/** Hands-on screen and authentication audit using the packaged JAR and real HTTP. */
public final class ScreenAudit extends EmulatorClient {
    static Object menu, screen;
    static Class<?> canvasClass;
    static String output;
    static int h;
    static void snap(String n) throws Exception { screenshot(output+"/"+n+".png"); }
    static Object current() throws Exception { return canvasClass.getField("curScr").get(null); }
    static Object dialog() throws Exception { return canvasClass.getField("currentDialog").get(null); }
    static void closeDialog() throws Exception { canvasClass.getMethod("endDlg").invoke(null); }
    static void clickMenu(int index,int count) throws Exception {
        int top=(h-((Math.min(7,count)-1)*36+22))/2;
        menu.getClass().getMethod("onPointerReleased",int.class,int.class,int.class).invoke(menu,240,top+index*36+5,0);
    }
    static void commandButton(Object scr,String name) throws Exception {
        Object button=scr.getClass().getField(name).get(scr);
        Object action=button.getClass().getField("action").get(button);
        Method perform=action.getClass().getMethod("perform");perform.setAccessible(true);perform.invoke(action);
    }
    static void enter(String fieldName,String value) throws Exception {
        Object f=field(screen,fieldName);
        screen.getClass().getMethod("onPointerReleased",int.class,int.class,int.class).invoke(screen,n(f,"x")+2,n(f,"y")+2,0);
        Display display=Display.getDisplay((javax.microedition.midlet.MIDlet)app);
        await(()->display.getCurrent() instanceof TextBox,"open native text entry " + fieldName);
        TextBox box=(TextBox)display.getCurrent();box.setString(value);
        Field listenerField=Displayable.class.getDeclaredField("listener");listenerField.setAccessible(true);
        CommandListener listener=(CommandListener)listenerField.get(box);
        Command ok=(Command)field(f,"nativeOkCommand");
        listener.commandAction(ok,box);
        await(()->!(display.getCurrent() instanceof TextBox),"return from text entry");
    }
    static void cancelInput(String fieldName) throws Exception {
        Object f=field(screen,fieldName);String before=(String)f.getClass().getMethod("getText").invoke(f);
        screen.getClass().getMethod("onPointerReleased",int.class,int.class,int.class).invoke(screen,n(f,"x")+2,n(f,"y")+2,0);
        Display display=Display.getDisplay((javax.microedition.midlet.MIDlet)app);
        await(()->display.getCurrent() instanceof TextBox,"open text entry for cancel");
        TextBox box=(TextBox)display.getCurrent();box.setString("discarded");
        Field lf=Displayable.class.getDeclaredField("listener");lf.setAccessible(true);
        ((CommandListener)lf.get(box)).commandAction((Command)field(f,"nativeCancelCommand"),box);
        if(!before.equals(f.getClass().getMethod("getText").invoke(f)))throw new AssertionError("Cancel changed text");
    }
    static void expectError(String expected,String screenshotName) throws Exception {
        await(()->dialog()!=null && text(app,"network").indexOf(expected)>=0,"error "+expected);
        snap(screenshotName);closeDialog();
    }
    public static void main(String[] args) {
        try { audit(args);System.out.println("SCREEN_AUDIT_PASS");System.exit(0); }
        catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
    static void audit(String[] args) throws Exception {
        output=args[2];
        Headless headless=new Headless();Common emulator=(Common)field(headless,"emulator");
        ArrayList<String> params=new ArrayList<String>();params.add("--rms");params.add("memory");params.add(args[0]);
        DeviceEntry device=new DeviceEntry("Default device",null,"org/microemu/device/default/device.xml",true,false);
        emulator.initParams(params,device,J2SEDevice.class);
        J2SEDeviceDisplay dd=(J2SEDeviceDisplay)DeviceFactory.getDevice().getDeviceDisplay();
        dd.setDisplayRectangle(new Rectangle(0,0,480,800));dd.setDisplayPaintable(new Rectangle(0,0,480,800));
        emulator.initMIDlet(true);app=MIDletBridge.getCurrentMIDlet();
        send=app.getClass().getMethod("send",String.class,String.class);set(app,"base",args[1]);set(app,"token","");
        canvasClass=app.getClass().getClassLoader().loadClass("coreLG.CCanvas");menu=field(app,"menu");
        await(()->current()==menu,"main menu");h=((Integer)canvasClass.getField("hieght").get(null)).intValue();snap("01-main");

        clickMenu(2,5);screen=field(app,"login");snap("02-server");
        enter("username","http://example.com");commandButton(screen,"center");
        await(()->dialog()!=null,"HTTP remote rejected");snap("03-server-invalid");closeDialog();
        enter("username","http://127.0.0.1:8080@evil.example");commandButton(screen,"center");
        if(current()!=screen || dialog()==null)throw new AssertionError("Remote host disguised as loopback accepted");
        closeDialog();
        enter("username",args[1]);commandButton(screen,"center");await(()->current()==menu,"save server returns home");

        clickMenu(0,5);screen=field(app,"login");snap("04-login");cancelInput("username");commandButton(screen,"center");
        await(()->dialog()!=null,"empty credentials");snap("05-empty");closeDialog();commandButton(screen,"left");
        await(()->current()==menu,"back from login");

        clickMenu(1,5);screen=field(app,"login");snap("06-register");
        enter("username","a");enter("password","audit-test-password");commandButton(screen,"center");
        expectError("Ten 3-16","06a-invalid-name");
        enter("username","AuditUser");enter("password","short");commandButton(screen,"center");
        expectError("Mat khau can","06b-short-password");
        enter("password","audit-test-password");snap("07-register-filled");
        commandButton(screen,"center");
        await(()->state()!=null && current()==menu && ((Boolean)field(menu,"lobby")),"register to lobby");snap("08-lobby");
        clickMenu(2,5);expectError("Chua du dieu kien","09-event-not-ready");
        clickMenu(3,5);await(()->current()==menu && !((Boolean)field(menu,"lobby")) && !((Boolean)app.getClass().getMethod("hasSession").invoke(app)),"logout returns home");

        clickMenu(0,5);screen=field(app,"login");enter("username","AuditUser");enter("password","wrong-password");
        commandButton(screen,"center");expectError("Sai tai khoan","10-wrong-password");
        enter("password","audit-test-password");commandButton(screen,"center");
        await(()->current()==menu && ((Boolean)field(menu,"lobby")),"login success");snap("11-login-success");
        clickMenu(3,5);await(()->current()==menu && !((Boolean)field(menu,"lobby")) && !((Boolean)app.getClass().getMethod("hasSession").invoke(app)),"second logout");
        clickMenu(1,5);screen=field(app,"login");enter("username","AuditUser");enter("password","audit-test-password");
        commandButton(screen,"center");expectError("Ten da duoc su dung","12-duplicate-account");
        commandButton(screen,"left");await(()->current()==menu,"back from duplicate registration");
        clickMenu(3,5);await(()->current()!=menu,"offline menu");snap("13-offline-menu");
        app.getClass().getMethod("home").invoke(app);await(()->current()==menu,"return to test menu");
        clickMenu(4,5);
        if(!((Boolean)app.getClass().getMethod("isStopped").invoke(app)))throw new AssertionError("Exit did not stop app");
    }
}
