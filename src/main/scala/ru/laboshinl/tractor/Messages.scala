package ru.laboshinl.tractor

import java.util.UUID

import akka.routing.ConsistentHashingRouter.ConsistentHashable

import scala.collection.mutable


case class TrackerMsg(id: UUID = null, size: Int = 0, finished: Int = 0, startedAt: Long = 0,
                      isNew: Boolean = false) extends ConsistentHashable {
  override def consistentHashKey = id

  def +(m: TrackerMsg): TrackerMsg = new TrackerMsg(m.id, this.size, this.finished + 1, this.startedAt, false)

  def getProgress: Float = if (this.size.equals(0)) 0 else this.finished.toFloat / this.size * 100

  def isFinished: Boolean = this.finished.equals(this.size)
}

case class MapperMsg(id: UUID, key: Long, flow: TractorFlow) extends ConsistentHashable {
  override def consistentHashKey = key
}

case class AggregatorMsg(id: UUID, value: mutable.Map[Long, TractorFlow]) extends ConsistentHashable {
  override def consistentHashKey = id
}

case class WorkerMsg(id: UUID, bigDataFilePath: String, chunkIndex: Int)

case object Acknowledged