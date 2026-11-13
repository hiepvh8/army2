package army2.online;

import javax.microedition.lcdui.*;

/** Touch + keypad arena. Visuals use original JAR sprite sheets; rules are server-owned. */
public final class Arena extends Canvas implements CommandListener, Runnable {
    private final OnlineMidlet app;
    private final Command leave = new Command("Roi phong", Command.BACK, 9);
    private final Command ready = new Command("San sang", Command.SCREEN, 1);
    private final Command start = new Command("Bat dau", Command.SCREEN, 2);
    private final Command pass = new Command("Bo luot", Command.SCREEN, 3);
    private Image soldier, enemy, boss;
    private int angle = 45, power = 73, lastTurn, displayedShot, frame;
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
    private int sx(int x) { return x * getWidth() / 720; }
    private int sy(int y) { return 55 + y * (getHeight()-155) / 320; }
    protected void paint(Graphics g) {
        State s = app.state;
        g.setColor(0x102238); g.fillRect(0,0,getWidth(),getHeight());
        g.setFont(Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL));
        g.setColor(0xffffff);
        if (s == null) { g.drawString("Dang ket noi...",8,8,Graphics.TOP|Graphics.LEFT); return; }
        if (lastTurn != s.turn) {
            lastTurn=s.turn;
            for (int i=0;i<s.players.length;i++) if (s.players[i].id==s.uid) angle=s.players[i].x>360?135:45;
        }
        g.drawString("#"+s.room+" "+s.mode+" | "+s.name+" | "+s.coins+" xu",5,3,Graphics.TOP|Graphics.LEFT);
        if (s.phase.equals("lobby")) {
            g.drawString("Cho nguoi choi: tat ca San sang",8,35,Graphics.TOP|Graphics.LEFT);
            g.drawString("Chu phong chon Bat dau",8,53,Graphics.TOP|Graphics.LEFT);
            for(int i=0;i<s.players.length;i++) g.drawString(s.players[i].name+(s.players[i].ready?" [OK]":" [cho]"),12,83+i*24,Graphics.TOP|Graphics.LEFT);
            button(g,0,getHeight()-70,getWidth()/2,34,"SAN SANG");
            button(g,getWidth()/2,getHeight()-70,getWidth()/2,34,"BAT DAU");
        } else {
            g.drawString(s.phase.equals("finished")?"TRAN DA KET THUC - chon Roi phong":(s.turn==s.uid?"LUOT CUA BAN":"Dang cho doi thu")+" | "+s.seconds+"s",5,22,Graphics.TOP|Graphics.LEFT);
            g.setColor(0x2c4860); g.fillRect(0,sy(160),getWidth(),sy(256)-sy(160));
            g.setColor(0x8dcf75); g.fillRect(0,sy(256),getWidth(),4);
            g.setColor(0x534b43); g.fillRect(0,sy(256)+4,getWidth(),Math.max(0,getHeight()-100-sy(256)));
            for(int i=0;i<s.players.length;i++) {
                State.Player p=s.players[i];
                if(p.hp<=0) continue;
                int x=sx(p.x),y=sy(256);
                Image image=p.id==s.uid?soldier:enemy;
                if(image!=null) {
                    g.drawRegion(image,0,120,24,24,0,x,y,Graphics.HCENTER|Graphics.BOTTOM);
                    g.drawRegion(image,0,0,24,24,0,x,y-12,Graphics.HCENTER|Graphics.BOTTOM);
                }
                else {g.setColor(p.id==s.uid?0x55ddff:0xff8855);g.fillRect(x-6,y-20,12,20);}
                g.setColor(0xffffff);g.drawString(p.name,x,y-48,Graphics.HCENTER|Graphics.TOP);
                g.setColor(0x302030);g.fillRect(x-20,y-30,40,4);
                g.setColor(0x63e6a2);g.fillRect(x-20,y-30,40*p.hp/p.maxHp,4);
                g.setColor(0xffffff);g.drawString(""+p.hp,x,y+4,Graphics.HCENTER|Graphics.TOP);
            }
            if(s.bossMax>0) {
                int x=sx(600),y=sy(256);
                if(boss!=null && s.bossHp>0)g.drawRegion(boss,0,0,112,112,0,x,y,Graphics.HCENTER|Graphics.BOTTOM);
                g.setColor(0xffbb66);g.drawString("BOSS "+s.bossHp+"/"+s.bossMax,x,sy(105),Graphics.HCENTER|Graphics.TOP);
            }
            g.setColor(0xffe18a);
            if(displayedShot!=s.shotId){displayedShot=s.shotId;frame=1;}
            int visible=Math.min(frame,s.path.length);
            for(int i=1;i<visible;i++)g.drawLine(sx(s.path[i-1][0]),sy(s.path[i-1][1]),sx(s.path[i][0]),sy(s.path[i][1]));
            if(visible>0){int[] point=s.path[visible-1];g.fillArc(sx(point[0])-3,sy(point[1])-3,6,6,0,360);}
            g.setColor(0xffffff);
            String log=s.phase.equals("finished")?s.result:s.log;
            if(log!=null)g.drawString(log,5,getHeight()-100,Graphics.TOP|Graphics.LEFT);
            int w=getWidth()/5,y=getHeight()-73;
            button(g,0,y,w,30,"Goc -"); button(g,w,y,w,30,"Goc +");
            button(g,w*2,y,w,30,"Luc -"); button(g,w*3,y,w,30,"Luc +"); button(g,w*4,y,getWidth()-w*4,30,"BAN");
            button(g,0,y+33,w,27,"< DI");button(g,w,y+33,w,27,"DI >");
            g.setColor(0xffffff);g.drawString("Goc "+angle+" | Luc "+power,w*2+4,y+37,Graphics.TOP|Graphics.LEFT);
        }
        if(app.network.length()>0){g.setColor(0xff9999);g.drawString(app.network,4,getHeight()-14,Graphics.TOP|Graphics.LEFT);}
    }
    private void button(Graphics g,int x,int y,int w,int h,String text){
        g.setColor(0x295b78);g.fillRect(x+1,y,w-2,h);g.setColor(0xffffff);
        g.drawString(text,x+w/2,y+5,Graphics.HCENTER|Graphics.TOP);
    }
    protected void pointerReleased(int x,int y){
        State s=app.state;if(s==null)return;
        if(s.phase.equals("lobby")){if(y>=getHeight()-70 && y<getHeight()-36)app.send(x<getWidth()/2?"ready":"start","");return;}
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
