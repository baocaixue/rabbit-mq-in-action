# RabbitMQ入门    
- 相关概念介绍 ...................................................[1](#Concept)    
    - 生产者和消费者 ...................................................[1.1](#Producers-And-Consumers)   
    - 队列  ...................................................[1.2](#Queue)  
    - 交换器、路由键、绑定  ...................................................[1.3](#Exchange-RoutingKey-Binding)  
    - 交换器类型  ...................................................[1.4](#Exchange-Type)  
    - RabbitMQ运转流程  ...................................................[1.5](#Flow)  
- AMQP协议介绍  ...................................................[2](#AMQP)    
    - AMQP生产者流转过程  ...................................................[2.1](#AMQP-Producers)  
    - AMQP消费者流转过程  ...................................................[2.2](#AMQP-Consumers)  
    - AMQP命令概览  ...................................................[2.3](#AMQP-Command)    
    
    
    
## Concept  
　　RabbitMQ整体上是一个生产者与消费者模型，主要负责接收、存储和转发消息。可以将消息传递的过程理解为：当你把一个包裹送到邮局，邮局会暂存并最终将邮件通过邮递员送到收件人手上，RabbitMQ就好比由邮局、邮箱和邮递员组成的一个系统。从计算机术语层面来说，RabbitMQ模型更像是一种交换机模型。  

### Producers-And-Consumers  
　　Producer：生产者，投递消息的一方。  
　　生产者创建消息，然后发布到RabbitMQ中。消息一般包含2个部分：消息体和标签（Label）。消息体也可以称之为payload，在实际应用中，消息体一般是一个带有业务逻辑结构的数据，如一个JSON字符串。当然可以进一步对这个消息体进行序列化操作。消息的标签用来描述这条消息，比如一个交换器的名称和一个路由键。生产者把消息交由RabbitMQ，RabbitMQ之后会根据标签把消息发送给感兴趣的消费者（Consumer）。  
　　Consumer：消费者，接收消息的一方。  
　　消费者连接到RabbitMQ服务器，并订阅到队列上。当消费者消费一条消息时，只是消费消息的消息体（payload）。在消息路由的过程中，消息的标签会丢弃，存入到队列中的消息只有消息体，消费者也只会消费消息体，也就不知道消息的生产者是谁。  
　　Broker：消息中间件的服务节点。  
　　对RabbitMQ来说，一个RabbitMQ Broker可以简单看作一个RabbitMQ的服务节点，或者RabbitMQ的服务实例。大多数情况下也可以将一个RabbitMQ Broker看作一台RabbitMQ服务器。  
　　对于生产者将消息存入RabbitMQ Broker，以及消费者从Broker消费数据的流程一般是：  
　　首先生产者将业务方数据进行可能的包装，之后封装成消息，发送（AMQP协议里这个动作对应的命令是Basic.Publish）到Broker中。消费者订阅并接收消息（AMQP协议中这个动作对应的命令为Basic.Consume或Basic.Get），经过可能的解包处理得到原始的数据，之后再进行业务处理逻辑。这个业务处理逻辑并不一定需要和接收消息的逻辑使用同一个线程。消费者进程可以使用一个进程取接收消息，存入到内存中，比如使用Java中的BlockingQueue。业务处理逻辑使用另一个线程从内存中读取数据，这样将应用进一步解耦，提高整个应用的处理效率。  


### Queue  
　　Queue：队列，是RabbitMQ的内部对象，用于存储消息。  
　　RabbitMQ中的消息都只能存储在队列中，这一点与Kafka这种消息中间件相反。Kafka将消息存储在topic（主题）这个逻辑层面，而相对应的队列逻辑只是topic实际存储文件中的位移标识。RabbitMQ的生产者产生消息并最终投递到队列中，消费者可以从队列中获取消息并消费。  
　　多个消费者可以订阅同一个队列，这是队列的消息会被平均分摊（Round-Robin，即轮询）给多个消费者进行处理，而不是每个消费者都接收到所有消息并处理。  
　　RabbitMQ不支持队列层面的广播消费，如果需要广播消费，需要在其上进行二次开发，处理逻辑会变得复杂，不建议。  

### Exchange-RoutingKey-Binding  
　　Exchange：交换器。生产者将消息发送到Exchange（交换器，通常也可以用大写的“X”来表示），由交换器将消息路由到一个或多个队列中。如果路由找不到，或许返回给生产者，或许直接丢弃。这里可以将RabbitMQ中的交换器看作一个简单的实体。  
　　RabbitMQ中的交换器由四种类型，不同的类型有着不同的路由策略。  
　　RoutingKey：路由键。生产者将消息发送给交换器的时候，一般会指定一个RoutingKey，用来指定这个消息的路由规则，而这个RoutingKey需要与交换器类型和绑定键（BindingKey）联合使用才能生效。  
　　在交换器类型和绑定键固定的情况下，生产者可以在发送消息给交换器时，通过指定RoutingKey来决定消息流向哪里。  
　　Binding：绑定。RabbitMQ中通过绑定将交换器和队列关联起来，在绑定的时候一般会指定一个绑定键（BindingKey），这样RabbitMQ就知道如何正确地将消息路由到队列了。  
　　生产者将消息发送给交换器时，需要一个RoutingKey，当BindingKey和RoutingKey相匹配时，消息会被路由到对应的队列中。在绑定多个队列到同一个交换器的时候，这些绑定允许使用相同的BindingKey。BindingKey并不是在所有情况下都生效，它依赖于交换器类型，比如fanout类型的交换器就会五十BindingKey，而是将消息路由到所有绑定到该交换器的队列中。  
　　direct类型的 交换器RoutingKey和BindingKey完全匹配才能使用给，而topic交换器类型下，RoutingKey和BindingKey之间需要做模糊匹配，两者并不是相同的。  
　　BindingKey其实也属于路由键中的一种，官方解释为：`the routing key to use for the binding`。大多时候，都把BindingKey和RoutingKey看作RoutingKey，为了避免混淆，可以这么理解：  
* 在使用绑定的时候，其中需要的路由键是BindingKey。涉及的客户端方法如：channel.exchangeBind、channel.queueBind，对应的AMQP命令为Exchange.Bind、Queue.Bind。  
* 在发送消息的时候，其中需要的路由键是RoutingKey。涉及的客户端方法如：channel.basicPublish，对应的AMQP命令为Basic.Publish。   


### Exchange-Type   
　　RabbitMQ常用的交换器类型有 fanout、 direct、 topic、 headers这四种。AMQP协议里还提到另外两种类型：System和自定义，这里不予描述。    
　　**fanout**    
　　它会把所有发送到该交换器的消息路由到所有与该交换器绑定的队列中。   
　　**direct**    
　　direct类型的交换器会把消息路由到那些BindingKey和RoutingKey完全匹配的队列中。     
　　**topic**   
　　topic类型的交换器，在匹配规则上做了扩展，它与direct类型的交换器类似，也是消息路由到BindingKey和RoutingKey相匹配的队列中，但匹配规则有些不同，它约定：  
　　 * RoutingKey为一个点号“.”分隔的字符串，如“com.rabbitmq.client”、“java.util.concurrent”、“com.hidden.client”；  
　　 * BindingKey和RoutingKey一样也是点号分隔的字符串；  
　　 * BindingKey中可以存在两种特殊的字符串“\*”和“#”，用于做模糊匹配，其中“\*”用于匹配一个单词，“#”用于匹配多规则单词（可以是零个）。    
　　**headers**   
　　headers类型的交换器不依赖于路由键的匹配规则来路由消息，而是根据发送的消息内容中headers属性进行匹配。在绑定队列和交换器时制定一组键值对，当发送消息到交换器时，RabbitMQ会获取到该消息的headers（也是一个键值对形式），对比其中的键值对是否完全匹配队列和交换器绑定时指定的键值对，如果完全匹配则消息会路由到该队列，否则不会路由到该队列。headers类型的交换器性能会很差，而且也不实用，基本上不会看到它的存在。     


### Flow    
　　回顾整个消息队列的使用过程。在最初状态下，生产者发送消息的时候：  
（1） 生产者连接到RabbitMQ Broker，建立一个连接（Connection），开启一个信道（Channel）；    
（2） 生产者声明一个交换器，并设置相关属性，如交换器类型、是否持久化等；  
（3） 生产者声明一个队列并设置相关属性，如是否排他、是否持久化、是否自动删除等；    
（4） 生产者通过路由键将交换器和队列绑定起来；     
（5） 生产者发送消息到RabbitMQ Broker，其中包含路由键、交换器等信息；    
（6） 相应的交换器根据接收到的路由键查找相匹配的队列；    
（7） 如果找到，则将从生产者发送过来的消息存入相应的队列中；    
（8） 如果没有找到，则根据生产者配置的属性选择丢弃还是回退给生产者；    
（9） 关闭资源（信道、连接）。     
　　消费者接收消息的过程：    
（1） 消费者连接到RabbitMQ Broker，建立一个连接（Connection），开启一个信道（Channel）；    
（2） 消费者向RabbitMQ Broker请求消费相应队列中的消息，可能会设置相应的回调函数，以及做一些准备工作；    
（3） 等待RabbitMQ Broker回应并投递相应队列中的消息，消费者接收消息；    
（4） 消费者确认（ack）接收到的消息；   
（5） RabbitMQ从队列中删除相应的已经被确认的消息；  
（6） 关闭资源（信道、连接）。    

　　这里引入了两个概念：**Connection** 和 **Channel** 。无论是生产者还是消费者，都需要和RabbitMQ Broker建立连接，这个连接就是一条TCP连接，也就是Connection。一旦TCP连接建立起来，客户端紧接着可以创建AMQP信道（Channel），每个信道都会被指派一个唯一的ID。信道是建立在Connection之上的虚拟连接，RabbitMQ处理每条AMQP指令都是通过信道完成的。     
　　我们完全可以直接使用Connection就能完成信道的工作，为什么还要引入信道呢？试想这样一个场景，一个应用程序中有很多个线程要从RabbitMQ中消费消息，或者生产消息，那么必然需要建立很多个Connection，也就是多个TCP连接。然而对操作系统而言，建立和销毁TCP连接是非常昂贵的开销，如果遇到使用高峰，性能瓶颈也会随之显现。RabbitMQ采用类似NIO的做法，选择TCP连接复用，不仅可以减少性能开销，同时也便于管理。      
　　每个线程把持一个信道，所以信道复用了Connection的TCP连接。同时RabbitMQ可以确保每个线程的私密性，就像拥有独立的连接一样。当每个信道的流量不是很大时，复用单一的Connection可以在产生性能瓶颈的情况下有效地节省TCP连接资源。但，当信道本身的流量很大时，这时复用一个Connection就会产生性能瓶颈，进而使整体的流量被限制了。此时就需要开辟多个Connection，将这些信道均摊到这些Connection。   
　　信道在AMQP中是一个很重要的概念，大多数操作都是在信道这个层面展开的。RabbitMQ相关的API和AMQP紧密相连，`channel.basicPublish`对应AMQP的`Basic.Publish`命令。     


## AMQP    
　　RabbitMQ是AMQP协议的Erlang的实现（RabbitMQ还支持STMP、MQTT等协议）。AMQP的模型架构和RabbitMQ的模型架构是一样的，生产者将消息发送给交换器，交换器和队列绑定。当生产者发送消息时所携带的RoutingKey与绑定时的BindingKey相匹配时，消息即被存入相应的队列中。消费者可以订阅相应的队列来获取消息。     
　　RabbitMQ中的交换器、交换器类型、队列、绑定、路由键等都是遵循AMQP协议中的相应概念。      
　　AMQP协议本身包括三层：    
* Module Layer：位于协议最高层，主要定义了一些供客户端调用的命令，客户端可以利用这些命令实现自己的业务逻辑。如，客户端可以使用Queue.Declare命令声明一个队列或者使用Basic.Consume订阅消费一个队列中的消息。    
* Session Layer：位于中间层，主要负责将客户端的命令发送给服务器，再将服务端的应答返回给客户端，主要为客户端与服务器之间的通信提供可靠性同步机制和错误处理。    
* Transport Layer：位于最底层，主要传输二进制数据流，提供帧的处理、信道的复用、错误的检测和数据的表示等。      
　　AMQP说到底还是一个通信协议，通信协议都会涉及报文交互，从low-level层面举例来说，AMQP本身是应用层的协议，其填充于TCP协议层的数据部分。而从high-level层面来说，AMQP是通过协议命令进行交互的。AMQP协议可以看作一系列结构化命令的集合，这里的命令代表一种操作，类似于HTTP中的方法（GET、POST、PUT、DELETE等）。       


### AMQP-Producers    
　　如下生产者代码片段：      
```java
Connection connection = factory.newConnection();
Channel channel = connection.createChannel();
String message = "This is test message!";
channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getByte());
channel.close();
connection.close();
```    
　　当客户端与Broker建立连接的时候，会调用`factory.newConnection`方法，这个方法会进一步封装成Protocol Header 0-9-1 的报文头 发送给Broker，以此通知Broker本次交互采用的协议是AMQP 0-9-1协议，紧接着Broker返回`Connection.Start`来建立连接，在连接的过程中涉及Connection.Start/.Start-OK、Connection.Tune/.Tune-OK、Connection.Open/.Open-OK这6个命令的交互。    
　　当客户端调用`connection.createChannel`方法准备开启信道的时候，其包装Channel.Open命令发送给Broker，等待Channel.Open-OK命令。    
　　当客户端发送消息的时候，需要调用`channel.basicPublish`方法，对应的AMQP命令为Basic.Publish，注意这个命令和前面涉及的命令略有不同，这个命令还包含了Content Header和Content Body。Content Header里面包含的是消息体的属性，如，投递模式、优先级等，而Content Body里面是消息本身。    
　　当客户端发送完消息需要关闭资源时，涉及Channel.Close/.Close-Ok与Connection.Close/.Close-Ok的命令交互。    


### AMQP-Consumers    
　　如下消费者代码片段：    
```java
Connection connection = factory.newConnection();
Channel channel = connection.createChannel();
Consumer consumer = new DefaultConsumer(channel){}//...
channel.basicQos(64);
channel.basicConsume(QUEUE_NAME, consumer);
TimeUnit.SECONDS.sleep(5);
channel.close();
connection.close();
```    
　　消费者客户端同样需要与Broker建立连接，与生产者客户端一样，协议交互同样涉及Connection.Start/.Start-Ok、Connection.Tune/.Tune-Ok和Connection.Open/.Open-Ok等。    
　　紧接着也少不了在Connection上建立Channel，和生产者客户端一样，协议涉及Channel.Open/.Open-Ok。    
　　如果在消费之前调用了channel.basicQos(int prefetchCount)的方法来设置消费者客户端最大能“保持”的未确认的消息个数（即预取个数），那么协议会涉及Basic.Qos/.Qos-Ok这两个AMQP命令。    
　　在真正消费之前，消费者客户端需要向Broker发送Basic.Consumer命令（即调用channel.basicConsumer方法）将Channel置为接收模式，之后Broker回执Basic.Consume-Ok以告诉消费者客户端准备好消费消息。紧接着Broker向消费者客户端推送（Push）消息，即Basic.Deliver命令，这个命令和Basic.Publish一样会携带Content Header和Content Body。    
　　消费者接收到消息并正确消费之后，向Broker发送确认 ，即Basic.Ack命令。    
　　在消费者停止消费的时候，主动关闭连接，这点和生产者一样，涉及Channel.Close/.Close-Ok和Connection.Close/.Close-Ok。    

## AMQP-Command    
　　AMQP 0-9-1协议中的命令远远不止前面所提的，下面给出AMQP命令列表。     
   
| 名称 | 是否包含内容体 | 对应客户端中的方法 | 简要描述 |     
|---|---|---|---    
| Connection.Start | 否 | factory.newConnection | 建立连接相关    
| Connection.Start-Ok | 否 | 同上 | 同上    
| Connection.Tune | 否 | 同上 | 同上    
| Connection.Tune-Ok | 否 | 同上 | 同上    
| Connection.Open | 否 | 同上 | 同上    
| Connection.Open-Ok | 否 | 同上 | 同上    
| Connection.Close | 否 | connection.close | 关闭连接    
| Connection.Close-Ok | 否 | 同上 | 同上    
| Channel.Open | 否 | connection.openChannel | 开启信道    
| Channel.Open-Ok | 否 | 同上 | 同上    
| Channel.Close | 否 | channel.close | 关闭信道    
| Channel.Close-Ok | 否 | 同上 | 同上    
| Exchange.Declare | 否 | channel.exchangeDeclare | 声明交换器    
| Exchange.Declare-Ok | 否 | 同上 | 同上    
| Exchange.Delete | 否 | channel.exchangeDelete | 删除交换器    
| Exchange.Delete-Ok | 否 | 同上 | 同上    
| Exchange.Bind | 否 | channel.exchangeBind | 交换器与交换器绑定    
| Exchange.Bind-Ok | 否 | 同上 | 同上    
| Exchange.Unbind | 否 | channel.exchangeUnbind | 交换器与交换器解绑    
| Exchange.Unbind-Ok | 否 | 同上 | 同上    
| Queue.Declare | 否 | channel.queueDeclare | 声明队列    
| Queue.Declare-Ok | 否 | 同上 | 同上    
| Queue.Bind | 否 | channel.queueBind | 队列与交换器绑定    
| Queue.Bind-Ok | 否 | 同上 | 同上    
| Queue.Purge | 否 | channel.queuePurge | 清除队列中的内容     
| Queue.Purge-Ok | 否 | 同上 | 同上    
| Queue.Delete | 否 | channel.queueDelete | 删除队列    
| Queue.Delete-Ok | 否 | 同上 | 同上     
| Queue.Unbind | 否 | channel.queueUnbind | 队列与交换器解绑     
| Queue.Unbind-Ok | 否 | 同上 | 同上       
| Basic.Qos | 否 | channel.basicQos | 设置未被确认消费的个数    
| Basic.Qos-Ok | 否 | 同上 | 同上    
| Basic.Consume | 否 | channel.basicConsume | 消费消息（推模式）    
| Basic.Consume-Ok | 否 | 同上 | 同上     
| Basic.Cancel | 否 | channel.basicCancel | 取消     
| Basic.Cancel-Ok | 否 | 同上 | 同上    
| Basic.Publish | 是 | channel.basicPublish | 发送消息  
| Basic.Return | 是 | 无 | 未能成功路由的消息返回    
| Basic.Deliver | 是 | 无 | Broker推送消息    
| Basic.Get | 否 | channel.basicGet | 消费消息（拉模式）   
| Basic.Get-OK | 是 | 同上 | 同上     
| Basic.Ack | 否 | channel.basicAck | 确认    
| Basic.Reject | 否 | channel.basicReject | 拒绝（单条拒绝）    
| Basic.Recover | 否 | channel.basicRecover | 请求Broker重新发送未被确认的消息    
| Basic.Recover-Ok | 否 | 同上 | 同上    
| Basic.Nack | 否 | channel.basicNack | 拒绝（可批量拒绝）    
| Tx.Select | 否 | channel.txSelect | 开启事务    
| Tx.Select-Ok | 否 | 同上 | 同上    
| Tx.Commit | 否 | channel.txCommit | 事务提交    
| Tx.Commit-Ok | 否 | 同上 | 同上     
| Tx.Rollback | 否 | channel.txRollback | 事务回滚     
| Tx.Rollback-Ok | 否 | 同上 | 同上      
| Confirm.Select | 否 | channel.confirmSelect | 开启发送端确认模式    
| Confirm.Select-Ok | 否 | 同上 | 同上    