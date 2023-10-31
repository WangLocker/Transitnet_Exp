package whu.edu.cs.transitnet.service;

import edu.whu.hyk.encoding.Decoder;
import edu.whu.hyk.model.PostingList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import whu.edu.cs.transitnet.service.index.CubeId;
import whu.edu.cs.transitnet.service.index.HistoricalTripIndex;
import whu.edu.cs.transitnet.service.index.HytraEngineManager;
import whu.edu.cs.transitnet.service.index.TripId;

import java.io.*;
import java.text.ParseException;
import java.util.*;

import java.util.concurrent.atomic.AtomicBoolean;


@Service
public class GeneratorService_merge {
    @Autowired
    HytraEngineManager hytraEngineManager;

    @Autowired
    HistoricalTripIndex historicalTripIndex;

    /**
     * key:天
     * value: 当天所有cube的volume，按照zorder和level排序（0-0,1-0,...,63-0, 0-1,1-1,...,7-1, 0-2）
     */
    public  HashMap<String,HashMap<String,Integer>> cubeVol_new = new HashMap<>();
    public  HashMap<String,int[]> cubeVol = new HashMap<>();

    /**
     * 记录合并后的cube分布，如果仍然存在则为1，否则为0
     */
    public  HashMap<String,int[]> bitMap = new HashMap<>();

    public  HashMap<String, String> compactionMap = new HashMap<>();

    /**
     * day -> {plane_idx -> {cube_id}}
     * xplanes: 0 ~ 2^resolution - 1
     * yplanes: 2^resolution ~ 2*2^resolution - 1
     * zplanes: 2*2^resolution ~ 3*2^resolution- 1
     */
    public HashMap<Integer, HashSet<String>> planes = new HashMap<>();
    public HashMap<CubeId, HashSet<TripId>> CT_List_DZL=new HashMap<>();

    static int epsilon=30;
    static int resolution=6;
    static String sep="@";

    public HashMap<CubeId, ArrayList<TripId>> CT_List_arr=new HashMap<>();
    public HashMap<CubeId, HashSet<TripId>> CT_List=new HashMap<>();
    public HashMap<CubeId, HashSet<TripId>> merge_CT_List = new HashMap();
    public HashMap<TripId, ArrayList<CubeId>> TC_List_arr=new HashMap<>();
    public HashMap<TripId, List<CubeId>> merge_TC_List=new HashMap<>();
    private String date;

    public void setup(String date) throws ParseException {
        this.date=date;
        //反序列化对应日期的txt文件，然后得到对应的cubetriplist...
        historicalTripIndex.cubeTripListSerializationAndDeserilization(date);
        CT_List_arr=historicalTripIndex.getCubeTripList();

        String time_s=date+" 00:00:00";
        String time_e=date+" 23:59:59";
//        historicalTripIndex.tripCubeListSerializationAndDeserilization(time_s,time_e);
//        TC_List_arr=historicalTripIndex.getTripCubeList();

        // 遍历 CT_List_arr 并将其转换为 HashSet 并存储到 CT_List 中
        double rate=0.6;
        int num= (int) (rate*CT_List_arr.size());
        int it=0;
        for (CubeId cubeId : CT_List_arr.keySet()) {
            if(it==num){
                break;
            }
            ArrayList<TripId> tidList = CT_List_arr.get(cubeId);
            HashSet<TripId> tSet = new HashSet<>(tidList);
            CT_List.put(cubeId, tSet);
            it++;
        }
    }

    public void generateMap() {

        //这里暂只做5.20号的数据且  考虑合并

        //计算cube volume
        CT_List.forEach((cid, idList) -> {
            int zorder = Integer.parseInt(cid.toString());
            int level = 0;

            if(!cubeVol.containsKey(date)){
                int size = (int) (Math.pow(8,resolution+1) - 1) / 7;
                cubeVol.put(date, new int[size]);
            }
            int offset  = getOffset(zorder,level);
            try {
                cubeVol.get(date)[offset] += idList.size();
            } catch (Exception e){
                System.out.println("Generate map error:  Arrays.toString(dzl)");
            }
            for (int parentOffset : getAncestorOffsets(offset)) {
                cubeVol.get(date)[parentOffset] += idList.size();
            }
        });

        //生成compaction map
        for(String day : cubeVol.keySet()) {
            BFS(day+sep+0+sep+resolution);
        }
    }




    public HashMap<Integer, HashSet<String>> generatePlanes() {
        bitMap.forEach((day, cubes) -> {
            int size = cubes.length;
            for(int i = 0; i < size; i++){
                if (cubes[i] == 1){
                    //将cube转换Planes
                    //这个OFFSET的cube代表的是哪个（DZL）cube，根据ZL算出他的start-end的最小粒度的三维坐标（0-64）
                    int[] zl = offsetToZandL(i);
                    String cid = day+sep+zl[0]+sep+zl[1];
                    int[] box = decodeZ3(zl[0],zl[1]);
                    for (int a = box[0]; a <= box[1]; a++){
                        if(!planes.containsKey(a)){planes.put(a, new HashSet<>());}
                        planes.get(a).add(cid);
                    }
                    for (int b = box[2]; b <= box[3]; b++){
                        int idx = b + (int) Math.pow(2, resolution);
                        if(!planes.containsKey(idx)){planes.put(idx, new HashSet<>());}
                        planes.get(idx).add(cid);
                    }
                    for (int c = box[4]; c <= box[5]; c++){
                        int idx = c + (int) Math.pow(2, resolution+1);
                        if(!planes.containsKey(idx)){planes.put(idx, new HashSet<>());}
                        planes.get(idx).add(cid);
                    }//遍历所有不同level的cube，然后计算这个cube的最↙和最↗的投影到ijk上的坐标，然后计算每个轴的每个单元格上投了哪些cube
                }
            }
        });
        return planes;
    }

    public void BFS(String cid) {
        String[] items = cid.split(sep);
        String day = items[0];
        int zorder = Integer.parseInt(items[1]);
        int level = Integer.parseInt(items[2]);

        if(level == 0){
            if(!bitMap.containsKey(day)){
                bitMap.put(day, new int[(int) (Math.pow(8,resolution+1) - 1) / 7]);
            }
            bitMap.get(day)[getOffset(zorder,level)] = 1;
            compactionMap.put(cid, cid);
            return;
        } //如果到了level 0，就直接写入本身

        if(shouldMerge(cid)) {
            if(!bitMap.containsKey(day)){
                bitMap.put(day, new int[(int) (Math.pow(8,resolution+1) - 1) / 7]);
            }
            bitMap.get(day)[getOffset(zorder,level)] = 1;//从大cube逐层往下搜索，遇到需要合并的大cube则表示该大cube合并后存在，置1
            writeMap(cid);
            return;
        } //如果应该合并，则写入merge map, bitmap置1

        for(int z  = zorder * 8; z < zorder * 8 + 8; z++){ //否则考察下一层cube
            BFS(day+sep+z+sep+(level-1));
        }
    }

    public boolean shouldMerge(String cid) {
        String[] items = cid.split(sep);
        String day = items[0];
        int zorder = Integer.parseInt(items[1]);
        int level = Integer.parseInt(items[2]);
        int offset = getOffset(zorder, level);
        return cubeVol.get(day)[offset] <= epsilon * Math.pow(5,level);
    }

    public void writeMap(String cid) {
        String[] items = cid.split(sep);
        String day = items[0];
        int zorder = Integer.parseInt(items[1]);
        int l = Integer.parseInt(items[2]);

        if(l == 0){return;}
        for(int z  = zorder * 8; z < zorder * 8 + 8; z++){
                String ccid = day+sep+z+sep+(l-1);
                compactionMap.put(ccid,cid);
                writeMap(ccid);
        }

    }


    public void updateMergeCTandTC(){

        CT_List.forEach((C,T)->{
            CT_List_DZL.put(new CubeId("2023-05-20"+"@"+C+"@"+"0"),CT_List.get(C));
        });
        CT_List=CT_List_DZL;
        compactionMap.forEach((fromCid, toCid)->{
            //如果是没有执行合并的level 0 cube，直接写入
            if(fromCid.equals(toCid)){
                    merge_CT_List.put(new CubeId(fromCid),CT_List.get(new CubeId(fromCid)));
            }
            else {
                String ancestor = toCid;
                while (compactionMap.containsKey(ancestor)){
                    ancestor = compactionMap.get(ancestor);
                }

                HashSet<TripId> tidSet = merge_CT_List.getOrDefault(new CubeId(ancestor), new HashSet<>());
                tidSet.addAll(CT_List.getOrDefault(new CubeId(fromCid), new HashSet<>()));
                merge_CT_List.put(new CubeId(ancestor), tidSet);
            }
        });

        //test
//        try {
//            FileReader fileReader = new FileReader("D:\\datasets\\std_res.csv");
//            BufferedReader bufferedReader = new BufferedReader(fileReader);
//            FileWriter fileWriter = new FileWriter("D:\\datasets\\cube_include_stdres.csv");
//            String line;
//            while ((line = bufferedReader.readLine()) != null) {
//                TripId tid=new TripId(line);
//                AtomicBoolean flag= new AtomicBoolean(false);
//                merge_CT_List.forEach((C,T)->{
//                    if(T!=null&&T.contains(tid)){
//                        try {
//                            fileWriter.write(tid+"\n");
//                            flag.set(true);
//                        } catch (IOException e) {
//                            throw new RuntimeException(e);
//                        }
//                    }
//                });
//                if(flag.equals(new AtomicBoolean(false))){
//                    fileWriter.write(tid+"not include in mergeList"+"\n");
//                }
//            }
//            bufferedReader.close();
//            fileWriter.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        //test

        merge_CT_List.forEach((cid, tidSet) ->{
            if(tidSet != null){
                for(TripId tid : tidSet){
                    List<CubeId> cidList = merge_TC_List.getOrDefault(tid, new ArrayList<>());
                    cidList.add(cid);
                    merge_TC_List.put(tid, cidList);
                }
            }
        });

//        AtomicInteger sum = new AtomicInteger();
//        PostingList.CT.forEach((k,v)->{
//            sum.addAndGet(v.size());
//        });
//        System.out.println("trajs in CT: " + sum);
//        sum.set(0);
//        PostingList.mergeCT.forEach((k,v)->{
//            if(v != null)
//            sum.addAndGet(v.size());
//        });
//        System.out.println("trajs in mergeCT: " + sum);

    }



    /**
     * 根据zorder和level计算在cubeVol中的offset
     * @param zorder
     * @param level
     * @return
     */
    public int getOffset(int zorder, int level){
        int base = 0;
        for(int i = 0; i < level; i++){
            base += (int) Math.pow(8,resolution - i);
        }
        return base + zorder;
    }

    public int[] offsetToZandL(int offset) {
        int reverse = (int) (Math.pow(8.0,(double) resolution+1) - 1.0) / 7 - (offset+1);
        int base = 1;


        int level ;
        for(level=resolution;reverse/base>0;--level){
            reverse-=base;
            base*=8;
        }
        int z = (int) Math.pow(8.0,(double) resolution-level) - (reverse+1);
        return new int[]{z,level};
    }


    /**
     * 计算level0cube的<b>所有</b>上层cube的offset
     * @param offset
     * @return
     */
    public int[] getAncestorOffsets(int offset){
        int[] offsets = new int[resolution];
        for(int i = 1; i <= resolution; i++){
            offsets[i-1] = getOffset(offset / (int) Math.pow(8,i), i);
        }
        return offsets;
    }

    /**
     * 仅计算输入cube的<b>下一层</b>的cube的offset
     * @param offset
     * @return
     */
    public int[] getChildOffsets(int offset){
        int[] offsets = new int[8];
        for(int i = 0; i < 8; i++){
            offsets[i] = getOffset(offset / (int) Math.pow(8,i), i);
        }
        return offsets;
    }

    public void writeLsmConfig(String filePath){
        File f = new File(filePath);
        FileOutputStream out;
        try {
            out = new FileOutputStream(f, false);
            OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");


            StringBuilder mm = new StringBuilder("merge map\n");
            writer.write(mm.toString());
            compactionMap.forEach((k,v) -> {
                try {
                    writer.write(""+k+":"+v+"\n");
                }catch (IOException e){
                    e.printStackTrace();
                }
            });



            StringBuilder kpl = new StringBuilder("\nkeys_per_level\n");
            bitMap.forEach((day,map) -> {
                for(int i = 0; i < map.length; i++){
                    if(map[i] == 1) {
                        int[] zl = offsetToZandL(i);
                        String cid = day+sep+zl[0]+sep+zl[1];
                        kpl.append(zl[1]).append(":").append(cid).append("\n");
                    }
                }
                kpl.append(day).append(sep).append(0).append(sep).append(resolution);
            });

            writer.write(kpl.toString());

            StringBuilder estpl = new StringBuilder("\nelement_size_threshold_per_level\n");
            for(int i = 0; i <= resolution; i++){
                estpl.append(i).append(":").append((int) (epsilon * Math.pow(5, i))).append("\n");
            }
            writer.write(estpl.toString());

            writer.write("\nelement_length_per_level\n");
            writer.write("all:"+10);
            writer.close();

        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeKV(String filePath){
        File f = new File(filePath);
        FileOutputStream out;
        try {
            out = new FileOutputStream(f, false);
            OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");



            PostingList.CP.forEach((cid, idList)->{
                StringBuilder kv = new StringBuilder();
                String[] items = cid.split(sep);
                String day = items[0];
                int z = Integer.parseInt(items[1]);
                int l = Integer.parseInt(items[2]);

                //如果不用合并，直接写入
                if (bitMap.get(day)[getOffset(z,l)] == 1){
                    idList.forEach(id -> {
                        kv.append("put:").append(cid).append(",").append(id).append("\n");
                    });
                }

                else {
                    String destination = cid;
                    while (compactionMap.containsKey(destination)){
                        destination = compactionMap.get(destination);
                    }
                    String finalDestination = destination;
                    idList.forEach(id -> {
                        kv.append("put:").append(finalDestination).append(",").append(id).append("\n");
                    });
                }
                try {
                    writer.write(kv.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });


            writer.close();

        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeTCWithCompaction(String filePath) {
        HashMap<String,HashSet<Integer>> CT = new HashMap<>();
        File f = new File(filePath);
        FileOutputStream out;
        try {
            out = new FileOutputStream(f, false);
            OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
            //通过合并前的ct和compaction map 构造合并后的ct
            PostingList.CT.forEach((cid, tid_set) -> {
                String to_cid = cid;
                while(compactionMap.containsKey(to_cid)){
                    to_cid = compactionMap.get(to_cid);
                }
                if(!CT.containsKey(to_cid)){
                    CT.put(to_cid, new HashSet<>());
                }
                CT.get(to_cid).addAll(PostingList.CT.get(cid));

            });

            CT.entrySet().forEach(entry -> {
                try {
                    writer.write(entry.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            writer.close();

        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean testMap() {
        for(int i=0;i<bitMap.get("2023-05-20").length;i++){
            if(bitMap.get("2023-05-20")[i]==1){
                int[] zl=offsetToZandL(i);
                if(!merge_CT_List.containsKey(new CubeId("2023-05-20@"+zl[0]+"@"+zl[1]))){
                    return false;
                }
            }
        }
        return true;
    }

    public int[] decodeZ3(int zorder, int level) {
        int digits = 3 * 6;

        String bits;
        for(bits = Integer.toBinaryString(zorder); digits > bits.length(); bits = "0" + bits) {
        }

        String bitsI = "";
        String bitsJ = "";
        String bitsK = "";

        int i;
        for(i = 0; i < bits.length(); ++i) {
            if (i % 3 == 0) {
                bitsI = bitsI + bits.charAt(i);
            }

            if (i % 3 == 1) {
                bitsJ = bitsJ + bits.charAt(i);
            }

            if (i % 3 == 2) {
                bitsK = bitsK + bits.charAt(i);
            }
        }

        i = bitToint(bitsI);
        int J = bitToint(bitsJ);
        int K = bitToint(bitsK);
        int i1 = i * (int)Math.pow(2, (double)level);
        int i2 = i1 + (int)Math.pow(2, (double)level) - 1;
        int j1 = J * (int)Math.pow(2, (double)level);
        int j2 = j1 + (int)Math.pow(2, (double)level) - 1;
        int k1 = K * (int)Math.pow(2, (double)level);
        int k2 = k1 + (int)Math.pow(2, (double)level) - 1;
        return new int[]{i1, i2, j1, j2, k1, k2};
    }

    public int bitToint(String bits) {
        int sum = 0;
        int length = bits.length();

        for(int i = 0; i < length; ++i) {
            sum = (int)((double)sum + (double)Integer.parseInt(String.valueOf(bits.charAt(i))) * Math.pow(2.0, (double)(length - i - 1)));
        }

        return sum;
    }
}
