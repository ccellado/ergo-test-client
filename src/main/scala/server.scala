package server

import io.vertx.core.Vertx.vertx
import io.vertx.core.buffer.Buffer
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.lang.scala.ImplicitConversions.vertxFutureToScalaFuture
import io.vertx.lang.scala.*
import io.vertx.scala.core.DeploymentOptions
import org.ergoplatform.network.HandshakeSerializer
import org.ergoplatform.network.message.{InvData, InvSpec, MessageSpecV1}

import scala.collection.concurrent.TrieMap
import scala.language.implicitConversions
import scala.concurrent.Future
import messageSpec.*
import org.ergoplatform.modifiers.NetworkObjectTypeId
import org.ergoplatform.nodeView.history.ErgoSyncInfoMessageSpec
import scodec.bits.BitVector
import utils.Utils.{Address, NodeState}

import java.util.concurrent.atomic.AtomicReference
import scodec.Codec
import scodec.*
import scodec.bits.ByteVector
import scodec.codecs.*
import scorex.util.Extensions.LongOps
import scorex.util.serialization.VLQReader

import scala.util.Try

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

def gerZigZagLong(buf: Array[Byte]): Long =
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

case class AppVerticle(
    addr: Address,
    state: AtomicReference[NodeState],
    db: TrieMap[String, String]
) extends ScalaVerticle:

  private def messageHandler(msg: Array[Byte], code: Int): String = code.match

    case ErgoSyncInfoMessageSpec.messageCode =>
      // db.put("context", "syncing")
      "Got Sync message"

    case InvSpec.messageCode =>
      state.set(NodeState.Syncing)
      val data = invDataCodec.decode(BitVector(msg))
      val res  = data.toOption.get
      val dataPasred = InvSpec.parseBytes(msg)
//        .getOrElse(InvData(NetworkObjectTypeId.fromByte(-1), Seq.empty))
      val zigzagLong = gerZigZagLong(res.value.count.toArray).toIntExact
//      db.put(data.typeId.toString, data.ids.toString())
//      s"Received ModId ${NetworkObjectTypeId.fromByte(data.typeId)} with Txs of size ${data.ids.length}"
      s"\n[CUSTOM DECODER] TypeId ${NetworkObjectTypeId.fromByte(res.value.networkObject)} " +
        s"| DataLength ${zigzagLong} | DataLength(calculated) ${res.value.rest.length / 32}" +
        s"\n[ERGO DECODER] TypeId ${NetworkObjectTypeId.fromByte(dataPasred.typeId)} " +
        s"| DataLength ${dataPasred.ids.length} | DataLength(calculated) ${dataPasred.ids.length}"

    case _ => "Error Message"

  override def asyncStart: Future[Unit] =
    val client = vertx.createNetClient()

    (client
      .connect(addr.port, addr.url)
      .map: sc =>
        sc.handler: buf =>
          state.get() match

            case NodeState.Running =>
              val res = HandshakeSerializer.parseBytesTry(buf.getBytes).toString
              db.put("lastHandshake", res)
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
                val res = messageHandler(x.value.data.toArray, x.value.msgType)
                println(s"Message ${x.value.msgType} result ${res}")
              println("Sending Sync message")
              sc.write(
                Buffer.buffer(
                  Message.serializeNodeMessage(
                    ErgoSyncInfoMessageSpec,
                    utils.Utils.syncMessageSerializedToBytes
                  )
                )
              )
      )
      .mapEmpty()
