import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.camel._
import com.google.inject._
import com.google.inject.name.{ Named, Names }
import com.typesafe.config.Config
import net.codingwell.scalaguice.InjectorExtensions._
import net.codingwell.scalaguice.ScalaModule
import org.apache.camel.Exchange
import org.apache.camel.builder.Builder

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
  val consumer = system.actorOf(GuiceAkkaExtension(system).props(HttpConsumer.name))
}

class SampleModule extends AbstractModule with ScalaModule {
  override def configure() = {
    bind[Actor].annotatedWith(Names.named(HttpConsumer.name)).to[HttpConsumer]
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

object HttpConsumer extends NamedActor {
  override final val name = "HttpConsumer"
}

class HttpConsumer @Inject() (@Named(DirtyTopicProcessor.name) processor: ActorRef) extends Consumer {

  override def replyTimeout = 500 millis

  override def endpointUri = "jetty://http://localhost:8875?httpBindingRef=mybinding"

  override def onRouteDefinition = (rd) => rd
    .onException(classOf[Exception])
    .handled(true)
    .transform(Builder.exceptionMessage())
    .setHeader(Exchange.HTTP_RESPONSE_CODE, Builder.constant(500))
    .end

  override def receive = {
    case msg: CamelMessage =>
      val body = Option(msg.body).map(Function.const(msg.bodyAs[String])).getOrElse("empty body")
      val headers = msg.headers.keysIterator.map { k =>
        k -> msg.headerAs[String](k).getOrElse(msg.headerAs[Any](k).toString)
      }.toMap
      processor forward DirtyTopicRequest(headers, body)
  }

  final override def preRestart(reason: Throwable, message: Option[Any]) = {
    sender() ! Failure(reason)
  }
}

case class DirtyTopicRequest(headers: Map[String, String], body: String)

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
    case DirtyTopicRequest(headers, body) =>
      val headersString = headers.iterator.map {
        case (key, value) =>
          s"$key: $value"
      }.mkString("\n")
      val response =
        s"""
          |Request Headers:
          |$headersString
          |Body:
          |$body
        """.stripMargin

      sender() ! CamelMessage(response, Map(
        Exchange.HTTP_RESPONSE_CODE -> 202
      ))
      context.system.shutdown()
  }
}
