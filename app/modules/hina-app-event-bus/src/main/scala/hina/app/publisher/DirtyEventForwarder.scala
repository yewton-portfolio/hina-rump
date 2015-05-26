package hina.app.publisher

import akka.actor.ActorRef
import akka.actor.Status.Failure
import akka.camel.{ CamelMessage, Consumer }
import com.google.inject.Inject
import com.google.inject.name.Named
import hina.util.akka.NamedActor

import scala.concurrent.duration._
import scala.language.postfixOps

object DirtyEventForwarder extends NamedActor {
  override final val name = "DirtyEventForwarder"
  final val endpointUri = "seda:dirty-event"
}

class DirtyEventForwarder @Inject() (@Named(DirtyEventProcessor.name) processor: ActorRef) extends Consumer {

  override def replyTimeout = 5000 millis

  override def endpointUri = DirtyEventForwarder.endpointUri

  override def receive = {
    case msg: CamelMessage =>
      val t = for {
        name <- msg.headerAs[String]("name")
        publisherId <- msg.headerAs[String]("X-HINA-PUBLISHER-NAME")
        exchangeId <- msg.headerAs[String](CamelMessage.MessageExchangeId)
      } yield {
        DirtyEventRequest(name, publisherId, msg.bodyAs[String], exchangeId)
      }
      t match {
        case scala.util.Success(req) => processor forward req
        case scala.util.Failure(e)   => processor forward DirtyEventBadRequest(e)
      }
  }

  final override def preRestart(reason: Throwable, message: Option[Any]) = {
    sender() ! Failure(reason)
  }
}
