package oanavodo.songbase.test;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 *
 * @author Reiner
 */
public class SongBaseTest {

    public static final Path basedir = Paths.get(System.getProperty("basedir"));
    public static final Path testdir = basedir.resolve("target/test");

    public static enum TestOption { NOCREATE, REPLACEOUT };

    public static Path resourcePath(String resource) throws FileNotFoundException, URISyntaxException {
        URL url = SongBaseTest.class.getResource(resource);
        if (url == null) throw new FileNotFoundException(resource);
        return Paths.get(url.toURI());
    }

    public SongBaseTest() {}

    /**
     * Test calls of songbase main.
     * Possible subsitutes:<br>
     * %run%<br>
     * %[&lt;ref&gt;:]&lt;file&gt;%<br>
     * %[&lt;ref&gt;:]&lt;file&gt;=%<br>
     * %[&lt;ref&gt;:]&lt;file&gt;=&lt;cmpfile&gt;%<br>
     * %=&lt;cmpfile&gt;%<br>
     * <ul>
     * <li>%run% is relaced by the run folder of the test, where all involved files are copied to.</li>
     * <li>&lt;file&gt; input file which is copied from the test resources to the test run folder.
     * If it is appended by a equal sign the file is checked after test if unchanged.</li>
     * <li>&lt;cmpfile&gt; compare file which is copied from the test resources.
     * After test input file is checked if it has same content as compare file.
     * If only equal sign and no input file is specified (for output only), an generated filename is used which content is checked after test run.</li>
     * <li>[&lt;ref&gt;:] name of a previous test. The input file is taken from the run folder of this test instead of the test resources.</li>
     * </ul>
     * If a file substitute is prefixed by the following signs:
     * <ul>
     * <li>&lt; standard input is taken from the file and file name is removed from the command line.</li>
     * <li>&gt; standard output is set the file and file name is removed from the command line.</li>
     * <li>2&gt; standard error is set the file and file name is removed from the command line.</li>
     * <li>@ file name is removed from the command line. Use this if only file should be copied and songs should be created.</li>
     * </ul>
     */
    @TestFactory
    DynamicTest[] allTests() {
        return new DynamicTest[] {
            // check tests
            songTest("check1", "--base=%run% --check %Playsorted1.m3u% 2>%=check/check1.out%"),
            songTest("check2", "--base=%run% --check %Playsorted2.m3u8% 2>%=check/check2.out%", TestOption.NOCREATE, TestOption.REPLACEOUT),
            songTest("check3", "--base=%run% --check @%Playsorted1.m3u% @%Playsorted2.m3u8% 2>%=check/check3.out%"),
            // check UTF-8 with BOM
            songTest("check4", "--base=%run% --check %check/Playcheck4.m3u8% 2>%=check/check4.out%", TestOption.NOCREATE, TestOption.REPLACEOUT),
            // convert tests
            songTest("convert1", "--out %=Playsorted1.m3u8% %Playsorted1.m3u=%"),
            songTest("convert2", "--out %=Playsorted2.m3u%  %Playsorted2.m3u8=%"),
            songTest("convert3", "--type=m3u --out %=Playsorted1.m3u8% <%convert1:Playsorted1.m3u%"),
            songTest("convert4", "--type=m3u --out - %convert2:Playsorted2.m3u8% >%=Playsorted2.m3u%"),
            songTest("convert5", "--out Rock/%=convert/convert5.m3u% %Playsorted1.m3u=%"),
            songTest("convert6", "--out %=Playsorted1.m3u% Rock/%convert/convert5.m3u=%"),
            // map tests
            songTest("map1", "--base=%run% --map Rock=Other %map/map1input.m3u=map/map1result3.m3u% @%Playsorted1.m3u=map/map1result1.m3u% @%Playsorted2.m3u8=map/map1result2.m3u8%"),
            // shuffle and sort tests
            songTest("shuffle1", "--shuffle %Playsorted1.m3u% %Playsorted2.m3u8%"),
            songTest("sort1", "--sort  %shuffle1:Playsorted1.m3u=Playsorted1.m3u% %shuffle1:Playsorted2.m3u8=Playsorted2.m3u8%"),
            songTest("shuffle2", "--shuffle %Playsorted1.m3u%  --out - >%Playmixed1.m3u%"),
            songTest("shuffle3", "--shuffle %Playsorted2.m3u8% --out - >%Playmixed2.m3u8%"),
            songTest("sort2", "--sort %shuffle2:Playmixed1.m3u=%  --out - >%=Playsorted1.m3u%"),
            songTest("sort3", "--sort %shuffle3:Playmixed2.m3u8=% --out - >%=Playsorted2.m3u8%"),
            songTest("shuffle4", "--base=%run% --shuffle --type=m3u  - <%Playsorted1.m3u% >%Playmixed1.m3u%"),
            songTest("shuffle5", "--base=%run% --shuffle --type=m3u8 - <%Playsorted2.m3u8% >%Playmixed2.m3u8%"),
            songTest("sort4", "--base=%run% --sort --type=m3u  - <%shuffle4:Playmixed1.m3u%  >%=Playsorted1.m3u%"),
            songTest("sort5", "--base=%run% --sort --type=m3u8 - <%shuffle5:Playmixed2.m3u8% >%=Playsorted2.m3u8%"),
        };
    }

    DynamicTest songTest(String name, String command, TestOption... option) {
        return DynamicTest.dynamicTest(name + " Test", () -> new SongCommand(name, command, option).call());
    }
}
