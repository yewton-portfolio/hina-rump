package hina.app.modules

import java.util

import com.google.inject.{ AbstractModule, Inject, Provides, Singleton }
import com.typesafe.config.Config
import hina.app.publisher.EventDecoder
import hina.domain.{ Event, EventSerializer }
import hina.util.kafka.{ KafkaConsumerFactory, ZooKeeperConsumerFactory }
import kafka.serializer.{ Decoder, DefaultDecoder, StringDecoder }
import net.codingwell.scalaguice.ScalaModule
import org.apache.kafka.clients.producer.{ KafkaProducer, Producer }
import org.apache.kafka.common.serialization.StringSerializer

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
  @Singleton
  def provideEventDecoder: Decoder[Event] = new EventDecoder()

  @Provides
  @Inject
  @Singleton
  def provideKafkaProducer(config: Config): Producer[String, Event] = {
    val producerConfigs: util.Map[String, Object] = config.getConfig("kafka.producer").root().unwrapped()
    new KafkaProducer(producerConfigs, new StringSerializer, new EventSerializer)
  }
}
