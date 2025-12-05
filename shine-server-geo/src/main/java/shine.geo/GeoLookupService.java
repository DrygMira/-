package shine.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Сервис для геолокации по IP.
 *
 * Основной метод:
 *   resolveCountryCityOrIp(ip) -> "Country, City" или исходный ip, если не удалось.
 */
public final class GeoLookupService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // Сервис геолокации. Сейчас ip-api.com, можно потом вынести в конфиг.
    private static final String GEO_API_URL = "http://ip-api.com/json/";

    // Сервис для получения собственного внешнего IP
    private static final String PUBLIC_IP_URL = "https://api.ipify.org";

    private GeoLookupService() {
        // utility-класс
    }

    /**
     * Возвращает строку вида "Country, City" по IP.
     * Если запрос не удался, возвращает исходный ip.
     */
    public static String resolveCountryCityOrIp(String ip) {
        // На всякий случай простая защита от private/локальных IP (они всё равно не определяются)
        if (isPrivateOrLocalIp(ip)) {
            return ip;
        }

        try {
            String url = GEO_API_URL + ip + "?fields=status,country,city,message";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                return ip;
            }

            JsonNode root = JSON_MAPPER.readTree(response.body());
            String status = root.path("status").asText();
            if (!"success".equals(status)) {
                // Например: {"status":"fail","message":"private range"}
                return ip;
            }

            String country = root.path("country").asText(null);
            String city = root.path("city").asText(null);

            if (country == null && city == null) {
                return ip;
            }

            // Собираем аккуратную строку
            if (country != null && city != null) {
                return country + ", " + city;
            } else if (country != null) {
                return country;
            } else {
                return city;
            }
        } catch (IOException | InterruptedException e) {
            // В боевом коде можно логировать
            return ip;
        }
    }

    /**
     * Пытается получить внешний IP текущей машины через HTTP-сервис.
     * В случае ошибки возвращает fallbackIp.
     */
    public static String fetchPublicIpOrDefault(String fallbackIp) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PUBLIC_IP_URL))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                return fallbackIp;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return fallbackIp;
            }

            return body.trim();
        } catch (IOException | InterruptedException e) {
            // В боевом коде можно логировать
            return fallbackIp;
        }
    }

    /**
     * Примитивная проверка на частные и локальные IP.
     * Для внешней геолокации они бесполезны.
     */
    private static boolean isPrivateOrLocalIp(String ip) {
        if (ip == null) return true;

        ip = ip.trim();

        return ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("127.")
                || ip.startsWith("0.")
                || ip.startsWith("169.254.")
                // Диапазон 172.16.0.0 – 172.31.255.255
                || ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }
}
