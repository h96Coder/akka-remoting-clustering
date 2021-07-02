package remoting

import akka.actor._
import com.typesafe.config.ConfigFactory
import remoting.WorkCountDemo.{CountWord, Initiate, TextMsg, WordCountResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
case object WorkCountDemo {
 case class Initiate(nWorker: Int)
 case class CountWord(text: String)
 case class WordCountResult(count: Int)
 case class TextMsg(text: String)
}

class MasterActor extends Actor with ActorLogging {
 override def receive: Receive = {
  case Initiate(nWorker) =>
   log.info(s"workers $nWorker")
   val workerSelection  = (1 to nWorker).map( x => context.actorSelection(s"akka://remoteSystem@127.0.0.1:2554/user/WorkerActor$x"))
   workerSelection.foreach( _ ! Identify("hkAkka"))
   context.become(initialiting(List(), nWorker))
 }
 def initialiting(workers: List[ActorRef], nWorker: Int): Receive ={
  case ActorIdentity("hkAkka", Some(ref)) =>
   log.info(s"actor xxx $ref")
   if(nWorker == 1) {
    log.info("call online event {}", workers)
    context.become(online(workers, 0, 0))
   }
   else context.become(initialiting(ref :: workers, nWorker - 1 ))
 }
 def online(workers: List[ActorRef], remainingTask: Int, totalCount: Int): Receive ={
  case TextMsg(text) =>
     val sentence = text.split("#")
     Iterator.continually(workers).flatten.zip(sentence.iterator).foreach{
      pair =>
      val (worker, sentence) = pair
         worker ! CountWord(sentence)
     }
   context.become(online(workers, remainingTask + sentence.length, totalCount))
  case WordCountResult(count) =>
   log.info(s"total count  $totalCount")
   if(remainingTask == 1) {
    workers.foreach(_ ! PoisonPill)
    context.stop(self)
   }
   context.become(online(workers, remainingTask - 1, totalCount + count))
 }
}

class WorkerActor extends Actor with ActorLogging {
 override def receive: Receive = {
  case CountWord(text) =>
     val words =  text.split("")
     log.info("words {}", words.size)
     sender() ! WordCountResult(words.size)
 }
}


object MasterApp extends App{
 val conf = ConfigFactory.load("remoting/remote.conf")
 val sys = ActorSystem("localSystem", conf)
 val masterActor = sys.actorOf(Props[MasterActor], "MasterActor")
 masterActor ! Initiate(5)
 Thread.sleep(2000)
 //print(scala.io.Source.fromFile("/home/himanshu/Desktop/ResourceAllocationDoc.txt").getLines().toList.toString())
// val text  = scala.io.Source.fromFile("/home/himanshu/Desktop/ResourceAllocationDoc.txt").getLines().toList.toString()
  masterActor ! TextMsg(
  scala.io.Source.fromFile("/home/himanshu/Desktop/ResourceAllocationDoc.txt").getLines().toList.mkString(sep = "#")
 )
}
object WorkerApp extends App   {
 val conf = ConfigFactory.load("remoting/remote.conf").getConfig("remoteSystem")
 val sys = ActorSystem("remoteSystem", conf)
 (1 to 5).foreach(x => sys.actorOf(Props[WorkerActor], s"WorkerActor$x"))
}


