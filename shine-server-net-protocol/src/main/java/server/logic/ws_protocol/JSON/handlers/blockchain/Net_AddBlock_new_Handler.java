package server.logic.ws_protocol.JSON.handlers.blockchain;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.entyties.blockchain.Net_AddBlock_Request;
import server.logic.ws_protocol.JSON.entyties.blockchain.Net_AddBlock_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.WireCodes;

public final class Net_AddBlock_new_Handler implements JsonMessageHandler {

    @Override
    public Net_Response handle(Net_Request baseReq, ConnectionContext ctx) throws Exception {
        Net_AddBlock_Request req = (Net_AddBlock_Request) baseReq;

        var r = BlockchainStateService_new.getInstance().addBlockAtomically(
                req.getLogin(),
                req.getBlockchainName(),
                req.getGlobalNumber(),
                req.getPrevGlobalHash(),
                req.getBlockBytesB64()
        );

        Net_AddBlock_Response resp = new Net_AddBlock_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());

        if (r.isOk()) {
            resp.setStatus(WireCodes.Status.OK);
            resp.setReasonCode(null);
        } else {
            resp.setStatus(r.httpStatus);
            resp.setReasonCode(r.reasonCode);
        }

        // Даже при ошибке (например bad_global_sequence) можно вернуть “что сервер считает последним”
        resp.setServerLastGlobalNumber(r.serverLastGlobalNumber);
        if (r.serverLastGlobalHash != null) {
            resp.setServerLastGlobalHash(r.serverLastGlobalHash);
        }

        return resp;
    }
}