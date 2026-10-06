package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public class WeatherApp {

    public static void main(String[] args) throws Exception {
        double lat = 55.75;
        double lon = 37.62;
        int limit = 3;

        String apiKey = System.getenv("YANDEX_WEATHER_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Задайте YANDEX_WEATHER_API_KEY"
            );
        }

        URI uri = URI.create(String.format(
                Locale.ROOT,
                "https://api.weather.yandex.ru/v2/forecast"
                        + "?lat=%.6f&lon=%.6f&limit=%d",
                lat,
                lon,
                limit
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("X-Yandex-Weather-Key", apiKey.strip())
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );

        System.out.println("URL: " + uri);
        System.out.println("HTTP: " + response.statusCode());

        String rawJson = response.body();

        System.out.println("Полный JSON-ответ:");
        System.out.println(rawJson);

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "API вернул HTTP " + response.statusCode()
            );
        }

        JsonObject root = JsonParser.parseString(rawJson)
                .getAsJsonObject();

        double currentTemperature = requiredNumber(
                root, "fact", "temp"
        );

        System.out.println(
                "Текущая температура: "
                        + currentTemperature + " °C"
        );

        double average = averageTemperature(root, limit);

        System.out.printf(
                Locale.ROOT,
                "Средняя дневная температура за %d суток: %.2f °C%n",
                limit,
                average
        );
    }

    static double requiredNumber(
            JsonElement root,
            String... path
    ) {
        JsonElement value = root;

        for (String field : path) {
            if (value == null || !value.isJsonObject()) {
                throw new IllegalStateException(
                        "Нет поля " + String.join(".", path)
                );
            }

            value = value.getAsJsonObject().get(field);
        }

        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalStateException(
                    "Нет числового поля "
                            + String.join(".", path)
            );
        }

        double number = value.getAsDouble();

        if (!Double.isFinite(number)) {
            throw new IllegalStateException(
                    "Некорректное число в "
                            + String.join(".", path)
            );
        }

        return number;
    }

    static double averageTemperature(
            JsonObject root,
            int limit
    ) {
        if (limit < 1) {
            throw new IllegalArgumentException(
                    "limit должен быть положительным"
            );
        }

        JsonElement forecastsElement = root.get("forecasts");

        if (forecastsElement == null
                || !forecastsElement.isJsonArray()) {
            throw new IllegalStateException(
                    "В ответе нет массива forecasts"
            );
        }

        JsonArray forecasts = forecastsElement.getAsJsonArray();

        if (forecasts.size() < limit) {
            throw new IllegalStateException(
                    "Получено меньше "
                            + limit + " суток прогноза"
            );
        }

        double sum = 0.0;

        for (int i = 0; i < limit; i++) {
            sum += requiredNumber(
                    forecasts.get(i),
                    "parts",
                    "day",
                    "temp_avg"
            );
        }

        return sum / limit;
    }
}