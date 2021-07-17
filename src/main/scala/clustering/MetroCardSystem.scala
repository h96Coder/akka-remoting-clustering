package clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.ConfigFactory

import java.util.{Date, UUID}
import scala.util.Random

case class MetroCard(id: String, amount: Double)
case class Attempted(metroCard: MetroCard, date: Date)
case object EntryAccepted
case class EntryRejected(reason: String)

///////////////////////////////////////////////////////
////// Sharding Actor
////////////////////////////////////////////////////////
object MetroStation{
  def props(validator: ActorRef) = Props(new MetroStation(validator))
}
class MetroStation(validator: ActorRef) extends Actor with ActorLogging{
  override def receive: Receive = {
    case o: MetroCard => validator ! Attempted(o, new Date())
    case EntryAccepted => log.info("The card accepted")
    case EntryRejected(reason) => log.info(s"reason $reason of rejection")
  }
}
///////////////////////////////////////////////////////
////// Card Validator Actor
////////////////////////////////////////////////////////

class CardValidator extends Actor with ActorLogging{
  override def preStart(): Unit = log.info("Validator started")
  override def receive: Receive = {
    case Attempted(card @ MetroCard(id, amount), _) =>
      log.info(s"vlaidating card $card")
      if(amount > 2.5){
        sender() ! EntryAccepted
      }
      else{
        sender() ! EntryRejected("insufficient amount, plz recharge your card")
      }
  }
}

///////////////////////////////////////////////////////
////// SHarding settings
////////////////////////////////////////////////////////

object MetroStationSetting{
  val numberOfShards = 10
  val numberOfEntity = 100
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ Attempted(MetroCard(id, _), _) =>
      ((id.hashCode().abs % numberOfEntity).toString, msg)
  }
  val extractShardId: ShardRegion.ExtractShardId ={
    case msg @ Attempted(MetroCard(id, _), _) =>
      (id.hashCode().abs % numberOfShards).toString
  }
}

///////////////////////////////////////////////////////
////// SHarding settings
////////////////////////////////////////////////////////

class MetroCardSystem(port: Int, noOfEntryGate: Int) extends App{
  val conf = ConfigFactory.parseString(
    s"""
      |akka.remote.artery.canonical.port = $port
      |""".stripMargin).withFallback(ConfigFactory.load("clustering/singletonCluster.conf"))
  val system = ActorSystem("ClusterSystem", conf)
  val validatorShardedRegionRef = ClusterSharding(system).start(
    typeName = "metrocardValidator",
    entityProps = Props[CardValidator],
    settings = ClusterShardingSettings(system),
    extractEntityId = MetroStationSetting.extractEntityId,
    extractShardId= MetroStationSetting.extractShardId
  )
  print("validator "+ validatorShardedRegionRef)
  val gates = (1 to noOfEntryGate).map(_ => system.actorOf(MetroStation.props(validatorShardedRegionRef)))
  print("Himanshu"+ gates)
  Thread.sleep(1000)
  for(_ <- 1 to 10){
    val randIndex = Random.nextInt(noOfEntryGate)
    val gate = gates(randIndex)
    gate ! MetroCard(UUID.randomUUID().toString, 100 * Random.nextDouble())
  }
}
object Delhi extends  MetroCardSystem(2551, 5)
object Noida extends  MetroCardSystem(2552, 10)

//class CardEntry extends App{
//  val conf = ConfigFactory.parseString(
//    s"""
//       |akka.remote.artery.canonical.port = $port
//       |""".stripMargin).withFallback(ConfigFactory.load("clustering/singletonCluster.conf"))
//  val system = ActorSystem("ClusterSystem", conf)
//}
