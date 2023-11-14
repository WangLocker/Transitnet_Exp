package whu.edu.cs.transitnet.jmh_test;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import whu.edu.cs.transitnet.TransitnetApplication;
import whu.edu.cs.transitnet.service.HistoricalRangeService_merge;

import java.text.ParseException;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class MergeRangeTest {

    private HistoricalRangeService_merge Service; // 不再使用 @Autowired 注入

    @Setup
    public void setup() throws ParseException {
        // 创建 Spring ApplicationContext，并仅初始化您需要的 Spring Bean
        ConfigurableApplicationContext ctx = SpringApplication.run(TransitnetApplication.class);

        // 从 Spring 容器中获取 Spring Bean
        Service = ctx.getBean(HistoricalRangeService_merge.class);
        double []ps={30.5000, 114.2000, 30.5267, 114.2313} ;
        String date="2023-05-20";
        Service.setup(ps,date);
        Service.prepare();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Measurement(iterations = 10, time = 1)
    @Warmup(iterations = 10, time = 5000, timeUnit = TimeUnit.MILLISECONDS)
    public void M() {
        // 在这里调用您的服务类的方法进行性能测试
        Service.exec();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MergeRangeTest.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
