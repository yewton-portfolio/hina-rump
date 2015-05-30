package hina.app.admin

import akka.actor.ActorRef
import akka.camel.CamelMessage
import com.google.inject.Inject
import com.google.inject.name.Named
import hina.app.{ RestConsumer, RestProcessor }
import hina.domain.publisher.PublisherRepository
import hina.util.akka.NamedActor
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.camel.Exchange

object PublisherManager extends NamedActor {
  override final val name = "PublisherManager"

  object Forwarder extends NamedActor {
    final val endpointUri = "seda:publisher-manager"
    override final val name = "PublisherMangerForwarder"
  }

  class Forwarder @Inject() (@Named(PublisherManager.name) override val processor: ActorRef) extends RestConsumer {
    override def endpointUri: String = Forwarder.endpointUri
  }
}

class PublisherManager @Inject() (val repo: PublisherRepository) extends RestProcessor {
  override def receive = {
    case msg: CamelMessage =>
      sender() ! CamelMessage("I am " + self.path, Map(
        Exchange.HTTP_RESPONSE_CODE -> HttpResponseStatus.OK.code()))
  }
}
