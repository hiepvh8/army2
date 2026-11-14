package army2.online;

import CLib.mGraphics;
import coreLG.CCanvas;
import effect.Cloud;
import model.Font;
import model.IAction;
import network.Command;
import screen.CScreen;
import screen.MainMenuScr;

/** Original game menu layout, fonts, background and input loop. */
public final class GameMenu extends MainMenuScr {
    private final OnlineMidlet app;
    private boolean lobby;
    private int selected;
    private static final String[] HOME = {"ĐĂNG NHẬP", "ĐĂNG KÝ", "MÁY CHỦ", "CHƠI OFFLINE", "THOÁT"};
    public GameMenu(OnlineMidlet app) {
        super(); this.app = app;
        center = new Command("Chọn", new IAction() { public void perform() { choose(); } });
        left = new Command("Quay lại", new IAction() { public void perform() { if (lobby) GameMenu.this.app.home(); } });
    }
    public boolean isLobby() { return lobby; }
    public void showHome() { lobby = false; selected = 0; super.show(); }
    public void showLobby() { lobby = true; selected = 0; super.show(); }
    private int count() { State s=app.state; return lobby ? 5 + (s == null ? 0 : s.rooms.length) : HOME.length; }
    private String label(int n) {
        if (!lobby) return HOME[n];
        State s = app.state;
        if (n==0) return "PVP THỬ NGHIỆM";
        if (n==1) return "BOSS THỬ NGHIỆM";
        if (n==2) return s==null ? "SỰ KIỆN" : "SỰ KIỆN " + s.progress + "/" + s.goal + (s.claimed ? " (ĐÃ NHẬN)" : "");
        if (n==3) return "ĐĂNG XUẤT";
        if (n==4) return "NHÂN VẬT GỐC";
        State.Room r = s.rooms[n-5];
        return "#" + r.id + " " + r.name + " " + r.count + "/" + r.capacity;
    }
    private void choose() {
        if (!lobby) {
            if (selected==0) { if (app.hasSession()) app.connect(); else app.openLogin(false); }
            else if (selected==1) app.openLogin(true);
            else if (selected==2) app.openSettings();
            else if (selected==3) app.offline();
            else app.exitGame();
        } else {
            State s=app.state;
            if(selected==0) app.send("create","&mode=pvp");
            else if(selected==1) app.send("create","&mode=boss");
            else if(selected==2) app.send("claim","");
            else if(selected==3) app.send("logout","");
            else if(selected==4) app.showOriginalProfile();
            else if(s!=null && selected-5<s.rooms.length) app.send("join","&room="+s.rooms[selected-5].id);
        }
    }
    public void update() {
        if (selected >= count()) selected = Math.max(0, count()-1);
        if (CCanvas.currentDialog == null && !CCanvas.menu.showMenu) {
            if (CCanvas.keyPressed[2]) { selected = selected>0 ? selected-1 : count()-1; CScreen.clearKey(); }
            if (CCanvas.keyPressed[8]) { selected = selected<count()-1 ? selected+1 : 0; CScreen.clearKey(); }
        }
        super.update();
    }
    public void paint(mGraphics g) {
        g.setColor(7852799); g.fillRect(0,0,CCanvas.width,CCanvas.hieght,false);
        Cloud.paintCloud(g);
        int count=count(); int visible=Math.min(7,count);
        int first=Math.max(0,Math.min(selected-visible+1,count-visible));
        int top=(CCanvas.hieght - ((visible-1)*36+22))/2;
        if(lobby && app.state!=null) Font.normalFont.drawString(g,app.state.name+"  |  "+app.state.coins+" xu",CCanvas.width/2,Math.max(4,top-30),mGraphics.HCENTER|mGraphics.TOP);
        for(int i=0;i<visible;i++) {
            int index=first+i, y=top+i*36;
            if(index==selected) {g.setColor(3374591);g.fillRect(CCanvas.width/2-100,y,200,22,false);}
            Font.bigFont.drawString(g,label(index),CCanvas.width/2,y,mGraphics.HCENTER|mGraphics.TOP);
        }
        paintCommand(g);
        Font.normalFont.drawString(g,lobby?"Quay lại":"",5,CCanvas.hieght-cmdH/2-Font.normalFont.getHeight()/2,0);
        Font.normalFont.drawString(g,"Chọn",CCanvas.width/2,CCanvas.hieght-cmdH/2-Font.normalFont.getHeight()/2,2);
    }
    public void onPointerReleased(int x,int y,int pointer) {
        if(CCanvas.currentDialog!=null || CCanvas.menu.showMenu) return;
        int count=count(), visible=Math.min(7,count);
        int first=Math.max(0,Math.min(selected-visible+1,count-visible));
        int top=(CCanvas.hieght-((visible-1)*36+22))/2;
        if(y>=top && y<top+visible*36) {selected=first+(y-top)/36; if(selected<count) choose(); return;}
        if(y>=CCanvas.hieght-cmdH) {
            if(x<CCanvas.width/3 && lobby) app.home();
            else if(x>=CCanvas.width/3 && x<2*CCanvas.width/3) choose();
        }
    }
}
