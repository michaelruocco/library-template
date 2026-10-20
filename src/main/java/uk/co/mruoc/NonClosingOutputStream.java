package uk.co.mruoc;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

class NonClosingOutputStream extends FilterOutputStream {

    public NonClosingOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}
