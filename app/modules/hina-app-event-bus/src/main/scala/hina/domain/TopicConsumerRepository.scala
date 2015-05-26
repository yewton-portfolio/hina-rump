package hina.domain

import com.google.inject.Inject
import com.typesafe.config.Config

/**
 *
 */
trait TopicConsumerRepository {
  def resolve(topic: String): Set[String]
}

class TopicConsumerRepositoryOnMemory @Inject() (config: Config) extends TopicConsumerRepository {
  override def resolve(topic: String): Set[String] = TopicConsumerRepositoryOnMemory.records.getOrElse(topic, Set.empty)
}

object TopicConsumerRepositoryOnMemory {
  final val records: Map[String, Set[String]] = Map(
    "test" -> Set("test-consumer"),
    "test2" -> Set("test2-consumer1", "test2-consumer2")
  )
}