package sharding.test.db.service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import rabbit.open.orm.exception.RabbitDMLException;
import rabbit.open.orm.pool.SessionFactory;
import rabbit.open.orm.spring.SpringDaoAdapter;
import sharding.test.db.entity.RWUser;

/**
 * <b>Description  读写分离的服务</b>
 */
@Service
public class RWUserService extends SpringDaoAdapter<RWUser> {

    @Resource(name="readWriteSplitedSessionFactory")
    protected SessionFactory factory;
    
    @PostConstruct
    public void setUp(){
        setSessionFactory(factory);
    }
    
    public SessionFactory getFactory() {
        return factory;
    }
    
    @Transactional
    public void doTransaction() {
        add(new RWUser("xx", 100));
        add(new RWUser("xx1", 100));
    }
    

    @Transactional
    public void doRollBack() {
        add(new RWUser("yyy", 100));
        add(new RWUser("yyyy", 100));
        throw new RabbitDMLException("rollback");
    }
    
    
}
