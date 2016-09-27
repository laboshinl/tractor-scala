package ru.laboshinl.tractor

import java.io.RandomAccessFile
import java.net.InetAddress
import java.util.UUID

import akka.actor._
import akka.cluster.Cluster
//import akka.pattern.Patterns
import akka.routing._
import akka.util.Timeout
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

import akka.pattern.ask

import scala.concurrent.Await


/**
 * Created by laboshinl on 9/27/16.
 */

object ApplicationMain extends App {

  var flag = false
  var bigDataFilePath = "/home/laboshinl/Downloads/bigFlows.pcap"

  System.setProperty("akka.remote.netty.tcp.hostname", InetAddress.getLocalHost.getHostAddress)

  if (args.length > 0) {
    flag = true
    bigDataFilePath = args(0)
  }


  val system = ActorSystem("ClusterSystem", ConfigFactory.load())
  val cluster = Cluster(system)
  val defaultBlockSize = 10 * 1024 * 1024

  if (flag) {
    //Thread.sleep(10000)

    val printer = system.actorOf(Props[PrintActor], "printer")
    val reducer = system.actorOf(FromConfig.props(Props(new ReduceActor(printer))), "reducer")
    val aggregator = system.actorOf(FromConfig.props(Props(new AggregateActor(reducer, printer))), "aggregator")
    val tracker = system.actorOf(FromConfig.props(Props(new TrackActor(aggregator, printer))), "tracker")
    val mapper = system.actorOf(FromConfig.props(Props(new MapActor(tracker, aggregator))), "mapper")

    implicit val timeout = Timeout(5 seconds)
    val future = aggregator ? akka.routing.GetRoutees
    val result = Await.result(future, timeout.duration).asInstanceOf[akka.routing.Routees]
    reducer ! new Broadcast(result)

    Thread.sleep(10000)

   // 1.to(20).foreach(_ => {
      val totalChunks = totalMessages(bigDataFilePath)
      val id = UUID.randomUUID()
      tracker ! TrackerMsg(id, totalChunks, 0, System.currentTimeMillis, isNew = true)
      for (i <- 0 to totalChunks)
        mapper ! WorkerMsg(id, bigDataFilePath, i)
   // })
  }


  private def totalMessages(bigDataFilePath: String): Int = {
    val randomAccessFile = new RandomAccessFile(bigDataFilePath, "r")
    try {
      (randomAccessFile.length / defaultBlockSize).toInt
    } finally {
      randomAccessFile.close()
    }
  }
}
