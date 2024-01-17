import messageSpec.Message
import org.ergoplatform.network.{
  Handshake,
  HandshakeSerializer,
  PeerSpec,
  Version
}
import scodec.bits.BitVector

import java.net.InetSocketAddress

@main
def test(): Unit =
  val myPeerSpec = PeerSpec(
    agentName = "TestErgoClient_" + math.random(),
    protocolVersion = Version("5.0.18"),
    nodeName = "TestNode_" + math.random(),
    declaredAddress = Some(InetSocketAddress("0.0.0.0", 9026)),
    features = Seq()
  )
  val magic            = Array(1: Byte, 0: Byte, 7: Byte, 9: Byte) // test magic
  val handshakeMessage = Handshake(myPeerSpec, System.currentTimeMillis())
  val handshakeMessageSerializedToBytes =
    magic ++ Array(
      HandshakeSerializer.messageCode,
      HandshakeSerializer.toBytes(handshakeMessage).length.toByte
    ) ++ HandshakeSerializer.toBytes(handshakeMessage)

  val msg =
    Message.messageCodec.decode(BitVector(handshakeMessageSerializedToBytes))
  println(msg)
  msg.map: x =>
    x.value.magic.foreach(x => println(x))

  val mes1 = Array(-80, -127, -54, -52, -48, 49, 7, 101, 114, 103, 111, 114,
    101, 102, 5, 0, 18, 13, 99, 99, 101, 108, 108, 97, 100, 111, 45, 116, 101,
    115, 116, 0, 3, 16, 4, 0, 1, 0, 1, 3, 13, 2, 0, 2, 3, -36, -78, -86, -52,
    -120, -74, -105, -65, 6, 2, 6, 127, 0, 0, 1, -66, 70).map(_.toBinaryString)

  val mes2 = Array(-91, -103, -48, -52, -48, 49, 7, 101, 114, 103, 111, 114,
    101, 102, 5, 0, 18, 13, 99, 99, 101, 108, 108, 97, 100, 111, 45, 116, 101,
    115, 116, 0, 3, 16, 4, 0, 1, 0, 1, 3, 14, 2, 0, 2, 3, -8, -21, -105, -52,
    -126, -59, -86, -97, -92, 1, 2, 6, 127, 0, 0, 1, -66, 70).map(_.toBinaryString)

  println(mes1.mkString("Array(", ", ", ")"))

  println(mes2.mkString("Array(", ", ", ")"))