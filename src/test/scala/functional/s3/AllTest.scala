package functional.s3

import org.scalatest.Suites

class AllTest extends Suites (
  new AmazonSDKTest,
  new AuthTest,
  new CorsTest,
  new CorsServerTest
)
