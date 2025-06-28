package com.example.JavaTelegramBot;

import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
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

    public UpdateConsumer() {
        this.telegramClient = new OkHttpTelegramClient("7879780491:AAHvxJHAZN08x5CG70DMA6csJGk-HuY0_vM");
    }


    @SneakyThrows
    @Override
    public void consume(Update update) {

        if (update.hasMessage()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            if(messageText.equals("/start")){
                sendMainMenu(chatId);
            }
            else if(messageText.equals("/keyboard")){
                sendReplyKeyBoard(chatId);
            }
            else {
                SendMessage message = SendMessage
                        .builder()
                        .text("I don't get your message: " + update.getMessage().getText())
                        .chatId(chatId)
                        .build();

                telegramClient.execute(message);
            }
        } else if(update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }

    }

    @SneakyThrows
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        var data = callbackQuery.getData();
        var chatId = callbackQuery.getFrom().getId();
        var user = callbackQuery.getFrom();

        switch (data){
            case "my_name" -> sendMyName(chatId, user);
            case "random" -> sendRandom(chatId);
            case "long_process" -> sendImage(chatId);
            default -> sendMessage(chatId, "Unknown command");
        }
    }

    @SneakyThrows
    private void sendMessage(Long chatId, String message){
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

            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (IOException | TelegramApiException e) {
                throw new RuntimeException(e);
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
                .formatted(
                        user.getFirstName() + " " + user.getLastName(), user.getUserName()
                );

        sendMessage(chatId, text);
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


        List<InlineKeyboardRow> buttons = List.of(
                new InlineKeyboardRow(button1),
                new InlineKeyboardRow(button2),
                new InlineKeyboardRow(button3)
                );


        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(buttons);

        sendMessage.setReplyMarkup(markup);

        telegramClient.execute(sendMessage);
    }
}
