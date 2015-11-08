package akka.s3

import akka.http.scaladsl.model.{ContentTypes, HttpRequest}
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.HmacUtils

case class AuthV2Common(req: HttpRequest, headers: HeaderList, getSecretKey: String => String) {

  // http://docs.aws.amazon.com/AmazonS3/latest/dev/RESTAuthentication.html
  val subresources = Set("cors", "acl", "lifecycle", "location", "logging", "notification", "partNumber", "policy", "requestPayment", "torrent", "uploadId", "uploads", "versionId", "versioning", "versions", "website")
  // response-* to override the response header
  val responseOverride = Set("response-content-type", "response-content-language", "response-expires", "response-cache-control", "response-content-disposition", "response-content-encoding")
  // and ?delete (for delete multiple objects)

  def computeSignature(stringToSign: String, secretKey: String) = {
    val a: Array[Byte] = HmacUtils.hmacSha1(secretKey.getBytes, stringToSign.getBytes("UTF-8"))
    Base64.encodeBase64String(a)
  }

  def stringToSign(dateOrExpire: String): String = {
    val contentType: String = req.entity.contentType match {
      case ContentTypes.NoContentType => ""
      case a => a.value
    }

    val s = httpVerb + "\n" +
      headers.get("content-md5").getOrElse("") + "\n" +
      contentType.toLowerCase + "\n" +
      dateOrExpire + "\n" +
      canonicalizedAmzHeaders +
      canonicalizedResource
    println(s)
    s
  }

  def httpVerb = {
    req.method.value
  }

  def canonicalizedAmzHeaders = {
    headers
      .filter(_.toLowerCase.startsWith("x-amz-"))
      .sortBy(_._1) // stable sort
      // TODO reduce [a->b, a->c] => a->b,c
      .map(a => s"${a._1.toLowerCase}:${a._2}\n")
      .mkString
  }

  // for bucket request we need slash like /bucketname/
  // and uri.path.toString returns so
  def canonicalizedResource: String = {
    val pathURI = req.uri.path.toString

    val l = req.uri.query()
      .sortBy(_._1)
      .filter { a =>
      val s = subresources ++ responseOverride ++ Set("delete")
      s.contains(a._1)
    }

    val paramsURI =
      if (l.isEmpty) {
        ""
      } else {
        "?" + l.map { a =>
          if (a._2 == "") {
            a._1
          } else {
            a._1 + "=" + a._2
          }
        }.mkString("&")
      }

    pathURI + paramsURI
  }
}
