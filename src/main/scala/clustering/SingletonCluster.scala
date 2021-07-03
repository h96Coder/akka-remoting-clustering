package clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.ConfigFactory

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.Random

case class Order(items: List[String], amount: Double)
case class Transaction(order_id : Int, txnId: String, amount: Double)
class PaymentActor extends Actor with ActorLogging{
  override def receive: Receive = {
    case m: Transaction => log.info("got transaction {}", m)
    case n => log.info("anothere mesg {}", n)
  }
}
class PaymentApp(port : Int) extends App{
  val conf = ConfigFactory.parseString(
    s"""
      |akka.remote.artery.canonical.port = $port
      |""".stripMargin).withFallback(ConfigFactory.load("clustering/singletonCluster.conf"))
  val system = ActorSystem("ClusterSystem", conf)
  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props[PaymentActor],
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    "paymentSystem"
  )
}
object Node1 extends PaymentApp(2551)
object Node2 extends PaymentApp(2552)
object Node3 extends PaymentApp(2553)

class OnlineShoppingCheckout(paymentActor: ActorRef)  extends Actor with ActorLogging {
  val orderId = 0
  override def receive: Receive = {
    case Order(_, amount) =>
      log.info(s"got order orderId $orderId of amount $amount")
      val transaction = Transaction(order_id = orderId, txnId = UUID.randomUUID().toString, amount = 50 * orderId)
      paymentActor ! transaction
  }
}
object OnlineShoppingCheckout{
  def props(paymentActor :ActorRef) = Props(new OnlineShoppingCheckout(paymentActor))
}

object checkOut extends App{
  val conf = ConfigFactory.parseString(
    s"""
      |akka.remote.artery.canonical.port = 2555
      |""".stripMargin).withFallback(ConfigFactory.load("clustering/singletonCluster.conf"))
  val system = ActorSystem("ClusterSystem", conf)
  val proxy  = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "user/paymentSystem",
      settings = ClusterSingletonProxySettings(system)
    ),
    "paymentProxySystem"
  )
  val actor = system.actorOf(OnlineShoppingCheckout.props(proxy), "checkoutShopping")
  import system.dispatcher
  system.scheduler.schedule(1.seconds, 5.seconds, () =>
    {
      val amount = Order(List(), Random.nextDouble()*100)
    actor ! amount
    }
  )

}
