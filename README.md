
     官网：http://www.rabbit-open.top/rabbit/orm2

================================== V 2.0 =========================================
	
    1、调整了框架架构，解耦了数据源，使得RABBIT ORM可以使用其它数据源工作
    
    2、不再支持Column 注解中的usedForMapping功能。统一通过API增添过滤条件
    
    3、强化了SQLQuery和JDBCQuery。两者都不再支持直接在代码写sql实现，转为在xml中写sql
    
    4、强化了Query类。动态添加内链接条件不再有表个数的限制
    
    5、解耦了方言实现和功能代码之间的关联

================================== V 2.1 =========================================	
	
	1、新增dml操作前置过滤器 DMLFilter
	
            
================================== V 2.2 =========================================  

    1、新增分区表的支持, 分区表策略对SQLQuery、NamedQuery对象生效， 需要sql开发人员自己确认使用哪张表
    
                        分区表可以关联查询其它表，但是不能被其它表关联查询
                        外键字段不参与分区规则计算                
        

    2、修正2.0重构后meta信息中joinMetas信息在使用过程中被污染的bug。 修正为每次使用时都clone一个副本
    
    
================================== V 2.3 =========================================   
    
    1、新增分库支持。  规则：中间表必须和主表一个库， 关联表必须和主表一个库

    
================================== V 2.4 =========================================   
    
    1、完善RabbitDataSource新增SQLException关闭连接功能，主要是为应对网络闪断异常。是对DataSourceMonitor的补充完善。

================================== V 2.4.1 =========================================   
    
    1、扩展@Column注解，新增keyWord方法，标记当前字段名是数据库关键字。在生成dml语句时就会特殊处理该字段
    
    2、重构ddl代码，去掉部分重复代码
    
    3、将SpringDaoAdapter中的createNameMappedQuery更名为createFieldsMappingQuery
    
    4、新增支持【主表对象】简单OR类型过滤条件的API ----> setMultiDropFilter。MultiDropFilter不参与分表计算因子
    

================================== V 2.4.2 =========================================   
    
    1、新增对SQLite3的支持
    
    2、AbstractQuery 新增list和unique方法。
    
    3、扩展查询对象功能， 允许同一个查询对象重复执行execute、count、list以及unique方法
    
    
    
    
    
    
    
          