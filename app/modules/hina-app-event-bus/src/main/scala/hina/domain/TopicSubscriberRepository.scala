package hina.domain

import com.google.inject.Inject
import com.typesafe.config.Config
import hina.domain.subscriber.{ Subscriber, SubscriberPlugin, SubscriberRepositoryOnMemory }

trait TopicSubscriberRepository {
  def findAll: Map[Topic, Set[(Subscriber, SubscriberPlugin)]]
  def resolve(topic: Topic): Set[(Subscriber, SubscriberPlugin)]
}

object TopicSubscriberRepositoryOnMemory {
  val subscriberRepository = new SubscriberRepositoryOnMemory
  final val records: Map[Topic, Set[(Subscriber, SubscriberPlugin)]] = Map(
    Topic.withName("test000") ->
      Set(
        subscriberRepository.resolveByName("rabbitmq-subscriber").get ->
          SubscriberPlugin.RabbitMQ(
            "hina-local-kafka02",
            5672,
            "test000-subscribe",
            SubscriberPlugin.RabbitMQ.basicOptions(
              "test000-subscribe",
              "bunny",
              "bunny"))))
}
class TopicSubscriberRepositoryOnMemory @Inject() (config: Config) extends TopicSubscriberRepository {
  override def findAll: Map[Topic, Set[(Subscriber, SubscriberPlugin)]] =
    TopicSubscriberRepositoryOnMemory.records
  override def resolve(topic: Topic): Set[(Subscriber, SubscriberPlugin)] =
    TopicSubscriberRepositoryOnMemory.records.getOrElse(topic, Set.empty)
}
