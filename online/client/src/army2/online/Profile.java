package army2.online;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordEnumeration;

/** Transfers original AVP1 saves and keeps a checked local rollback copy. */
public final class Profile {
    private static final int MAX_BYTES = 0x300000;
    private static final String REVISION = "army2_profile_rev";
    private static final String BACKUP = "army2_profile_backup";

    public static final class Result {
        public final int revision;
        public final byte[] bytes;
        Result(int revision, byte[] bytes) { this.revision=revision; this.bytes=bytes; }
    }

    public static Result request(String base, String token, byte[] data, int expected) throws IOException {
        boolean upload = data != null;
        HttpConnection conn=null;
        OutputStream out=null;
        DataInputStream in=null;
        try {
            conn=(HttpConnection)Connector.open(base+(upload?"/profile/save":"/profile/load"), Connector.READ_WRITE, true);
            conn.setRequestMethod(HttpConnection.POST);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", String.valueOf(upload?data.length:0));
            if(upload) conn.setRequestProperty("X-Profile-Revision",String.valueOf(expected));
            out=conn.openOutputStream();
            if(upload) out.write(data);
            out.close(); out=null;
            int status=conn.getResponseCode();
            if(status==404 && !upload) throw new IOException("Tài khoản chưa có nhân vật trên máy chủ.");
            if(status==409) throw new IOException("Dữ liệu máy chủ đã thay đổi. Hãy kiểm tra và tải về trước khi lưu tiếp.");
            if(status!=200) throw new IOException("Không đồng bộ được nhân vật (HTTP " + status + ").");
            int revision;
            try { revision=Integer.parseInt(conn.getHeaderField("X-Profile-Revision")); }
            catch(Exception e) { throw new IOException("Máy chủ thiếu phiên bản bản lưu."); }
            if(revision<=0) throw new IOException("Phiên bản bản lưu không hợp lệ.");
            if(upload) return new Result(revision,null);
            long length=conn.getLength();
            if(length<31 || length>MAX_BYTES) throw new IOException("Kích thước bản lưu không hợp lệ.");
            byte[] bytes=new byte[(int)length];
            in=new DataInputStream(conn.openInputStream());in.readFully(bytes);
            if(in.read()!=-1) throw new IOException("Bản lưu có dữ liệu thừa.");
            return new Result(revision,bytes);
        } finally {
            if(in!=null) try {in.close();} catch(IOException ignored) {}
            if(out!=null) try {out.close();} catch(IOException ignored) {}
            if(conn!=null) try {conn.close();} catch(IOException ignored) {}
        }
    }

    public static int revision(String base,int uid) {
        try {
            byte[] bytes=read(REVISION);
            if(bytes==null)return 0;
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
            String server=in.readUTF();int user=in.readInt(),rev=in.readInt();in.close();
            return server.equals(base) && user==uid && rev>0?rev:0;
        } catch(Exception e) {return 0;}
    }
    public static void setRevision(String base,int uid,int revision) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeUTF(base);out.writeInt(uid);out.writeInt(revision);out.close();
        write(REVISION,bytes.toByteArray(),false);
    }
    private static int lastBackup(RecordStore store) throws Exception {
        int last=0;
        RecordEnumeration records=store.enumerateRecords(null,null,false);
        try {while(records.hasNextElement()) {int id=records.nextRecordId();if(id>last)last=id;}}
        finally {records.destroy();}
        return last;
    }
    public static boolean hasBackup() throws IOException {return backup()!=null;}
    public static byte[] backup() throws IOException {
        RecordStore store=null;
        try {
            store=RecordStore.openRecordStore(BACKUP,false);
            int id=lastBackup(store);return id==0?null:store.getRecord(id);
        } catch(javax.microedition.rms.RecordStoreNotFoundException absent) {return null;}
        catch(Exception e) {throw new IOException("Không đọc được dự phòng: " + e.toString());}
        finally {if(store!=null)try{store.closeRecordStore();}catch(Exception ignored){}}
    }
    public static void saveBackup(byte[] data) throws IOException {
        if(data==null || data.length==0 || data.length>MAX_BYTES) throw new IOException("Dự phòng không hợp lệ.");
        RecordStore store=null;
        try {
            store=RecordStore.openRecordStore(BACKUP,true);
            if(store.getNumRecords()>=8) throw new IOException("Đã có 8 dự phòng. Hãy kiểm tra và xóa bớt trước.");
            int id=store.addRecord(data,0,data.length);
            byte[] check=store.getRecord(id);
            if(check.length!=data.length) throw new IOException("Lưu dự phòng không đầy đủ.");
            for(int i=0;i<data.length;i++) if(check[i]!=data[i]) throw new IOException("Lưu dự phòng không khớp.");
        } catch(IOException e) {throw e;}
        catch(Exception e) {throw new IOException("Không lưu được dự phòng: " + e.toString());}
        finally {if(store!=null)try{store.closeRecordStore();}catch(Exception ignored){}}
    }
    public static void deleteBackup() throws IOException {
        RecordStore store=null;
        try {
            store=RecordStore.openRecordStore(BACKUP,false);
            int id=lastBackup(store);if(id!=0)store.deleteRecord(id);
        } catch(javax.microedition.rms.RecordStoreNotFoundException ignored) {}
        catch(Exception e) {throw new IOException("Không xóa được dự phòng: " + e.toString());}
        finally {if(store!=null)try{store.closeRecordStore();}catch(Exception ignored){}}
    }
    private static byte[] read(String name) throws IOException {
        RecordStore store=null;
        try {
            store=RecordStore.openRecordStore(name,false);
            if(store.getNumRecords()!=1) throw new IOException("Kho dữ liệu không hợp lệ.");
            return store.getRecord(1);
        } catch(javax.microedition.rms.RecordStoreNotFoundException absent) {return null;}
        catch(IOException e) {throw e;}
        catch(Exception e) {throw new IOException("Không đọc được dữ liệu trên máy: " + e.toString());}
        finally {if(store!=null)try{store.closeRecordStore();}catch(Exception ignored){}}
    }
    private static void write(String name,byte[] data,boolean onlyNew) throws IOException {
        if(data==null || data.length==0 || data.length>MAX_BYTES) throw new IOException("Dữ liệu trên máy không hợp lệ.");
        RecordStore store=null;
        try {
            store=RecordStore.openRecordStore(name,true);
            if(store.getNumRecords()==0) store.addRecord(data,0,data.length);
            else {
                if(onlyNew) throw new IOException("Đã có dự phòng. Hãy kiểm tra trước khi xóa dự phòng cũ.");
                store.setRecord(1,data,0,data.length);
            }
            byte[] check=store.getRecord(1);
            if(check.length!=data.length) throw new IOException("Lưu dự phòng không đầy đủ.");
            for(int i=0;i<data.length;i++) if(check[i]!=data[i]) throw new IOException("Lưu dự phòng không khớp.");
        } catch(IOException e) {throw e;}
        catch(Exception e) {throw new IOException("Không lưu được dữ liệu trên máy: " + e.toString());}
        finally {if(store!=null)try{store.closeRecordStore();}catch(Exception ignored){}}
    }
}
