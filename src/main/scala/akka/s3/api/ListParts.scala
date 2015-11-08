package akka.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import scala.xml.NodeSeq

trait ListParts { self: AuthorizedContext =>
  def doListParts(bucketName: String, keyName: String, uploadId: String) = {
    val params = req.listFromQueryParams

    val partNumberMarker = params.get("part-number-marker")

    val startNumber = partNumberMarker match {
      case Some(a) => a.toInt
      case None => 0
    }

    val maxParts = params.get("max-parts")
    val listMaxLen = maxParts match {
      case Some(a) => a.toInt
      case None => 1000
    }

    val upload = tree
      .findBucket(bucketName).get
      .findKey(keyName).get
      .findUpload(uploadId).get

    val emitList0 = upload.listParts
      .dropWhile (_.id < startNumber)
      .filter(_.completed)

    val truncated = emitList0.size > listMaxLen

    val emitList = emitList0.take(listMaxLen)

    val nextPartNumberMarker = emitList.lastOption match {
      case Some(a) => a.id
      case None => 0
    }

    val acl = Acl.read(upload.acl)

    val ownerId = acl.owner.get // FIXME

    // If the initiator is an AWS account, this element provides the same information as the Owner element.
    // If the initiator is an IAM User, then this element provides the user ARN and display name.
    val initiatorId = ownerId

    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID, requestId)
    )

    complete (
      StatusCodes.OK,
      headers,
      <ListPartsResult>
        <Bucket>{bucketName}</Bucket>
        <Key>{keyName}</Key>
        <UploadId>{uploadId}</UploadId>
        <Initiator>
          <ID>{initiatorId}</ID>
          <DisplayName>{users.getUser(initiatorId).get.displayName}</DisplayName>
        </Initiator>
        <Owner>
          <ID>{ownerId}</ID>
          <DisplayName>{users.getUser(ownerId).get.displayName}</DisplayName>
        </Owner>
        <StorageClass>STANDARD</StorageClass>
        { partNumberMarker.noneOrSome(NodeSeq.Empty){a => <PartNumberMarker>{a}</PartNumberMarker>} }
        <NextPartNumberMarker>{nextPartNumberMarker}</NextPartNumberMarker>
        { maxParts.noneOrSome(NodeSeq.Empty)(a => <MaxParts>{a}</MaxParts>) }
        <IsTruncated>{truncated}</IsTruncated>
        { for (part <- emitList) yield
        <Part>
          <PartNumber>{part.id}</PartNumber>
          <LastModified>{part.data.lastModified.format000Z}</LastModified>
          <ETag>{part.data.computeMD5}</ETag>
          <Size>{part.data.length}</Size>
        </Part>
        }
      </ListPartsResult>
    )
  }
}
