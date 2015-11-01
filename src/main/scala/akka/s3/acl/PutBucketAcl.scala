package akka.s3.acl

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.s3.AuthorizedContext

trait PutBucketAcl { self: AuthorizedContext =>
  def doPutBucketAcl() = {
    // val xml =
    // val acl = fromXML(xml)
    complete(StatusCodes.NotImplemented)
  }
}
