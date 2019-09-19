# RabbitMQ进阶    
- 消息何去何从...................................................[1](#Where-Is-Message)    
    - mandatory参数...................................................[1.1](#mandatory)    
    - immediate参数...................................................[1.2](#immediate)   
    - 备份交换器...................................................[1.3](#Alternate-Exchange)   
- 过期时间...................................................[2](#TTL)
    - 设置消息的TTL...................................................[2.1](#Message-TTL)   
    - 设置队列的TTL...................................................[2.2](#Queue-TTL)    
- 死信队列...................................................[3](#DLX)   
- 延迟队列...................................................[4](#Delay-Queue)    
- 优先级队列...................................................[5](#Priority-Queue)    
- RPC实现...................................................[6](#RPC)
- 持久化...................................................[7](#Persistence)  
- 生产者确认...................................................[8](#Confirm)  
    - 事务机制...................................................[8.1](#Transaction)
    - 发送方确认机制...................................................[8.2](#Publisher-Confirm) 
- 消费端要点介绍...................................................[9](#Consumer)
    - 消息分发...................................................[9.1](#Message-Distribution)
    - 消息顺序性...................................................[9.2](#Message-Order)
    - 弃用QueueingConsumer...................................................[9.3](#Abandon-QueueingConsumer) 
- 消息传输保障...................................................[10](#Message-Transmission-Guarantee)     




***   

## Where-Is-Message    
　　mandatory和immediate是channel.basicPublish方法中的两个参数，它们都有当消息传递过程中不可达目的地时将消息返回给生产者的功能。RabbitMQ提供的备份交换器可以将未能被交换器路由的消息（没有绑定队列或没有匹配的绑定）存储起来，而不用返回给客户端。    

### mandatory    
　　当mandatory参数设置为true时，交换器无法根据自身的类型和路由键找到一个符合条件的队列，那么RabbitMQ会调用Basic.Return命令将消息返回给生产者。当mandatory参数为false时，出现上述情况消息直接丢弃。    
　　生产者获取上述未被正确路由到合适队列的消息，是通过调用channel.addReturnListener来添加ReturnListener监听器实现的。代码如下：    
```java
channel.addReturnListener((replyCode, replyText, exchange, routingKey, properties, body) -> {
            String message = new String(body);
            System.out.println("message can't routing correct queue, so return : " + message);
});

channel.basicPublish("testExchange", "noSuchRoutingKey", true, MessageProperties.PERSISTENT_TEXT_PLAIN, "this body should return".getBytes());
```    

　　上面代码中生产者没有成功地将消息路由到队列，此时RabbitMQ会通过Basic.Return返回消息，之后生产者客户客户端通过ReturnListener监听到了这个事件。    


### immediate    
　　当immediate参数设置为true时，如果交换器在将消息路由到队列时发现队列上并不存在任何消费者，那么这条消息将不会存入队列中。当与路由键匹配的所有队列都没有消费者时，该消息会通过Basic.Return返回至生产者。    
　　概括来说，mandatory参数告诉服务器至少将该消息路由到一个队列，否则将消息返回给生产者。immediate参数告诉服务器，如果该消息关联的队列上有生产者，则立即投递；如果所有匹配的队列上都没有消费者，则直接将消息返回给生产者，不用将消息存入队列而等待消费者了。    
　　*RabbitMQ 3.0版本开始去掉了对immediate参数的支持，对此RabbitMQ官方解释是：immediate参数会影响镜像队列的性能，增加代码复杂性，建议采用TTL和DLX的方法替代*。发送immediate参数为true的Basic.Publish客户端会报如下异常：    
```
[WARN] - [AN unexpected connection driver error occured (Exception message: Connection reset)] - [com.rabbitmq.client.impl.ForgivingExceptionHandler:120]
```    
　　RabbitMQ服务端日志会报如下异常（默认日志路径为：$RABBITMQ_HOME/var/log/rabbitmq/rabbit@$HOSTNAME.log）    
```
    =ERROR REPORT=== 25-May-2019::15:10:23 ===
    Error on AMQP connection<0.25391.2>...
```   

### Alternate-Exchange    
　　备份交换器（AE）。生产者在发送消息的时候如果不设置mandatory参数，那么消息在未被路由的情况下将会丢失；如果设置了mandatory参数，那么需要添加ReturnListener编程逻辑，生产者的代码变得更加复杂了。这时候，可以使用备份交换器，这样可以将未被路由到队列的消息存储在RabbitMQ中，等到有需要的时候再去处理这些消息。    
　　可以通过在声明交换器（调用channel.exchangeDeclare方法）的时候添加alternate-exchange参数来实现，也可以通过策略的方式实现。如果两者同时使用，前者的优先级更高，会覆盖掉策略的设置。    
　　使用参数[示例](./src/main/java/com/isaac/ch4/AlternateExchangeDemo.java)    
　　如果采用Policy的方式来设置备份交换器，可以参考如下：    
`rabbitmqctl set_policy AE "^targetExchange$" '{"alternate-exchange":"backUpExchange"}'`    
　　备份交换器和普通的交换器没有太大区别，为了避免消息在备份时还是丢失，建议设置备份交换器的类型为fanout。对于备份交换器，总结了以下几种特殊情况：    
* 如果设置的备份交换器不存在，客户端和RabbitMQ服务端都不会有异常，此时消息会丢失    
* 如果备份交换器没有绑定队列（或没有任何匹配的队列），客户端和RabbitMQ服务端都不会有异常，此时消息会丢失    
* 如果备份交换器和mandatory参数一起使用，mandatory参数无效    


## TTL    
　　TTL，Time to Live的简称，即过期时间。RabbitMQ可以对消息和队列设置TTL。    


### Message-TTL    
　　目前有两种方法可以设置消息的TTL。第一种方法是通过设置队列的属性设置，队列中的所有消息都有相同的过期时间。第二种方法是对消息本身进行单独设置，每条消息的TTL可以不同。如果两种方法一起使用，则消息的TTL以两者之间较小的数值为准。消息在队列中的生存时间一旦超过设置的TTL值时，就会变成”死信“（Dead Message），消费者将无法再收到该消息（不是绝对）。    

　　通过队列属性设置消息TTL的方法是在channel.queueDeclare方法中加入x-message-ttl参数实现的，这个参数的单位是毫秒。如下所示：    
```java
Map<String, Object> args = new HashMap<>();
args.put("x-message-ttl", 6000);
channel.queueDeclare(queue, durable, exclusive, autoDelete, args);
```    
　　同时也可以通过Policy的方式来设置TTL，如下：    
`rabbitmqctl set_policy TTL ".*" '{"message-ttl":6000}' --apply-to queues`    
　　还可以用HTTP API接口设置：    
```shell 
    $curl -i -u root:root -H "content-type:application/json" -X PUT -d '{"auto_delete":false,"durable":true,"arguments":{"x-message-ttl":6000}}'
    http://localhost:15672/api/queues/{vhost}/{queuename}
```    

　　如果不设置TTL，则表示此消息不会过期；如果将TTL设置为0，则表示除非此时可以直接将消息投递到消费者，否则该消息会被立即丢弃，这个特性可以部分代替RabbitMQ3.0之前的immediate参数，而immediate参数在投递失败Basic.Return给生产者的功能可以用死信队列来替代。    

　　而对于每条消息设置TTL，可以使用channel.basicPublish方法，在属性中加入expiration的属性参数：    
```java
AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties.Builder();
AMQP.BasicProperties properties = builder.deliveryMode(2).expiration("6000").build();
channel.basicPublish("testExchange","testRoutingKey", properties, "this is a ddl message!".getBytes());
```    

　　HTTP API接口设置：    
```shell
$ curl -i -u root:root -H "content-type:application/json" -X POST -d '{"properties":{"expiration":"60000"},"routing_key":"routingkey","payload":"my body","payload_encoding":"string"}'
http://localhost:15672/api/exchanges/{vhost}/{exchangename}/publish
```    

### Queue-TTL    
　　通过channel.queueDeclare方法中的**x-expires**参数可以控制队列被自动删除前处于未使用状态的时间。未使用的意思是队列上没有任何消费者，队列也没有被重新声明，并且在过期时间段内没有调用过Basic.Get命令。RabbitMQ重启时间会重新计算。     



## DLX    

