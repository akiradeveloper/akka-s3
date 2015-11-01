package functional.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

trait PutObjectAcl { self: AuthorizedContext =>
  case class PutObjectAcl() {
    def run = {
      // val xml =
      // val acl = fromXML(xml)
      complete(StatusCodes.NotImplemented)
    }
  }
}
