package hina.samples

import java.util.UUID

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.camel.{ CamelMessage, Producer }

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 *
 */
object RabbitMQPublisher extends App {

  val system = ActorSystem("rabbitmq-publisher")
  val Tick = "tick"
  val a = system.actorOf(Props[RabbitMQPublisher])
  val k = system.actorOf(Props(classOf[Kicker], a))
  import system.dispatcher
  system.scheduler.schedule(0 milliseconds,
    10 milliseconds,
    k,
    Tick)

  class Kicker(ref: ActorRef) extends Actor {
    override def receive = {
      case Tick =>
        val headers = Map(
          "rabbitmq.CONTENT_TYPE" -> "text/plain",
          "rabbitmq.DELIVERY_MODE" -> "2",
          "X-HINA-PUBLISHER" -> "rabbit-publisher")
        val data = CamelMessage("test-data-" + UUID.randomUUID().toString, headers)
        println(data.toString())
        ref ! data
    }
  }
}

class RabbitMQPublisher extends Producer {
  final val options = Map(
    "username" -> "bunny",
    "password" -> "bunny",
    "declare" -> "true",
    "queue" -> "test000",
    "autoDelete" -> "false",
    "autoAck" -> "false"
  )
  final val optionString = options.map { case (k, v) => s"$k=$v" }.mkString("&")
  override val endpointUri = "rabbitmq://hina-local-kafka02/test000?" + optionString
}
