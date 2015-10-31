package functional

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.apache.tika.Tika

import scala.collection.JavaConversions._
import scala.collection.mutable

package object s3 {
  // Closeable < AutoCloseable
  def using[A <: AutoCloseable, B](resource: A)(f: A => B): B = {
    try {
      f(resource)
    } finally {
      resource.close
    }
  }

  implicit class PathOps(path: Path) {
    def writeBytes(data: Source[ByteString, _]): Unit = {
      implicit val system = ActorSystem()
      implicit val mat = ActorMaterializer()
      using(Files.newOutputStream(path, StandardOpenOption.CREATE)) { f =>
        data.runWith(Sink.foreach { a =>
          f.write(a.toArray)
        })
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
        p.iterator.toSeq
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

  // List of (key, value) pairs but searching by key is case-insensitive
  object KVList {
    case class t(unwrap: Seq[(String, String)]) {
      def get(key: String): Option[String] = unwrap.find{a => a._1.toLowerCase == key.toLowerCase}.map(_._2)
      def ++(other: t): t = t(unwrap ++ other.unwrap)
    }
    def builder: Builder = Builder()
    case class Builder() {
      val m = mutable.Map[String, String]()
      def append(k: String, v: Option[String]): this.type = {
        if (v.isDefined) {
          m += k -> v.get
        }
        this
      }
      def build: t = {
        t(m.toSeq)
      }
    }
  }

  implicit class ErrorOps(b: => Boolean) {
    def orFailWith(e: Error.t): Unit = {
      if (!b) {
        Error.failWith(e)
      }
    }
    def not: Boolean = {
      !b
    }
  }

  implicit class ApplyIf[A](a: A) {
    def applyIf(p: => Boolean)(f: A => A): A = {
      if (p) {
        f(a)
      } else {
        a
      }
    }
    def applySome[B](x: Option[B])(f: A => B => A) = {
      x match {
        case Some(b) => f(a)(b)
        case None => a
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
