import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset = earliest")
class MinimalEmbeddedKafkaTest {

    @Configuration
    @EnableAutoConfiguration
    static class TestConfiguration {
    }

    private static final String TOPIC_NAME = "topic";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private BlockingQueue<ConsumerRecord<String, String>> consumptionQueue = new LinkedBlockingDeque<>();

    @KafkaListener(topics = TOPIC_NAME, groupId = "group-id")
    private void listen(ConsumerRecord<String, String> consumerRecord) throws InterruptedException {
        consumptionQueue.put(consumerRecord);
    }

    @Test
    void test() throws Exception {
        final String KEY = "key", VALUE = "value";
        kafkaTemplate.send(TOPIC_NAME, KEY, VALUE).get();
        ConsumerRecord<String, String> consumerRecord = consumptionQueue.poll(3, TimeUnit.SECONDS);

        assertThat(consumerRecord.key()).isEqualTo(KEY);
        assertThat(consumerRecord.value()).isEqualTo(VALUE);
    }

}
