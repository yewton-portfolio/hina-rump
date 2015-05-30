package hina.app.modules

import java.util.concurrent.Executors

import com.google.inject.Provider

import scala.concurrent.ExecutionContext

/**
 *
 */
object Providers {
  private def cpus = Runtime.getRuntime.availableProcessors()
  final val ioExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(cpus * 5))

  object ZkExecutionContextProvider {
    final val name = "ZkExecutionContext"
  }

  class ZkExecutionContextProvider() extends Provider[ExecutionContext] {
    override def get(): ExecutionContext = ioExecutionContext
  }

  object BlockingIOExecutionContextProvider {
    final val name = "BlockingIOExecutionContextProvider"
  }

  class BlockingIOExecutionContextProvider extends Provider[ExecutionContext] {
    override def get(): ExecutionContext = ioExecutionContext
  }
}
