package pacman.gdx.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import pacman.gdx.server.actors.Pacman;
import pacman.gdx.server.ws.WebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ForkJoinPool;

@Component
public class GameLoop extends ApplicationAdapter {

    private final WebSocketHandler socketHandler;
    private final Array<String> events = new Array<>();
    private final Json json;
    private float lastRender = 0;
    private static final float frameRate = 1 / 30f;
    private final ObjectMap<String, Pacman> pacmans = new ObjectMap<>();
    private final Array<Pacman> stateToSend = new Array<>();
    private final ForkJoinPool pool = ForkJoinPool.commonPool();

    public GameLoop(WebSocketHandler socketHandler, Json json) {
        this.socketHandler = socketHandler;
        this.json = json;
    }

    @Override
    public void create() {
        socketHandler.setConnectListener(session -> {
            Pacman pacman = new Pacman();
            pacman.setId(session.getId());
            pacmans.put(session.getId(), pacman);
            try {
                session.getNativeSession()
                        .getBasicRemote()
                        .sendText(
                                String.format("{\"class\":\"sessionKey\",\"id\":\"%s\"}", session.getId())
                        );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        socketHandler.setDisconnectListener(session -> {
            sendToEverybody(String.format("{\"class\":\"evict\",\"id\":\"%s\"}", session.getId()));
            pacmans.remove(session.getId());
        });
        socketHandler.setMessageListener((((session, message) -> {
            pool.execute(() -> {
                String type = message.get("type").asText();
                switch (type) {
                    case "state":
                        Pacman pacman = pacmans.get(session.getId());
                        pacman.setLeftPressed(message.get("leftPressed").asBoolean());
                        pacman.setRightPressed(message.get("rightPressed").asBoolean());
                        pacman.setUpPressed(message.get("upPressed").asBoolean());
                        pacman.setDownPressed(message.get("downPressed").asBoolean());
                        break;
                    default:
                        throw new RuntimeException("Unknown WS object type: " + type);
                }
            });
        })));
    }

    @Override
    public void render() {
        lastRender += Gdx.graphics.getDeltaTime();
        if (lastRender >= frameRate) {
            stateToSend.clear();
            for (ObjectMap.Entry<String, Pacman> pacmanEntry : pacmans) {
                Pacman pacman = pacmanEntry.value;
                pacman.act(lastRender);
                stateToSend.add(pacman);
            }

            lastRender = 0;

            String stateJson = json.toJson(stateToSend);

            sendToEverybody(stateJson);
        }
    }

    private void sendToEverybody(String json) {
        pool.execute(() -> {

            for (StandardWebSocketSession session : socketHandler.getSessions()) {
                try {
                    if (session.isOpen()) {
                        session.getNativeSession().getBasicRemote().sendText(json);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
