package akka.s3

import akka.http.scaladsl.model.HttpRequest

import scala.util.Try

case class AuthV2Presigned(req: HttpRequest, getSecretKey: String => String) extends Auth {

  def hl = HeaderList.Aggregate(Seq(req.listFromQueryParams, req.listFromHeaders))

  override def run = Try {
    require(computeSignature == signature)
    accessKey
  }.toOption

  def accessKey = hl.get("AWSAccessKeyId").get
  def expires = hl.get("Expires").get
  def signature = hl.get("Signature").get

  def computeSignature = {
    val a = AuthV2Common(req, hl, getSecretKey)
    val s = a.stringToSign(expires)
    a.computeSignature(s, getSecretKey(accessKey))
  }
}
