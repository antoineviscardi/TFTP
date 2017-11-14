package app;


public class FTServerLauncher {
	private static FTServer server;

	public static void main(String[] args) {
		server = new FTServer();
		server.run();
	}
}
