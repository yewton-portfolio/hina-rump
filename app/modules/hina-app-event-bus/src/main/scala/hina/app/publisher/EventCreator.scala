package hina.app.publisher

import akka.actor.{ Actor, ActorRef }
import akka.pattern.pipe
import com.google.inject.Inject
import com.google.inject.name.Named
import com.typesafe.config.Config
import hina.app.modules.Providers.ZkExecutionContextProvider
import hina.domain.publisher.TopicPublisherRepository
import hina.domain.{ Event, Topic }
import hina.util.akka.NamedActor
import org.apache.kafka.clients.producer.{ Callback, Producer, ProducerRecord, RecordMetadata }
import org.apache.kafka.common.errors.TimeoutException

import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.control.NonFatal

object EventCreator extends NamedActor {
  override final val name = "EventCreator"

  abstract sealed class ErrorReason
  object ErrorReason {
    case object TimeOut extends ErrorReason
    case object Unknown extends ErrorReason
  }

  case class Request(topic: Topic, event: Event, sender: ActorRef)
  case class Response(event: Event, originalSender: ActorRef)
  case class ErrorResponse(reason: ErrorReason, e: Throwable, originalSender: ActorRef)
}

class EventCreator @Inject() (val config: Config,
                              val repository: TopicPublisherRepository,
                              val producer: Producer[String, Event],
                              @Named(ZkExecutionContextProvider.name) implicit val executionContext: ExecutionContext)
    extends Actor {

  override def receive = {
    case EventCreator.Request(topic, event, originalSender) =>
      val record: ProducerRecord[String, Event] = new ProducerRecord(topic.name, event)
      val promise = Promise[AnyRef]()
      try {
        producer.send(record, new Callback {
          override def onCompletion(data: RecordMetadata, e: Exception): Unit = {
            val result = Option(e).map {
              case e: TimeoutException => EventCreator.ErrorResponse(EventCreator.ErrorReason.TimeOut, e, sender())
              case _                   => EventCreator.ErrorResponse(EventCreator.ErrorReason.Unknown, e, sender())
            }.getOrElse(EventCreator.Response(event, originalSender))
            promise.success(result)
          }
        })
        pipe(promise.future).to(sender())
      } catch {
        case NonFatal(e) =>
          sender() ! EventCreator.ErrorResponse(EventCreator.ErrorReason.Unknown, e, originalSender)
      }
  }
}
