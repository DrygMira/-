package shine.db.entities;

/**
 * Запись блока (таблица blocks).
 */
public class BlockEntry {

    private String login;
    private String bchName;

    private int  blockGlobalNumber;
    private byte[] blockGlobalPreHashe;

    private int  blockLineIndex;
    private int  blockLineNumber;
    private byte[] blockLinePreHashe;

    private int  msgType;
    private int  msgSubType;

    private byte[] blockByte;

    private String toLogin;
    private String toBchName;
    private Integer toBlockGlobalNumber;
    private byte[] toBlockHashe;

    // новое
    private byte[] blockHash;
    private byte[] blockSignature;
    private Integer editedByBlockGlobalNumber;

    public BlockEntry() {}

    public BlockEntry(String login,
                      String bchName,
                      int blockGlobalNumber,
                      byte[] blockGlobalPreHashe,
                      int blockLineIndex,
                      int blockLineNumber,
                      byte[] blockLinePreHashe,
                      int msgType,
                      int msgSubType,
                      byte[] blockByte,
                      String toLogin,
                      String toBchName,
                      Integer toBlockGlobalNumber,
                      byte[] toBlockHashe,
                      byte[] blockHash,
                      byte[] blockSignature,
                      Integer editedByBlockGlobalNumber) {
        this.login = login;
        this.bchName = bchName;
        this.blockGlobalNumber = blockGlobalNumber;
        this.blockGlobalPreHashe = blockGlobalPreHashe;
        this.blockLineIndex = blockLineIndex;
        this.blockLineNumber = blockLineNumber;
        this.blockLinePreHashe = blockLinePreHashe;
        this.msgType = msgType;
        this.msgSubType = msgSubType;
        this.blockByte = blockByte;
        this.toLogin = toLogin;
        this.toBchName = toBchName;
        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHashe = toBlockHashe;
        this.blockHash = blockHash;
        this.blockSignature = blockSignature;
        this.editedByBlockGlobalNumber = editedByBlockGlobalNumber;
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getBchName() { return bchName; }
    public void setBchName(String bchName) { this.bchName = bchName; }

    public int getBlockGlobalNumber() { return blockGlobalNumber; }
    public void setBlockGlobalNumber(int blockGlobalNumber) { this.blockGlobalNumber = blockGlobalNumber; }

    public byte[] getBlockGlobalPreHashe() { return blockGlobalPreHashe; }
    public void setBlockGlobalPreHashe(byte[] blockGlobalPreHashe) { this.blockGlobalPreHashe = blockGlobalPreHashe; }

    public int getBlockLineIndex() { return blockLineIndex; }
    public void setBlockLineIndex(int blockLineIndex) { this.blockLineIndex = blockLineIndex; }

    public int getBlockLineNumber() { return blockLineNumber; }
    public void setBlockLineNumber(int blockLineNumber) { this.blockLineNumber = blockLineNumber; }

    public byte[] getBlockLinePreHashe() { return blockLinePreHashe; }
    public void setBlockLinePreHashe(byte[] blockLinePreHashe) { this.blockLinePreHashe = blockLinePreHashe; }

    public int getMsgType() { return msgType; }
    public void setMsgType(int msgType) { this.msgType = msgType; }

    public int getMsgSubType() { return msgSubType; }
    public void setMsgSubType(int msgSubType) { this.msgSubType = msgSubType; }

    public byte[] getBlockByte() { return blockByte; }
    public void setBlockByte(byte[] blockByte) { this.blockByte = blockByte; }

    public String getToLogin() { return toLogin; }
    public void setToLogin(String toLogin) { this.toLogin = toLogin; }

    public String getToBchName() { return toBchName; }
    public void setToBchName(String toBchName) { this.toBchName = toBchName; }

    public Integer getToBlockGlobalNumber() { return toBlockGlobalNumber; }
    public void setToBlockGlobalNumber(Integer toBlockGlobalNumber) { this.toBlockGlobalNumber = toBlockGlobalNumber; }

    public byte[] getToBlockHashe() { return toBlockHashe; }
    public void setToBlockHashe(byte[] toBlockHashe) { this.toBlockHashe = toBlockHashe; }

    public byte[] getBlockHash() { return blockHash; }
    public void setBlockHash(byte[] blockHash) { this.blockHash = blockHash; }

    public byte[] getBlockSignature() { return blockSignature; }
    public void setBlockSignature(byte[] blockSignature) { this.blockSignature = blockSignature; }

    public Integer getEditedByBlockGlobalNumber() { return editedByBlockGlobalNumber; }
    public void setEditedByBlockGlobalNumber(Integer editedByBlockGlobalNumber) { this.editedByBlockGlobalNumber = editedByBlockGlobalNumber; }
}