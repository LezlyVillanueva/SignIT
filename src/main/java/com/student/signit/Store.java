package com.student.signit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Central data store + workflow (state machine) engine + immutable audit ledger.
 * Data lives in MySQL (see DbStore / schema.sql). If MySQL cannot be reached at start-up,
 * the app falls back to Java serialization in ~/.signit/data.ser for that session.
 */
final class Store implements Serializable {
    private static final long serialVersionUID = 1L;

    static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("M/d/yyyy, h:mm:ss a", Locale.ENGLISH);
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH);
    static final Path DIR = Paths.get(System.getProperty("user.home"), ".signit");
    static final Path FILE = DIR.resolve("data.ser");
    static final double BUDGET_THRESHOLD = 50000;   // conditional routing rule
    static final String DEMO_PASSWORD = "password";

    private static Store inst;

    final List<User> users = new ArrayList<>();
    final List<Doc> docs = new ArrayList<>();
    final List<AuditEntry> ledger = new ArrayList<>();
    int docSeq, userSeq;
    transient User current;
    transient int ledgerSaved;                    // ledger rows already written to MySQL
    static boolean dbMode;                        // true while MySQL is the storage
    private static boolean dbWarned;

    // ---------------------------------------------------------------- lifecycle
    static Store get() {
        if (inst == null) inst = load();
        return inst;
    }

    private static Store load() {
        try {
            DbStore.ensureSchema();
            dbMode = true;
            if (DbStore.hasUsers()) return DbStore.load();
            Store s = readFile();                       // empty database: import an old data.ser, otherwise seed demo data
            if (s != null) {
                for (User u : s.users) s.fixLibrary(u);
                DbStore.sync(s);
                try { Files.move(FILE, DIR.resolve("data.ser.imported"), StandardCopyOption.REPLACE_EXISTING); }
                catch (IOException ignored) { }
                return s;
            }
            s = new Store();
            s.seed();
            return s;
        } catch (Exception e) {
            dbMode = false;
            e.printStackTrace();
            notifyUser("Could not use MySQL (" + e.getMessage() + ").\nSignIT will keep its data in a local file for this session.\n\n"
                    + "Check that MySQL is running and that the settings in Database.java are correct.");
        }
        Store s = readFile();
        if (s != null) return s;
        s = new Store();
        s.seed();
        return s;
    }

    private static Store readFile() {
        if (Files.exists(FILE)) {
            try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(FILE))) {
                return (Store) in.readObject();
            } catch (Exception ignored) { /* fall through */ }
        }
        return null;
    }

    private static void notifyUser(String msg) {
        System.err.println(msg);
        if (!java.awt.GraphicsEnvironment.isHeadless())
            javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(null, msg,
                    "SignIT database", javax.swing.JOptionPane.WARNING_MESSAGE));
    }

    static void resetDemoData() {
        try { Files.deleteIfExists(FILE); } catch (IOException ignored) { }
        try { Files.deleteIfExists(DIR.resolve("data.ser.imported")); } catch (IOException ignored) { }
        if (dbMode) {
            try { DbStore.wipe(); } catch (Exception e) { e.printStackTrace(); notifyUser("Could not clear MySQL: " + e.getMessage()); }
        }
        inst = null;
        get();
    }

    /** Writes everything to MySQL (or to the local file when MySQL is not in use). */
    void save() {
        if (dbMode) {
            try {
                DbStore.sync(this);
                dbWarned = false;
            } catch (Exception e) {
                e.printStackTrace();
                if (!dbWarned) {
                    dbWarned = true;
                    notifyUser("Could not save to MySQL: " + e.getMessage()
                            + "\n\nYour changes are kept in memory and will be saved on the next action once MySQL is back.\n"
                            + "Do not close SignIT until it saves.");
                }
            }
            return;
        }
        try {
            Files.createDirectories(DIR);
            try (ObjectOutputStream o = new ObjectOutputStream(Files.newOutputStream(FILE))) {
                o.writeObject(this);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ---------------------------------------------------------------- hashing
    static String sha256(String s) { return sha256(s.getBytes(StandardCharsets.UTF_8)); }

    static String sha256(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static String sigData(User u) {
        return u.signatureImage != null ? "IMG:" + sha256(u.signatureImage) : "TXT:" + u.signatureText;
    }

    static String stamp(String userId, String docId, String sigData, String ts) {
        return sha256(userId + docId + sigData + ts);   // Hash = SHA-256(UserID + DocumentID + SignatureData + Timestamp)
    }

    // ---------------------------------------------------------------- users
    User byId(String id) {
        for (User u : users) if (u.id.equals(id)) return u;
        return null;
    }

    User byEmail(String email) {
        for (User u : users) if (u.email.equalsIgnoreCase(email.trim())) return u;
        return null;
    }

    List<User> reviewers() {
        List<User> l = new ArrayList<>();
        for (User u : users) if (u.role == Role.FACULTY && u.status == UserStatus.ACTIVE) l.add(u);
        return l;
    }

    List<User> pendingUsers() {
        List<User> l = new ArrayList<>();
        for (User u : users) if (u.status == UserStatus.PENDING_APPROVAL || u.requestedRole != null) l.add(u);
        return l;
    }

    private User addUser(String name, String email, Role role, String dept, String position,
                         UserStatus st, LocalDate joined) {
        User u = new User();
        u.id = String.format("U%03d", ++userSeq);
        u.name = name; u.email = email; u.role = role; u.department = dept; u.position = position;
        u.status = st; u.joined = joined; u.passHash = sha256(DEMO_PASSWORD); u.signatureText = name;
        users.add(u);
        return u;
    }

    /** @return error message or null on success. */
    String login(String email, String pass) {
        User u = byEmail(email == null ? "" : email);
        if (u == null || !u.passHash.equals(sha256(pass))) {
            log("Auth", "Failed Login Attempt", email == null || email.isBlank() ? "unknown" : email.trim(), null, null, null, null);
            return "Invalid email or password.";
        }
        if (u.status == UserStatus.PENDING_APPROVAL) return "Your account is awaiting Super Admin verification.";
        if (u.status == UserStatus.REJECTED) return "Your registration was rejected by the Super Admin.";
        current = u;
        log("Auth", "User Login", u, null, null, null);
        return null;
    }

    /** @return error message or null on success. */
    String register(String name, String email, String pass, Role role, String dept) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) return "Please fill in name, email and password.";
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return "Please enter a valid email address.";
        if (pass.length() < 6) return "Password must be at least 6 characters.";
        if (byEmail(email) != null) return "That email is already registered.";
        User u = addUser(name.trim(), email.trim(), role, dept.isBlank() ? "Unassigned" : dept.trim(),
                role.label, UserStatus.PENDING_APPROVAL, LocalDate.now());
        u.passHash = sha256(pass);
        log("Auth", "User Registration", u, null, null, "Requested role: " + role.label);
        save();
        return null;
    }

    void requestElevation(User u, Role r) {
        u.requestedRole = r;
        log("Auth", "Role Elevation Request", u, null, null, "Requested role: " + r.label);
        save();
    }

    void decideUser(User admin, User target, boolean approve) {
        if (target.status == UserStatus.PENDING_APPROVAL) {
            target.status = approve ? UserStatus.ACTIVE : UserStatus.REJECTED;
            log("Admin", approve ? "Registration Approved" : "Registration Rejected", admin, target.email, null, null);
        } else if (target.requestedRole != null) {
            if (approve) {
                target.role = target.requestedRole;
                target.position = target.role.label;
            }
            log("Admin", approve ? "Role Elevation Approved" : "Role Elevation Rejected", admin, target.email, null,
                    "Requested role: " + target.requestedRole.label);
            target.requestedRole = null;
        }
        save();
    }

    // ---------------------------------------------------------------- signature library
    /** The user's saved signature PNGs. The first entry is the default. Creates a typed one if the library is empty. */
    List<SigAsset> sigs(User u) {
        if (fixLibrary(u)) save();
        return u.library;
    }

    /** Makes sure the library exists, migrates an old single image, and guarantees one entry. True if anything changed. */
    private boolean fixLibrary(User u) {
        boolean dirty = false;
        if (u.library == null) { u.library = new ArrayList<>(); dirty = true; }
        if (u.signatureImage != null) {                         // migrate the old single-image signature
            u.library.add(0, new SigAsset("Uploaded signature", u.signatureImage));
            u.signatureImage = null;
            dirty = true;
        }
        if (u.library.isEmpty()) {
            u.library.add(new SigAsset("Typed signature", SigTools.typedPng(u.signatureText == null ? u.name : u.signatureText)));
            dirty = true;
        }
        return dirty;
    }

    void addSignature(User u, String name, byte[] png) {
        sigs(u).add(new SigAsset(name, png));
        log("Signatures", "Signature Added to Library", u, null, null, name);
        save();
    }

    void deleteSignature(User u, SigAsset a) {
        List<SigAsset> l = sigs(u);
        if (l.size() <= 1) return;                              // always keep at least one
        l.remove(a);
        log("Signatures", "Signature Removed from Library", u, null, null, a.name);
        save();
    }

    void makeDefaultSignature(User u, SigAsset a) {
        List<SigAsset> l = sigs(u);
        if (l.remove(a)) l.add(0, a);
        save();
    }

    // ---------------------------------------------------------------- ledger
    AuditEntry log(String cat, String title, User u, String ref, String hash, String note) {
        return log(cat, title, u.name, u.id, ref, hash, note);
    }

    AuditEntry log(String cat, String title, String by, String actorId, String ref, String hash, String note) {
        AuditEntry e = new AuditEntry();
        e.id = String.format("SYS%03d", ledger.size() + 1);
        e.category = cat; e.title = title; e.by = by; e.actorId = actorId; e.ref = ref; e.hash = hash; e.note = note;
        e.time = LocalDateTime.now().withNano(0);
        e.prevHash = ledger.isEmpty() ? "GENESIS" : ledger.get(ledger.size() - 1).entryHash;
        e.entryHash = entryHash(e);
        ledger.add(e);
        save();
        return e;
    }

    private static String entryHash(AuditEntry e) {
        return sha256(String.join("|", e.prevHash, e.id, e.category, e.title, String.valueOf(e.by),
                String.valueOf(e.actorId), String.valueOf(e.ref), String.valueOf(e.hash),
                String.valueOf(e.note), e.time.format(TS)));
    }

    /** Recomputes the whole hash chain; false means a record was altered. */
    boolean verifyChain() {
        String prev = "GENESIS";
        for (AuditEntry e : ledger) {
            if (!e.prevHash.equals(prev) || !e.entryHash.equals(entryHash(e))) return false;
            prev = e.entryHash;
        }
        return true;
    }

    /** @return {kind, message} where kind is ok / bad / none. */
    String[] verifyHash(String prefix) {
        prefix = prefix.trim().toLowerCase();
        if (prefix.length() < 6) return new String[]{"none", "Enter at least 6 characters of the SHA-256 stamp."};
        for (Doc d : docs)
            for (Approval a : d.approvals)
                if (a.stamp.startsWith(prefix)) {
                    User u = byId(a.userId);
                    String re = stamp(a.userId, d.id, a.sigData, a.tsString);
                    String who = u == null ? a.userId : u.name;
                    if (re.equals(a.stamp))
                        return new String[]{"ok", "VERIFIED. Stamp issued to " + who + " for " + d.id + " (step "
                                + (a.step + 1) + ") at " + a.tsString + ". Recomputed hash matches the stored record"
                                + (a.active ? "." : " (this approval was later superseded by a reset/step-back).")};
                    return new String[]{"bad", "MISMATCH. The stamp exists but the recomputed hash differs; the record may have been tampered with."};
                }
        for (AuditEntry e : ledger)
            if (e.hash != null && e.hash.startsWith(prefix))
                return new String[]{"ok", "Found in ledger entry " + e.id + " (" + e.title + " by " + e.by + ")."};
        return new String[]{"none", "No ledger record matches that hash prefix."};
    }

    // ---------------------------------------------------------------- documents
    List<Doc> docsOf(User u) {
        List<Doc> l = new ArrayList<>();
        for (Doc d : docs) if (d.ownerId.equals(u.id)) l.add(d);
        l.sort((a, b) -> b.submitted.compareTo(a.submitted));
        return l;
    }

    List<Doc> inbox(User u) {
        List<Doc> l = new ArrayList<>();
        for (Doc d : docs)
            if (d.status == DocStatus.PENDING_REVIEW && d.reviewers.get(d.currentStep).equals(u.id)) l.add(d);
        return l;
    }

    int count(User u, DocStatus s) {
        int n = 0;
        for (Doc d : docs) if (d.ownerId.equals(u.id) && d.status == s) n++;
        return n;
    }

    static boolean isDecision(AuditEntry e) {
        return e.actorId != null && e.ref != null && e.ref.startsWith("DOC")
                && (e.category.equals("Signatures") || (e.category.equals("Workflow") && !e.title.equals("Document Fully Approved")));
    }

    List<AuditEntry> history(User u) {
        List<AuditEntry> l = new ArrayList<>();
        for (AuditEntry e : ledger) if (u.id.equals(e.actorId) && isDecision(e)) l.add(0, e);
        return l;
    }

    List<AuditEntry> trail(Doc d) {
        List<AuditEntry> l = new ArrayList<>();
        for (AuditEntry e : ledger)
            if (d.id.equals(e.ref) && !e.category.equals("Auth")) l.add(e);
        return l;
    }

    Doc docById(String id) {
        for (Doc d : docs) if (d.id.equals(id)) return d;
        return null;
    }

    private User financeOfficer() {
        for (User u : users) if ("Finance Officer".equals(u.position) && u.status == UserStatus.ACTIVE) return u;
        return null;
    }

    /** True when the conditional routing engine will add an extra review step. */
    boolean needsFinanceStep(String type, double amount) {
        return "Budget Request".equals(type) && amount > BUDGET_THRESHOLD && financeOfficer() != null;
    }

    Doc submit(User owner, String title, String type, String desc, double amount,
               List<File> files, List<String> reviewerIds) {
        List<String> rev = new ArrayList<>(reviewerIds);
        boolean added = false;
        if (needsFinanceStep(type, amount) && !rev.contains(financeOfficer().id)) {
            rev.add(financeOfficer().id);
            added = true;
        }
        Doc d = new Doc();
        d.id = String.format("DOC-%d-%03d", Year.now().getValue(), ++docSeq);
        d.title = title; d.type = type; d.description = desc; d.ownerId = owner.id; d.amount = amount;
        d.reviewers = rev;
        d.submitted = LocalDateTime.now().withNano(0);
        docs.add(d);
        addFiles(d, files);
        log("Documents", "Document Submitted", owner, d.id, null, null);
        if (added)
            log("Workflow", "Conditional Step Added", "System", null, d.id, null,
                    "Budget exceeds " + (long) BUDGET_THRESHOLD + "; Finance Officer review appended as step " + rev.size());
        save();
        return d;
    }

    void addFiles(Doc d, List<File> files) {
        if (files == null) return;
        for (File f : files) {
            try {
                Path dir = DIR.resolve("attachments").resolve(d.id);
                Files.createDirectories(dir);
                Path dest = dir.resolve(f.getName());
                Files.copy(f.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                d.attachments.add(new Attachment(f.getName(), dest.toString()));
            } catch (IOException e) {
                d.attachments.add(new Attachment(f.getName(), null));
            }
        }
    }

    void resubmit(Doc d, User owner, String newDesc, List<File> files, String note) {
        if (d.status != DocStatus.REVISION_REQUESTED && d.status != DocStatus.REJECTED) return;
        d.currentStep = d.status == DocStatus.REVISION_REQUESTED ? d.revisionStep : 0;
        d.status = DocStatus.PENDING_REVIEW;
        if (newDesc != null && !newDesc.isBlank()) d.description = newDesc;
        addFiles(d, files);
        log("Documents", "Document Resubmitted", owner, d.id, null, note == null || note.isBlank() ? null : note);
        log("Workflow", "Forwarded to Step " + (d.currentStep + 1), "System", null, d.id, null,
                "Now awaiting " + byId(d.reviewers.get(d.currentStep)).name);
    }

    // ---------------------------------------------------------------- PDF signing
    /** The first real (uploaded) PDF attached to the document, or null. */
    File originalPdf(Doc d) {
        for (Attachment a : d.attachments)
            if (a.path != null && a.name.toLowerCase().endsWith(".pdf") && new File(a.path).isFile()) return new File(a.path);
        return null;
    }

    /** The PDF to show: the signed copy if one exists, otherwise the original upload. */
    File currentPdf(Doc d) {
        if (d.signedPdf != null && new File(d.signedPdf).isFile()) return new File(d.signedPdf);
        return originalPdf(d);
    }

    /** Re-creates the signed copy from the untouched original: applicant signature + every active approval. */
    void rebuildSigned(Doc d) {
        File orig = originalPdf(d);
        if (orig == null) return;
        List<PdfTools.Stamp> stamps = new ArrayList<>();
        User owner = byId(d.ownerId);
        if (d.ownerSig != null)
            stamps.add(new PdfTools.Stamp(d.ownerSig, d.ownerPage, d.ownerX, d.ownerY, d.ownerW,
                    "Signed by " + (owner == null ? "applicant" : owner.name) + " (applicant) | " + d.submitted.format(TS)));
        for (Approval a : d.approvals)
            if (a.active && a.placed && a.sigImage != null) {
                User u = byId(a.userId);
                stamps.add(new PdfTools.Stamp(a.sigImage, a.page, a.px, a.py, a.pw,
                        "Signed by " + (u == null ? a.userId : u.name) + " | " + a.tsString + " | SHA-256 " + a.stamp.substring(0, 12)));
            }
        try {
            if (stamps.isEmpty()) {
                if (d.signedPdf != null) Files.deleteIfExists(Paths.get(d.signedPdf));
                d.signedPdf = null;
                return;
            }
            Path dir = DIR.resolve("attachments").resolve(d.id).resolve("signed");
            Files.createDirectories(dir);
            Path dest = dir.resolve(orig.getName().replaceAll("(?i)\\.pdf$", "") + "-signed.pdf");
            PdfTools.stampAll(orig, dest.toFile(), stamps);
            d.signedPdf = dest.toString();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ---------------------------------------------------------------- workflow engine
    private boolean canAct(Doc d, User u) {
        return d.status == DocStatus.PENDING_REVIEW && d.reviewers.get(d.currentStep).equals(u.id);
    }

    /** Sequential auto-forwarding with the typed signature (used by seed data). */
    String approve(Doc d, User u, String comment) {
        return approve(d, u, comment, null, 0, 0, 0, 0);
    }

    /**
     * Signs, stamps, and advances to the next reviewer's queue.
     * @param png the signature PNG dropped on the page (null = typed name); px/py = centre, pw = width as page fractions;
     *            page = 0-based PDF page it was dropped on
     */
    String approve(Doc d, User u, String comment, byte[] png, double px, double py, double pw, int page) {
        if (!canAct(d, u)) return null;
        int step = d.currentStep;
        Approval a = new Approval();
        a.step = step; a.userId = u.id; a.comment = comment;
        if (png != null) { a.sigImage = png; a.px = px; a.py = py; a.pw = pw; a.page = page; a.placed = true; }
        a.time = LocalDateTime.now().withNano(0);
        a.tsString = a.time.format(TS);
        a.sigData = png != null ? "IMG:" + sha256(png) : sigData(u);
        File orig = originalPdf(d);
        if (png != null && orig != null) {                       // bind the stamp to the exact PDF that was signed
            try { a.sigData += "|PDF:" + sha256(Files.readAllBytes(orig.toPath())); } catch (IOException ignored) { }
        }
        a.stamp = stamp(u.id, d.id, a.sigData, a.tsString);
        d.approvals.add(a);
        if (png != null) rebuildSigned(d);
        log("Signatures", "Step " + (step + 1) + " Approved", u, d.id, a.stamp, comment);
        if (step == d.reviewers.size() - 1) {
            d.status = DocStatus.APPROVED;
            log("Workflow", "Document Fully Approved", u, d.id, a.stamp, null);
        } else {
            d.currentStep++;
            log("Workflow", "Forwarded to Step " + (d.currentStep + 1), "System", null, d.id, null,
                    "Now awaiting " + byId(d.reviewers.get(d.currentStep)).name);
        }
        save();
        return a.stamp;
    }

    /** Applicant / author signature dropped on the page when the document is created. */
    void setOwnerSig(Doc d, byte[] png, double x, double y, double w, int page) {
        d.ownerSig = png; d.ownerX = x; d.ownerY = y; d.ownerW = w; d.ownerPage = page;
        rebuildSigned(d);
        if (png != null) log("Signatures", "Applicant Signed", byId(d.ownerId), d.id, sha256(png), null);
        save();
    }

    /** A faculty letter: written by the author, signed on the page, issued immediately. */
    Doc issueLetter(User author, String subject, String recipient, String body, byte[] png,
                    double px, double py, double pw) {
        Doc d = new Doc();
        d.id = String.format("DOC-%d-%03d", Year.now().getValue(), ++docSeq);
        d.title = subject; d.type = "Letter"; d.description = body; d.ownerId = author.id; d.recipient = recipient;
        d.reviewers.add(author.id);
        d.submitted = LocalDateTime.now().withNano(0);
        docs.add(d);
        log("Documents", "Letter Created", author, d.id, null, recipient == null || recipient.isBlank() ? null : "To: " + recipient);
        approve(d, author, "Signed and issued.", png, px, py, pw, 0);
        return d;
    }

    void requestRevision(Doc d, User u, String comment) {
        if (!canAct(d, u)) return;
        d.status = DocStatus.REVISION_REQUESTED;
        d.revisionStep = d.currentStep;
        log("Workflow", "Revision Requested", u, d.id, null, comment);
        save();
    }

    /** Step-back rejection: invalidates the previous approval and returns to the prior reviewer. */
    void stepBack(Doc d, User u, String comment) {
        if (!canAct(d, u)) return;
        if (d.currentStep == 0) {                      // nobody before step 1 except the applicant
            requestRevision(d, u, comment);
            return;
        }
        Approval prev = d.approvalAt(d.currentStep - 1);
        if (prev != null) prev.active = false;
        d.currentStep--;
        rebuildSigned(d);
        log("Workflow", "Step-Back Reject", u, d.id, null,
                comment + "  [Returned to " + byId(d.reviewers.get(d.currentStep)).name + "]");
        save();
    }

    /** Full reset: cancels all progress and drops the document back to Step 1 as rejected. */
    void fullReset(Doc d, User u, String comment) {
        if (!canAct(d, u)) return;
        for (Approval a : d.approvals) a.active = false;
        d.currentStep = 0;
        rebuildSigned(d);
        d.status = DocStatus.REJECTED;
        log("Workflow", "Rejected (Full Reset)", u, d.id, null, comment);
        save();
    }

    // ---------------------------------------------------------------- demo data
    private void seed() {
        LocalDate j = LocalDate.of(2026, 8, 1);
        User maria = addUser("Maria Santos", "student@signit.edu", Role.STUDENT, "Computer Science", "Student", UserStatus.ACTIVE, j);
        User james = addUser("Prof. James Rivera", "faculty@signit.edu", Role.FACULTY, "Computer Science", "Adviser", UserStatus.ACTIVE, j);
        addUser("Dr. Elena Cruz", "admin@signit.edu", Role.ADMIN, "IT Services", "Super Admin", UserStatus.ACTIVE, j);
        User lorna = addUser("Dr. Lorna Bautista", "lorna@signit.edu", Role.FACULTY, "Student Affairs", "Dean of Students", UserStatus.ACTIVE, j);
        User rafael = addUser("Atty. Rafael Morales", "rafael@signit.edu", Role.FACULTY, "Registrar's Office", "Registrar", UserStatus.ACTIVE, j);
        addUser("Mr. Paolo Dizon", "paolo@signit.edu", Role.FACULTY, "Finance Office", "Finance Officer", UserStatus.ACTIVE, j);
        addUser("Carlos Delos Reyes", "carlos.dr@signit.edu", Role.STUDENT, "Engineering", "Student", UserStatus.ACTIVE, LocalDate.of(2026, 8, 1));
        addUser("Isabel Tan", "isabel.tan@signit.edu", Role.FACULTY, "Mathematics", "Faculty", UserStatus.ACTIVE, LocalDate.of(2026, 7, 15));
        addUser("Jose Domingo", "jose.d@signit.edu", Role.STUDENT, "Nursing", "Student", UserStatus.ACTIVE, LocalDate.of(2026, 8, 20));
        addUser("Carla Reyes", "carla.r@signit.edu", Role.STUDENT, "Computer Science", "Student", UserStatus.ACTIVE, LocalDate.of(2026, 9, 1));
        addUser("Miguel Reyes", "miguel.r@signit.edu", Role.STUDENT, "Business Admin", "Student", UserStatus.PENDING_APPROVAL, LocalDate.of(2026, 9, 9));
        addUser("Ana Villanueva", "ana.v@signit.edu", Role.FACULTY, "Physics", "Faculty", UserStatus.PENDING_APPROVAL, LocalDate.of(2026, 9, 10));

        List<String> trio = Arrays.asList(james.id, lorna.id, rafael.id);

        Doc d1 = submit(maria, "Academic Clearance Form \u2014 Graduation", "Clearance",
                "Clearance form required for graduation ceremony participation and transcript release.", 0, null, trio);
        d1.attachments.add(new Attachment("transcript.pdf", null));
        d1.attachments.add(new Attachment("enrollment_cert.pdf", null));

        Doc d2 = submit(maria, "Student Organization Event Permit", "Event Permit",
                "Permit request for the annual Computer Science Society Tech Fair.", 0, null, trio);
        d2.attachments.add(new Attachment("event_proposal.pdf", null));

        Doc d3 = submit(maria, "Research Grant Budget Justification", "Budget Request",
                "Budget justification for the undergraduate research grant (equipment and participant incentives).",
                35000, null, trio);
        d3.attachments.add(new Attachment("budget_breakdown.xlsx", null));

        approve(d1, james, "Approved. All academic records are in order.");
        approve(d2, james, "Approved. Event aligns with student activity guidelines.");
        approve(d2, lorna, "Approved. Venue and date confirmed.");
        approve(d2, rafael, "Approved. Records cleared.");
        requestRevision(d3, james, "Please itemize the equipment costs and attach supplier quotations.");
        save();
    }
}