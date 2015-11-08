package akka.s3

import scala.util.Try

class VersioningTest extends AmazonTestBase(ServerConfig.forTest) {

  test("should be not found") { p =>
    val cli = p.client
    cli.createBucket("buc")
    assert(Try{val o = cli.getObject("buc", "k")}.isFailure)

    val f = getTestFile("test.txt")
    cli.putObject("buc", "k", f)
    assert(Try{val o = cli.getObject("buc", "k")}.isSuccess)

    cli.deleteObject("buc", "k")
    assert(Try{val o = cli.getObject("buc", "k")}.isFailure)
  }

  //  test("should get delete marker") { p =>
  //    val cli = p.client
  //    cli.createBucket("buc")
  //
  //    val conf = new BucketVersioningConfiguration(BucketVersioningConfiguration.ENABLED)
  //    cli.setBucketVersioningConfiguration(new SetBucketVersioningConfigurationRequest("buc", conf))
  //
  //    val f = getTestFile("test.txt")
  //    cli.putObject("buc", "k", f)
  //
  //    val req = new DeleteObjectsRequest("buc")
  //    req.setKeys(List(new KeyVersion("k")))
  //    val result = cli.deleteObjects(req)
  //    val versionId = result.getDeletedObjects.get(0).getDeleteMarkerVersionId
  //
  //    val obj = cli.getObject(new GetObjectRequest("buc", "k", versionId))
  //    // TODO
  //  }
}
