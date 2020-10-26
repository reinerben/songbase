package oanavodo.songbase.playlist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 *
 * @author oana
 */
public class UTF8BufferedReader extends BufferedReader {

    private boolean firstline = true;

    public UTF8BufferedReader(Reader in) {
        super(in);
    }

    @Override
    public String readLine() throws IOException {
        String line =  super.readLine();
        if (firstline) {
            if (line.charAt(0) == '\uFEFF') line = line.substring(1);
            firstline = false;
        }
        return line;
    }
}
