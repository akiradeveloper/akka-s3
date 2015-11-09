package akka.s3

import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Await, Future}
import scala.util.{Success, Try}
import scala.xml.{NodeSeq, XML}

import akka.http.scaladsl.server.Directives._

import java.net.URLEncoder

trait CompleteMultipartUpload { self: AuthorizedContext =>
  def doCompleteMultipartUpload(bucketName: String, keyName: String, uploadId: String) = {
    case class Part(partNumber: Int, eTag: String)

    val upload = tree
      .findBucket(bucketName).get
      .findKey(keyName).get
      .findUpload(uploadId).get

    // it's not of type application/xml so spray's unmarshaller crashes
    // val xml: NodeSeq = Unmarshaller.unmarshalUnsafe[NodeSeq](req.entity)
    val parts0 = Try {
      val xml = XMLLoad.load(req.entity.dataBytes)
      assert(xml != null)

      (xml \ "Part").map { x =>
        Part(
          (x \ "PartNumber").text.toInt,
          (x \ "ETag").text.replace("\"", "") // ETag includes double quotes
        )
      }
    }
    parts0.isSuccess.orFailWith(Error.MalformedXML())

    val parts = parts0.get

    // The parts are guaranteed to be sorted
    (parts == parts.sortBy(_.partNumber)).orFailWith(Error.InvalidPartOrder())

    val lastNumber = parts.last.partNumber

    for (Part(partNumber, eTag) <- parts) {
      val uploadedPart = upload.partById(partNumber)

      (eTag == uploadedPart.data.computeMD5).orFailWith(Error.InvalidPart())

      // Each part must be at least 5 MB in size, except the last part.
      if (partNumber < lastNumber &&
        uploadedPart.data.length < (5 << 20)) {
        Error.failWith(Error.EntityTooSmall())
      }
    }

    // http://stackoverflow.com/questions/12186993/what-is-the-algorithm-to-compute-the-amazon-s3-etag-for-a-file-larger-than-5gb
    def calcETag(md5s: Seq[String]): String = {
      val bui = new StringBuilder
      for (md5 <- md5s) {
        bui.append(md5)
      }
      val hex = bui.toString
      val raw = BaseEncoding.base16.decode(hex.toUpperCase)
      val hasher = Hashing.md5.newHasher
      hasher.putBytes(raw)
      val digest = hasher.hash.toString
      digest + "-" + md5s.size
    }
    val newETag = calcETag(parts.map(_.eTag))

    val mergeFut = Future {
      upload.mergeParts(parts.map(_.partNumber))
      Meta.read(upload.meta).copy(eTag = newETag).write(upload.meta)
      upload.completeMerging
    }

    // send whitespaces until background merging is complete
    val whitespaces: Stream[String] = Stream.continually {
      " "
    }.takeWhile{ _ =>
      val b = mergeFut.isCompleted
      // sleep 1s if it still in progress
      if (!b) {
        Thread.sleep(1000)
      }
      !b
    }

    val successContents: Stream[String] =
      <CompleteMultipartUploadResult>
        <Location>{s"http://${server.config.ip}:${server.config.port}/${bucketName}/${URLEncoder.encode(keyName)}"}</Location>
        <Bucket>{bucketName}</Bucket>
        <Key>{keyName}</Key>
        <ETag>{newETag}</ETag>
      </CompleteMultipartUploadResult>
        .toString.toStream.map(_.toString)

    // The error could be reported after sending 200 OK
    val failureContents: Stream[String] =
      Error.mkXML(Error.toCodeAndMessage(Error.InternalError("merging failed")),
                  req.uri.path.toString(),
                  requestId)
        .toString.toStream.map(_.toString)

    val contents: Stream[String] = mergeFut.value.get match {
      case Success(_) => successContents
      case _ => failureContents
    }

    // x-amz-version-id
    // Version ID of the newly created object, in case the bucket has versioning turned on.
    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID, requestId),
      (X_AMZ_VERSION_ID, "null")
    )

    complete(
      HttpResponse(StatusCodes.OK, headers, HttpEntity.CloseDelimited(
        MediaTypes.`application/xml`, // FIXME not sure..
        Source(whitespaces #::: contents).map(ByteString(_))
      )))
  }
}
