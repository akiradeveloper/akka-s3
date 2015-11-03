package akka.s3

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._

import scala.collection.immutable
import scala.util.Random

trait RouteUtil {
  val extractBucket = path(Segment ~ (Slash | PathEnd))
  val extractObject = path(Segment / Rest)
  val toRawHeader = (a: (String, String)) => RawHeader(a._1, a._2)

  implicit class HttpRequestOps(unwrap: HttpRequest) {
    def listFromHeaders = HeaderList.FromRequestHeaders(unwrap)
    def listFromQueryParams = HeaderList.FromRequestQuery(unwrap.uri.query)
  }
}

case class Server(config: ServerConfig) extends RouteUtil {
  def doOptionsObject(req: HttpRequest, reqId: String) = complete("hoge")
  def doPostObject(req: HttpRequest, reqId: String) = complete("hoge")

  val tree = Tree(config.treePath)
  val users = UserTable(config.adminPath.resolve("db.sqlite"))

  def handler(req: HttpRequest, requestId: String) = ExceptionHandler {
    // FIXME error should sometimes contains header info such as x-amz-delete-marker
    case Error.Exception(e) => {
      // headers should be immutable.Seq
      val headers = immutable.Seq(
        (X_AMZ_REQUEST_ID, requestId)
      ).map(toRawHeader)

      val o = Error.toCodeAndMessage(e)
      // Don't forget a caller ctx otherwise completion flies to unknown somewhere

      complete(
        o.code,
        headers,
        Error.mkXML(o, req.uri.path.toString(), requestId))
    }
    case e: Throwable => {
      e.printStackTrace
      val headers = immutable.Seq(
        (X_AMZ_REQUEST_ID, requestId)
      ).map(toRawHeader)

      val ste: StackTraceElement = e.getStackTrace()(0)
      val msg = s"${ste.getFileName}(${ste.getLineNumber}) ${e.getMessage}"
      val o = Error.toCodeAndMessage(Error.InternalError(msg))
      complete(
        StatusCodes.InternalServerError,
        headers,
        Error.mkXML(o, req.uri.path.toString(), requestId))
    }
  }

  val route =
    extractRequest { req =>
      logRequestResult("") {
        val requestId = Random.alphanumeric.take(16).mkString
        handleExceptions(handler(req, requestId)) {
          // doOptionsObject(req, requestId) ~
          // doPostObject(req, requestId) ~
          extractRequest { _ => // FIXME just to dynamically create the successive routing
            val authResult: (Option[String], Boolean) =
              if (req.listFromHeaders.get("Authorization").isDefined) {
                (None, true)
                //              val a = Stream(AuthV2(req, m), AuthV4()).map(_.run).find(_.isDefined).flatten
                //              a.isDefined.orFailWith(Error.SignatureDoesNotMatch())
                //              a
              } else if (req.listFromQueryParams.get("Signature").isDefined) {
                //              val a = Stream(AuthV2Presigned(req, m)).map(_.run).find(_.isDefined).flatten
                //              a.isDefined.orFailWith(Error.SignatureDoesNotMatch())
                //              a
                (None, true)
              } else if (req.listFromQueryParams.get("X-Amz-Signature").isDefined) {
                //              val a = Stream(AuthV4Presigned()).map(_.run).find(_.isDefined).flatten
                //              a.isDefined.orFailWith(Error.SignatureDoesNotMatch())
                //              a
                (None, true)
              } else {
                (None, false)
              }

            val callerId: Option[String] = authResult match {
              case (None, true) =>
                Error.failWith(Error.SignatureDoesNotMatch())
                None
              case (a, _) => a.flatMap { x => users.getId(x) }
            }

            AuthorizedContext(tree, users, req, callerId, requestId).route
          }
        }
      }
    }
}

case class AuthorizedContext(tree: Tree,
                             users: UserTable,
                             req: HttpRequest,
                             callerId: Option[String],
                             requestId: String)
  extends RouteUtil
  with GetService
  with PutBucket
  with PutObject
  with GetObject
  {
    def doGetBucketLocation(bucketName: String) = complete("hoge")
    def doGetBucket(bucketName: String) = complete("hoge")
    def doListParts(bucketName: String, keyName: String, uploadId: String) = complete("hoge")
    def doListMultipartUploads(bucketName: String) = complete("hoge")
    def doUploadPart(bucketName: String, keyName: String, partNumber: Int, uploadId: String) = complete("hoge")
    def doUploadPartByCopy(bucketName: String, keyName: String) = complete("hoge")
    def doDeleteBucket(bucketName: String) = complete("hoge")
    def doAbortMultipartUpload(bucketName: String, keyName: String) = complete("hoge")
    def doDeleteObject(bucketName: String, keyName: String) = complete("hoge")
    def doDeleteMultipleObjects(bucketName: String) = complete("hoge")
    def doInitiateMultipartUpload(bucketName: String, keyName: String) = complete("hoge")
    def doCompleteMultipleUpload(bucketName: String, keyName: String, uploadId: String) = complete("hoge")
    def doHeadBucket(bucketName: String) = complete("hoge")

    val route =
      get {
        path("") {
          doGetService()
        }
      } ~
      get {
        extractBucket { bucketName =>
          parameter("location") { a =>
            doGetBucketLocation(bucketName)
          }
        }
      } ~
      get {
        extractBucket { bucketName =>
          doGetBucket(bucketName)
        }
      } ~
      get {
        extractObject { (bucketName, keyName) =>
          parameter("uploadId") { uploadId =>
            doListParts(bucketName, keyName, uploadId)
          }
        }
      } ~
      get {
        extractBucket { bucketName =>
          parameter("uploads") { a =>
            doListMultipartUploads(bucketName)
          }
        }
      } ~
      get {
        extractObject { (bucketName, keyName) =>
          doGetObject(bucketName, keyName)
        }
      } ~
      put {
        extractBucket { bucketName =>
          doPutBucket(bucketName)
        }
      } ~
      put {
        extractObject { (bucketName, keyName) =>
          parameters("partNumber".as[Int], "uploadId".as[String]) { (partNumber, uploadId) =>
            doUploadPart(bucketName, keyName, partNumber, uploadId)
          }
        }
      } ~
      put {
        extractObject { (bucketName, keyName) =>
          parameters("partNumber".as[Int], "uploadId".as[String]) { (partNumber, uploadId) =>
            headerValueByName("x-amz-copy-source") { copySource => // /bucketName/key
              doUploadPartByCopy(bucketName, keyName)
            }
          }
        }
      } ~
      put {
        extractObject { (bucketName, keyName) =>
          doPutObject(bucketName, keyName)
        }
      } ~
      delete {
        extractBucket { bucketName =>
          doDeleteBucket(bucketName)
        }
      } ~
      delete {
        extractObject { (bucketName, keyName) =>
          parameter("uploadId") { a =>
            doAbortMultipartUpload(bucketName, keyName)
          }
        }
      } ~
      delete {
        extractObject { (bucketName, keyName) =>
          doDeleteObject(bucketName, keyName)
        }
      } ~
      post {
        extractBucket { bucketName =>
          parameter("delete") { a =>
            doDeleteMultipleObjects(bucketName)
          }
        }
      } ~
      post {
        extractObject { (bucketName, keyName) =>
          parameter("uploads") { a =>
            doInitiateMultipartUpload(bucketName, keyName)
          }
        }
      } ~
      post {
        extractObject { (bucketName, keyName) =>
          parameter("uploadId") { uploadId =>
            doCompleteMultipleUpload(bucketName, keyName, uploadId)
          }
        }
      } ~
      head {
        extractBucket { bucketName =>
          doHeadBucket(bucketName)
        }
      } ~
      head {
        extractObject { (bucketName, keyName) =>
          // transparent-head-requests is off
          // Note that, even when this setting is off the server will never send
          // but message bodies on responses to HEAD requests.
          doGetObject(bucketName, keyName)
        }
      }
  }
