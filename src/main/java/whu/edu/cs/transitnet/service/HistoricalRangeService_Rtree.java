package whu.edu.cs.transitnet.service;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Rectangle;
import edu.whu.hyk.encoding.Decoder;
import edu.whu.hyk.encoding.Encoder;
import edu.whu.hyk.model.Point;
import edu.whu.hyk.model.PostingList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rx.Observable;
import whu.edu.cs.transitnet.pojo.RealTimePointEntity;
import whu.edu.cs.transitnet.service.index.*;

import java.io.*;
import java.sql.*;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class HistoricalRangeService_Rtree {
    @Autowired
    HytraEngineManager hytraEngineManager;

    @Autowired
    HistoricalTripIndex historicalTripIndex;

    @Autowired
    EncodeService encodeService;

    @Autowired
    DecodeService decodeService;


    public double[] spatial_range={};

    public HashMap<TripId, ArrayList<RealTimePointEntity>> TPlist=new HashMap<>();
    public HashMap<TripId, ArrayList<RealTimePointEntity>> TPlist_temp=new HashMap<>();
    public void buildTrajDB() throws IOException {
        DeserializeHashMap();
        double rate=1.0;
        int num=(int)(rate*TPlist_temp.size());
        int it=0;
        for(TripId tid:TPlist_temp.keySet()){
            if(it==num){
                break;
            }
            TPlist.put(tid,TPlist_temp.get(tid));
            it++;
        }
    }

    public void buildTrajDB_init(){
        Connection conn = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // 使用MySQL的JDBC驱动
            String url = "jdbc:mysql://localhost:3306/test"; // 修改为你的MySQL数据库连接URL
            String username = "root"; // 修改为你的MySQL用户名
            String password = "021212"; // 修改为你的MySQL密码
            conn = DriverManager.getConnection(url, username, password);

            int dataSize = -1;
            String sql ="";
            if(dataSize == -1){sql = String.format("select * from %s", "real_time_data_temp");}
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.isClosed()) {
                    System.out.println("no result is found!");
                }
                while (rs.next()){
                    double lat = rs.getDouble("lat");
                    double lon = rs.getDouble("lon");
                    String time = rs.getString("recorded_time");
                    String tid = rs.getString("trip_id");
                    String vid = rs.getString("vehicle_id");
                    RealTimePointEntity p=new RealTimePointEntity(tid,vid,lat,lon,time);
                    if(!TPlist.containsKey(new TripId(tid))){
                        TPlist.put(new TripId(tid), new ArrayList<RealTimePointEntity>());
                    }
                    TPlist.get(new TripId(tid)).add(p);
                }
                rs.close();
            } catch (SQLException e) {
                throw new IllegalStateException(e.getMessage());

            }
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }
        System.out.println("Opened database successfully");
        SerializeHashMap();
    }


    public void SerializeHashMap() {
        // 指定要保存的文件名
        String fileName = "C:\\Users\\Dell\\Desktop\\projects\\transitnet-master\\src\\main\\Rtree_TPlist.txt";
        try {
            FileOutputStream myFileOutStream
                    = new FileOutputStream(fileName);

            ObjectOutputStream myObjectOutStream
                    = new ObjectOutputStream(myFileOutStream);

            myObjectOutStream.writeObject(TPlist);

            // closing FileOutputStream and
            // ObjectOutputStream
            myObjectOutStream.close();
            myFileOutStream.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void DeserializeHashMap() throws IOException {
        String fileName = "C:\\Users\\Dell\\Desktop\\projects\\transitnet-master\\src\\main\\elec_TPList.txt";
        try {
            FileInputStream fileInput = new FileInputStream(
                    fileName);


            ObjectInputStream objectInput
                    = new ObjectInputStream(fileInput);

            TPlist_temp = (HashMap)objectInput.readObject();

            objectInput.close();
            fileInput.close();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }



    public void setup(double[] ps) throws IOException {
        this.spatial_range=ps;
        buildTrajDB();
    }

    public List<TripId> rtreeTraj() {

        final RTree<TripId, Rectangle>[] rtree = new RTree[]{RTree.create()};
        TPlist.forEach((tid, plist) -> {
            //获取每条轨迹的MBR
            List<RealTimePointEntity> sortedLat = plist.stream().sorted(Comparator.comparing(RealTimePointEntity::getLat)).collect(Collectors.toList());
            List<RealTimePointEntity> sortedLon = plist.stream().sorted(Comparator.comparing(RealTimePointEntity::getLon)).collect(Collectors.toList());

            double lat_min = sortedLat.get(0).getLat(),
                    lat_max = sortedLat.get(sortedLat.size()-1).getLat(),
                    lon_min = sortedLon.get(0).getLon(),
                    lon_max = sortedLon.get(sortedLon.size()-1).getLon();

            Rectangle mbr = Geometries.rectangleGeographic(lon_min,lat_min,lon_max,lat_max);
            rtree[0] = rtree[0].add(tid, mbr);
        });
        long start = System.currentTimeMillis();
        Observable<Entry<TripId, Rectangle>> results =
                rtree[0].search(Geometries.rectangle(spatial_range[1],spatial_range[0],spatial_range[3],spatial_range[2]));
        long end = System.currentTimeMillis();
        Long running = end - start;
        System.out.println("[HISTORICALRangeEXPSERVICE] Rtree time: " + (running)+ "ms");
        List<TripId> res=new ArrayList<>();
        results.subscribe(entry -> {
            TripId tid = entry.value();
            res.add(tid);
        });

        //test=========
//        try {
//            FileWriter fileWriter = new FileWriter("D:\\datasets\\rtree_res.csv");
//            for (TripId item : res) {
//                fileWriter.write(item.toString() + "\n");
//            }
//            fileWriter.close();
//            System.out.println("HashSet items saved " );
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        //test=========

        return res;
    }
}