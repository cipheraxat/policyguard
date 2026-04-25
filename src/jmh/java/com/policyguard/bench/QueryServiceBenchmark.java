package com.policyguard.bench;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.multipart.MultipartFile;

import com.policyguard.PolicyguardApplication;
import com.policyguard.fixtures.PolicyFixtureFactory;
import com.policyguard.fixtures.PolicyFixtureFactory.PolicyFixture;
import com.policyguard.service.ingestion.DocumentIngestionService;
import com.policyguard.service.query.QueryOutcome;
import com.policyguard.service.query.QueryService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * JMH benchmark for the {@link QueryService} end-to-end pipeline (stub profile,
 * no external calls).
 *
 * <p>Run via:
 * <pre>
 *   mvn -Pjmh package -DskipTests
 *   mvn -Pjmh exec:java -Dexec.mainClass=com.policyguard.bench.JmhRunner
 * </pre>
 */
@Fork(1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 5, time = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class QueryServiceBenchmark {

    private ConfigurableApplicationContext ctx;
    private QueryService queryService;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        ctx = new SpringApplicationBuilder(PolicyguardApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("stub")
                .run();

        DocumentIngestionService ingestion = ctx.getBean(DocumentIngestionService.class);
        PolicyFixtureFactory factory = new PolicyFixtureFactory();

        for (PolicyFixture fixture : factory.buildAll()) {
            try {
                Map<String, Object> meta = new java.util.HashMap<>(
                        fixture.metadata() != null ? fixture.metadata() : Map.of());
                meta.put("documentId", fixture.documentId());
                meta.put("title",      fixture.title());
                meta.put("docType",    fixture.docType());
                ingestion.ingest(new BenchMultipartFile(fixture.documentId(), fixture.pdfBytes()), meta);
            } catch (Exception e) {
                // Already ingested from a previous warmup iteration — safe to skip.
            }
        }

        queryService = ctx.getBean(QueryService.class);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    /** Benchmarks the answered/retrieval path (representative production workload). */
    @Benchmark
    public void answeredQuery(Blackhole bh) {
        QueryOutcome outcome = queryService.handle(
                "What is the data retention period for customer records?",
                "bench-user",
                null);
        bh.consume(outcome);
    }

    /** Benchmarks the escalated path — no LLM call, just risk classification + queue. */
    @Benchmark
    public QueryOutcome escalatedQuery() {
        return queryService.handle(
                "We need an exception to override the HIPAA policy for this client request",
                "bench-user",
                null);
    }

    // ── minimal MultipartFile adapter ─────────────────────────────────────────

    private static final class BenchMultipartFile implements MultipartFile {

        private final String name;
        private final byte[] bytes;

        BenchMultipartFile(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes != null ? bytes : new byte[0];
        }

        @Override public String getName()             { return name; }
        @Override public String getOriginalFilename() { return name + ".pdf"; }
        @Override public String getContentType()      { return "application/pdf"; }
        @Override public boolean isEmpty()            { return bytes.length == 0; }
        @Override public long getSize()               { return bytes.length; }
        @Override public byte[] getBytes()            { return bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }

        @Override
        public void transferTo(File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
