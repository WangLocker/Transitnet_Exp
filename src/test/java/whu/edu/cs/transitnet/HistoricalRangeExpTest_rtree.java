package whu.edu.cs.transitnet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import whu.edu.cs.transitnet.service.HistoricalRangeService_Rtree;
import whu.edu.cs.transitnet.service.HistoricalRangeService_nomerge;
import whu.edu.cs.transitnet.service.index.TripId;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
@MapperScan("whu.edu.cs.transitnet.*")
public class HistoricalRangeExpTest_rtree {
    @Autowired
    HistoricalRangeService_Rtree historicalrangeExpService;

    @Test
    public void ExpTest() throws InterruptedException, IOException, ParseException {
        double []ps={40.8100, -73.9200, 40.8367, -73.8840};
        historicalrangeExpService.setup(ps);
        List<TripId> res=historicalrangeExpService.rtreeTraj();
    }
}
