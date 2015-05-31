package hina.domain

import org.apache.avro.Schema
import org.joda.time.DateTime

object Event {
  private[this] final val schemaString =
    """
      |{"namespace": "net.yewton.hina",
      | "type": "record",
      | "name": "EventRecord",
      | "fields": [
      |     {"name": "exchange_id", "type": "string"},
      |     {"name": "publisher", "type": "string"},
      |     {"name": "content_type", "type": "string"},
      |     {"name": "created_at_millis", "type": "long"},
      |     {"name": "body", "type": "bytes"}
      | ]
      |}
    """.stripMargin
  final val schema = new Schema.Parser().parse(schemaString)
}

case class Event(exchangeId: String,
                 publisher: String,
                 contentType: String,
                 acceptedAt: DateTime,
                 body: Array[Byte])
