package shine.geo;

/**
 * Тестовый запуск геолокации.
 *.
 * Логика:
 * 1) Если в args[0] передан IP — используем его.
 * 2) Иначе пробуем узнать внешний IP текущей машины.
 * 3) Если не удалось — берём константу TEST_IP.
 * 4) Вызываем GeoLookupService.resolveCountryCityOrIp(...) и печатаем результат.
 */
public class GeoLookupTestMain {

    // Константа на случай, если не удалось узнать внешний IP.
    private static final String TEST_IP = "8.8.8.8";

    public static void main(String[] args) {
        String ip;

        if (args.length > 0 && args[0] != null && !args[0].isBlank()) {
            ip = args[0].trim();
            System.out.println("Используем IP из аргумента: " + ip);
        } else {
            // Пытаемся узнать внешний IP
            String detectedIp = GeoLookupService.fetchPublicIpOrDefault(TEST_IP);
            if (TEST_IP.equals(detectedIp)) {
                System.out.println("Не удалось определить внешний IP, используем тестовый: " + TEST_IP);
            } else {
                System.out.println("Определён внешний IP: " + detectedIp);
            }
            ip = detectedIp;
        }

        String result = GeoLookupService.resolveCountryCityOrIp(ip);
        System.out.println("Результат геолокации: " + result);
    }
}
