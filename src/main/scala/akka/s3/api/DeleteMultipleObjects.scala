package akka.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import scala.util.Try
import scala.xml.NodeSeq

trait DeleteMultipleObjects { self: AuthorizedContext =>
  def doDeleteMultipleObjects(bucketName: String) = {
    case class Object(key: String, versionId: Option[Int])
    val objects0 = Try {
      val xml = XMLLoad.load(req.entity.dataBytes)
      // TODO Quite mode
      (xml \ "Object").map { o =>
        val key = (o \ "Key").text
        val versionId = (o \ "VersionId") match {
          case NodeSeq.Empty => None
          case a => Some(a.text.toInt)
        }
        Object(key, versionId)
      }
    }
    objects0.isSuccess.orFailWith(Error.MalformedXML())

    val objects: List[Object] = objects0.get.toList

    val b = tree.findBucket(bucketName).get

    sealed trait DeleteResult { def toXML: NodeSeq }
    case class DeletedResult(key: String,
                             // VersionId for the versioned object in the case of a versioned delete.
                             versionId: Option[Int],
                             // DeleteMarker:
                             // If a specific delete request either creates or deletes a delete marker,
                             // Amazon S3 returns this element in the response with a value of true.
                             // DeleteMarkerVersionId:
                             // If the specific delete request in the Multi-Object Delete either creates or deletes a delete marker,
                             // Amazon S3 returns this element in response with the version ID of the delete marker.
                             deleteMarker: Option[Int]
                              ) extends DeleteResult {
      override def toXML: NodeSeq = {
        <Deleted>
          <Key>{key}</Key>
          { versionId.noneOrSome(NodeSeq.Empty) { a => <VersionId>{a}</VersionId> } }
          { deleteMarker.noneOrSome(NodeSeq.Empty) { a => <DeleteMarker>true</DeleteMarker> } }
          { deleteMarker.noneOrSome(NodeSeq.Empty) { a => <DeleteMarkerVersionId>{a}</DeleteMarkerVersionId> } }
        </Deleted>
      }
    }
    case class FailedResult(key: String,
                            versionId: Option[Int] ,
                            code: String, // AccessDenied or InternalError
                            message: String
                             ) extends DeleteResult {
      override def toXML: NodeSeq = {
        <Error>
          <Key>{key}</Key>
          { versionId.map { a => <VersionId>{a}</VersionId> } }
          <Code>{code}</Code>
          <Message>{message}</Message>
        </Error>
      }
    }

    val results: List[DeleteResult] = objects.map { o =>
      val k = b.key(o.key)
      if (!k.path.exists) {
        FailedResult(k.getKeyName, None, "AccessDenied", "")
      } else {
        try {
          val res = k.delete(None)
          val r = DeletedResult(k.getKeyName, None, res)
          r
        } catch {
          case _: Throwable => FailedResult(k.getKeyName, None, "InternalError", "")
        }
      }
    }

    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID, requestId)
    )

    complete(
      StatusCodes.OK,
      headers,
      <DeleteResult>
        { for (result <- results) yield result.toXML }
      </DeleteResult>
    )
  }
}
