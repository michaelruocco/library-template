package uk.co.mruoc;

import java.io.OutputStream;

public interface PdfFactory {
    void toPdf(RequestForm form, OutputStream outputStream);
}
