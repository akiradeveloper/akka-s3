package functional.s3

import org.scalatest.FunSuite

class CorsTest extends FunSuite {

  import Cors._

  val rules = Seq(
    Rule(
      origins = Seq(AllowedOrigin("hoge.com")),
      allowedMethods = Seq(AllowedMethod("POST"), AllowedMethod("PUT")),
      allowedHeaders = Seq(AllowedHeader("Content-Type")),
      exposeHeaders = List(),
      maxAgeSeconds = None
    ),
    Rule(
      origins = Seq(AllowedOrigin("*")),
      allowedMethods = Seq(AllowedMethod("GET"), AllowedMethod("LIST")),
      allowedHeaders = List(),
      exposeHeaders = List(),
      maxAgeSeconds = None
    )
  )

  test("fail (bad origin)") {
    assert(Cors.tryMatch(rules, Cors.Request(
      AllowedOrigin("hige.com"),
      AllowedMethod("PUT"),
      List())) === None)
  }

  test("fail (bad method)") {
    assert(Cors.tryMatch(rules, Cors.Request(
      AllowedOrigin("hoge.com"),
      AllowedMethod("DELETE"),
      List())) === None)
  }

  test("fail (bad headers)") {
    assert(Cors.tryMatch(rules, Cors.Request(
      AllowedOrigin("hoge.com"),
      AllowedMethod("PUT"),
      Seq(AllowedHeader("amz-x-requist-id")))) === None)
  }

  test("pass (exact match)") {
    assert(Cors.tryMatch(rules, Cors.Request(
      AllowedOrigin("hoge.com"),
      AllowedMethod("PUT"),
      Seq(AllowedHeader("Content-Type")))) !== None)
  }

  test("pass (wildcard origin)") {
    assert(Cors.tryMatch(rules, Cors.Request(
      AllowedOrigin("hige.com"),
      AllowedMethod("GET"),
      Seq())) !== None)
  }
}
