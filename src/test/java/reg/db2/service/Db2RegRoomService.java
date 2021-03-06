package reg.db2.service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import rabbit.open.orm.pool.SessionFactory;
import rabbit.open.orm.spring.SpringDaoAdapter;
import reg.db2.entity.RegRoom;

@Service
public class Db2RegRoomService extends SpringDaoAdapter<RegRoom> {

	@Resource(name = "sessionFactory4db2")
	protected SessionFactory factory;

	@PostConstruct
	public void setUp() {
		setSessionFactory(factory);
	}

	public SessionFactory getFactory() {
		return factory;
	}
}
