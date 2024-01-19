package testclient.message

import org.ergoplatform.network.message.{MessageConstants, MessageSpec}
import scodec.*
import scodec.bits.ByteVector
import scodec.codecs.*
import scorex.crypto.hash.Blake2b256
import scorex.utils.Ints
import testclient.utils.Utils.magic

case class Message(
    magic: ByteVector,
    msgType: Byte,
    size: Int,
    checksum: ByteVector,
    data: ByteVector
)

object Message:
  given messageCodec: Codec[Message] = (
    bytes(MessageConstants.MagicLength)
      :: byte
      :: int32
      :: bytes(MessageConstants.ChecksumLength)
      :: bytes
  ).as[Message]

  def serializeNodeMessage(spec: MessageSpec[_], msg: Array[Byte]): Array[Byte] =
      magic
        ++ Array(spec.messageCode)
        ++ Ints.toByteArray(msg.length)
        ++ Blake2b256.hash(msg).take(MessageConstants.ChecksumLength)
        ++ msg