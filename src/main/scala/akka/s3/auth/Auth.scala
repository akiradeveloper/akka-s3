package akka.s3

trait Auth {
  def run: Option[String]
}
