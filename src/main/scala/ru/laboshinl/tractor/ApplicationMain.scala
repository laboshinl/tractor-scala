package ru.laboshinl.tractor

import java.io.{RandomAccessFile => Jraf}
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
  var bigDataFilePath = "/home/laboshinl/Downloads/smallFlows.pcap"

  //System.setProperty("akka.remote.netty.tcp.hostname", InetAddress.getLocalHost.getHostAddress)

  if (args.length == 0) {
    flag = true
    //bigDataFilePath = args(0)
  }


  val system = ActorSystem("ClusterSystem", ConfigFactory.load())
  val cluster = Cluster(system)
  //val defaultBlockSize = 10 * 1024 * 1024
  val blockSize = ConfigFactory.load.getInt("tractor.block-size")

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

    //Thread.sleep(10000)

    1.to(1).foreach(_ => {
      val fileSize = getFileSize(bigDataFilePath)
      val totalChunks = if ((fileSize/blockSize).toInt == 0) 1 else  (fileSize/blockSize).toInt
      val id = UUID.randomUUID()
      tracker ! TrackerMsg(id, totalChunks, 0, System.currentTimeMillis, isNew = true)
      for (i <- 0 to totalChunks-1) {
        val startPos = blockSize * i
        val endPos = if (i == totalChunks - 1) fileSize else (blockSize * (i + 1) - 1);
        mapper ! WorkerMsg(id, bigDataFilePath, startPos, endPos)
      }
    })
  }

  private def getFileSize(bigDataFilePath: String): Long = {
    val randomAccessFile = new Jraf(bigDataFilePath, "r")
    try {
      randomAccessFile.length
    } finally {
      randomAccessFile.close()
    }
  }
}
