package hina.app.admin

import akka.actor.{ Actor, ActorLogging, Props }
import akka.pattern.pipe
import com.google.inject.Inject
import com.google.inject.name.Named
import hina.app.modules.Providers.ZkExecutionContextProvider
import hina.domain.{ Event, Topic, TopicSubscriberRepository }
import hina.util.akka.NamedActor
import hina.util.kafka.KafkaConsumerFactory
import kafka.consumer.{ ConsumerConnector, ConsumerIterator, KafkaStream }
import kafka.serializer.Decoder
import kafka.utils.ZkUtils
import org.I0Itec.zkclient.ZkClient

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ ExecutionContext, Future, blocking }

case class StartConsume(topic: String, groupId: String, number: Int)

object StarvingConsumer extends NamedActor {
  override def name: String = "StarvingConsumer"
}

class StarvingConsumer @Inject() (kafkaConsumerFactory: KafkaConsumerFactory,
                                  keyDecoder: Decoder[String],
                                  valueDecoder: Decoder[Event],
                                  topicConsumerRepository: TopicSubscriberRepository,
                                  zkClient: ZkClient,
                                  @Named(ZkExecutionContextProvider.name) ec: ExecutionContext)
    extends Actor with ActorLogging {
  private[this] val consumers = ListBuffer.empty[ConsumerConnector]

  override def receive = {
    case StartConsume(topic, groupId, number) =>
      val consumer: ConsumerConnector = kafkaConsumerFactory.create(groupId)
      consumers.append(consumer)
      val topicCountMap = Map(topic -> number)
      val streams: List[KafkaStream[String, Event]] =
        consumer.createMessageStreams(topicCountMap, keyDecoder, valueDecoder)(topic)
      streams.foreach { (stream: KafkaStream[String, Event]) =>
        val child = context.actorOf(Props(classOf[StarvingConsumeWorker], stream, ec))
        child ! DoConsume
      }
  }

  override def preStart(): Unit = {
    ZkUtils.getAllTopics(zkClient).filter(_.startsWith(Topic.prefix)) foreach { topic =>
      self ! StartConsume(topic, s"starving-consumer-$topic", 2)
    }
  }

  override def postStop(): Unit = {
    consumers.foreach { consumer =>
      consumer.commitOffsets
      consumer.shutdown()
    }
  }
}

case object DoConsume
case class HasNext(result: Boolean)

class StarvingConsumeWorker(val kafkaStream: KafkaStream[String, Event],
                            val ec: ExecutionContext)
    extends Actor with ActorLogging {
  implicit val executionContext = ec
  val iterator: ConsumerIterator[String, Event] = kafkaStream.iterator()

  override def receive = {
    case DoConsume =>
      Future {
        blocking {
          HasNext(iterator.hasNext())
        }
      } pipeTo self
    case HasNext(result) =>
      if (result) {
        val event: Event = iterator.next().message()
        log.info(s"#### ${self.path} Consumed: " + event.toString)
        self ! DoConsume
      } else {
        context.stop(self)
      }
  }
}