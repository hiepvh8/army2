package army2.online;

import java.io.DataInputStream;
import java.io.IOException;

/** Immutable-after-publication wire snapshot. No client-authored HP or rewards. */
public final class State {
    public String token, name, eventId, eventTitle, result, log, mode, phase;
    public int uid, coins, wins, goal, progress, room, owner, turn, seq, seconds, bossHp, bossMax, shotId;
    public boolean claimed, eventActive;
    public Room[] rooms;
    public Player[] players;
    public int[][] path;
    public static final class Room {
        public int id, count, capacity;
        public String name, mode, phase;
    }
    public static final class Player {
        public int id, x, hp, maxHp;
        public String name;
        public boolean ready;
    }
    public static State read(DataInputStream in) throws IOException {
        if (in.readInt() != 0x41324f31) throw new IOException("Sai giao thuc server.");
        int status = in.readUnsignedByte();
        String error = in.readUTF();
        if (status != 0) throw new IOException(error);
        State s = new State();
        s.token = in.readUTF();
        s.uid = in.readInt(); s.name = in.readUTF(); s.coins = in.readInt(); s.wins = in.readInt();
        s.eventId = in.readUTF(); s.eventTitle = in.readUTF(); s.goal = in.readInt(); s.progress = in.readInt();
        s.claimed = in.readBoolean(); s.eventActive = in.readBoolean();
        int count = in.readUnsignedShort();
        if (count > 64) throw new IOException("Qua nhieu phong.");
        s.rooms = new Room[count];
        for (int i = 0; i < count; i++) {
            Room r = new Room(); r.id = in.readInt(); r.name = in.readUTF(); r.mode = in.readUTF();
            r.count = in.readUnsignedByte(); r.capacity = in.readUnsignedByte(); r.phase = in.readUTF();
            s.rooms[i] = r;
        }
        s.players = new Player[0]; s.path = new int[0][2]; s.phase = "lobby";
        if (in.readBoolean()) {
            s.room = in.readInt(); s.mode = in.readUTF(); s.phase = in.readUTF();
            s.owner = in.readInt(); s.turn = in.readInt(); s.seq = in.readInt(); s.seconds = in.readInt();
            s.bossHp = in.readInt(); s.bossMax = in.readInt(); s.result = in.readUTF();
            count = in.readUnsignedByte();
            if (count > 4) throw new IOException("Qua nhieu nguoi choi.");
            s.players = new Player[count];
            for (int i = 0; i < count; i++) {
                Player p = new Player(); p.id = in.readInt(); p.name = in.readUTF();
                p.x = in.readInt(); p.hp = in.readInt(); p.maxHp = in.readInt(); p.ready = in.readBoolean();
                s.players[i] = p;
            }
            count = in.readUnsignedShort();
            if (count > 241) throw new IOException("Duong dan qua dai.");
            s.path = new int[count][2];
            for (int i = 0; i < count; i++) { s.path[i][0] = in.readShort(); s.path[i][1] = in.readShort(); }
            s.shotId = in.readInt(); s.log = in.readUTF();
        }
        return s;
    }
}
