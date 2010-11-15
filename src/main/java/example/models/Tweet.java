package example.models;

import java.io.Serializable;
import java.util.UUID;


public class Tweet implements Serializable
{
    private final UUID key;
    private final String uname;
    private final String body;

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
}
