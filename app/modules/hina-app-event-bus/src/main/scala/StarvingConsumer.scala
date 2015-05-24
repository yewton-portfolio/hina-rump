import akka.actor.{ Actor, ActorLogging, Props }
import akka.pattern.pipe
import com.google.inject.Inject
import kafka.consumer.{ ConsumerConnector, ConsumerIterator, KafkaStream }
import kafka.serializer.Decoder
import kafka.utils.ZkUtils
import org.I0Itec.zkclient.ZkClient
import org.apache.avro.file.{ DataFileReader, SeekableByteArrayInput }
import org.apache.avro.generic.{ GenericDatumReader, GenericRecord }

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Future, blocking }

case class StartConsume(topic: String, groupId: String, number: Int)

object StarvingConsumer extends NamedActor {
  override def name: String = "StarvingConsumer"
}

class StarvingConsumer @Inject() (kafkaConsumerFactory: KafkaConsumerFactory,
                                  keyDecoder: Decoder[String],
                                  valueDecoder: Decoder[Array[Byte]],
                                  topicConsumerRepository: TopicConsumerRepository,
                                  zkClient: ZkClient) extends Actor with ActorLogging {
  private[this] val consumers = ListBuffer.empty[ConsumerConnector]

  override def receive = {
    case StartConsume(topic, groupId, number) =>
      val consumer: ConsumerConnector = kafkaConsumerFactory.create(groupId)
      consumers.append(consumer)
      val topicCountMap = Map(topic -> number)
      val streams: List[KafkaStream[String, Array[Byte]]] =
        consumer.createMessageStreams(topicCountMap, keyDecoder, valueDecoder)(topic)
      streams.foreach { (stream: KafkaStream[String, Array[Byte]]) =>
        val child = context.actorOf(Props(classOf[StarvingConsumeWorker], stream))
        child ! DoConsume
      }
  }

  override def preStart(): Unit = {
    ZkUtils.getAllTopics(zkClient).sorted.foreach { topic =>
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
class StarvingConsumeWorker(kafkaStream: KafkaStream[String, Array[Byte]]) extends Actor with ActorLogging {
  val iterator: ConsumerIterator[String, Array[Byte]] = kafkaStream.iterator()

  override def receive = {
    case DoConsume =>
      import context.dispatcher
      Future {
        blocking {
          HasNext(iterator.hasNext())
        }
      } pipeTo self
    case HasNext(result) =>
      if (result) {
        val msg: Array[Byte] = iterator.next().message()
        val datumReader = new GenericDatumReader[GenericRecord](DirtyEventProcessor.schema)
        val dataReader = new DataFileReader[GenericRecord](new SeekableByteArrayInput(msg), datumReader)
        log.info(s"#### ${self.path} Consumed: " + dataReader.next().toString)
        self ! DoConsume
      } else {
        context.stop(self)
      }
  }
}