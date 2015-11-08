package akka.s3

import akka.http.scaladsl.model.HttpRequest

import scala.util.Try

case class AuthV2Presigned(req: HttpRequest, getSecretKey: String => String) extends Auth {

  val hl = HeaderList.Aggregate(Seq(req.listFromQueryParams, req.listFromHeaders))

  override def run = Try {
    val accessKey = hl.get("AWSAccessKeyId").get
    val expires = hl.get("Expires").get
    val signature = hl.get("Signature").get
    val alg = AuthV2Common(req, hl, getSecretKey)
    val stringToSign = alg.stringToSign(expires)
    val computed = alg.computeSignature(stringToSign, getSecretKey(accessKey))
    require(computed == signature)
    accessKey
  }.toOption
}
