package com.student.signit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Student / applicant screens. */
final class StudentPages {
    private StudentPages() { }

    private static JComponent stat(String num, String label, Color iconBg, Color numColor) {
        Ui.Round c = new Ui.Round(Color.WHITE, Ui.BORDER, 18);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new EmptyBorder(18, 20, 18, 20));
        Ui.Round box = new Ui.Round(iconBg, null, 12) {
            @Override public Dimension getPreferredSize() { return new Dimension(40, 40); }
            @Override public Dimension getMaximumSize() { return new Dimension(40, 40); }
        };
        box.setLayout(new GridBagLayout());
        box.add(Ui.label(num, Font.BOLD, 16, numColor));
        c.add(box);
        c.add(Ui.gap(12));
        c.add(Ui.label(label, Font.PLAIN, 12, Ui.MUTED));
        return c;
    }

    static JComponent overview(Shell sh) {
        Store st = Store.get();
        User u = sh.user;
        Ui.VPanel p = Ui.page();
        int h = LocalTime.now().getHour();
        String greet = h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
        p.add(Ui.h1(greet + ", " + u.firstName()));
        p.add(Ui.gap(4));
        p.add(Ui.sub("Here are the latest updates on your submitted documents and approvals."));
        p.add(Ui.gap(24));

        JPanel stats = new JPanel(new GridLayout(1, 5, 16, 0));
        stats.setOpaque(false);
        stats.setAlignmentX(0f);
        stats.add(stat(String.valueOf(st.docsOf(u).size()), "Total Submitted", new Color(0xFCE4F0), Ui.PRIMARY));
        stats.add(stat(String.valueOf(st.count(u, DocStatus.PENDING_REVIEW)), "Pending Review", new Color(0xFFF6D9), new Color(0xD97706)));
        stats.add(stat(String.valueOf(st.count(u, DocStatus.APPROVED)), "Fully Approved", new Color(0xDDF8EC), new Color(0x059669)));
        stats.add(stat(String.valueOf(st.count(u, DocStatus.REVISION_REQUESTED)), "Revisions Needed", new Color(0xFFEBDD), new Color(0xEA580C)));
        stats.add(stat(String.valueOf(st.count(u, DocStatus.REJECTED)), "Rejected", new Color(0xFFE4E6), new Color(0xE11D48)));
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        p.add(stats);
        p.add(Ui.gap(30));

        p.add(Ui.label("RECENT SUBMISSIONS", Font.BOLD, 13, Ui.LABEL));
        p.add(Ui.gap(14));
        List<Doc> docs = st.docsOf(u);
        if (docs.isEmpty()) {
            Ui.Round e = Ui.card();
            e.add(Ui.label("No submissions yet", Font.BOLD, 14, Ui.DARK));
            e.add(Ui.gap(4));
            e.add(Ui.sub("Start a clearance request and watch it move through its approval pipeline."));
            e.add(Ui.gap(12));
            e.add(Ui.btn("Submit a form", Ui.BStyle.PRIMARY, () -> sh.go("submit")));
            p.add(e);
        }
        for (int i = 0; i < Math.min(5, docs.size()); i++) {
            Doc d = docs.get(i);
            p.add(Common.docCard(d, () -> sh.show(Common.detail(sh, d, "overview", "Back to overview"))));
            p.add(Ui.gap(12));
        }
        return p;
    }

    static JComponent myDocuments(Shell sh) {
        Store st = Store.get();
        User u = sh.user;
        Ui.VPanel p = Ui.page();
        p.add(Ui.h1("My Documents"));
        p.add(Ui.gap(4));
        p.add(Ui.sub("Every form you've submitted and where it is in its approval pipeline."));
        p.add(Ui.gap(20));

        Ui.Field search = new Ui.Field("Search by title or document ID");
        String[] opts = {"All statuses", "Pending Review", "Approved", "Revision Requested", "Rejected"};
        JComboBox<String> filter = Ui.combo(opts);
        Ui.Flex bar = new Ui.Flex(new BorderLayout(12, 0));
        bar.add(search, BorderLayout.CENTER);
        filter.setPreferredSize(new Dimension(200, 40));
        bar.add(filter, BorderLayout.EAST);
        p.add(bar);
        p.add(Ui.gap(16));

        Ui.Flex list = Ui.vbox();
        p.add(list);
        Runnable fill = () -> {
            list.removeAll();
            String q = search.getText().trim().toLowerCase();
            int f = filter.getSelectedIndex();
            int shown = 0;
            for (Doc d : st.docsOf(u)) {
                if (f > 0 && !d.status.label.equals(opts[f])) continue;
                if (!q.isEmpty() && !d.title.toLowerCase().contains(q) && !d.id.toLowerCase().contains(q)) continue;
                list.add(Common.docCard(d, () -> sh.show(Common.detail(sh, d, "documents", "Back to documents"))));
                list.add(Ui.gap(12));
                shown++;
            }
            if (shown == 0) list.add(Ui.sub("No documents match your filters."));
            list.revalidate();
            list.repaint();
        };
        fill.run();
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { fill.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { fill.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { fill.run(); }
        });
        filter.addActionListener(e -> fill.run());
        return p;
    }

    // ------------------------------------------------------------------ submit form
    static JComponent submit(Shell sh) {
        Store st = Store.get();
        User u = sh.user;
        Ui.VPanel p = Ui.page();
        p.add(Ui.h1("Submit New Form"));
        p.add(Ui.gap(4));
        p.add(Ui.sub("Define your document and approval pipeline. The system will auto-forward after each approval."));
        p.add(Ui.gap(20));

        // ---- document details
        Ui.Field title = new Ui.Field("e.g. Academic Clearance Form \u2014 Graduation");
        JComboBox<String> type = Ui.combo("Clearance", "Event Permit", "Budget Request", "Leave Request", "Other");
        Ui.Field amount = new Ui.Field("e.g. 35000");
        Ui.Flex amountRow = Ui.field("Requested Amount (PHP) \u2014 over " + String.format("%,d", (long) Store.BUDGET_THRESHOLD)
                + " triggers an extra Finance review", amount);
        amountRow.setVisible(false);
        Ui.Area desc = new Ui.Area("Brief description of what you're submitting and why.", 4);
        List<File> files = new ArrayList<>();
        Runnable[] pdfSync = new Runnable[1];
        Ui.Flex fileList = Ui.vbox();
        Runnable[] refreshFiles = new Runnable[1];
        refreshFiles[0] = () -> {
            fileList.removeAll();
            for (File f : new ArrayList<>(files)) {
                Ui.Round r = new Ui.Round(new Color(0xFFEEF5), null, 10);
                r.setLayout(new BorderLayout());
                r.setBorder(new EmptyBorder(8, 12, 8, 12));
                Ui.Flex l = Ui.hbox();
                l.add(new Ui.Ico("clip", 14, Ui.PRIMARY));
                l.add(Ui.hgap(8));
                l.add(Ui.label(f.getName(), Font.PLAIN, 12.5f, Ui.DARK));
                r.add(l, BorderLayout.CENTER);
                Ui.Btn rm = new Ui.Btn("Remove", Ui.BStyle.LINK);
                rm.padV = 0; rm.padH = 4; rm.setFont(Ui.font(Font.PLAIN, 12));
                rm.addActionListener(e -> { files.remove(f); refreshFiles[0].run(); });
                r.add(rm, BorderLayout.EAST);
                fileList.add(r);
                fileList.add(Ui.gap(6));
            }
            fileList.revalidate();
            fileList.repaint();
            if (pdfSync[0] != null) pdfSync[0].run();
        };

        Ui.Round c1 = Ui.card();
        c1.add(Ui.section("Document details"));
        c1.add(Ui.gap(14));
        c1.add(Ui.field("Document Title", title));
        c1.add(Ui.field("Document Type", type));
        c1.add(amountRow);
        c1.add(Ui.field("Description", desc));
        c1.add(Ui.label("Attach Supporting Documents", Font.BOLD, 12, new Color(0x7A1E4F)));
        c1.add(Ui.gap(6));
        c1.add(Ui.dropZone("Click to upload or drag & drop", l -> {
            for (File f : l) if (f.isFile() && files.stream().noneMatch(x -> x.equals(f))) files.add(f);
            refreshFiles[0].run();
        }));
        c1.add(Ui.gap(8));
        c1.add(fileList);
        p.add(c1);
        p.add(Ui.gap(16));

        // ---- approval pipeline
        List<User> pool = st.reviewers();
        User[] arr = pool.toArray(new User[0]);
        List<JComboBox<User>> combos = new ArrayList<>();
        Ui.Flex steps = Ui.vbox();
        JLabel notice = Ui.label(" ", Font.PLAIN, 12, new Color(0xB45309));

        Ui.Round c2 = Ui.card();
        c2.add(Ui.section("Approval pipeline"));
        c2.add(Ui.gap(6));
        c2.add(Ui.sub("Define the ordered sequence of reviewers. The document auto-advances upon each approval."));
        c2.add(Ui.gap(14));
        c2.add(steps);

        Runnable addStep = () -> {
            if (combos.size() >= 5) return;
            JComboBox<User> cb = Ui.combo(arr, x -> x.name + "  \u2014  " + x.position);
            int idx = combos.size();
            if (idx < arr.length) cb.setSelectedIndex(idx);
            combos.add(cb);
            Ui.Flex row = new Ui.Flex(new BorderLayout(12, 0));
            row.add(new Ui.StepDot(idx + 1, 0), BorderLayout.WEST);
            row.add(cb, BorderLayout.CENTER);
            steps.add(row);
            steps.add(Ui.gap(10));
            steps.revalidate();
            steps.repaint();
        };
        for (int i = 0; i < 3; i++) addStep.run();

        Ui.Flex ctl = Ui.hbox();
        ctl.add(Ui.btn("+ Add step", Ui.BStyle.SOFT, addStep).small());
        ctl.add(Ui.hgap(8));
        ctl.add(Ui.btn("\u2212 Remove last", Ui.BStyle.SOFT, () -> {
            if (combos.size() <= 1) return;
            combos.remove(combos.size() - 1);
            steps.remove(steps.getComponentCount() - 1);
            steps.remove(steps.getComponentCount() - 1);
            steps.revalidate();
            steps.repaint();
        }).small());
        ctl.add(Box.createHorizontalGlue());
        c2.add(Ui.gap(4));
        c2.add(ctl);
        c2.add(Ui.gap(10));
        c2.add(notice);
        p.add(c2);
        p.add(Ui.gap(16));

        // ---- optional: sign your own submission by dragging a saved signature onto the page
        SignPaper paper = new SignPaper();
        Runnable syncPaper = () -> paper.setContent(false,
                title.getText().isBlank() ? "Untitled form" : title.getText().trim(),
                "Submitted by " + u.name + " \u00b7 " + Common.date(java.time.LocalDateTime.now()) + " \u00b7 " + type.getSelectedItem(),
                null, desc.getText());
        syncPaper.run();
        File[] shownPdf = {null};
        pdfSync[0] = () -> {                       // the first attached PDF becomes the page you sign on
            File first = null;
            for (File f : files) if (f.getName().toLowerCase().endsWith(".pdf")) { first = f; break; }
            if (first == null ? shownPdf[0] != null : !first.equals(shownPdf[0])) {
                shownPdf[0] = first;
                paper.clear();
                paper.setPdf(first);
            }
        };
        SignUi.onChange(title, syncPaper);
        SignUi.onChange(desc, syncPaper);
        type.addActionListener(e -> syncPaper.run());
        Ui.Round c3 = Ui.card();
        c3.add(Ui.section("Sign your submission (optional)"));
        c3.add(Ui.gap(6));
        c3.add(Ui.sub("Drag one of your saved signatures onto the form. If you attached a PDF above, you sign that PDF itself."));
        c3.add(Ui.gap(14));
        c3.add(SigTray.build(st, u, paper, () -> sh.go("profile")));
        c3.add(Ui.gap(14));
        c3.add(SignUi.pager(paper));
        c3.add(Ui.gap(8));
        c3.add(SignUi.centered(paper));
        p.add(c3);
        p.add(Ui.gap(16));

        Runnable updateNotice = () -> {
            boolean budget = "Budget Request".equals(type.getSelectedItem());
            amountRow.setVisible(budget);
            double amt = parse(amount.getText());
            notice.setText(st.needsFinanceStep((String) type.getSelectedItem(), amt)
                    ? "Conditional routing: this amount exceeds the threshold, so a Finance Officer review step will be added automatically."
                    : " ");
            amountRow.getParent().revalidate();
        };
        type.addActionListener(e -> updateNotice.run());
        amount.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateNotice.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateNotice.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateNotice.run(); }
        });

        JLabel err = Ui.label(" ", Font.PLAIN, 12.5f, Ui.ERROR);
        p.add(err);
        p.add(Ui.gap(6));
        Ui.Flex sendRow = new Ui.Flex(new BorderLayout());
        sendRow.add(Ui.btn("Submit & Start Pipeline \u2192", Ui.BStyle.PRIMARY, () -> {
            if (title.getText().isBlank()) { err.setText("Please enter a document title."); return; }
            String t = (String) type.getSelectedItem();
            double amt = 0;
            if ("Budget Request".equals(t)) {
                amt = parse(amount.getText());
                if (amt <= 0) { err.setText("Please enter a valid requested amount for a budget request."); return; }
            }
            List<String> ids = new ArrayList<>();
            for (JComboBox<User> cb : combos) {
                User r = (User) cb.getSelectedItem();
                if (r == null) { err.setText("No faculty reviewers are available."); return; }
                if (ids.contains(r.id)) { err.setText("Each step needs a different reviewer."); return; }
                ids.add(r.id);
            }
            Doc d = st.submit(u, title.getText().trim(), t, desc.getText().trim(), amt, files, ids);
            if (paper.hasSignature()) st.setOwnerSig(d, paper.png(), paper.cx(), paper.cy(), paper.widthFraction(), paper.page());
            Main.info(sh, d.id + " was submitted. It is now in " + st.byId(d.reviewers.get(0)).name + "'s inbox queue.");
            sh.show(Common.detail(sh, d, "documents", "Back to documents"));
        }), BorderLayout.CENTER);
        p.add(sendRow);
        return p;
    }

    private static double parse(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return 0; }
    }
}