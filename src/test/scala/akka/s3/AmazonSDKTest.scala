package akka.s3

import scala.collection.JavaConversions._

class AmazonSDKTest extends AmazonTestBase(ServerConfig.forTest) {

  test("add buckets") { p =>
    val cli = p.client
    cli.createBucket("mybucket1")
    cli.createBucket("a.b")
    val res = cli.listBuckets
    assert(res.forall(_.getOwner.getId === TestUsers.hoge.id))
    assert(Set(res(0).getName, res(1).getName) === Set("mybucket1", "a.b"))
    assert(res.size === 2)

    // location
    val loc = cli.getBucketLocation("mybucket1")
    assert(loc == "US")
  }

  test("put and get object") { p =>
    val cli = p.client
    cli.createBucket("a.b")
    val f = getTestFile("test.txt")
    val putRes = cli.putObject("a.b", "myobj.txt", f)
    assert(putRes.getVersionId === "null")

    val obj1 = cli.getObject("a.b", "myobj.txt")
    checkFileContent(obj1, f)
    // TODO check result

    // test second read
    val obj2 = cli.getObject("a.b", "myobj.txt")
    checkFileContent(obj2, f)
  }
}
