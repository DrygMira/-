package server.logic.ws_protocol.JSON.handlers.channels.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.ArrayList;
import java.util.List;

public class Net_GetMessageThread_Response extends Net_Response {
    private List<MessageNode> ancestors = new ArrayList<>();
    private MessageNode focus;
    private List<MessageNodeTree> descendants = new ArrayList<>();

    public List<MessageNode> getAncestors() { return ancestors; }
    public void setAncestors(List<MessageNode> ancestors) { this.ancestors = ancestors; }

    public MessageNode getFocus() { return focus; }
    public void setFocus(MessageNode focus) { this.focus = focus; }

    public List<MessageNodeTree> getDescendants() { return descendants; }
    public void setDescendants(List<MessageNodeTree> descendants) { this.descendants = descendants; }

    public static class MessageNode extends Net_GetChannelMessages_Response.MessageItem {
        private ChannelInfo channelInfo;

        public ChannelInfo getChannelInfo() { return channelInfo; }
        public void setChannelInfo(ChannelInfo channelInfo) { this.channelInfo = channelInfo; }
    }

    public static class ChannelInfo {
        private String ownerBlockchainName;
        private Net_GetChannelMessages_Response.BlockRef channelRoot;

        public String getOwnerBlockchainName() { return ownerBlockchainName; }
        public void setOwnerBlockchainName(String ownerBlockchainName) { this.ownerBlockchainName = ownerBlockchainName; }

        public Net_GetChannelMessages_Response.BlockRef getChannelRoot() { return channelRoot; }
        public void setChannelRoot(Net_GetChannelMessages_Response.BlockRef channelRoot) { this.channelRoot = channelRoot; }
    }

    public static class MessageNodeTree {
        private MessageNode node;
        private List<MessageNodeTree> children = new ArrayList<>();

        public MessageNode getNode() { return node; }
        public void setNode(MessageNode node) { this.node = node; }

        public List<MessageNodeTree> getChildren() { return children; }
        public void setChildren(List<MessageNodeTree> children) { this.children = children; }
    }
}
