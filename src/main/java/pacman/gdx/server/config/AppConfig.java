package pacman.gdx.server.config;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pacman.gdx.server.GameLoop;
import pacman.gdx.server.actors.Pacman;

@Configuration
public class AppConfig {
    @Bean
    public HeadlessApplication getApplication(GameLoop gameLoop){
        return new HeadlessApplication(gameLoop);
    }

    @Bean
    public Json getJson() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.addClassTag("pacman", Pacman.class);
        return json;
    }
}
