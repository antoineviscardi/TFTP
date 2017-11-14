package app;

public class FTClientLauncher {
	private static FTClient client;

	public static void main(String[] args) {
		client = new FTClient();
		client.run();
	}
}