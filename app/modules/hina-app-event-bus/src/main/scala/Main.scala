import akka.actor.Status.Failure
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.camel._
import com.google.inject._
import com.google.inject.name.{Named, Names}
import com.typesafe.config.Config
import io.netty.handler.codec.http.HttpResponseStatus
import kafka.utils.ZKStringSerializer
import net.codingwell.scalaguice.InjectorExtensions._
import net.codingwell.scalaguice.ScalaModule
import org.I0Itec.zkclient.ZkClient
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestBindingMode

import scala.beans.BeanProperty
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object Main extends App {
  val injector = Guice.createInjector(
    new ConfigModule(),
    new AkkaModule(),
    new MyModule(),
    new DirtyEventModule(),
    new TopicCreatorModule(),
    new KafkaModule()
  )

  val system = injector.instance[ActorSystem]
  val camel = CamelExtension(system)
  camel.context.addRoutes(new MyRouteBuilder)
  system.actorOf(GuiceAkkaExtension(system).props(DirtyEventForwarder.name))
  system.actorOf(GuiceAkkaExtension(system).props(TopicCreator.name))
  system.actorOf(GuiceAkkaExtension(system).props(StarvingConsumer.name))

  sys.addShutdownHook {
    system.shutdown()
    system.awaitTermination(10.seconds)
  }
}

class MyRouteBuilder() extends RouteBuilder {
  case class ErrorResponse(@BeanProperty message: String, @BeanProperty detail: String)

  override def configure(): Unit = {
    configureRoutes(getContext).onException(classOf[Exception])
      .handled(true)
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()))
      //.setHeader(Exchange.CONTENT_TYPE, Builder.constant("application/json"))
      .setBody().constant(ErrorResponse("Internal Server Error", exceptionMessage().toString))
      .end()

    restConfiguration()
      .component("netty4-http")
      .host("localhost")
      .port(8875)
      .skipBindingOnErrorCode(false)
      .dataFormatProperty("prettyPrint", "true")
      .bindingMode(RestBindingMode.json)

    rest("/v1/topics/")

      .post("/{name}/events")
      .consumes("application/json")
      .produces("application/json")
      .to("seda:dirty-event")

      .put("/{topic}")
      .produces("application/json")
      .to("seda:create-topic")
  }
}

class MyModule extends AbstractModule with ScalaModule {
  override def configure() = {
    bind[Actor].annotatedWith(Names.named(DirtyEventForwarder.name)).to[DirtyEventForwarder]
    bind[Actor].annotatedWith(Names.named(StarvingConsumer.name)).to[StarvingConsumer]
    bind[PublisherTopicRepository].to[PublisherTopicRepositoryOnMemory]
    bind[TopicConsumerRepository].to[TopicConsumerRepositoryOnMemory]
  }

  @Provides
  @Inject
  def provideZkClient(config: Config): ZkClient = {
    val zkConnect = config.getString("zookeeper.connect")
    new ZkClient(zkConnect, 30000, 30000, ZKStringSerializer)
  }

  @Provides
  @Named("ZkIO")
  def provideZkIOExecutionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  @Provides
  @Named("KafkaIO")
  def provideKafkaIOExecutionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
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

object DirtyEventForwarder extends NamedActor {
  override final val name = "DirtyEventForwarder"
  final val endpointUri = "seda:dirty-event"
}

class DirtyEventForwarder @Inject() (@Named(DirtyEventProcessor.name) processor: ActorRef) extends Consumer {

  override def replyTimeout = 5000 millis

  override def endpointUri = DirtyEventForwarder.endpointUri

  override def receive = {
    case msg: CamelMessage =>
      val t = for {
        name <- msg.headerAs[String]("name")
        publisherId <- msg.headerAs[String]("X-HINA-PUBLISHER-NAME")
        exchangeId <- msg.headerAs[String](CamelMessage.MessageExchangeId)
      } yield {
        DirtyEventRequest(name, publisherId, msg.bodyAs[String], exchangeId)
      }
      t match {
        case scala.util.Success(req) => processor forward req
        case scala.util.Failure(e)   => processor forward DirtyEventBadRequest(e)
      }
  }

  final override def preRestart(reason: Throwable, message: Option[Any]) = {
    sender() ! Failure(reason)
  }
}
