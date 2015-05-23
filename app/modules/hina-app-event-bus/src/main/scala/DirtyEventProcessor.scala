import java.io.ByteArrayOutputStream
import java.util

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.camel.CamelMessage
import akka.pattern.pipe
import akka.routing.{Pool, RoundRobinPool}
import com.google.inject.name.{Named, Names}
import com.google.inject.{AbstractModule, Inject, Provides}
import com.typesafe.config.Config
import io.netty.handler.codec.http.HttpResponseStatus
import net.codingwell.scalaguice.ScalaModule
import org.apache.avro.Schema
import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.{GenericData, GenericDatumWriter, GenericRecord}
import org.apache.camel.Exchange
import org.apache.kafka.clients.producer._

import scala.beans.BeanProperty
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.Try

case class DirtyEventRequest(name: String, publisherId: String, body: String, exchangeId: String)
case class DirtyEventBadRequest(e: Throwable)
case class DirtyEventResponse(@BeanProperty name: String, @BeanProperty exchangeId: String)
case class DirtyEventErrorResponse(@BeanProperty code: String, @BeanProperty detail: String)

class DirtyEventModule extends AbstractModule with ScalaModule with GuiceAkkaActorRefProvider {
  override def configure(): Unit = {
    bind[Actor].annotatedWith(Names.named(DirtyEventProcessor.name)).to[DirtyEventProcessor]
  }

  @Provides
  @Named("dirty-events-pool")
  @Inject
  def provideDirtyEventPool(system: ActorSystem): Pool = RoundRobinPool(10)

  @Provides
  @Named(DirtyEventProcessor.name)
  @Inject
  def provideDirtyEventProcessorRef(system: ActorSystem,
                                    @Named("dirty-events-pool") pool: Pool): ActorRef =
    providePoolRef(system, DirtyEventProcessor.name, pool)
}

object DirtyEventProcessor extends NamedActor {
  override final val name = "DirtyEventProcessor"
  private[this] val schemaString =
    """
      |{"namespace": "net.yewton.hina",
      | "type": "record",
      | "name": "DirtyTopicRecord",
      | "fields": [
      |     {"name": "publisher_id", "type": "string"},
      |     {"name": "accepted_at_millis", "type": "long"},
      |     {"name": "body", "type": "string"},
      |     {"name": "exchange_id", "type": "string"}
      | ]
      |}
    """.stripMargin
  lazy val schema: Schema = new Schema.Parser().parse(schemaString)
}

class DirtyEventProcessor @Inject() (val config: Config,
                                     val repository: PublisherTopicRepository,
                                     val producer: Producer[String, Array[Byte]],
                                     @Named("KafkaIO") val executionContext: ExecutionContext) extends Actor {
  private[this] val producerConfigs: util.Map[String, Object] = config.getConfig("kafka.producer").root().unwrapped()
  producerConfigs.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  producerConfigs.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")

  private[this] implicit val ec = executionContext

  def receive = {
    case DirtyEventRequest(name, publisherId, body, exchangeId) =>
      val schema = DirtyEventProcessor.schema
      val rec: GenericRecord = new GenericData.Record(schema)
      rec.put("publisher_id", publisherId)
      rec.put("accepted_at_millis", org.joda.time.DateTime.now().getMillis)
      rec.put("body", body)
      rec.put("exchange_id", exchangeId)

      val datumWriter = new GenericDatumWriter[GenericRecord](schema)
      val dataWriter = new DataFileWriter[GenericRecord](datumWriter)
      val baos = new ByteArrayOutputStream()
      dataWriter.create(schema, baos)
      dataWriter.append(rec)
      dataWriter.close()

      val record: ProducerRecord[String, Array[Byte]] = new ProducerRecord(name, baos.toByteArray)
      val promise = Promise[CamelMessage]()
      producer.send(record, new Callback {
        override def onCompletion(data: RecordMetadata, e: Exception): Unit = {
          val result: Try[CamelMessage] = Option(e).map(scala.util.Failure(_)).getOrElse {
            val response = DirtyEventResponse(name, exchangeId + ":" + context.self.path.toString)
            val message = CamelMessage(response, Map(
              Exchange.HTTP_RESPONSE_CODE -> HttpResponseStatus.ACCEPTED.code()))
            scala.util.Success(message)
          }
          promise.complete(result)
        }
      })
      promise.future pipeTo sender()
    case DirtyEventBadRequest(e) =>
      val response = DirtyEventErrorResponse("error", e.getMessage)
      sender() ! CamelMessage(response, Map(
        Exchange.HTTP_RESPONSE_CODE -> HttpResponseStatus.BAD_REQUEST.code()
      ))
  }
}