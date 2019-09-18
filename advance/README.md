# RabbitMQ进阶    
- 消息何去何从...................................................[1](#Where-Is-Message)    
    - mandatory参数...................................................[1.1](#mandatory)    
    - immediate参数...................................................[1.2](#immediate)   
    - 备份交换器...................................................[1.3](#Alternate-Exchange)   
- 过期时间...................................................[2](#TTL)
    - 设置消息的TTL...................................................[2.1](#Message-TTL)   
    - 设置队列的TTL...................................................[2.2](#Queue-TTL)    
- 死信队列...................................................[3](#Dead-Queue)   
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
&nbsp;&nbsp;mandatory和immediate是channel.basicPublish方法中的两个参数，它们都有当消息传递过程中不可达目的地时将消息返回给生产者的功能。RabbitMQ提供的备份交换器可以将未能被交换器路由的消息（没有绑定队列或没有匹配的绑定）存储起来，而不用返回给客户端。    

### mandatory    
&nbsp;&nbsp;当mandatory参数设置为true时，交换器无法根据自身的类型和路由键找到一个符合条件的队列，那么RabbitMQ会调用Basic.Return命令将消息返回给生产者。当mandatory参数为false时，出现上述情况消息直接丢弃。    
&nbsp;&nbsp;生产者获取上述未被正确路由到合适队列的消息，是通过调用channel.addReturnListener来添加ReturnListener监听器实现的。代码如下：    
```java
channel.addReturnListener((replyCode, replyText, exchange, routingKey, properties, body) -> {
            String message = new String(body);
            System.out.println("message can't routing correct queue, so return : " + message);
});

channel.basicPublish("testExchange", "noSuchRoutingKey", true, MessageProperties.PERSISTENT_TEXT_PLAIN, "this body should return".getBytes());
```    

&nbsp;&nbsp;上面代码中生产者没有成功地将消息路由到队列，此时RabbitMQ会通过Basic.Return返回消息，之后生产者客户客户端通过ReturnListener监听到了这个事件。    


### immediate    
&nbsp;&nbsp;当immediate参数设置为true时，如果交换器在将消息路由到队列时发现队列上并不存在任何消费者，那么这条消息将不会存入队列中。当与路由键匹配的所有队列都没有消费者时，该消息会通过Basic.Return返回至生产者。    
&nbsp;&nbsp;概括来说，mandatory参数告诉服务器至少将该消息路由到一个队列，否则将消息返回给生产者。immediate参数告诉服务器，如果该消息关联的队列上有生产者，则立即投递；如果所有匹配的队列上都没有消费者，则直接将消息返回给生产者，不用将消息存入队列而等待消费者了。    
&nbsp;&nbsp;*RabbitMQ 3.0版本开始去掉了对immediate参数的支持，对此RabbitMQ官方解释是：immediate参数会影响镜像队列的性能，增加代码复杂性，建议采用TTL和DLX的方法替代*。发送immediate参数为true的Basic.Publish客户端会报如下异常：    
```
[WARN] - [AN unexpected connection driver error occured (Exception message: Connection reset)] - [com.rabbitmq.client.impl.ForgivingExceptionHandler:120]
```    
&nbsp;&nbsp;RabbitMQ服务端日志会报如下异常（默认日志路径为：$RABBITMQ_HOME/var/log/rabbitmq/rabbit@$HOSTNAME.log）    
```
    =ERROR REPORT=== 25-May-2019::15:10:23 ===
    Error on AMQP connection<0.25391.2>...
```   

### Alternate-Exchange    
&nbsp;&nbsp;备份交换器（AE）。生产者在发送消息的时候如果不设置mandatory参数，那么消息在未被路由的情况下将会丢失；如果设置了mandatory参数，那么需要添加ReturnListener编程逻辑，生产者的代码变得更加复杂了。这时候，可以使用备份交换器，这样可以将未被路由到队列的消息存储在RabbitMQ中，等到有需要的时候再去处理这些消息。    
&nbsp;&nbsp;可以通过在声明交换器（调用channel.exchangeDeclare方法）的时候添加alternate-exchange参数来实现，也可以通过策略的方式实现。如果两者同时使用，前者的优先级更高，会覆盖掉策略的设置。    
&nbsp;&nbsp;使用参数[示例](./src/main/java/com/isaac/ch4/AlternateExchangeDemo.java)    
&nbsp;&nbsp;如果采用Policy的方式来设置备份交换器，可以参考如下：    
`rabbitmqctl set_policy AE "^targetExchange$" '{"alternate-exchange":"backUpExchange"}'`    
&nbsp;&nbsp;备份交换器和普通的交换器没有太大区别，为了避免消息在备份时还是丢失，建议设置备份交换器的类型为fanout。对于备份交换器，总结了以下几种特殊情况：    
* 如果设置的备份交换器不存在，客户端和RabbitMQ服务端都不会有异常，此时消息会丢失    
* 如果备份交换器没有绑定队列（或没有任何匹配的队列），客户端和RabbitMQ服务端都不会有异常，此时消息会丢失    
* 如果备份交换器和mandatory参数一起使用，mandatory参数无效    


