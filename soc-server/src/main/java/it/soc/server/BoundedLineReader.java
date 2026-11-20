package it.soc.server;

import java.io.IOException;
import java.io.Reader;

public class BoundedLineReader {

    private final Reader reader;
    private final int maxLength;

    public BoundedLineReader(Reader reader, int maxLength) {
        this.reader = reader;
        this.maxLength = maxLength;
    }

    public String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int next;

        while ((next = reader.read()) != -1) {
            if (next == '\n') {
                int length = line.length();

                if (length > 0 && line.charAt(length - 1) == '\r') {
                    line.setLength(length - 1);
                }

                return line.toString();
            }

            if (line.length() >= maxLength) {
                throw new IOException("Riga troppo lunga");
            }

            line.append((char) next);
        }

        return line.length() == 0 ? null : line.toString();
    }
}
