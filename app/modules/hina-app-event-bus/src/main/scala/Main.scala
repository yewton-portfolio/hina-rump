import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.camel._
import com.google.inject._
import com.google.inject.name.{ Named, Names }
import com.typesafe.config.Config
import io.netty.handler.codec.http.HttpResponseStatus
import net.codingwell.scalaguice.InjectorExtensions._
import net.codingwell.scalaguice.ScalaModule
import org.apache.camel.Exchange
import org.apache.camel.builder.{ Builder, RouteBuilder }
import org.apache.camel.model.rest.RestBindingMode

import scala.beans.BeanProperty
import scala.concurrent.duration._
import scala.language.postfixOps

object Main extends App {

  val injector = Guice.createInjector(
    new ConfigModule(),
    new AkkaModule(),
    new MyModule(),
    new DirtyTopicModule()
  )

  val system = injector.instance[ActorSystem]
  val camel = CamelExtension(system)
  camel.context.addRoutes(new MyRouteBuilder)
  val consumer = system.actorOf(GuiceAkkaExtension(system).props(DirtyTopicConsumer.name))
}

class MyRouteBuilder() extends RouteBuilder {
  override def configure(): Unit = {
    restConfiguration()
      .component("netty4-http")
      .host("localhost")
      .port(8875)
      .skipBindingOnErrorCode(false)
      .dataFormatProperty("prettyPrint", "true")
      .bindingMode(RestBindingMode.json)

    rest("/v1/topics/")
      .consumes("application/json")
      .produces("application/json")

      .post("/{name}/events").to("direct:dirty-topic")

    constant("")
  }
}

class MyModule extends AbstractModule with ScalaModule {
  override def configure() = {
    bind[Actor].annotatedWith(Names.named(DirtyTopicConsumer.name)).to[DirtyTopicConsumer]
    bind[PublisherTopicRepository].to[PublisherTopicRepositoryOnMemory]
  }
}

object AkkaModule {
  class ActorSystemProvider @Inject() (val config: Config, val injector: Injector) extends Provider[ActorSystem] {
    override def get: ActorSystem = {
      val system = ActorSystem("main-actor-system", config)
      GuiceAkkaExtension(system).initialize(injector)
      system
    }
  }
}

/**
 * A module providing an Akka ActorSystem.
 */
class AkkaModule extends AbstractModule with ScalaModule {
  override def configure() {
    bind[ActorSystem].toProvider[AkkaModule.ActorSystemProvider].asEagerSingleton()
  }
}

object DirtyTopicConsumer extends NamedActor {
  override final val name = "DirtyTopicConsumer"
}

class DirtyTopicConsumer @Inject() (@Named(DirtyTopicProcessor.name) processor: ActorRef) extends Consumer {

  override def replyTimeout = 500 millis

  override def endpointUri = "direct:dirty-topic"

  case class ErrorResponse(@BeanProperty message: String, @BeanProperty detail: String)

  override def onRouteDefinition = (rd) => rd.onException(classOf[Exception])
    .handled(true)
    .setHeader(Exchange.HTTP_RESPONSE_CODE, Builder.constant(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()))
    .setHeader(Exchange.CONTENT_TYPE, Builder.constant("application/json"))
    .setBody().constant(ErrorResponse("Internal Server Error", Builder.exceptionMessage().toString))
    .end()

  override def receive = {
    case msg: CamelMessage =>
      val t = for {
        name <- msg.headerAs[String]("name")
        publisherId <- msg.headerAs[String]("X-HINA-PUBLISHER-NAME")
      } yield {
        DirtyTopicRequest(name, publisherId, msg.bodyAs[String])
      }
      t match {
        case scala.util.Success(req) => processor forward req
        case scala.util.Failure(e)   => processor forward DirtyTopicBadRequest(e)
      }
  }

  final override def preRestart(reason: Throwable, message: Option[Any]) = {
    sender() ! Failure(reason)
  }
}
