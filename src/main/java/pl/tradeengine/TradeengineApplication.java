package pl.tradeengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.tradeengine.config.StrategyProperties;

@SpringBootApplication
@EnableConfigurationProperties(StrategyProperties.class)
public class TradeengineApplication {

	static void main(String[] args) {
		SpringApplication.run(TradeengineApplication.class, args);
	}

}
