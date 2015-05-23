import java.util
import java.util.concurrent.FutureTask

import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.pattern.pipe
import akka.camel.CamelMessage
import com.google.inject.name.{ Named, Names }
import com.google.inject.{ AbstractModule, Inject, Provides }
import com.typesafe.config.Config
import io.netty.handler.codec.http.HttpResponseStatus
import net.codingwell.scalaguice.ScalaModule
import org.apache.camel.Exchange
import org.apache.kafka.clients.producer.{ RecordMetadata, ProducerRecord, KafkaProducer }

import scala.beans.BeanProperty
import scala.concurrent.Future

case class DirtyTopicRequest(name: String, publisherId: String, body: String)
case class DirtyTopicBadRequest(e: Throwable)
case class DirtyTopicResponse(@BeanProperty name: String, @BeanProperty status: String)
case class DirtyTopicErrorResponse(@BeanProperty code: String, @BeanProperty detail: String)

class DirtyTopicModule extends AbstractModule with ScalaModule with GuiceAkkaActorRefProvider {
  override def configure(): Unit = {
    bind[Actor].annotatedWith(Names.named(DirtyTopicProcessor.name)).to[DirtyTopicProcessor]
  }

  @Provides
  @Named(DirtyTopicProcessor.name)
  @Inject
  def provideDirtyTopicProcessorRef(system: ActorSystem): ActorRef = provideActorRef(system, DirtyTopicProcessor.name)
}

object DirtyTopicProcessor extends NamedActor {
  override final val name = "DirtyTopicProcessor"
}

class DirtyTopicProcessor @Inject() (val config: Config, val repository: PublisherTopicRepository) extends Actor {
  private[this] lazy val producerConfigs: util.Map[String, Object] = config.getConfig("kafka.producer").root().unwrapped()
  producerConfigs.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  producerConfigs.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

  private[this] lazy val producer: KafkaProducer[String, String] = new KafkaProducer(producerConfigs)

  def receive = {
    case DirtyTopicRequest(name, publisherId, body) =>
      // send body to somewhere
      val record: ProducerRecord[String, String] = new ProducerRecord("test-topic", org.joda.time.DateTime.now().getMillis.toString)
      import context.dispatcher
      val f: Future[CamelMessage] = Future(producer.send(record).get()).map { (data: RecordMetadata) =>
        val body = "Topic=%s, Partition=%d, Offset = %d".format(data.topic(), data.partition(), data.offset())
        val response = DirtyTopicResponse(name, body)
        CamelMessage(response, Map(
          Exchange.HTTP_RESPONSE_CODE -> HttpResponseStatus.ACCEPTED.code()
        ))
      }
      f pipeTo sender()
    case DirtyTopicBadRequest(e) =>
      val response = DirtyTopicErrorResponse("error", e.getMessage)
      sender() ! CamelMessage(response, Map(
        Exchange.HTTP_RESPONSE_CODE -> HttpResponseStatus.BAD_REQUEST.code()
      ))
  }
}