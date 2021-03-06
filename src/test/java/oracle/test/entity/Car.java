package oracle.test.entity;

import rabbit.open.orm.annotation.Column;
import rabbit.open.orm.annotation.Entity;
import rabbit.open.orm.annotation.PrimaryKey;
import rabbit.open.orm.dml.policy.Policy;

@Entity("T_CAR")
public class Car {

    @PrimaryKey(policy=Policy.SEQUENCE, sequence="MYSEQ")
    @Column("ID")
    private Integer id;
    
    @Column("CAR_NO")
    private String carNo;
    
    @Column("USER_ID")
    private User user;

    @Column("TEAM_ID")
    private Team team;

    @Column("ZONE_ID")
    private Zone zone;
    
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCarNo() {
        return carNo;
    }

    public void setCarNo(String carNo) {
        this.carNo = carNo;
    }

    public Car(String carNo) {
        super();
        this.carNo = carNo;
    }

    public Car() {
        super();
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Car(String carNo, User user) {
        super();
        this.carNo = carNo;
        this.user = user;
    }

    public Car(String carNo, Team team) {
        super();
        this.carNo = carNo;
        this.team = team;
    }

    @Override
    public String toString() {
        return "Car [id=" + id + ", carNo=" + carNo + ", user=" + user + "]";
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Zone getZone() {
        return zone;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    } 
    
    
}
