package testclient.server

import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetSocket
import io.vertx.lang.scala.*
import io.vertx.lang.scala.ImplicitConversions.vertxFutureToScalaFuture
import io.vertx.scala.core.NetClientOptions
import org.ergoplatform.modifiers.NetworkObjectTypeId
import org.ergoplatform.modifiers.history.header.HeaderSerializer
import org.ergoplatform.modifiers.history.popow.{
  NipopowAlgos,
  NipopowProofSerializer
}
import org.ergoplatform.network.HandshakeSerializer
import org.ergoplatform.network.message.{
  GetNipopowProofSpec,
  InvData,
  InvSpec,
  ModifiersSpec,
  NipopowProofData,
  NipopowProofSpec,
  RequestModifierSpec
}
import org.ergoplatform.nodeView.history.ErgoSyncInfoMessageSpec
import scodec.*
import scodec.bits.BitVector
import scorex.util.ModifierId
import testclient.*

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.language.implicitConversions
import testclient.utils.Utils.*
import testclient.message.Message

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

lazy val nipopowAlgos: NipopowAlgos = new NipopowAlgos(chainSettings)
/** Binary serializer for NiPoPoW proofs
  */
lazy val nipopowSerializer = new NipopowProofSerializer(nipopowAlgos)

val messageType = (b: Byte) =>
  b match
    case ModifiersSpec.messageCode           => "Modifiers"
    case InvSpec.messageCode                 => "InvData"
    case NipopowProofSpec.messageCode        => "NipopowProof"
    case ErgoSyncInfoMessageSpec.messageCode => "SyncMessage"
    case _                                   => s"Unknown message $b"

case class AppVerticle(
    addr: Address,
    nipopow: Boolean,
    state: AtomicReference[NodeState],
    modifierMempool: ConcurrentLinkedQueue[ModifierId]
) extends ScalaVerticle:

  private def messageHandler(
      msg: Message,
      buffer: Array[Byte]
  ): Either[String, InvData] =
    println(
      s"[MSG] ${messageType(msg.msgType)} : size ${msg.size} | size(calc) ${msg.data.length} | size(buffer) ${buffer.length}"
    )
    msg.msgType.match

      case ModifiersSpec.messageCode =>
        val data = ModifiersSpec.parseBytes(msg.data.toArray)
        val blockData = data.modifiers.map((id, bytes) =>
          (id, HeaderSerializer.parseBytes(bytes))
        )
        Left(
          s"Got Modifiers with Id ${data.typeId} and Headers ${blockData.head._2}"
        )

      case ErgoSyncInfoMessageSpec.messageCode =>
        Left("Got Sync message")

      case InvSpec.messageCode =>
        val data = InvSpec.parseBytes(msg.data.toArray)
        modifierMempool.addAll(data.ids.asJava) // record added
        println(s"Received ModId ${NetworkObjectTypeId
            .fromByte(data.typeId)} with Txs of size ${data.ids.length}")
        Right(data)

      case NipopowProofSpec.messageCode =>
        val data      = NipopowProofSpec.parseBytes(msg.data.toArray)
        val proofData = nipopowSerializer.parseBytes(data)
        Left(s"Got NipopowProof with validity ${proofData.isValid}")

      case _ => Left("Unknown Message")

  override def asyncStart: Future[Unit] =
    val client = vertx.createNetClient(
      new NetClientOptions()
        .setReceiveBufferSize(Int.MaxValue)
        .setSendBufferSize(Int.MaxValue)
    )

    (client
      .connect(addr.port, addr.url)
      .map: (sc: NetSocket) =>
        sc.handler: buf =>
          state.get() match

            case NodeState.Running =>
              val res = HandshakeSerializer.parseBytesTry(buf.getBytes).toString
              println(s"Got handshake $res")
              println("Sending handshake!")
              sc.write(
                Buffer.buffer(utils.Utils.handshakeMessageSerializedToBytes)
              )
              state.set(NodeState.Handshaking)

            case _ =>
              val msg = Message.messageCodec.decode(BitVector(buf.getBytes))
              msg.map: x =>
                println(s"[MSG TYPE]: ${x.value.msgType}")
                messageHandler(x.value, buf.getBytes) match
                  case Left(str) =>
                    println(s"Message ${x.value.msgType} result ${str}")
                  case Right(invData) =>
                    sc.write(
                      Buffer.buffer(
                        Message.serializeNodeMessage(
                          RequestModifierSpec,
                          InvSpec.toBytes(invData)
                        )
                      )
                    )

              state.get() match
                case NodeState.Handshaking if !nipopow =>
                  println("Sending Sync message")
                  sc.write(
                    Buffer.buffer(
                      Message.serializeNodeMessage(
                        ErgoSyncInfoMessageSpec,
                        utils.Utils.syncMessageSerializedToBytes
                      )
                    )
                  )
                case NodeState.Handshaking if nipopow =>
                  println("Sending NipopowSync message")
                  sc.write(
                    Buffer.buffer(
                      Message.serializeNodeMessage(
                        GetNipopowProofSpec,
                        GetNipopowProofSpec
                          .toBytes(NipopowProofData(6, 10, None))
                      )
                    )
                  )
                case _ => println("No sync")
      )
      .mapEmpty()
