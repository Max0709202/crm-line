package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

/**
 * Scheduled DB backup: a gzipped SQL dump of the application's database (the one the app is
 * connected to — crm_v2), written through the app's own DB connection into
 * {@code /home/centos/crm-platform/crm-backups} as {@code crm-<timestamp>.sql.gz} — the filename
 * pattern {@link com.crm.controller.SystemStatsController} reads for "最新バックアップ".
 *
 * <p>The dump is made over JDBC rather than with {@code mysqldump}: this host has no MySQL client
 * (MySQL runs in a container), so the old {@code mysqldump | gzip} never produced a file. Each
 * table is written as {@code DROP TABLE IF EXISTS} + {@code SHOW CREATE TABLE} + INSERTs, read in a
 * single consistent snapshot ({@code START TRANSACTION WITH CONSISTENT SNAPSHOT}).
 *
 * <p>Disabled by default — an operator must explicitly turn it on from セキュリティ設定, since it
 * writes a full DB dump to local disk on a schedule (2026-09-09 client request also asked for a
 * capacity warning alongside the schedule UI).
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    /** Must stay under crm.service's ReadWritePaths= (see the systemd unit's sandbox
     *  directives) — ProtectHome=read-only blocks writes anywhere else under /home/centos.
     *  Override via app.backup.dir if deploying under a different path/user. */
    private final String backupDir;
    private static final String KEY_ENABLED = "backup.enabled";
    private static final String KEY_INTERVAL_HOURS = "backup.intervalHours";
    private static final String KEY_RETENTION_COUNT = "backup.retentionCount";
    private static final String KEY_LAST_RUN_AT = "backup.lastRunAt";
    /** Outcome of the latest attempt: "成功" or "失敗"; with its time. */
    private static final String KEY_LAST_RESULT = "backup.lastResult";
    private static final String KEY_LAST_RESULT_AT = "backup.lastResultAt";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    public static final String RESULT_OK = "成功";
    public static final String RESULT_NG = "失敗";
    /** Rows per INSERT statement. */
    private static final int ROWS_PER_INSERT = 200;

    private final CrmSettingRepository settingRepository;
    private final DataSource dataSource;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BackupService(CrmSettingRepository settingRepository, DataSource dataSource,
                          @Value("${app.backup.dir:/home/centos/crm-platform/crm-backups}") String backupDir) {
        this.settingRepository = settingRepository;
        this.dataSource = dataSource;
        this.backupDir = backupDir;
    }

    public static class Config {
        public boolean enabled;
        public int intervalHours;
        public int retentionCount;
        public LocalDateTime lastRunAt;
        /** {@link #RESULT_OK} / {@link #RESULT_NG}, null before the first run. */
        public String lastResult;
        public LocalDateTime lastResultAt;
    }

    public Config getConfig() {
        Config c = new Config();
        c.enabled = "true".equals(settingValue(KEY_ENABLED));
        c.intervalHours = parseIntOr(settingValue(KEY_INTERVAL_HOURS), 24);
        c.retentionCount = parseIntOr(settingValue(KEY_RETENTION_COUNT), 7);
        c.lastRunAt = parseTime(settingValue(KEY_LAST_RUN_AT));
        c.lastResult = blankToNull(settingValue(KEY_LAST_RESULT));
        c.lastResultAt = parseTime(settingValue(KEY_LAST_RESULT_AT));
        return c;
    }

    @Transactional
    public void saveConfig(boolean enabled, int intervalHours, int retentionCount) {
        intervalHours = Math.max(1, Math.min(720, intervalHours));
        retentionCount = Math.max(1, Math.min(365, retentionCount));
        setSetting(KEY_ENABLED, Boolean.toString(enabled));
        setSetting(KEY_INTERVAL_HOURS, Integer.toString(intervalHours));
        setSetting(KEY_RETENTION_COUNT, Integer.toString(retentionCount));
    }

    /** Called by the hourly scheduler tick — runs a backup if enabled and due. */
    public void runIfDue(LocalDateTime now) {
        Config c = getConfig();
        if (!c.enabled) return;
        if (c.lastRunAt != null && c.lastRunAt.plusHours(c.intervalHours).isAfter(now)) return;
        runBackupNow();
    }

    /** True while a backup is being written. */
    public boolean isRunning() { return running.get(); }

    /**
     * Writes one backup synchronously and prunes old ones beyond the retention count. Also called
     * by 今すぐバックアップ. Returns false when it failed (or another backup was already running).
     */
    public boolean runBackupNow() {
        if (!running.compareAndSet(false, true)) return false;
        try {
            return doBackup();
        } finally {
            running.set(false);
        }
    }

    private boolean doBackup() {
        File dir = new File(backupDir);
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("BackupService: could not create backup dir {}", backupDir);
            recordResult(false);
            return false;
        }
        String fileName = "crm-" + LocalDateTime.now().format(TS) + ".sql.gz";
        File out = new File(dir, fileName);
        File part = new File(dir, fileName + ".part");
        try {
            try (Connection con = dataSource.getConnection();
                 Writer w = new BufferedWriter(new OutputStreamWriter(
                         new GZIPOutputStream(new FileOutputStream(part), 65536), StandardCharsets.UTF_8))) {
                dump(con, w);
            }
            if (!part.renameTo(out)) throw new IOException("rename failed: " + part);
            setSetting(KEY_LAST_RUN_AT, LocalDateTime.now().toString());
            recordResult(true);
            log.info("BackupService: wrote {} ({} bytes)", fileName, out.length());
            pruneOld(dir);
            return true;
        } catch (IOException | SQLException | RuntimeException e) {
            log.warn("BackupService: backup failed: {}", e.toString());
            part.delete();
            out.delete();
            recordResult(false);
            return false;
        }
    }

    /** The whole schema the connection points at, as SQL. */
    private static void dump(Connection con, Writer w) throws SQLException, IOException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try (Statement st = con.createStatement()) {
            st.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ");
        }
        try (Statement st = con.createStatement()) {
            st.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
        }
        try {
            w.write("-- CRM backup " + LocalDateTime.now() + " (database " + con.getCatalog() + ")\n");
            w.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n\n");
            List<String> tables = new ArrayList<>();
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'")) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            for (String t : tables) dumpTable(con, t, w);
            w.write("SET FOREIGN_KEY_CHECKS=1;\n");
        } finally {
            con.rollback();
            con.setAutoCommit(autoCommit);
        }
    }

    private static void dumpTable(Connection con, String table, Writer w) throws SQLException, IOException {
        String q = "`" + table.replace("`", "``") + "`";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + q)) {
            if (!rs.next()) return;
            w.write("DROP TABLE IF EXISTS " + q + ";\n" + rs.getString(2) + ";\n\n");
        }
        // streamed row by row (MySQL Connector/J: forward-only + fetch size MIN_VALUE)
        try (Statement st = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            st.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet rs = st.executeQuery("SELECT * FROM " + q)) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                int n = 0;
                while (rs.next()) {
                    w.write(n % ROWS_PER_INSERT == 0 ? "INSERT INTO " + q + " VALUES\n(" : ",\n(");
                    for (int i = 1; i <= cols; i++) {
                        if (i > 1) w.write(',');
                        w.write(literal(rs, i, md.getColumnType(i)));
                    }
                    w.write(')');
                    n++;
                    if (n % ROWS_PER_INSERT == 0) w.write(";\n");
                }
                if (n % ROWS_PER_INSERT != 0) w.write(";\n");
                w.write('\n');
            }
        }
    }

    /** One column value as a MySQL literal. */
    static String literal(ResultSet rs, int i, int type) throws SQLException {
        switch (type) {
            case Types.BINARY: case Types.VARBINARY: case Types.LONGVARBINARY: case Types.BLOB: {
                byte[] b = rs.getBytes(i);
                if (b == null) return "NULL";
                StringBuilder sb = new StringBuilder(b.length * 2 + 3).append("X'");
                for (byte x : b) sb.append(String.format("%02X", x));
                return sb.append('\'').toString();
            }
            case Types.BIT: case Types.BOOLEAN: {
                Object o = rs.getObject(i);
                if (o == null) return "NULL";
                if (o instanceof Boolean) return ((Boolean) o) ? "1" : "0";
                if (o instanceof byte[]) {
                    long v = 0;
                    for (byte x : (byte[]) o) v = (v << 8) | (x & 0xFF);
                    return Long.toString(v);
                }
                return o instanceof Number ? o.toString() : quote(o.toString());
            }
            case Types.TINYINT: case Types.SMALLINT: case Types.INTEGER: case Types.BIGINT:
            case Types.DECIMAL: case Types.NUMERIC: case Types.FLOAT: case Types.REAL: case Types.DOUBLE: {
                String v = rs.getString(i);
                return v == null ? "NULL" : v;
            }
            default: {
                String v = rs.getString(i);
                return v == null ? "NULL" : quote(v);
            }
        }
    }

    static String quote(String v) {
        StringBuilder sb = new StringBuilder(v.length() + 8).append('\'');
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\0': sb.append("\\0"); break;
                case '\u001a': sb.append("\\Z"); break;
                default: sb.append(c);
            }
        }
        return sb.append('\'').toString();
    }

    private void recordResult(boolean ok) {
        try {
            setSetting(KEY_LAST_RESULT, ok ? RESULT_OK : RESULT_NG);
            setSetting(KEY_LAST_RESULT_AT, LocalDateTime.now().toString());
        } catch (RuntimeException e) {
            log.warn("BackupService: could not record the result: {}", e.toString());
        }
    }

    private void pruneOld(File dir) {
        Config c = getConfig();
        File[] files = dir.listFiles((d, name) -> name.startsWith("crm-") && name.endsWith(".sql.gz"));
        if (files == null || files.length <= c.retentionCount) return;
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        sorted.sort(Comparator.comparingLong(File::lastModified));
        int toDelete = sorted.size() - c.retentionCount;
        for (int i = 0; i < toDelete; i++) {
            if (sorted.get(i).delete()) {
                log.info("BackupService: pruned old backup {}", sorted.get(i).getName());
            }
        }
    }

    private String settingValue(String key) {
        return settingRepository.findBySettingKey(key).map(CrmSetting::getSettingValue).orElse(null);
    }

    private void setSetting(String key, String value) {
        CrmSetting s = settingRepository.findBySettingKey(key).orElseGet(() -> {
            CrmSetting ns = new CrmSetting();
            ns.setSettingKey(key);
            ns.setDescription("DB backup schedule setting: " + key);
            ns.setUpdatedAt(LocalDateTime.now());
            return ns;
        });
        s.setSettingValue(value);
        s.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(s);
    }

    private static LocalDateTime parseTime(String v) {
        if (v == null || v.trim().isEmpty()) return null;
        try { return LocalDateTime.parse(v.trim()); } catch (RuntimeException e) { return null; }
    }

    private static String blankToNull(String v) {
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    private static int parseIntOr(String v, int fallback) {
        if (v == null || v.trim().isEmpty()) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}
