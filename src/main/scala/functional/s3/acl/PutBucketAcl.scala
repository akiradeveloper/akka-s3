package functional.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

trait PutBucketAcl { self: AuthorizedContext =>
  def doPutBucketAcl() = {
    // val xml =
    // val acl = fromXML(xml)
    complete(StatusCodes.NotImplemented)
  }
}
