package clustering
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberJoined, MemberRemoved, MemberUp, UnreachableMember}
import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props}
import com.typesafe.config.ConfigFactory

class ClusterSubscription extends Actor with ActorLogging{
  val cluster = Cluster(context.system)
  override def preStart(): Unit = {
    cluster.subscribe(
      self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
      )
  }

  override def postStop(): Unit = cluster.unsubscribe(self)
  override def receive:Receive = {
    case MemberJoined(member) => {
      log.info(s"Memeber Joined $member")
    }
    case MemberUp(member) if member.hasRole("crunch")=> {
      log.info(s"Memeber Up $member")
    }
    case MemberRemoved(member, previousStatus)=>{
      log.info(s"Memeber Up $member and previous status $previousStatus")
    }
    case UnreachableMember(member) =>{
      log.info(s"unreachable member  $member")
    }
    case m: MemberEvent => log.info(s"another Memeber event $m")
  }
}
object ClusterBasics extends App{
  def startCluster(ports: List[Int]): Unit ={
    ports.foreach { port =>
      val conf = ConfigFactory.parseString(
        s"""
           |akka.remote.artery.canonical.port = $port
           |""".stripMargin).withFallback(ConfigFactory.load("clustering/cluster.conf"))
      ActorSystem("ClusterSystem", conf)
    }
  }
  startCluster(List(2551, 2552, 0))
}

object ClusterBasics_Manual extends App{
  val system = ActorSystem("ClusterSystem" , ConfigFactory.load("clustering/cluster.conf").getConfig("manualRegistration"))
  val cluster = Cluster(system)
//  cluster.joinSeedNodes(List(
//    Address("akka", "ClusterSystem", "127.0.0.1", 2552),
//    Address("akka", "ClusterSystem", "127.0.0.1", 2551) // equivalent to akka://ClusterSystem@localhost:2552
//  ))
  cluster.join( Address("akka", "ClusterSystem", "127.0.0.1", 57581))
  system.actorOf(Props[ClusterSubscription], "clustersubscription")
}
