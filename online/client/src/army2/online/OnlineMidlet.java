package army2.online;

import com.teamobi.mobiarmy2.GameMidlet;
import com.teamobi.mobiarmy2.CloudSaveGuard2342;
import com.teamobi.mobiarmy2.OfflineSave;
import com.teamobi.mobiarmy2.OfflineSaveSafe;
import coreLG.CCanvas;
import java.io.*;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.rms.RecordStore;

/** Runs the original game canvas and adds online screens using its UI components. */
public final class OnlineMidlet extends GameMidlet implements Runnable {
    private Display display;
    private GameMenu menu;
    private GameLogin login;
    private Arena arena;
    private ProfileMenu profileMenu;
    public volatile State state;
    public volatile String network = "";
    private volatile boolean online, stopped;
    private boolean initialized;
    private String base, token = "";
    private String pendingOp, pendingArgs;
    private int endpointRevision;
    private boolean originalLoaded, profileBusy;
    private byte[] pendingProfile;
    private int pendingProfileRevision;
    public boolean isStopped() { return stopped; }

    public void startApp() throws MIDletStateChangeException {
        if (!initialized) {
            initialized = true;
            display = Display.getDisplay(this);
            base = getAppProperty("Army2-Server");
            if (base == null) base = "http://127.0.0.1:8080";
            restore();
            super.startApp();
            arena = new Arena(this);
            menu = new GameMenu(this);
            profileMenu = new ProfileMenu(this);
            // SplashScr uses the pre-existing menu instance instead of creating another.
            boolean alreadyVisible = CCanvas.curScr == CCanvas.mainMenuScr && CCanvas.mainMenuScr != null;
            CCanvas.mainMenuScr = menu;
            if (alreadyVisible) menu.showHome();
            new Thread(this).start();
        } else if (online && state != null && state.room != 0) display.setCurrent(arena);
        else display.setCurrent(GameMidlet.gameCanvas);
    }
    public String serverAddress() { return base; }
    public boolean hasSession() { return token.length() != 0; }
    public void home() {
        synchronized (this) { online = false; endpointRevision++; pendingOp = null; pendingProfile = null; profileBusy = false; }
        CCanvas.endDlg();
        display.setCurrent(GameMidlet.gameCanvas);
        CCanvas.mainMenuScr = menu;
        menu.showHome();
    }
    public void offline() {
        synchronized (this) { online = false; endpointRevision++; pendingOp = null; pendingProfile = null; profileBusy = false; }
        CCanvas.endDlg();
        display.setCurrent(GameMidlet.gameCanvas);
        CCanvas.mainMenuScr = new screen.MainMenuScr();
        CCanvas.mainMenuScr.show();
    }
    public void openLogin(boolean registering) {
        login = new GameLogin(this, registering ? GameLogin.REGISTER : GameLogin.LOGIN);
        login.show();
    }
    public void openSettings() {
        login = new GameLogin(this, GameLogin.SETTINGS);
        login.show();
    }
    public void saveServer(String value) {
        value = value.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length()-1);
        if (!validServer(value)) {
            CCanvas.startOKDlg("Dùng HTTPS cho máy chủ từ xa."); return;
        }
        synchronized (this) {
            if (!base.equals(value)) { endpointRevision++; token = ""; state = null; pendingOp = null; online = false; }
            base = value; save();
        }
        home();
    }
    private static boolean validServer(String value) {
        boolean secure=value.startsWith("https://");
        if(!secure && !value.startsWith("http://")) return false;
        int begin=secure?8:7, end=value.indexOf('/',begin);
        if(end<0) end=value.length();
        if(end==begin || value.indexOf('@')>=0 || value.indexOf('\\')>=0 || value.indexOf('?')>=0 || value.indexOf('#')>=0) return false;
        for(int i=0;i<value.length();i++) if(value.charAt(i)<=32 || value.charAt(i)>126) return false;
        String authority=value.substring(begin,end);
        int colon=authority.indexOf(':');
        String host=colon<0?authority:authority.substring(0,colon);
        if(host.length()==0 || host.charAt(0)=='.' || host.charAt(host.length()-1)=='.') return false;
        for(int i=0;i<host.length();i++) {
            char c=host.charAt(i);
            if(!((c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9') || c=='.' || c=='-')) return false;
        }
        if(colon>=0) {
            String port=authority.substring(colon+1);int number=0;
            if(port.length()==0 || port.length()>5) return false;
            for(int i=0;i<port.length();i++){char c=port.charAt(i);if(c<'0'||c>'9')return false;number=number*10+c-'0';}
            if(number<1 || number>65535)return false;
        }
        return secure || (colon>=0 && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")));
    }
    public void authenticate(String name, String password, boolean registering) {
        if (name.length() == 0 || password.length() == 0) { CCanvas.startOKDlg("Hãy nhập tài khoản và mật khẩu."); return; }
        send(registering ? "register" : "login", "&name=" + Api.escape(name) + "&password=" + Api.escape(password));
        CCanvas.startWaitDlg("Đang kết nối...");
    }
    public void connect() { online = true; send("poll", ""); CCanvas.startWaitDlg("Đang kết nối..."); }
    public void exitGame() { stopped = true; GameMidlet.exit(); }
    private void restore() {
        RecordStore rms = null;
        try {
            rms = RecordStore.openRecordStore("army2_online_v1", false);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(rms.getRecord(1)));
            base = in.readUTF(); token = in.readUTF(); in.close();
        } catch (Exception ignored) {} finally { if (rms != null) try { rms.closeRecordStore(); } catch (Exception ignored) {} }
    }
    private void save() {
        RecordStore rms = null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes); out.writeUTF(base); out.writeUTF(token); out.close();
            rms = RecordStore.openRecordStore("army2_online_v1", true);
            byte[] data = bytes.toByteArray();
            if (rms.getNumRecords() == 0) rms.addRecord(data, 0, data.length);
            else rms.setRecord(1, data, 0, data.length);
        } catch (Exception e) { network = "Không lưu được phiên đăng nhập."; }
        finally { if (rms != null) try { rms.closeRecordStore(); } catch (Exception ignored) {} }
    }
    public synchronized void send(String op, String args) {
        if (pendingOp != null) return;
        pendingOp = op; pendingArgs = args; notifyAll();
    }
    public void showLobby() {
        display.setCurrent(GameMidlet.gameCanvas);
        menu.showLobby();
    }
    public void showOriginalProfile() { profileMenu.show(); }
    private void loadOriginal() throws IOException {
        if(!CloudSaveGuard2342.maySave()) throw new IOException("Hãy thoát hẳn game và mở lại sau khi phục hồi nhân vật.");
        if(!OfflineSave.hasSave()) throw new IOException("Chưa có nhân vật trên máy. Chọn Chơi offline > Chơi mới để tạo.");
        if(!originalLoaded) {
            if(!GameMidlet.continueOfflineGame()) throw new IOException("Không đọc được nhân vật trên máy.");
            originalLoaded=true;
        }
    }
    public void enterOriginal() {
        try {
            loadOriginal();
            CCanvas.mainMenuScr=menu;
            GameMidlet.enterOfflineMenu();
        } catch(Exception e) {CCanvas.startOKDlg(e.getMessage());}
    }
    private synchronized boolean queueProfile(String op,byte[] bytes,int revision) {
        if(profileBusy || pendingOp!=null || state==null || token.length()==0)return false;
        profileBusy=true;pendingProfile=bytes;pendingProfileRevision=revision;
        pendingOp=op;pendingArgs="";notifyAll();return true;
    }
    public void uploadOriginal() {
        try {
            loadOriginal();
            byte[] bytes=CloudSaveGuard2342.exportFresh();
            int rev=Profile.revision(base,state.uid);
            if(!queueProfile("profile_upload",bytes,rev)) throw new IOException("Đang có yêu cầu khác. Hãy thử lại.");
            CCanvas.startWaitDlg("Đang lưu nhân vật...");
        } catch(Exception e) {CCanvas.startOKDlg(e.getMessage());}
    }
    public void downloadOriginal() {
        try {
            if(!CloudSaveGuard2342.maySave()) throw new IOException("Hãy thoát hẳn game và mở lại trước khi tải.");
            if(!queueProfile("profile_download",null,0)) throw new IOException("Đang có yêu cầu khác. Hãy thử lại.");
            CCanvas.startWaitDlg("Đang tải nhân vật...");
        } catch(Exception e) {CCanvas.startOKDlg(e.getMessage());}
    }
    public void replaceServerOriginal() {
        try {
            loadOriginal();
            byte[] bytes=CloudSaveGuard2342.exportFresh();
            if(!queueProfile("profile_resolve",bytes,0)) throw new IOException("Đang có yêu cầu khác. Hãy thử lại.");
            CCanvas.startWaitDlg("Đang sao lưu bản máy chủ...");
        } catch(Exception e) {CCanvas.startOKDlg(e.getMessage());}
    }
    public void restoreOriginalBackup() {
        try {
            if(!CloudSaveGuard2342.maySave()) throw new IOException("Hãy thoát hẳn game và mở lại trước khi phục hồi.");
            byte[] bytes=Profile.backup();
            if(bytes==null) throw new IOException("Chưa có bản dự phòng trên máy.");
            if(OfflineSave.hasSave()) {loadOriginal();Profile.saveBackup(CloudSaveGuard2342.exportFresh());}
            Profile.setRevision(base,state.uid,0);
            if(!CloudSaveGuard2342.importAndFreeze(bytes)) throw new IOException("Không phục hồi được: " + OfflineSaveSafe.error);
            originalLoaded=false;
            CCanvas.startOKDlg("Đã phục hồi dự phòng. Thoát hẳn game và mở lại trước khi chơi.");
        } catch(Exception e) {CCanvas.startOKDlg(e.getMessage());}
    }
    public void deleteOriginalBackup() {
        try {Profile.deleteBackup();CCanvas.startOKDlg("Đã xóa dự phòng mới nhất trên máy.");}
        catch(Exception e) {CCanvas.startOKDlg(e.getMessage());}
    }
    private void applyOriginal(Profile.Result result,String server,int uid) throws IOException {
        if(!CloudSaveGuard2342.maySave()) throw new IOException("Hãy khởi động lại game trước khi tải.");
        if(OfflineSave.hasSave()) {
            loadOriginal();
            Profile.saveBackup(CloudSaveGuard2342.exportFresh());
        }
        // Clear the optimistic-concurrency token before changing local data;
        // even an interrupted/failed import must not permit a stale overwrite.
        Profile.setRevision(server,uid,0);
        if(!CloudSaveGuard2342.importAndFreeze(result.bytes))
            throw new IOException("Không nhập được nhân vật; bản dự phòng vẫn được giữ: " + OfflineSaveSafe.error);
        originalLoaded=false;
        try {Profile.setRevision(server,uid,result.revision);}
        catch(IOException e) {throw new IOException("Đã tải nhân vật nhưng không lưu được phiên bản. Khởi động lại và kiểm tra trước khi đồng bộ.");}
    }
    private void runProfile(final String op, final byte[] bytes, final int expected,
                            final String server, final String secret, final int uid, final int generation) {
        try {
            final Profile.Result result=Profile.request(server,secret,op.equals("profile_resolve")?null:bytes,expected);
            display.callSerially(new Runnable(){public void run(){
                synchronized(OnlineMidlet.this) {
                    if(generation!=endpointRevision || !secret.equals(token) || state==null || state.uid!=uid)return;
                    profileBusy=false;
                }
                CCanvas.endDlg();
                try {
                    if(op.equals("profile_resolve")) {
                        // Preserve the current remote version first. The subsequent
                        // upload still uses CAS, so a simultaneous writer wins safely.
                        Profile.saveBackup(result.bytes);
                        if(!queueProfile("profile_upload",bytes,result.revision))
                            throw new IOException("Đã dự phòng bản máy chủ. Hãy thử lưu lại.");
                        CCanvas.startWaitDlg("Đang lưu nhân vật...");
                    } else if(op.equals("profile_upload")) {
                        Profile.setRevision(server,uid,result.revision);
                        CCanvas.startOKDlg("Đã lưu nhân vật lên tài khoản " + state.name + ".");
                    } else {
                        applyOriginal(result,server,uid);
                        CCanvas.startOKDlg("Đã tải nhân vật. Thoát hẳn game và mở lại trước khi chơi.");
                    }
                } catch(Exception e) {CCanvas.startOKDlg(e.getMessage());}
            }});
        } catch(final Exception e) {
            display.callSerially(new Runnable(){public void run(){
                synchronized(OnlineMidlet.this) {
                    if(generation!=endpointRevision)return;
                    profileBusy=false;
                }
                CCanvas.endDlg();
                CCanvas.startOKDlg(e.getMessage()==null?"Không kết nối được máy chủ.":e.getMessage());
            }});
        }
    }
    private void refresh(State s, boolean action) {
        if (action) CCanvas.endDlg();
        if (s.room != 0) {
            if (display.getCurrent() != arena && !(display.getCurrent() instanceof Alert)) display.setCurrent(arena);
            arena.repaint();
        } else if (display.getCurrent() == arena || CCanvas.curScr == login || (CCanvas.curScr == menu && !menu.isLobby())) {
            showLobby();
        }
    }
    public void run() {
        while (!stopped) {
            String op, args, requestBase, requestToken;
            int revision, profileRevision, uid;
            byte[] profileBytes;
            boolean requested;
            synchronized (this) {
                if (pendingOp == null && !online) {
                    try { wait(1000); } catch (InterruptedException ignored) {}
                    continue;
                }
                requested = pendingOp != null;
                op = pendingOp == null ? "poll" : pendingOp;
                args = pendingOp == null ? "" : pendingArgs;
                pendingOp = null; pendingArgs = null;
                requestBase = base; requestToken = token; revision = endpointRevision;
                profileBytes=pendingProfile;pendingProfile=null;
                profileRevision=pendingProfileRevision;uid=state==null?0:state.uid;
            }
            if(op.equals("profile_upload") || op.equals("profile_download") || op.equals("profile_resolve")) {
                runProfile(op,profileBytes,profileRevision,requestBase,requestToken,uid,revision);
                continue;
            }
            try {
                final State s = Api.call(requestBase, requestToken, op, args);
                synchronized (this) {
                    if (revision != endpointRevision) continue;
                    if (op.equals("logout")) {
                        token = ""; state = null; online = false; save();
                        display.callSerially(new Runnable() { public void run() { home(); } });
                        continue;
                    }
                    if (s.token.length() > 0) { token = s.token; save(); }
                    state = s; online = true; network = "";
                }
                final int currentRevision = revision;
                final boolean action = requested;
                display.callSerially(new Runnable() { public void run() { if (currentRevision == endpointRevision) refresh(s, action); } });
            } catch (final Exception e) {
                synchronized (this) { if (revision != endpointRevision) continue; }
                network = e.getMessage() == null ? e.toString() : e.getMessage();
                boolean expired = network.indexOf("dang nhap") >= 0 || network.indexOf("Phien") >= 0;
                if (expired) { online = false; token = ""; state = null; save(); }
                if (requested || expired) {
                    final boolean goHome = expired;
                    display.callSerially(new Runnable() { public void run() {
                        if (goHome) home();
                        String message=e.getMessage() == null ? "Không kết nối được máy chủ." : e.getMessage();
                        if (display.getCurrent()==arena) {
                            Alert notice=new Alert("Thông báo", message, null, AlertType.INFO);
                            notice.setTimeout(Alert.FOREVER);
                            display.setCurrent(notice, arena);
                        } else {
                            CCanvas.endDlg();
                            CCanvas.startOKDlg(message);
                        }
                    } });
                }
                arena.repaint();
            }
            try { Thread.sleep(900); } catch (InterruptedException ignored) {}
        }
    }
    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        stopped = true;
        if (GameMidlet.isStartGame) super.destroyApp(unconditional);
    }
    protected void pauseApp() { if (GameMidlet.isStartGame) super.pauseApp(); }
}
