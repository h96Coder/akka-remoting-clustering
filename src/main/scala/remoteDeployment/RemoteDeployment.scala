package remoteDeployment

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, AddressFromURIString, Deploy, PoisonPill, Props, Terminated}
import akka.remote.RemoteScope
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory

class RemoteActor extends  Actor with ActorLogging{
  override def receive: Receive = {
    case text: String =>
      log.info("Remote Actor created  " + text)
  }
}
class ParentLocal extends Actor with ActorLogging{
  override def receive: Receive = {
    case "create" =>
      log.info("creating child")
      val child = context.actorOf(Props[RemoteActor], "remoteChildActor")
      context.watch(child)
    case Terminated(ref) =>
      log.info("terminated {}" + ref)
  }
}
object LocalApp extends App {
  val system = ActorSystem("LocalActorSystem", ConfigFactory.load("remoteDeployment/remote.conf").getConfig("localSystem"))
  val actor = system.actorOf(Props[RemoteActor], "remoteActor")
  actor ! "hello remote actor"
  print(actor)
  // expected  --> akka://RemoteActorSystem@127.0.0.1:2554/remoteActor
  // actual  -->   akka://RemoteActorSystem@127.0.0.1:2554/remote/akka/LocalActorSystem@127.0.0.1:2553/user/remoteActor


  // programatically deployed actor
  val address: Address = AddressFromURIString("akka://RemoteActorSystem@127.0.0.1:2554")
  val pactor = system.actorOf(Props[RemoteActor].withDeploy(
    Deploy(scope = RemoteScope(address))
  ))
  pactor ! "programtically i deployed"
  // routers with routes deployed remotly
  val poolrouter = system.actorOf(FromConfig.props(Props[RemoteActor]), "routerRemoteChildActor")
  (1 to 10).map(i => s"child actor $i").foreach(poolrouter ! _)


  // creating child remote deployement
  system.actorOf(Props[ParentLocal], "parent") ! "create"
  Thread.sleep(1000)
  val actoradd = system.actorSelection("akka://RemoteActorSystem@127.0.0.1:2554/remote/akka/LocalActorSystem@127.0.0.1:2553/user/parent/remoteChildActor")
  actoradd ! "Hey again as a child"
  actoradd ! PoisonPill

}
object  RemoteApp extends App{
   val system = ActorSystem("RemoteActorSystem", ConfigFactory.load("remoteDeployment/remote.conf").getConfig("remoteSystem"))
}
