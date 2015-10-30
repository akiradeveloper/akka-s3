package functional.s3

import java.io.{FileInputStream, File}

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.{S3ClientOptions, AmazonS3Client}
import org.apache.commons.io.IOUtils

class AmazonTestBase(genConfig: => ServerConfig) extends ServerTestBase(genConfig) {

  def checkFileContent(a: S3Object, b: File): Unit = {
    assert(IOUtils.contentEquals(a.getObjectContent, new FileInputStream(b)))
  }

  case class FixtureParam(client: AmazonS3Client)
  override protected def withFixture(test: OneArgTest) = {
    val conf = new ClientConfiguration
    // force to use v2 signature. not supported in 1.9.7
    conf.setSignerOverride("S3SignerType")

    val cli = new AmazonS3Client(new BasicAWSCredentials(TestUsers.hoge.accessKey, TestUsers.hoge.secretKey), conf)
    cli.setEndpoint(s"http://${server.config.ip}:${server.config.port}")
    cli.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true))

    test(FixtureParam(cli))
  }
}
