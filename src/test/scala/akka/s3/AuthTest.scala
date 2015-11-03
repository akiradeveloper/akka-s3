package akka.s3

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.HttpRequest
import org.scalatest.FunSuite

import akka.http.scaladsl.model.HttpProtocols._
import akka.http.scaladsl.model.HttpMethods._

class AuthTest extends FunSuite {
  val accessKey = "44CF9590006BF252F707"
  val secretKey = "OtxrzxIsfpFjA7SwPzILwy8Bw21TLhquhboDYROV"

  test("example1") {
    val req =
      HttpRequest(
        method = PUT,
        uri = "/quotes/nelson",
        protocol = `HTTP/1.0`,
        headers = List(
          RawHeader("Content-Md5", "c8fdb181845a4ca6b8fec737b3581d76"),
          RawHeader("Content-Type", "text/html"),
          RawHeader("Date", "Thu, 17 Nov 2005 18:49:58 GMT"),
          RawHeader("X-Amz-Meta-Author", "foo@bar.com"),
          RawHeader("X-Amz-Magic", "abracadabra"),
          RawHeader("Authorization", s"AWS ${accessKey}:jZNOcbfWmD/A/f3hSvVzXZjM2HU=")
        )
      )

    val auth = AuthV2(req, Map(accessKey -> secretKey))
    assert(auth.run.isDefined)
  }

  test("example2") {
    val req =
      HttpRequest(
        method = GET,
        uri = "/quotes/nelson",
        protocol = `HTTP/1.0`,
        headers = List(
          RawHeader("Date", "XXXXXXXXX"),
          RawHeader("X-Amz-Magic", "abracadabra"),
          RawHeader("X-Amz-Date", "Thu, 17 Nov 2005 18:49:58 GMT"),
          RawHeader("Authorization", s"AWS ${accessKey}:5m+HAmc5JsrgyDelh9+a2dNrzN8=")
        )
      )

    val auth = AuthV2(req, Map(accessKey -> secretKey))
    assert(auth.run.isDefined)
  }

  test("example3") {
    val req =
      HttpRequest(
        method = GET,
        uri = "/quotes/nelson?AWSAccessKeyId=44CF9590006BF252F707&Expires=1141889120&Signature=vjbyPxybdZaNmGa%2ByT272YEAiv4%3D",
        protocol = `HTTP/1.0`
      )

    val auth = AuthV2Presigned(req, Map(accessKey -> secretKey))
    assert(auth.run.isDefined)
  }

  test("actual case1: bucket path") {
    val req =
      HttpRequest(
        method = PUT,
        uri = "/mybucket1/",
        protocol = `HTTP/1.1`,
        headers = List(
          RawHeader("Authorization", "AWS myid:EBrN3wP3EVWxYf3UhxVeBeVFFYI="),
          RawHeader("Date", "Thu, 20 Aug 2015 07:31:47 GMT"),
          RawHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        )
      )

    val auth = AuthV2(req, Map("myid" -> "mykey"))
    assert(auth.run.isDefined)
  }

  test("actual case2: multipart download") {
    val req =
      HttpRequest(
        method = HEAD,
        uri = "/mybucket/myobj",
        protocol = `HTTP/1.1`,
        headers = List(
          RawHeader("Authorization", "AWS myid:iYqQ5KhDi0cHGUTeRSFpdVbaQIc="),
          RawHeader("Date", "Tue, 01 Sep 2015 04:11:42 GMT"),
          RawHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        )
      )

    val auth = AuthV2(req, Map("myid" -> "mykey"))
    assert(auth.run.isDefined)
  }
}
