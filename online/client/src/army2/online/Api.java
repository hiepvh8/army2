package army2.online;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.io.IOException;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

public final class Api {
    public static String escape(String text) {
        try {
            byte[] bytes = text.getBytes("UTF-8");
            StringBuffer out = new StringBuffer();
            String hex = "0123456789ABCDEF";
            for (int i=0; i<bytes.length; i++) {
                int c = bytes[i] & 255;
                if ((c>=65 && c<=90) || (c>=97 && c<=122) || (c>=48 && c<=57) || c==95 || c==45)
                    out.append((char)c);
                else { out.append('%'); out.append(hex.charAt(c>>4)); out.append(hex.charAt(c&15)); }
            }
            return out.toString();
        } catch (Exception e) { throw new IllegalArgumentException("UTF-8"); }
    }
    public static State call(String base, String token, String op, String args) throws IOException {
        HttpConnection conn = null;
        OutputStream output = null;
        DataInputStream input = null;
        try {
            conn = (HttpConnection)Connector.open(base + "/jme", Connector.READ_WRITE, true);
            conn.setRequestMethod(HttpConnection.POST);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Connection", "close");
            if (token.length() > 0) conn.setRequestProperty("Authorization", "Bearer " + token);
            byte[] body = ("op=" + escape(op) + args).getBytes("UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));
            output = conn.openOutputStream(); output.write(body); output.close(); output = null;
            int status = conn.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            input = new DataInputStream(conn.openInputStream());
            return State.read(input);
        } finally {
            if (input != null) try { input.close(); } catch (IOException ignored) {}
            if (output != null) try { output.close(); } catch (IOException ignored) {}
            if (conn != null) try { conn.close(); } catch (IOException ignored) {}
        }
    }
}
