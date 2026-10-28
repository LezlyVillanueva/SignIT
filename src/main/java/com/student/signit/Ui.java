
package com.student.signit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** Theme + reusable custom-painted Swing widgets that reproduce the SignIT Figma look. */
final class Ui {
    private Ui() { }

    // ------------------------------------------------------------------ theme
    static final Color BG = new Color(0xFFF7FA), BORDER = new Color(0xF9CFE3), PRIMARY = new Color(0xD6006F),
            PRIMARY2 = new Color(0xF72585), DARK = new Color(0x1A0A14), MUTED = new Color(0xB8457F),
            LABEL = new Color(0x9D1560), FIELD = new Color(0xFFEEF5), SOFT = new Color(0xFCE4F0),
            HINT = new Color(0xE0A3C2), ERROR = new Color(0xC81E1E);
    static final String FAMILY = pickFamily();

    private static String pickFamily() {
        java.util.Set<String> names = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Inter", "DM Sans", "Segoe UI", "Helvetica Neue", "Arial"})
            if (names.contains(f)) return f;
        return Font.SANS_SERIF;
    }

    static Font font(int style, float size) { return new Font(FAMILY, style, Math.round(size)); }

    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    // ------------------------------------------------------------------ labels
    static JLabel label(String t, int style, float size, Color c) {
        JLabel l = new JLabel(t);
        l.setFont(font(style, size));
        l.setForeground(c);
        l.setAlignmentX(0f);
        return l;
    }
    static JLabel h1(String t) { return label(t, Font.BOLD, 24, DARK); }
    static JLabel h2(String t) { return label(t, Font.BOLD, 15, DARK); }
    static JLabel sub(String t) { return label(t, Font.PLAIN, 13, MUTED); }
    static JLabel section(String t) { return label(t.toUpperCase(), Font.BOLD, 11, LABEL); }
    static JLabel small(String t) { return label(t, Font.PLAIN, 11.5f, MUTED); }
    static JLabel mono(String t, float size, Color c) {
        JLabel l = new JLabel(t);
        l.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size)));
        l.setForeground(c);
        l.setAlignmentX(0f);
        return l;
    }

    static Component gap(int h) { return Box.createRigidArea(new Dimension(0, h)); }
    static Component hgap(int w) { return Box.createRigidArea(new Dimension(w, 0)); }

    // ------------------------------------------------------------------ containers
    /** Transparent container that stretches horizontally inside a vertical BoxLayout. */
    static class Flex extends JPanel {
        Flex(LayoutManager lm) { super(lm); setOpaque(false); setAlignmentX(0f); }
        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    static Flex vbox() {
        Flex f = new Flex(null);
        f.setLayout(new BoxLayout(f, BoxLayout.Y_AXIS));
        return f;
    }

    static Flex hbox() {
        Flex f = new Flex(null);
        f.setLayout(new BoxLayout(f, BoxLayout.X_AXIS));
        return f;
    }

    static class Round extends JPanel {
        Color fill, line;
        int arc;
        Round(Color fill, Color line, int arc) {
            this.fill = fill; this.line = line; this.arc = arc;
            setOpaque(false);
            setAlignmentX(0f);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            if (fill != null) { g2.setColor(fill); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc); }
            if (line != null) { g2.setColor(line); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc); }
            g2.dispose();
        }
        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    static Round card() {
        Round r = new Round(Color.WHITE, BORDER, 20);
        r.setLayout(new BoxLayout(r, BoxLayout.Y_AXIS));
        r.setBorder(new EmptyBorder(20, 24, 20, 24));
        return r;
    }

    static class VPanel extends JPanel implements Scrollable {
        VPanel() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(BG); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static VPanel page() {
        VPanel p = new VPanel();
        p.setBorder(new EmptyBorder(32, 44, 36, 44));
        return p;
    }

    static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(null);
        sp.getViewport().setBackground(BG);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUI(new SBar());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    static class SBar extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = new Color(0xF4B6D3); trackColor = BG; }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = aa(g);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
    }

    static void handCursor(Component c) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) handCursor(k);
    }

    static void clickable(JComponent c, Runnable r) {
        handCursor(c);
        c.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { r.run(); }
        });
    }

    // ------------------------------------------------------------------ buttons
    enum BStyle { PRIMARY, SOFT, WARN, DANGER, LINK }

    static class Btn extends JButton {
        final BStyle st;
        int padV = 10, padH = 18;
        Btn(String text, BStyle st) {
            super(text);
            this.st = st;
            setFont(font(Font.BOLD, 12.5f));
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(0f);
        }
        Btn small() { padV = 7; padH = 14; setFont(font(Font.BOLD, 11.5f)); return this; }
        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + padH * 2, fm.getHeight() + padV * 2);
        }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            boolean en = isEnabled(), hov = getModel().isRollover() && en;
            Color text, line = null;
            if (!en) {
                g2.setColor(new Color(0xEDEDF1)); g2.fillRoundRect(0, 0, w, h, 12, 12);
                text = new Color(0x9A9AA6);
            } else switch (st) {
                case PRIMARY:
                    g2.setPaint(new GradientPaint(0, 0, hov ? PRIMARY.brighter() : PRIMARY, w, h, PRIMARY2));
                    g2.fillRoundRect(0, 0, w, h, 12, 12);
                    text = Color.WHITE; break;
                case SOFT:
                    g2.setColor(hov ? SOFT : new Color(0xFFEEF5)); g2.fillRoundRect(0, 0, w, h, 12, 12);
                    line = BORDER; text = PRIMARY; break;
                case WARN:
                    g2.setColor(hov ? new Color(0xFFEBD0) : new Color(0xFFF4E6)); g2.fillRoundRect(0, 0, w, h, 12, 12);
                    line = new Color(0xFBD2A0); text = new Color(0xC2410C); break;
                case DANGER:
                    g2.setColor(hov ? new Color(0xFFDADA) : new Color(0xFFEEEE)); g2.fillRoundRect(0, 0, w, h, 12, 12);
                    line = new Color(0xF8B4B4); text = new Color(0xC81E1E); break;
                default:
                    text = hov ? PRIMARY2 : PRIMARY;
            }
            if (line != null) { g2.setColor(line); g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12); }
            g2.setFont(getFont());
            g2.setColor(text);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    static Btn btn(String t, BStyle s, Runnable r) {
        Btn b = new Btn(t, s);
        b.addActionListener(e -> r.run());
        return b;
    }

    // ------------------------------------------------------------------ chips / badges
    static class Chip extends JLabel {
        final Color bg, line;
        Chip(String text, Color bg, Color fg, Color line) {
            super(text);
            this.bg = bg; this.line = line;
            setFont(font(Font.PLAIN, 11.5f));
            setForeground(fg);
            setBorder(new EmptyBorder(3, 12, 3, 12));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(bg); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.setColor(line); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static Chip chip(String text, String kind) {
        switch (kind) {
            case "yellow": return new Chip(text, new Color(0xFFFBEB), new Color(0xB45309), new Color(0xFDE68A));
            case "green": return new Chip(text, new Color(0xECFDF5), new Color(0x047857), new Color(0xA7F3D0));
            case "orange": return new Chip(text, new Color(0xFFF7ED), new Color(0xC2410C), new Color(0xFED7AA));
            case "red": return new Chip(text, new Color(0xFEF2F2), new Color(0xB91C1C), new Color(0xFECACA));
            case "purple": return new Chip(text, new Color(0xF5F0FF), new Color(0x6D28D9), new Color(0xDDD0FB));
            case "blue": return new Chip(text, new Color(0xEFF6FF), new Color(0x1D4ED8), new Color(0xBFDBFE));
            case "pink": return new Chip(text, new Color(0xFFF0F7), new Color(0xBE185D), new Color(0xFBCFE8));
            default: return new Chip(text, new Color(0xF3F4F6), new Color(0x6B7280), new Color(0xE5E7EB));
        }
    }

    static Chip statusChip(DocStatus s) {
        switch (s) {
            case APPROVED: return chip(s.label, "green");
            case PENDING_REVIEW: return chip(s.label, "yellow");
            case REVISION_REQUESTED: return chip(s.label, "orange");
            default: return chip(s.label, "red");
        }
    }

    /** Wraps a component so it keeps its natural size in the top-right of a BorderLayout region. */
    static JComponent top(JComponent c) {
        Flex f = new Flex(new BorderLayout());
        f.add(c, BorderLayout.NORTH);
        return f;
    }

    // ------------------------------------------------------------------ inputs
    private static void fieldBg(JTextComponent c, Graphics g) {
        Graphics2D g2 = aa(g);
        g2.setColor(FIELD); g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 14, 14);
        g2.setColor(c.hasFocus() ? PRIMARY2 : BORDER);
        g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 14, 14);
        g2.dispose();
    }

    private static void fieldHint(JTextComponent c, Graphics g, String hint) {
        if (hint == null || !c.getText().isEmpty()) return;
        Graphics2D g2 = aa(g);
        g2.setFont(c.getFont()); g2.setColor(HINT);
        Insets in = c.getInsets();
        g2.drawString(hint, in.left, in.top + g2.getFontMetrics().getAscent() + (c instanceof JTextArea ? 0 : 0));
        g2.dispose();
    }

    static class Field extends JTextField {
        final String hint;
        Field(String hint) {
            this.hint = hint;
            setOpaque(false); setFont(font(Font.PLAIN, 13)); setForeground(DARK); setCaretColor(PRIMARY);
            setBorder(new EmptyBorder(11, 14, 11, 14)); setAlignmentX(0f);
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) { fieldBg(this, g); super.paintComponent(g); fieldHint(this, g, hint); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }

    static class PField extends JPasswordField {
        PField() {
            setOpaque(false); setFont(font(Font.PLAIN, 13)); setForeground(DARK); setCaretColor(PRIMARY);
            setBorder(new EmptyBorder(11, 14, 11, 14)); setAlignmentX(0f);
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) { fieldBg(this, g); super.paintComponent(g); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }

    static class Area extends JTextArea {
        final String hint;
        Area(String hint, int rows) {
            super(rows, 20);
            this.hint = hint;
            setOpaque(false); setFont(font(Font.PLAIN, 13)); setForeground(DARK); setCaretColor(PRIMARY);
            setLineWrap(true); setWrapStyleWord(true);
            setBorder(new EmptyBorder(11, 14, 11, 14)); setAlignmentX(0f);
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) { fieldBg(this, g); super.paintComponent(g); fieldHint(this, g, hint); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }

    static <T> JComboBox<T> combo(T[] items, Function<T, String> text) {
        JComboBox<T> cb = new JComboBox<T>(items) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                g2.setColor(FIELD); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(BORDER); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        cb.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override public void paint(Graphics g) {
                        Graphics2D g2 = aa(g);
                        g2.setColor(PRIMARY);
                        int cx = getWidth() / 2 - 2, cy = getHeight() / 2;
                        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawPolyline(new int[]{cx - 4, cx, cx + 4}, new int[]{cy - 2, cy + 2, cy - 2}, 3);
                        g2.dispose();
                    }
                };
                b.setBorder(null); b.setContentAreaFilled(false); b.setFocusPainted(false);
                return b;
            }
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle r, boolean f) { }
        });
        cb.setOpaque(false);
        cb.setAlignmentX(0f);
        cb.setFont(font(Font.PLAIN, 13));
        cb.setBorder(new EmptyBorder(2, 4, 2, 4));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                @SuppressWarnings("unchecked") String s = v == null ? "" : text.apply((T) v);
                JLabel c = (JLabel) super.getListCellRendererComponent(l, s, i, sel, foc);
                c.setBorder(new EmptyBorder(8, 10, 8, 10));
                c.setFont(font(Font.PLAIN, 13)); c.setForeground(DARK);
                c.setOpaque(i >= 0);
                c.setBackground(sel ? SOFT : Color.WHITE);
                return c;
            }
        });
        return cb;
    }

    static JComboBox<String> combo(String... items) { return combo(items, s -> s); }

    /** Labelled form row: small caption above the control. */
    static Flex field(String caption, JComponent input) {
        Flex f = vbox();
        f.add(label(caption, Font.BOLD, 12, new Color(0x7A1E4F)));
        f.add(gap(6));
        f.add(input);
        f.add(gap(14));
        return f;
    }

    // ------------------------------------------------------------------ drop zone
    static JComponent dropZone(String text, Consumer<List<File>> onFiles) {
        class Zone extends JPanel {
            Zone() {
                setOpaque(false); setAlignmentX(0f);
                setPreferredSize(new Dimension(300, 84));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        JFileChooser fc = new JFileChooser();
                        fc.setMultiSelectionEnabled(true);
                        if (fc.showOpenDialog(Zone.this) == JFileChooser.APPROVE_OPTION)
                            onFiles.accept(java.util.Arrays.asList(fc.getSelectedFiles()));
                    }
                });
                setTransferHandler(new TransferHandler() {
                    @Override public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
                    @SuppressWarnings("unchecked")
                    @Override public boolean importData(TransferSupport s) {
                        try {
                            onFiles.accept((List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                            return true;
                        } catch (Exception ex) { return false; }
                    }
                });
            }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 84); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                g2.setColor(FIELD); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(new Color(0xF4B6D3));
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                icon(g2, "image", getWidth() / 2f - 9, 18, 18, PRIMARY);
                g2.setFont(font(Font.PLAIN, 12.5f)); g2.setColor(PRIMARY);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, 58);
                g2.dispose();
            }
        }
        return new Zone();
    }

    // ------------------------------------------------------------------ icons
    static void icon(Graphics g0, String n, float x, float y, float s, Color c) {
        Graphics2D g = aa(g0);
        g.setColor(c);
        g.setStroke(new BasicStroke(Math.max(1.4f, s * 0.1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.translate(x, y);
        switch (n) {
            case "grid":
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++)
                    g.draw(new RoundRectangle2D.Float(s * (.14f + .42f * i), s * (.14f + .42f * j), s * .32f, s * .32f, s * .1f, s * .1f));
                break;
            case "doc":
                g.draw(new RoundRectangle2D.Float(s * .2f, s * .08f, s * .6f, s * .84f, s * .14f, s * .14f));
                g.draw(new Line2D.Float(s * .34f, s * .42f, s * .66f, s * .42f));
                g.draw(new Line2D.Float(s * .34f, s * .62f, s * .6f, s * .62f));
                break;
            case "plus":
                g.draw(new Line2D.Float(s * .5f, s * .18f, s * .5f, s * .82f));
                g.draw(new Line2D.Float(s * .18f, s * .5f, s * .82f, s * .5f));
                break;
            case "user":
                g.draw(new Ellipse2D.Float(s * .32f, s * .1f, s * .36f, s * .36f));
                g.draw(new Arc2D.Float(s * .16f, s * .56f, s * .68f, s * .7f, 0, 180, Arc2D.OPEN));
                break;
            case "users":
                g.draw(new Ellipse2D.Float(s * .16f, s * .14f, s * .32f, s * .32f));
                g.draw(new Arc2D.Float(s * .06f, s * .56f, s * .52f, s * .6f, 0, 180, Arc2D.OPEN));
                g.draw(new Ellipse2D.Float(s * .56f, s * .2f, s * .26f, s * .26f));
                g.draw(new Arc2D.Float(s * .52f, s * .58f, s * .42f, s * .5f, 0, 180, Arc2D.OPEN));
                break;
            case "inbox": {
                g.draw(new RoundRectangle2D.Float(s * .12f, s * .2f, s * .76f, s * .62f, s * .14f, s * .14f));
                Path2D p = new Path2D.Float();
                p.moveTo(s * .12f, s * .56f); p.lineTo(s * .35f, s * .56f); p.lineTo(s * .4f, s * .68f);
                p.lineTo(s * .6f, s * .68f); p.lineTo(s * .65f, s * .56f); p.lineTo(s * .88f, s * .56f);
                g.draw(p);
                break;
            }
            case "clock":
                g.draw(new Ellipse2D.Float(s * .12f, s * .12f, s * .76f, s * .76f));
                g.draw(new Line2D.Float(s * .5f, s * .3f, s * .5f, s * .5f));
                g.draw(new Line2D.Float(s * .5f, s * .5f, s * .65f, s * .6f));
                break;
            case "clipboard":
                g.draw(new RoundRectangle2D.Float(s * .2f, s * .16f, s * .6f, s * .74f, s * .12f, s * .12f));
                g.fill(new RoundRectangle2D.Float(s * .36f, s * .06f, s * .28f, s * .16f, s * .06f, s * .06f));
                g.draw(new Line2D.Float(s * .34f, s * .5f, s * .66f, s * .5f));
                g.draw(new Line2D.Float(s * .34f, s * .68f, s * .6f, s * .68f));
                break;
            case "lock":
                g.draw(new RoundRectangle2D.Float(s * .22f, s * .46f, s * .56f, s * .4f, s * .1f, s * .1f));
                g.draw(new Arc2D.Float(s * .32f, s * .12f, s * .36f, s * .6f, 0, 180, Arc2D.OPEN));
                break;
            case "check": {
                g.draw(new Ellipse2D.Float(s * .1f, s * .1f, s * .8f, s * .8f));
                Path2D p = new Path2D.Float();
                p.moveTo(s * .3f, s * .52f); p.lineTo(s * .45f, s * .66f); p.lineTo(s * .7f, s * .38f);
                g.draw(p);
                break;
            }
            case "clip":
                g.rotate(Math.toRadians(45), s / 2, s / 2);
                g.draw(new RoundRectangle2D.Float(s * .36f, s * .06f, s * .28f, s * .88f, s * .28f, s * .28f));
                break;
            case "logout": {
                Path2D p = new Path2D.Float();
                p.moveTo(s * .45f, s * .16f); p.lineTo(s * .2f, s * .16f); p.lineTo(s * .2f, s * .84f); p.lineTo(s * .45f, s * .84f);
                g.draw(p);
                g.draw(new Line2D.Float(s * .4f, s * .5f, s * .86f, s * .5f));
                Path2D h = new Path2D.Float();
                h.moveTo(s * .7f, s * .34f); h.lineTo(s * .86f, s * .5f); h.lineTo(s * .7f, s * .66f);
                g.draw(h);
                break;
            }
            case "bolt": {
                Path2D p = new Path2D.Float();
                p.moveTo(s * .56f, s * .05f); p.lineTo(s * .2f, s * .56f); p.lineTo(s * .48f, s * .56f);
                p.lineTo(s * .42f, s * .95f); p.lineTo(s * .8f, s * .4f); p.lineTo(s * .52f, s * .4f); p.closePath();
                g.fill(p);
                break;
            }
            case "image": {
                g.draw(new RoundRectangle2D.Float(s * .12f, s * .15f, s * .76f, s * .7f, s * .12f, s * .12f));
                g.draw(new Ellipse2D.Float(s * .56f, s * .28f, s * .14f, s * .14f));
                Path2D p = new Path2D.Float();
                p.moveTo(s * .12f, s * .75f); p.lineTo(s * .38f, s * .5f); p.lineTo(s * .55f, s * .68f);
                p.lineTo(s * .68f, s * .58f); p.lineTo(s * .88f, s * .76f);
                g.draw(p);
                break;
            }
            case "chev": {
                Path2D p = new Path2D.Float();
                p.moveTo(s * .38f, s * .2f); p.lineTo(s * .66f, s * .5f); p.lineTo(s * .38f, s * .8f);
                g.draw(p);
                break;
            }
            default: break;
        }
        g.dispose();
    }

    static class Ico extends JComponent {
        final String name; final int size; final Color color;
        Ico(String name, int size, Color color) { this.name = name; this.size = size; this.color = color; setOpaque(false); }
        @Override public Dimension getPreferredSize() { return new Dimension(size, size); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) { icon(g, name, 0, 0, size, color); }
    }

    // ------------------------------------------------------------------ avatar / segments / step dot
    static class Avatar extends JComponent {
        final String text; final int size; final Color bg, fg;
        Avatar(String text, int size, Color bg, Color fg) {
            this.text = text; this.size = size; this.bg = bg; this.fg = fg; setOpaque(false);
        }
        Avatar(String text, int size) { this(text, size, null, Color.WHITE); }
        @Override public Dimension getPreferredSize() { return new Dimension(size, size); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setPaint(bg == null ? new GradientPaint(0, 0, PRIMARY, size, size, PRIMARY2) : new GradientPaint(0, 0, bg, 0, 1, bg));
            g2.fillOval(0, 0, size - 1, size - 1);
            g2.setColor(fg);
            g2.setFont(font(Font.BOLD, size * 0.36f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (size - fm.stringWidth(text)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Segmented step progress bar (done / current / queued). */
    static class Segments extends JComponent {
        final int total, done, current;
        Segments(int total, int done, int current) { this.total = total; this.done = done; this.current = current; setAlignmentX(0f); }
        @Override public Dimension getPreferredSize() { return new Dimension(200, 8); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 8); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int gap = 22, w = (getWidth() - (total - 1) * gap) / total;
            for (int i = 0; i < total; i++) {
                int x = i * (w + gap);
                g2.setColor(i < done ? PRIMARY : i == current ? new Color(0xF9A8D4) : new Color(0xFBCFE8));
                g2.fillRoundRect(x, 1, w, 6, 6, 6);
                if (i < total - 1) { g2.setColor(new Color(0xFBCFE8)); g2.fillOval(x + w + gap / 2 - 3, 1, 6, 6); }
            }
            g2.dispose();
        }
    }

    static class Dot extends JComponent {
        Dot() { setOpaque(false); }
        @Override public Dimension getPreferredSize() { return new Dimension(8, 20); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(PRIMARY);
            g2.fillOval(1, 6, 6, 6);
            g2.dispose();
        }
    }

    static class StepDot extends JComponent {
        final int n; final int mode; // 0 queued, 1 current, 2 done
        StepDot(int n, int mode) { this.n = n; this.mode = mode; setOpaque(false); }
        @Override public Dimension getPreferredSize() { return new Dimension(30, 30); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            if (mode == 2) { g2.setColor(PRIMARY); g2.fillOval(0, 0, 29, 29); }
            else if (mode == 1) { g2.setColor(Color.WHITE); g2.fillOval(0, 0, 29, 29); g2.setColor(PRIMARY); g2.setStroke(new BasicStroke(1.4f)); g2.drawOval(1, 1, 27, 27); }
            else { g2.setColor(SOFT); g2.fillOval(0, 0, 29, 29); }
            g2.setColor(mode == 2 ? Color.WHITE : PRIMARY);
            g2.setFont(font(Font.BOLD, 11.5f));
            String t = String.valueOf(n);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(t, (30 - fm.stringWidth(t)) / 2, (30 - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Signature preview: uploaded image if present, otherwise the typed name in italics. */
    static JComponent signaturePreview(User u, String caption, boolean solidBorder) {
        class Sig extends JPanel {
            Sig() { setOpaque(false); setAlignmentX(0f); setPreferredSize(new Dimension(300, 110)); }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 110); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                g2.setColor(FIELD); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setColor(solidBorder ? PRIMARY : new Color(0xF4B6D3));
                g2.setStroke(solidBorder ? new BasicStroke(1.2f)
                        : new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                if (u.signatureImage != null) {
                    try {
                        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(u.signatureImage));
                        double sc = Math.min((getWidth() - 40.0) / img.getWidth(), (getHeight() - 40.0) / img.getHeight());
                        int w = (int) (img.getWidth() * sc), h = (int) (img.getHeight() * sc);
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g2.drawImage(img, (getWidth() - w) / 2, 10 + (getHeight() - 36 - h) / 2, w, h, null);
                    } catch (Exception ignored) { }
                } else {
                    g2.setColor(PRIMARY);
                    g2.setFont(font(Font.ITALIC, 22));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(u.signatureText, (getWidth() - fm.stringWidth(u.signatureText)) / 2, getHeight() / 2 - 2);
                }
                g2.setColor(MUTED);
                g2.setFont(font(Font.PLAIN, 11.5f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(caption, (getWidth() - fm.stringWidth(caption)) / 2, getHeight() - 22);
                g2.dispose();
            }
        }
        return new Sig();
    }

    // ------------------------------------------------------------------ tables
    /** Cell that ignores its content's width so column weights align across rows. */
    private static JComponent cell(JComponent c) {
        Flex f = new Flex(new GridBagLayout()) {
            @Override public Dimension getPreferredSize() { return new Dimension(0, super.getPreferredSize().height); }
            @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
        };
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST; gc.weightx = 1;
        f.add(c, gc);
        return f;
    }

    private static Flex row(JComponent[] cells, double[] w, int padV) {
        Flex r = new Flex(new GridBagLayout());
        r.setBorder(new EmptyBorder(padV, 22, padV, 22));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < cells.length; i++) { gc.gridx = i; gc.weightx = w[i]; r.add(cell(cells[i]), gc); }
        return r;
    }

    static JComponent table(String[] heads, double[] w, List<JComponent[]> rows, IntConsumer onClick) {
        Round c = new Round(Color.WHITE, BORDER, 20);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        JComponent[] hs = new JComponent[heads.length];
        for (int i = 0; i < heads.length; i++) hs[i] = section(heads[i]);
        c.add(row(hs, w, 14));
        for (int i = 0; i < rows.size(); i++) {
            Flex r = row(rows.get(i), w, 13);
            r.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER), r.getBorder()));
            if (onClick != null) { final int idx = i; clickable(r, () -> onClick.accept(idx)); }
            c.add(r);
        }
        return c;
    }
}
