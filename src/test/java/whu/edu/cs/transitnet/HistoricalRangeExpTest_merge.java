package whu.edu.cs.transitnet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import whu.edu.cs.transitnet.service.HistoricalRangeService_merge;

import java.io.IOException;
import java.text.ParseException;

@RunWith(SpringRunner.class)
@SpringBootTest
@MapperScan("whu.edu.cs.transitnet.*")
public class HistoricalRangeExpTest_merge {
    @Autowired
    HistoricalRangeService_merge historicalrangeExpService;

    @Test
    public void ExpTest() throws InterruptedException, IOException, ParseException {
        double []ps={30.5000, 114.2000, 30.5267, 114.2313};
        String date="2023-05-20";
        historicalrangeExpService.setup(ps,date);
        historicalrangeExpService.historaical_range_search();
    }
}
