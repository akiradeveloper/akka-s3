package akka.s3

import org.scalatest.Suites

class AllTest extends Suites (
  new AmazonSDKTest,
  new VersioningTest,
  new McTest,
  new S3CmdTest,
  new AuthTest,
  new CorsTest,
  new CorsServerTest
)
