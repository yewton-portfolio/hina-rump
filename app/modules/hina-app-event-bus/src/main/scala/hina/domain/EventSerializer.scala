package hina.domain

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

import org.apache.avro.generic.{ GenericData, GenericDatumWriter, GenericRecord }
import org.apache.avro.io.{ BinaryEncoder, EncoderFactory }
import org.apache.kafka.common.serialization.Serializer

class EventSerializer extends Serializer[Event] {
  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()

  override def serialize(topic: String, data: Event): Array[Byte] = Option(Event).map { event =>
    val schema = Event.schema
    val rec: GenericRecord = new GenericData.Record(schema)
    rec.put("exchange_id", data.exchangeId)
    rec.put("publisher", data.publisher)
    rec.put("content_type", data.contentType)
    rec.put("created_at_millis", data.acceptedAt.getMillis)
    rec.put("body", ByteBuffer.wrap(data.body))

    val datumWriter = new GenericDatumWriter[GenericRecord](schema)
    val out = new ByteArrayOutputStream()
    val encoder: BinaryEncoder = EncoderFactory.get().directBinaryEncoder(out, null)
    datumWriter.write(rec, encoder)
    out.flush()
    out.toByteArray
  }.orNull

  override def close(): Unit = ()
}
