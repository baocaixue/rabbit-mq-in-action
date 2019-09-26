# RabbitMQ进阶    
- 消息何去何从...................................................[1](#Where-Is-Message)    
    - mandatory参数...................................................[1.1](#mandatory)    
    - immediate参数...................................................[1.2](#immediate)   
    - 备份交换器...................................................[1.3](#Alternate-Exchange)   
- 过期时间...................................................[2](#TTL)
    - 设置消息的TTL...................................................[2.1](#Message-TTL)   
    - 设置队列的TTL...................................................[2.2](#Queue-TTL)    
- 死信交换器...................................................[3](#DLX)   
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
　　死信交换器（DLX），全称为Dead-Letter-Exchange，又称死信邮箱。当消息在一个队列中变成死信之后，它能被重新发送到另一个交换器中，这个交换器就是DLX，绑定DLX的队列就称之为死信对列。    
　　消息变成死信一般是由以下几种情况：    
* 消息被拒绝（Basic.Reject/Basic.Nack），并设置requeue参数为false 
* 消息过期
* 队列达到最大长度

　　DLX也是一个正常的交换器，和一般的交换器没有区别，它能在任何队列上被指定，实际上就是设置某个队列的属性。当这个队列中存在死信时，RabbitMQ就会自动地将这个消息重新发布到DLX上去，进而被路由到另一个队列，即死信对列。可以监听这个队列中的消息以进行相应的处理，这个特性与消息的TTL设置为0配合使用可以弥补immediate参数的功能。    
　　通过在channel.queueDeclare方法中设置**x-dead-letter-exchange**参数来为这个队列添加DLX（[示例](./src/main/java/com/isaac/ch4/DLXDemo.java)）：    
```java
channel.exchangeDeclare("dlxExchange", "direct");
Map<String, Object> args = new HashMap<>();
args.put("x-dead-letter-exchange", "dlxExchange");
chanel.queueDeclare("myQueu", false, fasle, false, args);
```    
　　也可以通过Policy的方式：    
`rabbitmqctl set_policy DLX ".*" '{"dead-letter-exchange":"dlxExchange"}' --apply-to queues`     

　　对于RabbitMQ来说，DLX是个非常有用的特性。它可以处理异常情况下，消息不能够被消费者正确消费（消费者调用了Basic.Nack或Basic.Reject）而被置入死信队列中的情况，后续分析程序可以通过消费这个死信队列中的内容来分析当时所遇到的异常情况，进而可以改善和优化系统。DLX配合TTL还可以实现延迟队列的功能。    


## Delay-Queue    
　　延迟队列存储的对象是对应的延迟消息，所谓的“延迟消息”是指当消息被发送以后，并不想让消费者立刻拿到消息，而是等待特定的时间后，消费者才能拿到这个消息进行消费。    
　　在AMQP协议中，或者RabbitMQ本身没有直接支持延迟队列的功能，但是可以通过前面所介绍的DLX和TTL模拟出延迟队列的功能。[示例](./src/main/java/com/isaac/ch4/DLXDemo.java)    
　　
## Priority-Queue    
　　优先级队列，具有高优先级的消息具备优先被消费的特权。    
　　可以设置队列的**x-max-priority**参数来配置一个队列的最大优先级：    
```java
Map<String, Object> args = new HashMap<>();
args.put("x-max-priority", 10);
channel.queueDeclare("queue", true, false, false, args);
```    
　　发送消息时设置当前消息优先级：    
```java
AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties.Builder();
builder.priority(5);
AMQP.BasicProperties properties = builder.build();
channel.basicPublish("exchange", "routingKey", properties, "test".getBytes());
```    
　　如果在消费者的消费速度大于生产者的速度且Broker中没有消息堆积的情况下，对发送的消息设置优先级就没有多大意义了。    


## RPC    
　　RPC，是Remote Procedure Call的简称，即远程调用。它是一种通过网络从远程计算机上请求服务，而不需要了解底层网络的技术。RPC的主要功能是让构建分布式计算更容易，在提供强大的远程调用能力时不损失本地调用的语义简介性。    
　　RPC的协议有很多，比如最早的CORBA、Java RMI、WebService的RPC风格、Hessian、Thrift甚至还有Restful API。
　　一般在RabbitMQ中进行RPC是很简单的。客户端发送请求消息，服务端回复响应的消息，为了接受响应的消息，需要在请求消息中发送一个回调队列：    
```java
String callbackQueueName = channel.queueDeclare().getQueue();
BasicProperties props = new BasicProperties.Builder().replyTo(callbackQueueName).build();
channel.basicPublish("", "rpc_queue", props, message.getBytes());
//then code to read a response message from the callback_queue
```    
　`The default exchange is implicitly bound to every queue, with a routing key equal to the queue name. It is not possible to explicitly bind to, or unbind from the default exchange. It also cannot be deleted.`       

  RabbitMQ默认交换器，绑定了所有队列，routingKey是队列名     
  RPC演示代码[参见](./src/main/java/com/isaac/ch4/rpc)    


## Persistence    
　　持久化可以提高RabbitMQ的可靠性，以防在异常情况下（重启、关闭、宕机等）的数据丢失。RabbitMQ的持久化分为三个部分：交换器的持久化、队列的持久化和消息的持久化。    
　　交换器的持久化是通过声明交换器时将durable参数置为true实现的。如果交换器不设置持久化，那么在RabbitMQ服务重启之后，相关的交换器元数据会丢失，不过消息不会丢失，只是不能将消息发送到这个交换器中了。对一个长期使用的交换器来说，建议将其置为持久化的。    
　　队列的持久化是通过在声明队列时将durable参数置为true实现的。如果队列不设置持久化，那么在RabbitMQ服务重启之后，相关队列的元数据会丢失，此时数据也会丢失。    
　　队列的持久化能保证其本身的元数据不会因为异常而丢失，但是并不能保证内部所存储的消息不会丢失。要确保消息不会丢失，需要将其设置为持久化。通过将消息的投递模式（BasicProperties中deliveryMode属性）设置为2即可实现消息的持久化。MessageProperties.PERSISTENT_TEXT_PLAIN史记上封装了这个属性:     
```java
public static final BasicProperties PERSISTENT_TEXT_PLAIN = 
    new BasicProperties("text/plain",
                       null,
                       null,
                       2,//deliveryMode
                       0, null, null, null,
                       null, null, null, null,
                       null, null);
```    

　　**注意要点：** 将消息设置为持久化，会影响RabbitMQ的性能。在选择是否将消息设置为持久化时，需要在可靠性和吞吐量之间做一个权衡。    

## Confirm    
　　在使用RabbitMQ的时候，可以通过消息持久化操作来解决因为服务器的异常崩溃而导致的消息丢失，除此之外，还会遇到一个问题，当消息的生产者将消息发送出去之后，消息到底有没有正确的到达服务器？如果不进行特殊的配置，默认情况下发送消息的操作是不会返回任何信息给生产者的。RabbitMQ针对这个问题提供了两种解决方式：    
* 通过🦐🦐事务机制实现
* 通过发送方确认（publish confirm）机制实现    


### Transaction    
　　RabbitMQ客户端中与事务机制相关的方法有三个：**channel.txSelect**，**channel.txCommit**和**channel.txRollback**。    
　　事务机制很费RabbitMQ性能。（PS：这里我完全不知道，为什么生产者还有所谓的事务回滚？？？）    

### Publisher-Confirm    
　　这里引入一种轻量级的方式——发送方确认（publisher confirm）机制。    
　　生产者将信道摄制成confirm（确认）模式，一旦信道进入confirm模式，所有在该信道上发布的消息都会被指派一个唯一的ID（从1开始），一旦消息被投递到所有匹配的队列之后，RabbitMQ就会发送一个确认（Basic.Ack）给生产者（包含消息的唯一ID），这就使得生产者知晓消息已经正确的到达目的地了。如果消息和队列是持久化的，那么确认消息会在消息写入磁盘之后发出。RabbitMQ回传给生产者的确认消息中的deliveryTag包含了确认消息的序号。    
　　更多内容，[参见](./src/main/java/com/isaac/ch4/PublisherConfirmDemo.java)    

## Consumer    
　　之前介绍的消费者客户端可以通过推模式或者拉模式的方式来获取并消费消息，当消费者处理完业务逻辑之后可以手动确认消息已被接受，这样RabbitMQ才能把当前消息从队列中标记为清除。如果消费者无法处理当前消息，可以通过**channel.basicNack**或**channel.basicReject**来拒绝掉。    
　　这里对RabbitMQ消费端来说，还有几点需要注意的：    
* 消息分发
* 消息顺序性
* 弃用QueueingConsumer    

### Message-Distribution    
　　当RabbitMQ队列拥有多个消费者时，队列收到的消息将以轮询（round-robin）的分发方式发送给消费者。每条消息只会发送给订阅列表里的一个消费者。这种方式非常适合扩展，而且它是专为并发程序设计的。如果现在负载加重，那么只需要创建更多的消费者来消费处理消息即可。    
　　很多时候轮询的分发机制也不是那么优雅。默认情况下，如果有n个消费者，那么RabbitMQ会将第m条消息分发给第m%n个消费者，RabbitMQ不管消费者是否消费并已经确认了消息。试想一下，如果某些消费者任务繁重，来不及消费那么多消息，而某些其他消费者由于某些原因很快处理完了消息，进而空闲，这样就会造成整体应用吞吐量的下降。    
　　那么如何处理这种情况呢？这里就要用到channel.basicQos(int prefetchCount)这个方法，**该方法允许限制信道上的消费者所能保持的最大为确认消息的数量**。    
　　**注意要点：**    
　　Basic.Qos的使用对于拉模式的消费方式无效。    
　　channel.basicQos有三种类型的重载方法：     
　　（1）`void basicQos(int prefetchCount) throws IOException;`    
　　（2）`void basicQos(int prefetchCount, boolean global) throws IOException;`    
　　（3）`void basicQos(int prefetchSize, int prefetchCount, boolean global) throws IOException;`    

　　prefetchCount为0表示没有上限。prefetchSize代表消费者所能接受未确认消息的总体大小，为0表示没有上限，单位时B。    
　　对于一个信道来说，它可以同时消费多个队列，当设置了prefetchCount大于0时，这个信道需要和各个队列协调以确保发送的消息都没有超过所限定的prefetchCount大小的值，这样会使RabbitMQ的性能降低，尤其是这些队列分散在集群中的多个Broker节点中。RabbitMQ为了提升相关性能，在AMQP 0-9-1协议之上重新定义了global这个参数，如下：    

global参数 | AMQP 0-9-1 | RabbitMQ    
--- | --- | ---     
false | 信道上所有的消费者都需要遵从prefetchCount的限定值 | 信道上新的消费者需要遵从prefetchCount的限定值    
true | 当前通信链路（Connection）上所有的消费者都需要遵从prefetchCount的限定值 | 信道上所有的消费者都需要遵从prefetchCount的限定值    

     
### Message-Order    
　　消息的顺序性是指消费者消费到的消息和发送者发布的消息顺序是一致的。    
　　需要注意，消息的顺序性不是绝对的（从生产者事务、延时队列、优先级队列等分析）。    

### Abandon-QueueingConsumer    
　　对于QueueingConsumer这个类，RabbitMQ客户端4.x版本开始已经弃用了，原因如下：    
* 内存溢出问题，QueueingConsumer内部使用LinkedBlockingQueue来缓存消息，会导致客户端内存溢出假死，于是恶心循环，队列消息不断堆积而得不到消化。（使用QueueingConsumer一定要用Basic.Qos控制！）
* 会拖累同一个Connection下的所有信道，使其性能降低
* 同步递归调用QueueingConsumer会产生死锁
* RabbitMQ的自动连接恢复机制（automatic connection recovery）不支持QueueingConsumer的这种形式
* QueueingConsumer不是事件驱动的    

　　为了避免不必要的麻烦，建议在消费消息的时候尽量使用继承DefaultConsumer的方式。    

## Message-Transmission-Guarantee    
　　消息中间件的传输保障层级：    
* At most once: 最多一次。消息可能会丢失，但不会重复传输
* At least once: 最少一次。消息不会丢失，但可能会重复传输
* Exactly once: 恰好一次。每条消息可定会被传输一次且仅传输一次（RabbitMQ本身不支持）    

　　最少一次保证需要考虑以下内容：    
* publisher confirm
* mandatory or alternate exchange
* durable
* autoAck=false