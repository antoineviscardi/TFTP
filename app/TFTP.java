package app;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import app.CommInterface;
import app.Messages.Message;

public class TFTP implements CommInterface {

	private static final int MAX_DATA_SIZE = 1000;
	private static final int MAX_PACKET_SIZE = 1200; // Depends on Protobuf
														// implementation. Not
														// exactly 1024 + 4
	private int TIMEOUT_SEND = 50;
	private int TIMEOUT_RECEIVE = 100;

	private static final boolean PRINT_TRAFIC = false; // When true will print
														// trafic on both side
														// in console
	private static final int PRINT_COMPLETION = 2; // 0: no print;
													// 1: print percentages;
													// 2: print ratio.
													// 3: print number of resent
													// packets

	private DatagramSocket socket;
	private InetAddress destAddress;
	private int srcPort, destPort = -1;

	private Message lastMessageSent;
	private Message currentMessageReceived;
	private Message lastMessageReceived;

	private int blockNumber = 0;

	private int numOfRetry = 0;

	@Override
	public void initializeServer(int sourcePort) {
		srcPort = sourcePort;
		try {
			socket = new DatagramSocket(srcPort);
		} catch (SocketException e) {
			System.out.println("Error initializing server: " + e);
		}
	}

	@Override
	public void initializeClient(String address, int destinationPort) {
		destPort = destinationPort;
		try {
			socket = new DatagramSocket();
			destAddress = InetAddress.getByName(address);
		} catch (SocketException | UnknownHostException e) {
			System.out.println("Error while initializing client: " + e);
		}
	}

	@Override
	public void sendCommand(String command) {
		// Initialize for new transfer
		blockNumber = 0;
		lastMessageSent = null;
		currentMessageReceived = null;
		lastMessageReceived = null;

		// Parse the command
		String[] tokens = parseCommand(command);
		if (tokens.length > 2) {
			System.out.println("Error: too many arguments in command");
			return;
		}

		// Get data from command
		Message.RequestType requestType = null;
		String fileName;

		switch (tokens[0]) {
		case "put":
			requestType = Message.RequestType.WRQ;
			break;
		case "get":
			requestType = Message.RequestType.RRQ;
			break;
		case "quit":
			closeConnection();
			break;
		default:
			System.out.println("Error: Command not recognized");
			break;
		}
		fileName = tokens[1];

		// Create message
		Message message = Message.newBuilder().setOpCode(requestType).setFileName(fileName).build();

		// Send message
		if (!sendMessage(message))
			return;
	}

	@Override
	public String getCommand() {
		// Initialize for new transfer
		blockNumber = 0;
		destPort = -1;
		lastMessageSent = null;
		currentMessageReceived = null;
		lastMessageReceived = null;
		try {
			// Block until request received
			Message message;
			Message.RequestType opCode;
			String requestTypeString;
			while (true) {
				// Receive a message with timeout of 0
				message = receiveMessage(0);

				// Make sure it is a request
				opCode = message.getOpCode();
				if (opCode == Message.RequestType.WRQ) {
					requestTypeString = "put";
					break;
				} else if (opCode == Message.RequestType.RRQ) {
					requestTypeString = "get";
					break;
				}
			}

			// return command
			String fileName = message.getFileName();
			return requestTypeString + "," + fileName;

		} catch (ErrorPacketReceivedException e) {
			// Catch any error packet received
			return null;
		}
	}

	@Override
	public void sendFile(String filePath) {
		try (FileInputStream file = new FileInputStream(filePath);) {
			// If you sent no message, server side
			if (lastMessageSent == null) {
				// Go to normal data/ack sequence
			} else if (lastMessageSent.getOpCode() == Message.RequestType.WRQ) {
				// Wait for ack before sending data
				for (int i = 0; i <= 5; i++) {
					if (i == 5) {
						sendError("Ack never received");
						return;
					}
					Message message = receiveMessage((int) (TIMEOUT_SEND * Math.pow(2, i)));
					if (message == null || message.getOpCode() != Message.RequestType.ACK
							|| message.getBlockNum() != blockNumber) {
						if (!sendMessage(lastMessageSent))
							return;
						numOfRetry++;
					} else {
						break;
					}
				}
			}

			// Loop until transfer complete
			int from = 0, to = 0, length = file.available();
			boolean lastSent = false;
			while (!lastSent) {
				// Send data
				from = to;
				if (length - from >= MAX_DATA_SIZE) {
					to = from + MAX_DATA_SIZE;
				} else {
					to = length;
					lastSent = true;
				}
				byte[] buf = new byte[to - from];
				file.read(buf);
				Message message = Message.newBuilder().setOpCode(Message.RequestType.DATA).setBlockNum(++blockNumber)
						.setData(ByteString.copyFrom(buf)).build();
				if (!sendMessage(message))
					return;

				// If receive no ack (timeout or RRQ packet again) resends data.
				for (int i = 0; i <= 10; i++) {
					if (i == 10) {
						sendError("Ack never received");
						return;
					}
					message = receiveMessage(TIMEOUT_SEND);
					if (message == null || message.getOpCode() != Message.RequestType.ACK
							|| message.getBlockNum() != blockNumber) {
						// Making sure we don't get caught in storm
						if (numOfRetry++ % 5 != 4) {
							if (!sendMessage(lastMessageSent))
								return;
						}
					} else {
						break;
					}
				}

				// Print progression
				printProgression(to, length);
			}
			System.out.println("# of resent packet: " + numOfRetry);

		} catch (ErrorPacketReceivedException e) {
			System.out.println(e);
		} catch (IOException e) {
			sendError("Error while reading file: " + e);
		}
	}

	@Override
	public void receiveFile(String filePath) {
		try (FileOutputStream file = new FileOutputStream(filePath);) {
			// If no message sent, server side
			if (lastMessageSent == null) {
				// Send ack #0
				Message message = Message.newBuilder().setOpCode(Message.RequestType.ACK).setBlockNum(blockNumber)
						.build();
				if (!sendMessage(message))
					return;
			} else if (lastMessageSent.getOpCode() == Message.RequestType.RRQ) {
				// Go to normal data/ack sequence
			}

			// Receive one packet at a time and send ack
			Message message = null;
			int dataSize = MAX_DATA_SIZE;

			while (dataSize == MAX_DATA_SIZE) {
				// Receive message
				for (int i = 0; i <= 5; i++) {
					if (i == 5) {
						sendError("Receiver timeout");
						return;
					}
					message = receiveMessage(TIMEOUT_RECEIVE);
					if (message == null && lastMessageSent.getOpCode() == Message.RequestType.RRQ) {
						// If RRQ packet lost
						if (!sendMessage(lastMessageSent))
							return;
					} else if (lastMessageSent.getBlockNum() == 0 && message != null && message.getOpCode() == Message.RequestType.WRQ) {
						// If receive WRQ again, first ack never got through
						if (!sendMessage(lastMessageSent))
							return;
						numOfRetry++;
					} else if (message == null || message.getOpCode() != Message.RequestType.DATA) {
						// Receiver never resends packets. Risk of packet storm!
						// If sender does not receive ack, data will be resent
						// upon what receiver resends ack.
					} else {
						break;
					}
				}

				// If received same data as last time, don't write to file.
				if (lastMessageReceived != null && message.getBlockNum() == lastMessageReceived.getBlockNum()) {
					if (PRINT_TRAFIC)
						System.out.println("Received same data");
				} else {
					dataSize = message.getData().size();
					file.write(message.getData().toByteArray());
				}

				// Send ack
				Message ack = Message.newBuilder().setOpCode(Message.RequestType.ACK).setBlockNum(message.getBlockNum())
						.build();
				if (!sendMessage(ack))
					return;
			}

			// Making sure last sent ack received.
			for (int i = 0; i <= 5; i++) {
				if (i == 5) {
					return;
				}
				message = receiveMessage(TIMEOUT_RECEIVE);
				
				if (message == null || message.getOpCode() != Message.RequestType.DATA) {
					// Receiver never resends packets. Risk of packet storm!
					// If sender does not receive ack, data will be resent
					// upon what receiver resends ack.
				} else {
					Message ack = Message.newBuilder().setOpCode(Message.RequestType.ACK).setBlockNum(message.getBlockNum())
							.build();
					if (!sendMessage(ack))
						return;
					break;
				}
			}

		} catch (ErrorPacketReceivedException e) {
			System.out.println(e);
		} catch (FileNotFoundException e) {
			System.out.println("File was not found: " + e);
		} catch (IOException e) {
			System.out.println("IOException: " + e);
		}
	}

	@Override
	public void error(String e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void closeConnection() {
		System.exit(0);
	}

	private boolean sendMessage(Message message) {
		// To byte array and check length
		byte[] messageByte = message.toByteArray();
		if (messageByte.length > MAX_PACKET_SIZE) {
			System.out.println(message.getBlockNum());
			sendError("Error: packet too large of size " + messageByte.length + " bytes");
			return false;
		}

		// Send message
		DatagramPacket packet = new DatagramPacket(messageByte, messageByte.length, destAddress, destPort);
		try {
			socket.send(packet);
			if (PRINT_TRAFIC)
				System.out.println("Sent message:\n" + message);
			lastMessageSent = message;
		} catch (IOException e) {
			sendError("Error while sending command: " + e);
			return false;
		}
		return true;
	}

	/*
	 * Receive message. Also check if error was received. If internal error
	 * occurs, sends error packet. Return null in all cases where connection
	 * should terminate
	 */
	private Message receiveMessage(int timeout) throws ErrorPacketReceivedException {
		// Receive packet with timeout of <timeout>ms
		byte[] buf = new byte[MAX_PACKET_SIZE];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		try {
			socket.setSoTimeout(timeout);
			socket.receive(packet);
			socket.setSoTimeout(0);
		} catch (SocketTimeoutException e) {
			return null;
		} catch (IOException e) {
			sendError("Error while receiving packet: " + e);
			return null;
		}

		// Parse received message
		// Check packet source and ignore if not from good source
		// (Unless it is the first packet received)
		if (destAddress == null)
			destAddress = packet.getAddress();
		if (destPort == -1)
			destPort = packet.getPort();
		if (destPort != packet.getPort() || !destAddress.equals(packet.getAddress()))
			return null;

		Message message;
		byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
		try {
			message = Message.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			sendError("Error while unpacking packet using protobuf: " + e);
			return null;
		}
		if (message.getOpCode() == Message.RequestType.ERROR) {
			throw new ErrorPacketReceivedException(message.getErrMsg());
		}

		if (currentMessageReceived == null) {
			currentMessageReceived = message;
		} else {
			lastMessageReceived = currentMessageReceived;
			currentMessageReceived = message;
		}
		if (PRINT_TRAFIC)
			System.out.println("Received message:\n" + message);
		return message;
	}

	private void sendError(String errorMessage) {
		// Print error message
		System.out.println(errorMessage);

		// Build error message
		Message message = Message.newBuilder().setOpCode(Message.RequestType.ERROR)
				.setErrCode(Message.ErrCode.AccessViolation).setErrMsg(errorMessage).build();
		byte[] messageByte = message.toByteArray();

		// Send message
		DatagramPacket packet = new DatagramPacket(messageByte, messageByte.length, destAddress, destPort);
		try {
			socket.send(packet);
			System.out.println("Error packet sent");
		} catch (IOException e) {
			System.out.println("Error while sending error packet: " + e);
		}
	}

	private String[] parseCommand(String command) {
		command.replaceAll("\\s", "").toLowerCase();
		String[] tokens = command.split(",");
		return tokens;
	}

	private void printProgression(long current, long finish) {
		switch (PRINT_COMPLETION) {
		case 0:
			break;
		case 1:
			long currentValue = 100 * current / finish;
			long lastValue = 100 * (current - MAX_DATA_SIZE) / finish;

			if (currentValue != lastValue)
				System.out.printf("%d%% done\n", currentValue);
			break;
		case 2:
			float c = (float) (current / 1000.0);
			float f = (float) (finish / 1000.0);
			System.out.printf("%.3f//%.3f KB transferred\n", c, f);
			break;
		case 3:
			System.out.println("# of resent packet: " + numOfRetry);
			// if (numOfRetry == 4)
			// System.exit(0);
		}
	}

	private class ErrorPacketReceivedException extends Exception {
		private static final long serialVersionUID = 1L;

		public ErrorPacketReceivedException(String m) {
			super("Error packet received: " + m);
		}
	}
}
