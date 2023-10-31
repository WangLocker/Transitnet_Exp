package whu.edu.cs.transitnet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import whu.edu.cs.transitnet.service.HistoricalRangeService_nomerge;

import java.io.IOException;
import java.text.ParseException;

@RunWith(SpringRunner.class)
@SpringBootTest
@MapperScan("whu.edu.cs.transitnet.*")
public class HistoricalRangeExpTest_nomerge {
    @Autowired
    HistoricalRangeService_nomerge historicalrangeExpService;

    @Test
    public void ExpTest() throws InterruptedException, IOException, ParseException {
        double []ps={40.8100, -73.9200, 40.8367, -73.8840};
        String date="2023-05-20";
        historicalrangeExpService.setup(ps,date);
        double average=0;
        average=historicalrangeExpService.historaical_range_search();
        System.out.println("【average time】============");
        System.out.println(average+" ms");
    }
}
