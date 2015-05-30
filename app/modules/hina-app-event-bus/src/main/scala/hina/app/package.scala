package hina

import akka.actor.{ Actor, ActorRef }
import akka.camel.{ CamelExtension, CamelMessage, Consumer }
import org.apache.camel.impl.DefaultCamelContext

package object app {
  object RestConsumer {
    final val poolName = "RestConsumerPool"
  }
  trait RestConsumer extends Consumer {
    val processor: ActorRef
    override def receive = {
      case msg: CamelMessage => processor forward msg
    }
  }

  trait RestProcessor extends Actor {
    implicit val camelContext: DefaultCamelContext = CamelExtension(context.system).context
  }
}
