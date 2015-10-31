package functional.s3

trait Auth {
  def run: Option[String]
}
