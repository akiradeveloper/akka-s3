package akka.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._

import scala.xml.XML

trait AdminSupport { self: Server =>
  def adminRoute =
    path("admin" / "user") {
      post {
        val newUser = users.mkUser
        complete(User.toXML(newUser))
      }
    } ~
    path("admin" / "user" / Segment) { id =>
      get {
        val user = users.getUser(id)
        user.isDefined.orFailWith(Error.AccountProblem()) // FIXME error type
        complete(User.toXML(user.get))
      } ~
      delete {
        complete(StatusCodes.NotImplemented)
      } ~
      put {
        entity(as[String]) { s =>
          val xml = XML.loadString(s)
          val user = users.getUser(id)
          user.isDefined.orFailWith(Error.AccountProblem()) // FIXME error type
        val newUser = user.get.modifyWith(xml)
          users.updateUser(id, newUser)
          complete(StatusCodes.OK)
        }
      }
    }
}
