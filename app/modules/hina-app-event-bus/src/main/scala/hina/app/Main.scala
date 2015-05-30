package hina.app

import akka.actor.ActorSystem
import akka.camel._
import com.google.inject._
import hina.app.admin.{ PublisherManager, StarvingConsumer, TopicCreator }
import hina.app.modules._
import hina.app.publisher.DirtyEventForwarder
import hina.util.akka.GuiceAkkaExtension
import net.codingwell.scalaguice.InjectorExtensions._

import scala.concurrent.duration._
import scala.language.postfixOps

object Main extends App {
  val injector = Guice.createInjector(
    new ConfigModule(),
    new AkkaModule(),
    new MainModule(),
    new DirtyEventModule(),
    new KafkaModule()
  )

  val system = injector.instance[ActorSystem]
  val camel = CamelExtension(system)
  camel.context.addRoutes(new MainRouteBuilder)
  system.actorOf(GuiceAkkaExtension(system).props(DirtyEventForwarder.name))
  system.actorOf(GuiceAkkaExtension(system).props(TopicCreator.Forwarder.name))
  system.actorOf(GuiceAkkaExtension(system).props(PublisherManager.Forwarder.name))
  system.actorOf(GuiceAkkaExtension(system).props(StarvingConsumer.name))

  sys.addShutdownHook {
    system.shutdown()
    system.awaitTermination(10.seconds)
  }
}
