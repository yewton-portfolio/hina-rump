package hina.app.modules

import java.util.concurrent.Executors

import com.google.inject.Provider

import scala.concurrent.ExecutionContext

/**
 *
 */
object Providers {
  final val ioExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  object ZkExecutionContextProvider {
    final val name = "ZkExecutionContext"
  }

  class ZkExecutionContextProvider() extends Provider[ExecutionContext] {
    override def get(): ExecutionContext = ioExecutionContext
  }
}
