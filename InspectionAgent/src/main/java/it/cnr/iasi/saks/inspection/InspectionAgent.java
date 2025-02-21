package it.cnr.iasi.saks.inspection;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;

public class InspectionAgent {
    private static final Logger log = LoggerFactory.getLogger(InspectionAgent.class);

    private static final String OUT_FILE_PATH = "dump.pl";

    public static void premain(String agentArgs, Instrumentation inst) {
        log.info("Configuring packages to trace");
        String[] sutPackages = agentArgs.split(",");

        for(String pack: sutPackages)
            log.debug("Tracing package {}", pack);

        log.info("Registering shutdown hook");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            int count = MetricsCollector.instance().getInvokes().size();
            log.info("Dumped {} facts.", count);
        }));

        log.info("Registering transformation class");
        MetricsCollector.instance().setOutputFile(OUT_FILE_PATH);
        inst.addTransformer(new InspectionClassTransformer(sutPackages), false);
    }
}