package akka.s3

import java.nio.file.{Files, Path}

import org.apache.commons.io.IOUtils

import scala.pickling.Defaults._
import scala.pickling.binary.{BinaryPickle, _}

object Versioning {
  type t = File

  val UNVERSIONED = 0
  val ENABLED = 1
  case class File(value: Int) {
    def write(path: Path): Unit = {
      LoggedFile(path).put { f =>
        f.writeBytes(this.pickle.value)
      }
    }
  }
  def read(path: Path): this.File = {
    using(Files.newInputStream(LoggedFile(path).get.get)) { f =>
      BinaryPickle(IOUtils.toByteArray(f)).unpickle[File]
    }
  }
}
