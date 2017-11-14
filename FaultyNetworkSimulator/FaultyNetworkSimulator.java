package FaultyNetworkSimulator;
import java.io.*;

public class FaultyNetworkSimulator {
	private static final int SERVERPORT = 6070;
	private static final int LISTENPORT = 6050;
	private static int errorPercent = 75;

	public static void main(String[] args) throws IOException {
		BufferedReader stdin;
		stdin = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			System.out.print("Enter the % of packets to be dropped (0-100):>");
			String selectedAction = stdin.readLine();
			try {
				errorPercent = Integer.parseInt(selectedAction);
			} catch (Exception e) {
				System.err.println("Invalid Input");
				continue;
			}
			if (errorPercent >= 0 && errorPercent <= 100) {
				break;
			} else {
				System.err.println("Invalid Input");
			}
		}
		new CommunicationThread(LISTENPORT,SERVERPORT, errorPercent).start();
	}
}