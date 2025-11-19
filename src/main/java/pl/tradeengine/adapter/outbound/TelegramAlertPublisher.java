package pl.tradeengine.adapter.outbound.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.port.AlertPublisher;
import java.util.List;

@Slf4j
@Service
@Profile("prod") // Aktywne tylko na profilu "prod"
public class TelegramAlertPublisher implements AlertPublisher {

    private final SimpleTelegramBot bot;

    public TelegramAlertPublisher(@Value("${app.telegram.bot-token}") String botToken,
                                  @Value("${app.telegram.chat-id}") String chatId) {
        this.bot = new SimpleTelegramBot(botToken, chatId);
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this.bot);
            log.info("Telegram bot registered successfully!");
        } catch (TelegramApiException e) {
            log.error("Error registering Telegram bot", e);
        }
    }

    @Override
    public void publish(List<AlertToSend> alerts) {
        for (AlertToSend alert : alerts) {
            bot.sendMessage(alert.toString()); // Używamy toString(), które poprawiłeś!
        }
    }

    // Wewnętrzna klasa bota, żeby nie zaśmiecać projektu
    private static class SimpleTelegramBot extends TelegramLongPollingBot {
        private final String chatId;

        public SimpleTelegramBot(String botToken, String chatId) {
            super(botToken);
            this.chatId = chatId;
        }

        @Override
        public void onUpdateReceived(Update update) {
            // Ignorujemy przychodzące wiadomości, bot ma tylko wysyłać
        }

        @Override
        public String getBotUsername() {
            return "TradeEngineBot"; // Dowolna nazwa
        }

        public void sendMessage(String message) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(message);
            try {
                execute(sendMessage);
                log.info("Sent alert to Telegram: {}", message);
            } catch (TelegramApiException e) {
                log.error("Failed to send message to Telegram", e);
            }
        }
    }
}
