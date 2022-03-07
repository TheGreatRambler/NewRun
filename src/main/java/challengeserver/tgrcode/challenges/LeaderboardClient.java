package challengeserver.tgrcode.challenges;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class LeaderboardClient extends WebSocketClient {
	public LeaderboardClient() throws URISyntaxException, InterruptedException {
		super(new URI("wss://tgrcode.com:9684"));
		connectBlocking();
		JsonObject ret = Json.object().add("flag", "is_server");
		send(ret.toString());
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) { }

	@Override
	public void onMessage(String message) {
		JsonObject m = Json.parse(message).asObject();
		String flag  = m.getString("flag", "none");

		if(flag.equals("ping")) {
			JsonObject ret = Json.object()
								 .add("flag", "pong")
								 .add("time", System.currentTimeMillis());
			send(ret.toString());
		}

		if(flag.equals("get_uuids")) {
			JsonObject ret = Json.object().add("flag", "send_uuids");

			JsonArray uuids = new JsonArray();
			for(Player player : Bukkit.getOnlinePlayers()) {
				uuids.add(player.getUniqueId().toString());
			}

			ret.add("uuids", uuids);
			send(ret.toString());
		}
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		// The codecodes are documented in class
		// org.java_websocket.framing.CloseFrame
		System.out.println("Connection closed by "
						   + (remote ? "remote peer" : "us") + " Code: " + code
						   + " Reason: " + reason);
	}

	@Override
	public void onError(Exception ex) {
		ex.printStackTrace();
		// if the error is fatal then onClose will be called additionally
	}

	public void markPlayerPlaying(String uuid) {
		JsonObject ret
			= Json.object().add("flag", "send_uuid").add("uuid", uuid);
		send(ret.toString());
	}

	public void markPlayerDone(String uuid) {
		JsonObject ret
			= Json.object().add("flag", "remove_uuid").add("uuid", uuid);
		send(ret.toString());
	}

	public void sendReplay(String replayPath) {
		try {
			String url = "http://tgrcode.com:9685/newrun/upload_replay";

			try(CloseableHttpClient client = HttpClients.createDefault()) {
				HttpPost post = new HttpPost(url);
				HttpEntity entity
					= MultipartEntityBuilder.create()
						  .addPart("file",
							  new FileBody(new File("replays", replayPath)))
						  .build();
				post.setEntity(entity);

				try(CloseableHttpResponse response = client.execute(post)) {
					// ...
				}
			}
		} catch(IOException e) {
			// Handle error well
		}
	}

	public boolean downloadReplay(String name) {
		try {
			String url = "http://tgrcode.com:9685/newrun/download_replay";

			try(CloseableHttpClient client = HttpClients.createDefault()) {
				HttpGet get = new HttpGet(url);
				get.setHeader("replay_name", name);

				HttpResponse response = client.execute(get);
				HttpEntity entity     = response.getEntity();
				if(entity != null) {
					BufferedInputStream bis
						= new BufferedInputStream(entity.getContent());
					BufferedOutputStream bos
						= new BufferedOutputStream(new FileOutputStream(
							new File("replays", name + ".newrun")));
					IOUtils.copy(bis, bos);
					bis.close();
					bos.close();
					return true;
				}
			}
		} catch(IOException e) {
			// Handle error well
		}
		return false;
	}
}