package hina.app.subscriber

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.pattern.pipe
import com.google.inject.Inject
import com.google.inject.name.Named
import hina.app.modules.Providers.ZkExecutionContextProvider
import hina.domain.subscriber.{ Subscriber, SubscriberPlugin }
import hina.domain.{ Event, Topic, TopicSubscriberRepository }
import hina.util.akka.NamedActor
import hina.util.kafka.KafkaConsumerFactory
import kafka.consumer.{ ConsumerConnector, ConsumerIterator, KafkaStream }
import kafka.serializer.Decoder
import org.I0Itec.zkclient.ZkClient

import scala.collection.mutable
import scala.concurrent._
import scala.util.control.NonFatal

object EventConsumer extends NamedActor {
  override final val name = "EventConsumer"

  case class Start(topic: Topic,
                   subscriber: Subscriber,
                   plugin: SubscriberPlugin,
                   numThreads: Int)

  case class Restart(req: Start)

  object Worker {
    case object DoConsume
    case class HasNext(result: Boolean)
  }

  class Worker(val kafkaStream: KafkaStream[String, Event],
               val receiver: ActorRef,
               val restartRequest: EventConsumer.Restart,
               implicit val ec: ExecutionContext)
      extends Actor with ActorLogging {
    val iterator: ConsumerIterator[String, Event] = kafkaStream.iterator()

    override def receive = {
      case Worker.DoConsume =>
        val f = Future {
          blocking {
            Worker.HasNext(iterator.hasNext())
          }
        }
        pipe(f).to(self)
      case Worker.HasNext(result) =>
        if (result) {
          try {
            val event: Event = iterator.next().message()
            receiver ! event
          } catch {
            case NonFatal(e) =>
              log.error(e, "iterator.next threw an exception. stopping.")
              self ! akka.actor.Status.Failure(e)
          }
        } else {
          self ! akka.actor.Status.Failure(new RuntimeException("hasNext returns false"))
        }
      case akka.actor.Status.Failure(e) =>
        log.error(e, "stopping")
        context.parent ! restartRequest
        context.stop(self)
    }
  }
}

class EventConsumer @Inject() (kafkaConsumerFactory: KafkaConsumerFactory,
                               keyDecoder: Decoder[String],
                               valueDecoder: Decoder[Event],
                               topicConsumerRepository: TopicSubscriberRepository,
                               zkClient: ZkClient,
                               @Named(ZkExecutionContextProvider.name) implicit val ec: ExecutionContext)
    extends Actor {
  private[this] val consumers: mutable.Map[(Topic, Subscriber), ConsumerConnector] = mutable.Map.empty

  override def receive = {
    case request @ EventConsumer.Start(topic, subscriber, plugin, numThreads) =>
      // @todo receiver は HTTP の場合は要らないとかそういう場合わけ
      val receiver: ActorRef = plugin match {
        case _: SubscriberPlugin.RabbitMQ =>
          context.actorOf(Props(classOf[EventConsumerRabbitMQ], plugin))
        case _ => ???
      }
      val consumer: ConsumerConnector = kafkaConsumerFactory.create(subscriber.name)
      consumers.put((topic, subscriber), consumer)
      val topicCountMap = Map(topic.name -> numThreads)
      val streams: List[KafkaStream[String, Event]] =
        consumer.createMessageStreams(topicCountMap, keyDecoder, valueDecoder)(topic.name)
      streams.foreach { (stream: KafkaStream[String, Event]) =>
        val child = context.actorOf(Props(
          classOf[EventConsumer.Worker],
          stream,
          receiver,
          EventConsumer.Restart(request),
          ec))
        child ! EventConsumer.Worker.DoConsume
      }
    case EventConsumer.Restart(request) =>
      val key = (request.topic, request.subscriber)
      consumers.get(key).foreach(_.shutdown())
      consumers.remove(key)
      self ! request
  }

  override def preStart(): Unit = {
    topicConsumerRepository.findAll.foreach {
      case (topic, subscribers: Set[(Subscriber, SubscriberPlugin)]) =>
        subscribers.foreach {
          case (subscriber, plugin) =>
            self ! EventConsumer.Start(topic, subscriber, plugin, 2)
        }
    }
  }

  override def postStop(): Unit = {
    consumers.foreach {
      case (_, consumer) =>
        consumer.shutdown()
    }
  }
}
