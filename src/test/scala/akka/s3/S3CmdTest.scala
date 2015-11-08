package akka.s3

import java.io.File
import java.nio.file.Paths

import scala.sys.process.Process

class S3CmdTest extends ServerTestBase(ServerConfig.forTest) {

  case class FixtureParam()
  override protected def withFixture(test: OneArgTest) = {
    test(FixtureParam())
  }

  def s3cmd(command: String) = {
    val config: File = getTestFile("s3cmd.cfg")
    val cmdLine = s"s3cmd --config=${config.getAbsolutePath} ${command}"
    println("***" + cmdLine)
    Process(cmdLine)
  }

  implicit class RaiseError(e: Int) {
    def orFail = {
      if (e != 0) {
        assert(false)
      }
    }
  }

  val dlfile = "/tmp/albero-s3-dlfile"

  test("put and get") { _ =>
    val f: File = getTestFile("test.txt")

    (s3cmd("mb s3://myb") !) orFail

    (s3cmd(s"put ${f.getAbsolutePath} s3://myb") !) orFail

    // s3cmd uses only the basename as the keyname
    Paths.get(dlfile).delete
    (s3cmd(s"get s3://myb/test.txt ${dlfile}") !) orFail

    (Process(s"cmp ${f.getAbsolutePath} ${dlfile}") !) orFail
  }

  test("upload and get") { _ =>
    val fn = "s3test-large.dat"
    mkRandomFile(Paths.get(s"/tmp/${fn}"), 32 * 1024 * 1024)

    (s3cmd("mb s3://myb") !) orFail

    (s3cmd(s"--multipart-chunk-size-mb=5 put /tmp/${fn} s3://myb") !) orFail

    Paths.get(dlfile).delete
    (s3cmd(s"get s3://myb/${fn} ${dlfile}") !) orFail

    (Process(s"cmp /tmp/${fn} ${dlfile}") !) orFail
  }
}
