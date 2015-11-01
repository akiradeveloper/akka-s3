package akka.s3.acl

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.s3.AuthorizedContext

trait GetObjectAcl { self: AuthorizedContext =>
  case class GetObjectAcl() {
    def run = {
      complete(StatusCodes.NotImplemented)
    }
  }
}
