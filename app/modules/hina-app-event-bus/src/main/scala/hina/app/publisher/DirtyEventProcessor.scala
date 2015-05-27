package hina.app.publisher

import java.io.ByteArrayOutputStream
import java.util

import akka.actor.Actor
import akka.camel.CamelMessage
import akka.pattern.pipe
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.Inject
import com.google.inject.name.Named
import com.typesafe.config.Config
import hina.app.modules.Providers.ZkExecutionContextProvider
import hina.domain.PublisherTopicRepository
import hina.util.akka.NamedActor
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.avro.Schema
import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.{GenericData, GenericDatumWriter, GenericRecord}
import org.apache.camel.Exchange
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.errors.TimeoutException

import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.concurrent.{ExecutionContext, Promise}

case class DirtyEventRequest(name: String, publisherId: String, body: String, exchangeId: String)
case class DirtyEventBadRequest(e: Throwable)
case class DirtyEventResponse(@BeanProperty name: String, @(JsonProperty @field)("exchange_id") exchangeId: String)
case class DirtyEventErrorResponse(@BeanProperty code: String, @BeanProperty detail: String)

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
                                     @Named(ZkExecutionContextProvider.name) val executionContext: ExecutionContext) extends Actor {
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
          val result: CamelMessage = Option(e).map {
            case e: TimeoutException =>
              val response = DirtyEventErrorResponse("timeout-error", e.getMessage)
              CamelMessage(response, Map(
                Exchange.HTTP_RESPONSE_CODE -> HttpResponseStatus.REQUEST_TIMEOUT.code()))
            case _ =>
              val response = DirtyEventErrorResponse("error", e.getMessage)
              CamelMessage(response, Map(
                Exchange.HTTP_RESPONSE_CODE -> HttpResponseStatus.INTERNAL_SERVER_ERROR.code()))
          }.getOrElse {
            val response = DirtyEventResponse(name, exchangeId)
            CamelMessage(response, Map(
              Exchange.HTTP_RESPONSE_CODE -> HttpResponseStatus.ACCEPTED.code()))
          }
          promise.success(result)
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