import java.time.Instant;

public class Message implements MessageInterface {

    private final String sender;
    private final String receiver;
    private String content;
    private long timestamp;
    boolean reported = true;

    public Message(String sender, String receiver, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = 0;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long time) {
        this.timestamp = time;
    }

    public void setReported() {
        this.reported = true;
    }

    public String toString() {
        return "From: " + sender + "\nTo: " + receiver + "\nMessage: " + content + "\nSent at: "
                + Instant.ofEpochMilli(timestamp);
    }
}
