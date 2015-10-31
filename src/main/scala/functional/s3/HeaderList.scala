package functional.s3

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{Multipart, HttpRequest}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import scala.collection.mutable

trait HeaderList {
  def get(name: String): Option[String]

  // def filter(p: String => Boolean): Seq[(String, String)]
}

// List of (key, value) pairs but searching by key is case-insensitive
object KVList {
  case class t(unwrap: Seq[(String, String)]) extends HeaderList {
    def get(key: String): Option[String] = unwrap.find{a => a._1.toLowerCase == key.toLowerCase}.map(_._2)
  }
  def builder: Builder = Builder()
  case class Builder() {
    val l = mutable.ListBuffer[(String, String)]()
    def append(k: String, v: Option[String]) = { if (v.isDefined) { l += k -> v.get }; this }
    def build = t(l)
  }
}

object HeaderList {

  case class Aggregate(xs: Seq[HeaderList]) extends HeaderList {
    def get(name: String) = {
      var ret: Option[String] = None
      xs.foreach { x =>
        ret = ret <+ x.get(name)
      }
      ret
    }
  }

  case class FromRequestHeaders(req: HttpRequest) extends HeaderList {
    def get(name: String): Option[String] = {
      req.headers.find(_.is(name.toLowerCase)).map(_.value)
    }
  }

  case class FromRequestQuery(q: Query) extends HeaderList {
    def get(name: String): Option[String] = {
      q.get(name)
    }
  }

  case class FromMultipart(mfd: Multipart.FormData) extends HeaderList {
    var file: Source[ByteString, Any] = _
    val tmp = mutable.ListBuffer[(String, String)]()
    mfd.parts.runForeach { part =>
      val name = part.name
      val entity = part.entity
      if (name == "file") {
        file = part.entity.dataBytes
      } else {
        part.entity.dataBytes.runForeach { data =>
          val charset = entity.contentType.charset.value
          val str = data.decodeString(charset)
          tmp += Pair(name, str)
        }
      }
    }
    val headerList = KVList.t(tmp)

    override def get(name: String) = headerList.get(name)
  }
}
