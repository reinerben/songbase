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

    public static Path testdir = Paths.get(System.getProperty("basedir"), "target/test");

    public static Path resourcePath(String resource) throws FileNotFoundException, URISyntaxException {
        URL url = SongBaseTest.class.getResource(resource);
        if (url == null) throw new FileNotFoundException(resource);
        return Paths.get(url.toURI());
    }

    public SongBaseTest() {}

    @TestFactory
    DynamicTest[] allTests() {
        return new DynamicTest[] {
            songTest("sort1", "--sort  %!Playlist1.m3u%   %!Playlist2.m3u8%"),
            songTest("sort2", "--sort -%=Playlist1.m3u%  >%*Playlist1.m3u%"),
            songTest("sort3", "--sort -%=Playlist2.m3u8% >%*Playlist2.m3u8%"),
            songTest("sort4", "--sort <%=Playlist1.m3u%  >%*Playlist1.m3u%"),
            songTest("sort5", "--sort <%=Playlist2.m3u8% >%*Playlist2.m3u8%"),
        };
    }

    DynamicTest songTest(String name, String command) {
        return DynamicTest.dynamicTest(name + " Test", () -> new SongCommand(name, command).call());
    }
}
