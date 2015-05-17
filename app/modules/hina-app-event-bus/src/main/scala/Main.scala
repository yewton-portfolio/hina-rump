import akka.actor.{ Props, Actor, ActorSystem }
import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.scala.dsl.builder.ScalaRouteBuilder
import akka.camel.{ CamelExtension, CamelMessage, Consumer, Producer }

import scala.collection.immutable.TreeMap

object Main extends App {
  println("Hello, World!")

  TreeMap(sys.props.toSeq: _*).foreach {
    case (key: String, value: String) =>
      println(s"$key = $value")
  }

  val system = ActorSystem("test")
  val camel = CamelExtension(system)
  val cc = camel.context
  val consumer = system.actorOf(Props[HttpConsumer])
}

class HttpConsumer extends Consumer {
  override def endpointUri = "jetty://http://localhost:8875"

  override def receive = {
    case msg: CamelMessage =>
      val response = Option(msg.body) match {
        case Some(body) => body.asInstanceOf[String]
        case None       => "nothing"
      }
      val additionals = msg.headers.toString()
      sender() ! response + additionals
  }
}

class HttpProducer extends Actor with Producer {
  override def endpointUri: String = ""
}
