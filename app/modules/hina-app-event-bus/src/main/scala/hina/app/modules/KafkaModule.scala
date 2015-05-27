package hina.app.modules

import java.util

import com.google.inject.{AbstractModule, Inject, Provides, Singleton}
import com.typesafe.config.Config
import hina.util.kafka.{KafkaConsumerFactory, ZooKeeperConsumerFactory}
import kafka.serializer.{Decoder, DefaultDecoder, StringDecoder}
import net.codingwell.scalaguice.ScalaModule
import org.apache.kafka.clients.producer.{KafkaProducer, Producer}

/**
 *
 */
class KafkaModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[KafkaConsumerFactory].to[ZooKeeperConsumerFactory].in[Singleton]
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
