package akka.s3

import akka.http.scaladsl.model.{StatusCodes, HttpEntity}
import akka.http.scaladsl.server.Directives._

trait HeadBucket { self: AuthorizedContext =>
  def doHeadBucket(bucketName: String) = {
    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID,  requestId)
    )

    val b = tree.findBucket(bucketName).get

    // TODO check permission

    complete(StatusCodes.OK, headers, HttpEntity.Empty)
  }
}
