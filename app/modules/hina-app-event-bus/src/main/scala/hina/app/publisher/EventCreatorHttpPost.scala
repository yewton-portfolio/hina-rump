package hina.app.publisher

import java.util

import akka.actor.ActorRef
import akka.camel.CamelMessage
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.google.inject.name.Named
import hina.app.{ RestConsumer, RestProcessor }
import hina.domain.{ Event, Topic }
import hina.util.akka.NamedActor
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.camel.Exchange
import org.joda.time.DateTime

import scala.annotation.meta.field
import scala.util.{ Failure, Success, Try }

object EventCreatorHttpPost extends NamedActor {
  import Exchange._
  import HttpResponseStatus._
  override final val name = "EventCreatorHttpPost"
  final val mapper = new ObjectMapper()

  object Forwarder extends NamedActor {
    final val endpointUri = "seda:event-creator-http-post"
    override final val name = "EventCreatorHttpPostForwarder"
  }

  class Forwarder @Inject() (@Named(EventCreatorHttpPost.name) override val processor: ActorRef) extends RestConsumer {
    override def endpointUri: String = Forwarder.endpointUri
  }

  case class Response(@(JsonProperty @field)("exchange_id") exchangeId: String) {
    def asCamelMessage: CamelMessage =
      CamelMessage(mapper.writeValueAsString(this), Map(HTTP_RESPONSE_CODE -> OK.code()))
  }

  case class ErrorResponse(e: Throwable, status: HttpResponseStatus) {
    def asCamelMessage: CamelMessage = {
      val response = new util.HashMap[String, String]()
      response.put("exception", e.getClass.getSimpleName)
      response.put("message", e.getMessage)
      CamelMessage(mapper.writeValueAsString(response), Map(HTTP_RESPONSE_CODE -> status.code()))
    }
  }
}

class EventCreatorHttpPost @Inject() (@Named(EventCreator.name) val processor: ActorRef) extends RestProcessor {
  override def receive = {
    case msg: CamelMessage =>
      val t: Try[EventCreator.Request] = for {
        topic <- msg.headerAs[String]("topic")
        publisher <- msg.headerAs[String]("X-HINA-PUBLISHER")
        exchangeId <- msg.headerAs[String](CamelMessage.MessageExchangeId)
        contentType <- msg.headerAs[String]("Content-Type")
      } yield {
        val event = Event(exchangeId, publisher, contentType, DateTime.now(), msg.bodyAs[Array[Byte]])
        EventCreator.Request(Topic.withName(topic), event, sender())
      }

      t match {
        case Success(req) => processor ! req
        case Failure(e) =>
          val response = EventCreatorHttpPost.ErrorResponse(e, HttpResponseStatus.BAD_REQUEST)
          sender() ! response.asCamelMessage
      }
    case EventCreator.Response(event, originalSender) =>
      originalSender ! EventCreatorHttpPost.Response(event.exchangeId).asCamelMessage
    case EventCreator.ErrorResponse(reason, e, originalSender) => reason match {
      case EventCreator.ErrorReason.TimeOut |
        EventCreator.ErrorReason.Unknown =>
        originalSender ! EventCreatorHttpPost.ErrorResponse(e, HttpResponseStatus.INTERNAL_SERVER_ERROR).asCamelMessage
    }
  }

  final override def preRestart(reason: Throwable, message: Option[Any]) = {
    sender() ! Failure(reason)
  }
}
