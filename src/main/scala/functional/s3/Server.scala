package functional.s3

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

case class Server(config: ServerConfig) {
  val extractBucket = path(Segment ~ (Slash | PathEnd))
  val extractObject = path(Segment / Rest)

  def doGetService() = complete("hoge")
  def doGetBucketLocation(bucketName: String) = complete("hoge")
  def doGetBucket(bucketName: String) = complete("hoge")
  def doListParts(bucketName: String, keyName: String, uploadId: String) = complete("hoge")
  def doListMultipartUploads(bucketName: String) = complete("hoge")
  def doGetObject(bucketName: String, keyName: String) = complete("hoge")
  def doPutBucket(bucketName: String) = complete("hoge")
  def doUploadPart(bucketName: String, keyName: String, partNumber: Int, uploadId: String) = complete("hoge")
  def doUploadPartByCopy(bucketName: String, keyName: String) = complete("hoge")
  def doPutObject(bucketName: String, keyName: String) = complete("hoge")
  def doDeleteBucket(bucketName: String) = complete("hoge")
  def doAbortMultipartUpload(bucketName: String, keyName: String) = complete("hoge")
  def doDeleteObject(bucketName: String, keyName: String) = complete("hoge")
  def doDeleteMultipleObjects(bucketName: String) = complete("hoge")
  def doInitiateMultipartUpload(bucketName: String, keyName: String) = complete("hoge")
  def doCompleteMultipleUpload(bucketName: String, keyName: String, uploadId: String) = complete("hoge")
  def doHeadBucket(bucketName: String) = complete("hoge")
  def doHeadObject(bucketName: String, keyName: String) = complete("hoge")

  val users = UserTable(config.adminPath)

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
        doHeadObject(bucketName, keyName)
      }
    }
}
