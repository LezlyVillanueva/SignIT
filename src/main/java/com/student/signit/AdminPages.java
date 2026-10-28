package com.student.signit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Super Admin screens. */
final class AdminPages {
    private AdminPages() { }

    private static JComponent stat(String num, String label, String subText, Color iconBg, Color numColor) {
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
        c.add(Ui.label(label, Font.BOLD, 13.5f, Ui.DARK));
        c.add(Ui.gap(2));
        c.add(Ui.small(subText));
        return c;
    }

    /** Yellow "awaiting verification" row with Approve / Reject. */
    private static JComponent pendingRow(Shell sh, User target, boolean avatar) {
        Store st = Store.get();
        Ui.Flex row = new Ui.Flex(new BorderLayout(14, 0));
        if (avatar) row.add(Ui.top(new Ui.Avatar(target.initials(), 38, new Color(0xFDE68A), new Color(0xB45309))), BorderLayout.WEST);
        Ui.Flex m = Ui.vbox();
        m.add(Ui.label(target.name, Font.BOLD, 13.5f, Ui.DARK));
        boolean elev = target.status == UserStatus.ACTIVE && target.requestedRole != null;
        String detail = elev
                ? target.email + " \u00b7 requests role elevation: " + target.role.label + " \u2192 " + target.requestedRole.label
                : target.email + " \u00b7 " + target.role.label.toLowerCase() + " \u00b7 " + target.department;
        m.add(Ui.label(detail, Font.PLAIN, 11.5f, Ui.MUTED));
        if (avatar) m.add(Ui.label("Joined " + target.joined, Font.PLAIN, 11.5f, Ui.MUTED));
        row.add(m, BorderLayout.CENTER);
        Ui.Flex b = Ui.hbox();
        b.add(Ui.btn("Approve", Ui.BStyle.PRIMARY, () -> { st.decideUser(sh.user, target, true); sh.refresh(); }).small());
        b.add(Ui.hgap(8));
        b.add(Ui.btn("Reject", Ui.BStyle.SOFT, () -> { st.decideUser(sh.user, target, false); sh.refresh(); }).small());
        row.add(Ui.top(b), BorderLayout.EAST);
        return row;
    }

    static JComponent overview(Shell sh) {
        Store st = Store.get();
        Ui.VPanel p = Ui.page();
        p.add(Ui.h1("System Overview"));
        p.add(Ui.gap(4));
        p.add(Ui.sub("SignIT platform health and activity summary."));
        p.add(Ui.gap(24));

        int pending = st.pendingUsers().size();
        int active = 0, approvedMonth = 0;
        for (Doc d : st.docs) if (d.status == DocStatus.PENDING_REVIEW || d.status == DocStatus.REVISION_REQUESTED) active++;
        LocalDate now = LocalDate.now();
        for (AuditEntry e : st.ledger)
            if (e.title.equals("Document Fully Approved") && e.time.getMonth() == now.getMonth() && e.time.getYear() == now.getYear()) approvedMonth++;

        JPanel stats = new JPanel(new GridLayout(1, 4, 16, 0));
        stats.setOpaque(false);
        stats.setAlignmentX(0f);
        stats.add(stat(String.valueOf(st.users.size()), "Total Users", pending + " pending", new Color(0xFCE4F0), Ui.PRIMARY));
        stats.add(stat(String.valueOf(active), "Active Documents", "across all pipelines", new Color(0xE0F2FE), new Color(0x0284C7)));
        stats.add(stat(String.valueOf(approvedMonth), "Fully Approved", "this month", new Color(0xDDF8EC), new Color(0x059669)));
        stats.add(stat(String.valueOf(st.ledger.size()), "Audit Entries", "immutable records", new Color(0xF3E8FF), new Color(0x9333EA)));
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        p.add(stats);
        p.add(Ui.gap(24));

        List<User> pend = st.pendingUsers();
        if (!pend.isEmpty()) {
            Ui.Round box = new Ui.Round(new Color(0xFFFDF0), new Color(0xFDE68A), 20);
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            box.setBorder(new EmptyBorder(20, 24, 12, 24));
            box.add(Ui.label("\u25cf  Pending Verification (" + pend.size() + ")", Font.BOLD, 13.5f, new Color(0xB45309)));
            box.add(Ui.gap(14));
            for (User u : pend) { box.add(pendingRow(sh, u, false)); box.add(Ui.gap(14)); }
            p.add(box);
            p.add(Ui.gap(20));
        }

        // hash integrity verifier
        Ui.Round hv = Ui.card();
        hv.add(Ui.section("Hash integrity verifier"));
        hv.add(Ui.gap(10));
        hv.add(Ui.label("Enter a SHA-256 stamp from the audit ledger to verify its integrity against original records.", Font.PLAIN, 12.5f, Ui.MUTED));
        hv.add(Ui.gap(12));
        Ui.Field in = new Ui.Field("Enter SHA-256 hash prefix\u2026");
        in.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JLabel result = Ui.label(" ", Font.BOLD, 12.5f, Ui.DARK);
        Runnable verify = () -> {
            String[] r = st.verifyHash(in.getText());
            result.setForeground(r[0].equals("ok") ? new Color(0x047857) : r[0].equals("bad") ? Ui.ERROR : Ui.MUTED);
            result.setText("<html><body style='width:700px'>" + r[1] + "</body></html>");
            st.log("Admin", "Hash Verification", sh.user, null, null, "Prefix checked: " + in.getText().trim());
        };
        Ui.Flex row = new Ui.Flex(new BorderLayout(12, 0));
        row.add(in, BorderLayout.CENTER);
        row.add(Ui.btn("Verify", Ui.BStyle.PRIMARY, verify), BorderLayout.EAST);
        in.addActionListener(e -> verify.run());
        hv.add(row);
        hv.add(Ui.gap(12));
        hv.add(result);
        p.add(hv);
        return p;
    }

    static JComponent users(Shell sh) {
        Store st = Store.get();
        Ui.VPanel p = Ui.page();
        p.add(Ui.h1("User Management"));
        p.add(Ui.gap(18));

        List<User> pend = st.pendingUsers();
        if (!pend.isEmpty()) {
            p.add(Ui.label("\u25cf  AWAITING VERIFICATION", Font.BOLD, 11.5f, new Color(0xB45309)));
            p.add(Ui.gap(10));
            for (User u : pend) {
                Ui.Round c = new Ui.Round(new Color(0xFFFDF0), new Color(0xFDE68A), 18);
                c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
                c.setBorder(new EmptyBorder(16, 20, 16, 20));
                c.add(pendingRow(sh, u, true));
                p.add(c);
                p.add(Ui.gap(12));
            }
            p.add(Ui.gap(8));
        }

        List<User> act = new ArrayList<>();
        for (User u : st.users) if (u.status == UserStatus.ACTIVE) act.add(u);
        p.add(Ui.label("ACTIVE USERS (" + act.size() + ")", Font.BOLD, 11.5f, Ui.LABEL));
        p.add(Ui.gap(10));
        List<JComponent[]> rows = new ArrayList<>();
        for (User u : act) {
            Ui.Flex who = Ui.hbox();
            who.add(new Ui.Avatar(u.initials(), 34));
            who.add(Ui.hgap(12));
            Ui.Flex n = Ui.vbox();
            n.add(Ui.label(u.name, Font.BOLD, 12.5f, Ui.DARK));
            n.add(Ui.label(u.email, Font.PLAIN, 11, Ui.MUTED));
            who.add(n);
            rows.add(new JComponent[]{who, Ui.label(u.role == Role.ADMIN ? "Super Admin" : u.role.label, Font.PLAIN, 12.5f, Ui.DARK),
                    Ui.label(u.department, Font.PLAIN, 12.5f, Ui.DARK), Ui.chip("Active", "green"),
                    Ui.label(u.joined.toString(), Font.PLAIN, 12, Ui.MUTED)});
        }
        p.add(Ui.table(new String[]{"User", "Role", "Department", "Status", "Joined"}, new double[]{3.2, 1.4, 2, 1.2, 1.4}, rows, null));
        return p;
    }

    static JComponent allDocs(Shell sh) {
        Store st = Store.get();
        Ui.VPanel p = Ui.page();
        p.add(Ui.h1("All Documents"));
        p.add(Ui.gap(4));
        p.add(Ui.sub("Click a document to inspect its pipeline, signatures and audit trail."));
        p.add(Ui.gap(18));
        List<Doc> docs = new ArrayList<>(st.docs);
        docs.sort((a, b) -> b.submitted.compareTo(a.submitted));
        List<JComponent[]> rows = new ArrayList<>();
        for (Doc d : docs) {
            Ui.Flex t = Ui.vbox();
            t.add(Ui.label(d.title, Font.BOLD, 12.5f, Ui.DARK));
            t.add(Ui.mono(d.id, 10.5f, Ui.PRIMARY));
            rows.add(new JComponent[]{t, Ui.label(d.type, Font.PLAIN, 12, Ui.MUTED),
                    Ui.label(st.byId(d.ownerId).name, Font.PLAIN, 12.5f, Ui.DARK), Ui.statusChip(d.status),
                    Ui.label(d.displayStep() + " / " + d.reviewers.size(), Font.PLAIN, 12.5f, Ui.DARK),
                    Ui.label(Common.date(d.submitted), Font.PLAIN, 12, Ui.MUTED)});
        }
        p.add(Ui.table(new String[]{"Document", "Type", "Submitted by", "Status", "Step", "Date"},
                new double[]{3.2, 1.4, 1.8, 1.8, 0.8, 1.1}, rows,
                i -> sh.show(Common.detail(sh, docs.get(i), "documents", "Back to documents"))));
        return p;
    }

    static JComponent ledger(Shell sh) {
        Store st = Store.get();
        st.log("Admin", "Audit Log Viewed", sh.user, null, null, null);
        Ui.VPanel p = Ui.page();
        p.add(Ui.h1("Immutable Audit Ledger"));
        p.add(Ui.gap(4));
        p.add(Ui.sub("Append-only system-wide transaction log. All entries are tamper-evident."));
        p.add(Ui.gap(18));

        // banner
        Ui.Round banner = new Ui.Round(new Color(0xFFEEF5), Ui.BORDER, 16);
        banner.setLayout(new BorderLayout());
        banner.setBorder(new EmptyBorder(10, 18, 10, 14));
        Ui.Flex bl = Ui.hbox();
        bl.add(new Ui.Ico("lock", 14, Ui.PRIMARY));
        bl.add(Ui.hgap(8));
        bl.add(Ui.label(st.ledger.size() + " ENTRIES \u00b7 READ-ONLY", Font.BOLD, 11.5f, Ui.LABEL));
        banner.add(bl, BorderLayout.CENTER);
        banner.add(Ui.btn("Verify chain integrity", Ui.BStyle.PRIMARY, () -> {
            boolean ok = st.verifyChain();
            JOptionPane.showMessageDialog(sh, ok
                            ? "All " + st.ledger.size() + " ledger entries are intact.\nEvery record's hash links correctly to the previous one."
                            : "TAMPERING DETECTED. At least one ledger record no longer matches its hash chain.",
                    "Ledger verification", ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        }).small(), BorderLayout.EAST);
        p.add(banner);
        p.add(Ui.gap(12));

        // filter
        String[] cats = {"All categories", "Auth", "Documents", "Signatures", "Workflow", "Admin"};
        JComboBox<String> cat = Ui.combo(cats);
        Ui.Field search = new Ui.Field("Filter by title, user or reference");
        Ui.Flex bar = new Ui.Flex(new BorderLayout(12, 0));
        bar.add(search, BorderLayout.CENTER);
        cat.setPreferredSize(new Dimension(190, 40));
        bar.add(cat, BorderLayout.EAST);
        p.add(bar);
        p.add(Ui.gap(12));

        Ui.Round list = new Ui.Round(Color.WHITE, Ui.BORDER, 20);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        p.add(list);
        Runnable fill = () -> {
            list.removeAll();
            String q = search.getText().trim().toLowerCase();
            int shown = 0;
            for (int i = st.ledger.size() - 1; i >= 0; i--) {
                AuditEntry e = st.ledger.get(i);
                if (cat.getSelectedIndex() > 0 && !e.category.equals(cats[cat.getSelectedIndex()])) continue;
                if (!q.isEmpty() && !(e.title + " " + e.by + " " + (e.ref == null ? "" : e.ref)).toLowerCase().contains(q)) continue;
                list.add(entryRow(e, shown == 0));
                shown++;
            }
            if (shown == 0) {
                JLabel l = Ui.sub("No ledger entries match.");
                l.setBorder(new EmptyBorder(18, 22, 18, 22));
                list.add(l);
            }
            list.revalidate();
            list.repaint();
        };
        fill.run();
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { fill.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { fill.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { fill.run(); }
        });
        cat.addActionListener(e -> fill.run());
        return p;
    }

    private static JComponent entryRow(AuditEntry e, boolean first) {
        Ui.Flex row = new Ui.Flex(new BorderLayout(14, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                first ? new EmptyBorder(0, 0, 0, 0) : BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.BORDER),
                new EmptyBorder(14, 20, 14, 20)));
        String kind;
        switch (e.category) {
            case "Auth": kind = "purple"; break;
            case "Documents": kind = "blue"; break;
            case "Signatures": kind = "pink"; break;
            case "Workflow": kind = "orange"; break;
            default: kind = "red";
        }
        Ui.Chip chip = Ui.chip(e.category, kind);
        Ui.Flex chipBox = Ui.vbox();
        chipBox.setPreferredSize(new Dimension(92, 24));
        chipBox.add(chip);
        row.add(Ui.top(chipBox), BorderLayout.WEST);
        Ui.Flex m = Ui.vbox();
        m.add(Ui.label(e.title, Font.BOLD, 13, Ui.DARK));
        m.add(Ui.small("by " + e.by + (e.ref != null ? " \u00b7 ref: " + e.ref : "")));
        if (e.hash != null) {
            Ui.Flex h = Ui.hbox();
            h.add(new Ui.Ico("lock", 11, Ui.PRIMARY));
            h.add(Ui.hgap(5));
            h.add(Ui.mono("SHA-256: " + e.hash.substring(0, 16), 11, Ui.PRIMARY));
            m.add(h);
        }
        if (e.note != null && !e.note.isBlank()) m.add(Ui.label("\u201c" + e.note + "\u201d", Font.ITALIC, 11, Ui.MUTED));
        row.add(m, BorderLayout.CENTER);
        Ui.Flex r = Ui.vbox();
        JLabel t = Ui.mono(e.time.format(Store.TS), 11, Ui.PRIMARY);
        t.setAlignmentX(1f);
        JLabel id = Ui.mono(e.id, 10.5f, new Color(0xF4B6D3));
        id.setAlignmentX(1f);
        r.add(t);
        r.add(id);
        row.add(Ui.top(r), BorderLayout.EAST);
        return row;
    }
}
