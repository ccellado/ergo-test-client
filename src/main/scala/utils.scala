package utils

import io.vertx.core.ThreadingModel
import io.vertx.scala.core.DeploymentOptions
import org.ergoplatform.network.{Handshake, HandshakeSerializer, PeerSpec, Version}
import org.ergoplatform.nodeView.history.{ErgoSyncInfoMessageSpec, ErgoSyncInfoV2}
import scala.concurrent.{ExecutionContext, Future}

object Utils:
  case class Address(url: String, port: Int)

  enum NodeState:
    case Running, Syncing, Handshaking

  val myPeerSpec = PeerSpec(
    agentName = "TestErgoClient",
    protocolVersion = Version("5.0.18"),
    nodeName = "TestNode",
    declaredAddress = None,
    features = Seq.empty
  )

  val magic = Array(2: Byte, 0: Byte, 2: Byte, 3: Byte) // testnet magic
  val handshakeMessage = Handshake(myPeerSpec, System.currentTimeMillis())
  val handshakeMessageSerializedToBytes =
    HandshakeSerializer.toBytes(handshakeMessage)

  val syncMessage = ErgoSyncInfoV2(Seq.empty)
  val syncMessageSerializedToBytes = ErgoSyncInfoMessageSpec.toBytes(syncMessage)

  val depOptions =
    new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)

  val testPeers = Seq(
    Address("0.0.0.0", 9022),
    Address("213.239.193.208", 9022),
    Address("168.138.185.215", 9022),
    Address("192.234.196.165", 9022),
  )

  def await[T](f: Future[T])(using ec: ExecutionContext): T =
    try
      var res: Option[T] = None
      f.onComplete { x =>
        synchronized:
          res = Some(x.get)
          notify()
      }
      synchronized:
        while res.isEmpty do wait()
        res.get
    finally ()