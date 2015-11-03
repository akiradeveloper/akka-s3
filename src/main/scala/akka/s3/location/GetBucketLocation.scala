package akka.s3

import akka.http.scaladsl.model.StatusCodes
import scala.collection.immutable
import akka.http.scaladsl.server.Directives._

trait GetBucketLocation { self: AuthorizedContext =>



  def doGetBucketLocation(bucketName: String) = {
    val headers = RawHeaderList(
      (X_AMZ_REQUEST_ID, requestId)
    )

    // TODO (location isn't supported)
    complete(
      StatusCodes.OK,
      headers,
      <LocationConstraint></LocationConstraint>
    )
  }
}
