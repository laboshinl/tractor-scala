package ru.laboshinl.tractor

import akka.actor.{Props, ActorLogging, ActorSystem, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.persistence.PersistentActor
import ru.laboshinl.tractor.CountUpActor.{Increased, CountUp}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class CountUpActor extends PersistentActor with ActorLogging {

  var count: Int = 0

  override def persistenceId: String = self.path.name

  override def receiveCommand: Receive = {
    case c: CountUp =>
      log.info("receive command {}", c)
      persist(Increased(c.count)) {
        event =>
          count += event.diff
          log.info("current count {}", count)
      }
  }

  override def receiveRecover: Receive = {
    case e: Increased =>
      log.info("receive recover {}", e)
      count += e.diff
  }
}

object CountUpActor {
  def props = Props[CountUpActor]

  case class CountUp(count: Int)

  case class Increased(diff: Int)

}

object ApplicationMain extends App {
  val system = ActorSystem("MyActorSystem")

  val actor = system.actorOf(CountUpActor.props, "c1")

  actor ! CountUp(1)
  actor ! CountUp(1)
  actor ! CountUp(1)

  //var Await = ???

  Await.result(system.whenTerminated, Duration.Inf)
}