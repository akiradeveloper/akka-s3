package functional.s3

import java.io.File
import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.scalatest.{fixture, BeforeAndAfterEach}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

abstract class ServerTestBase(genConfig: => ServerConfig) extends fixture.FunSuite with BeforeAndAfterEach {

  def getTestFile(name: String): File = {
    val loader = getClass.getClassLoader
    new File(loader.getResource(name).getFile)
  }

  def mkRandomFile(f: Path, len: Int): Unit = {
    if (Files.exists(f) && Files.size(f) == len) {
      return
    }
    val writer = Files.newOutputStream(f)
    val bytes = new Array[Byte](len)
    Random.nextBytes(bytes)
    writer.write(bytes)
    writer.close
  }

  implicit var system: ActorSystem = _
  implicit var mat: ActorMaterializer = _
  implicit var server: Server = _

  override def beforeEach: Unit = {
    system = ActorSystem()
    mat = ActorMaterializer()

    // We need to evaluate the value because genConfig may reset the context everytime it is called
    val config = genConfig
    server = Server(config)

    server.users.addUser(TestUsers.hoge)

    val fut = Http().bindAndHandle(
      handler = Route.handlerFlow(server.route),
      interface = config.ip,
      port = config.port)

    Await.ready(fut, Duration.Inf)
  }

  override def afterEach: Unit = {
    system.shutdown
    system.awaitTermination
  }
}
