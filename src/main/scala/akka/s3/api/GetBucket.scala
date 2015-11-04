package akka.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import scala.collection.immutable
import scala.xml.NodeSeq

trait GetBucket { self: AuthorizedContext =>
  def doGetBucket(bucketName: String) = {

    sealed trait Group {
      def toXML: NodeSeq
      def lastKeyName: String
    }

    case class Contents(version: Version) extends Group {
      val acl = Acl.read(version.acl)
      val ownerId = acl.owner.get

      override def toXML = {
        <Contents>
          <Key>{version.getKeyName}</Key>
          <LastModified>{version.path.lastModified.format000Z}</LastModified>
          <ETag>{Meta.read(version.meta).eTag}</ETag>
          <Size>{version.data.length}</Size>
          <StorageClass>STANDARD</StorageClass>
          <Owner>
            <ID>{ownerId}</ID>
            <DisplayName>{users.getUser(ownerId).get.displayName}</DisplayName>
          </Owner>
        </Contents>
      }
      override def lastKeyName = version.getKeyName
    }
    // FIXME use non empty list
    case class CommonPrefixes(versions: List[Version], prefix: String) extends Group {
      override def toXML = {
        <CommonPrefixes>
          <Prefix>{prefix}</Prefix>
        </CommonPrefixes>
      }
      override def lastKeyName = {
        versions.last.getKeyName
      }
    }

    val params = req.listFromQueryParams
    val marker: Option[String] = params.get("marker")
    val prefix = params.get("prefix")
    val delimiter = params.get("delimiter")
    val maxKeys = params.get("max-keys").map(_.toInt)
    val encodingType = params.get("encoding-type")

    def computePrefix(s: String, delim: String): String = {
      val t = s.takeWhile(_ != delim)
      // pure key name doesn't end with '/'
      // a/a (-> a/)
      // a (-> a)
      val slash = if (t == s) { "" } else { "/" }
      t + slash
    }

    val groups: List[Group] = tree.findBucket(bucketName).get.listKeys
      .map(_.getCurrentVersion)
      .filter(_.isDefined).map(_.get)
      .filter{a => !Meta.read(a.meta).isDeleteMarker} // GetBucket shouldn't include ones with delete marker
      .sortBy(_.getKeyName)

      .applySome(marker) { a => b => a.dropWhile(_.getKeyName < b) }

      .applySome(prefix) { a => b => a.filter(_.getKeyName.startsWith(b)) }

      .map { a => Contents(a) }

      .applyIf(delimiter.isDefined) {
        a => a
//      val delim = delimiter.get
//      filteredByPrefix.groupBy(v => computePrefix(v.getKeyName, delim)).toList // [prefix, versions]
//      .map { x => x match {
//        case List(v) => Contents(v)
//        case _ =>
//      } }
      }

    val len: Int = maxKeys match {
      case Some(a) => a
      case None => 1000 // default
    }

    val truncated = groups.size > len

    val nextMarker = if (truncated) {
      Some(groups.last.lastKeyName)
    } else {
      None
    }

    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID, requestId)
    )

    complete(
      StatusCodes.OK,
      headers,
      <ListBucketResult>
        <Name>{bucketName}</Name>
        { prefix.noneOrSome(NodeSeq.Empty) { a => <Prefix>{a}</Prefix> } }
        { marker.noneOrSome(NodeSeq.Empty) { a => <Marker>{a}</Marker> } }
        { maxKeys.noneOrSome(NodeSeq.Empty) { a => <MaxKeys>{a}</MaxKeys> } }
        <IsTruncated>{truncated}</IsTruncated>
        { for (g <- groups) yield g.toXML }
      </ListBucketResult>
    )
  }
}
