package hina.app

import akka.actor.ActorSystem
import akka.camel._
import com.google.inject._
import hina.app.admin.{ PublisherManager, TopicCreator }
import hina.app.modules._
import hina.app.publisher.{ EventCreatorHttpPost, EventCreatorRabbitMQ }
import hina.app.subscriber.EventConsumer
import hina.util.akka.GuiceAkkaExtension
import net.codingwell.scalaguice.InjectorExtensions._

import scala.concurrent.duration._
import scala.language.postfixOps

object Demo extends App {
  val injector = Guice.createInjector(
    new ConfigModule(),
    new AkkaModule(),
    new MainModule(),
    new KafkaModule()
  )

  val system = injector.instance[ActorSystem]
  val camel = CamelExtension(system)
  camel.context.addRoutes(new MainRouteBuilder)
  system.actorOf(GuiceAkkaExtension(system).props(EventCreatorHttpPost.Forwarder.name))
  system.actorOf(GuiceAkkaExtension(system).props(TopicCreator.Forwarder.name))
  system.actorOf(GuiceAkkaExtension(system).props(PublisherManager.Forwarder.name))
  // system.actorOf(GuiceAkkaExtension(system).props(StarvingConsumer.name))
  system.actorOf(GuiceAkkaExtension(system).props(EventCreatorRabbitMQ.name)) // @todo Pool 化
  system.actorOf(GuiceAkkaExtension(system).props(EventConsumer.name))

  sys.addShutdownHook {
    system.shutdown()
    system.awaitTermination(10.seconds)
  }
}
