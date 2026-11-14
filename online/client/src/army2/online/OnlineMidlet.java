package army2.online;

import com.teamobi.mobiarmy2.GameMidlet;
import coreLG.CCanvas;
import java.io.*;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.rms.RecordStore;

/** Runs the original game canvas and adds online screens using its UI components. */
public final class OnlineMidlet extends GameMidlet implements Runnable {
    private Display display;
    private GameMenu menu;
    private GameLogin login;
    private Arena arena;
    public volatile State state;
    public volatile String network = "";
    private volatile boolean online, stopped;
    private boolean initialized;
    private String base, token = "";
    private String pendingOp, pendingArgs;
    private int endpointRevision;
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
        synchronized (this) { online = false; endpointRevision++; pendingOp = null; }
        display.setCurrent(GameMidlet.gameCanvas);
        CCanvas.mainMenuScr = menu;
        menu.showHome();
    }
    public void offline() {
        synchronized (this) { online = false; endpointRevision++; pendingOp = null; }
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
        if (!(value.startsWith("https://") || value.startsWith("http://127.0.0.1:") || value.startsWith("http://localhost:"))) {
            CCanvas.startOKDlg("Dùng HTTPS cho máy chủ từ xa."); return;
        }
        synchronized (this) {
            if (!base.equals(value)) { endpointRevision++; token = ""; state = null; pendingOp = null; online = false; }
            base = value; save();
        }
        home();
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
    private void refresh(State s, boolean action) {
        if (action) CCanvas.endDlg();
        if (s.room != 0) {
            if (display.getCurrent() != arena) display.setCurrent(arena);
            arena.repaint();
        } else if (display.getCurrent() == arena || CCanvas.curScr == login || (CCanvas.curScr == menu && !menu.isLobby())) {
            showLobby();
        }
    }
    public void run() {
        while (!stopped) {
            String op, args, requestBase, requestToken;
            int revision;
            synchronized (this) {
                if (pendingOp == null && !online) {
                    try { wait(1000); } catch (InterruptedException ignored) {}
                    continue;
                }
                op = pendingOp == null ? "poll" : pendingOp;
                args = pendingOp == null ? "" : pendingArgs;
                pendingOp = null; pendingArgs = null;
                requestBase = base; requestToken = token; revision = endpointRevision;
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
                final boolean action = !op.equals("poll");
                display.callSerially(new Runnable() { public void run() { if (currentRevision == endpointRevision) refresh(s, action); } });
            } catch (final Exception e) {
                synchronized (this) { if (revision != endpointRevision) continue; }
                network = e.getMessage() == null ? e.toString() : e.getMessage();
                boolean expired = network.indexOf("dang nhap") >= 0 || network.indexOf("Phien") >= 0;
                if (expired) { online = false; token = ""; state = null; save(); }
                if (!op.equals("poll") || expired) {
                    final boolean goHome = expired;
                    display.callSerially(new Runnable() { public void run() {
                        if (goHome) home();
                        CCanvas.endDlg();
                        CCanvas.startOKDlg(e.getMessage() == null ? "Không kết nối được máy chủ." : e.getMessage());
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
