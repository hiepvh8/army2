package army2.online;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Visual-only rendering of the original ice-bridge map bundled in IOS_TuTien.jar.
 * Combat positions, hits and rewards remain authoritative on the test server.
 */
public final class OriginalScene {
    private static final String MAP="/res/map/0_caycaubang.map";
    private static final int SIM_LEFT=25, SIM_RIGHT=695, MAP_LEFT=64, MAP_RIGHT=703;
    private static final int GROUND=507, SIM_GROUND=256;
    private int mapWidth, mapHeight;
    private int[] tileIds, tileX, tileY;
    private Image[] tiles=new Image[128];
    private Image farIce, nearIce, wind, windCenter;
    private boolean loaded;

    public OriginalScene() {
        DataInputStream in=null;
        try {
            InputStream raw=getClass().getResourceAsStream(MAP);
            if(raw==null)throw new IOException("Map resource missing");
            in=new DataInputStream(raw);
            mapWidth=in.readUnsignedShort();mapHeight=in.readUnsignedShort();
            int count=in.readUnsignedByte();
            if(mapWidth<720 || mapWidth>4096 || mapHeight<400 || count<1 || count>120)
                throw new IOException("Invalid original map");
            tileIds=new int[count];tileX=new int[count];tileY=new int[count];
            for(int i=0;i<count;i++){
                int id=in.readUnsignedByte();int x=in.readShort(),y=in.readShort();
                if(id>=tiles.length)throw new IOException("Unknown map tile");
                tileIds[i]=id;tileX[i]=x;tileY[i]=y;
                if(tiles[id]==null)tiles[id]=Image.createImage("/res/icon/map/"+id+".png");
            }
            farIce=Image.createImage("/res/map/bgItem/back8.png");
            nearIce=Image.createImage("/res/map/bgItem/back7.png");
            wind=Image.createImage("/res/wind.png");
            windCenter=Image.createImage("/res/wind2.png");
            loaded=true;
        } catch(Exception ignored) {loaded=false;}
        finally {if(in!=null)try{in.close();}catch(IOException ignored){}}
    }
    public boolean isLoaded(){return loaded;}
    public int tileCount(){return tileIds==null?0:tileIds.length;}
    public int mapWidth(){return mapWidth;}
    public int mapHeight(){return mapHeight;}
    public int mapX(int x){return MAP_LEFT+(x-SIM_LEFT)*(MAP_RIGHT-MAP_LEFT)/(SIM_RIGHT-SIM_LEFT);}
    public int camera(int simFocus,int width){
        int x=mapX(simFocus)-width/2;
        return Math.max(0,Math.min(x,Math.max(0,mapWidth-width)));
    }
    public int ground(int height){return Math.max(125,height-112);}
    public int screenX(int simX,int camera){return mapX(simX)-camera;}
    public int screenY(int simY,int height){return ground(height)+(simY-SIM_GROUND);}
    public void paintTerrain(Graphics g,int width,int height,int camera){
        g.setColor(0xe8f3ff);g.fillRect(0,0,width,height);
        int floor=ground(height);
        if(!loaded){g.setColor(0x8dcf75);g.fillRect(0,floor,width,4);return;}
        int w=farIce.getWidth(), offset=(camera/8)%w;
        for(int x=-offset-w;x<width+w;x+=w){
            g.drawImage(farIce,x,floor+12,Graphics.LEFT|Graphics.BOTTOM);
            g.drawImage(farIce,x+30,floor+105,Graphics.LEFT|Graphics.BOTTOM);
        }
        w=nearIce.getWidth();offset=(camera/4)%w;
        for(int x=-offset-w;x<width+w;x+=w)
            g.drawImage(nearIce,x+50,floor+78,Graphics.LEFT|Graphics.BOTTOM);
        for(int i=0;i<tileIds.length;i++) {
            Image tile=tiles[tileIds[i]];
            int x=tileX[i]-camera;
            if(x+tile.getWidth()<0 || x>=width)continue;
            g.drawImage(tile,x,floor+(tileY[i]-GROUND),Graphics.TOP|Graphics.LEFT);
        }
    }
    public void paintWind(Graphics g,int width){
        if(!loaded)return;
        g.drawImage(wind,width/2,23,Graphics.HCENTER|Graphics.VCENTER);
        g.drawImage(windCenter,width/2,23,Graphics.HCENTER|Graphics.VCENTER);
    }
}
