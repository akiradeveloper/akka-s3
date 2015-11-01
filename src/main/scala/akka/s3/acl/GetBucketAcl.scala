package akka.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

trait GetBucketAcl { self: AuthorizedContext =>
  case class GetBucketAcl(bucketName: String) {
    def run = {
      val b = tree.findBucket(bucketName)
      // val acl = b.acl
      complete(StatusCodes.NotImplemented)
    }
  }
}
