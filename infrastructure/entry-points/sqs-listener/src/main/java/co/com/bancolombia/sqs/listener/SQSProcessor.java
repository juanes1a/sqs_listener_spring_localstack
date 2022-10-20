package co.com.bancolombia.sqs.listener;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.function.Consumer;

@Service
@AllArgsConstructor
public class SQSProcessor implements Consumer<Message> {
    // private final MyUseCase myUseCase;

    @Override
    public void accept(Message message) {
        System.out.println(message.body());
        // myUseCase.doAny(message.body());
    }
}
