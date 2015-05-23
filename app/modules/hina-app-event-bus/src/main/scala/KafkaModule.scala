import java.util

import com.google.inject.{AbstractModule, Inject, Provides}
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.apache.kafka.clients.producer.{KafkaProducer, Producer}

class KafkaModule extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    bind[Producer[String, Array[Byte]]].to[KafkaProducer[String, Array[Byte]]]
  }

  @Provides
  @Inject
  def provideKafkaProducer(config: Config): KafkaProducer[String, Array[Byte]] = {
    val producerConfigs: util.Map[String, Object] = config.getConfig("kafka.producer").root().unwrapped()
    producerConfigs.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producerConfigs.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
    new KafkaProducer(producerConfigs)
  }
}
