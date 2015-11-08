package akka.s3

import akka.http.scaladsl.model.HttpRequest

import scala.util.Try

case class AuthV2(req: HttpRequest, getSecretKey: String => String) extends Auth {

  override def run = Try {
    val hl = HeaderList.Aggregate(Seq(req.listFromHeaders, req.listFromQueryParams))
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
      (a, b, c)
    }
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
    val alg = AuthV2Common(req, hl, getSecretKey)
    val stringToSign = alg.stringToSign(date)
    require(alg.computeSignature(stringToSign, secretKey) == signature)
    accessKey
  }.toOption
}
