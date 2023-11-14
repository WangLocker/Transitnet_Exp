package whu.edu.cs.transitnet.jmh_test;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

public class simple_sample_test {
    @Benchmark
    @BenchmarkMode(Mode.AverageTime) // 使用平均运行时间
    @OutputTimeUnit(TimeUnit.MICROSECONDS) // 设置输出单位为微秒
    public void wellHelloThere() {
        try {
            // 让当前线程休眠 3 秒
            Thread.sleep(300);
        } catch (InterruptedException e) {
            // 处理线程中断异常
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(simple_sample_test.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
