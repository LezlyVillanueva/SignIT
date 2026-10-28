package com.student.signit;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Roles for role-based access control (RBAC). */
enum Role {
    STUDENT("Student"), FACULTY("Faculty"), ADMIN("Super Admin");
    final String label;
    Role(String label) { this.label = label; }
}

enum UserStatus { PENDING_APPROVAL, ACTIVE, REJECTED }

/** Document lifecycle states shown as live badges. */
enum DocStatus {
    PENDING_REVIEW("Pending Review"), APPROVED("Approved"),
    REVISION_REQUESTED("Revision Requested"), REJECTED("Rejected");
    final String label;
    DocStatus(String label) { this.label = label; }
}

class User implements Serializable {
    private static final long serialVersionUID = 1L;
    String id, name, email, passHash, department, position;
    Role role;
    Role requestedRole;            // pending role-elevation request (null if none)
    UserStatus status;
    LocalDate joined;
    String signatureText;          // typed signature (rendered in italics)
    byte[] signatureImage;         // legacy single signature image (migrated into the library)
    List<SigAsset> library;        // saved signature PNGs (null in old data files; use Store.sigs)

    String initials() {
        String[] p = name.trim().split("\\s+");
        String s = p[0].substring(0, 1);
        if (p.length > 1) s += p[1].substring(0, 1);
        return s.toUpperCase();
    }

    /** A short human-friendly first name ("Prof. James Rivera" -> "James"). */
    String firstName() {
        String[] p = name.trim().split("\\s+");
        if (p.length > 1 && p[0].endsWith(".")) return p[1];
        return p[0];
    }

    @Override public String toString() { return name; }
}

/** One saved signature PNG in a user's library. */
class SigAsset implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;
    byte[] png;
    SigAsset(String name, byte[] png) { this.name = name; this.png = png; }
}

class Attachment implements Serializable {
    private static final long serialVersionUID = 1L;
    String name, path;             // path == null for demo placeholders
    Attachment(String name, String path) { this.name = name; this.path = path; }
}

/** One reviewer decision with its SHA-256 stamp. */
class Approval implements Serializable {
    private static final long serialVersionUID = 1L;
    int step;
    String userId, comment, stamp, sigData, tsString;
    LocalDateTime time;
    boolean active = true;         // false once invalidated by a step-back / full reset
    byte[] sigImage;               // the PNG the reviewer dropped on the document
    double px, py, pw;             // placement: centre x/y and width, as fractions of the page width/height
    boolean placed;
    int page;                      // 0-based PDF page the signature was dropped on
}

class Doc implements Serializable {
    private static final long serialVersionUID = 1L;
    String id, title, type, description, ownerId;
    List<String> reviewers = new ArrayList<>();
    int currentStep, revisionStep;
    DocStatus status = DocStatus.PENDING_REVIEW;
    LocalDateTime submitted;
    double amount;
    String recipient;              // letters only
    byte[] ownerSig;               // optional applicant/author signature dropped on the page
    double ownerX, ownerY, ownerW;
    int ownerPage;
    String signedPdf;              // path of the PDF copy with all signatures stamped in (null if none)
    List<Attachment> attachments = new ArrayList<>();
    List<Approval> approvals = new ArrayList<>();

    Approval approvalAt(int step) {
        for (Approval a : approvals) if (a.active && a.step == step) return a;
        return null;
    }

    /** Step number shown to people ("Step 2 of 3"). */
    int displayStep() {
        return status == DocStatus.APPROVED ? reviewers.size() : currentStep + 1;
    }
}

/** An append-only, hash-chained ledger record. */
class AuditEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    String id, category, title, by, actorId, ref, hash, note, prevHash, entryHash;
    LocalDateTime time;
}