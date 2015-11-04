package akka.s3

import org.scalatest.Suites

class AllTest extends Suites (
  new AmazonSDKTest,
  new McTest,
  new AuthTest,
  new CorsTest,
  new CorsServerTest
)
