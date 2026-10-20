package uk.co.mruoc;

import lombok.Builder;
import org.openpdf.text.Rectangle;

@Builder
public record PdfFormConfig(String title, Rectangle pageSize) {
    // intentionally blank
}
