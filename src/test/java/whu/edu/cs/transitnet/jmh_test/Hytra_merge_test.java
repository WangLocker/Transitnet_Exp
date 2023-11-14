package whu.edu.cs.transitnet.jmh_test;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import whu.edu.cs.transitnet.service.index.CubeId;
import whu.edu.cs.transitnet.service.index.TripId;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
@State(Scope.Benchmark)
public class Hytra_merge_test {
    private HashMap<CubeId, HashSet<TripId>> CTlist=new HashMap<>();
    private HashMap<Integer, HashSet<String>> planes=new HashMap<>();
    private int resolution=6;
    double[] S={40.502873, -74.252339, 40.93372, -73.701241};
    double deltaX;
    double deltaY;

    @Setup
    /*在这里准备搜索所用的数据*/
    public void setup() {
        deltaX = (S[2] - S[0]) / Math.pow(2.0, (double)resolution);
        deltaY = (S[3] - S[1]) / Math.pow(2.0, (double)resolution);
        String list = "D:\\datasets\\jmh_resource\\MergeCTlist_80.txt";
        try {
            FileInputStream listInput1 = new FileInputStream(
                    list);

            ObjectInputStream listInput2
                    = new ObjectInputStream(listInput1);

            CTlist = (HashMap)listInput2.readObject();

            listInput2.close();
            listInput1.close();

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String pl = "D:\\datasets\\jmh_resource\\MergeCTlist_80.txt";
        try {
            FileInputStream plInput1 = new FileInputStream(
                    pl);

            ObjectInputStream plInput2
                    = new ObjectInputStream(plInput1);

            planes = (HashMap)plInput2.readObject();

            plInput2.close();
            plInput1.close();

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public int[] decodeZ2(int zorder) {
        int digits = 2 * resolution;

        String bits;
        for(bits = Integer.toBinaryString(zorder); digits > bits.length(); bits = "0" + bits) {
        }

        String bitsI = "";
        String bitsJ = "";

        for(int i = 0; i < bits.length(); ++i) {
            if ((i & 1) == 0) {
                bitsI = bitsI + bits.charAt(i);
            } else {
                bitsJ = bitsJ + bits.charAt(i);
            }
        }

        return new int[]{bitToint(bitsI), bitToint(bitsJ)};
    }

    public int bitToint(String bits) {
        int sum = 0;
        int length = bits.length();

        for(int i = 0; i < length; ++i) {
            sum = (int)((double)sum + (double)Integer.parseInt(String.valueOf(bits.charAt(i))) * Math.pow(2.0, (double)(length - i - 1)));
        }

        return sum;
    }

    public int encodeGrid(double lat, double lon) {
        int i = (int)((lat - S[0]) / deltaX);
        int j = (int)((lon - S[1]) / deltaY);
        return combine2(i, j, resolution);
    }

    public int combine2(int aid, int bid, int lengtho) {
        int length = lengtho;
        int[] a = new int[lengtho];

        int[] b;
        for(b = new int[lengtho]; length-- >= 1; bid /= 2) {
            a[length] = aid % 2;
            aid /= 2;
            b[length] = bid % 2;
        }

        int[] com = new int[2 * lengtho];

        for(int i = 0; i < lengtho; ++i) {
            com[2 * i] = a[i];
            com[2 * i + 1] = b[i];
        }

        return bitToint(com, 2 * lengtho);
    }

    public static int bitToint(int[] a, int length) {
        int sum = 0;

        for(int i = 0; i < length; ++i) {
            sum = (int)((double)sum + (double)a[i] * Math.pow(2.0, (double)(length - i - 1)));
        }

        return sum;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime) // 使用平均运行时间
    @OutputTimeUnit(TimeUnit.MICROSECONDS) // 设置输出单位为微秒
    public void spatialHytra() throws InterruptedException {
        Thread.sleep(100);
        double[] spatial_range={40.8100, -73.9200, 40.8367, -73.8840};
        int[] ij_s = decodeZ2(encodeGrid(spatial_range[0],spatial_range[1]));
        int[] ij_e = decodeZ2(encodeGrid(spatial_range[2],spatial_range[3]));

        //encode time range
        int t_s = 3600 * 0, t_e = 3600 * 24;
        double delta_t = 86400 / Math.pow(2, resolution);
        int k_s = (int)(t_s/delta_t), k_e = (int) (t_e/delta_t);

        //union
        HashSet<String> planes_i = new HashSet<>(), planes_j = new HashSet<>(), planes_k = new HashSet<>();
        for(int i = ij_s[0]; i <= ij_e[0]; i++) {
            if(planes.containsKey(i))
                planes_i.addAll(planes.get(i));
        }
        for(int j = ij_s[1] + (int) Math.pow(2,resolution); j <= ij_e[1] + (int) Math.pow(2,resolution); j++) {
            if(planes.containsKey(j))
                planes_j.addAll(planes.get(j));
        }
        for(int k = k_s + (int) Math.pow(2,resolution+1); k <= k_e + (int) Math.pow(2,resolution+1); k++) {
            if(planes.containsKey(k))
                planes_k.addAll(planes.get(k));
        }

        //intersection
        planes_i.retainAll(planes_j);
        planes_i.retainAll(planes_k);

        HashSet<TripId> res = new HashSet<>();
        planes_i.forEach(cid -> {
            if(CTlist.containsKey(new CubeId(cid))){
                if(CTlist.get(new CubeId(cid))!=null){
                    res.addAll(CTlist.get(new CubeId(cid)));
                }
            }
        });
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Hytra_merge_test.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
