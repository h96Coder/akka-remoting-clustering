import ChatObject.{ChatMessage, EnterRoom, UserMessage}
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory

object ChatObject{
  case class ChatMessage(nickname: String, content: String)
  case class EnterRoom(address: String, nickname: String)
  case class UserMessage(msg: String)
}
class ChatActor(nickName: String, port: Int) extends Actor with ActorLogging {
  val cluster = Cluster(context.system)
  override def preStart() = cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  override def postStop(): Unit = cluster.unsubscribe(self)
  override def receive: Receive = online(Map())
  def getSelection(member: String) ={
    context.actorSelection(s"${member}/user/chatActor")
  }
  def online(chatRoom: Map[String, String]):Receive = {
    case MemberUp(member) =>
      val remoteactorSel = getSelection(member.address.toString)
      remoteactorSel ! EnterRoom(s"${self.path.address}@127.0.0.1:$port", nickName)
      //actorSel.resolveOne(10.seconds).map(self ! EnterRoom(member.address, actorSel))

    case MemberRemoved(member, status) =>
      context.become(online(chatRoom - member.address.toString))

    case EnterRoom(address, nickname) =>
      if(nickname != nickName)
      {
        log.info("Entered the room {}", nickname)
        context.become(online(chatRoom + (address -> nickname)))
      }

    case UserMessage(content) =>
      log.info("all actors {}", chatRoom.values)
      chatRoom.keys.foreach{
        x => getSelection(x) ! ChatMessage(nickName, content)
      }
    case ChatMessage(nickname, content) =>
      log.info(s"Actor $nickname send msg $content")
  }
}
class ChatApp(nickname: String, port: Int) extends App{
  val conf  = ConfigFactory.parseString(
    s"""
      |akka.remote.artery.canonical.port = $port
      |""".stripMargin).withFallback(ConfigFactory.load("clustering/clusterChat.conf"))
  val chatactor = ActorSystem("ChatSystem", conf).actorOf(Props(new ChatActor(nickname, port)), "chatActor")
  scala.io.Source.stdin.getLines().foreach(x => chatactor ! UserMessage(x))
}
object HK extends ChatApp("HK", 2551)
object BK extends ChatApp("BK", 2552)
object VK extends ChatApp("VK", 2553)
