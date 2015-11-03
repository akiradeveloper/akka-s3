package akka.s3

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import scala.collection.immutable

trait GetService extends ScalaXmlSupport { self: AuthorizedContext =>
  def doGetService() = {
    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID, requestId)
    )

    // FIXME if the user is public?

    // FIXME
    // only ones belong to the caller will be listed

    // FIXME callerId is Option
    val a = tree.listBuckets
    complete (
      StatusCodes.OK,
      headers,
      <ListAllMyBucketsResult>
        <Owner>
          <ID>{callerId.get}</ID>
          <DisplayName>{users.getUser(callerId.get).get.displayName}</DisplayName>
        </Owner>
        <Buckets>
          { for (b <- tree.listBuckets if b.completed) yield
          <Bucket>
            <Name>{b.path.lastName}</Name>
            <CreationDate>{b.path.lastModified.format000Z}</CreationDate>
          </Bucket>
          }
        </Buckets>
      </ListAllMyBucketsResult>
    )
  }
}
