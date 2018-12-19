package example.models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="User")
public class User {
    @Id
    private String name;
    @Column(name="password")
    private String password;
    
    public User(String name, String password) {
        this.name = name;
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
      this.name = name;
    }


    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }


    public boolean comparePasswords(String compare) {
        return password.equals(compare);
    }
}
