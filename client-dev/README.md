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
    - 拉模式 ...................................................[4.2](#Pull-Message)
- 消费端的确认与拒绝 ...................................................[5](#Message-Ack)
- 关闭连接 ...................................................[6](#Close)    

　　　　RabbitMQ Java客户端使用com.rabbitmq.client作为顶级包名，关键的类和接口有Channel、Connection、ConnectionFactory、Consumer等。**AMQP协议层面的操作通过Channel接口实现。Connection是用来开启Channel（信道）的，也可以注册事件处理器，也可以在应用结束时关闭连接**。与RabbitMQ相关的开发工作，基本上也是围绕Connection和Channel两个类展开的。

***

## Connect-RabbitMQ    
　　　　下面的代码用来在给定的参数（IP地址、端口号、用户名、密码等）下连接RabbitMQ：    
```java
ConnectionFactory factory = new ConnectionFactory();
factory.setUsername(USERNAME);
factory.setPassword(PASSWORD);
factory.setVirtualHost(virtualHost);
factory.setHost(IP_ADDRESS);
factory.setPort(PORT);
Connection conn = factory.newConnection();
```    
　　　　也可以选择使用URI的方式来实现，例如下面代码：    
```java
ConnectionFactory factory = new ConnectionFactory();
factory.setUri("amqp://userName:password@ipAddress:portNumber/virtualHost");
Connection conn = factory.newConnection();
//Connection接口被用来创建一个Channel:
Channel channel = conn.createChannel();
//在创建之后，Channel可以用来发送或接收消息
```    
　　**注意要点：**    
　　Connection可以用来创建多个Channel实例，但是Channel实例不能再线程间共享，应用程序应该为每一个线程开辟一个Channel。某些情况下Channel的操作可以并发运行，但是在其他情况下会导致在网络上出现错误的通信帧交错，同时也会影响发送方确认（publisher confirm）机制的运行，所以多线程间共享Channel实例是非线程安全的。    


## Exchange-Queue    
　　交换器和队列是AMQP中high-level层面的构建模块，应用程序需确保在使用它们的时候就已经存在了，在使用之前要先声明（declare）它们。    
　　下面代码演示了如何声明一个交换器和队列：    
```java
channel.exchangeDeclare(exchangeName, "direct", true);
String queueName = channel.queueDeclare().getQueue();
channel.queueBind(queueName, exchangeName, routingKey);
```    
　　上面创建了持久化的，非自动删除的，绑定类型为direct的交换器，同时也创建了一个非持久化的，排他的，自动删除的队列（此队列名称由RabbitMQ自动生成）。这里的交换器和队列都没有设置特殊的参数。    
　　上面代码也展示了如何将队列和交换器绑定起来。上面的队列具备如下特性：只对当前应用中同一个Connection层面可用，同一个Connection的不同Channel可共用，并且也会在应用连接断开时自动删除。    
　　如果要在应用中共享一个队列，可以做如下声明，如下所示：    
```java
channel.exchangeDeclare(exchangeName, "direct", true);
channel.queueDeclare(queueName, true, false, false, null);
channel.queueBind(queueName, exchangeName, routingKey);
```   
　　这里队列被声明为持久化的，非排他的，非自动删除的，而且也被分配另一个确定的已知的名称（由客户端分配而非RabbitMQ自动生成）。    
　　注意：Channel的API方法都是可以重载的，比如exchangeDeclare、queueDeclare。根据参数不同，可以有不同的重载形式，根据自身的需要进行调用。    
　　生产者和消费者都可以声明一个交换器或者队列。如果尝试声明一个已存在的交换器或队列，只要声明的参数完全匹配现存在的交换器或队列，RabbitMQ就可以什么都不做，并成功返回。如果声明的参数不匹配则会抛出异常。    


### exchangeDeclare    
　　exchangeDeclare有多个重载方法，这些重载方法都是由下面这个方法中缺省的某些参数构成的。    
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
　　这个方法的返回值时Exchange.DeclareOk，用来标识成功声明了一个交换器。    
　　各个参数详细说明如下所述：    
* exchange: 交换器的名称  
* type: 交换器的类型，常见的如fanout、direct、topic    
* durable: 设置是否持久化。durable设置为true标识持久化，反之是非持久化。持久化可以将交换器存盘，在服务器重启的时候不会丢失相关信息    
* autoDelete: 设置是否自动删除。autoDelete设置为true则表示自动删除。自动删除的前提是至少有一个队列或交换器与这个交换器绑定，之后所有与这个交换器绑定的队列或者交换器都与此解绑。注意不能错误理解为：当与此交换器连接的客户端都断开时，RabbitMQ会自动删除本交换器    
* internal: 设置是否是内置的。如果设置为true，则表示是内置的交换器，客户端程序无法直接发送消息到这个交换器中，只能通过交换器路由到交换器这种方式    
* argument: 其他一些结构化参数，比如alternate-exchange    

　　exchangeDeclare的其他重载方法如下：    
（1） Exchange.DeclareOk exchangeDeclare(String exchange, String type) throws IOException;    
（2） Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable) throws IOException;    
（3） Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException;    

　　与此对应的，将第二个参数String type换成BuiltInExchangeType type对应的几个重载方法（不常用）：    
（1） Exchange.DeclareOk exchangeDeclare(String exchange, BuiltInExchangeType type) throws IOException;    
（2） Exchange.DeclareOk exchangeDeclare(String exchange, BuiltInExchangeType type, boolean durable) throws IOException;    
（3） Exchange.DeclareOk exchangeDeclare(String exchange, BuiltInExchangeType type, boolean durable, boolean autoDelete, Map<String, Object> arguments) throws IOException;    
（4） Exchange.DeclareOk exchangeDeclare(String exchange, BuiltInExchangeType type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) throws IOException;    

　　与exchangeDeclare师出同门的还有几个方法，如exchangeDeclareNoWait方法，具体定义如下（也有BuiltInExchangeType版的）：    
```java
void exchangeDeclareNoWait(
        String type,
        boolean durable,
        boolean autoDelete,
        boolean internal,
        Map<String,Object> arguments
) throws IOException;
```    
　　这个exchangeDeclareNoWait是指AMQP中的Exchange.Declare命令的参数，意思是不需要服务器返回，所以方法返回时void。但这个方法因为没有返回值确认交换器真的建立好，所以不建议使用。       

　　这里还有一个方法exchangeDeclarePassive，主要用来检测相应的交换器是否存在。如果存在则正常返回；如果不存在则抛出异常：404 channel exception，同时channel也会被关闭。    
`Exchange.DeclareOk exchangeDeclarePassive(String name) throws IOException;`    


　　交换器删除的相应方法如下：    
（1） `Exchange.DeleteOk exchangeDelete(String exchange) throws IOException;`    
（2） `void exchangeDeleteNoWait(String exchange, boolean ifUnused) throws IOException;`   
（3） `Exchange.DeleteOk exchangeDelete(String exchange, boolean ifUnused) throws IOException;`    
　　ifUnused参数用来设置是否在交换器没有被使用的情况下删除。    


### queueDeclare    
　　queueDeclare重载的方法只有两个：   
（1） `Queue.DeclareOk queueDeclare() throws IOException;`    
（2） `Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException;`    
　　不带任何参数的queueDeclare方法默认创建一个由RabbitMQ命令的（类似这种zmq.gen-LhQz1gv3GhDOv8PIDabOXA名称，这种队列也称之为匿名队列）、排他的、自动删除的、非持久化的队列。    
　　方法参数详细说明如下所述：    
* queue: 队列的名称    
* durable: 设置是否持久化。为true则设置队列为持久化。持久化的队列会存盘，在服务器重启的时候可以保证不丢失相关信息    
* **exclusive**: 设置是否排他。为true则设置队列为排他的。如果一个队列被声明为排他队列，该队列进队首次声明它的连接可见，并在连接断开时自动删除。这里需要注意三点：排他队列基于连接（Connection）可见，同一个连接的不同信道（Channel）是可以同时访问同一个连接创建的排他队列；”首次“是指如果一个连接已经声明了一个排他队列，其他连接是不允许建立同名的排他队列的，这个与普通队列不同；即使该队列是持久化的，一旦连接关闭或者客户端退出，该排他队列都会被自动删除。这种队列适用于一个客户端同时发送和读取消息的应用场景    
* autoDelete: 设置是否自动删除。为true则设置队列为自动删除。自动删除的前提是：至少有一个消费者连接到这个队列，之后所有与这个队列连接的消费者都断开时，才会自动删除。    
* arguments: 设置队列的一些其他参数，如x-message-ttl、x-expires、x-max-length、 x-max-length-bytes、 x-dead-letter-exchange、 x-dead-letter-routing-key、 x-max-priority等。    

　　**注意要点：**    
　　生产者和消费者都能使用queueDeclare来声明一个队列，但是如果消费者在同一个信道上订阅了另一个队列，就无法在声明队列了。必须先取消订阅，然后将信道置为”传输“模式，之后才能声明队列。    

　　对应于exchangeDeclareNoWait方法，这个也有一个queueDeclareNoWait方法：    
`void queueDeclareNoWait(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) throws IOException;`    

　　同样也有一个queueDeclarePassive的方法：    
`Queue.DeclareOk queueDeclarePassive(String queue) throws IOException;`    

　　队列也有相应的删除方法：    
（1） `Queue.DeleteOk queueDelete(String queue) throws IOException;`        
（2） `Queue.DeleteOk queueDelete(String queue, boolean ifUnused, boolean ifEmpty) throws IOException;`  
（3） `void queueDeleteNoWait(String queue, boolean ifUnused, boolean ifEmpty) throws IOException;`    

　　清除队列内容方法：    
`Queue.PurgeOk queuePurge(String queue) throws IOException;`    

### queueBind    
　　将队列与交换器绑定的方法如下：    
（1） `Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException;`    
（2） `Queue.BindOk queueBind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException;`    
（3） `void queueBindNoWait(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException;`    
　　方法中涉及的参数详解：    
* queue: 队列名称    
* exchange: 交换器名称    
* routingKey: 用来绑定队列和交换器的路由键    
* arguments: 定义绑定的一些参数    
　　队列和交换器解绑：    
（1） `Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey) throws IOException;`    
（2） `Queue.UnbindOk queueUnbind(String queue, String exchange, String routingKey, Map<String, Object> arguments) throws IOException;`    

### exchangeBind    
　　不仅可以将交换器与队列绑定，也可以将交换器与交换器绑定，相应方法如下：    
（1） `Exchange.BindOk exchangeBind(String destination, String source, String routingKey) throws IOException;`     
（2） `Exchange.BindOk exchangeBind(String destination, String source, String routingkey, Map<String, Object> arguments) throws IOException;`    
（3） `void exchangeBindNoWait(String destination, String source, String routingKey, Map<String, Object> arguments) throws IOException;`    
　　绑定之后，**消息从source交换器转发到destination交换器**，某种程度上来说destination交换器可以看作一个队列。    


## When-Create    



## Send-Message    
　　如果要发送一个消息，可以使用Channel类的basicPublish方法，比如发送一条内容为”Hello World!“的消息，参考如下：    
```java
byte[] messageBodyBytes = "Hello World!".getBytes();
channel.basicPublish(exchangeName, routingKey, null, messageBodyBytes);
```    
　　为了更好控制发送，可以使用mandatory这个参数，或者发送一些特定的属性信息：    
`channel.basicPublish(exchangeName, routingKey, mandatory, MessageProperties.PERSISTENT_TEXT_PLAIN, messageBodyBytes);`    

　　上面这行代码发送了一条消息，这条消息的投递模式（delivery mode）设置为2，即消息会被持久化（存入磁盘）在服务器中。同时这条消息的优先级（priority）设置为0，content-type为”text/plain“。也可以自己设定消息的属性：    
```java
channel.basicPublish(
    exchangeName,
    routingKey,
    new AMQP.BasicProperties.Builder().contentType("text/plain").deliveryMode(2).priority(1).userId("hidden").build(),
    messageBodyButes
);
```    
　　也可以发送一条带有headers的消息：    
```java
Map<String, Object> headers = new HashMap<>();
headers.put("location", "here");
header.put("time", "today");
channel.basicPublish(
        exchangeName,
        routingKey,
        new AMQP.BasicProperties.Builder().headers(headers).build(),
        messageBodyBytes
);
```    
　　还可以发送一条带有过期时间（expiration）的消息：    
```java
channel.basicPublish(
        exchangeName,
        routingKey,
        new AMQP.BasicProperties.Builder().expiration("60000").build(),
        messageBodyBytes
);
```    
　　对于basicPublish而言，有几个重载的方法：    
（1） `void basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body) throws IOException;`    
（2） `void basicPublish(String exchange, String routingKey, boolean mandatory, BasicProperties props, byte[] body) throws IOException;`    
（3） `void basicPublish(String exchange, String routingKey, boolean mandatory, boolean immediate, BasicProperties props, byte[] body) throws IOException;`    
　　对应具体参数解释如下：    
* exchange: 交换器的名称，指明消息要发送到哪个交换器中。如果设置为空字符串，则消息会被发送到RabbitMQ默认的交换器中    
* routingKey: 路由键，交换器根据路由键将消息存储到相应的队列中    
* props: 消息的基本属性集，其包含14个属性成员，分别有contentType、contentEncoding、headers、deliveryMode、priority、correlationId、replyTo、expiration、messageId、timestamp、type、userId、appId、clusterId    
* body: 消息体（payload），真正需要发送的消息    
* mandatory和immediate的详细内容请参考[4.1](../advance/README.md)    

## Consume-Message    
　　RabbitMQ的消费模式分为两种：推（Push）模式和拉（Pull）模式。推模式采用Basic.Consume进行消费，而拉模式则是调用Basic.Get进行消费。    

### Push-Message    
　　在推模式中，可以通过持续订阅的方式来消费消息，使用到的相关类有`com.rabbitmq.client.Consumer`、`com.rabbitmq.client.DefaultConsumer`。    
　　接收消息一般通过实现Consumer接口或继承DefaultConsumer类来实现。当调用与Consumer相关的API方法时，不同的订阅采用不同的消费者标签（consumerTag）来区分彼此，在同一个Channel中的消费者也需要通过唯一的消费者标签以作区分，代码如下：    
```java
boolean autoAck = false;
channel.basicQos(64);
channel.basicConsume(queueName, autoAck, "myConsumerTag",
    new DefaultConsumer(channel){
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException{
            String routingKey = envelope.getRoutingKey();
            String contentType = properties.getContentType();
            long deliveryTag = envelope.getDeliveryTag();
            //process the message components here ...
            channel.basicAck(deliveryTag, false);
        }
    }
);
```   
　　注意，上面代码中显式地设置autoAck为false，然后在接收到消息之后进行显式ack操作（channel.basicAck），对于消费者来说做这个设置是非常必要的，可以防止消息不必要的丢失。    

　　Channel类中basicConsume方法有如下几种形式：    
（1） `String basicConsume(Stirng queue, Consumer callback) throws IOException;`    
（2） `String basicConsume(String queue, boolean autoAck, Consumer callback) throws IOException;`    
（3） `String basicConsume(String queue, boolean autoAck, Map<String, Object> arguments, Consumer callback) throws IOException;`    
（4） `String basicConsume(String queue, boolean autoAck, String consumerTag, Consumer callback) throws IOException;`    
（5） `String basicConsume(String queue, boolean autoAck, String consumerTag, boolean noLocal, boolean exclusive, Map<String, Object> arguments, Consumer callback) throws IOException;`    
　　对应的参数说明如下所述：    
* queue: 队列名称   
* autoAck: 设置是否自动确认，建议设成false，不自动确认
* consumerTag: 消费者标签，用来区分多个消费者
* noLocal: 设置为true则表示不能将同一个Connection中生产者发送的消息传递给这个Connection中的消费者
* exclusive: 设置是否排他
* arguments: 设置消费者其他参数
* callback: 设置消费者回调函数。用来处理RabbitMQ推送过来的消息，比如DefaultConsumer，使用时需要客户端重写其中的方法    

　　对于消费者客户端来说，重写handleDelivery方法是十分方便的。更复杂的消费者客户端会重写更多的方法，具体如下：    
`void handleConsumerOk(Stirng consumerTag);`    
`void handleCancelOk(String consumerTag);`    
`void handleCancel(String consumerTag) throws IOException;`    
`void handleShutdownSignal(String consumerTag, ShutdownSignalException sig);`    
`void handleRecoverOk(String consumerTag);`    

　　比如handleShutdownSignal方法，当Channel或者Connection关闭的时候会调用。再者，handleConsumeOk方法会在其他方法之前调用，返回消费者标签。    
　　重写handleCancelOk方法和handleCancel方法，这样消费端可以在显式地或者隐式地取消订阅的时候调用。也可以通过channel.basicCancel方法来显式地取消一个消费者的订阅：`channel.basicCancel(consumerTag)`    


### Pull-Message    
　　对于拉模式的消费方式。通过channel.basicGet方法可以单条地获取消息，其返回值是GetResponse。Channel类的basicGet方法没有重载方法，只有：    
`GetResponse basicGet(String queue, boolean autoAck) throws IOException;`    
　　注意要点:    
　　Basic.Consume将信道（Channel）置为投递模式，知道取消队列的订阅为止。在投递模式期间，RabbitMQ会不断地推送消息给消费者，当然推送消息到个数还是会受到Basic.Qos的限制。如果只想从队列中获取单条消息而不是持续订阅，建议还是使用Basic.Get进行消费。但不能将Basic.Get放在循环里代替Basic.Consume，这样会严重影响RabbitMQ性能。这种情况下要实现高吞吐量，还是要使用Basic.Consume方法。    


## Message-Ack    
　　为了保证消息从队列可靠地到达消费者，RabbitMQ提供了消息确认机制（message acknowledgement）。消费者在订阅队列时，可以指定autoAck参数，当autoAck等于false时，RabbitMQ会等待消费者显式地回复确认信号后才从内存（或磁盘）中移去消息（实际上是先打上删除标记，之后再删除）。当autoAck等于true时，RabbitMQ会自动把发送出去的消息置为确认，然后从内存（或磁盘）中删除，而不管消费者是否真正地消费到了这些消息。    
　　采用消息确认机制后，只要设置autoAck参数为false，消费者就有足够的时间处理消息（任务），不用担心处理消息的过程中消费者进程挂掉之后消息丢失的问题，因为RabbitMQ会一直等待持有消息知道消费者显式调用Basic.Ack命令为止。    
　　当autoAck参数设置为false，对于RabbitMQ服务端而言，队列中的消息分为了两个部分：一部分是等待投递给消费者的消息；一部分是已经投递给消费者，但是还没有收到消费者确认信号的消息。如果RabbitMQ一直没有收到消费者的确认信号，并且消费此消息的消费者已经断开连接，则RabbitMQ会安排该消息重新进入队列，等待投递给下一个消费者。    
　　RabbitMQ不会为未确认的消息设置过期时间，它判断此消息是否需要重新投递给消费者的唯一依据是消费该消息的消费者连接是否已经断开，这么涉及的原因是RabbitMQ允许消费者消费一条消息的时间可以很久很久。    
　　查看准备投递和未确认状态的队列消息命令：`rabbitmqctl list_queues name messages_ready messages_unacknowledged`    
    
　　在消费者接收到消息后，如果想明确拒绝当前的消息而不是确认，RabbitMQ在2.0.0版本引入了Basic.Reject这个命令，消费者客户端可以调用与其对应的`channel.basicReject`方法来告诉RabbitMQ拒绝这个消息。    
　　Channel类中的basicReject方法定义如下：    
`void basicReject(long deliveryTag, boolean requeue) throws IOException;`    

　　其中deliveryTag可以看作消息的编号，它是一个64位的长整型值。如果requeue参数设置未true，则RabbitMQ会重新将这条消息存入队列，以便可以发送给下一个订阅者；如果requeue参数设置为false，则RabbitMQ会立即把消息从队列中移除。    

　　Basic.Reject命令一次只能拒绝一条消息，如果想要批量拒绝消息，则可以使用`Basic.Nack`命令。消费者客户端可以调用`channel.basicNack`方法来实现，方法定义如下：    
`void basicNack(long deliveryTag, blloean multiple, boolean requeue) throws IOException;`    

　　其中multiple参数，如果为false则表示拒绝编号为deliveryTag的这一条消息，这个时候basicNack和basicReject方法一样；multiple参数设置为true则表示拒绝deliveryTag编号之前所有未被当前消费者确认的消息。     

　　对于requeue，AMQP中还有一个命令Basic.Recover具备可重入队列的特性。其对应的客户端方法为：    
（1） `Basic.RecoverOk basicRecover() throws IOException;`    
（2） `Basic.RecoverOk basicRecover(boolean requeue) throws IOException;`    

　　这个channel.basicRecover方法用来请求RabbitMQ重新发送还未被确认的消息。如果requeue参数为true，则未被确认的消息会被重新加入到队列中，这样对于同一条消息来说，可能会被分配给与之前不同的消费者。如果requeue为false，那么同一条消息还是会被分配给与之前相同的消费者。默认为true。    


## Close    
　　[see code](./src/main/java/com/issac/ch3/demo/CloseDemo.java)