package tabby.config;

import lombok.extern.slf4j.Slf4j;
import soot.G;
import soot.PhaseOptions;
import soot.jimple.spark.SparkTransformer;
import soot.options.Options;

import java.io.File;
import java.util.HashMap;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Slf4j
public class SootConfiguration {

    /**
     * soot 默认配置
     */
    public static void initSootOption(){
        String output = String.join(File.separator, System.getProperty("user.dir"), "temp");
        log.debug("Output directory: " + output);
        G.reset();
        // 添加transformer
//        PackManager.v()
//                .getPack("wjtp")
//                .add(new Transform("wjtp.classTransformer", new ClassInfoTransformer()));
//        PackManager.v()
//                .getPack("jtp")
//                .add(new Transform("jtp.callGraphTransformer", callGraphTransformer));
        Options.v().set_verbose(true); // 打印详细信息

        Options.v().set_prepend_classpath(true); // 优先载入soot classpath
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_keep_line_number(true); // 记录文件行数
        Options.v().set_src_prec(Options.src_prec_J); // 优先处理Jimple 格式
        Options.v().set_output_dir(output); // 设置IR Jimple的输出目录
        Options.v().set_output_format(Options.output_format_jimple); // 输出Jimple格式
        Options.v().set_validate(true);
        Options.v().set_whole_program(true);// 开启 过程间分析
        Options.v().set_no_writeout_body_releasing(true);

        // 设置自定义的package
        PhaseOptions.v().setPhaseOption("cg","on");
//        enableSpark();

//        PhaseOptions.v().setPhaseOption("jtp.callGraphTransformer", "off");
    }

    private static void enableSpark()
    {
        //Enable Spark
        HashMap<String,String> opt = new HashMap<String,String>();
        opt.put("verbose","true");
        opt.put("enabled","true");
        opt.put("propagator","worklist");
        opt.put("simple-edges-bidirectional","false");
        opt.put("on-fly-cg","true");
        opt.put("set-impl","double");
        opt.put("double-set-old","hybrid");
        opt.put("double-set-new","hybrid");
        opt.put("pre_jimplify", "true");
        opt.put("cs-demand", "true");
        opt.put("traversal", "75000");
        opt.put("passes", "10");
        opt.put("lazy-pts", "true");

        SparkTransformer.v().transform("",opt);
    }

}
