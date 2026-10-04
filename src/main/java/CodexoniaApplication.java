import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAutoConfiguration
public class CodexoniaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodexoniaApplication.class, args);
    }

    @Bean
    HashController hashController() {
        return new HashController();
    }

    @Bean
    RunController runController() {
        return new RunController();
    }
}
