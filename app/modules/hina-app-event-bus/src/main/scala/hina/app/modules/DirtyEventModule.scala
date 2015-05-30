package hina.app.modules

import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.routing.{ DefaultResizer, RoundRobinPool }
import com.google.inject.name.{ Named, Names }
import com.google.inject.{ AbstractModule, Inject, Provides }
import hina.app.publisher.DirtyEventProcessor
import hina.util.akka.GuiceAkkaActorRefProvider
import net.codingwell.scalaguice.ScalaModule

/**
 *
 */
class DirtyEventModule extends AbstractModule with ScalaModule with GuiceAkkaActorRefProvider {
  override def configure(): Unit = {
    bind[Actor].annotatedWith(Names.named(DirtyEventProcessor.name)).to[DirtyEventProcessor]
  }

  @Provides
  @Named(DirtyEventProcessor.name)
  @Inject
  def provideDirtyEventProcessorRef(system: ActorSystem): ActorRef = {
    val resizer = DefaultResizer(lowerBound = 2, upperBound = 10)
    provideActorRef(system, DirtyEventProcessor.name, RoundRobinPool(10, Some(resizer)))
  }
}
