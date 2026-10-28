package com.student.signit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/** Faculty / approver screens. */
final class FacultyPages {
    private FacultyPages() { }

    static JComponent inbox(Shell sh) {
        Store st = Store.get();
        Ui.VPanel p = Ui.page();
        List<Doc> docs = st.inbox(sh.user);
        p.add(Ui.h1("Inbox Queue"));
        p.add(Ui.gap(4));
        p.add(Ui.sub(docs.size() == 0 ? "You're all caught up \u2014 nothing is waiting for your review."
                : docs.size() + " document" + (docs.size() == 1 ? "" : "s") + " awaiting your review."));
        p.add(Ui.gap(22));
        for (Doc d : docs) {
            User owner = st.byId(d.ownerId);
            Ui.Round c = Ui.card();
            Ui.Flex top = new Ui.Flex(new BorderLayout());
            Ui.Flex l = Ui.vbox();
            Ui.Flex meta = Ui.hbox();
            meta.add(Ui.mono(d.id, 11, Ui.PRIMARY));
            meta.add(Ui.hgap(10));
            meta.add(Ui.small("\u2022  " + d.type));
            l.add(meta);
            l.add(Ui.gap(5));
            l.add(Ui.label(d.title, Font.BOLD, 15.5f, Ui.DARK));
            l.add(Ui.gap(4));
            l.add(Ui.small("Submitted by " + owner.name + " \u00b7 " + Common.date(d.submitted)
                    + " \u00b7 Step " + d.displayStep() + " of " + d.reviewers.size()));
            top.add(l, BorderLayout.CENTER);
            top.add(Ui.top(Ui.chip("Pending Review", "yellow")), BorderLayout.EAST);
            c.add(top);
            c.add(Ui.gap(14));
            Ui.Round desc = new Ui.Round(new Color(0xFFEEF5), null, 12);
            desc.setLayout(new BorderLayout());
            desc.setBorder(new EmptyBorder(11, 14, 11, 14));
            desc.add(Ui.label(d.description, Font.PLAIN, 12.5f, Ui.DARK));
            c.add(desc);
            c.add(Ui.gap(14));
            c.add(Ui.btn("Open & Review \u2192", Ui.BStyle.PRIMARY, () -> sh.show(review(sh, d))));
            p.add(c);
            p.add(Ui.gap(14));
        }
        return p;
    }

    static JComponent history(Shell sh) {
        Store st = Store.get();
        Ui.VPanel p = Ui.page();
        p.add(Ui.h1("Action History"));
        p.add(Ui.gap(4));
        p.add(Ui.sub("Every decision you've made, with its cryptographic stamp."));
        p.add(Ui.gap(22));
        List<AuditEntry> hist = st.history(sh.user);
        if (hist.isEmpty()) p.add(Ui.sub("No actions yet."));
        for (AuditEntry e : hist) {
            Doc d = st.docById(e.ref);
            User owner = d == null ? null : st.byId(d.ownerId);
            Ui.Round c = Ui.card();
            c.setBorder(new EmptyBorder(16, 22, 16, 22));
            Ui.Flex top = new Ui.Flex(new BorderLayout());
            Ui.Flex l = Ui.vbox();
            l.add(Ui.mono(e.ref, 11, Ui.PRIMARY));
            l.add(Ui.gap(4));
            l.add(Ui.label(d == null ? e.ref : d.title, Font.BOLD, 14.5f, Ui.DARK));
            l.add(Ui.gap(3));
            l.add(Ui.small("by " + (owner == null ? "?" : owner.name) + " \u00b7 " + e.time.format(Store.TS)));
            if (e.hash != null) {
                l.add(Ui.gap(8));
                Ui.Flex h = Ui.hbox();
                h.add(new Ui.Ico("lock", 12, Ui.PRIMARY));
                h.add(Ui.hgap(6));
                h.add(Ui.mono("Your stamp: " + e.hash.substring(0, 16), 11.5f, Ui.PRIMARY));
                l.add(h);
            }
            if (e.note != null && !e.note.isBlank()) {
                l.add(Ui.gap(6));
                l.add(Ui.label("\u201c" + e.note + "\u201d", Font.ITALIC, 11.5f, Ui.MUTED));
            }
            top.add(l, BorderLayout.CENTER);
            String t = e.title;
            JComponent chip = t.contains("Approved") ? Ui.chip("Approved", "green")
                    : t.startsWith("Revision") ? Ui.chip("Revision Requested", "orange")
                    : t.startsWith("Step-Back") ? Ui.chip("Returned", "orange")
                    : Ui.chip("Rejected", "red");
            top.add(Ui.top(chip), BorderLayout.EAST);
            c.add(top);
            p.add(c);
            p.add(Ui.gap(12));
        }
        return p;
    }

    // ------------------------------------------------------------------ review screen
    static JComponent review(Shell sh, Doc d) {
        Store st = Store.get();
        User me = sh.user;
        Ui.VPanel p = Ui.page();
        Ui.Btn back = new Ui.Btn("\u2039  Back to inbox", Ui.BStyle.LINK);
        back.padH = 0; back.padV = 2;
        back.addActionListener(e -> sh.go("inbox"));
        p.add(back);
        p.add(Ui.gap(14));

        Ui.Flex head = new Ui.Flex(new BorderLayout());
        Ui.Flex hl = Ui.vbox();
        hl.add(Ui.mono(d.id, 11.5f, Ui.PRIMARY));
        hl.add(Ui.gap(3));
        hl.add(Ui.label(d.title, Font.BOLD, 22, Ui.DARK));
        hl.add(Ui.gap(3));
        hl.add(Ui.small("Submitted by " + st.byId(d.ownerId).name + " \u00b7 " + Common.date(d.submitted)));
        head.add(hl, BorderLayout.CENTER);
        head.add(Ui.top(Ui.statusChip(d.status)), BorderLayout.EAST);
        p.add(head);
        p.add(Ui.gap(18));

        // document details
        Ui.Round det = Ui.card();
        det.add(Ui.section("Document details"));
        det.add(Ui.gap(10));
        det.add(Ui.label("<html><body style='width:640px'>" + d.description.replace("<", "&lt;") + "</body></html>", Font.PLAIN, 13, Ui.DARK));
        if ("Budget Request".equals(d.type) && d.amount > 0) {
            det.add(Ui.gap(6));
            det.add(Ui.label("Requested amount: PHP " + String.format("%,.2f", d.amount), Font.BOLD, 12.5f, Ui.LABEL));
        }
        det.add(Ui.gap(12));
        det.add(Common.attachmentChips(sh, d));
        p.add(det);
        p.add(Ui.gap(16));

        // prior approvals
        Ui.Round prior = Ui.card();
        prior.add(Ui.section("Prior approvals"));
        prior.add(Ui.gap(12));
        boolean any = false;
        for (int i = 0; i < d.currentStep; i++) {
            Approval a = d.approvalAt(i);
            if (a == null) continue;
            any = true;
            User r = st.byId(a.userId);
            Ui.Flex row = new Ui.Flex(new BorderLayout(12, 0));
            row.add(Ui.top(new Ui.StepDot(i + 1, 2)), BorderLayout.WEST);
            Ui.Flex m = Ui.vbox();
            m.add(Ui.label(r.name, Font.BOLD, 13, Ui.DARK));
            m.add(Ui.mono("SHA-256: " + a.stamp.substring(0, 12), 11, Ui.MUTED));
            if (a.comment != null && !a.comment.isBlank()) m.add(Ui.label("\u201c" + a.comment + "\u201d", Font.ITALIC, 11.5f, Ui.MUTED));
            row.add(m, BorderLayout.CENTER);
            row.add(Ui.top(Ui.chip("Signed", "green")), BorderLayout.EAST);
            prior.add(row);
            prior.add(Ui.gap(10));
        }
        if (!any) prior.add(Ui.small("You are the first reviewer in this pipeline."));
        p.add(prior);
        p.add(Ui.gap(16));

        // e-signature: drag a saved PNG onto the page
        SignPaper paper = SignPaper.forDoc(d, st);
        Ui.Round sig = Ui.card();
        sig.add(Ui.section("Sign the document"));
        sig.add(Ui.gap(12));
        sig.add(SigTray.build(st, me, paper, () -> sh.go("profile")));
        sig.add(Ui.gap(14));
        sig.add(SignUi.pager(paper));
        sig.add(Ui.gap(8));
        sig.add(SignUi.centered(paper));
        p.add(sig);
        p.add(Ui.gap(16));
        Ui.Btn approve = new Ui.Btn("\u2713 Approve & Forward", Ui.BStyle.PRIMARY);
        approve.setEnabled(false);
        paper.addListener(() -> approve.setEnabled(paper.hasSignature()));

        // feedback
        Ui.Area fb = new Ui.Area("Add contextual notes or feedback (required for rejection)...", 3);
        Ui.Round fc = Ui.card();
        fc.add(Ui.section("Feedback / comments"));
        fc.add(Ui.gap(12));
        fc.add(fb);
        p.add(fc);
        p.add(Ui.gap(16));

        // decision
        JLabel err = Ui.label(" ", Font.PLAIN, 12.5f, Ui.ERROR);
        Ui.Round dec = Ui.card();
        dec.add(Ui.section("Decision"));
        dec.add(Ui.gap(12));
        Ui.Flex r1 = Ui.hbox();
        approve.addActionListener(e -> {
            String stamp = st.approve(d, me, fb.getText().trim(), paper.png(), paper.cx(), paper.cy(), paper.widthFraction(), paper.page());
            if (stamp == null) { Main.info(sh, "This document is no longer awaiting your action."); sh.go("inbox"); return; }
            String next = d.status == DocStatus.APPROVED ? "The document is now FULLY APPROVED."
                    : "Auto-forwarded to " + st.byId(d.reviewers.get(d.currentStep)).name + ".";
            Main.info(sh, "Approved. " + next + "\n\nSHA-256 stamp:\n" + stamp);
            sh.go("inbox");
        });
        r1.add(approve);
        r1.add(Ui.hgap(10));
        r1.add(Ui.btn("\u21a9 Request Revision", Ui.BStyle.WARN, () -> {
            if (needFeedback(fb, err)) return;
            st.requestRevision(d, me, fb.getText().trim());
            Main.info(sh, "Revision requested. The applicant has been notified.");
            sh.go("inbox");
        }));
        r1.add(Box.createHorizontalGlue());
        dec.add(r1);
        dec.add(Ui.gap(14));
        dec.add(Ui.small("Rejection options:"));
        dec.add(Ui.gap(6));
        Ui.Flex r2 = Ui.hbox();
        String stepLabel = d.currentStep == 0 ? "Step-Back Reject (return to applicant)" : "Step-Back Reject (return to previous)";
        r2.add(Ui.btn(stepLabel, Ui.BStyle.DANGER, () -> {
            if (needFeedback(fb, err)) return;
            st.stepBack(d, me, fb.getText().trim());
            Main.info(sh, d.currentStep == 0 && d.status == DocStatus.REVISION_REQUESTED
                    ? "Returned to the applicant for revision."
                    : "Returned to " + st.byId(d.reviewers.get(d.currentStep)).name + " for minor fixes.");
            sh.go("inbox");
        }).small());
        r2.add(Ui.hgap(8));
        r2.add(Ui.btn("Full Reset (back to Step 1)", Ui.BStyle.DANGER, () -> {
            if (needFeedback(fb, err)) return;
            if (JOptionPane.showConfirmDialog(sh, "This cancels ALL approval progress and drops the document back to Step 1.\nContinue?",
                    "Full reset", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            st.fullReset(d, me, fb.getText().trim());
            Main.info(sh, "The document was rejected and reset to Step 1.");
            sh.go("inbox");
        }).small());
        r2.add(Box.createHorizontalGlue());
        dec.add(r2);
        dec.add(Ui.gap(10));
        dec.add(err);
        p.add(dec);
        return p;
    }

    private static boolean needFeedback(Ui.Area fb, JLabel err) {
        if (fb.getText().isBlank()) {
            err.setText("Feedback is required to request a revision or reject a document.");
            fb.requestFocusInWindow();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ letters
    static JComponent letters(Shell sh) {
        Store st = Store.get();
        Ui.VPanel p = Ui.page();
        Ui.Flex head = new Ui.Flex(new BorderLayout());
        Ui.Flex hl = Ui.vbox();
        hl.add(Ui.h1("Letters"));
        hl.add(Ui.gap(4));
        hl.add(Ui.sub("Write an official letter, drag your signature onto it, and issue it."));
        head.add(hl, BorderLayout.CENTER);
        head.add(Ui.top(Ui.btn("+ Write a letter", Ui.BStyle.PRIMARY, () -> sh.show(compose(sh)))), BorderLayout.EAST);
        p.add(head);
        p.add(Ui.gap(22));
        int n = 0;
        for (Doc d : st.docsOf(sh.user)) {
            if (!"Letter".equals(d.type)) continue;
            n++;
            p.add(Common.docCard(d, () -> sh.show(Common.detail(sh, d, "letters", "Back to letters"))));
            p.add(Ui.gap(12));
        }
        if (n == 0) p.add(Ui.sub("You haven't written any letters yet."));
        return p;
    }

    static JComponent compose(Shell sh) {
        Store st = Store.get();
        User me = sh.user;
        Ui.VPanel p = Ui.page();
        Ui.Btn back = new Ui.Btn("\u2039  Back to letters", Ui.BStyle.LINK);
        back.padH = 0; back.padV = 2;
        back.addActionListener(e -> sh.go("letters"));
        p.add(back);
        p.add(Ui.gap(10));
        p.add(Ui.h1("Write a Letter"));
        p.add(Ui.gap(4));
        p.add(Ui.sub("The page on the right updates as you type. Drag your signature onto it, then issue the letter."));
        p.add(Ui.gap(20));

        Ui.Field to = new Ui.Field("e.g. The Registrar's Office");
        Ui.Field subject = new Ui.Field("e.g. Certification of Enrollment");
        Ui.Area body = new Ui.Area("Write the body of the letter here\u2026", 9);
        SignPaper paper = new SignPaper();
        String date = Common.date(java.time.LocalDateTime.now());
        Runnable sync = () -> paper.setContent(true, subject.getText().trim(), date, to.getText().trim(), body.getText());
        sync.run();
        SignUi.onChange(to, sync);
        SignUi.onChange(subject, sync);
        SignUi.onChange(body, sync);

        Ui.Round form = Ui.card();
        form.add(Ui.section("Letter"));
        form.add(Ui.gap(12));
        form.add(Ui.field("To", to));
        form.add(Ui.field("Subject", subject));
        form.add(Ui.field("Body", body));
        form.add(SigTray.build(st, me, paper, () -> sh.go("profile")));
        form.add(Ui.gap(14));
        JLabel err = Ui.label(" ", Font.PLAIN, 12.5f, Ui.ERROR);
        form.add(err);
        form.add(Ui.gap(4));
        form.add(Ui.btn("Sign & Issue Letter", Ui.BStyle.PRIMARY, () -> {
            if (subject.getText().isBlank()) { err.setText("Please enter a subject."); return; }
            if (body.getText().isBlank()) { err.setText("Please write the body of the letter."); return; }
            if (!paper.hasSignature()) { err.setText("Drag one of your signatures onto the letter first."); return; }
            Doc d = st.issueLetter(me, subject.getText().trim(), to.getText().trim(), body.getText().trim(),
                    paper.png(), paper.cx(), paper.cy(), paper.widthFraction());
            Main.info(sh, "Letter " + d.id + " was signed and issued.");
            sh.show(Common.detail(sh, d, "letters", "Back to letters"));
        }));

        Ui.Flex wrap = new Ui.Flex(new BorderLayout(24, 0));
        wrap.add(form, BorderLayout.CENTER);
        Ui.Flex right = new Ui.Flex(new BorderLayout());
        right.add(paper, BorderLayout.NORTH);
        wrap.add(right, BorderLayout.EAST);
        p.add(wrap);
        return p;
    }
}