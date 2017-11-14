package app;

public class FTServer {
	private int serverPort = 6070;
	private CommInterface ftp = new TFTP();

	public void run() {
		System.out.println("Server running...");

		// Initialize the server
		ftp.initializeServer(serverPort);

		// Main server loop
		while (true) {
			System.out.println("Server waiting for request...");

			// Wait for command
			String command = "";
			command = ftp.getCommand();
			if (command == null) {
				continue;
			}
			System.out.println("Server received command:\n\t" + command);

			// Parse the command
			String tokens[] = parseCommand(command);

			// Interpret the command
			switch (tokens[0].toLowerCase()) {
			case "put":
				receiveFile(tokens[1]);
				break;
			case "get":
				sendFile(tokens[1]);
				break;
			}
		}
	}

	private void sendFile(String filePath) {
		filePath = new String("./server/send/" + filePath);
		System.out.println("Server sending file:\n\t" + filePath);
		ftp.sendFile(filePath);
	}

	private void receiveFile(String filePath) {
		filePath = new String("./server/receive/" + filePath);
		System.out.println("Server receiving file:\n\t" + filePath);
		ftp.receiveFile(filePath);
	}

	private String[] parseCommand(String command) {
		command.replaceAll("\\s", "");
		String[] tokens = command.split(",");
		return tokens;
	}

}
