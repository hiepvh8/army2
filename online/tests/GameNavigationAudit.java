import java.io.File;
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

/** Explore one original-menu path per disposable emulator process. */
public final class GameNavigationAudit extends EmulatorClient {
    static Class<?> canvas, menuClass;static Object menu;static String out;
    static Object get(Object target,String name) throws Exception {
        Class<?> c=target instanceof Class ? (Class<?>)target : target.getClass();
        while(c!=null){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class?null:target);}catch(NoSuchFieldException e){c=c.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    static Object cur() throws Exception{return canvas.getField("curScr").get(null);}
    static Object dialog() throws Exception{return canvas.getField("currentDialog").get(null);}
    static String clean(Object o){return String.valueOf(o).replace('\t',' ').replace('\n',' ').replace('\r',' ');}
    static String describe(Object o){
        if(o==null)return "";
        try {Object lines=get(o,"info");if(lines instanceof String[])return Arrays.toString((String[])lines);}catch(Exception ignored){}
        return o.getClass().getName();
    }
    static String commands(Object screen){
        StringBuilder s=new StringBuilder();
        for(String n:new String[]{"left","center","right"})try{Object c=get(screen,n);if(c!=null)s.append(n).append('=').append(get(c,"caption")).append(';');}catch(Exception ignored){}
        return s.toString();
    }
    static String[] choices() throws Exception {
        int level=menuClass.getField("curMenuLevel").getInt(null);
        int main=menuClass.getField("curMenuSelect").getInt(null);
        if(level==0)return (String[])menuClass.getField("menuString").get(null);
        String[][] subs=(String[][])menuClass.getField("subMenuString").get(null);
        if(level==1)return subs[main];
        int sub=((Integer)get(menuClass,"curSubMenuSelect")).intValue();
        if(sub==1)return subs[6];
        if(sub==2)return subs[9];
        if(sub==3)return subs[menuClass.getField("MENU_LAPDOI").getByte(null)];
        if(sub==4)return subs[menuClass.getField("MENU_CUAHANG").getByte(null)];
        return new String[0];
    }
    static boolean popup() throws Exception {Object pop=canvas.getField("menu").get(null);return pop!=null && ((Boolean)get(pop,"showMenu")).booleanValue();}
    static void state(String tag) throws Exception {
        Object s=cur(),d=dialog();
        System.out.println("NAV_STATE\t"+tag+"\t"+clean(s==null?null:s.getClass().getName())+"\t"+
            clean(d==null?"":d.getClass().getName())+"\t"+clean(describe(d))+"\t"+popup()+"\t"+clean(s==null?"":commands(s)));
        Displayable display=Display.getDisplay((javax.microedition.midlet.MIDlet)app).getCurrent();
        System.out.println("NAV_DISPLAY\t"+tag+"\t"+clean(display==null?null:display.getClass().getName())+"\t"+clean(display==null?"":display.getTitle()));
        if(d!=null)System.out.println("NAV_DIALOG_COMMANDS\t"+tag+"\t"+clean(commands(d)));
        if(popup()){
            Object pop=canvas.getField("menu").get(null);Vector items=(Vector)get(pop,"menuItems");
            for(int i=0;i<items.size();i++){
                Object command=items.elementAt(i);
                System.out.println("NAV_POPUP\t"+tag+"\t"+i+"\t"+clean(get(command,"caption")));
            }
        }
    }
    static void shoot(String stage) throws Exception {screenshot(out+"/"+stage+".png");}
    static void invokeCommand(Object scr,String name) throws Exception {
        Object c=get(scr,name);if(c==null)throw new IllegalStateException("button not present: "+name);
        Object action=get(c,"action");Method m=action.getClass().getMethod("perform");m.setAccessible(true);m.invoke(action);
    }
    public static void main(String[] args){
        try{run(args);System.out.println("NAV_DONE");System.exit(0);}
        catch(Throwable t){while(t instanceof InvocationTargetException && t.getCause()!=null)t=t.getCause();
            System.out.println("NAV_ERROR\t"+clean(t.getClass().getName())+"\t"+clean(t.getMessage()));
            t.printStackTrace();try{state("error");shoot("error");}catch(Throwable ignored){}System.exit(2);}
    }
    static void run(String[] args) throws Exception {
        out=args[1];String path=args[2];String action=args.length>3?args[3]:"";
        Headless h=new Headless();Common e=(Common)get(h,"emulator");ArrayList<String> p=new ArrayList<String>();
        p.add("--rms");p.add("memory");p.add(args[0]);
        e.initParams(p,new DeviceEntry("Default device",null,"org/microemu/device/default/device.xml",true,false),J2SEDevice.class);
        J2SEDeviceDisplay d=(J2SEDeviceDisplay)DeviceFactory.getDevice().getDeviceDisplay();
        d.setDisplayRectangle(new Rectangle(0,0,480,800));d.setDisplayPaintable(new Rectangle(0,0,480,800));
        e.initMIDlet(true);app=MIDletBridge.getCurrentMIDlet();ClassLoader cl=app.getClass().getClassLoader();
        canvas=cl.loadClass("coreLG.CCanvas");menuClass=cl.loadClass("screen.MenuScr");
        await(()->cur()!=null,"launcher");
        Class<?> game=cl.loadClass("com.teamobi.mobiarmy2.GameMidlet");
        game.getMethod("startNewOfflineGame",String.class).invoke(null,"AuditHero");
        Class<?> save=cl.loadClass("com.teamobi.mobiarmy2.OfflineSaveSafe");
        if(!((Boolean)save.getMethod("newGameSucceeded").invoke(null)))throw new IllegalStateException("New profile failed: "+save.getField("error").get(null));
        game.getMethod("enterOfflineMenu").invoke(null);await(()->menuClass.isInstance(cur()),"original menu");
        menu=cur();Thread.sleep(400);
        System.out.println("NAV_ROOT\t"+clean(Arrays.toString((String[])menuClass.getField("menuString").get(null))));
        String[][] subs=(String[][])menuClass.getField("subMenuString").get(null);
        for(int i=0;i<subs.length;i++)if(subs[i]!=null)System.out.println("NAV_SUB\t"+i+"\t"+clean(Arrays.toString(subs[i])));
        state("root");
        if(path.length()!=0)for(String segment:path.split("-")){
            if(cur()!=menu || dialog()!=null || popup()) {System.out.println("NAV_BLOCKED\t"+segment);break;}
            int i=Integer.parseInt(segment);String[] labels=choices();
            if(i<0 || i>=labels.length)throw new IllegalArgumentException("Invalid menu index "+i+" in "+Arrays.toString(labels));
            int level=menuClass.getField("curMenuLevel").getInt(null);
            menuClass.getField("select").setInt(menu,i);
            System.out.println("NAV_CHOOSE\t"+level+"\t"+i+"\t"+clean(labels[i]));
            menuClass.getMethod("getMenuLevel").invoke(menu);Thread.sleep(500);state("after-"+segment);
        }
        shoot("screen");
        int actionIndex=0;
        if(action.length()!=0)for(String part:action.split(",")){
            if(part.startsWith("popup:")){
                if(!popup())throw new IllegalStateException("No popup menu");
                Object pop=canvas.getField("menu").get(null);Vector items=(Vector)get(pop,"menuItems");
                int index=Integer.parseInt(part.substring(6));Object command=items.elementAt(index);
                System.out.println("NAV_ACTION_TARGET\tpopup\t"+index+"\t"+clean(get(command,"caption")));
                pop.getClass().getField("showMenu").setBoolean(pop,false);
                Object handler=get(command,"action");Method method=handler.getClass().getMethod("perform");method.setAccessible(true);method.invoke(handler);
            }
            else if(dialog()!=null){System.out.println("NAV_ACTION_TARGET\tdialog\t"+part);invokeCommand(dialog(),part);}
            else {System.out.println("NAV_ACTION_TARGET\tscreen\t"+part);invokeCommand(cur(),part);}
            Thread.sleep(600);state("after-action-"+(++actionIndex));
        }
        if(action.length()!=0)shoot("after-action");
    }
}
