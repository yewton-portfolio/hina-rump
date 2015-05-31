package hina.app.subscriber

import akka.actor.{ Actor, ActorRef, Props }
import akka.camel.{ CamelMessage, Producer }
import hina.domain.Event
import hina.domain.subscriber.SubscriberPlugin

object EventConsumerRabbitMQ {
  class InternalProducer(plugin: SubscriberPlugin.RabbitMQ) extends Actor with Producer {
    // @todo handle nack/ack
    override final val endpointUri = plugin.endpointUri
  }
}

class EventConsumerRabbitMQ(plugin: SubscriberPlugin.RabbitMQ) extends Actor {
  private[this] val producer: ActorRef = context.actorOf(Props(classOf[EventConsumerRabbitMQ.InternalProducer], plugin))

  def receive = {
    case Event(exchangeId, publisher, contentType, acceptedAt, body) =>
      producer ! CamelMessage(body, Map(
        "rabbitmq.CONTENT_TYPE" -> contentType,
        "rabbitmq.DELIVERY_MODE" -> "2"))
      sender() ! EventConsumer.Worker.DoConsume
  }
}
