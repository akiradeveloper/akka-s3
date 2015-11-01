package akka.s3

import akka.http.scaladsl.model.headers.{ETag, HttpOrigin, `Access-Control-Allow-Origin`}
import akka.http.scaladsl.model.{HttpHeader, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._

import scala.collection.immutable

trait PutObject { self: AuthorizedContext =>
  def doPutObject(bucketName: String, key: String) {
    val k = tree
      .findBucket(bucketName).get
      .key(key)
    k.mk

    val v = k.acquireNewVersion

    Acl.File(callerId, Seq()).write(v.acl)

    v.data.writeBytes(req.entity.dataBytes)
    val newETag = v.data.computeMD5

//      val md5 = req.getHeaders.get("content-md5")
//      if (md5.isDefined) {
//        (md5.get == newETag).orFailWith(Error.BadDigest())
//      }

    val hl = HeaderList.Aggregate(Seq(req.listFromHeaders, req.listFromQueryParams))

    require(!v.meta.exists)
    Meta.File(
      isVersioned = false,
      isDeleteMarker = false,
      eTag = newETag,
      attrs = KVList.builder
        .append(CONTENT_TYPE, hl.get(CONTENT_TYPE))
        .append(CONTENT_DISPOSITION, hl.get(CONTENT_DISPOSITION))
        .build,
      xattrs = KVList.builder.build
    ).write(v.meta)
    assert(v.meta.exists)

    v.commit

    val headers =
      ETag(newETag) +:
      immutable.Seq (
        (X_AMZ_REQUEST_ID, requestId),
        (X_AMZ_VERSION_ID, "null")
      ).map(toRawHeader)
      // CORS: Actual request after preflight also contains Origin header
      // and the server should return Access-Control-Allow-Origin as well as response to preflight request.
      .applySome(hl.get("Origin")) { a => b => `Access-Control-Allow-Origin`(HttpOrigin(b)) +: a }

    complete(StatusCodes.OK, headers, HttpEntity.Empty)
  }
}
