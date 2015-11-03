package akka

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.apache.tika.Tika

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

package object s3 {
  val X_AMZ_DELETE_MARKER = "x-amz-delete-marker"
  val X_AMZ_VERSION_ID = "x-amz-version-id"
  val X_AMZ_REQUEST_ID = "x-amz-request-id"

  val CONTENT_TYPE = "content-type"
  val CONTENT_DISPOSITION = "content-disposition"

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  implicit class HttpRequestOps(unwrap: HttpRequest) {
    def listFromHeaders = HeaderList.FromRequestHeaders(unwrap)
    def listFromQueryParams = HeaderList.FromRequestQuery(unwrap.uri.query)
  }

  // Closeable < AutoCloseable
  def using[A <: AutoCloseable, B](resource: A)(f: A => B): B = {
    try {
      f(resource)
    } finally {
      resource.close
    }
  }

  implicit class PathOps(path: Path) {
    // should fix. should return Future
    def writeBytes(data: Source[ByteString, Any])(implicit system: ActorSystem, mat: Materializer): Unit = {
      using(Files.newOutputStream(path, StandardOpenOption.CREATE)) { f =>
        val fut = data.runForeach { a: ByteString =>
          println(s"hoge + ${a.size}")
          f.write(a.toArray)
        }
        Await.ready(fut, Duration.Inf)
        f.flush
      }
    }

    def writeBytes(data: Array[Byte]): Unit = {
      using(Files.newOutputStream(path, StandardOpenOption.CREATE)) { f =>
        f.write(data)
        f.flush
      }
    }

    def readBytes: Array[Byte] = {
      using(Files.newInputStream(path)) { f =>
        IOUtils.toByteArray(f)
      }
    }

    def computeMD5: String = {
      using(Files.newInputStream(path)) { inp =>
        DigestUtils.md5Hex(inp)
      }
      //Base64.encodeBase64String(DigestUtils.md5(Files.newInputStream(path)))
    }

    def length: Long = {
      Files.size(path)
    }
    def touch = Files.createFile(path)
    def delete = {
      if (exists) {
        Files.delete(path)
      }
    }
    def exists = Files.exists(path)
    def mkdirp {
      if (!exists) {
        Files.createDirectories(path)
      }
    }
    def children: Seq[Path] = {
      using(Files.newDirectoryStream(path)) { p =>
        p.iterator.toList
      }
    }
    def lastName: String = path.getFileName.toString
    def contentType: String = {
      // Files.probeContentType(path)
      using(Files.newInputStream(path)) { f =>
        val tika = new Tika()
        tika.detect(f)
      }
    }
    def emptyDirectory = Files.walkFileTree(path, new SimpleFileVisitor[Path] {
      override def visitFile(x: Path, attrs: BasicFileAttributes) = {
        Files.delete(x)
        FileVisitResult.CONTINUE
      }
      override def postVisitDirectory(x: Path, e: IOException) = {
        if (x == path) {
          FileVisitResult.TERMINATE
        } else {
          Files.delete(x)
          FileVisitResult.CONTINUE
        }
      }
    })
    def lastModified: Date = {
      new Date(Files.getLastModifiedTime(path).toMillis)
    }
  }

  implicit class StringOps(self: String) {
    def compIns(other: String): Boolean = {
      self.toLowerCase == other.toLowerCase
    }
    def optInt: Option[Int] = {
      try {
        Some(self.toInt)
      } catch {
        case _: Throwable =>  None
      }
    }
  }

  implicit class DateOps(self: Date) {
    // 'Z' means UTC
    def format000Z = {
      val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
      sdf.format(self)
    }
  }

  implicit class BoolOps(b: => Boolean) {
    def orFailWith(e: Error.t): Unit = {
      if (!b) {
        s3.Error.failWith(e)
      }
    }
    def not: Boolean = {
      !b
    }
  }

  implicit class AnyOps[A](a: A) {
    def `|>`[B](f: A => B): B = f(a)

    def applyIf(p: => Boolean)(f: A => A): A = {
      if (p) {
        f(a)
      } else {
        a
      }
    }
    // FIXME not sure this is correct (seems so dangerous)
    def applySome[B, C](x: Option[B])(f: A => B => C): C = {
      x match {
        case Some(b) => f(a)(b)
        case None => a.asInstanceOf[C]
      }
    }
  }

  implicit class OptionOps[A](a: Option[A]) {
    def noneOrSome[B](default: B)(f: A => B): B = {
      if (a.isDefined) {
        f(a.get)
      } else {
        default
      }
    }
    def `<+`(b: Option[A]): Option[A] = {
      (a, b) match {
        case (Some(_), _) => a
        case _ => b
      }
    }
  }
}
