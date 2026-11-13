package army2.online;

import com.teamobi.mobiarmy2.GameMidlet;
import java.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.rms.RecordStore;

/** Additive entry point: original game classes are retained byte for byte. */
public final class OnlineMidlet extends GameMidlet implements CommandListener, Runnable {
    private Display display;
    private List home, lobby;
    private Form login, settings;
    private TextField username, password, endpoint;
    private final Command ok = new Command("OK", Command.OK, 1);
    private final Command register = new Command("Dang ky", Command.SCREEN, 2);
    private final Command back = new Command("Quay lai", Command.BACK, 9);
    public volatile State state;
    public volatile String network = "";
    private Arena arena;
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
            arena = new Arena(this);
            home = new List("ARMY2 Online - test v1", List.IMPLICIT);
            home.append("Online: dang nhap / tiep tuc", null);
            home.append("Dia chi server", null);
            home.append("Game offline goc", null);
            home.append("Thoat", null);
            home.setCommandListener(this);
            new Thread(this).start();
            display.setCurrent(home);
        } else if (online) display.setCurrent(state != null && state.room != 0 ? (Displayable) arena : (lobby == null ? (Displayable) home : (Displayable) lobby));
        else if (GameMidlet.isStartGame) super.startApp();
        else display.setCurrent(home);
    }

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
        } catch (Exception e) { network = "Khong luu duoc phien dang nhap."; }
        finally { if (rms != null) try { rms.closeRecordStore(); } catch (Exception ignored) {} }
    }
    private void loginForm() {
        login = new Form("Tai khoan online rieng");
        login.append("Ten 3-16 ky tu. Mat khau >=10 ky tu. Khong dung tai khoan cloud cu.");
        username = new TextField("Ten", "", 16, TextField.ANY);
        password = new TextField("Mat khau", "", 128, TextField.PASSWORD);
        login.append(username); login.append(password);
        login.addCommand(ok); login.addCommand(register); login.addCommand(back); login.setCommandListener(this);
        display.setCurrent(login);
    }
    public void commandAction(Command cmd, Displayable screen) {
        if (screen == home) {
            int i = home.getSelectedIndex();
            if (i == 0) {
                if (token.length() == 0) loginForm();
                else { online = true; send("poll", ""); }
            } else if (i == 1) {
                settings = new Form("Server online");
                endpoint = new TextField("HTTPS URL (khong /jme)", base, 220, TextField.URL);
                settings.append(endpoint); settings.addCommand(ok); settings.addCommand(back);
                settings.setCommandListener(this); display.setCurrent(settings);
            } else if (i == 2) {
                synchronized (this) { online = false; endpointRevision++; pendingOp = null; }
                try { super.startApp(); } catch (Exception e) { error(e.toString(), home); }
            } else { stopped = true; notifyDestroyed(); }
        } else if (screen == settings) {
            if (cmd == ok) {
                String value = endpoint.getString().trim();
                while (value.endsWith("/")) value = value.substring(0, value.length()-1);
                // Plain HTTP is only for loopback development. Real accounts require HTTPS.
                if (!(value.startsWith("https://") || value.startsWith("http://127.0.0.1:") || value.startsWith("http://localhost:"))) {
                    error("Dung HTTPS cho server tu xa.", settings); return;
                }
                synchronized (this) {
                    if (!base.equals(value)) {
                        endpointRevision++; token = ""; state = null; pendingOp = null; online = false;
                    }
                    base = value; save();
                }
            }
            display.setCurrent(home);
        } else if (screen == login) {
            if (cmd == back) { display.setCurrent(home); return; }
            String args = "&name=" + Api.escape(username.getString()) + "&password=" + Api.escape(password.getString());
            password.setString("");
            send(cmd == register ? "register" : "login", args);
        } else if (screen == lobby) {
            if (cmd == back) { synchronized (this) { online = false; endpointRevision++; pendingOp = null; } display.setCurrent(home); return; }
            int index = lobby.getSelectedIndex();
            if (index == 0) send("create", "&mode=pvp");
            else if (index == 1) send("create", "&mode=boss");
            else if (index == 2) send("claim", "");
            else if (index == 3) send("logout", "");
            else if (state != null && index-4 < state.rooms.length)
                send("join", "&room=" + state.rooms[index-4].id);
        }
    }

    public synchronized void send(String op, String args) {
        if (pendingOp != null) return;
        pendingOp = op; pendingArgs = args; notifyAll();
    }
    private void refresh(State s) {
        if (s.room != 0) {
            if (display.getCurrent() != arena && !(display.getCurrent() instanceof Alert)) display.setCurrent(arena);
            arena.repaint();
        } else {
            Displayable current = display.getCurrent();
            if (current == home || current == login || current == arena || current == lobby || current == null) {
                int selected = lobby != null ? lobby.getSelectedIndex() : 0;
                lobby = new List(s.name + " | " + s.coins + " xu", List.IMPLICIT);
                lobby.append("Tao phong PvP 1v1", null);
                lobby.append("Tao phong boss 2-4 nguoi", null);
                lobby.append("Su kien: " + s.progress + "/" + s.goal + (s.claimed ? " da nhan" : " - nhan 200 xu"), null);
                lobby.append("Dang xuat tai khoan", null);
                for (int i=0; i<s.rooms.length; i++) {
                    State.Room r=s.rooms[i];
                    lobby.append("#" + r.id + " " + r.mode + " " + r.name + " " + r.count + "/" + r.capacity, null);
                }
                if (selected >= 0 && selected < lobby.size()) lobby.setSelectedIndex(selected, true);
                lobby.addCommand(back); lobby.setCommandListener(this); display.setCurrent(lobby);
            }
        }
    }
    private void error(String message, Displayable next) {
        Alert alert = new Alert("Thong bao", message, null, AlertType.INFO);
        alert.setTimeout(Alert.FOREVER); display.setCurrent(alert, next == null ? home : next);
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
                        display.callSerially(new Runnable() { public void run() { display.setCurrent(home); } });
                        continue;
                    }
                    if (s.token.length() > 0) { token = s.token; save(); }
                    state = s; online = true; network = "";
                }
                final int currentRevision = revision;
                display.callSerially(new Runnable() { public void run() { if (currentRevision == endpointRevision) refresh(s); } });
            } catch (final Exception e) {
                synchronized (this) { if (revision != endpointRevision) continue; }
                network = e.getMessage() == null ? e.toString() : e.getMessage();
                if (!op.equals("poll") || network.indexOf("dang nhap") >= 0 || network.indexOf("Phien") >= 0) {
                    boolean expired = network.indexOf("dang nhap") >= 0 || network.indexOf("Phien") >= 0;
                    final Displayable next = expired ? (Displayable) home : display.getCurrent();
                    if (expired) { online = false; token = ""; state = null; save(); }
                    display.callSerially(new Runnable() { public void run() { error(e.getMessage(), next); } });
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
    protected void pauseApp() {
        // Manic EMU may suspend networking in background; poll resumes to recover server state.
        if (GameMidlet.isStartGame) super.pauseApp();
    }
}
