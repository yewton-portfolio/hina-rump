package hina

import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorRef }
import akka.camel.{ CamelExtension, CamelMessage, Consumer }
import org.apache.camel.builder.Builder
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

    override def onRouteDefinition = (rd) => rd.onException(classOf[Exception]).
      handled(true).transform(Builder.exceptionMessage()).end()

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      sender() ! Failure(reason)
    }
  }

  trait RestProcessor extends Actor {
    implicit val camelContext: DefaultCamelContext = CamelExtension(context.system).context
  }
}
