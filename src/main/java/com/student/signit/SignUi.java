package com.student.signit;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Image helpers for signature PNGs. */
final class SigTools {
    private SigTools() { }

    static BufferedImage decode(byte[] b) {
        try { return b == null ? null : ImageIO.read(new ByteArrayInputStream(b)); }
        catch (IOException e) { return null; }
    }

    static byte[] png(BufferedImage img) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try { ImageIO.write(img, "png", o); } catch (IOException ignored) { }
        return o.toByteArray();
    }

    static BufferedImage argb(BufferedImage src) {
        BufferedImage o = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return o;
    }

    static boolean hasTransparency(BufferedImage img) {
        if (!img.getColorModel().hasAlpha()) return false;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if ((img.getRGB(x, y) >>> 24) < 250) return true;
        return false;
    }

    /** Turns white / near-white pixels transparent (soft edge) so a scanned signature blends into the page. */
    static BufferedImage stripWhite(BufferedImage src) {
        BufferedImage o = argb(src);
        for (int y = 0; y < o.getHeight(); y++)
            for (int x = 0; x < o.getWidth(); x++) {
                int p = o.getRGB(x, y);
                int a = p >>> 24, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                int min = Math.min(r, Math.min(g, b));
                if (min >= 245) a = 0;
                else if (min > 200) a = a * (245 - min) / 45;
                o.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        return o;
    }

    /** Trims empty (transparent) margins and scales very large images down. */
    static BufferedImage cropAndFit(BufferedImage src) {
        int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++)
                if ((src.getRGB(x, y) >>> 24) > 16) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
        BufferedImage out = src;
        if (maxX >= minX && maxY >= minY) {
            int pad = 4;
            int x0 = Math.max(0, minX - pad), y0 = Math.max(0, minY - pad);
            int x1 = Math.min(src.getWidth(), maxX + pad + 1), y1 = Math.min(src.getHeight(), maxY + pad + 1);
            out = src.getSubimage(x0, y0, x1 - x0, y1 - y0);
        }
        if (out.getWidth() > 900) {
            int w = 900, h = Math.max(1, out.getHeight() * 900 / out.getWidth());
            BufferedImage s = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = s.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(out, 0, 0, w, h, null);
            g.dispose();
            out = s;
        } else out = argb(out);
        return out;
    }

    /** The user's name rendered in a script-style font as a transparent PNG. */
    static byte[] typedPng(String name) {
        Set<String> fams = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        Font f = null;
        for (String c : new String[]{"Segoe Script", "Brush Script MT", "Lucida Handwriting", "Snell Roundhand", "Apple Chancery"})
            if (fams.contains(c)) { f = new Font(c, Font.PLAIN, 56); break; }
        if (f == null) f = new Font(Font.SERIF, Font.ITALIC, 56);
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = tmp.createGraphics();
        FontMetrics fm = m.getFontMetrics(f);
        int w = fm.stringWidth(name) + 40, h = fm.getHeight() + 30, asc = fm.getAscent();
        m.dispose();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(f);
        g.setColor(new Color(0x1B2A6B));
        g.drawString(name, 20, 15 + asc);
        g.dispose();
        return png(cropAndFit(img));
    }

    /** File chooser -> PNG asset (optionally making a white background transparent). Returns null if cancelled/invalid. */
    static SigAsset upload(Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose a signature image");
        fc.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, GIF)", "png", "jpg", "jpeg", "gif"));
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile();
        try {
            if (f.length() > 5_000_000) { Main.info(parent, "Please choose an image smaller than 5 MB."); return null; }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(f.toPath())));
            if (img == null) { Main.info(parent, "That file is not a valid image."); return null; }
            img = argb(img);
            if (!hasTransparency(img)) {
                int r = JOptionPane.showConfirmDialog(parent,
                        "This image has a solid background.\nMake the white background transparent so the signature blends into documents?",
                        "Signature background", JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.CLOSED_OPTION) return null;
                if (r == JOptionPane.YES_OPTION) img = stripWhite(img);
            }
            String n = f.getName();
            int dot = n.lastIndexOf('.');
            if (dot > 0) n = n.substring(0, dot);
            return new SigAsset(n, png(cropAndFit(img)));
        } catch (Exception e) {
            Main.info(parent, "Could not read that file.");
            return null;
        }
    }

    /** Small modal pad to draw a signature with the mouse. Returns a transparent PNG or null. */
    static byte[] draw(Component parent) {
        final List<List<Point>> strokes = new ArrayList<>();
        final Color ink = new Color(0x14213D);
        JComponent pad = new JComponent() {
            { setPreferredSize(new Dimension(440, 170));
              MouseAdapter m = new MouseAdapter() {
                  @Override public void mousePressed(MouseEvent e) { List<Point> s = new ArrayList<>(); s.add(e.getPoint()); strokes.add(s); repaint(); }
                  @Override public void mouseDragged(MouseEvent e) { if (!strokes.isEmpty()) { strokes.get(strokes.size() - 1).add(e.getPoint()); repaint(); } }
              };
              addMouseListener(m); addMouseMotionListener(m);
              setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                g2.setColor(Color.WHITE); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(new Color(0xF4B6D3));
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.drawLine(20, getHeight() - 40, getWidth() - 20, getHeight() - 40);
                paintStrokes(g2, strokes, ink);
                if (strokes.isEmpty()) {
                    g2.setFont(Ui.font(Font.PLAIN, 12.5f)); g2.setColor(Ui.HINT);
                    g2.drawString("Sign here with your mouse or trackpad", 20, getHeight() - 18);
                }
                g2.dispose();
            }
        };
        Ui.Flex f = Ui.vbox();
        f.setBorder(new EmptyBorder(6, 6, 6, 6));
        f.add(pad);
        f.add(Ui.gap(8));
        f.add(Ui.btn("Clear", Ui.BStyle.SOFT, () -> { strokes.clear(); pad.repaint(); }).small());
        if (JOptionPane.showConfirmDialog(parent, f, "Draw your signature", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION || strokes.isEmpty()) return null;
        BufferedImage img = new BufferedImage(pad.getWidth(), pad.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintStrokes(g, strokes, ink);
        g.dispose();
        return png(cropAndFit(img));
    }

    private static void paintStrokes(Graphics2D g2, List<List<Point>> strokes, Color ink) {
        g2.setColor(ink);
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (List<Point> s : strokes) {
            if (s.size() == 1) { Point p = s.get(0); g2.fillOval(p.x - 1, p.y - 1, 3, 3); continue; }
            Path2D path = new Path2D.Float();
            path.moveTo(s.get(0).x, s.get(0).y);
            for (int i = 1; i < s.size(); i++) path.lineTo(s.get(i).x, s.get(i).y);
            g2.draw(path);
        }
    }
}

/** Misc small UI helpers for the signing screens. */
final class SignUi {
    private SignUi() { }

    static JComponent centered(JComponent c) {
        Ui.Flex f = new Ui.Flex(new FlowLayout(FlowLayout.CENTER, 0, 0));
        f.add(c);
        return f;
    }

    /** "Page 2 of 5" with previous / next buttons; hides itself for single-page papers. */
    static JComponent pager(SignPaper p) {
        Ui.Flex row = new Ui.Flex(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JLabel l = Ui.small(" ");
        Ui.Btn prev = Ui.btn("\u2039 Prev", Ui.BStyle.SOFT, () -> p.goPage(p.viewPage() - 1)).small();
        Ui.Btn next = Ui.btn("Next \u203a", Ui.BStyle.SOFT, () -> p.goPage(p.viewPage() + 1)).small();
        row.add(prev); row.add(l); row.add(next);
        Runnable upd = () -> {
            row.setVisible(p.isPdf() && p.pageCount() > 1);
            l.setText("Page " + (p.viewPage() + 1) + " of " + p.pageCount());
            prev.setEnabled(p.viewPage() > 0);
            next.setEnabled(p.viewPage() < p.pageCount() - 1);
            row.revalidate();
        };
        p.addListener(upd);
        upd.run();
        return row;
    }

    static void onChange(JTextComponent c, Runnable r) {
        c.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        });
    }
}

/** Non-interactive picture of a signature PNG (library tiles). */
final class SigPreview extends JComponent {
    private final BufferedImage img;
    SigPreview(byte[] png, int w, int h) {
        img = SigTools.decode(png);
        setPreferredSize(new Dimension(w, h));
        setOpaque(false);
        setAlignmentX(0f);
    }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Ui.aa(g);
        g2.setColor(Ui.FIELD);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        if (img != null) {
            double sc = Math.min((getWidth() - 20.0) / img.getWidth(), (getHeight() - 16.0) / img.getHeight());
            int w = (int) (img.getWidth() * sc), h = (int) (img.getHeight() * sc);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.drawImage(img, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        }
        g2.dispose();
    }
}

/**
 * A paper page that shows a document and accepts ONE dropped signature (movable / resizable),
 * plus any number of already-placed, read-only signatures.
 */
final class SignPaper extends JComponent {
    static final int W = 480, H = 680;

    private static final class Mark {
        BufferedImage img; double x, y, w; String label;
    }

    private boolean letter;
    private String heading = "", meta = "", recipient, body = "";
    private final List<Mark> fixed = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    // PDF mode: the page images come from a real PDF instead of generated text
    private File pdf;
    private int pageCount = 1, viewPage = 0, sigPage = 0;
    private BufferedImage pageImg;

    private byte[] png;
    private BufferedImage img;
    private double cx = 0.7, cy = 0.8, w = 0.3;

    SignPaper() {
        setOpaque(false);
        setPreferredSize(new Dimension(W, H));
        MouseAdapter ma = new MouseAdapter() {
            Point off;
            @Override public void mousePressed(MouseEvent e) {
                if (sigVisible() && rect().contains(e.getPoint()))
                    off = new Point(e.getX() - (int) (cx * getWidth()), e.getY() - (int) (cy * getHeight()));
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (off == null) return;
                cx = clamp((e.getX() - off.x) / (double) getWidth(), 0, 1);
                cy = clamp((e.getY() - off.y) / (double) getHeight(), 0, 1);
                changed();
            }
            @Override public void mouseReleased(MouseEvent e) { off = null; }
            @Override public void mouseMoved(MouseEvent e) {
                setCursor(sigVisible() && rect().contains(e.getPoint())
                        ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : Cursor.getDefaultCursor());
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (sigVisible() && rect().contains(e.getPoint())) {
                    w = clamp(w - e.getWheelRotation() * 0.02, 0.08, 0.7);
                    changed();
                } else if (getParent() != null) getParent().dispatchEvent(SwingUtilities.convertMouseEvent(SignPaper.this, e, getParent()));
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
        setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) {
                return s.isDrop() && SigTray.dragging != null && s.isDataFlavorSupported(DataFlavor.stringFlavor);
            }
            @Override public boolean importData(TransferSupport s) {
                if (!canImport(s)) return false;
                Point p = s.getDropLocation().getDropPoint();
                setSignature(SigTray.dragging.png, p.x / (double) getWidth(), p.y / (double) getHeight());
                SigTray.dragging = null;
                return true;
            }
        });
    }

    @Override public Dimension getMaximumSize() { return getPreferredSize(); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---- content
    void setContent(boolean letter, String heading, String meta, String recipient, String body) {
        this.letter = letter;
        this.heading = heading == null ? "" : heading;
        this.meta = meta == null ? "" : meta;
        this.recipient = recipient;
        this.body = body == null ? "" : body;
        repaint();
    }

    void addFixed(byte[] png, double x, double y, double w, String label) {
        BufferedImage i = SigTools.decode(png);
        if (i == null) return;
        Mark m = new Mark();
        m.img = i; m.x = x; m.y = y; m.w = w; m.label = label;
        fixed.add(m);
        repaint();
    }

    static SignPaper forDoc(Doc d, Store st) {
        SignPaper p = new SignPaper();
        boolean letter = "Letter".equals(d.type);
        User owner = st.byId(d.ownerId);
        String meta = letter ? Common.date(d.submitted)
                : "Submitted by " + (owner == null ? "?" : owner.name) + " · " + Common.date(d.submitted) + " · " + d.type;
        String body = d.description == null ? "" : d.description;
        if ("Budget Request".equals(d.type) && d.amount > 0) body += "\n\nRequested amount: PHP " + String.format("%,.2f", d.amount);
        p.setContent(letter, d.title, meta, d.recipient, body);
        File pdf = st.currentPdf(d);
        if (pdf != null) { p.setPdf(pdf); if (p.isPdf()) return p; }   // signatures are already stamped into the PDF itself
        if (d.ownerSig != null) p.addFixed(d.ownerSig, d.ownerX, d.ownerY, d.ownerW, owner == null ? "" : owner.name);
        for (Approval a : d.approvals)
            if (a.active && a.placed && a.sigImage != null) {
                User u = st.byId(a.userId);
                p.addFixed(a.sigImage, a.px, a.py, a.pw, u == null ? "" : u.name);
            }
        return p;
    }

    // ---- active signature
    void addListener(Runnable r) { listeners.add(r); }
    private void changed() { repaint(); for (Runnable r : new ArrayList<>(listeners)) r.run(); }

    void setSignature(byte[] png, double cx, double cy) {
        BufferedImage i = SigTools.decode(png);
        if (i == null) return;
        this.png = png; this.img = i; this.sigPage = viewPage;
        this.cx = clamp(cx, 0, 1); this.cy = clamp(cy, 0, 1);
        changed();
    }

    /** Click-to-place: drops the signature in the next free spot near the bottom right. */
    void place(byte[] png) {
        setSignature(png, 0.7, clamp(0.88 - 0.09 * fixed.size(), 0.25, 0.9));
    }

    void clear() { png = null; img = null; changed(); }

    private boolean sigVisible() { return img != null && (pdf == null || sigPage == viewPage); }

    // ---- PDF mode
    /** Show a real PDF instead of generated text (null switches back to the text page). */
    void setPdf(File f) {
        pdf = null; pageImg = null; pageCount = 1; viewPage = 0;
        if (f != null) {
            try { pageCount = PdfTools.pageCount(f); pdf = f; loadPage(); }
            catch (Exception e) { e.printStackTrace(); pdf = null; pageImg = null; pageCount = 1; }
        }
        if (pdf == null) setPreferredSize(new Dimension(W, H));
        revalidate();
        changed();
    }

    private void loadPage() throws IOException {
        pageImg = PdfTools.render(pdf, viewPage, W * 2);
        int h = (int) Math.round(W * (double) pageImg.getHeight() / pageImg.getWidth());
        setPreferredSize(new Dimension(W, h));
    }

    void goPage(int p) {
        if (pdf == null || p < 0 || p >= pageCount || p == viewPage) return;
        try { viewPage = p; loadPage(); revalidate(); changed(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    boolean isPdf() { return pdf != null; }
    int pageCount() { return pageCount; }
    int viewPage() { return viewPage; }
    /** 0-based page the active signature sits on. */
    int page() { return sigPage; }
    boolean hasSignature() { return img != null; }
    byte[] png() { return png; }
    double cx() { return cx; }
    double cy() { return cy; }
    double widthFraction() { return w; }
    void setWidthFraction(double v) { w = clamp(v, 0.08, 0.7); changed(); }

    private Rectangle rect() {
        if (img == null) return new Rectangle();
        int pw = (int) (w * getWidth());
        int ph = Math.max(1, pw * img.getHeight() / img.getWidth());
        return new Rectangle((int) (cx * getWidth()) - pw / 2, (int) (cy * getHeight()) - ph / 2, pw, ph);
    }

    // ---- painting
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Ui.aa(g);
        int pw = getWidth(), ph = getHeight();
        if (pageImg != null) {
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, pw, ph);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.drawImage(pageImg, 0, 0, pw, ph, null);
            g2.setColor(Ui.BORDER); g2.drawRect(0, 0, pw - 1, ph - 1);
        } else paintText(g2, pw, ph);

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        if (pageImg == null) for (Mark m : fixed) {
            int sw = (int) (m.w * pw), sh = Math.max(1, sw * m.img.getHeight() / m.img.getWidth());
            int x0 = (int) (m.x * pw) - sw / 2, y0 = (int) (m.y * ph) - sh / 2;
            g2.drawImage(m.img, x0, y0, sw, sh, null);
            g2.setColor(new Color(0x9A9AA6));
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawLine(x0, y0 + sh + 2, x0 + sw, y0 + sh + 2);
            g2.setFont(Ui.font(Font.PLAIN, 10));
            g2.drawString(m.label, x0, y0 + sh + 14);
        }
        if (sigVisible()) {
            Rectangle r = rect();
            g2.drawImage(img, r.x, r.y, r.width, r.height, null);
            g2.setColor(Ui.PRIMARY);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
            g2.drawRoundRect(r.x - 4, r.y - 4, r.width + 8, r.height + 8, 8, 8);
        } else if (hasDropHint && img == null) {
            g2.setFont(Ui.font(Font.PLAIN, 11.5f));
            g2.setColor(pageImg != null ? Ui.PRIMARY : Ui.HINT);
            String s = "Drop your signature here";
            g2.drawString(s, (pw - g2.getFontMetrics().stringWidth(s)) / 2, ph - 40);
        }
        g2.dispose();
    }

    /** The generated paper (used when no real PDF is attached). */
    private void paintText(Graphics2D g2, int pw, int ph) {
        g2.setColor(new Color(0, 0, 0, 18)); g2.fillRect(3, 4, pw - 4, ph - 4);
        g2.setColor(Color.WHITE); g2.fillRect(0, 0, pw - 4, ph - 4);
        g2.setColor(Ui.BORDER); g2.drawRect(0, 0, pw - 5, ph - 5);

        int mx = 42, tw = pw - 4 - mx * 2, y = 52, limit = ph - 60;
        g2.setColor(Ui.DARK);
        if (letter) {
            g2.setFont(Ui.font(Font.PLAIN, 12));
            g2.setColor(Ui.MUTED);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(meta, pw - 4 - mx - fm.stringWidth(meta), y);
            y += 34;
            g2.setColor(Ui.DARK);
            if (recipient != null && !recipient.isBlank()) y = wrap(g2, "To: " + recipient, Font.BOLD, 12.5f, mx, y, tw, 18, limit);
            y += 6;
            y = wrap(g2, heading.isBlank() ? "Subject: —" : "Subject: " + heading, Font.BOLD, 12.5f, mx, y, tw, 18, limit);
            y += 14;
        } else {
            y = wrap(g2, heading, Font.BOLD, 18, mx, y, tw, 24, limit);
            y += 2;
            g2.setColor(Ui.MUTED);
            y = wrap(g2, meta, Font.PLAIN, 11, mx, y, tw, 16, limit);
            y += 16;
            g2.setColor(Ui.DARK);
        }
        g2.setColor(Ui.DARK);
        wrap(g2, body, Font.PLAIN, 12.5f, mx, y, tw, 19, limit);
    }

    private boolean hasDropHint = false;
    void setDropHint(boolean on) { hasDropHint = on; repaint(); }

    /** Draws wrapped text; returns the y position after the last line. Stops at {@code limit}. */
    private static int wrap(Graphics2D g2, String text, int style, float size, int x, int y, int width, int lineH, int limit) {
        g2.setFont(Ui.font(style, size));
        FontMetrics fm = g2.getFontMetrics();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String t = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(t) > width && line.length() > 0) {
                    if (y > limit) return y;
                    g2.drawString(line.toString(), x, y);
                    y += lineH;
                    line = new StringBuilder(word);
                } else line = new StringBuilder(t);
            }
            if (y > limit) return y;
            g2.drawString(line.toString(), x, y);
            y += lineH;
        }
        return y;
    }
}

/** Library of the user's saved signatures; thumbnails can be dragged onto a {@link SignPaper} (or clicked). */
final class SigTray {
    private SigTray() { }

    /** The signature currently being dragged (read by the paper's drop handler). */
    static SigAsset dragging;
    private static final String MARK = "signit-signature";

    private static final class Thumb extends JComponent {
        final SigAsset a; final BufferedImage img; final SignPaper paper;
        Thumb(SigAsset a, SignPaper paper) {
            this.a = a; this.paper = paper; this.img = SigTools.decode(a.png);
            setPreferredSize(new Dimension(118, 66));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(a.name + " — drag onto the document, or click to place");
            TransferHandler th = new TransferHandler() {
                @Override public int getSourceActions(JComponent c) { return COPY; }
                @Override protected Transferable createTransferable(JComponent c) { dragging = Thumb.this.a; return new StringSelection(MARK); }
                @Override protected void exportDone(JComponent c, Transferable t, int action) { dragging = null; }
            };
            setTransferHandler(th);
            MouseAdapter m = new MouseAdapter() {
                Point press; boolean dragged;
                @Override public void mousePressed(MouseEvent e) { press = e.getPoint(); dragged = false; }
                @Override public void mouseDragged(MouseEvent e) {
                    if (press == null || dragged || press.distance(e.getPoint()) < 5) return;
                    dragged = true;
                    BufferedImage ghost = ghost();
                    th.setDragImage(ghost);
                    th.setDragImageOffset(new Point(ghost.getWidth() / 2, ghost.getHeight() / 2));
                    th.exportAsDrag(Thumb.this, e, TransferHandler.COPY);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (!dragged && press != null) { Thumb.this.paper.place(Thumb.this.a.png); repaint(); }
                    press = null;
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            paper.addListener(this::repaint);
        }
        BufferedImage ghost() {
            int gw = (int) (paper.widthFraction() * SignPaper.W);
            int gh = Math.max(1, gw * img.getHeight() / img.getWidth());
            BufferedImage g = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = g.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
            g2.drawImage(img, 0, 0, gw, gh, null);
            g2.dispose();
            return g;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.aa(g);
            boolean on = paper.png() == a.png;
            g2.setColor(Color.WHITE); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(on ? Ui.PRIMARY : Ui.BORDER);
            g2.setStroke(new BasicStroke(on ? 2f : 1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            if (img != null) {
                double sc = Math.min((getWidth() - 20.0) / img.getWidth(), (getHeight() - 22.0) / img.getHeight());
                int w = (int) (img.getWidth() * sc), h = (int) (img.getHeight() * sc);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.drawImage(img, (getWidth() - w) / 2, 6 + (getHeight() - 22 - h) / 2, w, h, null);
            }
            g2.setFont(Ui.font(Font.PLAIN, 10));
            g2.setColor(Ui.MUTED);
            String n = a.name.length() > 18 ? a.name.substring(0, 17) + "…" : a.name;
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(n, (getWidth() - fm.stringWidth(n)) / 2, getHeight() - 7);
            g2.dispose();
        }
    }

    /** Builds the tray card: thumbnails, size slider, remove, and a link to manage the library. */
    static Ui.Round build(Store st, User u, SignPaper paper, Runnable manage) {
        paper.setDropHint(true);
        Ui.Round card = new Ui.Round(new Color(0xFFF7FA), Ui.BORDER, 16);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.add(Ui.section("Your signatures"));
        card.add(Ui.gap(4));
        JLabel status = Ui.small("Drag a signature onto the page — or click one to place it.");
        card.add(status);
        card.add(Ui.gap(10));

        JPanel grid = new JPanel(new GridLayout(0, 3, 8, 8));
        grid.setOpaque(false);
        for (SigAsset a : st.sigs(u)) grid.add(new Thumb(a, paper));
        Ui.Flex wrap = new Ui.Flex(new BorderLayout());
        wrap.add(grid, BorderLayout.NORTH);
        card.add(wrap);
        card.add(Ui.gap(10));

        JSlider size = new JSlider(8, 70, (int) Math.round(paper.widthFraction() * 100));
        size.setOpaque(false);
        size.setPreferredSize(new Dimension(150, 24));
        size.addChangeListener(e -> { if (size.getValueIsAdjusting()) paper.setWidthFraction(size.getValue() / 100.0); });
        Ui.Flex row = Ui.hbox();
        row.add(Ui.small("Size"));
        row.add(Ui.hgap(8));
        row.add(size);
        row.add(Ui.hgap(10));
        row.add(Ui.btn("Remove", Ui.BStyle.SOFT, paper::clear).small());
        row.add(Box.createHorizontalGlue());
        if (manage != null) {
            Ui.Btn mg = Ui.btn("Manage signatures →", Ui.BStyle.LINK, manage);
            mg.padH = 0; mg.padV = 2; mg.setFont(Ui.font(Font.PLAIN, 12));
            row.add(mg);
        }
        card.add(row);

        paper.addListener(() -> {
            status.setText(paper.hasSignature() ? "Placed — drag it on the page to move it, scroll over it to resize."
                    : "Drag a signature onto the page — or click one to place it.");
            int v = (int) Math.round(paper.widthFraction() * 100);
            if (!size.getValueIsAdjusting() && size.getValue() != v) size.setValue(v);
        });
        return card;
    }
}