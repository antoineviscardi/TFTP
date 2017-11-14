package app;

public interface CommInterface {
	public void initializeServer( int sourcePort);
	public void initializeClient(String address, int destinationPort);
	public void sendFile(String filePath);
	public void receiveFile(String filePath);
	public void sendCommand(String command);
	public void error(String e);
	public String getCommand();
	public void closeConnection();
}

