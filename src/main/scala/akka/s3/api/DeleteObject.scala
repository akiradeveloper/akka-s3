package akka.s3

import akka.http.scaladsl.server.Directives._

import akka.http.scaladsl.model.StatusCodes

import akka.http.scaladsl.model.HttpEntity

trait DeleteObject { self: AuthorizedContext =>
  def doDeleteObject(bucketName: String, keyName: String) = {
    val hl = req.listFromQueryParams

    val versionId: Option[Int] = hl.get("versionId").map(_.toInt)

    val k = tree
      .findBucket(bucketName).get
      .findKey(keyName).get

    val res = k.delete(versionId)

    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID, requestId),
      // x-amz-delete-marker:
      // Specifies whether the versioned object that was permanently deleted was (true) or was not (false) a delete marker.
      // In a simple DELETE, this header indicates whether (true) or not (false) a delete marker was created.
      (X_AMZ_DELETE_MARKER, res.isDefined.toString),
      (X_AMZ_VERSION_ID, "null") // newly created delele markers's version
    )

    complete(StatusCodes.OK, headers, HttpEntity.Empty)
  }
}
