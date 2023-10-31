package whu.edu.cs.transitnet;
import edu.whu.hyk.encoding.Decoder;
import edu.whu.hyk.encoding.Encoder;
import edu.whu.hyk.util.GeoUtil;
import org.junit.Test;
import whu.edu.cs.transitnet.service.GeneratorService_merge;

import java.io.IOException;
import java.text.ParseException;

public class generate_Qr_Test {
    @Test
    public void ExpTest() throws InterruptedException, IOException, ParseException {
        double[] S = {40.810,-73.920};
        double minlat = S[0];
        double minlon = S[1];
        double lat1 = GeoUtil.increaseLat(minlat,5000 );
        double lon1 = GeoUtil.increaseLng(minlat,minlon,5000);
        System.out.println("40.8100"+", "+"-73.9200"+", "+lat1+", "+lon1);
    }
}
