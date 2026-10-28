package com.student.signit;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Screens and helpers shared between roles. */
final class Common {
    private Common() { }

    static String date(java.time.LocalDateTime t) { return t.format(Store.DATE); }

    // ------------------------------------------------------------------ document card (overview / lists)
    static JComponent docCard(Doc d, Runnable open) {
        Ui.Round c = Ui.card();
        c.setBorder(new EmptyBorder(18, 22, 18, 22));
        int n = d.reviewers.size();
        Ui.Flex top = new Ui.Flex(new BorderLayout());
        Ui.Flex left = Ui.vbox();
        left.add(Ui.mono(d.id, 11, Ui.PRIMARY));
        left.add(Ui.gap(4));
        left.add(Ui.label(d.title, Font.BOLD, 14.5f, Ui.DARK));
        left.add(Ui.gap(4));
        left.add(Ui.small("Step " + d.displayStep() + " of " + n + " \u00b7 " + date(d.submitted)));
        top.add(left, BorderLayout.CENTER);
        Ui.Flex right = Ui.hbox();
        right.add(Ui.statusChip(d.status));
        right.add(Ui.hgap(10));
        right.add(new Ui.Ico("chev", 14, Ui.MUTED));
        top.add(Ui.top(right), BorderLayout.EAST);
        c.add(top);
        c.add(Ui.gap(14));
        int done = d.status == DocStatus.APPROVED ? n : d.currentStep;
        c.add(new Ui.Segments(n, done, d.status == DocStatus.PENDING_REVIEW ? d.currentStep : -1));
        Ui.clickable(c, open);
        return c;
    }

    static void openAttachment(Component parent, Attachment a) {
        if (a.path == null) {
            Main.info(parent, a.name + " is a demo placeholder, so there is no file stored for it.\n"
                    + "Files you attach in \"Submit Form\" are copied to ~/.signit/attachments and open from here.");
            return;
        }
        try { Desktop.getDesktop().open(new File(a.path)); }
        catch (Exception e) { Main.info(parent, "Could not open " + a.name + " on this system."); }
    }

    static JComponent attachmentChips(Component parent, Doc d) {
        Ui.Flex row = new Ui.Flex(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (Attachment a : d.attachments) {
            Ui.Round chip = new Ui.Round(Ui.SOFT, null, 12);
            chip.setLayout(new BoxLayout(chip, BoxLayout.X_AXIS));
            chip.setBorder(new EmptyBorder(8, 12, 8, 14));
            chip.add(new Ui.Ico("clip", 14, Ui.PRIMARY));
            chip.add(Ui.hgap(7));
            chip.add(Ui.label(a.name, Font.PLAIN, 12.5f, Ui.DARK));
            Ui.clickable(chip, () -> openAttachment(parent, a));
            row.add(chip);
        }
        if (d.attachments.isEmpty()) row.add(Ui.small("No attachments"));
        return row;
    }

    // ------------------------------------------------------------------ document detail (applicant / admin view)
    static JComponent detail(Shell sh, Doc d, String backKey, String backText) {
        Store st = Store.get();
        User me = sh.user;
        Ui.VPanel p = Ui.page();

        Ui.Btn back = new Ui.Btn("\u2039  " + backText, Ui.BStyle.LINK);
        back.padH = 0; back.padV = 2;
        back.addActionListener(e -> sh.go(backKey));
        p.add(back);
        p.add(Ui.gap(14));

        Ui.Flex head = new Ui.Flex(new BorderLayout());
        Ui.Flex hl = Ui.vbox();
        hl.add(Ui.mono(d.id, 11.5f, Ui.PRIMARY));
        hl.add(Ui.gap(3));
        hl.add(Ui.label(d.title, Font.BOLD, 22, Ui.DARK));
        hl.add(Ui.gap(4));
        hl.add(Ui.sub("<html><body style='width:640px'>" + escape(d.description) + "</body></html>"));
        head.add(hl, BorderLayout.CENTER);
        head.add(Ui.top(Ui.statusChip(d.status)), BorderLayout.EAST);
        p.add(head);
        p.add(Ui.gap(18));

        // document preview with every placed signature
        Ui.Round prev = Ui.card();
        prev.add(Ui.section("Document preview"));
        prev.add(Ui.gap(12));
        SignPaper view = SignPaper.forDoc(d, st);
        prev.add(SignUi.pager(view));
        prev.add(Ui.gap(8));
        prev.add(SignUi.centered(view));
        File signed = d.signedPdf == null ? null : new File(d.signedPdf);
        if (signed != null && signed.isFile()) {
            prev.add(Ui.gap(12));
            Ui.Btn open = Ui.btn("Open signed PDF", Ui.BStyle.SOFT, () -> {
                try { Desktop.getDesktop().open(signed); }
                catch (Exception e) { Main.info(sh, "Could not open the signed PDF on this system.\nIt is saved at:\n" + signed); }
            });
            prev.add(open);
        }
        p.add(prev);
        p.add(Ui.gap(16));

        // feedback + resubmit for the owner
        if (me.id.equals(d.ownerId) && (d.status == DocStatus.REVISION_REQUESTED || d.status == DocStatus.REJECTED)) {
            String fb = "";
            String by = "";
            for (AuditEntry e : st.trail(d))
                if (e.title.startsWith("Revision") || e.title.startsWith("Step-Back") || e.title.startsWith("Rejected")) {
                    fb = e.note == null ? "" : e.note;
                    by = e.by;
                }
            Ui.Round warn = new Ui.Round(new Color(0xFFF7ED), new Color(0xFED7AA), 20);
            warn.setLayout(new BoxLayout(warn, BoxLayout.Y_AXIS));
            warn.setBorder(new EmptyBorder(18, 24, 18, 24));
            warn.add(Ui.label(d.status == DocStatus.REJECTED ? "REJECTED \u2014 PROGRESS RESET TO STEP 1" : "REVISION REQUESTED", Font.BOLD, 11, new Color(0xC2410C)));
            warn.add(Ui.gap(8));
            warn.add(Ui.label("<html><body style='width:640px'>" + escape(by.isEmpty() ? "" : by + ": ") + "\u201c" + escape(fb) + "\u201d</body></html>",
                    Font.PLAIN, 13, Ui.DARK));
            warn.add(Ui.gap(12));
            warn.add(Ui.btn("Edit & Resubmit", Ui.BStyle.PRIMARY, () -> resubmitDialog(sh, d)));
            p.add(warn);
            p.add(Ui.gap(16));
        }

        // approval pipeline
        Ui.Round pipe = Ui.card();
        pipe.add(Ui.section("Approval pipeline"));
        pipe.add(Ui.gap(14));
        for (int i = 0; i < d.reviewers.size(); i++) {
            User r = st.byId(d.reviewers.get(i));
            Approval a = d.approvalAt(i);
            boolean cur = i == d.currentStep && d.status != DocStatus.APPROVED && d.status != DocStatus.REJECTED;
            int mode = a != null ? 2 : cur ? 1 : 0;
            Ui.Flex row = new Ui.Flex(new BorderLayout(14, 0));
            row.add(Ui.top(new Ui.StepDot(i + 1, mode)), BorderLayout.WEST);
            Ui.Flex mid = Ui.vbox();
            mid.add(Ui.label(r.name, Font.BOLD, 13.5f, Ui.DARK));
            mid.add(Ui.small(r.position));
            if (a != null) {
                mid.add(Ui.gap(8));
                Ui.Round q = new Ui.Round(new Color(0xFFEEF5), Ui.BORDER, 12);
                q.setLayout(new BorderLayout());
                q.setBorder(new EmptyBorder(9, 14, 9, 14));
                q.add(Ui.label(a.comment == null || a.comment.isBlank() ? "Approved." : "\u201c" + a.comment + "\u201d", Font.PLAIN, 12, Ui.DARK));
                mid.add(q);
                mid.add(Ui.gap(6));
                Ui.Flex h = Ui.hbox();
                h.add(new Ui.Ico("lock", 11, Ui.MUTED));
                h.add(Ui.hgap(5));
                h.add(Ui.mono(a.stamp.substring(0, 12), 11, Ui.MUTED));
                mid.add(h);
            }
            row.add(mid, BorderLayout.CENTER);
            String lbl; String kind;
            if (a != null) { lbl = "Approved"; kind = "green"; }
            else if (cur) { lbl = "Awaiting"; kind = "yellow"; }
            else if (i == d.currentStep && d.status == DocStatus.REVISION_REQUESTED) { lbl = "Revision"; kind = "orange"; }
            else { lbl = "Queued"; kind = "gray"; }
            row.add(Ui.top(Ui.chip(lbl, kind)), BorderLayout.EAST);
            pipe.add(row);
            pipe.add(Ui.gap(16));
        }
        p.add(pipe);
        p.add(Ui.gap(16));

        // attachments
        Ui.Round att = Ui.card();
        att.add(Ui.section("Attachments"));
        att.add(Ui.gap(12));
        for (Attachment a : d.attachments) {
            Ui.Round r = new Ui.Round(new Color(0xFFEEF5), null, 12);
            r.setLayout(new BoxLayout(r, BoxLayout.X_AXIS));
            r.setBorder(new EmptyBorder(13, 16, 13, 16));
            r.add(new Ui.Ico("clip", 16, Ui.PRIMARY));
            r.add(Ui.hgap(10));
            r.add(Ui.label(a.name, Font.PLAIN, 13, Ui.DARK));
            Ui.clickable(r, () -> openAttachment(sh, a));
            att.add(r);
            att.add(Ui.gap(8));
        }
        if (d.attachments.isEmpty()) att.add(Ui.small("No attachments were uploaded."));
        p.add(att);
        p.add(Ui.gap(16));

        // audit trail
        Ui.Round trail = Ui.card();
        trail.add(Ui.section("Audit trail"));
        trail.add(Ui.gap(14));
        for (AuditEntry e : st.trail(d)) {
            Ui.Flex row = new Ui.Flex(new BorderLayout(10, 0));
            row.add(Ui.top(new Ui.Dot()), BorderLayout.WEST);
            Ui.Flex mid = Ui.vbox();
            mid.add(Ui.label(e.title, Font.BOLD, 13, Ui.DARK));
            mid.add(Ui.small("by " + e.by));
            if (e.hash != null) mid.add(Ui.mono("SHA-256: " + e.hash.substring(0, 12) + "\u2026", 11, Ui.PRIMARY));
            if (e.note != null && !e.note.isBlank()) mid.add(Ui.label("\u201c" + e.note + "\u201d", Font.ITALIC, 11.5f, Ui.MUTED));
            row.add(mid, BorderLayout.CENTER);
            row.add(Ui.top(Ui.mono(e.time.format(Store.TS), 11, Ui.PRIMARY)), BorderLayout.EAST);
            trail.add(row);
            trail.add(Ui.gap(12));
        }
        p.add(trail);
        return p;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void resubmitDialog(Shell sh, Doc d) {
        Ui.Area desc = new Ui.Area("Update the description", 4);
        desc.setText(d.description);
        Ui.Area note = new Ui.Area("Note to reviewers (what did you change?)", 3);
        List<File> files = new ArrayList<>();
        JLabel fl = Ui.small("No new files attached.");
        Ui.Flex f = Ui.vbox();
        f.setBorder(new EmptyBorder(6, 6, 6, 6));
        f.setPreferredSize(new Dimension(460, 360));
        f.add(Ui.field("Description", desc));
        f.add(Ui.field("Add supporting documents", Ui.dropZone("Click to upload or drag & drop", l -> {
            files.addAll(l);
            fl.setText(files.size() + " new file(s): " + names(files));
        })));
        f.add(fl);
        f.add(Ui.gap(12));
        f.add(Ui.field("Note", note));
        if (JOptionPane.showConfirmDialog(sh, f, "Edit & Resubmit " + d.id, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            Store.get().resubmit(d, sh.user, desc.getText().trim(), files, note.getText().trim());
            Main.info(sh, d.id + " was resubmitted and is back in the reviewer's queue.");
            sh.refresh();
        }
    }

    static String names(List<File> files) {
        StringBuilder sb = new StringBuilder();
        for (File f : files) sb.append(sb.length() == 0 ? "" : ", ").append(f.getName());
        return sb.toString();
    }

    // ------------------------------------------------------------------ profile & e-signature vault
    static JComponent profile(Shell sh) {
        Store st = Store.get();
        User u = sh.user;
        Ui.VPanel p = Ui.page();
        p.add(Ui.h1("Profile & Signatures"));
        p.add(Ui.gap(20));

        Ui.Round c = Ui.card();
        Ui.Flex top = Ui.hbox();
        top.add(new Ui.Avatar(u.initials(), 62));
        top.add(Ui.hgap(18));
        Ui.Flex who = Ui.vbox();
        who.add(Ui.label(u.name, Font.BOLD, 17, Ui.DARK));
        who.add(Ui.label(u.email, Font.PLAIN, 12.5f, Ui.MUTED));
        who.add(Ui.label(u.position + " \u00b7 " + u.role.label, Font.PLAIN, 11.5f, Ui.LABEL));
        top.add(who);
        c.add(top);
        c.add(Ui.gap(20));
        JPanel grid = new JPanel(new GridLayout(2, 2, 20, 14));
        grid.setOpaque(false);
        grid.setAlignmentX(0f);
        grid.add(kv("User ID", u.id));
        grid.add(kv("Department", u.department));
        grid.add(kv("Role", u.role == Role.STUDENT ? "Student / Applicant" : u.role == Role.FACULTY ? "Faculty / Approver" : "Super Admin"));
        grid.add(kv("Status", u.status == UserStatus.ACTIVE ? "Active" : "Pending"));
        c.add(grid);
        if (u.role == Role.STUDENT || u.role == Role.FACULTY) {
            c.add(Ui.gap(16));
            if (u.requestedRole != null) {
                c.add(Ui.chip("Role elevation to " + u.requestedRole.label + " is pending Super Admin approval", "yellow"));
            } else if (u.role == Role.STUDENT) {
                c.add(Ui.btn("Request Faculty role", Ui.BStyle.SOFT, () -> {
                    if (JOptionPane.showConfirmDialog(sh, "Send a role elevation request to the Super Admin?\nYour access stays the same until it is approved.",
                            "Role elevation", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                        st.requestElevation(u, Role.FACULTY);
                        sh.refresh();
                    }
                }));
            }
        }
        p.add(c);
        p.add(Ui.gap(16));

        Ui.Round v = Ui.card();
        v.add(Ui.section("Signature library"));
        v.add(Ui.gap(8));
        v.add(Ui.sub("<html><body style='width:620px'>Save your signatures as PNG images. When you sign a document, drag one onto the page "
                + "(or click it). Each signature is sealed into the document with a SHA-256 stamp.</body></html>"));
        v.add(Ui.gap(14));
        JPanel sigGrid = new JPanel(new GridLayout(0, 3, 14, 14));
        sigGrid.setOpaque(false);
        List<SigAsset> lib = st.sigs(u);
        for (int i = 0; i < lib.size(); i++) sigGrid.add(sigTile(sh, st, u, lib.get(i), i == 0, lib.size() > 1));
        Ui.Flex gw = new Ui.Flex(new BorderLayout());
        gw.add(sigGrid, BorderLayout.NORTH);
        v.add(gw);
        v.add(Ui.gap(16));
        Ui.Flex acts = Ui.hbox();
        acts.add(Ui.btn("Upload PNG", Ui.BStyle.PRIMARY, () -> {
            SigAsset a = SigTools.upload(sh);
            if (a != null) { st.addSignature(u, a.name, a.png); sh.refresh(); }
        }));
        acts.add(Ui.hgap(10));
        acts.add(Ui.btn("Draw signature", Ui.BStyle.SOFT, () -> {
            byte[] png = SigTools.draw(sh);
            if (png != null) { st.addSignature(u, "Drawn signature " + (st.sigs(u).size() + 1), png); sh.refresh(); }
        }));
        acts.add(Box.createHorizontalGlue());
        v.add(acts);
        p.add(v);
        return p;
    }

    private static JComponent sigTile(Shell sh, Store st, User u, SigAsset a, boolean isDefault, boolean canDelete) {
        Ui.Round t = new Ui.Round(Color.WHITE, Ui.BORDER, 16);
        t.setLayout(new BorderLayout(0, 8));
        t.setBorder(new EmptyBorder(10, 10, 10, 10));
        t.add(new SigPreview(a.png, 200, 84), BorderLayout.CENTER);
        Ui.Flex foot = Ui.vbox();
        foot.add(Ui.label(a.name, Font.BOLD, 12, Ui.DARK));
        Ui.Flex r = Ui.hbox();
        if (isDefault) r.add(Ui.chip("Default", "green"));
        else {
            Ui.Btn d = Ui.btn("Make default", Ui.BStyle.LINK, () -> { st.makeDefaultSignature(u, a); sh.refresh(); });
            d.padH = 0; d.padV = 2; d.setFont(Ui.font(Font.PLAIN, 12));
            r.add(d);
        }
        r.add(Box.createHorizontalGlue());
        if (canDelete) {
            Ui.Btn del = Ui.btn("Delete", Ui.BStyle.LINK, () -> {
                if (JOptionPane.showConfirmDialog(sh, "Delete \"" + a.name + "\" from your library?\nDocuments already signed keep their signature.",
                        "Delete signature", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                    st.deleteSignature(u, a);
                    sh.refresh();
                }
            });
            del.padH = 0; del.padV = 2; del.setFont(Ui.font(Font.PLAIN, 12));
            r.add(del);
        }
        foot.add(r);
        t.add(foot, BorderLayout.SOUTH);
        return t;
    }

    private static JComponent kv(String k, String v) {
        Ui.Flex f = Ui.vbox();
        f.add(Ui.small(k));
        f.add(Ui.gap(3));
        f.add(Ui.label(v, Font.BOLD, 13, Ui.DARK));
        return f;
    }
}