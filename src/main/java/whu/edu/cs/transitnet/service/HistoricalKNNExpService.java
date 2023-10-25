package whu.edu.cs.transitnet.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import whu.edu.cs.transitnet.dao.TripsDao;
import whu.edu.cs.transitnet.param.QueryKnnHisParam;
import whu.edu.cs.transitnet.pojo.RealTimePointEntity;
import whu.edu.cs.transitnet.pojo.TripsEntity;
import whu.edu.cs.transitnet.realtime.Vehicle;
import whu.edu.cs.transitnet.service.index.*;

import java.io.IOException;
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HistoricalKNNExpService {

    @Autowired
    HytraEngineManager hytraEngineManager;

    @Autowired
    HistoricalTripIndex historicalTripIndex;

    @Autowired
    EncodeService encodeService;

    @Autowired
    DecodeService decodeService;

    @Autowired
    ShapeIndex shapeIndex;

    @Autowired
    ScheduleIndex scheduleIndex;

    // 这个k指的是取前k个shape;15 12 9 6 3
    private int kShape = 50;
    private List<QueryKnnHisParam.Point> points;
    private int top_k;
    private String date;
    private List<String> timerange;
    private List<RealtimeKNNExpService.resItem>output_query_res;
    List<TripId> topkTripsLOC = new ArrayList<>();


    HashMap<TripId, ArrayList<RealTimePointEntity>> tripPointList = new HashMap<>();
    // trip - cube  map做操作 删掉list
    private HashMap<TripId, ArrayList<CubeId>> tripCubeList = new HashMap<>();

    private HashMap<TripId, ArrayList<CubeId>> allTripCubeList = new HashMap<>();

    public static class resItem {
        private int rank;
        private String busId;
        private double sim;

        public resItem( int rank,String first, double second) {
            this.rank=rank;
            this.busId = first;
            this.sim = second;
        }
        public int getRank(){
            return rank;
        }

        public void setRank(int r){
            this.rank=r;
        }

        public String getBusId() {
            return busId;
        }

        public void setBusId(String first) {
            this.busId = first;
        }

        public double getSim() {
            return sim;
        }

        public void setSim(double second) {
            this.sim = second;
        }
    }


    public void setup(List<QueryKnnHisParam.Point> points_in, int top_k_in){
        points=points_in;
        top_k=top_k_in;
    }


    @Autowired
    TripsDao tripsDao;

    /**
     * 不用 schedule 做筛选；user: [start_time, end_time]
     * @param userTripId
     * @param userGridList
     * @return
     */
    public ArrayList<TripId> filterTripList(TripId userTripId, ArrayList<GridId> userGridList) {
        // top-k shapes -> trips of top-k shapes
        ArrayList<TripId> tripIds = shapeIndex.getTripsOfTopKShapes(null, userGridList, kShape);

        return tripIds;
    }


    /**
     * 使用 shape - trip 一层索引
     * @param userTripId
     * @param userGridList
     * @throws InterruptedException
     * @throws ParseException
     */
    public void getTripIdCubeList(TripId userTripId, ArrayList<GridId> userGridList) throws InterruptedException, ParseException {
        tripCubeList = new HashMap<>();

        // 获取所有要判断的tripid
        ArrayList<TripId> filteredTripList = filterTripList(userTripId, userGridList);

        if (filteredTripList.isEmpty()) {
            return;
        }

        for (TripId tripId : filteredTripList) {
            if(allTripCubeList.containsKey(tripId)){
                tripCubeList.put(tripId, allTripCubeList.get(tripId));
            }
        }

    }




    // trip_id - similarity

    HashMap<TripId, Integer> tripSimListLOC = new HashMap<>();



    /**
     * 获取 Top-k trip （用户自己指定k，不是前面定义的变量k）
     * @throws IOException
     * @throws InterruptedException
     * @throws ParseException
     */
    public void getTopKTrips() throws IOException, InterruptedException, ParseException {
        allTripCubeList = historicalTripIndex.getTripCubeList();
//        //test
//        List<TripId> tripidList_all = new ArrayList<>(allTripCubeList.keySet());
//        List<TripId> shapetriplist_alltrip=new ArrayList<>();
//        for (ArrayList<TripId> tripIds : shapeIndex.shapeTripList.values()) {
//            shapetriplist_alltrip.addAll(tripIds);
//        }
//        Set<TripId> distinctTripIds = new HashSet<>(shapetriplist_alltrip);
//        List<TripId> uniqueAllShapeTripIdsList = new ArrayList<>(distinctTripIds);
//        int it=0;
//        int im=0;
//        for(TripId id:tripidList_all){
//            if (uniqueAllShapeTripIdsList.contains(id)){
//                im=im+1;
//            }
//            it++;
//            System.out.println("it: "+it+'\n');
//        }
//        System.out.println(im+"=================================");
//        //test
        ArrayList<GridId> userGridList = new ArrayList<>();
        ArrayList<CubeId> userCubeList = new ArrayList<>();

        ArrayList<String> times = new ArrayList<>();
        ArrayList<GridId> grids = new ArrayList<>();
        ArrayList<CubeId> cubes = new ArrayList<>();

        Date d = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));

        Long to_cube_st=System.currentTimeMillis();

        for (int i=0;i<points.size();i++) {
            GridId gridId = encodeService.getGridID(points.get(i).getLat(), points.get(i).getLng());
            grids.add(gridId);
            if(userGridList.isEmpty() || userGridList.lastIndexOf(gridId) != (userGridList.size() - 1)) {
                userGridList.add(gridId);
            }

            String recordedTime = points.get(i).getTime();
            Date parse = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(recordedTime);
            Long time = parse.getTime();

            CubeId cubeId = encodeService.encodeCube(points.get(i).getLat(), points.get(i).getLng(), time);
            cubes.add(cubeId);
            if(userCubeList.isEmpty() || userCubeList.lastIndexOf(cubeId) != (userCubeList.size() - 1)) {
                userCubeList.add(cubeId);
            }

            // 这个实体中的 recordedTime 是个字符串
            d.setTime(time);
            String date_hour_min_sec  = sdf.format(d);

            times.add(date_hour_min_sec);
        }

        Long to_cube_ed=System.currentTimeMillis();
        Long to_cube=to_cube_ed-to_cube_st;
        System.out.println("+++++++++++++++++++++to-cube time: "+ to_cube +"ms+++++++++++++++++++++");


        Long startTime=System.currentTimeMillis();
        int choice = 1;

        switch (choice) {
            case 1:
                // 一层索引
                getTripIdCubeList(null, userGridList);
                break;
            default:
                break;
        }

        // tripCubeList 为空则 continue


        Set<TripId> keySet = tripCubeList.keySet();
        for (TripId tripId1 : keySet) {
            if(tripCubeList.get(tripId1)==null) {
                continue;
            }

            // 利用 LOC 计算出来的相似度
            List<CubeId> intersection0 = new ArrayList<>(userCubeList);
            intersection0.retainAll(tripCubeList.get(tripId1));
            List<CubeId> intersection1 = intersection0.stream().distinct().collect(Collectors.toList());
            tripSimListLOC.put(tripId1, intersection1.size());
        }

        // topk LOC
        List<TripId> topTripsLOC = tripSimListLOC.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).map(Map.Entry::getKey).collect(Collectors.toList());

        if(topTripsLOC.size() >= top_k) {
            topkTripsLOC = topTripsLOC.subList(0, top_k);
        } else {
            topkTripsLOC = topTripsLOC;
        }

        Long endTime=System.currentTimeMillis();
        Long running=endTime-startTime;
        System.out.println("+++++++++++++++++++++Top-k time: "+ running +"ms+++++++++++++++++++++");
    }

    public List<RealtimeKNNExpService.resItem> get_res(){
        output_query_res=new ArrayList<>();
        for(int i=0;i<top_k;i++){
            TripId temp_tid=topkTripsLOC.get(i);
            double s=tripSimListLOC.get(temp_tid);
            RealtimeKNNExpService.resItem temp=new RealtimeKNNExpService.resItem(i+1,temp_tid.toString(),s);
            output_query_res.add(temp);
        }
        return  output_query_res;
    }
}
