package functional.s3

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CORSRule.AllowedMethods
import com.amazonaws.services.s3.model.{CORSRule, BucketCrossOriginConfiguration}
import org.apache.http.client.methods.HttpOptions
import org.apache.http.impl.client.HttpClients
import scala.collection.JavaConversions._

class CorsServerTest extends AmazonTestBase(ServerConfig.forTest) {

  def setup(client: AmazonS3Client) {
    client.createBucket("mybucket")
    val rules = List(
      new CORSRule()
        .withAllowedOrigins(List("https://hoge.com", "https://www.hoge.com"))
        .withAllowedMethods(List(AllowedMethods.POST))
        .withAllowedHeaders(List("x-requested-with", "content-type", "origin")),
      new CORSRule()
        .withAllowedOrigins(List("*"))
        .withAllowedMethods(List(AllowedMethods.GET))
        .withAllowedHeaders(List("accept-encoding", "accept-language", "origin"))
    )
    client.setBucketCrossOriginConfiguration("mybucket", new BucketCrossOriginConfiguration(rules))
  }

  test("pass (PUT/GET style)") { p =>
    val cli = p.client
    setup(cli)

    val httpCli = HttpClients.createDefault
    val req = new HttpOptions(s"http://${server.config.ip}:${server.config.port}/mybucket/myobj")
    req.addHeader("Origin", "https://www.hoge.com")
    req.addHeader("Access-Control-Request-Method", "POST")

    val res = httpCli.execute(req)
    assert(res.getStatusLine.getStatusCode === 200)
  }

  test("pass (POST style)") { p =>
    val cli = p.client
    setup(cli)

    val httpCli = HttpClients.createDefault
    val req = new HttpOptions(s"http://${server.config.ip}:${server.config.port}/mybucket/")
    req.addHeader("Origin", "https://www.hoge.com")
    req.addHeader("Access-Control-Request-Method", "POST")
    req.addHeader("Access-Control-Request-Headers", "x-requested-with, content-type")

    val res = httpCli.execute(req)
    assert(res.getStatusLine.getStatusCode === 200)
  }

  test("reject (not all headers matched)") { p =>
    val cli = p.client
    setup(cli)

    val httpCli = HttpClients.createDefault
    val req = new HttpOptions(s"http://${server.config.ip}:${server.config.port}/mybucket/")
    req.addHeader("Origin", "https://www.hoge.com")
    req.addHeader("Access-Control-Request-Method", "POST")
    req.addHeader("Access-Control-Request-Headers", "x-requested-with, content-type,content-disposition")

    val res = httpCli.execute(req)
    assert(res.getStatusLine.getStatusCode === 403)
  }

  test("reject (bad origin requested)") { p =>
    val cli = p.client
    setup(cli)

    val httpCli = HttpClients.createDefault
    val req = new HttpOptions(s"http://${server.config.ip}:${server.config.port}/mybucket/myobj")
    req.addHeader("Origin", "https://www.hige.com")
    req.addHeader("Access-Control-Request-Method", "PUT")

    val res = httpCli.execute(req)
    assert(res.getStatusLine.getStatusCode === 403)
  }
}
