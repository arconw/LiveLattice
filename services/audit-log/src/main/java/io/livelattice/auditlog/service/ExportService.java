package io.livelattice.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.auditlog.config.AuditProperties;
import io.livelattice.auditlog.dto.ExportRequest;
import io.livelattice.auditlog.dto.ExportStatusResponse;
import io.livelattice.auditlog.model.AuditEventEntity;
import io.livelattice.auditlog.model.ExportJobEntity;
import io.livelattice.auditlog.repository.AuditEventRepository;
import io.livelattice.auditlog.repository.ExportJobRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {

    private final ExportJobRepository jobRepository;
    private final AuditEventRepository auditEventRepository;
    private final MinioClient minioClient;
    private final AuditProperties properties;
    private final ObjectMapper objectMapper;

    public ExportService(ExportJobRepository jobRepository,
                         AuditEventRepository auditEventRepository,
                         MinioClient minioClient,
                         AuditProperties properties,
                         ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.auditEventRepository = auditEventRepository;
        this.minioClient = minioClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExportStatusResponse create(ExportRequest request) {
        String format = request.format().toLowerCase();
        if (!format.equals("csv") && !format.equals("parquet")) {
            throw new IllegalArgumentException("Unsupported export format: " + request.format());
        }
        if (request.to().isBefore(request.from())) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        String jobId = UUID.randomUUID().toString();
        ExportJobEntity job = new ExportJobEntity();
        job.setId(jobId);
        job.setWorkspaceId(request.workspaceId());
        job.setFrom(request.from());
        job.setTo(request.to());
        job.setFormat(format);
        job.setStatus("pending");
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public ExportStatusResponse findById(String jobId) {
        return jobRepository.findById(jobId).map(this::toResponse).orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<String> findWorkspaceId(String jobId) {
        return jobRepository.findById(jobId).map(ExportJobEntity::getWorkspaceId);
    }

    @Scheduled(fixedDelayString = "${livelattice.audit.export-processing-interval-ms:5000}")
    public void processPendingExports() {
        processNextPendingJob();
    }

    @Transactional
    public void processNextPendingJob() {
        Optional<ExportJobEntity> optional = jobRepository.findFirstByStatusOrderByCreatedAtAsc("pending");
        if (optional.isEmpty()) {
            return;
        }
        ExportJobEntity job = optional.get();
        job.setStatus("processing");
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        try {
            Specification<AuditEventEntity> spec = buildSpec(job);
            List<AuditEventEntity> events = fetchAll(spec);
            byte[] content = switch (job.getFormat()) {
                case "csv" -> writeCsv(events);
                case "parquet" -> writeParquet(events);
                default -> throw new IllegalArgumentException("Unsupported export format");
            };
            String path = String.format("%s/%s/%s/audit_export_%s.%s",
                properties.getExportPrefix(), job.getWorkspaceId(), job.getFormat(), job.getId(), job.getFormat());
            upload(path, content, contentType(job.getFormat()));
            job.setStatus("completed");
            job.setArtifactPath(path);
            job.setUpdatedAt(Instant.now());
        } catch (Exception ex) {
            job.setStatus("failed");
            job.setError(ex.getMessage());
            job.setUpdatedAt(Instant.now());
        }
        jobRepository.save(job);
    }

    public byte[] loadArtifact(String path) {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
            .bucket(properties.getExportBucket())
            .object(path)
            .build())) {
            return stream.readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load export artifact: " + path, ex);
        }
    }

    private Specification<AuditEventEntity> buildSpec(ExportJobEntity job) {
        List<Specification<AuditEventEntity>> specs = new ArrayList<>();
        specs.add((root, cb, q) -> q.equal(root.get("workspaceId"), job.getWorkspaceId()));
        specs.add((root, cb, q) -> q.greaterThanOrEqualTo(root.get("occurredAt"), job.getFrom()));
        specs.add((root, cb, q) -> q.lessThanOrEqualTo(root.get("occurredAt"), job.getTo()));
        Specification<AuditEventEntity> result = Specification.where((root, cb, q) -> q.conjunction());
        for (Specification<AuditEventEntity> spec : specs) {
            result = result.and(spec);
        }
        return result;
    }

    private List<AuditEventEntity> fetchAll(Specification<AuditEventEntity> spec) {
        List<AuditEventEntity> all = new ArrayList<>();
        int page = 0;
        while (true) {
            Page<AuditEventEntity> chunk = auditEventRepository.findAll(spec,
                PageRequest.of(page, 1000, Sort.by(Sort.Direction.ASC, "occurredAt", "id")));
            all.addAll(chunk.getContent());
            if (!chunk.hasNext()) {
                break;
            }
            page++;
        }
        return all;
    }

    private byte[] writeCsv(List<AuditEventEntity> events) {
        StringBuilder csv = new StringBuilder("id,workspace_id,actor_id,action,target_type,target_id,changes,metadata,previous_hash,hash,occurred_at\n");
        for (AuditEventEntity event : events) {
            csv.append(escape(event.getId())).append(",")
                .append(escape(event.getWorkspaceId())).append(",")
                .append(escape(event.getActorId())).append(",")
                .append(escape(event.getAction())).append(",")
                .append(escape(event.getTargetType())).append(",")
                .append(escape(event.getTargetId())).append(",")
                .append(escape(event.getChanges())).append(",")
                .append(escape(event.getMetadata())).append(",")
                .append(escape(event.getPreviousHash())).append(",")
                .append(escape(event.getHash())).append(",")
                .append(DateTimeFormatter.ISO_INSTANT.format(event.getOccurredAt())).append("\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] writeParquet(List<AuditEventEntity> events) {
        try {
            List<ExportEvent> exportEvents = new ArrayList<>(events.size());
            for (AuditEventEntity event : events) {
                exportEvents.add(new ExportEvent(
                    event.getId(),
                    event.getWorkspaceId(),
                    event.getActorId(),
                    event.getAction(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getChanges(),
                    event.getMetadata(),
                    event.getPreviousHash(),
                    event.getHash(),
                    event.getOccurredAt()
                ));
            }
            return ParquetWriterHelper.write(exportEvents);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write Parquet export", ex);
        }
    }

    private void upload(String path, byte[] content, String contentType) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
            .bucket(properties.getExportBucket())
            .object(path)
            .contentType(contentType)
            .stream(new ByteArrayInputStream(content), content.length, -1)
            .build());
    }

    private String contentType(String format) {
        return switch (format) {
            case "csv" -> "text/csv";
            case "parquet" -> "application/octet-stream";
            default -> "application/octet-stream";
        };
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\"", "\"\"");
        if (text.contains(",") || text.contains("\n") || text.contains("\"")) {
            text = "\"" + text + "\"";
        }
        return text;
    }

    private ExportStatusResponse toResponse(ExportJobEntity job) {
        return new ExportStatusResponse(
            job.getId(),
            job.getStatus(),
            job.getFormat(),
            job.getArtifactPath(),
            job.getError()
        );
    }
}
