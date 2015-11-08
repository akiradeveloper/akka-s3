package akka.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

trait InitiateMultipartUpload { self: AuthorizedContext =>
  def doInitiateMultipartUpload(bucketName: String, keyName: String) = {
    val k = tree
      .findBucket(bucketName).get
      .key(keyName)
    k.mk

    val upload = k.acquireNewUpload

    Acl.File(callerId, Seq()).write(upload.acl)

    Meta.File(
      isVersioned = false,
      isDeleteMarker = false,
      eTag = "",
      attrs = KVList.builder.build,
      xattrs = KVList.builder.build
    ).write(upload.meta)

    upload.completeInitiation

    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID, requestId)
    )

    complete(
      StatusCodes.OK,
      headers,
      <InitiateMultipartUploadResult>
        <Bucket>{bucketName}</Bucket>
        <Key>{keyName}</Key>
        <UploadId>{upload.id}</UploadId>
      </InitiateMultipartUploadResult>
    )
  }
}
