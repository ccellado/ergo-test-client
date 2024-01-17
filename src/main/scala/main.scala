
import utils.Utils.*


import java.util.concurrent.atomic.AtomicReference
import scala.collection.concurrent.TrieMap

// Vertx
import io.vertx.core.Vertx.vertx
import io.vertx.lang.scala.*

import scala.language.implicitConversions
import server.*

@main def main(): Unit =
  given ec: VertxExecutionContext =
    VertxExecutionContext(vertx, vertx.getOrCreateContext())

  val state: AtomicReference[NodeState] = AtomicReference[NodeState]
  state.set(NodeState.Running)

  val database: TrieMap[String, String] =
    scala.collection.concurrent.TrieMap.empty

  println(s"${state.get()}: Ergo Test Client")

  vertx.deployVerticle(
      AppVerticle(
        testPeers.head,
        state,
        database
      )
    )
