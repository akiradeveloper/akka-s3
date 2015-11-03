package akka.s3

import akka.http.scaladsl.model.{HttpEntity, StatusCodes, HttpMethods, HttpRequest}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import scala.collection.immutable

trait OptionsObject { self: AuthorizedContext =>
  import Cors._
  case class OptionsObject(req: HttpRequest, requestId: String) {
    def run =
      options {
        extractObject { (bucketName, keyName) =>
          // If CORS isn't set access should be denied
          val b = tree.findBucket(bucketName).get
          val cors = LoggedFile(b.cors).get
          cors.isDefined.orFailWith(Error.AccessDenied())

          val hl = HeaderList.Aggregate(Seq(req.listFromHeaders, req.listFromQueryParams))

          val corsReq = Cors.Request(
            origin = AllowedOrigin(hl.get("Origin").get), // essential (get always succeeds)
            requestMethod = AllowedMethod(hl.get("Access-Control-Request-Method").get), // essential
            requestHeaders = hl.get("Access-Control-Request-Headers") match { // [spec] A comma-delimited list of HTTP headers
              case Some(x) => x.split(",").map(_.trim).map(AllowedHeader(_))
              case None => Seq()
            }
          )

          val rules = Cors.read(cors.get).rules

          val result = tryMatch(rules, corsReq)
          result.isDefined.orFailWith(Error.AccessDenied())

          val rule = result.get._1
          val res = result.get._2

          val headers =
            `Access-Control-Allow-Credentials`(true) +:
            `Access-Control-Allow-Origin`(HttpOrigin(res.origin.unwrap)) +:
            `Access-Control-Allow-Methods`(HttpMethods.getForKey(res.allowedMethods.unwrap).get) +:
            { // FIXME (don't concat lists but use empty node)
              (res.maxAgeSeconds match {
                case None => immutable.Seq()
                case Some(a) => immutable.Seq(`Access-Control-Max-Age`(a.unwrap))
              }) ++
              (res.allowedHeaders match { // [spec] A comma-delimited list of HTTP headers
                 case Seq() => immutable.Seq()
                 case a => immutable.Seq(`Access-Control-Allow-Headers`(a.map(_.unwrap).mkString(",")))
              }) ++
              (res.exposeHeaders match { // [spec] A comma-delimited list of HTTP headers
                case Seq() => immutable.Seq()
                case a => immutable.Seq(`Access-Control-Expose-Headers`(res.exposeHeaders.map(_.unwrap).mkString(",")))
              })
            }

          complete(StatusCodes.OK, headers, HttpEntity.Empty)
        }
      }
  }
}
