package org.apache.gearpump.cluster

import org.apache.gearpump.Time.MilliSeconds
import org.apache.gearpump.metrics.Metrics.MetricType


object ClientToMaster {
  /** Options for read the metrics from the cluster */
  object ReadOption {
    type ReadOption = String

    val Key: String = "readOption"

    /** Read the latest record of the metrics, only return 1 record for one metric name (id) */
    val ReadLatest: ReadOption = "readLatest"

    /** Read recent metrics from cluster, typically it contains metrics in 5 minutes */
    val ReadRecent = "readRecent"

    /**
      * Read the history metrics, typically it contains metrics for 48 hours
      *
      * NOTE: Each hour only contain one or two data points.
      */
    val ReadHistory = "readHistory"
  }

  /** Query history metrics from master or app master. */
  case class QueryHistoryMetrics(
                                  path: String, readOption: ReadOption.ReadOption = ReadOption.ReadLatest,
                                  aggregatorClazz: String = "", options: Map[String, String] = Map.empty[String, String])
}

object MasterToClient {
  case class HistoryMetricsItem(time: MilliSeconds, value: MetricType)
  /**
    * History metrics returned from master, worker, or app master.
    *
    * All metric items are organized like a tree, path is used to navigate through the tree.
    * For example, when querying with path == "executor0.task1.throughput*", the metrics
    * provider picks metrics whose source matches the path.
    *
    * @param path    The path client provided. The returned metrics are the result query of this path.
    * @param metrics The detailed metrics.
    */
  case class HistoryMetrics(path: String, metrics: List[HistoryMetricsItem])

}