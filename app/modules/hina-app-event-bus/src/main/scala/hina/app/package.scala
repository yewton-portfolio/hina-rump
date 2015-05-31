package hina

import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorRef }
import akka.camel.{ CamelExtension, CamelMessage, Consumer }
import org.apache.camel.builder.Builder
import org.apache.camel.impl.DefaultCamelContext

import scala.concurrent.duration._
import scala.language.postfixOps

package object app {
  object RestConsumer {
    final val poolName = "RestConsumerPool"
  }

  trait RestConsumer extends Consumer {
    override def replyTimeout = 500 millis

    val processor: ActorRef
    override def receive = {
      case msg: CamelMessage => processor forward msg
    }

    override def onRouteDefinition = (rd) => rd.onException(classOf[Exception])
      .maximumRedeliveries(5)
      .handled(true)
      .transform(Builder.exceptionMessage())
      .end()

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      sender() ! Failure(reason)
    }
  }

  trait RestProcessor extends Actor {
    implicit val camelContext: DefaultCamelContext = CamelExtension(context.system).context
  }
}
