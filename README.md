# `@EmbeddedKafka` and Spring Kafka auto-configuration in a `@SpringBootTest` joined with `bootstrapServersProperty`

We all are used to Spring coming with reasonable defaults for everything and everything working together smoothly out of the box.
However, with
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
and Spring Boot Kafka auto-configuration[^spring-boot-kafka-auto-configuration],
surprisingly this does not appear to be the case by default.

As simple as
`bootstrapServersProperty = "spring.kafka.bootstrap-servers"`[^embeddedkafka-bootstrap-servers-property]
does the trick.


## Preface

As a beginner to Kafka, I had quite some difficulty to find out how to make
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
and Spring Boot Kafka auto-configuration[^spring-boot-kafka-auto-configuration] in a
[`@SpringBootTest`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/context/SpringBootTest.html)
with
[`@EnableAutoConfiguration`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/EnableAutoConfiguration.html)
work together.
Everything is documented[^same-broker-multiple-tests][^messaging.kafka.embedded][^kafka-testing-embeddedkafka-annotation] as it should,
but then I did not read all the documentation up-front as supposedly many of us developers would, too.

Internet search engines did in my opinion not list the most interesting and simplest examples[^techblog]
among the top results at the time of this writing.
With a lot of Internet searching using a lot of time, I eventually dug through to a worthy and recommendable configuration,
which other people have found before[^stackoverflow-50123621][^stackoverflow-48753051][^stackoverflow-62470196][^stackoverflow-69036197],
just to mention a few, and so, credits go to them all.


## Mismatching property name defaults

Spring Boot Kafka auto-configuration[^spring-boot-kafka-auto-configuration], consisting of
[`KafkaAutoConfiguration`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/kafka/KafkaAutoConfiguration.html)
and few more classes,
picks up Kafka's originally referred to as "`bootstrap.servers`"[^kafka-consumer-bootstrap-servers] property
from the context as property named "`spring.kafka.bootstrap-servers`"[^kafka-properties-bootstrap-servers]
into
[`KafkaProperties.bootstrapServers`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/kafka/KafkaProperties.html#getBootstrapServers())
.

By default,
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
, however, provides such a value with a property with a different name, namely
"`spring.embedded.kafka.brokers`" and both don't match out of the box.

```
spring.kafka.bootstrap-servers
```

is not equal to

```
spring.embedded.kafka.brokers
```

.


## Make Spring Kafka auto-configuration pick up `@EmbeddedKafka`'s `bootstrap.servers`

The easiest and quickest approach to make them fit is configuring

```java
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
```

. Alternatively,

```java
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers = ${spring.embedded.kafka.brokers}",
})
```

would do just as well.

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
```

For the complete sources see the Github repository[^sources] accompanying this article.


## Unique consumer `group.id`s[^kafka-consumer-group-id]

Applying

```java
@TestInstance(Lifecycle.PER_CLASS)
```

to the test classes prevents JUnit from instantiating for each `@Test` method another instance of the test class
and in turn Spring instantiating each time another
[`@KafkaListener`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/annotation/KafkaListener.html)
the always same `group.id` of which then not any longer being unique (unless the test class has not more than one `@Test` method).

Whenever there is more than one consumer, used directly or indirectly by a
[`@KafkaListener`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/annotation/KafkaListener.html)
, the consumer `group.id` can be specified, in a simple way but there are plenty other variant possible,
for example like this:

```java
@KafkaListener(..., groupId = "unique-listener-group-id")
```

or

```java
consumerFactory.createConsumer("unique-consumer-group-id", null);
```

By no means is this meant to comprehensively explain consumer groups, but just as a potentially useful hint.

Also useful might be

```java
@TestPropertySource(properties = "spring.kafka.consumer.auto-offset-reset = earliest")
```

as just another example.


## Testcontainers

As an alternative for Springs
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
in-memory Kafka broker setup, using
[Testcontainers](https://github.com/testcontainers/testcontainers-java)
might feel closer to plain Kafka as it for example supports environment variables as documented
as opposed to similarly but not identically named Spring context properties,
for example "`bootstrap.servers`"[^kafka-consumer-bootstrap-servers] vs. "`spring.kafka.bootstrap-servers`"[^kafka-properties-bootstrap-servers].

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

There are plenty of examples out there in the Internet and the sheer number and variety makes it cumbersome to find appropriate examples.
Many use Kafka clusters defined outside the test and plenty more use more or less verbose ways to configure a
[`@SpringBootTest`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/context/SpringBootTest.html)
with an
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
.
The focus of the article here is to show the easiest variant to get started,
which in my opinion includes the setup of the Kafka cluster used for a test within that very same test itself.

Far as I remember, I never had anything from Spring that did not come with working defaults prior to
[`@EmbeddedKafka`](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/test/context/EmbeddedKafka.html)
and Spring Boot Kafka auto-configuration[^spring-boot-kafka-auto-configuration],
which is a big surprise and I figure justifies to spread the word about how to make it work.

After all and with the examples shown, just one piece of additional configuration is unexpectedly necessary and
writing tests with Spring is as easy as ever also with Kafka.


## References

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
