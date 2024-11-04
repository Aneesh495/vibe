import java.io.IOException;

import java.io.IOException;

public interface MessageInterface {
    public String getSender();
    public String getReceiver();
    public String getContent();
    public void setContent(String content);
    public long getTimestamp();
    public void setTimestamp(long time);
    public void setReported();
    public String toString();
}

