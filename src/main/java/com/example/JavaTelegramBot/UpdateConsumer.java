package com.example.JavaTelegramBot;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class UpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final RestTemplate restTemplate;
    @Value("${openweathermap.api.key}")
    private String openWeatherApiKey;

    public UpdateConsumer(@Value("${telegram.bot.token}") String botToken, RestTemplate restTemplate) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.restTemplate = restTemplate;
    }

    @SneakyThrows
    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            String messageText = update.getMessage().getText().toLowerCase();
            Long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendMainMenu(chatId);
            } else if (messageText.equals("/keyboard")) {
                sendReplyKeyBoard(chatId);
            } else if (messageText.equals("/weather") || messageText.equals("погода")) {
                sendCitySelection(chatId);
            } else {
                SendMessage message = SendMessage
                        .builder()
                        .text("I don't get your message: " + update.getMessage().getText())
                        .chatId(chatId)
                        .build();
                telegramClient.execute(message);
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    @SneakyThrows
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        var data = callbackQuery.getData();
        var chatId = callbackQuery.getFrom().getId();
        var user = callbackQuery.getFrom();

        if (data.startsWith("weather_")) {
            String city = data.replace("weather_", "");
            sendWeatherInfo(chatId, city);
        } else {
            switch (data) {
                case "my_name" -> sendMyName(chatId, user);
                case "random" -> sendRandom(chatId);
                case "long_process" -> sendImage(chatId);
                case "weather" -> sendCitySelection(chatId);
                default -> sendMessage(chatId, "Unknown command");
            }
        }
    }

    @SneakyThrows
    private void sendMessage(Long chatId, String message) {
        SendMessage sendMessage = SendMessage
                .builder()
                .text(message)
                .chatId(chatId)
                .build();
        telegramClient.execute(sendMessage);
    }

    @SneakyThrows
    private void sendImage(Long chatId) {
        sendMessage(chatId, "Lunch image loading");
        new Thread(() -> {
            var imageUrl = "https://picsum.photos/200";
            try {
                URL url = new URL(imageUrl);
                var inputStream = url.openStream();
                SendPhoto sendPhoto = SendPhoto
                        .builder()
                        .chatId(chatId)
                        .photo(new InputFile(inputStream, "random.jpg"))
                        .caption("Your random picture:")
                        .build();
                telegramClient.execute(sendPhoto);
            } catch (IOException | TelegramApiException e) {
                sendMessage(chatId, "Ошибка загрузки изображения. Попробуйте позже!");
            }
        }).start();
    }

    @SneakyThrows
    private void sendRandom(Long chatId) {
        var randomInt = ThreadLocalRandom.current().nextInt(1, 100);
        sendMessage(chatId, "Your random number is " + randomInt);
    }

    @SneakyThrows
    private void sendMyName(Long chatId, User user) {
        var text = "Hello!\n\nYour name is: %s\nYour username is: @%s"
                .formatted(user.getFirstName() + " " + user.getLastName(), user.getUserName());
        sendMessage(chatId, text);
    }

    @SneakyThrows
    private void sendCitySelection(Long chatId) {
        SendMessage sendMessage = SendMessage
                .builder()
                .text("Выберите город для просмотра погоды:")
                .chatId(chatId)
                .build();
        List<InlineKeyboardRow> buttons = new ArrayList<>();
        String[] cities = {"Moscow", "Saint Petersburg", "Kyiv", "London", "New York"};
        for (String city : cities) {
            buttons.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text(city)
                            .callbackData("weather_" + city)
                            .build()
            ));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(buttons);
        sendMessage.setReplyMarkup(markup);
        telegramClient.execute(sendMessage);
    }

    @SneakyThrows
    private void sendWeatherInfo(Long chatId, String city) {
        try {
            String url = String.format("http://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric&lang=ru", city, openWeatherApiKey);
            WeatherResponse response = restTemplate.getForObject(url, WeatherResponse.class);
            if (response != null && response.getMain() != null) {
                String weatherText = String.format(
                        "Погода в %s:\n" +
                                "Температура: %.1f°C\n" +
                                "Ощущается как: %.1f°C\n" +
                                "Влажность: %d%%\n" +
                                "Описание: %s",
                        city,
                        response.getMain().getTemp(),
                        response.getMain().getFeelsLike(),
                        response.getMain().getHumidity(),
                        response.getWeather().get(0).getDescription()
                );
                sendMessage(chatId, weatherText);
            } else {
                sendMessage(chatId, "Не удалось получить данные о погоде для " + city);
            }
        } catch (Exception e) {
            sendMessage(chatId, "Ошибка при получении погоды. Попробуйте позже!");
        }
    }

    @SneakyThrows
    private void sendReplyKeyBoard(Long chatId) {
        SendMessage message = SendMessage
                .builder()
                .chatId(chatId)
                .text("This is just keyboard: ")
                .build();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow keyboardRow = new KeyboardRow("Hello!", "Picture");
        keyboardRows.add(keyboardRow);
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(keyboardRows);
        message.setReplyMarkup(markup);
        telegramClient.execute(message);
    }

    @SneakyThrows
    private void sendMainMenu(Long chatId) {
        SendMessage sendMessage = SendMessage
                .builder()
                .text("Welcome to the main menu! Choose an option:")
                .chatId(chatId)
                .build();
        var button1 = InlineKeyboardButton
                .builder()
                .text("What is your name?")
                .callbackData("my_name")
                .build();
        var button2 = InlineKeyboardButton
                .builder()
                .text("Random digit")
                .callbackData("random")
                .build();
        var button3 = InlineKeyboardButton
                .builder()
                .text("Loading picture")
                .callbackData("long_process")
                .build();
        var button4 = InlineKeyboardButton
                .builder()
                .text("Show weather")
                .callbackData("weather")
                .build();
        List<InlineKeyboardRow> buttons = List.of(
                new InlineKeyboardRow(button1),
                new InlineKeyboardRow(button2),
                new InlineKeyboardRow(button3),
                new InlineKeyboardRow(button4)
        );
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(buttons);
        sendMessage.setReplyMarkup(markup);
        telegramClient.execute(sendMessage);
    }

    // Классы для десериализации ответа OpenWeatherMap
    public static class WeatherResponse {
        private Main main;
        private List<Weather> weather;
        public Main getMain() { return main; }
        public void setMain(Main main) { this.main = main; }
        public List<Weather> getWeather() { return weather; }
        public void setWeather(List<Weather> weather) { this.weather = weather; }
    }

    public static class Main {
        private double temp;
        private double feelsLike;
        private int humidity;
        public double getTemp() { return temp; }
        public void setTemp(double temp) { this.temp = temp; }
        public double getFeelsLike() { return feelsLike; }
        public void setFeelsLike(double feelsLike) { this.feelsLike = feelsLike; }
        public int getHumidity() { return humidity; }
        public void setHumidity(int humidity) { this.humidity = humidity; }
    }

    public static class Weather {
        private String description;
        public String getDescription() { return description; }
    }
}
