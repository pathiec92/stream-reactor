
/*
 * Copyright 2020 Lenses.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.lenses.streamreactor.connect.aws.s3.sink

import io.lenses.streamreactor.connect.aws.s3.model.TopicPartitionOffset
import org.slf4j.Logger

import scala.concurrent.duration.FiniteDuration

/**
  * The [[CommitPolicy]] is responsible for determining when
  * a file should be flushed (closed on disk, and moved to be visible).
  *
  * Typical implementations will flush based on number of records,
  * file size, or time since the file was opened.
  */
trait CommitPolicy {

  /**
    * This method is invoked after a file has been written.
    *
    * If the output file should be committed at this time, then this
    * method should return true, otherwise false.
    *
    * Once a commit has taken place, a new file will be opened
    * for the next record.
    *
    *
    */
  def shouldFlush(context: CommitContext): Boolean
}

/**
  * @param tpo              the [[TopicPartitionOffset]] of the last record written
  * @param count            the number of records written thus far to the file
  * @param createdTimestamp the time in milliseconds when the the file was created/accessed first time
  */
case class CommitContext(tpo: TopicPartitionOffset,
                         count: Long,
                         fileSize: Long,
                         createdTimestamp: Long)

/**
  * Default implementation of [[CommitPolicy]] that will flush the
  * output file under the following circumstances:
  * - file size reaches limit
  * - time since file was created
  * - number of files is reached
  *
  * @param interval in millis
  */
case class DefaultCommitPolicy(fileSize: Option[Long],
                               interval: Option[FiniteDuration],
                               recordCount: Option[Long]) extends CommitPolicy {
  val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass.getName)
  require(fileSize.isDefined || interval.isDefined || recordCount.isDefined)

  override def shouldFlush(context: CommitContext): Boolean = {
    val open_time = System.currentTimeMillis() - context.createdTimestamp
    val flushDueToFileSize = fileSize.exists(_ <= context.fileSize)
    val flushDueToInterval = interval.exists(_.toMillis <= open_time)
    val flushDueToCount = recordCount.exists(_ <= context.count)

    val flush = flushDueToFileSize ||
      flushDueToInterval ||
      flushDueToCount

    if (flush) logger.debug(s"Flushing: Because why? size: $flushDueToFileSize, interval: $flushDueToInterval, count: $flushDueToCount, CommitContext: $context")

    flush
  }
}
