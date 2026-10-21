package uk.co.mruoc;

import lombok.RequiredArgsConstructor;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Image;

import java.io.IOException;
import java.util.Objects;

@RequiredArgsConstructor
public class PdfImage {

    private final String path;
    private final float fitWidth;
    private final float fitHeight;

    public PdfImage() {
        this("/logo.png", 160, 60);
    }

    public Image load() {
        try (var inputStream = getClass().getResourceAsStream(path)) {
            if (Objects.isNull(inputStream)) {
                throw new PdfException("logo not found on classpath at: " + path);
            }

            var image = Image.getInstance(inputStream.readAllBytes());
            image.scaleToFit(fitWidth, fitHeight);
            return image;
        } catch (IOException | DocumentException e) {
            throw new PdfException("failed to load logo", e);
        }
    }
}
