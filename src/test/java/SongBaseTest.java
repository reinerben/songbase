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

    public static Path resourcePath(String resource) throws FileNotFoundException, URISyntaxException {
        URL url = SongBaseTest.class.getResource(resource);
        if (url == null) throw new FileNotFoundException(resource);
        return Paths.get(url.toURI());
    }

    public SongBaseTest() {}

    @TestFactory
    DynamicTest[] allTests() {
        return new DynamicTest[] {
//            songTest("shuffle1", "--shuffle -%Playsorted1.m3u% >%Playmixed1.m3u%"),
//            songTest("shuffle2", "--shuffle -%Playsorted2.m3u% >%Playmixed2.m3u%"),
//            songTest("shuffle3", "--shuffle -%Playsorted3.m3u% >%Playmixed3.m3u%"),
//            songTest("shuffle4", "--shuffle -%Playsorted1.m3u8% >%Playmixed1.m3u8%"),
//            songTest("shuffle5", "--shuffle -%Playsorted2.m3u8% >%Playmixed2.m3u8%"),
//            songTest("shuffle6", "--shuffle -%Playsorted3.m3u8% >%Playmixed3.m3u8%"),
            songTest("sort1", "--sort  %Playmixed1.m3u=Playsorted1.m3u% %Playmixed2.m3u8=Playsorted2.m3u8%"),
            songTest("sort2", "--sort -%Playmixed1.m3u=%   >%=Playsorted1.m3u%"),
            songTest("sort3", "--sort -%Playmixed2.m3u8=%  >%=Playsorted2.m3u8%"),
            songTest("sort4", "--sort --base=%run% --type=m3u  - <%Playmixed1.m3u%  >%=Playsorted1.m3u%"),
            songTest("sort5", "--sort --base=%run% --type=m3u8 - <%Playmixed2.m3u8% >%=Playsorted2.m3u8%"),
        };
    }

    DynamicTest songTest(String name, String command) {
        return DynamicTest.dynamicTest(name + " Test", () -> new SongCommand(name, command).call());
    }
}
