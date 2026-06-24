package io.livelattice.auditlog.service;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;
import org.apache.parquet.io.SeekableInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ParquetWriterHelper {

    static final Schema SCHEMA = new Schema.Parser().parse("""
        {
          "type": "record",
          "name": "AuditEvent",
          "fields": [
            {"name": "id", "type": "string"},
            {"name": "workspace_id", "type": "string"},
            {"name": "actor_id", "type": "string"},
            {"name": "action", "type": "string"},
            {"name": "target_type", "type": "string"},
            {"name": "target_id", "type": "string"},
            {"name": "changes", "type": "string"},
            {"name": "metadata", "type": "string"},
            {"name": "previous_hash", "type": "string"},
            {"name": "hash", "type": "string"},
            {"name": "occurred_at", "type": "string"}
          ]
        }
        """);

    private ParquetWriterHelper() {
    }

    static byte[] write(List<ExportEvent> events) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputFile file = outputFile(output);
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(file)
            .withSchema(SCHEMA)
            .withCompressionCodec(CompressionCodecName.UNCOMPRESSED)
            .build()) {
            for (ExportEvent event : events) {
                GenericRecord record = new GenericData.Record(SCHEMA);
                record.put("id", event.id());
                record.put("workspace_id", event.workspaceId());
                record.put("actor_id", event.actorId());
                record.put("action", event.action());
                record.put("target_type", event.targetType());
                record.put("target_id", event.targetId());
                record.put("changes", event.changes());
                record.put("metadata", event.metadata());
                record.put("previous_hash", event.previousHash());
                record.put("hash", event.hash());
                record.put("occurred_at", DateTimeFormatter.ISO_INSTANT.format(event.occurredAt()));
                writer.write(record);
            }
        }
        return output.toByteArray();
    }

    static List<ChainEvent> readChainEvents(byte[] content) throws IOException {
        List<ChainEvent> events = new ArrayList<>();
        readChainEvents(content, 1000, events::addAll);
        return events;
    }

    static long readChainEvents(byte[] content, int batchSize, Consumer<List<ChainEvent>> consumer) throws IOException {
        int safeBatchSize = Math.max(1, batchSize);
        long count = 0;
        List<ChainEvent> batch = new ArrayList<>(safeBatchSize);
        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(inputFile(content)).build()) {
            GenericRecord record = reader.read();
            while (record != null) {
                batch.add(chainEvent(record));
                count++;
                if (batch.size() == safeBatchSize) {
                    consumer.accept(List.copyOf(batch));
                    batch.clear();
                }
                record = reader.read();
            }
        }
        if (!batch.isEmpty()) {
            consumer.accept(List.copyOf(batch));
        }
        return count;
    }

    private static ChainEvent chainEvent(GenericRecord record) {
        return new ChainEvent(
            text(record, "id"),
            text(record, "action"),
            text(record, "target_id"),
            text(record, "changes"),
            text(record, "previous_hash"),
            text(record, "hash")
        );
    }

    private static String text(GenericRecord record, String field) {
        Object value = record.get(field);
        return value == null ? "" : value.toString();
    }

    private static InputFile inputFile(byte[] content) {
        return new InputFile() {
            @Override
            public long getLength() {
                return content.length;
            }

            @Override
            public SeekableInputStream newStream() {
                return new SeekableByteArrayInputStream(content);
            }
        };
    }

    private static OutputFile outputFile(ByteArrayOutputStream output) {
        return new OutputFile() {
            @Override
            public PositionOutputStream create(long expectedSize) {
                return stream(output);
            }

            @Override
            public PositionOutputStream createOrOverwrite(long expectedSize) {
                output.reset();
                return stream(output);
            }

            @Override
            public boolean supportsBlockSize() {
                return false;
            }

            @Override
            public long defaultBlockSize() {
                return 0;
            }

            private PositionOutputStream stream(ByteArrayOutputStream out) {
                return new PositionOutputStream() {
                    @Override
                    public long getPos() {
                        return out.size();
                    }

                    @Override
                    public void write(int b) {
                        out.write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        out.write(b, off, len);
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };
    }

    private static final class SeekableByteArrayInputStream extends SeekableInputStream {

        private final byte[] content;
        private int position;

        private SeekableByteArrayInputStream(byte[] content) {
            this.content = content;
        }

        @Override
        public int read() {
            if (position >= content.length) {
                return -1;
            }
            return content[position++] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (length == 0) {
                return 0;
            }
            if (position >= content.length) {
                return -1;
            }
            int count = Math.min(length, content.length - position);
            System.arraycopy(content, position, bytes, offset, count);
            position += count;
            return count;
        }

        @Override
        public int read(ByteBuffer byteBuffer) {
            if (!byteBuffer.hasRemaining()) {
                return 0;
            }
            if (position >= content.length) {
                return -1;
            }
            int count = Math.min(byteBuffer.remaining(), content.length - position);
            byteBuffer.put(content, position, count);
            position += count;
            return count;
        }

        @Override
        public void readFully(byte[] bytes) throws IOException {
            readFully(bytes, 0, bytes.length);
        }

        @Override
        public void readFully(byte[] bytes, int start, int length) throws IOException {
            if (length == 0) {
                return;
            }
            int count = read(bytes, start, length);
            if (count < length) {
                throw new IOException("Unexpected end of Parquet content");
            }
        }

        @Override
        public void readFully(ByteBuffer byteBuffer) throws IOException {
            while (byteBuffer.hasRemaining()) {
                int count = read(byteBuffer);
                if (count < 0) {
                    throw new IOException("Unexpected end of Parquet content");
                }
            }
        }

        @Override
        public long getPos() {
            return position;
        }

        @Override
        public void seek(long newPos) throws IOException {
            if (newPos < 0 || newPos > content.length) {
                throw new IOException("Invalid seek position");
            }
            position = Math.toIntExact(newPos);
        }
    }
}
