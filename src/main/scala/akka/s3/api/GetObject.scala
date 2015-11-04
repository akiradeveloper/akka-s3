package akka.s3


import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{RawHeader, `Last-Modified`, ETag}

import scala.collection.immutable
import akka.http.scaladsl.server.Directives._

trait GetObject { self: AuthorizedContext =>

  def doGetObject(bucketName: String, keyName: String) = {

    val o = tree
      .findBucket(bucketName).get
      .findKey(keyName).get
      .getCurrentVersion
    o.isDefined.orFailWith(Error.NoSuchKey())

    val version = o.get
    (!version.metaT.isDeleteMarker).orFailWith(Error.NoSuchKey()) // TODO

    // TODO
    // no possiblity that the object is delete marker
    // because it's not version-specified Get

    val hl = HeaderList.Aggregate(Seq(req.listFromHeaders, req.listFromQueryParams))

    val headers: immutable.Seq[HttpHeader] =
      ETag(version.metaT.eTag) +:
      `Last-Modified`(DateTime(version.data.lastModified.getTime)) +:
      RawHeaderList(
        (X_AMZ_REQUEST_ID, requestId),
        (X_AMZ_VERSION_ID, if (version.metaT.isVersioned) {o.get.id.toString} else {"null"})
      )
      .applySome(
        hl.get("response-content-language")
      ) { a => b => RawHeader("Content-Language", b) +: a }
      .applySome(
        hl.get("response-expires")
      ) { a => b => RawHeader("Expires", b) +: a }
      .applySome(
        hl.get("response-cache-control")
      ) { a => b => RawHeader("Cache-Control", b) +: a }
      .applySome(
        hl.get("response-content-disposition") `<+`
          version.metaT.attrs.get(CONTENT_DISPOSITION)
      ) { a => b => RawHeader(CONTENT_DISPOSITION, b) +: a }
      .applySome(
        hl.get("response-content-encoding")
      ) { a => b => RawHeader("Content-Encoding", b) +: a }

    // so this code is only for version-specified Get
    // x-amz-delete-marker:
    // If false, this response header does not appear in the response.
    // .applyIf(version.meta.isDeleteMarker) { a => RawHeader(X_AMZ_DELETE_MARKER, version.meta.isDeleteMarker.toString) +: a }


    // FIXME (but how to implement this?)
    // [spec] The storage size of a delete marker is equal to the size of the key name of the delete marker.

    // FIXME
    // [spec] If you try to get an object and its current version is a delete marker, Amazon S3 responds with:
    // - A 404 (Object not found) error
    // - A response header, x-amz-delete-marker: true
    //   This response header never returns false; if the value is false, Amazon S3 does not include this response header in the response.


    // We need to override the Content-Type because the returning byte stream
    // always results in octet-stream type, which client doesn't expect to be.
    val contentType =
      hl.get("response-content-type") `<+`
      version.metaT.attrs.get(CONTENT_TYPE) `<+`
      Some(version.data.contentType) // fallback

    val ct = ContentType.parse(contentType.get).right.get
    complete(StatusCodes.OK, headers, HttpEntity(ct, version.data.toFile, 1 << 20))
  }
}
