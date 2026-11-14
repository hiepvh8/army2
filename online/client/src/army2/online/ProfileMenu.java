package army2.online;

import CLib.mGraphics;
import coreLG.CCanvas;
import effect.Cloud;
import model.Font;
import model.IAction;
import network.Command;
import screen.CScreen;

/** Manual, explicit save transfer for the original game character. */
public final class ProfileMenu extends CScreen {
    private final OnlineMidlet app;
    private int selected;
    private static final String[] LABELS={"VÀO GAME GỐC", "LƯU LÊN SERVER", "TẢI TỪ SERVER",
        "PHỤC HỒI", "XÓA DỰ PHÒNG", "GHI ĐÈ SERVER", "QUAY LẠI"};
    public ProfileMenu(OnlineMidlet app) {
        this.app=app;
        center=new Command("Chọn",new IAction(){public void perform(){choose();}});
        left=new Command("Quay lại",new IAction(){public void perform(){ProfileMenu.this.app.showLobby();}});
    }
    public void show(){selected=0;super.show();}
    private void confirm(String message,final int action) {
        CCanvas.startYesNoDlg(message,new IAction(){public void perform(){
            CCanvas.endDlg();
            if(action==1)app.uploadOriginal();
            else if(action==2)app.downloadOriginal();
            else if(action==3)app.restoreOriginalBackup();
            else if(action==4)app.deleteOriginalBackup();
            else if(action==5)app.replaceServerOriginal();
        }},new IAction(){public void perform(){CCanvas.endDlg();}});
    }
    private void choose() {
        if(selected==0) app.enterOriginal();
        else if(selected==1) confirm("Thay bản lưu trên tài khoản " + app.state.name + " bằng nhân vật trên máy?",1);
        else if(selected==2) confirm("Tải nhân vật từ tài khoản " + app.state.name + "? Nhân vật hiện tại sẽ được dự phòng trên máy.",2);
        else if(selected==3) confirm("Phục hồi dự phòng mới nhất? Nhân vật hiện tại sẽ được dự phòng trước.",3);
        else if(selected==4) confirm("Xóa bản dự phòng mới nhất? Sau đó không thể phục hồi bản này.",4);
        else if(selected==5) confirm("Thay bản trên máy chủ bằng nhân vật trên máy? Bản máy chủ sẽ được dự phòng trước.",5);
        else app.showLobby();
    }
    public void update() {
        if(CCanvas.currentDialog==null && !CCanvas.menu.showMenu) {
            if(CCanvas.keyPressed[2]) {selected=selected>0?selected-1:LABELS.length-1;CScreen.clearKey();}
            if(CCanvas.keyPressed[8]) {selected=selected<LABELS.length-1?selected+1:0;CScreen.clearKey();}
        }
        super.update();
    }
    public void paint(mGraphics g) {
        g.setColor(7852799);g.fillRect(0,0,CCanvas.width,CCanvas.hieght,false);Cloud.paintCloud(g);
        int top=(CCanvas.hieght-((LABELS.length-1)*36+22))/2;
        Font.normalFont.drawString(g,"Nhân vật gốc",CCanvas.width/2,Math.max(4,top-32),mGraphics.HCENTER|mGraphics.TOP);
        for(int i=0;i<LABELS.length;i++) {
            int y=top+i*36;
            if(i==selected) {g.setColor(3374591);g.fillRect(10,y,CCanvas.width-20,22,false);}
            Font.bigFont.drawString(g,LABELS[i],CCanvas.width/2,y+2,mGraphics.HCENTER|mGraphics.TOP);
        }
        paintCommand(g);
        Font.normalFont.drawString(g,"Quay lại",5,CCanvas.hieght-cmdH/2-Font.normalFont.getHeight()/2,0);
        Font.normalFont.drawString(g,"Chọn",CCanvas.width/2,CCanvas.hieght-cmdH/2-Font.normalFont.getHeight()/2,2);
    }
    public void onPointerReleased(int x,int y,int pointer) {
        if(CCanvas.currentDialog!=null || CCanvas.menu.showMenu)return;
        int top=(CCanvas.hieght-((LABELS.length-1)*36+22))/2;
        if(y>=top && y<top+LABELS.length*36) {selected=(y-top)/36;choose();return;}
        if(y>=CCanvas.hieght-cmdH) {
            if(x<CCanvas.width/3)app.showLobby();
            else if(x<2*CCanvas.width/3)choose();
        }
    }
}
