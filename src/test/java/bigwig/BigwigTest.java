package bigwig;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import java.io.*;

import org.testng.annotations.*;
import org.testng.log4testng.Logger;

import static org.testng.Assert.*;

/**
 * User: tdanford
 * Date: 8/5/13
 */
public class BigwigTest {

    private static Logger LOG = Logger.getLogger(BigwigTest.class);

    private static File testBigwigFile;

    @BeforeClass
    public void downloadData() throws IOException {
        String uri = "http://genome.ucsc.edu/goldenPath/help/examples/bigWigExample.bw";
        URL url = new URL(uri);

        File tmpDir = new File("testdata");
        if(!tmpDir.exists()) { tmpDir.mkdir(); }

        testBigwigFile = new File(tmpDir, "bigWigExample.bw");

        if(!testBigwigFile.exists()) {

            LOG.info(String.format("Downloading " + uri));

            HttpURLConnection cxn = (HttpURLConnection)url.openConnection();

            InputStream is = cxn.getInputStream();
            OutputStream os = new FileOutputStream(testBigwigFile);

            byte[] buffer = new byte[1024*10];
            int read = -1;

            while((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }

            is.close();
            os.close();

            LOG.info(String.format("Finished downloading"));
        }
    }

    @Test
    public void testLoad() throws IOException {
        Bigwig bigwig = new Bigwig(testBigwigFile);
        assertNotNull(bigwig);
    }
}
