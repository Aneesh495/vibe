public interface MessageInterface {
  String getSender();

  String getReceiver();

  String getContent();

  long getTimestamp();

  void setContent(String content);
}
