package hina.util.kafka

import java.util.Properties

import com.google.inject.Inject
import com.typesafe.config.{ Config, ConfigValue }
import kafka.consumer.{ Consumer, ConsumerConfig, ConsumerConnector }

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
