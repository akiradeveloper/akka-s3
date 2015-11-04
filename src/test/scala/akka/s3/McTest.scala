package akka.s3

import java.nio.file.Paths

import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.HttpClients

import scala.sys.process.Process
import scala.xml.XML

class McTest extends ServerTestBase(ServerConfig.forTest) {
  val alias = "akka-s3"
  val confDir = "/tmp/akka-s3-test-mc-config"

  implicit class RaiseError(e: Int) {
    def orFail = {
      if (e != 0) {
        assert(false)
      }
    }
  }

  def mc(cmd: String) = {
    val cmdline = s"mc --config-folder=${confDir} ${cmd}"
    println(cmdline)
    Process(cmdline)
  }

  override def beforeEach() {
    super.beforeEach()

    val url = s"http://localhost:9000/admin/user"
    val httpCli = HttpClients.createDefault
    val method = new HttpPost(url.toString)
    val res = httpCli.execute(method)

    val xml = XML.load(res.getEntity.getContent)
    val newUser = User.fromXML(xml)
    println(newUser)

    Paths.get(confDir).mkdirp
    Paths.get(confDir).emptyDirectory

    (mc(s"config host add localhost:9000 ${newUser.accessKey} ${newUser.secretKey} S3v2") !) orFail

    (mc(s"config alias add ${alias} http://localhost:9000") !) orFail
  }

  case class FixtureParam()
  override protected def withFixture(test: OneArgTest) = {
    test(FixtureParam())
  }

  test("add buckets") { _ =>

    // a bucket name too short is rejected by mc => OK

    // TODO
    // bucket name with dots is rejected => huh?
    // bucket name with slashes like "abc/def" is rejected => I am depressed

    assert((mc(s"ls ${alias}") !!).split("\n").length === 1) // not sure

    (mc(s"mb ${alias}/abc") !) orFail

    assert((mc(s"ls ${alias}") !!).split("\n").length === 1)

    (mc(s"mb ${alias}/DEF") !) orFail

    assert((mc(s"ls ${alias}") !!).split("\n").length === 2)
  }

  test("cp file and cat") { _ =>
    (mc(s"mb ${alias}/abc") !) orFail
    val f = getTestFile("test.txt")

    // by dir dest
    (mc(s"--quiet cp ${f.getAbsolutePath} ${alias}/abc") !) orFail

    assert((mc(s"cat ${alias}/abc/test.txt") !!).trim === "We love Scala!")

    // by explicit dest name
    (mc(s"--quiet cp ${f.getAbsolutePath} ${alias}/abc/test2.txt") !) orFail

    assert((mc(s"cat ${alias}/abc/test2.txt") !!).trim === "We love Scala!")
  }

  test("share (presigned get)") { _ =>
    (mc(s"mb ${alias}/abc") !) orFail
    val f = getTestFile("test.txt")
    (mc(s"--quiet cp ${f.getAbsolutePath} ${alias}/abc") !) orFail

    val httpCli = HttpClients.createDefault
    val url: String = (mc(s"share ${alias}/abc/test.txt") !!).split("\n")(1).split("URL: ")(1)

    val method = new HttpGet(url)
    val contents: String = IOUtils.toString(httpCli.execute(method).getEntity.getContent).trim
    assert(contents === "We love Scala!")
  }

  test("diff") { _ =>
    val f = getTestFile("test.txt")

    (mc(s"mb ${alias}/abc") !) orFail

    (mc(s"--quiet cp ${f.getAbsolutePath} ${alias}/abc") !) orFail

    (mc(s"mb ${alias}/def") !) orFail

    (mc(s"--quiet cp ${f.getAbsolutePath} ${alias}/def") !) orFail

    (mc(s"diff ${f.getAbsolutePath} ${alias}/abc/test.txt") !) orFail

    (mc(s"diff ${alias}/abc/test.txt ${alias}/def/test.txt") !) orFail
  }
}
