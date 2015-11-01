package functional.s3

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import scala.collection.immutable

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._

trait GetService { self: AuthorizedContext =>
  def doGetService() = {
    val headers = immutable.Seq(
      (X_AMZ_REQUEST_ID, requestId)
    ).map(toRawHeader)

    // FIXME
    // only ones belong to the caller will be listed
    complete (
      StatusCodes.OK,
      headers,
      <ListAllMyBucketsResult>
        <Owner>
          <ID>{callerId}</ID>
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
