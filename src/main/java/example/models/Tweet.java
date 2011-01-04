package example.models;

import java.io.Serializable;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="Tweet")
public class Tweet implements Serializable
{
    @Id
    private UUID key;
    @Column(name="uname")
    private String uname;
    @Column(name="body")
    private String body;

    public Tweet(UUID key, String uname, String body) {
        this.key = key;
        this.uname = uname;
        this.body = body;
    }

    public UUID getKey() {
        return key;
    }

    public String getUname() {
        return uname;
    }

    public String getBody() {
        return body;
    }

    public void setKey(UUID key) {
      this.key = key;
    }

    public void setUname(String uname) {
      this.uname = uname;
    }

    public void setBody(String body) {
      this.body = body;
    }
    
}
