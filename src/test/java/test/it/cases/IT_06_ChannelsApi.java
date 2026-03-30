package test.it.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import test.it.utils.TestConfig;
import test.it.utils.json.JsonBuilders;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * IT_06_ChannelsApi
 *
 * Проверяет базовые happy-path сценарии для новых операций:
 *  - ListSubscriptionsFeed
 *  - GetChannelMessages
 *  - GetMessageThread
 */
public class IT_06_ChannelsApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String run() {
        TestResult r = new TestResult("IT_06_ChannelsApi");
        Duration t = Duration.ofSeconds(8);

        final String login = TestConfig.LOGIN();
        final String bchName = TestConfig.getBlockchainName(login);

        try (WsSession ws = WsSession.open()) {
            String feedResp = ws.call("ListSubscriptionsFeed", JsonBuilders.listSubscriptionsFeed(login, 200), t);
            check200(r, feedResp, "ListSubscriptionsFeed");

            int ownSize = JsonParsers.payloadArraySize(feedResp, "ownedChannels");
            if (ownSize < 0) {
                r.fail("ListSubscriptionsFeed: отсутствует ownedChannels array, resp=" + feedResp);
                fail("ownedChannels missing");
            }
            r.ok("ListSubscriptionsFeed: ownedChannels size=" + ownSize);

            String chResp = ws.call("GetChannelMessages", JsonBuilders.getChannelMessages(bchName, 0, "", 200, "asc"), t);
            check200(r, chResp, "GetChannelMessages");

            JsonNode chRoot = MAPPER.readTree(chResp);
            JsonNode messages = chRoot.path("payload").path("messages");
            if (!messages.isArray()) {
                r.fail("GetChannelMessages: payload.messages не массив, resp=" + chResp);
                fail("messages is not array");
            }
            r.ok("GetChannelMessages: messages size=" + messages.size());

            if (messages.size() > 0) {
                JsonNode first = messages.get(0);
                int blockNumber = first.path("messageRef").path("blockNumber").asInt(-1);
                String blockHash = first.path("messageRef").path("blockHash").asText("");

                if (blockNumber > 0 && !blockHash.isBlank()) {
                    String threadResp = ws.call(
                            "GetMessageThread",
                            JsonBuilders.getMessageThread(bchName, blockNumber, blockHash, 20, 2, 50),
                            t
                    );
                    check200(r, threadResp, "GetMessageThread");

                    JsonNode threadRoot = MAPPER.readTree(threadResp).path("payload");
                    if (!threadRoot.path("ancestors").isArray() || !threadRoot.has("focus") || !threadRoot.path("descendants").isArray()) {
                        r.fail("GetMessageThread: неверная форма payload, resp=" + threadResp);
                        fail("thread payload shape invalid");
                    }
                    r.ok("GetMessageThread: payload shape OK");
                } else {
                    r.ok("GetMessageThread: пропущено, у первого сообщения нет корректного ref");
                }
            } else {
                r.ok("GetMessageThread: пропущено, в канале нет сообщений");
            }

        } catch (Throwable e) {
            r.fail("IT_06_ChannelsApi упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    private static void check200(TestResult r, String resp, String op) {
        int st = JsonParsers.status(resp);
        if (st != 200) {
            r.fail(op + ": ожидали status=200, получили " + st + ", resp=" + resp);
            fail(op + " status=" + st);
        }
    }
}
