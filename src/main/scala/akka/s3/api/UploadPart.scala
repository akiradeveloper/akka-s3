package akka.s3

import akka.http.scaladsl.model.{StatusCodes, HttpEntity}
import akka.http.scaladsl.model.headers.ETag
import akka.http.scaladsl.server.Directives._

trait UploadPart { self: AuthorizedContext =>
  def doUploadPart(bucketName: String, keyName: String, partId: Int, uploadId: String) = {
    val upload = tree
      .findBucket(bucketName).get
      .findKey(keyName).get
      .findUpload(uploadId).get
    val part = upload.acquirePart(partId)
    part.write(req.entity.dataBytes)
    val headers =
      ETag(part.data.computeMD5) +:
      RawHeaderList(
        (X_AMZ_REQUEST_ID, requestId)
      )
    complete(StatusCodes.OK, headers, HttpEntity.Empty)
  }
}
