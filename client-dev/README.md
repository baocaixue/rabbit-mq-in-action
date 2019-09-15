# 客户端开发向导   
- 连接RabbitMQ ...................................................[1](#Connect-RabbitMQ)
- 使用交换器和队列 ...................................................[2](#Exchange-Queue)
    - exchangeDeclare方法详解 ...................................................[2.1](#exchangeDeclare)
    - queueDeclare方法详解 ...................................................[2.2](#queueDeclare)
    - queueBind方法详解 ...................................................[2.3](#queueBind)
    - exchangeBind方法详解 ...................................................[2.4](#exchangeBind)
    - 何时创建 ...................................................[2.5](#When-Create)
- 发送消息 ...................................................[3](#Send-Message)
- 消费消息 ...................................................[4](#Consume-Message)
    - 推模式 ...................................................[4.1](#Push-Message)
    - 拉模式 ...................................................[4.21](#Pull-Message)
- 消费端的确认与拒绝 ...................................................[5](#Message-Ack)
- 关闭连接 ...................................................[6](#Close)    

&nbsp;&nbsp;RabbitMQ Java客户端使用com.rabbitmq.client作为顶级包名，关键的类和接口有Channel、Connection、ConnectionFactory、Consumer等。**AMQP协议层面的操作通过Channel接口实现。Connection是用来开启Channel（信道）的，也可以注册事件处理器，也可以在应用结束时关闭连接**。与RabbitMQ相关的开发工作，基本上也是围绕Connection和Channel两个类展开的。

***

## Connect-RabbitMQ    
&nbsp;&nbsp;下面的代码用来在给定的参数（IP地址、端口号、用户名、密码等）下连接RabbitMQ：    
```java
ConnectionFactory factory = new ConnectionFactory();
factory.setUsername(USERNAME);
factory.setPassword(PASSWORD);
factory.setVirtualHost(virtualHost);
factory.setHost(IP_ADDRESS);
factory.setPort(PORT);
Connection conn = factory.newConnection();
```    
&nbsp;&nbsp;也可以选择使用URI的方式来实现，例如下面代码：    
```java
ConnectionFactory factory = new ConnectionFactory();
factory.setUri("amqp://userName:password@ipAddress:portNumber/virtualHost");
Connection conn = factory.newConnection();
//Connection接口被用来创建一个Channel:
Channel channel = conn.createChannel();
//在创建之后，Channel可以用来发送或接收消息
```    
&nbsp;&nbsp;**注意要点：**    
&nbsp;&nbsp;Connection可以用来创建多个Channel实例，但是Channel实例不能再线程间共享，应用程序应该为每一个线程开辟一个Channel。某些情况下Channel的操作可以并发运行，但是在其他情况下会导致在网络上出现错误的通信帧交错，同时也会影响发送方确认（publisher confirm）机制的运行，所以多线程间共享Channel实例是非线程安全的。    


## Exchange-Queue    
&nbsp;&nbsp;交换器和队列是AMQP中high-level层面的构建模块，应用程序需确保在使用它们的时候就已经存在了，在使用之前要先声明（declare）它们。    
&nbsp;&nbsp;下面代码演示了如何声明一个交换器和队列：    
```java
channel.exchangeDeclare(exchangeName, "direct", true);
String queueName = channel.queueDeclare().getQueue();
channel.queueBind(queueName, exchangeName, routingKey);
```    
&nbsp;&nbsp;上面创建了持久化的，非自动删除的，绑定类型为direct的交换器，同时也创建了一个非持久化的，排他的，自动删除的队列（此队列名称由RabbitMQ自动生成）。这里的交换器和队列都没有设置特殊的参数。    
&nbsp;&nbsp;上面代码也展示了如何将队列和交换器绑定起来。上面的队列具备如下特性：只对当前应用中同一个Connection层面可用，同一个Connection的不同Channel可共用，并且也会在应用连接断开时自动删除。    
&nbsp;&nbsp;如果要在应用中共享一个队列，可以做如下声明，如下所示：    
```java
channel.exchangeDeclare(exchangeName, "direct", true);
channel.queueDeclare(queueName, true, false, false, null);
channel.queueBind(queueName, exchangeName, routingKey);
```   
&nbsp;&nbsp;这里队列被声明为持久化的，非排他的，非自动删除的，而且也被分配另一个确定的已知的名称（由客户端分配而非RabbitMQ自动生成）。    
&nbsp;&nbsp;注意：Channel的API方法都是可以重载的，比如exchangeDeclare、queueDeclare。根据参数不同，可以有不同的重载形式，根据自身的需要进行调用。    
&nbsp;&nbsp;生产者和消费者都可以声明一个交换器或者队列。如果尝试声明一个已存在的交换器或队列，只要声明的参数完全匹配现存在的交换器或队列，RabbitMQ就可以什么都不做，并成功返回。如果声明的参数不匹配则会抛出异常。    


### exchangeDeclare    
&nbsp;&nbsp;exchangeDeclare有多个重载方法，这些重载方法都是由下面这个方法中缺省的某些参数构成的。    
```java
Exchange.DeclareOk exchangeDeclare(
        String exchange,
        String type,
        boolean durable,
        boolean autoDelete,
        boolean internal,
        Map<String, Object> arguments
) throws IOException;
```    
&nbsp;&nbsp;这个方法的返回值时Exchange.DeclareOk，用来标识成功声明了一个交换器。    
&nbsp;&nbsp;各个参数详细说明如下所述：    
* exchange: 交换器的名称  
* type: 交换器的类型，常见的如fanout、direct、topic    
* durable: 设置是否持久化。durable设置为true标识持久化，反之是非持久化。持久化可以将交换器存盘，在服务器重启的时候不会丢失相关信息    
* autoDelete: 设置是否自动删除。autoDelete设置为true则表示自动删除。自动删除的前提是至少有一个队列或交换器与这个交换器绑定，之后所有与这个交换器绑定的队列或者交换器都与此解绑。注意不能错误理解为：当与此交换器连接的客户端都断开时，RabbitMQ会自动删除本交换器    
* internal: 设置是否是内置的。如果设置为true，则表示是内置的交换器，客户端程序无法直接发送消息到这个交换器中，只能通过交换器路由到交换器这种方式    
* argument: 其他一些结构化参数，比如alternate-exchange    

&nbsp;&nbsp;exchangeDeclare的其他重载方法如下：    
（1） Exchange.DeclareOk exchangeDeclare(String exchange, String type) throws IOException;    
（2） Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable) throws IOException;    
（3） Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException;    

&nbsp;&nbsp;与此对应的，将第二个参数String type换成BuiltInExchangeType type对应的几个重载方法（不常用）：    
（1） Exchange.DeclareOk exchangeDeclare(String exchange, BuiltInExchangeType type) throws IOException;    
（2） Exchange.DeclareOk exchangeDeclare(String exchange, BuiltInExchangeType type, boolean durable) throws IOException;    
（3） Exchange.DeclareOk exchangeDeclare(String exchange, BuiltInExchangeType type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException;    
（4） Exchange.DeclareOk exchangeDeclare(String exchange, BuiltInExchangeType type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException;    

&nbsp;&nbsp;与exchangeDeclare师出同门的还有几个方法，如exchangeDeclareNoWait方法，具体定义如下（也有BuiltInExchangeType版的）：    
```java
void exchangeDeclareNoWait(
        String type,
        boolean durable,
        boolean autoDelete,
        boolean internal,
        Map<String,Object> arguments
) throws IOException;
```    
&nbsp;&nbsp;这个exchangeDeclareNoWait是指AMQP中的Exchange.Declare命令的参数，意思是不需要服务器返回，所以方法返回时void。但这个方法因为没有返回值确认交换器真的建立好，所以不建议使用。       

&nbsp;&nbsp;这里还有一个方法exchangeDeclarePassive，主要用来检测相应的交换器是否存在。如果存在则正常返回；如果不存在则抛出异常：404 channel exception，同时channel也会被关闭。    
`Exchange.DeclareOk exchangeDeclarePassive(String name) throws IOException;`    


&nbsp;&nbsp;交换器删除的相应方法如下：    
（1） `Exchange.DeleteOk exchangeDelete(String exchange) throws IOException;`    
（2） `void exchangeDeleteNoWait(String exchange, boolean ifUnused) throws IOException;`   
（3） `Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException;`    
&nbsp;&nbsp;ifUnused参数用来设置是否在交换器没有被使用的情况下删除。    


### queueDeclare    
&nbsp;&nbsp;queueDeclare重载的方法只有两个：   
（1） `Queue.DeclareOk queueDeclare() throws IOException;`    
（2） `Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException;`    
&nbsp;&nbsp;不带任何参数的queueDeclare方法默认创建一个由RabbitMQ命令的（类似这种zmq.gen-LhQz1gv3GhDOv8PIDabOXA名称，这种队列也称之为匿名队列）、排他的、自动删除的、非持久化的队列。    
&nbsp;&nbsp;方法参数详细说明如下所述：    
* queue: 队列的名称    
* durable: 设置是否持久化。为true则设置队列为持久化。持久化的队列会存盘，在服务器重启的时候可以保证不丢失相关信息    
* **exclusive**: 设置是否排他。为true则设置队列为排他的。如果一个队列被声明为排他队列，该队列进队首次声明它的连接可见，并在连接断开时自动删除。这里需要注意三点：排他队列基于连接（Connection）可见，同一个连接的不同信道（Channel）是可以同时访问同一个连接创建的排他队列；”首次“是指如果一个连接已经声明了一个排他队列，其他连接是不允许建立同名的排他队列的，这个与普通队列不同；即使该队列是持久化的，一旦连接关闭或者客户端退出，该排他队列都会被自动删除。这种队列适用于一个客户端同时发送和读取消息的应用场景    
* autoDelete: 设置是否自动删除。为true则设置队列为自动删除。自动删除的前提是：至少有一个消费者连接到这个队列，之后所有与这个队列连接的消费者都断开时，才会自动删除。    
* arguments: 设置队列的一些其他参数，如x-message-ttl、x-expires、x-max-length、 x-max-length-bytes、 x-dead-letter-exchange、 x-dead-letter-routing-key、 x-max-priority等。    

&nbsp;&nbsp;**注意要点：**    
&nbsp;&nbsp;生产者和消费者都能使用queueDeclare来声明一个队列，但是如果消费者在同一个信道上订阅了另一个队列，就无法在声明队列了。必须先取消订阅，然后将信道置为”传输“模式，之后才能声明队列。    

&nbsp;&nbsp;对应于exchangeDeclareNoWait方法，这个也有一个queueDeclareNoWait方法：    
`void queueDeclareNoWait(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException;`    

&nbsp;&nbsp;同样也有一个queueDeclarePassive的方法：    
`Queue.DeclareOk queueDeclarePassive(String queue) throws IOException;`    

&nbsp;&nbsp;队列也有相应的删除方法：    
（1） `Queue.DeleteOk queueDelete(String queue) throws IOException;`        
（2） `Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) throws IOException;`  
（3） `void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) throws IOException;`    

&nbsp;&nbsp;清除队列内容方法：    
`Queue.PurgeOk queuePurge(String queue) throws IOException;`    

### queueBind    

   