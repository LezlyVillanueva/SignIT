package com.student.signit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntSupplier;

/** SignIT - Campus clearance & e-signature platform (Java Swing). */
public class Main {
    static JFrame frame;
    static JPanel root;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::start);
    }

    static void start() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        UIManager.put("OptionPane.messageFont", Ui.font(Font.PLAIN, 13));
        UIManager.put("OptionPane.buttonFont", Ui.font(Font.BOLD, 12));
        UIManager.put("OptionPane.background", Ui.BG);
        UIManager.put("Panel.background", Ui.BG);
        UIManager.put("OptionPane.messageForeground", Ui.DARK);
        Store.get();
        frame = new JFrame("SignIT \u2014 Campus Clearance");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        root = new JPanel(new BorderLayout());
        frame.setContentPane(root);
        frame.setSize(1280, 800);
        frame.setMinimumSize(new Dimension(1020, 660));
        frame.setLocationRelativeTo(null);
        showLogin();
        frame.setVisible(true);
    }

    static void setScreen(JComponent c) {
        root.removeAll();
        root.add(c, BorderLayout.CENTER);
        root.revalidate();
        root.repaint();
    }

    static void showLogin() {
        Store.get().current = null;
        setScreen(new LoginPanel());
    }

    static void showApp() {
        setScreen(new Shell(Store.get().current));
    }

    static void info(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "SignIT", JOptionPane.INFORMATION_MESSAGE);
    }
}

/** App frame for a signed-in user: sidebar navigation + swappable content area. */
final class Shell extends JPanel {
    final User user;
    private final JPanel holder = new JPanel(new BorderLayout());
    private final Map<String, NavItem> items = new LinkedHashMap<>();
    private final Map<String, Function<Shell, JComponent>> pages = new LinkedHashMap<>();
    String currentKey;

    Shell(User u) {
        super(new BorderLayout());
        this.user = u;
        holder.setBackground(Ui.BG);
        add(sidebar(), BorderLayout.WEST);
        add(holder, BorderLayout.CENTER);
        Store st = Store.get();
        switch (u.role) {
            case STUDENT:
                nav("overview", "Overview", "grid", StudentPages::overview, null);
                nav("documents", "My Documents", "doc", StudentPages::myDocuments, null);
                nav("submit", "Submit Form", "plus", StudentPages::submit, null);
                nav("profile", "Profile & Signatures", "user", Common::profile, null);
                break;
            case FACULTY:
                nav("inbox", "Inbox Queue", "inbox", FacultyPages::inbox, () -> st.inbox(u).size());
                nav("letters", "Letters", "doc", FacultyPages::letters, null);
                nav("history", "Action History", "clock", FacultyPages::history, null);
                nav("profile", "Profile & Signatures", "user", Common::profile, null);
                break;
            default:
                nav("overview", "Overview", "grid", AdminPages::overview, null);
                nav("users", "User Management", "users", AdminPages::users, () -> st.pendingUsers().size());
                nav("documents", "All Documents", "doc", AdminPages::allDocs, null);
                nav("ledger", "Audit Ledger", "clipboard", AdminPages::ledger, null);
        }
        buildNav();
        go(items.keySet().iterator().next());
    }

    // ------------------------------------------------------------------ navigation
    private void nav(String key, String text, String icon, Function<Shell, JComponent> page, IntSupplier badge) {
        items.put(key, new NavItem(text, icon, badge, () -> go(key)));
        pages.put(key, page);
    }

    private JPanel navBox;

    private void buildNav() {
        for (NavItem n : items.values()) { navBox.add(n); navBox.add(Ui.gap(4)); }
    }

    void go(String key) {
        currentKey = key;
        for (Map.Entry<String, NavItem> e : items.entrySet()) {
            e.getValue().selected = e.getKey().equals(key);
            e.getValue().repaint();
        }
        show(pages.get(key).apply(this));
    }

    /** Opens a detail page (kept under the current nav highlight). */
    void show(JComponent page) {
        holder.removeAll();
        holder.add(Ui.scroll(page), BorderLayout.CENTER);
        holder.revalidate();
        holder.repaint();
        for (NavItem n : items.values()) n.repaint();
    }

    void refresh() { go(currentKey); }

    // ------------------------------------------------------------------ sidebar
    private JComponent sidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Color.WHITE);
        side.setPreferredSize(new Dimension(236, 0));
        side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.BORDER));

        JPanel logo = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        logo.setOpaque(false);
        logo.setBorder(new EmptyBorder(16, 16, 16, 0));
        logo.add(new Brand(false));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(logo, BorderLayout.NORTH);
        top.add(new JSeparator() {{ setForeground(Ui.BORDER); }}, BorderLayout.CENTER);
        navBox = new JPanel();
        navBox.setOpaque(false);
        navBox.setLayout(new BoxLayout(navBox, BoxLayout.Y_AXIS));
        navBox.setBorder(new EmptyBorder(14, 10, 0, 10));

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(top, BorderLayout.NORTH);
        north.add(navBox, BorderLayout.CENTER);
        side.add(north, BorderLayout.NORTH);

        // footer: user + sign out
        JPanel foot = new JPanel();
        foot.setOpaque(false);
        foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
        foot.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.BORDER),
                new EmptyBorder(14, 14, 12, 14)));
        Ui.Flex me = Ui.hbox();
        me.add(new Ui.Avatar(user.initials(), 36));
        me.add(Ui.hgap(10));
        Ui.Flex who = Ui.vbox();
        who.add(Ui.label(user.name, Font.BOLD, 12, Ui.DARK));
        who.add(Ui.label(user.role == Role.STUDENT ? "Student" : user.role == Role.FACULTY ? "Faculty" : "Super Admin", Font.PLAIN, 11.5f, Ui.MUTED));
        me.add(who);
        foot.add(me);
        foot.add(Ui.gap(12));
        Ui.Flex out = Ui.hbox();
        out.add(Ui.hgap(8));
        out.add(new Ui.Ico("logout", 15, Ui.PRIMARY));
        out.add(Ui.hgap(6));
        Ui.Btn so = new Ui.Btn("Sign out", Ui.BStyle.LINK);
        so.padH = 6; so.padV = 2; so.setFont(Ui.font(Font.PLAIN, 12));
        so.addActionListener(e -> {
            Store.get().log("Auth", "User Logout", user, null, null, null);
            Main.showLogin();
        });
        out.add(so);
        foot.add(out);
        side.add(foot, BorderLayout.SOUTH);
        return side;
    }

    /** Rounded logo mark + "SignIT" wordmark. */
    static class Brand extends JComponent {
        final boolean white;
        Brand(boolean white) { this.white = white; }
        @Override public Dimension getPreferredSize() { return white ? new Dimension(160, 44) : new Dimension(130, 34); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.aa(g);
            if (white) { g2.setColor(new Color(255, 255, 255, 60)); g2.fillRoundRect(0, 0, 40, 40, 12, 12); }
            else { g2.setPaint(new GradientPaint(0, 0, Ui.PRIMARY, 34, 34, Ui.PRIMARY2)); g2.fillOval(0, 0, 33, 33); }
            Ui.icon(g2, "check", white ? 9 : 8, white ? 9 : 8, 18, Color.WHITE);
            g2.setFont(Ui.font(Font.BOLD, 20));
            int x = white ? 52 : 43, y = white ? 27 : 23;
            g2.setColor(white ? Color.WHITE : Ui.DARK);
            g2.drawString("Sign", x, y);
            g2.setColor(white ? new Color(0xFFD1E8) : Ui.PRIMARY2);
            g2.drawString("IT", x + g2.getFontMetrics().stringWidth("Sign"), y);
            g2.dispose();
        }
    }

    /** One sidebar entry. */
    static class NavItem extends JComponent {
        final String text, icon; final IntSupplier badge;
        boolean selected, hover;
        NavItem(String text, String icon, IntSupplier badge, Runnable go) {
            this.text = text; this.icon = icon; this.badge = badge;
            setAlignmentX(0f);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { go.run(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override public Dimension getPreferredSize() { return new Dimension(200, 40); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 40); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.aa(g);
            if (selected) { g2.setColor(new Color(0xFCE4F0)); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14); }
            else if (hover) { g2.setColor(new Color(0xFFF0F7)); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14); }
            Color c = selected ? Ui.PRIMARY : new Color(0x7A1E4F);
            Ui.icon(g2, icon, 14, 12, 16, c);
            g2.setFont(Ui.font(selected ? Font.BOLD : Font.PLAIN, 13.5f));
            g2.setColor(c);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 40, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            int n = badge == null ? 0 : badge.getAsInt();
            if (n > 0) {
                g2.setColor(Ui.PRIMARY);
                g2.fillOval(getWidth() - 34, 10, 20, 20);
                g2.setColor(Color.WHITE);
                g2.setFont(Ui.font(Font.BOLD, 11));
                FontMetrics f2 = g2.getFontMetrics();
                String s = String.valueOf(n);
                g2.drawString(s, getWidth() - 24 - f2.stringWidth(s) / 2, 10 + (20 - f2.getHeight()) / 2 + f2.getAscent());
            }
            g2.dispose();
        }
    }
}

/** Sign-in screen (split gradient hero + form) with demo accounts and registration. */
final class LoginPanel extends JPanel {
    private final Ui.Field email = new Ui.Field("you@signit.edu");
    private final Ui.PField pass = new Ui.PField();
    private final JLabel err = Ui.label(" ", Font.PLAIN, 12, Ui.ERROR);

    LoginPanel() {
        setLayout(new GridLayout(1, 2));
        add(hero());
        add(form());
    }

    private JComponent hero() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                g2.setPaint(new GradientPaint(0, 0, new Color(0xD6006F), getWidth(), getHeight(), new Color(0xFF6FB5)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 18));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawOval(-70, -60, 260, 260);
                g2.drawOval(getWidth() - 260, getHeight() - 230, 400, 400);
                g2.dispose();
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(30, 28, 30, 48));
        JComponent brand = new Shell.Brand(true);
        brand.setAlignmentX(0f);
        brand.setMaximumSize(new Dimension(200, 44));
        brand.setPreferredSize(new Dimension(200, 44));
        p.add(brand);
        p.add(Ui.gap(54));
        p.add(Ui.label("Campus clearance,", Font.BOLD, 36, Color.WHITE));
        p.add(Ui.label("finally effortless.", Font.BOLD, 36, Color.WHITE));
        p.add(Ui.gap(18));
        p.add(Ui.label("<html><body style='width:330px'>Submit forms, track approvals, and sign documents in real time \u2014 no paper, no email chains, no lost memos.</body></html>",
                Font.PLAIN, 14, new Color(255, 240, 248)));
       
        return p;
    }

    private JComponent form() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Ui.BG);
        Ui.Flex f = Ui.vbox();
        f.setPreferredSize(new Dimension(440, 520));
        f.add(Ui.label("Sign in to SignIT", Font.BOLD, 22, Ui.DARK));
        f.add(Ui.gap(4));
        f.add(Ui.sub("New or returning \u2014 sign in to access your clearance dashboard."));
        f.add(Ui.gap(26));
        f.add(Ui.field("EMAIL", email));
        pass.setText(Store.DEMO_PASSWORD);
        f.add(Ui.field("PASSWORD", pass));
        f.add(err);
        f.add(Ui.gap(4));
        Ui.Btn go = Ui.btn("Sign In", Ui.BStyle.PRIMARY, this::doLogin);
        Ui.Flex goRow = new Ui.Flex(new BorderLayout());
        goRow.add(go, BorderLayout.CENTER);
        f.add(goRow);
        f.add(Ui.gap(10));
        Ui.Flex links = Ui.hbox();
        Ui.Btn create = Ui.btn("Create an account", Ui.BStyle.LINK, () -> new RegisterDialog(SwingUtilities.getWindowAncestor(this)).setVisible(true));
        create.padH = 0;
        links.add(create);
        links.add(Box.createHorizontalGlue());
        Ui.Btn reset = Ui.btn("Reset demo data", Ui.BStyle.LINK, () -> {
            if (JOptionPane.showConfirmDialog(this, "Restore all demo users, documents and the audit ledger?",
                    "Reset demo data", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                Store.resetDemoData();
                Main.showLogin();
            }
        });
        reset.padH = 0;
        links.add(reset);
        f.add(links);
        f.add(Ui.gap(16));
        f.add(Ui.section("Demo accounts"));
        f.add(Ui.gap(10));
        f.add(demo("Student", "student@signit.edu", new Color(0xFFF3F8)));
        f.add(Ui.gap(8));
        f.add(demo("Faculty", "faculty@signit.edu", new Color(0xFFF1F1)));
        f.add(Ui.gap(8));
        f.add(demo("Admin", "admin@signit.edu", new Color(0xFCF1FF)));
        outer.add(f);
        pass.addActionListener(e -> doLogin());
        email.addActionListener(e -> doLogin());
        return outer;
    }

    private JComponent demo(String role, String mail, Color bg) {
        Ui.Round r = new Ui.Round(bg, Ui.BORDER, 12);
        r.setLayout(new BorderLayout());
        r.setBorder(new EmptyBorder(11, 16, 11, 16));
        Ui.Flex l = Ui.hbox();
        l.add(Ui.label(role, Font.BOLD, 12.5f, Ui.DARK));
        l.add(Ui.hgap(10));
        l.add(Ui.label(mail, Font.PLAIN, 12, Ui.MUTED));
        r.add(l, BorderLayout.CENTER);
        r.add(Ui.label("Use \u2192", Font.BOLD, 12, Ui.PRIMARY), BorderLayout.EAST);
        Ui.clickable(r, () -> { email.setText(mail); pass.setText(Store.DEMO_PASSWORD); err.setText(" "); doLogin(); });
        return r;
    }

    private void doLogin() {
        String e = Store.get().login(email.getText(), new String(pass.getPassword()));
        if (e != null) err.setText(e);
        else Main.showApp();
    }
}

/** Self-registration. New accounts stay PENDING_APPROVAL until a Super Admin verifies them. */
final class RegisterDialog extends JDialog {
    RegisterDialog(Window owner) {
        super(owner, "Create account", ModalityType.APPLICATION_MODAL);
        Ui.Field name = new Ui.Field("Full name"), mail = new Ui.Field("you@signit.edu"), dept = new Ui.Field("e.g. Computer Science");
        Ui.PField pw = new Ui.PField();
        JComboBox<String> role = Ui.combo("Student / Applicant", "Faculty / Approver");
        JLabel err = Ui.label(" ", Font.PLAIN, 12, Ui.ERROR);

        Ui.Flex f = Ui.vbox();
        f.setBorder(new EmptyBorder(24, 28, 24, 28));
        f.add(Ui.label("Create your SignIT account", Font.BOLD, 18, Ui.DARK));
        f.add(Ui.gap(4));
        f.add(Ui.sub("A Super Admin must verify new accounts before you can sign in."));
        f.add(Ui.gap(18));
        f.add(Ui.field("Full Name", name));
        f.add(Ui.field("Email", mail));
        f.add(Ui.field("Password (min. 6 characters)", pw));
        f.add(Ui.field("Role", role));
        f.add(Ui.field("Department", dept));
        f.add(err);
        f.add(Ui.gap(6));
        Ui.Flex row = Ui.hbox();
        row.add(Box.createHorizontalGlue());
        row.add(Ui.btn("Cancel", Ui.BStyle.SOFT, this::dispose));
        row.add(Ui.hgap(10));
        row.add(Ui.btn("Register", Ui.BStyle.PRIMARY, () -> {
            String e = Store.get().register(name.getText(), mail.getText(), new String(pw.getPassword()),
                    role.getSelectedIndex() == 0 ? Role.STUDENT : Role.FACULTY, dept.getText());
            if (e != null) { err.setText(e); return; }
            Main.info(this, "Registration submitted! A Super Admin will verify your account shortly.");
            dispose();
        }));
        f.add(row);
        JPanel bg = new JPanel(new BorderLayout());
        bg.setBackground(Ui.BG);
        bg.add(f);
        setContentPane(bg);
        setPreferredSize(new Dimension(460, 600));
        pack();
        setLocationRelativeTo(owner);
    }
}
