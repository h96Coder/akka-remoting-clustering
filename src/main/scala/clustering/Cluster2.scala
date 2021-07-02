package clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Props, ReceiveTimeout}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp, UnreachableMember}
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import akka.pattern.pipe
import clustering.ClusterObject.{ProcessCount, ProcessFile, ProcessLineCount, WorkerOrder}
import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt}


object ClusterObject{
  case class ProcessFile(file: String)
  case class ProcessResult(count: Int)
  case class ProcessCount(line: String, actor: ActorRef)
  case class ProcessLineCount(count: Int)
  case class WorkerOrder(line: String, aggregator: ActorRef)
}

class ClusterWordCountPriorityMailBox(setting: ActorSystem.Settings, config: Config) extends UnboundedPriorityMailbox(
  PriorityGenerator{
    case _ : MemberEvent => 4
    case _ => 1
  }
)
class MasterActor extends Actor with ActorLogging{
  val cluster = Cluster(context.system)
  var worker: Map[Address, ActorRef] = Map()
  var pendingRemove: Map[Address, ActorRef] = Map()
  override def preStart(): Unit = {
    cluster.subscribe(self,
      initialStateMode =  InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
      )
  }
  override def postStop(): Unit = cluster.unsubscribe(self)
  override def receive: Receive = handleEvent.orElse(workerRegisteration).orElse(handleJob)
  def handleEvent: Receive ={
    case MemberUp(member) if member.hasRole("worker") =>
      log.info("member up {}", member)
      if(pendingRemove.contains(member.address)){
        log.info("already member exits ")
        pendingRemove = pendingRemove - member.address
      }
      else{
        val actorSelection = context.actorSelection(s"${member.address}/user/worker1")
        log.info(s"heyyy babe ${member.address}/user/worker")
        actorSelection.resolveOne(100.seconds).map(ref => (member.address, ref)).pipeTo(self)
      }
    case UnreachableMember(member) if member.hasRole("worker")=>
      log.info("member unreachable {}", member)
      val workerOption = worker.get(member.address)
      workerOption.foreach{
        ref => pendingRemove = pendingRemove + (member.address -> ref)
      }

    case MemberRemoved(member, previousStatus) if member.hasRole("worker")=>
      log.debug("memeber removed {}", member)
      pendingRemove = pendingRemove -  member.address
      worker = worker - member.address
    case m: MemberEvent => //TODO
  }
  def workerRegisteration: Receive = {
    case pair: (Address, ActorRef) =>
      log.info("registered pair {}", pair)
      worker = worker + pair
  }

  def handleJob: Receive ={
    case ProcessFile(fileName) =>
      val aggregater = context.actorOf(Props[Counter], "counter")
      scala.io.Source.fromFile(fileName).getLines().foreach{
        self ! ProcessCount(_, aggregater)
      }
    case ProcessCount(line, aggregator) =>
      val rand = util.Random.nextInt((worker -- pendingRemove.keys).size)
      val actor = (worker -- pendingRemove.keys).values.toSeq(rand)
      log.info("Worker {} at master ", actor)
      actor ! WorkerOrder(line, aggregator)
      Thread.sleep(10)
  }
}
class Worker extends Actor with ActorLogging{
  override def receive: Receive ={
    case WorkerOrder(line: String, aggregator: ActorRef) =>
      log.info("Processing Actor {}", context)
      log.info("processing -- {}", line)
      Thread.sleep(1000)
      aggregator ! ProcessLineCount(line.split(" ").length)
  }
}

class Counter extends Actor with ActorLogging{
  context.setReceiveTimeout(5.seconds)
  override def receive: Receive = online(0)
  def online(totalCount: Int): Receive = {
    case ProcessLineCount(count) => {
      Thread.sleep(100)
      context.become(online(totalCount + count))
    }
    case ReceiveTimeout =>
      log.info("Total Count {}", totalCount)
      context.setReceiveTimeout(Duration.Undefined)
  }
}
object Cluster2  extends App{
   def createNode(port: Int, role: String, actorName: String, props: Props ) ={
     val config = ConfigFactory.parseString(
       s"""
         |akka.remote.artery.canonical.port = $port
         |akka.cluster.roles = [$role]
         |""".stripMargin).withFallback(ConfigFactory.load("clustering/cluster2.conf"))
     val system = ActorSystem("ClusterSystem", config)
     system.actorOf(props, actorName)
   }
  val master = createNode(2551, "master", "master", Props[MasterActor])
  createNode(2553, "worker", "worker1", Props[Worker])
  createNode(2554, "worker", "worker1", Props[Worker])
  Thread.sleep(1000)
  master ! ProcessFile("/home/himanshu/Desktop/ResourceAllocationDoc.txt")

}
object AdditionalWorker extends App{
  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2552
      |akka.cluster.roles = ["worker"]
      |""".stripMargin).withFallback(ConfigFactory.load("clustering/cluster2.conf"))
  ActorSystem("ClusterSystem", config).actorOf(Props[Worker], "worker1")
}
