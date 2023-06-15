![build status](https://github.com/dev-qnz/embedded-kafka-spring-auto-configuration-test/actions/workflows/build-and-test.yml/badge.svg)

# `@EmbeddedKafka` and Spring Boot Kafka auto-configuration in a `@SpringBootTest` joined with `bootstrapServersProperty`

We all are used to Spring coming with reasonable defaults for everything and everything working together smoothly out of the box.
Surprisingly, however, this does not appear to be the case by default with
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
and
[`@EnableAutoConfiguration`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/EnableAutoConfiguration.html)
in a
[`@SpringBootTest`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/context/SpringBootTest.html).

Eventually, as simple as
`bootstrapServersProperty = "spring.kafka.bootstrap-servers"`[^embeddedkafka-bootstrap-servers-property]
fixes the trick.


## Mismatching property name defaults

Everything is documented[^same-broker-multiple-tests][^messaging.kafka.embedded][^kafka-testing-embeddedkafka-annotation] as it should,
but then I did not read all the documentation up-front as supposedly many of us developers would, too.

Spring Boot Kafka auto-configuration[^spring-boot-kafka-auto-configuration], consisting of
[`KafkaAutoConfiguration`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/kafka/KafkaAutoConfiguration.html)
and few more classes,
picks up Kafka's originally referred to as "`bootstrap.servers`"[^kafka-consumer-bootstrap-servers] property
from the context as property named "`spring.kafka.bootstrap-servers`"[^kafka-properties-bootstrap-servers]
into
[`KafkaProperties.bootstrapServers`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/kafka/KafkaProperties.html#getBootstrapServers())
.

By default,
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html),
however, provides such a value with a property with a different name, named
"`spring.embedded.kafka.brokers`" and both don't match out of the box:

```
spring.kafka.bootstrap-servers
```

is not equal to

```
spring.embedded.kafka.brokers
```

.


## Make Spring Kafka auto-configuration pick up `@EmbeddedKafka`'s `bootstrap.servers`

The easiest and quickest approach to make the context property names fit is configuring

```java
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
```

. Alternatively,

```java
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers = ${spring.embedded.kafka.brokers}",
})
```

would do just as well among many more possibilities.

The essential parts of a Spring Boot test with
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
might look like this:

```java
@SpringBootTest
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class EmbeddedKafkaTest {

    @Configuration
    @EnableAutoConfiguration
    static class TestConfiguration {
    }

    ...
}
```

With that configuration,
[`@KafkaListener`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/annotation/KafkaListener.html)
annotated consumer methods and all kinds of
[`@Autowired`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/annotation/Autowired.html)
dependencies such as
[`KafkaTemplate`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/core/KafkaTemplate.html),
[`KafkaAdmin`](https://docs.spring.io/spring-kafka/docs/current/api/org/springframework/kafka/core/KafkaAdmin.html),
[`ConsumerFactory`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/core/ConsumerFactory.html),
and
[`ProducerFactory`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/core/ProducerFactory.html)
already resolve.

With some more minor tweaks explained further below, a working example looks like this:

```java
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
    topics = EmbeddedKafkaTest.TOPIC_NAME
)
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset = earliest")
@TestInstance(Lifecycle.PER_CLASS)
class EmbeddedKafkaTest {

    @Configuration
    @EnableAutoConfiguration
    static class TestConfiguration {
    }

    static final String TOPIC_NAME = "topic";

    @Autowired
    private KafkaAdmin admin;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private ProducerFactory<String, String> producerFactory;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private BlockingQueue<ConsumerRecord<String, String>> consumptionQueue = new LinkedBlockingDeque<>();

    @KafkaListener(topics = TOPIC_NAME, groupId = "listener")
    private void listen(ConsumerRecord<String, String> consumerRecord) throws InterruptedException {
        consumptionQueue.put(consumerRecord);
    }

    @Test
    void testProducerAndConsumer() throws Exception {
        final String KEY = "key1", VALUE = "value1";
        try (
            Consumer<String, String> consumer = consumerFactory.createConsumer("consumer", null);
            Producer<String, String> producer = producerFactory.createProducer();
        ) {
            consumer.subscribe(asList(TOPIC_NAME));

            producer.send(new ProducerRecord<>(TOPIC_NAME, KEY, VALUE), (metadata, exception) -> {
            }).get();
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));

            assertThat(records).singleElement().satisfies(singleRecord -> {
                assertThat(singleRecord.key()).isEqualTo(KEY);
                assertThat(singleRecord.value()).isEqualTo(VALUE);
            });
            consumer.commitSync();
            consumer.unsubscribe();
        }
    }

    @Test
    void testTemplateAndListener() throws Exception {
        final String KEY = "key2", VALUE = "value2";
        consumptionQueue.clear();

        kafkaTemplate.send(TOPIC_NAME, KEY, VALUE).get();
        ConsumerRecord<String, String> consumerRecord = consumptionQueue.poll(3, TimeUnit.SECONDS);

        assertThat(consumerRecord.key()).isEqualTo(KEY);
        assertThat(consumerRecord.value()).isEqualTo(VALUE);
        assertThat(consumptionQueue).isEmpty();
    }

    @Test
    void checkBootstrapServersParameterResolutionExample(
        @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
        @Autowired EmbeddedKafkaBroker broker
    ) throws Exception {
        assertThat(broker.getBrokersAsString()).isEqualTo(bootstrapServers);
    }

    @Test
    void testAdmin() {
        assertThat(admin.describeTopics(TOPIC_NAME)).containsKey(TOPIC_NAME);
    }

}
```


### Unique consumer `group.id`s[^kafka-consumer-group-id]

Whenever there is more than one consumer, no matter whether created explicitly or indirectly by a
[`@KafkaListener`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/annotation/KafkaListener.html),
each consumer's `group.id` has to be specified in order to be unique.
There are plenty other ways possible, but it can be achieved for example like this:

```java
@KafkaListener(..., groupId = "unique-listener-group-id")
```

or

```java
consumerFactory.createConsumer("unique-consumer-group-id", null);
```

By no means is this meant to comprehensively explain consumer groups, just as a potentially useful hint.

Applying

```java
@TestInstance(Lifecycle.PER_CLASS)
```

to the test classes prevents JUnit from instantiating for each
[`@Test`](https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/Test.html)
method another instance of the test class
and in turn Spring instantiating another context including each time another
[`@KafkaListener`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/annotation/KafkaListener.html)
the always same `group.id` of which then not any longer being unique
because always based on the very same configuration
(obviously except when the test class has not more than one
[`@Test`](https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/Test.html)
method).


### auto.offset.reset = earliest

Also useful might be:

```java
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset = earliest")
```


## A side-note to alternatives

### Docker Compose

There are many examples out there that work with Docker Compose.
While that certainly has its point, however, in my opinion, it is generally preferable that tests include all the setup they need,
which is the case with
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
and is not with Docker Compose. With
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
anyone can just check out and start a test from within their favorite IDE.
With Docker Compose some Docker containers usually have to be started before running tests or
worse if there are for example port collisions or left over data from previously run tests.


### Testcontainers

Probably the closest alternative to
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
in-memory Kafka broker setup with Spring is
[Testcontainers](https://java.testcontainers.org/).

It supports for example environment variables as documented and
as opposed to similarly but not identically named Spring context properties,
for example "`bootstrap.servers`"[^kafka-consumer-bootstrap-servers] vs. "`spring.kafka.bootstrap-servers`"[^kafka-properties-bootstrap-servers]
and generally might feel to come closer to plain Kafka or a production setup than
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
ever will.

Testcontainers provides with
[`KafkaContainer`](https://www.testcontainers.org/modules/kafka/)
for configuring containers specifically for Kafka brokers.
One minor difference to
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
might be that the latter supports configuring the number of brokers with a plain number with
[`EmbeddedKafka.count`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html#count())
whereas Testcontainers does not support multiple brokers exactly as easily.
For an example cluster setup, implying an appropriate example having multiple cluster nodes,
I'd recommend to check out
[`KafkaContainerCluster`](https://github.com/testcontainers/testcontainers-java/blob/main/examples/kafka-cluster/src/test/java/com/example/kafkacluster/KafkaContainerCluster.java)
[^kafka-container-cluster] as a starting point with Testcontainers.


## Conclusion

There are plenty of examples out there in the Internet and their sheer number and variety makes it cumbersome to find appropriate examples.

As far as I remember, I never had anything from Spring that did not come with working defaults prior to
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
and Spring Boot Kafka auto-configuration[^spring-boot-kafka-auto-configuration],
which is a big surprise and I figure justifies to spread the word about how to make it work.

After all and with the example shown, just one piece of additional configuration is unexpectedly necessary and
writing tests with Spring is as easy as ever also with Kafka.


## References

@EmbeddedKafka and Spring Boot Kafka auto-configuration in a @SpringBootTest joined with bootstrapServersProperty:
https://blog.mimacom.com/embeddedkafka-kafka-auto-configure-springboottest-bootstrapserversproperty/

Testing an Apache Kafka Integration within a Spring Boot Application and JUnit 5 (mimacom Tech Blog):
https://blog.mimacom.com/testing-apache-kafka-with-spring-boot-junit5/

Repository on Github accompanying this article with the complete sources:
https://github.com/dev-qnz/embedded-kafka-spring-auto-configuration-test


[^sources]: Repository on Github accompanying this article with the complete sources
https://github.com/dev-qnz/embedded-kafka-spring-auto-configuration-test

[^techblog]: Testing an Apache Kafka Integration within a Spring Boot Application and JUnit 5 (mimacom Tech Blog)
https://blog.mimacom.com/testing-apache-kafka-with-spring-boot-junit5/

[^spring-boot-kafka-auto-configuration]: Spring Boot Kafka auto-configuration
https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.kafka

[^same-broker-multiple-tests]: `spring.embedded.kafka.brokers.property=spring.kafka.bootstrap-servers`
https://docs.spring.io/spring-kafka/docs/current/reference/html/#same-broker-multiple-tests

[^messaging.kafka.embedded]: `@EmbeddedKafka(..., bootstrapServersProperty = "spring.kafka.bootstrap-servers")`
https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.kafka.embedded

[^kafka-testing-embeddedkafka-annotation]: `@EmbeddedKafka(..., bootstrapServersProperty = "spring.kafka.bootstrap-servers")`
https://docs.spring.io/spring-kafka/docs/current/reference/html/#kafka-testing-embeddedkafka-annotation

[^kafka-consumer-bootstrap-servers]: broker "`bootstrap.servers`" property - Apache Kafka configuration reference
https://kafka.apache.org/documentation/#producerconfigs_bootstrap.servers

[^kafka-consumer-group-id]: consumer "`group.id`" property - Apache Kafka configuration reference
https://kafka.apache.org/documentation/#consumerconfigs_group.id

[^kafka-container-cluster]: KafkaContainerCluster
https://github.com/testcontainers/testcontainers-java/blob/main/examples/kafka-cluster/src/test/java/com/example/kafkacluster/KafkaContainerCluster.java

[^stackoverflow-50123621]: `@TestPropertySource(properties = { "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}" })`
https://stackoverflow.com/questions/50123621/testing-a-kafkalistener-using-spring-embedded-kafka

[^stackoverflow-48753051]: `application.properties: spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}`
https://stackoverflow.com/questions/48753051/simple-embedded-kafka-test-example-with-spring-boot/48756251

[^stackoverflow-62470196]: `@EmbeddedKafka(..., bootstrapServersProperty = "spring.kafka.bootstrap-servers")`
https://stackoverflow.com/questions/62470196/spring-kafka-test-with-confluent-kafka-avro-serializer-cannot-find-zkclientconfi

[^stackoverflow-69036197]: `@EmbeddedKafka(..., bootstrapServersProperty = "spring.kafka.bootstrap-servers")`
https://stackoverflow.com/questions/69036197/embedded-kafka-integration-test-consumer-never-completes#69041287

[^embeddedkafka-bootstrap-servers-property]: `EmbeddedKafka.bootstrapServersProperty`
https://docs.spring.io/spring-kafka/docs/current/api/org/springframework/kafka/test/context/EmbeddedKafka.html#bootstrapServersProperty()

[^kafka-properties-bootstrap-servers]: `KafkaProperties.bootstrapServers`
https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/kafka/KafkaProperties.html#getBootstrapServers()
