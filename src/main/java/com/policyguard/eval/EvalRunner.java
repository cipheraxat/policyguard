package com.policyguard.eval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.policyguard.PolicyguardApplication;

/**
 * Standalone entry-point that boots Spring (non-web, stub profile), runs the
 * {@link EvalHarness}, prints a summary, and writes
 * {@code target/eval-report.json}.
 *
 * <pre>
 *   mvn -q -DskipTests package -Pstub
 *   java -cp target/policyguard-*.jar com.policyguard.eval.EvalRunner
 * </pre>
 *
 * This class is intentionally NOT a {@code @Component} — it is a manual
 * {@code main} that manages the Spring lifecycle itself.
 */
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(PolicyguardApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("stub")
                .run(args);

        try {
            EvalHarness harness = ctx.getBean(EvalHarness.class);
            EvalReport report = harness.run();
            printSummary(report);
        } catch (Exception e) {
            log.error("Eval run failed", e);
            System.exit(1);
        } finally {
            ctx.close();
        }
    }

    private static void printSummary(EvalReport r) {
        System.out.printf("%n=== PolicyGuard Eval Report ===%n");
        System.out.printf("  total_queries        : %d%n", r.totalQueries());
        System.out.printf("  answered             : %d%n", r.answered());
        System.out.printf("  escalated            : %d%n", r.escalated());
        System.out.printf("  refused              : %d%n", r.refused());
        System.out.printf("  citation_precision   : %.4f%n", r.citationPrecision());
        System.out.printf("  retrieval_recall@5   : %.4f%n", r.retrievalRecallAt5());
        System.out.printf("  escalation_precision : %.4f%n", r.escalationPrecision());
        System.out.printf("  escalation_recall    : %.4f%n", r.escalationRecall());
        System.out.printf("  refusal_rate         : %.4f%n", r.refusalRate());
        System.out.printf("  p95_latency_ms       : %d%n",   r.p95LatencyMs());
        System.out.printf("  pii_precision (stub) : %.4f%n", r.piiRedactionPrecision());
        System.out.printf("  pii_recall    (stub) : %.4f%n", r.piiRedactionRecall());
        System.out.printf("  report written to    : target/eval-report.json%n%n");
    }
}
