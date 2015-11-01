package functional.s3

import akka.http.scaladsl.model.{HttpEntity, StatusCodes}

import scala.collection.immutable
import akka.http.scaladsl.server.Directives._

trait PutBucket { self: AuthorizedContext =>
  def doPutBucket(bucketName: String) {
    val b = tree.bucket(bucketName)
    b.path.exists.not.orFailWith(Error.BucketAlreadyExists())
    b.mk

    Acl.File(callerId, List()).write(b.acl)

    Versioning.File(Versioning.UNVERSIONED).write(b.versioning)

    b.commit

    // TODO? xml in request. We don't support region and v4?
    //
    // If you send your create bucket request to the s3.amazonaws.com endpoint, the request go to the us-east-1 region.
    // Accordingly, the signature calculations in Signature Version 4 must use us-east-1 as region,
    // even if the location constraint in the request specifies another region where the bucket is to be created.

    val headers = immutable.Seq(
      (X_AMZ_REQUEST_ID, requestId)
    ).map(toRawHeader)

    complete(StatusCodes.OK, headers, HttpEntity.Empty)
  }
}
