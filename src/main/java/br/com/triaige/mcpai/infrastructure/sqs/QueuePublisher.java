package br.com.triaige.mcpai.infrastructure.sqs;

public interface QueuePublisher {

    void publish(String queueUrl, Object payload);

    void publish(String queueUrl, Object payload, long delaySeconds);
}
