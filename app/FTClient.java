package app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class FTClient {
	private int serverPort = 6050;
	private BufferedReader userInput;
	private CommInterface ftp = new TFTP();

	public void run() {
		// Initialize the command prompt
		System.out.println("COMMANDS:");
		System.out.println("\tput,<fileName>");
		System.out.println("\tget,<fileName>");
		System.out.println("\tquit");
		System.out.println("");
		System.out.println("<fileName> should not contain any white space characters");
		System.out.println("");
		userInput = new BufferedReader(new InputStreamReader(System.in));

		// Initialize the client TFTP
		ftp.initializeClient("localhost", serverPort);

		// Main loop
		String command = "";
		while (true) {
			System.out.print("ftp>");
			command = getCommand().replaceAll("\\s", "");;
			
			String tokens[] = parseCommand(command);
			if (tokens.length > 2) {
				System.out.println("Invalid command: too many arguments");
			} else if (tokens.length < 1) {
				System.out.println("Invalid command: too few arguments");
			} else if (tokens.length == 1 && !tokens[0].equals("quit")) {
				System.out.println("Invalid command: not recognized");
			}else {
				ftp.sendCommand(command);
				switch(tokens[0]) {
				case "put":
					 sendFile(tokens[1]);
					 break;
				case "get":
					receiveFile(tokens[1]);
					break;
				default:
					System.out.println("Invalid input");
				}
			}
		}

	}

	private void receiveFile(String filePath) {
		filePath = new String("./client/receive/" + filePath);
		System.out.println("Client receiving file:\n\t" + filePath);
		ftp.receiveFile(filePath);
	}

	private void sendFile(String filePath) {
		filePath = new String("./client/send/" + filePath);
		System.out.println("Client sending file:\n\t" + filePath);
		ftp.sendFile(filePath);
	}

	private String getCommand() {
		String command = "";
		try {
			command = userInput.readLine();
		} catch (IOException e) {
			System.out.println("Error while getting user input: " + e);
		}
		return command;
	}

	private String[] parseCommand(String command) {
		String[] tokens = command.split(",");
		return tokens;
	}
}
