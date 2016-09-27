package ru.laboshinl.tractor

import java.io.RandomAccessFile
import java.net.InetAddress
import java.util.UUID

import akka.actor._
import akka.cluster.Cluster
import akka.routing._
import com.typesafe.config.ConfigFactory

/**
 * Created by laboshinl on 9/27/16.
 */

object ApplicationMain extends App {

  var flag = false
  var bigDataFilePath = "/home/ubuntu/pcaps/2013-10-10_capture-win14.pcap"

  System.setProperty("akka.remote.netty.tcp.hostname", InetAddress.getLocalHost.getHostAddress)

  if (args.length > 0) {
    flag = true
    bigDataFilePath = args(0)
  }


  val system = ActorSystem("ClusterSystem", ConfigFactory.load())
  val cluster = Cluster(system)
  val defaultBlockSize = 64 * 1024 * 1024

  if (flag) {
    Thread.sleep(10000)

    val printer = system.actorOf(Props[PrintActor], "printer")
    val reducer = system.actorOf(FromConfig.props(Props(new ReduceActor(printer))), "reducer")
    val aggregator = system.actorOf(FromConfig.props(Props(new AggregateActor(reducer, printer))), "aggregator")
    val tracker = system.actorOf(FromConfig.props(Props(new TrackActor(aggregator, printer))), "tracker")
    val mapper = system.actorOf(FromConfig.props(Props(new MapActor(tracker, aggregator))), "mapper")


    Thread.sleep(10000)

    0.to(30).foreach(_ => {
      val totalChunks = totalMessages(bigDataFilePath)
      val id = UUID.randomUUID()
      tracker ! TrackerMsg(id, totalChunks, 0, System.currentTimeMillis, isNew = true)
      for (i <- 0 to totalChunks)
        mapper ! WorkerMsg(id, bigDataFilePath, i) //} )
    })
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
