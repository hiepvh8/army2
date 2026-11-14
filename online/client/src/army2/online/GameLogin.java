package army2.online;

import CLib.mGraphics;
import coreLG.CCanvas;
import effect.Cloud;
import model.Font;
import model.IAction;
import model.TField;
import network.Command;
import screen.CScreen;

/** Game-native fonts, panels, dialogs and TField input. */
public final class GameLogin extends CScreen {
    public static final int LOGIN=0, REGISTER=1, SETTINGS=2;
    private final OnlineMidlet app;
    private final int mode;
    public final TField username, password;
    private int focus;
    private int top, x, width;
    public GameLogin(OnlineMidlet app,int mode) {
        this.app=app;this.mode=mode;
        username=new TField();password=new TField();
        username.title=mode==SETTINGS?"Địa chỉ máy chủ":"Tài khoản";
        username.setMaxTextLenght(mode==SETTINGS?220:16);
        username.setIputType(mode==SETTINGS?0:3);
        if(mode==SETTINGS)username.setText(app.serverAddress());
        password.title="Mật khẩu";password.setMaxTextLenght(128);password.setIputType(2);
        username.setisFocus(true);password.setisFocus(false);
        center=new Command(mode==SETTINGS?"Lưu":(mode==REGISTER?"Đăng ký":"Đăng nhập"),new IAction(){public void perform(){submit();}});
        left=new Command("Quay lại",new IAction(){public void perform(){GameLogin.this.app.home();}});
    }
    private void layout() {
        int tabs=CCanvas.width>=200?4:3;
        int panelWidth=tabs*32+56;
        top=CCanvas.hieght/2-75;x=(CCanvas.width-panelWidth)/2+75;
        width=panelWidth-85;
        username.x=x;username.y=top+43;username.width=width;username.height=22;
        password.x=x;password.y=top+78;password.width=width;password.height=22;
    }
    public void show(){layout();super.show();}
    public void submit() {
        if(mode==SETTINGS) app.saveServer(username.getText());
        else { String secret=password.getText();password.setText("");app.authenticate(username.getText().trim(),secret,mode==REGISTER); }
    }
    public void update(){
        if(CCanvas.currentDialog==null && !CCanvas.menu.showMenu && mode!=SETTINGS && (CCanvas.keyPressed[2]||CCanvas.keyPressed[8])) {
            focus=1-focus;username.setisFocus(focus==0);password.setisFocus(focus==1);CScreen.clearKey();
        }
        username.update();if(mode!=SETTINGS)password.update();super.update();
    }
    public void keyPressed(int key){if(CCanvas.currentDialog==null){if(focus==0)username.keyPressed(key);else password.keyPressed(key);}super.keyPressed(key);}
    public void paint(mGraphics g){
        layout();g.setColor(7852799);g.fillRect(0,0,CCanvas.width,CCanvas.hieght,false);Cloud.paintCloud(g);
        int tabs=CCanvas.width>=200?4:3;
        CScreen.paintBorderRect(g,top,tabs,mode==SETTINGS?110:150,"");
        Font.bigFont.drawString(g,mode==SETTINGS?"MÁY CHỦ":(mode==REGISTER?"ĐĂNG KÝ":"ĐĂNG NHẬP"),CCanvas.width/2,top+3,mGraphics.HCENTER|mGraphics.TOP);
        Font.borderFont.drawString(g,mode==SETTINGS?"URL:":"Tài khoản:",x-60,username.y+4,0,false);username.paint(g);
        if(mode!=SETTINGS){Font.borderFont.drawString(g,"Mật khẩu:",x-60,password.y+4,0,false);password.paint(g);}
        super.paint(g);
    }
    public void onPointerReleased(int px,int py,int pointer){
        if(CCanvas.currentDialog!=null || CCanvas.menu.showMenu){super.onPointerReleased(px,py,pointer);return;}
        if(px>=username.x && px<username.x+username.width && py>=username.y && py<username.y+username.height){focus=0;username.setisFocus(true);password.setisFocus(false);username.doChangeToTextBox();return;}
        if(mode!=SETTINGS && px>=password.x && px<password.x+password.width && py>=password.y && py<password.y+password.height){focus=1;username.setisFocus(false);password.setisFocus(true);password.doChangeToTextBox();return;}
        super.onPointerReleased(px,py,pointer);
    }
}
