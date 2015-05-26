package hina.app.modules

import akka.actor.ActorSystem
import com.google.inject.{ AbstractModule, Inject, Injector, Provider }
import com.typesafe.config.Config
import hina.util.akka.GuiceAkkaExtension
import net.codingwell.scalaguice.ScalaModule

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
