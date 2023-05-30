package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CarSearchBot extends TelegramLongPollingBot {

    private Connection connection;
    private String choosing_id_car_save;
    private String data;
    private boolean access = false;

    public CarSearchBot() {

        List<BotCommand> botCommandList = new ArrayList<BotCommand>();
        botCommandList.add(new BotCommand("/start", "Запуск бота"));
        botCommandList.add(new BotCommand("/login", "Авторизация по данным"));
        botCommandList.add(new BotCommand("/registration", "Регистрация по данным"));
        botCommandList.add(new BotCommand("/search", "Показать каталог автомобилей"));

        try {
            this.execute(new SetMyCommands(botCommandList, new BotCommandScopeDefault(), null));
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/carsearch", "root", "1991");
        } catch (TelegramApiException error) {
            throw new RuntimeException(error);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws TelegramApiException {
        CarSearchBot carSearchBot = new CarSearchBot();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);

        try {
            telegramBotsApi.registerBot(carSearchBot);
        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            if (messageText.equals("/start")) {
                sendMessage(chatId, "Добро пожаловать! Выберите вход или регистрацию!");
            } else if ((messageText.equals("/registration")) || (messageText.startsWith("Регистрация"))) {
                processRegistration(chatId, messageText);
            } else if ((messageText.equals("/login")) || (messageText.startsWith("Вход"))) {
                processLogin(chatId, messageText);
            } else if (messageText.equals("/search")) {
                choosingCar(chatId);
            } else {
                sendMessage(chatId, "Неизвестная команда!");
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageID = update.getCallbackQuery().getMessage().getMessageId();
            long chatID = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.split(":")[0].equals("car") || callbackData.equals("cancel")) {
                choosing_id_car_save = callbackData.split(": ")[1];
                dataCar();
                EditMessageText messageText = new EditMessageText();
                messageText.setChatId(String.valueOf(chatID));
                messageText.setText(data);
                messageText.setMessageId((int) messageID);

                try {
                    execute(messageText);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage sendMessage = SendMessage.builder().chatId(chatId.toString()).text(text).build();

        try {
            execute(sendMessage);
        } catch (TelegramApiException error) {
            error.printStackTrace();
        }
    }

    private void processRegistration(Long chatId, String text) {
        Random random = new Random();

        if (text.startsWith("Регистрация")) {
            text = text.substring(13);
        }

        String[] data = text.split(", ");

        if (data.length != 3) {
            sendMessage(chatId, "Пожалуйста, введите данные в формате [Регистрация: ФИО, номер телефона, пароль (до 8 символов)]");
            return;
        }

        String user_id = Integer.toString(random.nextInt((99999 - 10000) + 1) + 10000);
        String user_name = data[0].trim();
        String user_phone = data[1].trim();
        String user_password = data[2].trim();

        try {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO User (ID_user, Name_user, Phone_user, Password_user) VALUES (?, ?, ?, ?)");
            statement.setString(1, user_id);
            statement.setString(2, user_name);
            statement.setString(3, user_phone);
            statement.setString(4, user_password);

            statement.executeUpdate();

            sendMessage(chatId, "Ваши данные сохранены, теперь авторизируйтесь!");
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Произошла ошибка при сохранении данных!");
        }
    }

    private void processLogin(Long chatId, String text) {

        if (text.startsWith("Вход")) {
            text = text.substring(6);
        }

        String[] data = text.split(", ");

        if (data.length != 2) {
            sendMessage(chatId, "Пожалуйста, введите данные в формате: 'Вход: номер телефона, пароль (до 8 символов)'");
            return;
        }

        String phoneNumber = data[0].trim();
        String password = data[1].trim();

        try {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM User WHERE Phone_user=? AND Password_user=?");
            statement.setString(1, phoneNumber);
            statement.setString(2, password);

            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                sendMessage(chatId, "Вы успешно вошли в систему!");
                access = true;
            } else {
                sendMessage(chatId, "Введенные данные не найдены. Пожалуйста, проверьте правильность введенных данных.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "Произошла ошибка при входе в систему.");
        }
    }

    private void choosingCar(Long chatId) {

        if (access) {
            SendMessage sendMessage = SendMessage.builder().chatId(chatId.toString()).text("Выберите автомобиль:").build();

            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            try {
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT Name_car, ID_car FROM ad_car");

                while (resultSet.next()) {
                    String carName = resultSet.getString("Name_car");
                    String carID = resultSet.getString("Id_car");

                    InlineKeyboardButton button = new InlineKeyboardButton();
                    button.setText(carName);
                    button.setCallbackData("car: " + carID);

                    List<InlineKeyboardButton> rowInline = new ArrayList<>();
                    rowInline.add(button);
                    rowsInline.add(rowInline);
                }
                keyboardMarkup.setKeyboard(rowsInline);
            } catch (SQLException error) {
                error.printStackTrace();
            }

            sendMessage.setReplyMarkup(keyboardMarkup);

            try {
                execute(sendMessage);
            } catch (TelegramApiException error) {
                error.printStackTrace();
            }
        } else {
            sendMessage(chatId, "Сначала войдите в систему!");
        }
    }

    private void dataCar() {

        String nameCar = "";
        String ownerPhone = "";
        String priceCar = "";
        String yearCar = "";
        String descriptionCar = "";
        String cityCar = "";

        try {
            Statement statement = connection.createStatement();

            ResultSet resultSet = statement.executeQuery("SELECT Id_car, Name_car, Owner_phone, Price_car, Year_car, Description_Car, City_car FROM Ad_car");

            while (resultSet.next()) {
                if (resultSet.getString("Id_car").equals(choosing_id_car_save)) {
                    nameCar = resultSet.getString("Name_car");
                    ownerPhone = resultSet.getString("Owner_phone");
                    priceCar = resultSet.getString("Price_car");
                    yearCar = resultSet.getString("Year_car");
                    cityCar = resultSet.getString("City_car");
                    descriptionCar = resultSet.getString("Description_Car");
                }
            }


            data = "Вы просматриваете втомобиль " + nameCar + " в городе " + cityCar + "\n" +
                    "Цена автомобиля: " + priceCar  + "₽\n" +
                    "Автомобиль был произведён в " + yearCar + " году\n" +
                    "Описание автомобиля: " + descriptionCar + "\n\n**Номер владельца: " + ownerPhone + "**";

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return "CarSearch";
    }

    public String getBotToken() {
        return "6200918156:AAE4McLtN0DRWN8uPPsBQTl8aJqPZftO8Do";
    }
}
