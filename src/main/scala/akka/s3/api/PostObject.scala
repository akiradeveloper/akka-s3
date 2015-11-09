package akka.s3

import java.net.URLEncoder

import akka.http.scaladsl.model.headers.{HttpOrigin, `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials`, ETag}
import akka.http.scaladsl.model.{StatusCodes, HttpEntity, Multipart, HttpRequest}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._

import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

trait PostObject { self: PreAuthContext =>

  def doPostObject() = {
    post {
      extractBucket { bucketName =>
        formField("key") { keyName =>
          internal(bucketName, keyName)
        }
      }
    }
  }

  def internal(bucketName: String, keyName: String) = {
    entity(as[Multipart.FormData]) { mfd =>
      // Post Object uses fields to send header data

      // TODO signature v4
      val fdl = HeaderList.FromMultipart(mfd)

      val hl = req.listFromHeaders
      val origin = hl.get("Origin")

      val allHl = HeaderList.Aggregate(Seq(fdl, hl))

      val policy = fdl.get("Policy")
      val successActionStatus = fdl.get("success_action_status").map(_.toInt)

      // "acl"?,
      // "Cache-Control"?,
      // "Content-Type"?,
      // "Content-Disposition"?,
      // "Content-Encoding"?,
      // "Expires"?,

      val callerId: Option[String] = if (policy.isDefined) {
        val getSecretKey = (accessKey: String) => users.getId(accessKey).flatMap(users.getUser(_)).map(_.secretKey).get
        val authKey = Stream(PostAuthV2(fdl, getSecretKey)).map(_.run).find(_.isDefined).flatten
        authKey.isDefined.orFailWith(Error.SignatureDoesNotMatch())
        users.getId(authKey.get)
      } else {
        None
      }

      val k = tree
        .findBucket(bucketName).get
        .key(keyName)
      k.mk

      val v = k.acquireNewVersion

      Acl.File(callerId, Seq()).write(v.acl)

//      val writeFut: Future[Unit] = mfd.parts.runForeach { part =>
//        println(part.name)
//        if (part.name == "file") {
//          v.data.writeBytes(part.entity.dataBytes)
//        }
//      }
//      Await.ready(writeFut, Duration.Inf)
      println(fdl.bytes)
      v.data.writeBytes(fdl.bytes.toArray)
      val newETag = v.data.computeMD5
      // TODO check

      // val usrMeta = fd.fields.filter { case (k,_) => k.startsWith("x-amz-meta-") }

      Meta.File(
        isVersioned = false,
        isDeleteMarker = false,
        eTag = newETag,
        attrs = KVList.builder
          .append(CONTENT_TYPE, allHl.get(CONTENT_TYPE))
          .append(CONTENT_DISPOSITION, allHl.get(CONTENT_DISPOSITION))
          .build,
        xattrs = KVList.builder.build
      ).write(v.meta)

      v.commit

      val headers =
        ETag(newETag) +: // not sure required
        `Access-Control-Allow-Credentials`(true) +:
        RawHeaderList(
          (X_AMZ_REQUEST_ID, requestId)
        )
        .applySome(origin) { a => b => `Access-Control-Allow-Origin`(HttpOrigin(b)) +: a }

      // Location is URL-encoded unlike Key isn't.
      // From out analysis of the actual http response, Location is found encoded.
      val xml =
        <PostResponse>
          <Bucket>{bucketName}</Bucket>
          <Key>{keyName}</Key>
          <ETag>{newETag}</ETag>
          <Location>{s"http://${server.config.ip}:${server.config.port}/${bucketName}/${URLEncoder.encode(keyName)}"}</Location>
        </PostResponse>

      // If the value is set to 200 or 204, Amazon S3 returns an empty document with a 200 or 204 status code.
      // If the value is set to 201, Amazon S3 returns an XML document with a 201 status code.
      // If the value is not set or if it is set to an invalid value, Amazon S3 returns an empty document with a 204 status code.
      val code = successActionStatus.getOrElse(204)
      code match {
        case 200 | 204 => complete(code, headers, HttpEntity.Empty)
        case 201 => complete(code, headers, xml)
        case _ => complete(StatusCodes.Forbidden) // FIXME
      }
    }
  }
}
