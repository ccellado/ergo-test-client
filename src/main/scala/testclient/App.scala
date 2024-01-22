package testclient

import scorex.util.ModifierId
import testclient.server.AppVerticle
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentLinkedQueue
import io.vertx.core.Vertx.vertx
import io.vertx.lang.scala.*
import scala.language.implicitConversions
import testclient.utils.Utils.*

// This code is useless documentation purposes only
@main def App(): Unit =

  given ec: VertxExecutionContext =
    VertxExecutionContext(vertx, vertx.getOrCreateContext())

  val state: AtomicReference[NodeState] = AtomicReference[NodeState]
  state.set(NodeState.Running)

  val modifierMempool: ConcurrentLinkedQueue[ModifierId] = ConcurrentLinkedQueue[ModifierId]()

  println(s"${state.get()}: Ergo Test Client")

  vertx.deployVerticle(
      AppVerticle(
        testPeers.head,
        nipopow = false,
        state,
        modifierMempool
      )
    )
