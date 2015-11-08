package akka.s3

import java.nio.file.{Files, Paths}

import org.scalatest.FunSuite

class UtilTest extends FunSuite {

  test("condApply") {
    assert(1.applyIf(true) { a => a + 1 }  === 2)
    assert(1.applyIf(false) { a => a + 1 }  === 1)
  }

  test("option plus (<+)") {
    assert((Some(1) `<+` Some(2)) === Some(1))
    assert((Some(1) `<+` None) === Some(1))
    assert((None `<+` Some(2)) === Some(2))
    assert((None `<+` None) === None)
  }

  test("kvlist") {
    val bui = KVList.builder
    assert(bui.build.unwrap === Seq())
    assert(bui.append("k", Some("v")).build.unwrap === Seq(("k", "v")))
    assert(bui.append("k", None).build.unwrap === Seq(("k", "v")))
  }

  test("content-type probe") {
    val fn = getClass.getClassLoader.getResource("test.txt").getFile
    assert(Paths.get(fn).contentType === "text/plain")
  }

  test("content-type probe (img)") {
    val fn = getClass.getClassLoader.getResource("test.jpg").getFile
    assert(Paths.get(fn).contentType === "image/jpeg")
  }

  test("logged file") {
    val rootDir = Paths.get("/tmp/akka-s3-test")
    rootDir.mkdirp
    val path = rootDir.resolve("obj")
    val obj = LoggedFile(path)

    assert(obj.get === None)

    val d1 = "aaa".getBytes
    obj.put { path =>
      path.writeBytes(d1)
    }
    assert(obj.get.get.readBytes === d1)

    val d2 = "bbb".getBytes
    obj.put { path =>
      path.writeBytes(d2)
    }
    assert(obj.get.get.readBytes === d2)
  }
}
