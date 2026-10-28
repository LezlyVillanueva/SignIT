package com.student.signit;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * PDF helpers built on Apache PDFBox 3.x: page count, page rendering for the on-screen preview,
 * and stamping signature PNGs onto the real PDF pages.
 */
final class PdfTools {
    private PdfTools() { }

    /** One signature to burn into a PDF page. cx/cy = centre, w = width, all as fractions of the visible page. */
    static final class Stamp {
        final byte[] png;
        final int page;
        final double cx, cy, w;
        final String label;
        Stamp(byte[] png, int page, double cx, double cy, double w, String label) {
            this.png = png; this.page = page; this.cx = cx; this.cy = cy; this.w = w; this.label = label;
        }
    }

    static int pageCount(File f) throws IOException {
        try (PDDocument doc = Loader.loadPDF(f)) {
            return doc.getNumberOfPages();
        }
    }

    /** Renders one page (0-based) to an image {@code pxWidth} pixels wide. The file is closed again straight away. */
    static BufferedImage render(File f, int page, int pxWidth) throws IOException {
        try (PDDocument doc = Loader.loadPDF(f)) {
            PDPage pg = doc.getPage(page);
            PDRectangle box = pg.getCropBox();
            int rot = rotation(pg);
            float widthPts = (rot == 90 || rot == 270) ? box.getHeight() : box.getWidth();
            float scale = pxWidth / widthPts;
            return new PDFRenderer(doc).renderImage(page, scale);
        }
    }

    private static int rotation(PDPage pg) {
        return ((pg.getRotation() % 360) + 360) % 360;
    }

    /** Writes {@code dest} = {@code src} with every stamp drawn on top. {@code src} is never modified. */
    static void stampAll(File src, File dest, List<Stamp> stamps) throws IOException {
        File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
        try (PDDocument doc = Loader.loadPDF(src)) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (Stamp s : stamps) {
                if (s.page < 0 || s.page >= doc.getNumberOfPages()) continue;
                PDPage pg = doc.getPage(s.page);
                PDRectangle box = pg.getCropBox();
                int rot = rotation(pg);
                boolean sideways = rot == 90 || rot == 270;
                float dw = sideways ? box.getHeight() : box.getWidth();     // size of the page as the viewer shows it
                float dh = sideways ? box.getWidth() : box.getHeight();

                PDImageXObject img = PDImageXObject.createFromByteArray(doc, s.png, "signature");
                float w = (float) (s.w * dw);
                float h = w * img.getHeight() / img.getWidth();
                float x = (float) (s.cx * dw) - w / 2f;
                float y = (float) ((1 - s.cy) * dh) - h / 2f;               // PDF y axis points up

                try (PDPageContentStream cs = new PDPageContentStream(doc, pg,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    // move to the crop box origin, then map "viewer" coordinates onto the (possibly rotated) page
                    cs.transform(Matrix.getTranslateInstance(box.getLowerLeftX(), box.getLowerLeftY()));
                    if (rot == 90) cs.transform(new Matrix(0, 1, -1, 0, dh, 0));
                    else if (rot == 180) cs.transform(new Matrix(-1, 0, 0, -1, dw, dh));
                    else if (rot == 270) cs.transform(new Matrix(0, -1, 1, 0, 0, dw));

                    cs.drawImage(img, x, y, w, h);

                    cs.beginText();
                    cs.setFont(font, 7);
                    cs.setNonStrokingColor(0.35f, 0.35f, 0.40f);
                    cs.newLineAtOffset(x, Math.max(4f, y - 9f));
                    cs.showText(ascii(s.label));
                    cs.endText();
                }
            }
            doc.save(tmp);
        }
        Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** The built-in PDF fonts only cover basic Latin, so anything else becomes '?'. */
    private static String ascii(String s) {
        return s == null ? "" : s.replaceAll("[^\\x20-\\x7E]", "?");
    }
}