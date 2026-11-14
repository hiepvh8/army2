package army2.online;

import javax.microedition.lcdui.*;

/** Touch + keypad arena. Visuals use original JAR sprite sheets; rules are server-owned. */
public final class Arena extends Canvas implements CommandListener, Runnable {
    private final OnlineMidlet app;
    private final Command leave = new Command("Roi phong", Command.BACK, 9);
    private final Command ready = new Command("San sang", Command.SCREEN, 1);
    private final Command start = new Command("Bat dau", Command.SCREEN, 2);
    private final Command pass = new Command("Bo luot", Command.SCREEN, 3);
    private final OriginalScene scene = new OriginalScene();
    private Image soldier, enemy, boss;
    private int angle = 45, power = 73, lastTurn, displayedShot, frame, manualFocus = -1;
    public Arena(OnlineMidlet app) {
        this.app = app;
        setFullScreenMode(false);
        addCommand(leave); addCommand(ready); addCommand(start); addCommand(pass); setCommandListener(this);
        try {
            soldier = Image.createImage("/res/player/rocketer.png");
            enemy = Image.createImage("/res/player/cowg.png");
            boss = Image.createImage("/res/player/krazul.png");
        } catch (Exception ignored) {}
        new Thread(this).start();
    }
    protected void paint(Graphics g) {
        State s = app.state;
        int width=getWidth(),height=getHeight();
        g.setFont(Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL));
        if (s == null) {g.setColor(0xe8f3ff);g.fillRect(0,0,width,height);g.setColor(0x183450);g.drawString("Dang ket noi...",8,8,Graphics.TOP|Graphics.LEFT);return;}
        if (lastTurn != s.turn) {
            lastTurn=s.turn;
            manualFocus=-1;
            for (int i=0;i<s.players.length;i++) if (s.players[i].id==s.uid) angle=s.players[i].x>360?135:45;
        }
        if (s.phase.equals("lobby")) {
            g.setColor(0x79caf5);g.fillRect(0,0,width,height);
            g.setColor(0xe4f5ff);g.fillRoundRect(12,45,width-24,Math.min(height-140,210),12,12);
            g.setColor(0x4281af);g.drawRoundRect(12,45,width-24,Math.min(height-140,210),12,12);
            g.setColor(0x173959);g.drawString("PHONG #"+s.room+" - "+(s.mode.equals("boss")?"BOSS":"PVP")+" THU NGHIEM",width/2,54,Graphics.HCENTER|Graphics.TOP);
            g.drawString("Tat ca san sang, chu phong bat dau",width/2,78,Graphics.HCENTER|Graphics.TOP);
            for(int i=0;i<s.players.length;i++) {
                Image avatar=i==0?soldier:enemy;int y=108+i*31;
                if(avatar!=null)g.drawRegion(avatar,0,120,24,24,0,30,y+24,Graphics.HCENTER|Graphics.BOTTOM);
                g.drawString(s.players[i].name+(s.players[i].ready?" [SAN SANG]":" [CHO]"),50,y+5,Graphics.TOP|Graphics.LEFT);
            }
            button(g,0,height-70,width/2,34,"SAN SANG");
            button(g,width/2,height-70,width/2,34,"BAT DAU");
        } else {
            if(displayedShot!=s.shotId){displayedShot=s.shotId;frame=1;manualFocus=-1;}
            int visible=Math.min(frame,s.path.length);
            int focus=360;
            for(int i=0;i<s.players.length;i++) if(s.players[i].id==(s.turn==0?s.uid:s.turn))focus=s.players[i].x;
            if(manualFocus>=0)focus=manualFocus;
            if(visible>0 && visible<s.path.length)focus=s.path[visible-1][0];
            int camera=scene.camera(focus,width),ground=scene.ground(height);
            scene.paintTerrain(g,width,height,camera);
            scene.paintWind(g,width);
            g.setColor(0x173959);g.drawString("0",width/2,19,Graphics.HCENTER|Graphics.TOP);
            g.drawString(s.phase.equals("finished")?"TRAN DA KET THUC":(s.turn==s.uid?"LUOT CUA BAN":"DANG CHO")+" - "+s.seconds+"s",5,5,Graphics.TOP|Graphics.LEFT);
            g.drawString((s.mode.equals("boss")?"BOSS":"PVP")+" THU NGHIEM",5,22,Graphics.TOP|Graphics.LEFT);
            if(!scene.isLoaded()) {g.setColor(0xb02020);g.drawString("KHONG TAI DUOC MAP GOC",5,40,Graphics.TOP|Graphics.LEFT);}
            else {g.setColor(0x355d78);g.drawString("DIA HINH CHI HIEN THI",5,40,Graphics.TOP|Graphics.LEFT);}
            minimap(g,s,width);
            for(int i=0;i<s.players.length;i++) {
                State.Player p=s.players[i];
                if(p.hp<=0) continue;
                int x=scene.screenX(p.x,camera),y=ground;
                Image image=p.id==s.uid?soldier:enemy;
                if(image!=null) {
                    int spriteFrame=(frame/12)%2==0?4:5;
                    g.drawRegion(image,0,spriteFrame*24,24,24,p.x>360?2:0,x,y,Graphics.HCENTER|Graphics.BOTTOM);
                }
                else {g.setColor(p.id==s.uid?0x55ddff:0xff8855);g.fillRect(x-6,y-20,12,20);}
                label(g,p.name,x,y-51);
                g.setColor(0x1a2631);g.fillRect(x-21,y-30,42,6);
                g.setColor(0x47e65d);g.fillRect(x-20,y-29,40*p.hp/Math.max(1,p.maxHp),4);
                label(g,""+p.hp,x,y-68);
            }
            if(s.bossMax>0) {
                int x=scene.screenX(600,camera),y=ground;
                if(boss!=null && s.bossHp>0) {
                    int bossFrame=boss.getWidth()>=896?(frame/18)%8:0;
                    g.drawRegion(boss,bossFrame*112,0,112,112,0,x,y,Graphics.HCENTER|Graphics.BOTTOM);
                }
                label(g,"BOSS "+s.bossHp+"/"+s.bossMax,x,y-136);
            }
            g.setColor(0x295ce2);
            for(int i=1;i<visible;i++)g.drawLine(scene.screenX(s.path[i-1][0],camera),scene.screenY(s.path[i-1][1],height),scene.screenX(s.path[i][0],camera),scene.screenY(s.path[i][1],height));
            if(visible>0){int[] point=s.path[visible-1];g.setColor(0xe84c1e);g.fillArc(scene.screenX(point[0],camera)-3,scene.screenY(point[1],height)-3,7,7,0,360);}
            String log=s.phase.equals("finished")?s.result:s.log;
            g.setColor(0x173959);if(log!=null)g.drawString(log,5,height-100,Graphics.TOP|Graphics.LEFT);
            int w=width/5,y=height-73;
            button(g,0,y,w,30,"Goc -"); button(g,w,y,w,30,"Goc +");
            button(g,w*2,y,w,30,"Luc -"); button(g,w*3,y,w,30,"Luc +"); button(g,w*4,y,width-w*4,30,"BAN");
            button(g,0,y+33,w,27,"< DI");button(g,w,y+33,w,27,"DI >");
            g.setColor(0x173959);g.drawString("Goc "+angle+" | Luc "+power,w*2+4,y+37,Graphics.TOP|Graphics.LEFT);
        }
        if(app.network.length()>0){g.setColor(0xa32020);g.drawString(app.network,4,height-14,Graphics.TOP|Graphics.LEFT);}
    }
    private void label(Graphics g,String text,int x,int y){
        g.setColor(0x102a40);g.drawString(text,x+1,y+1,Graphics.HCENTER|Graphics.TOP);
        g.setColor(0xffffff);g.drawString(text,x,y,Graphics.HCENTER|Graphics.TOP);
    }
    private void minimap(Graphics g,State s,int width){
        int x=width-87,y=4;g.setColor(0x112437);g.fillRoundRect(x,y,82,48,5,5);
        g.setColor(0xbddcf2);g.drawRoundRect(x,y,82,48,5,5);
        g.setColor(0x59d3f4);g.drawLine(x+6,y+35,x+76,y+35);
        for(int i=0;i<s.players.length;i++)if(s.players[i].hp>0){
            int dot=x+6+s.players[i].x*70/720;
            g.setColor(s.players[i].id==s.uid?0x5fff8a:0xff8775);g.fillRect(dot-2,y+30,5,5);
        }
        if(s.bossHp>0){g.setColor(0xffbf55);g.fillRect(x+63,y+24,6,6);}
    }
    private void button(Graphics g,int x,int y,int w,int h,String text){
        g.setColor(0xe8f6ff);g.fillRect(x+1,y,w-2,h);
        g.setColor(0x488eb9);g.drawRect(x+1,y,w-2,h-1);g.setColor(0x173959);
        g.drawString(text,x+w/2,y+5,Graphics.HCENTER|Graphics.TOP);
    }
    protected void pointerReleased(int x,int y){
        State s=app.state;if(s==null)return;
        if(s.phase.equals("lobby")){if(y>=getHeight()-70 && y<getHeight()-36)app.send(x<getWidth()/2?"ready":"start","");return;}
        int miniX=getWidth()-87;
        if(x>=miniX && x<miniX+82 && y>=4 && y<52){
            manualFocus=Math.max(25,Math.min(695,(x-miniX-6)*720/70));repaint();return;
        }
        int row=getHeight()-73,w=getWidth()/5;
        if(y>=row && y<row+30){int i=x/w;if(i==0)angle=Math.max(5,angle-1);else if(i==1)angle=Math.min(175,angle+1);else if(i==2)power=Math.max(10,power-1);else if(i==3)power=Math.min(100,power+1);else fire();}
        else if(y>=row+33 && y<row+60 && x<w*2)move(x<w?-30:30);
        repaint();
    }
    protected void keyPressed(int key){
        int action=0;try{action=getGameAction(key);}catch(IllegalArgumentException ignored){}
        if(key==KEY_NUM4 || action==LEFT)angle=Math.max(5,angle-1);
        else if(key==KEY_NUM6 || action==RIGHT)angle=Math.min(175,angle+1);
        else if(key==KEY_NUM2 || action==UP)power=Math.min(100,power+1);
        else if(key==KEY_NUM8 || action==DOWN)power=Math.max(10,power-1);
        else if(key==KEY_NUM5 || action==FIRE)fire();
        else if(key==KEY_NUM1)move(-30);
        else if(key==KEY_NUM3)move(30);
        repaint();
    }
    protected void keyRepeated(int key){keyPressed(key);}
    public void run(){
        while(!app.isStopped()){
            if(isShown()) {frame+=3;repaint();}
            try{Thread.sleep(40);}catch(InterruptedException ignored){}
        }
    }
    private void fire(){State s=app.state;if(s!=null && s.phase.equals("battle") && s.turn==s.uid)app.send("fire","&seq="+s.seq+"&angle="+angle+"&power="+power);}
    private void move(int dx){State s=app.state;if(s!=null && s.phase.equals("battle") && s.turn==s.uid)app.send("move","&seq="+s.seq+"&dx="+dx);}
    public void commandAction(Command c,Displayable d){if(c==leave)app.send("leave","");else if(c==ready)app.send("ready","");else if(c==start)app.send("start","");else if(c==pass && app.state!=null)app.send("pass","&seq="+app.state.seq);}
}
