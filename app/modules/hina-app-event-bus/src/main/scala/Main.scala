import akka.actor.Status.Failure
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.camel._
import com.google.inject._
import com.google.inject.name.{Named, Names}
import com.typesafe.config.Config
import net.codingwell.scalaguice.InjectorExtensions._
import net.codingwell.scalaguice.ScalaModule
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestBindingMode

import scala.beans.BeanProperty
import scala.concurrent.duration._

object Main extends App {

  val injector = Guice.createInjector(
    new ConfigModule(),
    new AkkaModule(),
    new SampleModule(),
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
      .component("jetty")
      .host("localhost")
      .port(8875)
      .dataFormatProperty("prettyPrint", "true")
      .bindingMode(RestBindingMode.json)

    rest("/v1/topics/")
      .consumes("application/json")
      .produces("application/json")

      .post("/{name}/events").to("direct:dirty-topic")
  }
}

class SampleModule extends AbstractModule with ScalaModule {
  override def configure() = {
    bind[Actor].annotatedWith(Names.named(DirtyTopicConsumer.name)).to[DirtyTopicConsumer]
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

  override def receive = {
    case msg: CamelMessage =>
      Option(msg.body) match {
        case None =>
          throw new RuntimeException("Body cannot be null")
        case Some(_) =>
          val name = msg.headerAs[String]("name").getOrElse(throw new RuntimeException("Name must be specified"))
          val body = msg.bodyAs[String]
          processor forward DirtyTopicRequest(name, body)
      }
  }

  final override def preRestart(reason: Throwable, message: Option[Any]) = {
    sender() ! Failure(reason)
  }
}

case class DirtyTopicRequest(name: String, body: String)
case class DirtyTopicResponse(@BeanProperty status: String)

class DirtyTopicModule extends AbstractModule with ScalaModule with GuiceAkkaActorRefProvider {
  override def configure(): Unit = {
    bind[Actor].annotatedWith(Names.named(DirtyTopicProcessor.name)).to[DirtyTopicProcessor]
  }

  @Provides
  @Named(DirtyTopicProcessor.name)
  def provideDirtyTopicProcessorRef(@Inject() system: ActorSystem): ActorRef = provideActorRef(system, DirtyTopicProcessor.name)
}

object DirtyTopicProcessor extends NamedActor {
  override final val name = "DirtyTopicProcessor"
}

class DirtyTopicProcessor extends Actor {
  def receive = {
    case DirtyTopicRequest(name, body) =>
      val response = DirtyTopicResponse("accepted")
      sender() ! CamelMessage(response, Map(
        Exchange.HTTP_RESPONSE_CODE -> 202
      ))
  }
}
