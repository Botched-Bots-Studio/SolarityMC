package fun.solaritymc.solarityonboarding.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Who has onboarded, in {@code onboarding.db}. Everything is held in memory (a row per player, so it
 * stays small) and every write goes to one background thread, so no game thread ever touches disk.
 */
public final class PlayerStore {

    public record Entry(UUID uuid, String name, long completedAt, String source) {
    }

    private final File file;
    private final Logger log;
    private final Map<UUID, Entry> done = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SolarityOnboarding-db");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public PlayerStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("The SQLite driver isn't on the server. Paper ships it; are you on Paper or Folia?", e);
        }
        file.getParentFile().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("CREATE TABLE IF NOT EXISTS onboarded ("
                    + "uuid TEXT PRIMARY KEY, name TEXT, completed_at INTEGER NOT NULL, source TEXT)");
        }
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT uuid, name, completed_at, source FROM onboarded")) {
            while (rs.next()) {
                try {
                    UUID id = UUID.fromString(rs.getString(1));
                    done.put(id, new Entry(id, rs.getString(2), rs.getLong(3), rs.getString(4)));
                } catch (IllegalArgumentException ignored) {
                    // a hand-edited bad row; skip it
                }
            }
        }
    }

    public boolean isDone(UUID uuid) {
        return done.containsKey(uuid);
    }

    public Optional<Entry> get(UUID uuid) {
        return Optional.ofNullable(done.get(uuid));
    }

    public int count() {
        return done.size();
    }

    /** Marks a player as onboarded. Returns false if they already were. */
    public boolean markDone(UUID uuid, String name, String source) {
        Entry e = new Entry(uuid, name, System.currentTimeMillis(), source);
        if (done.putIfAbsent(uuid, e) != null) {
            return false;
        }
        write(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO onboarded (uuid, name, completed_at, source) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, e.completedAt());
                ps.setString(4, source);
                ps.executeUpdate();
            }
        });
        return true;
    }

    public boolean reset(UUID uuid) {
        boolean had = done.remove(uuid) != null;
        write(() -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM onboarded WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        });
        return had;
    }

    public int wipe() {
        int n = done.size();
        done.clear();
        write(() -> {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("DELETE FROM onboarded");
            }
        });
        return n;
    }

    private interface Sql {
        void run() throws SQLException;
    }

    private void write(Sql sql) {
        writer.execute(() -> {
            try {
                sql.run();
            } catch (SQLException e) {
                log.log(Level.SEVERE, "Couldn't save to onboarding.db", e);
            }
        });
    }

    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warning("onboarding.db writes took over 10 seconds on shutdown; some may be lost");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // closing anyway
        }
    }
}
