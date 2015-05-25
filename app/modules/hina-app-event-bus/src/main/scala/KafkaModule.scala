import java.util
import java.util.Properties

import com.google.inject.{AbstractModule, Inject, Provides, Singleton}
import com.typesafe.config.{Config, ConfigValue}
import kafka.consumer.{Consumer, ConsumerConfig, ConsumerConnector}
import kafka.serializer.{Decoder, DefaultDecoder, StringDecoder}
import net.codingwell.scalaguice.ScalaModule
import org.apache.kafka.clients.producer.{KafkaProducer, Producer}

import scala.collection.JavaConverters._

trait KafkaConsumerFactory {
  def create(groupId: String): ConsumerConnector
}

class ZooKeeperConsumerFactory @Inject() (config: Config) extends KafkaConsumerFactory {
  def create(groupId: String): ConsumerConnector = {
    val props = new Properties()
    props.put("zookeeper.connect", config.getString("zookeeper.connect"))
    props.put("group.id", groupId)
    props.put("auto.commit.enable", "false")
    props.put("offsets.storage", "kafka")
    props.put("dual.commit.enabled", "false")
    props.put("auto.offset.reset", "smallest")
    props.put("exclude.internal.topics", "true")
    config.getConfig("kafka.consumer").root().asScala.foreach {
      case (key: String, value: ConfigValue) =>
        props.put(key, value.render())
    }
    val consumerConfig = new ConsumerConfig(props)
    Consumer.create(consumerConfig)
  }
}

class KafkaModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[KafkaConsumerFactory].to[ZooKeeperConsumerFactory].asEagerSingleton()
  }

  @Provides
  @Singleton
  def provideStringDecoder: Decoder[String] = new StringDecoder()

  @Provides
  @Singleton
  def provideByteArrayDecoder: Decoder[Array[Byte]] = new DefaultDecoder()

  @Provides
  @Inject
  def provideKafkaProducer(config: Config): Producer[String, Array[Byte]] = {
    val producerConfigs: util.Map[String, Object] = config.getConfig("kafka.producer").root().unwrapped()
    producerConfigs.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producerConfigs.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
    new KafkaProducer(producerConfigs)
  }
}
