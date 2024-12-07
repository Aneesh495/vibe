public interface Client{
    String sendRequest(String action, String caller, String data);
    String sendRequest(String action, String data);
    void closeConnection();
} 