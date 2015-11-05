package akka.s3

import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpRequest}
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.HmacUtils

import scala.util.Try

case class AuthV2(req: HttpRequest, getSecretKey: String => String) extends Auth {

  val hl = HeaderList.Aggregate(Seq(req.listFromHeaders, req.listFromQueryParams))

  val alg = AuthV2Common(req, hl, getSecretKey)

  override def run = Try {
    val (_, accessKey, signature) = splitSig
    val secretKey = getSecretKey(accessKey)
    val date = {
      val amzDate = hl.get("x-amz-date")
      if (amzDate.isDefined) {
        // [spec]
        // If you include the x-amz-date header, you must still include
        // a newline character in the canonicalized string at the point
        // where the Date value would normally be inserted.

        // [spec]
        // When an x-amz-date header is present in a request,
        // the system will ignore any Date header when computing the request signature.
        // Therefore, if you include the x-amz-date header,
        // use the empty string for the Date when constructing the StringToSign.
        ""
      } else {
        // We don't need to getOrElse but can just get
        // [spec]
        // A valid time stamp (using either the HTTP Date header or an x-amz-date alternative)
        // is mandatory for authenticated requests.
        hl.get("date").get
      }
    }
    val stringToSign = alg.stringToSign(date)
    require(alg.computeSignature(stringToSign, secretKey) == signature)
    accessKey
  }.toOption

  // AWS AccessKey:Signature
  def splitSig: (String, String, String) = {
    val v = hl.get("authorization").getOrElse("BANG!")
    val xs = v.split(" ")
    val a = xs(0)
    require(a == "AWS")
    val ys = xs(1).split(":")
    val b = ys(0)
    require(b != "")
    val c = ys(1)
    require(c != "")
    (a,b,c)
  }
}

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

    val l = req.uri.query
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