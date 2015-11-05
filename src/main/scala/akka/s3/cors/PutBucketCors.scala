package akka.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.xml.XML

trait PutBucketCors { self: AuthorizedContext =>
  def doPutBucketCors(bucketName: String) = {
    val b = tree.findBucket(bucketName).get

    val content = req.entity.dataBytes

    // TODO really bad. do more functional
    val fut = content.runForeach { bs =>
      val xml = XML.load(bs.iterator.asInputStream)
      val rules = Cors.parseXML(xml)
      Cors.File(rules).write(b.cors)
    }
    Await.ready(fut, Duration.Inf)
    complete(StatusCodes.OK)
  }
}
