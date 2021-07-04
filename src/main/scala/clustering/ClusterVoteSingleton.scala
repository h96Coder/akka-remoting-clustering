package clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, ReceiveTimeout, Terminated}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.ConfigFactory

import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Random

case class Person(id: String, age: Int)
object Person{
  def generate() = Person(UUID.randomUUID().toString, 16 + Random.nextInt(99))
}
case class Vote(p: Person, candidate: String)
case class Accepted(msg: String)
case class Rejected(msg: String)
case class Won(msg: String)
class VoteAggregator extends Actor with ActorLogging{
  val CANDIDATES = Set("John", "Jack", "Robin")
  context.setReceiveTimeout(10.second)
  override def receive: Receive = online(Set(), Map())
  def online(personVotes: Set[String], polls: Map[String, Int]): Receive={
    case Vote(Person(id, age), candidate) =>
      if(personVotes.contains(id)){
        sender() ! Rejected("Vote already submitted")
        log.info(s"$id Vote already submitted")
      }
      else if(age < 18){
        sender() ! Rejected("Not eligible for voting")
        log.info(s"$id Not eligible for voting")
      }
      else if(!CANDIDATES.contains(candidate)){
        sender() ! Rejected(s"invalid candidate $candidate")
        log.info(s"$candidate invalid ")
      }
      else{
        val canidateVotes = polls.getOrElse(candidate, 0)
        log.info(s"$id accepted")
        sender() ! Accepted("Vote accpeted")
        context.become(online(personVotes + id, polls + (candidate -> (1 + canidateVotes))))
      }
    case ReceiveTimeout =>
      sender() ! Won(s"won result $polls")
      context.setReceiveTimeout(Duration.Undefined)
      context.become(offline())
  }
  def offline(): Receive =
  {
   case v: Vote =>
     sender() ! "TimeUp...."
     log.info("time is over, timeup")
   case m =>
     sender() ! "Invalid MSG"
     log.info("invlid msg")
  }

}
object VoteSystem{
  def props(aggregator: ActorRef) = Props(new VoteSystem(aggregator))
}
class VoteSystem(aggregator: ActorRef) extends Actor with ActorLogging{
  override def receive: Receive = {
    case Vote(p: Person, candidate) =>
      log.info(s"Hi I am voting $candidate")
      aggregator ! Vote(p, candidate)
    case Accepted(msg) => log.info(s"got msg of accepted by actor --> $msg")
    case Won(msg) => log.info(s"Won --> $msg")
    case Rejected(msg) => log.info(s"got msg of rejected by actor --> $msg")
    case m => log.info(""+m)
  }
}
object ClusterVoteSingleton extends App {
  def createNdde(port: Int): Unit ={
    val conf = ConfigFactory.parseString(
      s"""
        |akka.remote.artery.canonical.port = $port
        |""".stripMargin).withFallback(ConfigFactory.load("clustering/singletonCluster.conf"))
    val system = ActorSystem("ClusterSystem", conf)
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props[VoteAggregator],
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ), "voteSystem")
  }
  createNdde(2551)
  createNdde(2552)
  createNdde(2553)
}
class Voter(p: Person, candidate: String, port: Int)  extends App{
  val conf = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = $port
       |""".stripMargin).withFallback(ConfigFactory.load("clustering/singletonCluster.conf"))
  val system = ActorSystem("ClusterSystem", conf)
  val proxy = system.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "user/voteSystem",
    settings = ClusterSingletonProxySettings(system)
  ))
  system.actorOf(VoteSystem.props(proxy), "voteSystem") ! Vote(p, candidate)
}
object Boby extends Voter(Person.generate(), "John", 2554)
object Sam extends Voter(Person.generate(), "Jack", 2555)
object Dean extends Voter(Person.generate(), "Robin", 2556)
object Mary extends Voter(Person.generate(), "John", 2557)
