package testclient.server

import io.vertx.core.Vertx.vertx
import io.vertx.core.buffer.Buffer
import io.vertx.lang.scala.*
import io.vertx.lang.scala.ImplicitConversions.vertxFutureToScalaFuture
import io.vertx.scala.core.DeploymentOptions
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
  MessageSpecV1,
  ModifiersSpec,
  NipopowProofData,
  NipopowProofSpec,
  RequestModifierSpec
}
import org.ergoplatform.nodeView.history.ErgoSyncInfoMessageSpec
import org.ergoplatform.settings.ChainSettings
import scodec.*
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs.*
import scorex.util.Extensions.LongOps
import scorex.util.ModifierId
import scorex.util.serialization.VLQReader
import testclient.*

import java.util.concurrent.atomic.AtomicReference
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, blocking}
import scala.language.implicitConversions
import scala.util.Try
import testclient.utils.Utils.*
import testclient.message.Message

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

case class InvDataTest(
    networkObject: Byte,
    count: ByteVector,
    rest: ByteVector
)

val invDataCodec: Codec[InvDataTest] = (
  byte
    :: bytes(8)
    :: bytes
).as[InvDataTest]

// special algo for long in invData message
private def getZigZagLong(buf: Array[Byte]): Long =
  var result: Long = 0
  var shift        = 0
  var i            = 0
  while (shift < 64)
    val b = buf(i)
    result = result | ((b & 0x7f).toLong << shift)
    if ((b & 0x80) == 0) return result
    shift += 7
    i += 1
  result

lazy val nipopowAlgos: NipopowAlgos = new NipopowAlgos(chainSettings)

/** Binary serializer for NiPoPoW proofs
  */
lazy val nipopowSerializer = new NipopowProofSerializer(nipopowAlgos)

case class AppVerticle(
    addr: Address,
    nipopow: Boolean,
    state: AtomicReference[NodeState],
    modifierMempool: ConcurrentLinkedQueue[ModifierId]
) extends ScalaVerticle:

  private def messageHandler(
      msg: Array[Byte],
      code: Int
  ): Either[String, InvData] = code.match

    case ModifiersSpec.messageCode =>
      val data = ModifiersSpec.parseBytes(msg)
      val blockData = data.modifiers.map((id, bytes) =>
        (id, HeaderSerializer.parseBytes(bytes))
      )
      Left(
        s"Got Modifiers with Id ${data.typeId} and Headers ${blockData.head._2}"
      )

    case ErgoSyncInfoMessageSpec.messageCode =>
      Left("Got Sync message")

    case InvSpec.messageCode =>
      state.set(NodeState.Syncing)
      val data = InvSpec.parseBytes(msg)
      modifierMempool.addAll(data.ids.asJava) // record added
      println(s"Received ModId ${NetworkObjectTypeId
          .fromByte(data.typeId)} with Txs of size ${data.ids.length}")
      Right(data)

    case NipopowProofSpec.messageCode =>
      val data = NipopowProofSpec.parseBytes(msg)
      val proofData = nipopowSerializer.parseBytes(data)
      Left(
        s"Got NipopowProof with validity ${proofData.isValid}"
      )
    case _ => Left("Unknown Message")

  override def asyncStart: Future[Unit] =
    val client = vertx.createNetClient()

    (client
      .connect(addr.port, addr.url)
      .map: sc =>
        sc.handler: buf =>
          state.get() match

            case NodeState.Running =>
              val res = HandshakeSerializer.parseBytesTry(buf.getBytes).toString
              // db.put("lastHandshake", res)
              println(s"Got handshake $res")
              println("Sending handshake!")
              sc.write(
                Buffer.buffer(utils.Utils.handshakeMessageSerializedToBytes)
              )
              state.set(NodeState.Handshaking)

            case _ =>
              val msg = Message.messageCodec.decode(BitVector(buf.getBytes))
              msg.map: x =>
                println(s"Received a message of ${x.value.msgType}")
                messageHandler(x.value.data.toArray, x.value.msgType) match
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
