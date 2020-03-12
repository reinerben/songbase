package oanavodo.songbase;

public class Options {

    public static enum Check { NO, YES, ONLY };

    public Check check = Check.YES;
    public boolean dryrun = false;
}
