package models

case class Session(uuid: String)

object Session {
  def create = {
    Session(java.util.UUID.randomUUID().toString)
  }
}
