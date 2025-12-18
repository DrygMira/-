package shine.db.entities;

/**
 * Запись блока (таблица blocks).
 *
 * Идея:
 * - Храним и "глобальную" позицию (blockGlobalNumber + blockGlobalPreHashe),
 *   и "линейную" позицию (blockLineIndex + blockLineNumber + blockLinePreHashe),
 *   плюс полезные поля маршрутизации (to*).
 *
 * В БД:
 * - int64 -> INTEGER (Java long)
 * - int32 -> INTEGER (Java int)
 * - int16 -> INTEGER (Java int/short)
 * - bytes -> BLOB (Java byte[])
 * - hashes -> TEXT
 */
public class BlockEntry {

    private long loginId;                 // int64
    private long blockchainId;            // int64
    private int  blockGlobalNumber;       // int32
    private String blockGlobalPreHashe;   // TEXT

    private int  blockLineIndex;          // int16 (храним как int)
    private int  blockLineNumber;         // int32
    private String blockLinePreHashe;     // TEXT

    private int  msgType;                 // int16 (храним как int)

    private byte[] blockByte;             // BLOB

    private long toLoginId;               // int64
    private int  toBlockchainId;          // int32
    private int  toBlockGlobalNumber;     // int32
    private String toBlockHashe;          // TEXT

    public BlockEntry() {}

    public BlockEntry(long loginId,
                      long blockchainId,
                      int blockGlobalNumber,
                      String blockGlobalPreHashe,
                      int blockLineIndex,
                      int blockLineNumber,
                      String blockLinePreHashe,
                      int msgType,
                      byte[] blockByte,
                      long toLoginId,
                      int toBlockchainId,
                      int toBlockGlobalNumber,
                      String toBlockHashe) {
        this.loginId = loginId;
        this.blockchainId = blockchainId;
        this.blockGlobalNumber = blockGlobalNumber;
        this.blockGlobalPreHashe = blockGlobalPreHashe;
        this.blockLineIndex = blockLineIndex;
        this.blockLineNumber = blockLineNumber;
        this.blockLinePreHashe = blockLinePreHashe;
        this.msgType = msgType;
        this.blockByte = blockByte;
        this.toLoginId = toLoginId;
        this.toBlockchainId = toBlockchainId;
        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHashe = toBlockHashe;
    }

    public long getLoginId() { return loginId; }
    public void setLoginId(long loginId) { this.loginId = loginId; }

    public long getBlockchainId() { return blockchainId; }
    public void setBlockchainId(long blockchainId) { this.blockchainId = blockchainId; }

    public int getBlockGlobalNumber() { return blockGlobalNumber; }
    public void setBlockGlobalNumber(int blockGlobalNumber) { this.blockGlobalNumber = blockGlobalNumber; }

    public String getBlockGlobalPreHashe() { return blockGlobalPreHashe; }
    public void setBlockGlobalPreHashe(String blockGlobalPreHashe) { this.blockGlobalPreHashe = blockGlobalPreHashe; }

    public int getBlockLineIndex() { return blockLineIndex; }
    public void setBlockLineIndex(int blockLineIndex) { this.blockLineIndex = blockLineIndex; }

    public int getBlockLineNumber() { return blockLineNumber; }
    public void setBlockLineNumber(int blockLineNumber) { this.blockLineNumber = blockLineNumber; }

    public String getBlockLinePreHashe() { return blockLinePreHashe; }
    public void setBlockLinePreHashe(String blockLinePreHashe) { this.blockLinePreHashe = blockLinePreHashe; }

    public int getMsgType() { return msgType; }
    public void setMsgType(int msgType) { this.msgType = msgType; }

    public byte[] getBlockByte() { return blockByte; }
    public void setBlockByte(byte[] blockByte) { this.blockByte = blockByte; }

    public long getToLoginId() { return toLoginId; }
    public void setToLoginId(long toLoginId) { this.toLoginId = toLoginId; }

    public int getToBlockchainId() { return toBlockchainId; }
    public void setToBlockchainId(int toBlockchainId) { this.toBlockchainId = toBlockchainId; }

    public int getToBlockGlobalNumber() { return toBlockGlobalNumber; }
    public void setToBlockGlobalNumber(int toBlockGlobalNumber) { this.toBlockGlobalNumber = toBlockGlobalNumber; }

    public String getToBlockHashe() { return toBlockHashe; }
    public void setToBlockHashe(String toBlockHashe) { this.toBlockHashe = toBlockHashe; }
}