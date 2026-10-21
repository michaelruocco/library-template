package uk.co.mruoc;

import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;

import java.awt.Color;

public class PdfStyles {

    private static final Color TEAL = new Color(0, 166, 166);
    private static final Color GREY = new Color(89, 89, 89);

    private static final Font SECTION_HEADING_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, TEAL);
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, GREY);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, GREY);

    private Color sectionHeadingColor() {
        return TEAL;
    }

    private Color textColor() {
        return GREY;
    }

    public Color tableBorderColor() {
        return GREY;
    }

    private Font sectionHeadingFont() {
        return SECTION_HEADING_FONT;
    }

    public Font headingFont() {
        return HEADING_FONT;
    }

    public Font bodyFont() {
        return BODY_FONT;
    }

    public Paragraph toSectionHeading(String text) {
        return new Paragraph(text, SECTION_HEADING_FONT);
    }

    public Paragraph toHeading(String text) {
        return new Paragraph(text, HEADING_FONT);
    }

    public Paragraph toBody(String text) {
        return new Paragraph(text, BODY_FONT);
    }
}
