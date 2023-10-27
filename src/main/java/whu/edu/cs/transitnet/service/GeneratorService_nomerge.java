package whu.edu.cs.transitnet.service;

import edu.whu.hyk.encoding.Decoder;
import edu.whu.hyk.model.PostingList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import whu.edu.cs.transitnet.service.index.CubeId;
import whu.edu.cs.transitnet.service.index.HistoricalTripIndex;
import whu.edu.cs.transitnet.service.index.HytraEngineManager;
import whu.edu.cs.transitnet.service.index.TripId;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.util.*;


@Service
public class GeneratorService_nomerge {
    @Autowired
    HytraEngineManager hytraEngineManager;

    @Autowired
    HistoricalTripIndex historicalTripIndex;

    /**
     * key:天
     * value: 当天所有cube的volume，按照zorder和level排序（0-0,1-0,...,63-0, 0-1,1-1,...,7-1, 0-2）
     */
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

    static int epsilon=30;
    static int resolution=6;
    static String sep="@";

    public HashMap<CubeId, ArrayList<TripId>> CT_List_arr=new HashMap<>();
    public HashMap<CubeId, HashSet<TripId>> CT_List=new HashMap<>();
    public HashMap<CubeId, HashSet<TripId>> merge_CT_List = new HashMap();

    public HashMap<TripId, ArrayList<CubeId>> TC_List_arr=new HashMap<>();
    public HashMap<TripId, ArrayList<CubeId>> merge_TC_List_arr=new HashMap<>();
    private String date;

    public void setup(String date) throws ParseException {
        this.date=date;
        //反序列化对应日期的txt文件，然后得到对应的cubetriplist...
        historicalTripIndex.cubeTripListSerializationAndDeserilization(date);
        CT_List_arr=historicalTripIndex.getCubeTripList();

        String time_s=date+" 00:00:00";
        String time_e=date+" 23:59:59";
        historicalTripIndex.tripCubeListSerializationAndDeserilization(time_s,time_e);
        TC_List_arr=historicalTripIndex.getTripCubeList();

        // 遍历 CT_List_arr 并将其转换为 HashSet 并存储到 CT_List 中
        for (CubeId cubeId : CT_List_arr.keySet()) {
            ArrayList<TripId> tidList = CT_List_arr.get(cubeId);
            HashSet<TripId> tSet = new HashSet<>(tidList);
            CT_List.put(cubeId, tSet);
        }
    }

    public void generateMap() {

        //这里暂只做5.20号的数据且不考虑合并

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
                    }
                }
            }
        });
        return planes;
    }


    //这个BFS实际上没有进行合并
    public void BFS(String cid) {
        String[] items = cid.split(sep);
        String day = items[0];

        int[] no_merge_cubes=new int[(int) (Math.pow(8,resolution+1) - 1) / 7];
        for(int i=0;i<Math.pow(8,resolution);i++){
            no_merge_cubes[i]=1;
        }
        bitMap.put(day, no_merge_cubes);

    }


    public void updateMergeCTandTC(){
        merge_CT_List=CT_List;
    }

    /**
     * 根据zorder和level计算在cubeVol中的offset
     * @param zorder
     * @param level
     * @return
     */
    //level=6 最粗粒度 数组最末尾
    //level=5
    //level=4
    //level=3
    //level=2
    //level=1
    //level=0 最细粒度 数组最前端
    public int getOffset(int zorder, int level){
        int base = 0;
        for(int i = 0; i < level; i++){
            base += (int) Math.pow(8,resolution - i);
        }
        return base + zorder;
    }

    public int[] offsetToZandL(int offset) {
        int level = -1;
        int base = 0;
        int nextBase = 0;

        // 计算 level
        while (offset >= nextBase) {
            base = nextBase;
            nextBase += (int) Math.pow(8, resolution - level - 1);
            level++;
        }

        int zOrder = offset - base;

        int[] result = {zOrder, level};
        return result;
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
