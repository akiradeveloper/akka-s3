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

    // [spec]
    // To authenticate a request, you must use a valid AWS Access Key ID that is registered with Amazon S3. Anonymous requests cannot list buckets, and you cannot list buckets that you did not create.
    // so if the caller is anonymous? empty list?

    // [spec]
    // The GET operation on the Service endpoint (s3.amazonaws.com) returns a list of all of the buckets owned by the authenticated sender of the request.


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
