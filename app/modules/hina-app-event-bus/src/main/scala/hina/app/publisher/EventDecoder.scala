package hina.app.publisher

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

import hina.domain.Event
import kafka.serializer.Decoder
import kafka.utils.VerifiableProperties
import org.apache.avro.generic.{ GenericDatumReader, GenericRecord }
import org.apache.avro.io.DecoderFactory
import org.apache.avro.util.Utf8
import org.joda.time.DateTime

class EventDecoder(props: VerifiableProperties = null) extends Decoder[Event] {
  def fromBytes(msg: Array[Byte]): Event = {
    val datumReader = new GenericDatumReader[GenericRecord](Event.schema)
    val input = new ByteArrayInputStream(msg)
    val decoder = DecoderFactory.get().directBinaryDecoder(input, null)
    val datum: GenericRecord = datumReader.read(null, decoder)

    Event(
      datum.get("exchange_id").asInstanceOf[Utf8].toString,
      datum.get("publisher").asInstanceOf[Utf8].toString,
      datum.get("content_type").asInstanceOf[Utf8].toString,
      new DateTime(datum.get("acceptedAt").asInstanceOf[Long]),
      datum.get("body").asInstanceOf[ByteBuffer].array())
  }
}
