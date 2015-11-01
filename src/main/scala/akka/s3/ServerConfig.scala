package akka.s3

import java.nio.file.{Path, Paths}

import com.google.common.jimfs.Jimfs
import com.typesafe.config.{Config, ConfigFactory}

trait ServerConfig {
  def mountpoint: Path
  def ip: String
  def port: Int

  def treePath: Path = mountpoint.resolve("tree")
  def adminPath: Path = mountpoint.resolve("admin")
}

object ServerConfig {
  def forTest = {
    val configRoot = ConfigFactory.load
    fromConfig(configRoot)
  }

  def fromConfig(configRoot: Config) = new ServerConfig {
    val config = configRoot.getConfig("functional.s3")
    val rootDir = if (config.hasPath("mountpoint")) {
      val dir = Paths.get(config.getString("mountpoint"))
      dir.mkdirp
      dir.emptyDirectory
      dir
    } else {
      val dir = Jimfs.newFileSystem.getPath("/s3test")
      dir.mkdirp
      dir
    }
    treePath.mkdirp
    adminPath.mkdirp
    override def mountpoint = rootDir
    override def ip = config.getString("ip")
    override def port: Int = config.getInt("port")
  }
}

