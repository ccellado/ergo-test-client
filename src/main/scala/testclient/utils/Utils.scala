package testclient.utils

import io.vertx.core.ThreadingModel
import io.vertx.scala.core.DeploymentOptions
import org.ergoplatform.mining.AutolykosPowScheme
import org.ergoplatform.network.{
  Handshake,
  HandshakeSerializer,
  PeerSpec,
  Version
}
import org.ergoplatform.nodeView.history.{
  ErgoSyncInfoMessageSpec,
  ErgoSyncInfoV2
}
import org.ergoplatform.settings.{
  ChainSettings,
  MonetarySettings,
  ReemissionSettings,
  VotingSettings
}
import scorex.util.ModifierId
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}

object Utils:
  // For testnet
  // application.conf + testnet.conf from node
  def chainSettings: ChainSettings = ChainSettings(
    protocolVersion = 3,
    addressPrefix = 16,
    blockInterval = FiniteDuration(45, SECONDS),
    epochLength = 128,
    eip37EpochLength = None,
    useLastEpochs = 8,
    voting = VotingSettings(
      votingLength = 128,
      softForkEpochs = 32,
      activationEpochs = 32,
      version2ActivationHeight = 128,
      version2ActivationDifficultyHex = "20"
    ),
    powScheme = AutolykosPowScheme(32, 26),
    monetary = MonetarySettings(minerRewardDelay = 720),
    reemission = ReemissionSettings(
      checkReemissionRules = false,
      emissionNftId = ModifierId(
        "06f29034fb69b23d519f84c4811a19694b8cdc2ce076147aaa050276f0b840f4"
      ),
      reemissionTokenId = ModifierId(
        "01345f0ed87b74008d1c46aefd3e7ad6ee5909a2324f2899031cdfee3cc1e022"
      ),
      reemissionNftId = ModifierId(
        "06f2c3adfe52304543f7b623cc3fccddc0174a7db52452fef8e589adacdfdfee"
      ),
      activationHeight = 188001,
      reemissionStartHeight = 1860400,
      injectionBoxBytesEncoded =
        "a0f9e1b5fb011003040005808098f4e9b5ca6a0402d1ed91c1b2a4730000730193c5a7c5b2a4730200f6ac0b0201345f0ed87b74008d1c46aefd3e7ad6ee5909a2324f2899031cdfee3cc1e02280808cfaf49aa53506f29034fb69b23d519f84c4811a19694b8cdc2ce076147aaa050276f0b840f40100325c3679e7e0e2f683e4a382aa74c2c1cb989bb6ad6a1d4b1c5a021d7b410d0f00"
    ),
    noPremineProof = Seq(
      "'Chaos reigns': what the papers say about the no-deal Brexit vote",
      "习近平的两会时间|这里有份习近平两会日历，请查收！",
      "ТАСС сообщил об обнаружении нескольких майнинговых ферм на столичных рынках",
      "000000000000000000139a3e61bd5721827b51a5309a8bfeca0b8c4b5c060931",
      "0xef1d584d77e74e3c509de625dc17893b22b73d040b5d5302bbf832065f928d03"
    ),
    foundersPubkeys = Seq(
      "039bb5fe52359a64c99a60fd944fc5e388cbdc4d37ff091cc841c3ee79060b8647",
      "031fb52cf6e805f80d97cde289f4f757d49accf0c83fb864b27d2cf982c37f9a8b",
      "0352ac2a471339b0d23b3d2c5ce0db0e81c969f77891b9edf0bda7fd39a78184e7"
    ),
    genesisStateDigestHex =
      "cb63aa99a3060f341781d8662b58bf18b9ad258db4fe88d09f8f71cb668cad4502",
    initialDifficultyHex = "01",
    makeSnapshotEvery = 52224,
    genesisId = Some(
      ModifierId(
        "cb63aa99a3060f341781d8662b58bf18b9ad258db4fe88d09f8f71cb668cad4502"
      )
    )
  )

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
  val syncMessageSerializedToBytes =
    ErgoSyncInfoMessageSpec.toBytes(syncMessage)

  val depOptions =
    new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)

  val testPeers = Seq(
    Address("0.0.0.0", 9022),
    Address("213.239.193.208", 9022),
    Address("168.138.185.215", 9022),
    Address("192.234.196.165", 9022)
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
