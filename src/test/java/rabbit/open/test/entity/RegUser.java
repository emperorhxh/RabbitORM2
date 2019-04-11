package rabbit.open.test.entity;

import java.util.Date;
import java.util.List;

import rabbit.open.orm.annotation.Column;
import rabbit.open.orm.annotation.Entity;
import rabbit.open.orm.annotation.OneToMany;
import rabbit.open.orm.annotation.PrimaryKey;
import rabbit.open.orm.dml.policy.Policy;

@Entity("REG_USER")
public class RegUser {

	@PrimaryKey(policy = Policy.AUTOINCREMENT)
	@Column("ID")
	private Integer id;

	@Column("START_")
	private Date start;

	@Column("END_")
	private Date end;

	@Column("FROM_")
	private Integer from;

	@Column("TO_")
	private Integer to;

	@OneToMany(joinColumn = "USER_ID")
	private List<RegRoom> rooms;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Date getStart() {
		return start;
	}

	public void setStart(Date start) {
		this.start = start;
	}

	public Date getEnd() {
		return end;
	}

	public void setEnd(Date end) {
		this.end = end;
	}

	public Integer getFrom() {
		return from;
	}

	public void setFrom(Integer from) {
		this.from = from;
	}

	public Integer getTo() {
		return to;
	}

	public void setTo(Integer to) {
		this.to = to;
	}

	public List<RegRoom> getRooms() {
		return rooms;
	}

	public void setRooms(List<RegRoom> rooms) {
		this.rooms = rooms;
	}
	
	

}
