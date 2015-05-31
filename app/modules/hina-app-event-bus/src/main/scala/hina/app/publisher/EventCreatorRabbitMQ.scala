package hina.app.publisher

import akka.actor.Status.Failure
import akka.actor.{ ActorLogging, ActorRef }
import akka.camel.{ Ack, CamelMessage, Consumer }
import com.google.inject.Inject
import com.google.inject.name.Named
import hina.domain.{ Event, Topic }
import hina.util.akka.NamedActor
import org.joda.time.DateTime

import scala.util.Try

object EventCreatorRabbitMQ extends NamedActor {
  override final val name = "EventCreatorRabbitMQ"
}

class EventCreatorRabbitMQ @Inject() (@Named(EventCreator.name) val processor: ActorRef)
    extends Consumer
    with ActorLogging {
  // @todo この辺全部コンストラクタ引数にする
  val host = "hina-local-kafka02"
  val port = 5672
  val vhost = "/"
  val exchangeName = "test000"
  val options = Map(
    "username" -> "bunny",
    "password" -> "bunny",
    "declare" -> "true",
    "queue" -> exchangeName,
    "autoDelete" -> "false",
    "autoAck" -> "false"
  )
  final val optionString = options.map { case (k, v) => s"$k=$v" }.mkString("&")
  override final val endpointUri = s"rabbitmq://$host:$port/$exchangeName?$optionString"
  override def autoAck = false

  override def receive = {
    case msg: CamelMessage =>
      val t: Try[EventCreator.Request] = for {
        exchangeId <- msg.headerAs[String](CamelMessage.MessageExchangeId)
        publisher <- msg.headerAs[String]("X-HINA-PUBLISHER")
        contentType <- msg.headerAs[String]("rabbitmq.CONTENT_TYPE")
      } yield {
        val event = Event(
          exchangeId,
          publisher,
          contentType,
          DateTime.now(),
          msg.bodyAs[Array[Byte]])
        EventCreator.Request(Topic.withName(exchangeName), event, sender())
      }
      t.foreach(processor ! _)
    case EventCreator.Response(event, originalSender) =>
      originalSender ! Ack
      // @todo reply-to が設定してあれば返す等の処理
      log.debug("{} ok", event)
    case EventCreator.ErrorResponse(reason, e, originalSender) => reason match {
      case EventCreator.ErrorReason.TimeOut |
        EventCreator.ErrorReason.Unknown =>
        originalSender ! Failure(e)
        // @todo reply-to が設定してあれば返す等の処理
        log.error(e, "error")
    }
  }
}
