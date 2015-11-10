package akka.s3

import java.io.FileInputStream
import java.nio.file.{Files, Paths}

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.model._
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.HmacUtils
import org.apache.http.entity.mime.MultipartEntityBuilder

import scala.util.Random
import scala.xml.XML

import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.{HttpPost, HttpGet, HttpPut}
import org.apache.http.entity.FileEntity
import org.apache.http.impl.client.HttpClients

import scala.collection.mutable
import scala.collection.JavaConversions._

class AmazonSDKTest extends AmazonTestBase(ServerConfig.forTest) {

  test("add buckets") { p =>
    val cli = p.client
    cli.createBucket("mybucket1")
    cli.createBucket("a.b")
    val res = cli.listBuckets
    assert(res.forall(_.getOwner.getId === TestUsers.hoge.id))
    assert(Set(res(0).getName, res(1).getName) === Set("mybucket1", "a.b"))
    assert(res.size === 2)

    // location
    val loc = cli.getBucketLocation("mybucket1")
    assert(loc == "US")
  }

  test("put and get object") { p =>
    val cli = p.client
    cli.createBucket("a.b")
    val f = getTestFile("test.txt")
    val putRes = cli.putObject("a.b", "myobj.txt", f)
    assert(putRes.getVersionId === "null")

    val obj1 = cli.getObject("a.b", "myobj.txt")
    checkFileContent(obj1, f)
    // TODO check result

    // test second read
    val obj2 = cli.getObject("a.b", "myobj.txt")
    checkFileContent(obj2, f)
  }

  test("put and get image object") { p =>
    val cli = p.client
    cli.createBucket("mybucket")
    val f = getTestFile("test.jpg")
    cli.putObject("mybucket", "myobj", f)
    val obj = cli.getObject("mybucket", "myobj")
    checkFileContent(obj, f)
  }

  test("put and get several objects") { p =>
    val cli = p.client
    cli.createBucket("mybucket")
    val f = getTestFile("test.txt")
    cli.putObject("mybucket", "myobj1", f)
    cli.putObject("mybucket", "myobj2", f)
    val objListing = cli.listObjects("mybucket")
    assert(objListing.getBucketName == "mybucket")
    val summaries = objListing.getObjectSummaries
    assert(summaries.size === 2)
    assert(summaries(0).getOwner.getId === TestUsers.hoge.id)

    val obj1 = cli.getObject("mybucket", "myobj1")
    checkFileContent(obj1, f)
    val obj2 = cli.getObject("mybucket", "myobj2")
    checkFileContent(obj2, f)
  }

  test("key with slashes") { p =>
    val cli = p.client
    cli.createBucket("mybucket")
    val f = getTestFile("test.txt")
    cli.putObject("mybucket", "a/b/c", f)
    assert(cli.listObjects("mybucket").getObjectSummaries.get(0).getKey === "a/b/c")
    val obj = cli.getObject("mybucket", "a/b/c")
    checkFileContent(obj, f)
  }

  test("presigend get") { p =>
    val cli = p.client

    cli.createBucket("mybucket")
    val f = getTestFile("test.txt")
    cli.putObject("mybucket", "a/b", f)

    val expires = new java.util.Date()
    var msec = expires.getTime()
    msec += 1000 * 60 * 60; // 1 hour.
    expires.setTime(msec)

    val generatePresignedUrlRequest = new GeneratePresignedUrlRequest("mybucket", "a/b")
    generatePresignedUrlRequest.setMethod(HttpMethod.GET)
    generatePresignedUrlRequest.setExpiration(expires)
    val url = cli.generatePresignedUrl(generatePresignedUrlRequest)
    println(url.toString)

    val httpCli = HttpClients.createDefault
    val method = new HttpGet(url.toString)
    val res = httpCli.execute(method)
    assert(res.getStatusLine.getStatusCode === 200)
    assert(IOUtils.contentEquals(res.getEntity.getContent, new FileInputStream(f)))
  }

  test("presigned put (or upload)") { p =>
    val cli = p.client
    cli.createBucket("mybucket")

    val expires = new java.util.Date()
    var msec = expires.getTime()
    msec += 1000 * 60 * 60 // 1 hour.
    expires.setTime(msec)

    val f = getTestFile("test.txt")
    val contentType = "text/plain"

    val req = new GeneratePresignedUrlRequest("mybucket", "a/b")
    req.setMethod(HttpMethod.PUT)
    req.setExpiration(expires)
    req.setContentType(contentType)
    val url = cli.generatePresignedUrl(req)
    println(url.toString)

    val httpCli = HttpClients.createDefault
    val method = new HttpPut(url.toString)
    method.setHeader("Content-Type", contentType)
    method.setEntity(new FileEntity(f))
    val res = httpCli.execute(method)
    assert(res.getStatusLine.getStatusCode === 200)

    val obj = cli.getObject("mybucket", "a/b")
    checkFileContent(obj, f)
  }

  test("delete an object") { p =>
    val cli = p.client
    cli.createBucket("mybucket")
    val f = getTestFile("test.txt")

    cli.putObject("mybucket", "a/b", f)
    cli.putObject("mybucket", "myobj2", f)
    assert(cli.listObjects("mybucket").getObjectSummaries.size === 2)

    cli.deleteObject("mybucket", "a/b")
    assert(cli.listObjects("mybucket").getObjectSummaries.size === 1)
    assert(cli.listObjects("mybucket").getObjectSummaries.get(0).getKey === "myobj2")
  }

  test("put -> delete -> put") { p =>
    val cli = p.client
    cli.createBucket("myb")
    val f = getTestFile("test.txt")
    assert(cli.listObjects("myb").getObjectSummaries.size === 0)
    cli.putObject("myb", "a", f)
    assert(cli.listObjects("myb").getObjectSummaries.size === 1)
    cli.deleteObject("myb", "a")
    assert(cli.listObjects("myb").getObjectSummaries.size === 0)
    cli.putObject("myb", "a", f)
    assert(cli.listObjects("myb").getObjectSummaries.size === 1)
  }

  test("multiple delete") { p =>
    val cli = p.client
    cli.createBucket("mybucket")
    val f = getTestFile("test.txt")
    cli.putObject("mybucket", "myobj1", f)
    cli.putObject("mybucket", "a/b", f)
    cli.putObject("mybucket", "c/d/e", f)
    assert(cli.listObjects("mybucket").getObjectSummaries.size === 3)

    val req = new DeleteObjectsRequest("mybucket")
    req.setKeys(List(new KeyVersion("myobj1"), new KeyVersion("c/d/e")))
    val result = cli.deleteObjects(req)
    assert(result.getDeletedObjects.size === 2)
    assert(result.getDeletedObjects.forall(!_.isDeleteMarker))
    // hmm... the SDK should use Option. returning null is bewildering
    assert(result.getDeletedObjects.forall(_.getDeleteMarkerVersionId === null))

    assert(cli.listObjects("mybucket").getObjectSummaries.size === 1)
    assert(cli.listObjects("mybucket").getObjectSummaries.get(0).getKey === "a/b")
  }

  test("multipart upload (lowlevel)") { p =>
    val cli = p.client
    cli.createBucket("mybucket")

    val path = Paths.get("/tmp/s3test-large.dat")
    mkRandomFile(path, 32 * 1024 * 1024)
    val f = path.toFile // ok because it's in the local filesystem

    val initReq = new InitiateMultipartUploadRequest("mybucket", "a/b")
    val initRes = cli.initiateMultipartUpload(initReq)
    assert(initRes.getBucketName === "mybucket")
    assert(initRes.getKey === "a/b")
    assert(initRes.getUploadId.length > 0)

    val partEtags = mutable.ListBuffer[PartETag]()
    val contentLength = f.length

    var i = 1
    var filePos: Long = 0
    while (filePos < contentLength) {
      val partSize = Math.min(contentLength - filePos, 5 * 1024 * 1024)

      val uploadReq = new UploadPartRequest()
        .withBucketName("mybucket")
        .withKey("a/b")
        .withUploadId(initRes.getUploadId())
        .withPartNumber(i)
        .withFileOffset(filePos)
        .withFile(f)
        .withPartSize(partSize)

      val res = cli.uploadPart(uploadReq)
      assert(res.getETag.size > 0)
      assert(res.getPartNumber === i)
      assert(res.getPartETag.getETag.size > 0)
      assert(res.getPartETag.getPartNumber === i)

      partEtags.add(res.getPartETag)

      i += 1
      filePos += partSize
    }

    val compReq = new CompleteMultipartUploadRequest(
      "mybucket",
      "a/b",
      initRes.getUploadId(),
      partEtags
    )
    val compRes = cli.completeMultipartUpload(compReq)
    assert(compRes.getETag.size > 0)
    assert(compRes.getBucketName === "mybucket")
    assert(compRes.getKey === "a/b")
    assert(compRes.getVersionId === "null")
    assert(compRes.getLocation === s"http://${server.config.ip}:${server.config.port}/mybucket/a%2Fb")

    val obj = cli.getObject("mybucket", "a/b")
    checkFileContent(obj, f)
  }

  test("multipart upload (highlevel)") { p =>
    val cli = p.client

    cli.createBucket("mybucket")

    val path = Paths.get("/tmp/s3test-large.dat")
    mkRandomFile(path, 32 * 1024 * 1024)
    val upFile = path.toFile // ok because it's in the local filesystem

    val tmUp = new TransferManager(cli)
    val upload = tmUp.upload("mybucket", "a/b", upFile)
    upload.waitForCompletion()
    tmUp.shutdownNow(false) // shutdown s3 client = false

    // Make sure that ordinary Get request succeeds
    val obj = cli.getObject("mybucket", "a/b")
    checkFileContent(obj, upFile)

    val tmDown = new TransferManager(cli)
    val downFile = Paths.get("/tmp/s3test-large-download.dat").toFile
    val download = tmDown.download(new GetObjectRequest("mybucket", "a/b"), downFile)
    download.waitForCompletion()
    tmDown.shutdownNow(false)

    assert(IOUtils.contentEquals(
      Files.newInputStream(upFile.toPath),
      Files.newInputStream(downFile.toPath)
    ))
  }

  test("post object and presigned get") { p =>
    val cli = p.client
    cli.createBucket("mybucket")

    val f = getTestFile("test.txt")

    val policy = Random.alphanumeric.take(64).mkString
    val arr: Array[Byte] = HmacUtils.hmacSha1(TestUsers.hoge.secretKey.getBytes, policy.getBytes("UTF-8"))
    val signature = Base64.encodeBase64String(arr)

    val httpCli = HttpClients.createDefault
    val reqPost = new HttpPost(s"http://${server.config.ip}:${server.config.port}/mybucket/")

    // since lisb's client sends uncapitalized signature and policy our test case follows.

    // [spec] passed as form fields to POST in the multipart/form-data encoded message body.
    val entity = MultipartEntityBuilder.create
      .addTextBody("key", "a/b")
      .addTextBody("policy", policy)
      .addTextBody("signature", signature)
      .addTextBody("AWSAccessKeyId", TestUsers.hoge.accessKey)
      .addTextBody("success_action_status", "201")
      .addTextBody("Content-Disposition", "hoge.txt")
      .addTextBody("Content-Type", "text/hoge")
      .addBinaryBody("file", f)
      .addTextBody("submit", "Upload to Amazon S3")
      .build

    reqPost.setEntity(entity)

    val resPost = httpCli.execute(reqPost)
    assert(resPost.getStatusLine.getStatusCode === 201)

    val xml = XML.load(resPost.getEntity.getContent)
    val bucketName = (xml \ "Bucket").text
    assert(bucketName === "mybucket")
    val keyName = (xml \ "Key").text
    assert(keyName === "a/b")
    val etag = (xml \ "ETag").text
    // assert(etag === )
    val loc = (xml \ "Location").text
    assert(loc === s"http://${server.config.ip}:${server.config.port}/mybucket/a%2Fb")

    val obj = cli.getObject("mybucket", "a/b")
    checkFileContent(obj, f)


    // presigned get
    val expires = new java.util.Date()
    var msec = expires.getTime()
    msec += 1000 * 60 * 60; // 1 hour.
    expires.setTime(msec)

    val generatePresignedUrlRequest = new GeneratePresignedUrlRequest("mybucket", "a/b")
    generatePresignedUrlRequest.setMethod(HttpMethod.GET)
    generatePresignedUrlRequest.setExpiration(expires)
    generatePresignedUrlRequest.addRequestParameter("response-content-language", "hogehoge")
    val url = cli.generatePresignedUrl(generatePresignedUrlRequest)
    println(url.toString)

    val reqGet = new HttpGet(url.toString)
    val resGet = httpCli.execute(reqGet)
    assert(resGet.getStatusLine.getStatusCode === 200)
    assert(IOUtils.contentEquals(resGet.getEntity.getContent, new FileInputStream(f)))

    assert(resGet.getFirstHeader("Content-Disposition").getValue == "hoge.txt")
    assert(resGet.getFirstHeader("Content-Type").getValue == "text/hoge")
    assert(resGet.getFirstHeader("Content-Language").getValue == "hogehoge")
  }

  test("presigned put and presigned get") { p =>
    val cli = p.client
    cli.createBucket("mybucket")

    val expires = new java.util.Date()
    var msec = expires.getTime()
    msec += 1000 * 60 * 60 // 1 hour.
    expires.setTime(msec)

    val httpCli = HttpClients.createDefault

    val contentType = "text/plain"

    // PUT
    val putPre = new GeneratePresignedUrlRequest("mybucket", "a/b")
    putPre.setMethod(HttpMethod.PUT)
    putPre.setExpiration(expires)
    putPre.setContentType(contentType)
    putPre.addRequestParameter("Content-Disposition", "hoge.txt")

    // FIXME
    // With Content-Type in query parameter results in PUT failure
    // but should be successful. Otherwise, pre-signed PUTs can't
    // give explicit Content-Type
    // putPre.addRequestParameter("Content-Type", "text")

    val urlPut = cli.generatePresignedUrl(putPre)
    println(urlPut.toString)

    val f = getTestFile("test.txt")
    val putReq = new HttpPut(urlPut.toString)
    putReq.setHeader("Content-Type", contentType)
    putReq.setEntity(new FileEntity(f))
    val resPut = httpCli.execute(putReq)
    assert(resPut.getStatusLine.getStatusCode === 200)

    // GET
    val getPre = new GeneratePresignedUrlRequest("mybucket", "a/b")
    getPre.setMethod(HttpMethod.GET)
    getPre.setExpiration(expires)
    getPre.addRequestParameter("response-content-type", "text/html")
    val urlGet = cli.generatePresignedUrl(getPre)
    println(urlGet.toString)

    val reqGet = new HttpGet(urlGet.toString)
    val resGet = httpCli.execute(reqGet)
    assert(resGet.getStatusLine.getStatusCode === 200)
    assert(IOUtils.contentEquals(resGet.getEntity.getContent, new FileInputStream(f)))

    assert(resGet.getFirstHeader("Content-Disposition").getValue == "hoge.txt")
    assert(resGet.getFirstHeader("Content-Type").getValue == "text/html")
  }
}