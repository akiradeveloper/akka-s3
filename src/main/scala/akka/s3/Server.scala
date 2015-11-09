package akka.s3

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, ExceptionHandler}

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.util.Random

trait RouteUtil {
  val extractBucket = path(Segment ~ (Slash | PathEnd))
  val extractObject = path(Segment / Rest)
  def RawHeaderList(xs: (String, String)*) = {
    // headers should be immutable.Seq
    immutable.Seq(xs:_*).map(a => RawHeader(a._1, a._2))
  }
}

case class Server(config: ServerConfig)
  extends RouteUtil
  with AdminSupport
  with OptionsObject
{
  val tree = Tree(config.treePath)
  val users = UserTable(config.adminPath.resolve("db.sqlite"))
  def ackError(e: Error.t)(implicit req: HttpRequest, requestId: String) = {
    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID, requestId)
    )
    val o = Error.toCodeAndMessage(e)
    complete(
      o.code,
      headers,
      Error.mkXML(o, req.uri.path.toString(), requestId)
    )
  }
  def handler(implicit req: HttpRequest, requestId: String) = ExceptionHandler {
    // FIXME error should sometimes contains header info such as x-amz-delete-marker
    case Error.Exception(e) =>
      ackError(e)
    case e: Throwable =>
      e.printStackTrace
      ackError(Error.InternalError("unknown error"))
  }

  def doPostObject(req: HttpRequest, reqId: String) = complete(StatusCodes.NotImplemented)

  def extractCallerId(req: HttpRequest): Option[String] = {
    // TODO should be String => Option[String]
    val getSecretKey: String => String = (accessKey: String) => users.getId(accessKey).flatMap(users.getUser(_)).map(_.secretKey).get

    val authResult: (Option[String], Boolean) =
      if (req.listFromHeaders.get("Authorization").isDefined) {
        (Stream(AuthV2(req, getSecretKey)).map(_.run).find(_.isDefined).flatten, true)
      } else if (req.listFromQueryParams.get("Signature").isDefined) {
        (Stream(AuthV2Presigned(req, getSecretKey)).map(_.run).find(_.isDefined).flatten, true)
      } else if (req.listFromQueryParams.get("X-Amz-Signature").isDefined) {
        (None, true)
      } else {
        (None, false)
      }

    authResult match {
      case (None, true) =>
        Error.failWith(Error.SignatureDoesNotMatch())
        None
      case (a, _) => a.flatMap { x => users.getId(x) }
    }
  }

  val route =
    logRequestResult("") {
      adminRoute ~
      extractRequest { req =>
        val requestId = Random.alphanumeric.take(16).mkString
        handleExceptions(handler(req, requestId)) {
          doOptionsObject(req, requestId) ~
          // doPostObject(req, requestId) ~
          extractRequest { _ => // FIXME just to dynamically create the successive routing
            val callerId = extractCallerId(req)
            AuthorizedContext(this, tree, users, req, callerId, requestId).route
          }
        }
      }
    }
}

case class AuthorizedContext(server: Server,
                             tree: Tree,
                             users: UserTable,
                             req: HttpRequest,
                             callerId: Option[String],
                             requestId: String)
  extends RouteUtil
  with GetService
  with PutBucket
  with GetBucket
  with HeadBucket
  with PutObject
  with GetObject
  with DeleteObject
  with DeleteMultipleObjects
  with PutBucketCors
  with GetBucketLocation
  with InitiateMultipartUpload
  with UploadPart
  with ListParts
  with CompleteMultipartUpload
  {
    val NOTIMPL = StatusCodes.NotImplemented
    def doListMultipartUploads(bucketName: String) = complete(NOTIMPL)
    def doUploadPartByCopy(bucketName: String, keyName: String) = complete(NOTIMPL)
    def doDeleteBucket(bucketName: String) = complete(NOTIMPL)
    def doAbortMultipartUpload(bucketName: String, keyName: String) = complete(NOTIMPL)

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
          parameter("cors") { _ =>
            doPutBucketCors(bucketName)
          }
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
            doCompleteMultipartUpload(bucketName, keyName, uploadId)
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
