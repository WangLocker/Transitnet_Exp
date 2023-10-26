package whu.edu.cs.transitnet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import whu.edu.cs.transitnet.param.QueryKnnHisParam;
import whu.edu.cs.transitnet.service.EncodeService;
import whu.edu.cs.transitnet.service.index.CubeId;
import whu.edu.cs.transitnet.service.index.HistoricalTripIndex;
import whu.edu.cs.transitnet.service.index.HytraEngineManager;

import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@MapperScan("whu.edu.cs.transitnet.*")
public class GetCubeRangeTest {
    @Autowired
    EncodeService encodeService;

    private List<QueryKnnHisParam.Point> points;
    private Set<CubeId> cubeIds=new HashSet<>();

    @Test
    public void ExpTest() throws InterruptedException, IOException, ParseException {
        generatePoints();
        for (int i=0;i<points.size();i++) {

            String recordedTime = points.get(i).getTime();
            Date parse = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(recordedTime);
            Long time = parse.getTime();

            CubeId cubeId = encodeService.encodeCube(points.get(i).getLat(), points.get(i).getLng(), time);
            cubeIds.add(cubeId);
        }
        try {
            FileWriter fileWriter = new FileWriter("D:\\datasets\\all_cubes.txt");
            for (CubeId item : cubeIds) {
                fileWriter.write(item.toString() + "\n");
            }
            fileWriter.close();
            System.out.println("HashSet items saved " );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void generatePoints() {
        points=new ArrayList<>();
        double latStart = 40.810;
        double latEnd = 40.815;
        double lonStart = -73.915;
        double lonEnd = -73.910;

        double latStep = 0.001;
        double lonStep = 0.001;
        int timeStep = 30; // 30秒的步长

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        for (double lat = latStart; lat <= latEnd; lat += latStep) {
            int t=0;
            for (double lon = lonStart; t<6; lon += lonStep) {
                t++;
                Calendar currentTime = Calendar.getInstance();
                currentTime.set(2023, 4, 20, 0, 0, 0); // 月份从0开始，所以4表示5月

                Calendar timeEnd = Calendar.getInstance();
                timeEnd.set(2023, 4, 20, 23, 59, 59); // 结束时间点

                while (currentTime.get(Calendar.HOUR_OF_DAY) != 0) {
                    currentTime.add(Calendar.SECOND, -timeStep);
                }

                while (currentTime.compareTo(timeEnd) <= 0) {
                    points.add(new QueryKnnHisParam.Point(lat, lon, sdf.format(currentTime.getTime())));
                    currentTime.add(Calendar.SECOND, timeStep);
                }

            }
        }
    }
}
