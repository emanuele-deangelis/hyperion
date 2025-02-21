package it.cnr.saks.hyperion;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "hyperion", mixinStandardHelpOptions = true, version = "hyperion 1.0",
        description = "Analyzer and Orchestrator of Test Programs.")
public class Hyperion implements Callable<Integer> {

    @Option(names = { "-a", "--analyze" }, paramLabel = "CONF_FILE", description = "The JSON file to configure the analysis")
    File analyzeJson;

    @Option(names = { "-e", "--extract-similarity" }, paramLabel = "CONF_FILE", description = "The JSON file to configure the similarity extraction")
    File similarityExtractionJson;

    @Option(names = { "-g", "--group-similar-tests" }, paramLabel = "CONF_FILE", description = "The JSON file to configure the test grouping activity")
    File groupingJson;

    @Override
    public Integer call() throws Exception {
        int ret = 64; // EX_USAGE

        // Switch on the possible incarnations of the Hyperion tool
        if(this.analyzeJson != null)
            ret = AnalyzerRunnerHelper.runAnalyzer(this.analyzeJson);
        if(this.similarityExtractionJson != null)
            ret = SimilarityExtractionRunnerHelper.runSimilarityExtraction(this.similarityExtractionJson);
        if(this.groupingJson != null)
            ret = GroupingRunnerHelper.runGrouping(this.groupingJson);

        return ret;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Hyperion()).execute(args);
        System.exit(exitCode);
    }
}
