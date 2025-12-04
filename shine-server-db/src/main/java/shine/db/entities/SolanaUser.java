package shine.db.entities;

public class SolanaUser {

    private long loginId;
    private String login;
    private long bchId;
    private String pubkey0;
    private String pubkey1;
    private Integer bchLimit;   // может быть null

    public SolanaUser() {
    }

    public SolanaUser(long loginId,
                      String login,
                      long bchId,
                      String pubkey0,
                      String pubkey1,
                      Integer bchLimit) {
        this.loginId = loginId;
        this.login = login;
        this.bchId = bchId;
        this.pubkey0 = pubkey0;
        this.pubkey1 = pubkey1;
        this.bchLimit = bchLimit;
    }

    public long getLoginId() {
        return loginId;
    }

    public void setLoginId(long loginId) {
        this.loginId = loginId;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public long getBchId() {
        return bchId;
    }

    public void setBchId(long bchId) {
        this.bchId = bchId;
    }

    public String getPubkey0() {
        return pubkey0;
    }

    public void setPubkey0(String pubkey0) {
        this.pubkey0 = pubkey0;
    }

    public String getPubkey1() {
        return pubkey1;
    }

    public void setPubkey1(String pubkey1) {
        this.pubkey1 = pubkey1;
    }

    public Integer getBchLimit() {
        return bchLimit;
    }

    public void setBchLimit(Integer bchLimit) {
        this.bchLimit = bchLimit;
    }
}
