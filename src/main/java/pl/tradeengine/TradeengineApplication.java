package pl.tradeengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TradeengineApplication {

    private static final Logger log = LoggerFactory.getLogger(TradeengineApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(TradeengineApplication.class, args);
        System.out.println("dudu daj buziaka");
        log.info("Starting TradeEngine jebac...");
	}

}
