package functional.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

trait GetObjectAcl { self: AuthorizedContext =>
  case class GetObjectAcl() {
    def run = {
      complete(StatusCodes.NotImplemented)
    }
  }
}
