package pl.romcio.driperska;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DriperskaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriperskaApplication.class, args);
    }
}
