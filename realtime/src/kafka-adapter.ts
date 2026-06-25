import { Kafka, type Producer } from "kafkajs";
import type { KafkaConfig } from "./config";

export interface KafkaSendBatch {
  topic: string;
  messages: { key: string; value: string }[];
}

export interface KafkaProducerAdapter {
  send(batch: KafkaSendBatch): Promise<void>;
  close(): Promise<void>;
}

export class KafkaJsProducer implements KafkaProducerAdapter {
  private readonly kafka: Kafka;
  private producer: Producer | undefined;

  constructor(config: KafkaConfig) {
    this.kafka = new Kafka({ brokers: config.brokers });
  }

  async connect(): Promise<void> {
    this.producer = this.kafka.producer();
    await this.producer.connect();
  }

  async send(batch: KafkaSendBatch): Promise<void> {
    if (!this.producer) {
      throw new Error("Producer not connected");
    }
    await this.producer.send({
      topic: batch.topic,
      messages: batch.messages.map((m) => ({ key: m.key, value: m.value }))
    });
  }

  async close(): Promise<void> {
    if (this.producer) {
      await this.producer.disconnect();
      this.producer = undefined;
    }
  }
}

export class NoopKafkaProducer implements KafkaProducerAdapter {
  sent: KafkaSendBatch[] = [];

  async send(batch: KafkaSendBatch): Promise<void> {
    this.sent.push(batch);
  }

  async close(): Promise<void> {}
}

export async function createProducer(config: KafkaConfig): Promise<KafkaProducerAdapter | undefined> {
  if (!config.enabled) {
    return undefined;
  }
  const producer = new KafkaJsProducer(config);
  try {
    await producer.connect();
    return producer;
  } catch (error) {
    console.error("Kafka producer unavailable; realtime op persistence disabled", error);
    await producer.close().catch((closeError) => {
      console.error("Kafka producer cleanup failed", closeError);
    });
    return undefined;
  }
}
