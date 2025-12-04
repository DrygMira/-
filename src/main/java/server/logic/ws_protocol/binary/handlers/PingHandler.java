package server.logic.ws_protocol.binary.handlers;

import server.logic.ws_protocol.WireCodes;

/**
 * Обработчик команды PING.
 * Возвращает просто статус PONG.
 */
public class PingHandler implements MessageHandler {
    @Override
    public byte[] handle(byte[] msg) {
        return new byte[]{
                0, 0, 0, (byte) WireCodes.Status.PONG  // проще и быстрее, можно и через ByteBuffer
        };
    }
}
