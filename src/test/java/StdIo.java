import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

class StdIo {

    InputStream stdin = null;
    PrintStream stdout = null;
    PrintStream stderr = null;
    private InputStream saved_stdin = null;
    private PrintStream saved_stdout = null;
    private PrintStream saved_stderr = null;

    public void setIn(InputStream in) {
        stdin = in;
    }

    public void setOut(PrintStream out) {
        stdout = out;
    }

    public void setErr(PrintStream err) {
        stderr = err;
    }

    public void set() {
        if (stdin != null) {
            saved_stdin = System.in;
            System.setIn(stdin);
            stdin = null;
        }
        if (stdout != null) {
            saved_stdout = System.out;
            System.setOut(stdout);
            stdout = null;
        }
        if (stderr != null) {
            saved_stderr = System.err;
            System.setErr(stderr);
            stderr = null;
        }
    }

    public void reset() {
        if (saved_stdin != null) {
            try {
                System.in.close();
            } catch (IOException e) {}
            System.setIn(saved_stdin);
            saved_stdin = null;
        }
        if (saved_stdout != null) {
            System.out.close();
            System.setOut(saved_stdout);
            saved_stdout = null;
        }
        if (saved_stderr != null) {
            System.err.close();
            System.setErr(saved_stderr);
            saved_stderr = null;
        }
    }
}
