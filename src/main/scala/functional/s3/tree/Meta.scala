package functional.s3

import java.nio.file.{Paths, Files, Path}
import org.apache.commons.io.IOUtils

import scala.pickling.Defaults._
import scala.pickling.binary._

object Meta {
  type t = File
  case class File(
                 isVersioned: Boolean,
                 isDeleteMarker: Boolean,
                 eTag: String,
                 attrs: KVList.t,
                 xattrs: KVList.t
                 )
  {
    def write(path: Path): Unit = {
      path.writeBytes(this.pickle.value)
    }
  }

  def read(path: Path): File = {
    using(Files.newInputStream(path)) { f =>
      BinaryPickle(IOUtils.toByteArray(f)).unpickle[File]
    }
  }
}

object MetaDump extends App {
  val path = Paths.get(args(0))
  val meta = Meta.read(path)
  println(meta)
}
