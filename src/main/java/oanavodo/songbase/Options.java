package oanavodo.songbase;

/**
 * Encapsulate command line options.
 * @author Reiner
 */
public class Options {

    public static enum Check { NO, YES, ONLY };

    private Check check = Check.YES;
    private boolean dryrun = false;

    public Check getCheck() {
        return check;
    }

    public void setCheck(Check check) {
        this.check = check;
    }

    public boolean isDryrun() {
        return dryrun;
    }

    public void setDryrun(boolean dryrun) {
        this.dryrun = dryrun;
    }
}
