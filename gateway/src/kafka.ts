import { Kafka, Partitioners, type Producer } from "kafkajs";

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

  constructor(brokers: string[]) {
    this.kafka = new Kafka({ brokers });
  }

  async connect(): Promise<void> {
    this.producer = this.kafka.producer({ createPartitioner: Partitioners.LegacyPartitioner });
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

export async function createProducer(config: { enabled: boolean; brokers: string[] }): Promise<KafkaProducerAdapter | undefined> {
  if (!config.enabled) {
    return undefined;
  }
  if (config.brokers.length === 1 && config.brokers[0].startsWith("localhost")) {
    return undefined;
  }
  const producer = new KafkaJsProducer(config.brokers);
  await producer.connect();
  return producer;
}
