package com.student.signit;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * MySQL persistence for {@link Store}. The Store keeps working in memory exactly as before;
 * this class loads it from the database at start-up and writes every change back (see schema.sql).
 */
final class Dbstore {
    private Dbstore() { }

    /** Same statements as schema.sql (kept in sync by hand when the schema changes). */
    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS meta (
            k VARCHAR(40)  NOT NULL PRIMARY KEY,
            v VARCHAR(255) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS users (
            id             VARCHAR(16)  NOT NULL PRIMARY KEY,
            name           VARCHAR(120) NOT NULL,
            email          VARCHAR(160) NOT NULL UNIQUE,
            pass_hash      CHAR(64)     NOT NULL,
            department     VARCHAR(120) NOT NULL,
            `position`     VARCHAR(120) NOT NULL,
            role           ENUM('STUDENT','FACULTY','ADMIN') NOT NULL,
            requested_role ENUM('STUDENT','FACULTY','ADMIN') NULL,
            status         ENUM('PENDING_APPROVAL','ACTIVE','REJECTED') NOT NULL,
            joined         DATE         NOT NULL,
            signature_text VARCHAR(160) NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS signature_images (
            hash CHAR(64)    NOT NULL PRIMARY KEY,
            data MEDIUMBLOB  NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS user_signatures (
            user_id    VARCHAR(16)  NOT NULL,
            slot       INT          NOT NULL,
            name       VARCHAR(160) NOT NULL,
            image_hash CHAR(64)     NOT NULL,
            PRIMARY KEY (user_id, slot),
            CONSTRAINT fk_usersig_user  FOREIGN KEY (user_id)    REFERENCES users(id) ON DELETE CASCADE,
            CONSTRAINT fk_usersig_image FOREIGN KEY (image_hash) REFERENCES signature_images(hash)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS documents (
            id             VARCHAR(24)   NOT NULL PRIMARY KEY,
            title          VARCHAR(255)  NOT NULL,
            type           VARCHAR(40)   NOT NULL,
            description    TEXT          NULL,
            owner_id       VARCHAR(16)   NOT NULL,
            current_step   INT           NOT NULL DEFAULT 0,
            revision_step  INT           NOT NULL DEFAULT 0,
            status         ENUM('PENDING_REVIEW','APPROVED','REVISION_REQUESTED','REJECTED') NOT NULL,
            submitted      DATETIME      NOT NULL,
            amount         DECIMAL(14,2) NOT NULL DEFAULT 0,
            recipient      VARCHAR(200)  NULL,
            owner_sig_hash CHAR(64)      NULL,
            owner_x        DOUBLE        NOT NULL DEFAULT 0,
            owner_y        DOUBLE        NOT NULL DEFAULT 0,
            owner_w        DOUBLE        NOT NULL DEFAULT 0,
            owner_page     INT           NOT NULL DEFAULT 0,
            signed_pdf     VARCHAR(500)  NULL,
            CONSTRAINT fk_doc_owner FOREIGN KEY (owner_id)       REFERENCES users(id),
            CONSTRAINT fk_doc_sig   FOREIGN KEY (owner_sig_hash) REFERENCES signature_images(hash)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS document_reviewers (
            doc_id  VARCHAR(24) NOT NULL,
            step_no INT         NOT NULL,
            user_id VARCHAR(16) NOT NULL,
            PRIMARY KEY (doc_id, step_no),
            CONSTRAINT fk_rev_doc  FOREIGN KEY (doc_id)  REFERENCES documents(id) ON DELETE CASCADE,
            CONSTRAINT fk_rev_user FOREIGN KEY (user_id) REFERENCES users(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS attachments (
            id     INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
            doc_id VARCHAR(24)  NOT NULL,
            name   VARCHAR(255) NOT NULL,
            path   VARCHAR(500) NULL,
            CONSTRAINT fk_att_doc FOREIGN KEY (doc_id) REFERENCES documents(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS approvals (
            id           INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
            doc_id       VARCHAR(24)  NOT NULL,
            step_no      INT          NOT NULL,
            user_id      VARCHAR(16)  NOT NULL,
            comment_text TEXT         NULL,
            stamp        CHAR(64)     NOT NULL,
            sig_data     VARCHAR(400) NOT NULL,
            ts_string    VARCHAR(40)  NOT NULL,
            approved_at  DATETIME     NOT NULL,
            active       BOOLEAN      NOT NULL DEFAULT TRUE,
            sig_hash     CHAR(64)     NULL,
            px           DOUBLE       NOT NULL DEFAULT 0,
            py           DOUBLE       NOT NULL DEFAULT 0,
            pw           DOUBLE       NOT NULL DEFAULT 0,
            placed       BOOLEAN      NOT NULL DEFAULT FALSE,
            page         INT          NOT NULL DEFAULT 0,
            CONSTRAINT fk_appr_doc  FOREIGN KEY (doc_id)   REFERENCES documents(id) ON DELETE CASCADE,
            CONSTRAINT fk_appr_user FOREIGN KEY (user_id)  REFERENCES users(id),
            CONSTRAINT fk_appr_sig  FOREIGN KEY (sig_hash) REFERENCES signature_images(hash)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS audit_ledger (
            seq        INT          NOT NULL UNIQUE,
            id         VARCHAR(16)  NOT NULL PRIMARY KEY,
            category   VARCHAR(30)  NOT NULL,
            title      VARCHAR(120) NOT NULL,
            by_name    VARCHAR(160) NOT NULL,
            actor_id   VARCHAR(16)  NULL,
            ref        VARCHAR(40)  NULL,
            hash       CHAR(64)     NULL,
            note       TEXT         NULL,
            prev_hash  VARCHAR(64)  NOT NULL,
            entry_hash CHAR(64)     NOT NULL,
            created_at DATETIME     NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        DROP TRIGGER IF EXISTS audit_ledger_no_update""",
        """
        CREATE TRIGGER audit_ledger_no_update BEFORE UPDATE ON audit_ledger FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_ledger is append-only'""",
        """
        DROP TRIGGER IF EXISTS audit_ledger_no_delete""",
        """
        CREATE TRIGGER audit_ledger_no_delete BEFORE DELETE ON audit_ledger FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_ledger is append-only'""",
    };

    private static Connection conn;
    /** Hashes of signature PNGs already stored, so the same image is never sent twice. */
    private static final Set<String> knownImages = new HashSet<>();

    private static synchronized Connection conn() throws SQLException {
        if (conn == null || conn.isClosed() || !conn.isValid(2)) conn = Database.getConnection();
        return conn;
    }

    // ------------------------------------------------------------------ setup
    static synchronized void ensureSchema() throws SQLException {
        Database.ensureDatabase();
        Connection c = conn();
        try (Statement st = c.createStatement()) {
            for (String sql : SCHEMA) {
                try { st.executeUpdate(sql); }
                catch (SQLException e) {
                    if (sql.startsWith("CREATE TRIGGER") || sql.startsWith("DROP TRIGGER")) {
                        // the ledger still works without the extra database-level protection
                        System.err.println("Could not create ledger trigger: " + e.getMessage());
                    } else throw e;
                }
            }
        }
    }

    static synchronized boolean hasUsers() throws SQLException {
        try (Statement st = conn().createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /** Empties every table (used by "Reset demo data"). */
    static synchronized void wipe() throws SQLException {
        Connection c = conn();
        try (Statement st = c.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String t : new String[]{"approvals", "attachments", "document_reviewers", "documents",
                    "user_signatures", "signature_images", "users", "audit_ledger", "meta"})
                st.execute("TRUNCATE TABLE " + t);           // TRUNCATE is not blocked by the ledger triggers
            st.execute("SET FOREIGN_KEY_CHECKS=1");
        }
        knownImages.clear();
    }

    // ------------------------------------------------------------------ load
    static synchronized Store load() throws SQLException {
        Connection c = conn();
        Store s = new Store();
        Map<String, byte[]> images = new HashMap<>();
        Map<String, Doc> docs = new HashMap<>();
        Map<String, User> users = new HashMap<>();

        try (Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT hash, data FROM signature_images")) {
                while (rs.next()) images.put(rs.getString(1), rs.getBytes(2));
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM users ORDER BY id")) {
                while (rs.next()) {
                    User u = new User();
                    u.id = rs.getString("id"); u.name = rs.getString("name"); u.email = rs.getString("email");
                    u.passHash = rs.getString("pass_hash"); u.department = rs.getString("department");
                    u.position = rs.getString("position"); u.role = Role.valueOf(rs.getString("role"));
                    String rr = rs.getString("requested_role");
                    u.requestedRole = rr == null ? null : Role.valueOf(rr);
                    u.status = UserStatus.valueOf(rs.getString("status"));
                    u.joined = rs.getObject("joined", LocalDate.class);
                    u.signatureText = rs.getString("signature_text");
                    u.library = new ArrayList<>();
                    s.users.add(u);
                    users.put(u.id, u);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT user_id, name, image_hash FROM user_signatures ORDER BY user_id, slot")) {
                while (rs.next()) {
                    User u = users.get(rs.getString(1));
                    byte[] png = images.get(rs.getString(3));
                    if (u != null && png != null) u.library.add(new SigAsset(rs.getString(2), png));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM documents ORDER BY submitted, id")) {
                while (rs.next()) {
                    Doc d = new Doc();
                    d.id = rs.getString("id"); d.title = rs.getString("title"); d.type = rs.getString("type");
                    d.description = rs.getString("description"); d.ownerId = rs.getString("owner_id");
                    d.currentStep = rs.getInt("current_step"); d.revisionStep = rs.getInt("revision_step");
                    d.status = DocStatus.valueOf(rs.getString("status"));
                    d.submitted = rs.getObject("submitted", LocalDateTime.class);
                    d.amount = rs.getDouble("amount"); d.recipient = rs.getString("recipient");
                    String h = rs.getString("owner_sig_hash");
                    d.ownerSig = h == null ? null : images.get(h);
                    d.ownerX = rs.getDouble("owner_x"); d.ownerY = rs.getDouble("owner_y");
                    d.ownerW = rs.getDouble("owner_w"); d.ownerPage = rs.getInt("owner_page");
                    d.signedPdf = rs.getString("signed_pdf");
                    s.docs.add(d);
                    docs.put(d.id, d);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT doc_id, user_id FROM document_reviewers ORDER BY doc_id, step_no")) {
                while (rs.next()) { Doc d = docs.get(rs.getString(1)); if (d != null) d.reviewers.add(rs.getString(2)); }
            }
            try (ResultSet rs = st.executeQuery("SELECT doc_id, name, path FROM attachments ORDER BY id")) {
                while (rs.next()) { Doc d = docs.get(rs.getString(1)); if (d != null) d.attachments.add(new Attachment(rs.getString(2), rs.getString(3))); }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM approvals ORDER BY id")) {
                while (rs.next()) {
                    Doc d = docs.get(rs.getString("doc_id"));
                    if (d == null) continue;
                    Approval a = new Approval();
                    a.step = rs.getInt("step_no"); a.userId = rs.getString("user_id");
                    a.comment = rs.getString("comment_text"); a.stamp = rs.getString("stamp");
                    a.sigData = rs.getString("sig_data"); a.tsString = rs.getString("ts_string");
                    a.time = rs.getObject("approved_at", LocalDateTime.class);
                    a.active = rs.getBoolean("active");
                    String h = rs.getString("sig_hash");
                    a.sigImage = h == null ? null : images.get(h);
                    a.px = rs.getDouble("px"); a.py = rs.getDouble("py"); a.pw = rs.getDouble("pw");
                    a.placed = rs.getBoolean("placed"); a.page = rs.getInt("page");
                    d.approvals.add(a);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM audit_ledger ORDER BY seq")) {
                while (rs.next()) {
                    AuditEntry e = new AuditEntry();
                    e.id = rs.getString("id"); e.category = rs.getString("category"); e.title = rs.getString("title");
                    e.by = rs.getString("by_name"); e.actorId = rs.getString("actor_id"); e.ref = rs.getString("ref");
                    e.hash = rs.getString("hash"); e.note = rs.getString("note");
                    e.prevHash = rs.getString("prev_hash"); e.entryHash = rs.getString("entry_hash");
                    e.time = rs.getObject("created_at", LocalDateTime.class);
                    s.ledger.add(e);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT k, v FROM meta")) {
                while (rs.next()) {
                    if (rs.getString(1).equals("docSeq")) s.docSeq = Integer.parseInt(rs.getString(2));
                    if (rs.getString(1).equals("userSeq")) s.userSeq = Integer.parseInt(rs.getString(2));
                }
            }
        }
        knownImages.clear();
        knownImages.addAll(images.keySet());
        s.ledgerSaved = s.ledger.size();
        return s;
    }

    // ------------------------------------------------------------------ save
    /** Writes the whole in-memory state in one transaction. New ledger rows are appended, never rewritten. */
    static synchronized void sync(Store s) throws SQLException {
        Connection c = conn();
        Set<String> pending = new HashSet<>();
        try {
            c.setAutoCommit(false);
            writeAll(c, s, pending);
            c.commit();
        } catch (SQLException | RuntimeException e) {
            try { c.rollback(); } catch (SQLException ignored) { }
            throw e;
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) { }
        }
        knownImages.addAll(pending);
        s.ledgerSaved = s.ledger.size();
    }

    private static String image(PreparedStatement ins, byte[] png, Set<String> pending) throws SQLException {
        if (png == null) return null;
        String h = Store.sha256(png);
        if (!knownImages.contains(h) && pending.add(h)) {
            ins.setString(1, h);
            ins.setBytes(2, png);
            ins.executeUpdate();
        }
        return h;
    }

    private static String cut(String s, int max) { return s == null || s.length() <= max ? s : s.substring(0, max); }

    private static void writeAll(Connection c, Store s, Set<String> pending) throws SQLException {
        try (PreparedStatement img = c.prepareStatement("INSERT IGNORE INTO signature_images (hash, data) VALUES (?, ?)")) {
            // ---- users
            try (PreparedStatement up = c.prepareStatement(
                    "INSERT INTO users (id, name, email, pass_hash, department, `position`, role, requested_role, status, joined, signature_text) "
                  + "VALUES (?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name), email=VALUES(email), "
                  + "pass_hash=VALUES(pass_hash), department=VALUES(department), `position`=VALUES(`position`), role=VALUES(role), "
                  + "requested_role=VALUES(requested_role), status=VALUES(status), joined=VALUES(joined), signature_text=VALUES(signature_text)")) {
                for (User u : s.users) {
                    up.setString(1, u.id); up.setString(2, u.name); up.setString(3, u.email); up.setString(4, u.passHash);
                    up.setString(5, u.department); up.setString(6, u.position); up.setString(7, u.role.name());
                    up.setString(8, u.requestedRole == null ? null : u.requestedRole.name());
                    up.setString(9, u.status.name()); up.setObject(10, u.joined); up.setString(11, u.signatureText);
                    up.executeUpdate();
                }
            }
            // ---- signature libraries
            try (PreparedStatement del = c.prepareStatement("DELETE FROM user_signatures WHERE user_id = ?");
                 PreparedStatement ins = c.prepareStatement("INSERT INTO user_signatures (user_id, slot, name, image_hash) VALUES (?,?,?,?)")) {
                for (User u : s.users) {
                    if (u.library == null) continue;            // library not created yet; nothing to change
                    del.setString(1, u.id);
                    del.executeUpdate();
                    int slot = 0;
                    for (SigAsset a : u.library) {
                        String h = image(img, a.png, pending);
                        ins.setString(1, u.id); ins.setInt(2, slot++); ins.setString(3, cut(a.name, 160)); ins.setString(4, h);
                        ins.executeUpdate();
                    }
                }
            }
            // ---- documents and their children
            try (PreparedStatement up = c.prepareStatement(
                    "INSERT INTO documents (id, title, type, description, owner_id, current_step, revision_step, status, submitted, amount, "
                  + "recipient, owner_sig_hash, owner_x, owner_y, owner_w, owner_page, signed_pdf) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                  + "ON DUPLICATE KEY UPDATE title=VALUES(title), type=VALUES(type), description=VALUES(description), owner_id=VALUES(owner_id), "
                  + "current_step=VALUES(current_step), revision_step=VALUES(revision_step), status=VALUES(status), submitted=VALUES(submitted), "
                  + "amount=VALUES(amount), recipient=VALUES(recipient), owner_sig_hash=VALUES(owner_sig_hash), owner_x=VALUES(owner_x), "
                  + "owner_y=VALUES(owner_y), owner_w=VALUES(owner_w), owner_page=VALUES(owner_page), signed_pdf=VALUES(signed_pdf)");
                 PreparedStatement delRev = c.prepareStatement("DELETE FROM document_reviewers WHERE doc_id = ?");
                 PreparedStatement delAtt = c.prepareStatement("DELETE FROM attachments WHERE doc_id = ?");
                 PreparedStatement delApp = c.prepareStatement("DELETE FROM approvals WHERE doc_id = ?");
                 PreparedStatement insRev = c.prepareStatement("INSERT INTO document_reviewers (doc_id, step_no, user_id) VALUES (?,?,?)");
                 PreparedStatement insAtt = c.prepareStatement("INSERT INTO attachments (doc_id, name, path) VALUES (?,?,?)");
                 PreparedStatement insApp = c.prepareStatement(
                         "INSERT INTO approvals (doc_id, step_no, user_id, comment_text, stamp, sig_data, ts_string, approved_at, active, "
                       + "sig_hash, px, py, pw, placed, page) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (Doc d : s.docs) {
                    String ownerHash = image(img, d.ownerSig, pending);
                    up.setString(1, d.id); up.setString(2, d.title); up.setString(3, d.type); up.setString(4, d.description);
                    up.setString(5, d.ownerId); up.setInt(6, d.currentStep); up.setInt(7, d.revisionStep);
                    up.setString(8, d.status.name()); up.setObject(9, d.submitted); up.setDouble(10, d.amount);
                    up.setString(11, d.recipient); up.setString(12, ownerHash); up.setDouble(13, d.ownerX);
                    up.setDouble(14, d.ownerY); up.setDouble(15, d.ownerW); up.setInt(16, d.ownerPage); up.setString(17, d.signedPdf);
                    up.executeUpdate();

                    delRev.setString(1, d.id); delRev.executeUpdate();
                    delAtt.setString(1, d.id); delAtt.executeUpdate();
                    delApp.setString(1, d.id); delApp.executeUpdate();
                    for (int i = 0; i < d.reviewers.size(); i++) {
                        insRev.setString(1, d.id); insRev.setInt(2, i); insRev.setString(3, d.reviewers.get(i));
                        insRev.executeUpdate();
                    }
                    for (Attachment a : d.attachments) {
                        insAtt.setString(1, d.id); insAtt.setString(2, cut(a.name, 255)); insAtt.setString(3, a.path);
                        insAtt.executeUpdate();
                    }
                    for (Approval a : d.approvals) {
                        String h = image(img, a.sigImage, pending);
                        insApp.setString(1, d.id); insApp.setInt(2, a.step); insApp.setString(3, a.userId);
                        insApp.setString(4, a.comment); insApp.setString(5, a.stamp); insApp.setString(6, a.sigData);
                        insApp.setString(7, a.tsString); insApp.setObject(8, a.time); insApp.setBoolean(9, a.active);
                        insApp.setString(10, h); insApp.setDouble(11, a.px); insApp.setDouble(12, a.py);
                        insApp.setDouble(13, a.pw); insApp.setBoolean(14, a.placed); insApp.setInt(15, a.page);
                        insApp.executeUpdate();
                    }
                }
            }
        }
        // ---- ledger: append only
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO audit_ledger (seq, id, category, title, by_name, actor_id, ref, hash, note, prev_hash, entry_hash, created_at) "
              + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (int i = s.ledgerSaved; i < s.ledger.size(); i++) {
                AuditEntry e = s.ledger.get(i);
                ins.setInt(1, i + 1); ins.setString(2, e.id); ins.setString(3, e.category); ins.setString(4, cut(e.title, 120));
                ins.setString(5, cut(e.by, 160)); ins.setString(6, e.actorId); ins.setString(7, e.ref); ins.setString(8, e.hash);
                ins.setString(9, e.note); ins.setString(10, e.prevHash); ins.setString(11, e.entryHash); ins.setObject(12, e.time);
                ins.executeUpdate();
            }
        }
        // ---- counters
        try (PreparedStatement m = c.prepareStatement("INSERT INTO meta (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)")) {
            m.setString(1, "docSeq"); m.setString(2, String.valueOf(s.docSeq)); m.executeUpdate();
            m.setString(1, "userSeq"); m.setString(2, String.valueOf(s.userSeq)); m.executeUpdate();
        }
    }
}