package akka.s3

import java.nio.file.{Files, Path}

import org.apache.commons.io.IOUtils

import scala.pickling.Defaults._
import scala.pickling.binary.{BinaryPickle, _}
import scala.xml.NodeSeq

object Acl {
  // FIXME owner should be string
  // anonymous owner's ID is a string "anonymous" according to ceph but not documented
  case class File(owner: Option[String], grants: Seq[Grant]) {
    def write(path: Path): Unit = {
      LoggedFile(path).put { f =>
        f.writeBytes(this.pickle.value)
      }
    }
  }
  def read(path: Path): File = {
    using(Files.newInputStream(LoggedFile(path).get.get)) { f =>
      BinaryPickle(IOUtils.toByteArray(f)).unpickle[File]
    }
  }

  trait Grantee
  case class ById(id: String) extends Grantee
  case class ByEmail(email: String) extends Grantee // TODO
  case class AuthenticatedUsers() extends Grantee // TODO
  case class AllUsers() extends Grantee // TODO

  // not sealed because WriteAcp and Read are allowed to bucket ACL only
  trait Permission
  case class Deny() extends Permission
  case class FullControl() extends Permission
  case class Write() extends Permission
  case class Read() extends Permission // bucket only
  case class WriteAcp() extends Permission // bucket only
  case class ReadAcp() extends Permission

  case class Grant(grantee: Grantee, perm: Permission)
}

// TODO
case class Acl(owner: User, grants: Seq[Acl.Grant]) {
  import Acl._

  def checkPermission(requester: User): Permission = {
    if (requester == owner) {
      FullControl()
    } else {
      // TODO
      Deny()
    }
  }

  def toXML: NodeSeq = {
    null
  }
}

trait AclSupport { self: Server =>
  import Acl._

  def fromXML(xml: NodeSeq): Acl.File = {
    val owner = Some((xml \ "Owner" \ "ID").text) // tmp. we assume non-anonymous user
    val grants = (xml \ "AccessControlList" \ "Grant").map { a =>
      // TODO
      val grantee = ById((a \ "Grantee" \ "ID").text) // tmp. we assume authenticated canonical user
      val perm = (a \ "Permission").text match {
        case "FULL_CONTROL" => FullControl()
        case "WRITE" => Write()
        case "READ" => Read()
        case "WRITE_ACP" => WriteAcp()
        case "READ_ACP" => ReadAcp()
      }
      Grant(grantee, perm)
    }
    File(owner, grants)
  }
}
