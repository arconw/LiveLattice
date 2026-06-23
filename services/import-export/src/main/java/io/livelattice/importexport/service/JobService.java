package io.livelattice.importexport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.config.ImportExportProperties;
import io.livelattice.importexport.model.JobState;
import io.livelattice.importexport.model.JobStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class JobService {

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ImportExportProperties properties;

    public JobService(StringRedisTemplate redisTemplate,
                      KafkaTemplate<String, String> kafkaTemplate,
                      ObjectMapper objectMapper,
                      ImportExportProperties properties) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public UUID createJob(String type) {
        return createJob(type, null, null);
    }

    public UUID createJob(String type, UUID workspaceId, String userSubject) {
        UUID jobId = UUID.randomUUID();
        JobState state = new JobState(jobId, type, workspaceId, userSubject, JobStatus.PENDING, 0, null, null, Instant.now(), Instant.now());
        save(state);
        return jobId;
    }

    public void updateStatus(UUID jobId, JobStatus status, int progress, String result, String error) {
        JobState current = find(jobId);
        if (current == null) {
            current = new JobState(jobId, "unknown", null, null, status, progress, result, error, Instant.now(), Instant.now());
        }
        JobState updated = new JobState(jobId, current.type(), current.workspaceId(), current.userSubject(), status, progress, result, error, current.createdAt(), Instant.now());
        save(updated);
    }

    public JobState find(UUID jobId) {
        String value = redisTemplate.opsForValue().get(key(jobId));
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, JobState.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public void submitAsyncJob(UUID jobId, String payload) {
        updateStatus(jobId, JobStatus.PENDING, 0, null, null);
        kafkaTemplate.send(properties.jobTopic(), jobId.toString(), payload);
    }

    private void save(JobState state) {
        try {
            redisTemplate.opsForValue().set(key(state.jobId()), objectMapper.writeValueAsString(state));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to save job state", e);
        }
    }

    private String key(UUID jobId) {
        return "import-export:job:" + jobId;
    }
}
