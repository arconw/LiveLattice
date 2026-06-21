# Testing Strategy

## Test Pyramid

```
            ╱╲
           ╱  ╲        E2E (5%) - k6, Playwright, Docker Compose
          ╱    ╲
         ╱------╲
        ╱        ╲     Integration (25%) - Testcontainers, SpringBootTest, NestJS e2e
       ╱          ╲
      ╱------------╲
     ╱              ╲  Unit (70%) - Jest (NestJS), JUnit 5 (Spring Boot)
    ╱                ╲
   ╱------------------╲
```

## Unit Tests

| Layer | Framework | Coverage Target |
|---|---|---|
| NestJS Gateway | Jest | 90%+ on guards, pipes, interceptors |
| NestJS Realtime | Jest | 90%+ on room manager, collaboration engine |
| Spring Boot Core | JUnit 5 + Mockito | 90%+ on domain services, 100% on repositories |
| Spring Boot Integrations | JUnit 5 | 85%+ on transformers, validators |

- **Naming**: `{Class}Test.java` / `{Class}.spec.ts`
- **File location**: co-located next to source file in `src/test/` or `__tests__/`

## Integration Tests

- **Testcontainers** for PostgreSQL, Redis, Kafka, OpenSearch, MinIO, ClickHouse
- **Spring Boot**: `@SpringBootTest` with `@Testcontainers`, slice tests (`@WebMvcTest`, `@DataJpaTest`)
- **NestJS**: `@nestjs/testing` TestModule with in-memory or Testcontainer-backed services
- **Contracts**: Pact or Spring Cloud Contract for gateway -> core API contracts

### Example: Core Domain Integration Test

```java
@SpringBootTest
@Testcontainers
class CanvasServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    private CanvasService canvasService;

    @Test
    void shouldCreateCanvasAndPublishEvent() {
        // given
        var cmd = new CreateCanvasCommand("Test", workspaceId, userId);
        // when
        var canvas = canvasService.handle(cmd);
        // then
        assertThat(canvas.getTitle()).isEqualTo("Test");
        // event published to Kafka
        var records = KafkaTestUtils.getRecords(kafkaConsumer, "canvas.created");
        assertThat(records).hasSize(1);
    }
}
```

## E2E Tests

- **Stack**: Docker Compose (full stack) + k6 for performance + Playwright or REST calls for functional
- **Scope**: Critical user journeys - create workspace, invite member, edit canvas, view dashboard
- **Target**: 2m virtual users via k6 for performance tests, 10 concurrent for functional

## Performance Tests (k6)

| Scenario | Target | Threshold |
|---|---|---|
| REST API throughput | 1000 req/s | p95 < 200ms, error rate < 0.1% |
| WebSocket message throughput | 5000 msg/s | p95 < 50ms latency |
| Canvas collaboration | 50 concurrent editors | p99 op latency < 200ms |
| Dashboard queries | 100 concurrent dashboards | p95 query < 500ms |
| Import/export | 10 concurrent 10MB files | p95 < 30s |

## Testing Infrastructure

- **CI pipeline**: GitHub Actions, parallel job matrix for each service
- **Testcontainers Reuse**: enable `testcontainers.reuse.enable=true` in CI to reuse containers across runs
- **Flaky test detection**: `@Flaky` annotation with rerun policy (3 attempts)
- **Code coverage**: JaCoCo for Java, Istanbul for Node; minimum 85% line coverage gate in CI

## Local Testing with Docker Compose

```bash
# Start all test dependencies
docker compose -f compose.test.yaml up -d

# Run all unit + integration tests
./gradlew test
cd gateway && npm test

# Run E2E k6 tests
k6 run tests/k6/canvas-collaboration.js
```
