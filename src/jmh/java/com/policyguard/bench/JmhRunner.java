package com.policyguard.bench;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Manual JMH runner entry-point — no maven plugin required.
 *
 * <pre>
 *   mvn -Pjmh package -DskipTests
 *   mvn -Pjmh exec:java -Dexec.mainClass=com.policyguard.bench.JmhRunner
 * </pre>
 *
 * Results are written to {@code target/jmh-results.json}.
 */
public class JmhRunner {

    public static void main(String[] args) throws Exception {
        Options opts = new OptionsBuilder()
                .include(QueryServiceBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.JSON)
                .result("target/jmh-results.json")
                .build();

        new Runner(opts).run();
    }
}
