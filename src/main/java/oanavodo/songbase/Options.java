package oanavodo.songbase;

/**
 * Encapsulate command line options.
 * @author Reiner
 */
public class Options {

    public static enum Check { NO, YES, ONLY };

    public Check check = Check.YES;
    public boolean dryrun = false;
}
