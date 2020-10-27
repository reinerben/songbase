package oanavodo.songbase.playlist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 *
 * @author oana
 */
public class UTF8BufferedReader extends BufferedReader {

    private boolean firstread = true;

    public UTF8BufferedReader(Reader in) {
        super(in);
    }

    @Override
    public String readLine() throws IOException {
        boolean first = firstread;
        firstread = false;
        String line =  super.readLine();
        if (first && (line.charAt(0) == '\uFEFF')) line = line.substring(1);
        return line;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if ((off < 0) || (len < 0) || ((off + len) > cbuf.length)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) return 0;

        if (firstread) {
            firstread = false;
            int bom = super.read();
            if (bom == -1) return -1;
            if (bom != 0xFEFF) {
                cbuf[off] = (char)bom;
                return super.read(cbuf, off + 1, len - 1) + 1;
            }
        }

        return super.read(cbuf, off, len);
    }
}
