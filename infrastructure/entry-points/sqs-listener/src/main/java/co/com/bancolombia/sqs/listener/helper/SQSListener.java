package co.com.bancolombia.sqs.listener.helper;

import co.com.bancolombia.sqs.listener.config.SQSProperties;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Log4j2
@Builder
public class SQSListener implements Runnable {
    private final SqsAsyncClient client;
    private final SQSProperties properties;
    private final Consumer<Message> consumer;
    private Timer timer;

    public SQSListener start() {
        this.timer = Metrics.timer("async_operation_flow_duration",
                "operation", "MessageFrom:" + properties.getQueueUrl(), "type", "", "status", "");
        ExecutorService service = Executors.newFixedThreadPool(properties.getNumberOfThreads());
        for (int i = 0; i < properties.getNumberOfThreads(); i++) {
            service.submit(this);
        }
        return this;
    }

    @Override
    public void run() {
        while (true) {
            try {
                listen();
            } catch (Exception e) {
                log.warn("Error from SQS", e);
            }
        }
    }

    private void listen() throws ExecutionException, InterruptedException {
        ReceiveMessageResponse response = getMessages();
        log.debug("Processing {} messages", response.messages().size());
        response.messages()
                .stream()
                .parallel()
                .peek(message -> timer.record(() -> consumer.accept(message)))
                .forEach(this::confirm);
    }

    @SneakyThrows
    private void confirm(Message message) {
        DeleteMessageRequest request = getDeleteMessageRequest(message.receiptHandle());
        client.deleteMessage(request).get();
        log.debug("Message confirmed {}", message.messageId());
    }

    private ReceiveMessageResponse getMessages() throws ExecutionException, InterruptedException {
        ReceiveMessageRequest request = getReceiveMessageRequest();
        return client.receiveMessage(request).get();
    }

    private ReceiveMessageRequest getReceiveMessageRequest() {
        return ReceiveMessageRequest.builder()
                .queueUrl(properties.getQueueUrl())
                .maxNumberOfMessages(properties.getMaxNumberOfMessages())
                .waitTimeSeconds(properties.getWaitTimeSeconds())
                .visibilityTimeout(properties.getVisibilityTimeout())
                .build();
    }

    private DeleteMessageRequest getDeleteMessageRequest(String receiptHandle) {
        return DeleteMessageRequest.builder()
                .queueUrl(properties.getQueueUrl())
                .receiptHandle(receiptHandle)
                .build();
    }

}
