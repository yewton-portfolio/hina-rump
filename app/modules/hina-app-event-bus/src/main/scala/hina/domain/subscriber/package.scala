package hina.domain

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

package object subscriber {
  case class SubscriberId(id: UUID)
  case class Subscriber(@BeanProperty id: SubscriberId, @BeanProperty name: String)

  trait SubscriberRepository {
    def nextIdentity: SubscriberId
    def save(entity: Subscriber): Unit
    def resolve(id: SubscriberId): Subscriber
  }

  class SubscriberRepositoryOnMemory extends SubscriberRepository {
    private[this] val records: java.util.Map[SubscriberId, Subscriber] = new ConcurrentHashMap[SubscriberId, Subscriber]()
    Seq("rabbitmq-subscriber").foreach { name =>
      val id = nextIdentity
      records.put(id, Subscriber(id, name))
    }

    def resolveByName(name: String): Option[Subscriber] = {
      records.asScala.values.find(_.name == name)
    }

    override def nextIdentity: SubscriberId = SubscriberId(UUID.randomUUID())

    override def save(entity: Subscriber): Unit = records.put(entity.id, entity)

    override def resolve(id: SubscriberId): Subscriber = records.get(id)
  }

  abstract sealed class SubscriberPlugin
  object SubscriberPlugin {
    object RabbitMQ {
      def basicOptions(queue: String,
                       username: String,
                       password: String,
                       declare: String = "true",
                       autoAck: String = "false",
                       autoDelete: String = "false",
                       durable: String = "true"): Map[String, String] = Map(
        "queue" -> queue,
        "username" -> username,
        "password" -> password,
        "declare" -> declare,
        "autoAck" -> autoAck,
        "autoDelete" -> autoDelete,
        "durable" -> durable)
    }
    case class RabbitMQ(host: String,
                        port: Int,
                        exchangeName: String,
                        options: Map[String, String] = Map.empty) extends SubscriberPlugin {
      final val optionString = options.map { case (k, v) => s"$k=$v" }.mkString("&")
      final val endpointUri = s"rabbitmq://$host:$port/$exchangeName?" + optionString
    }
    case object HttpPost extends SubscriberPlugin
  }
}
