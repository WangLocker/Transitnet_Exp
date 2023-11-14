package whu.edu.cs.transitnet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import whu.edu.cs.transitnet.service.index.HistoricalTripIndex;

import java.io.IOException;
import java.text.ParseException;

@RunWith(SpringRunner.class)
@SpringBootTest
@MapperScan("whu.edu.cs.transitnet.*")
public class createindextest {
    @Autowired
    HistoricalTripIndex his_index;

    @Test
    public void createindextest() throws InterruptedException, IOException, ParseException {
        System.out.println("11111111111111111111111111111");
    }
}
