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
&nbsp;&nbsp;RabbitMQ整体上是一个生产者与消费者模型，主要负责接收、存储和转发消息。可以将消息传递的过程理解为：当你把一个包裹送到邮局，邮局会暂存并最终将邮件通过邮递员送到收件人手上，RabbitMQ就好比由邮局、邮箱和邮递员组成的一个系统。从计算机术语层面来说，RabbitMQ模型更像是一种交换机模型。  

### Producers-And-Consumers  
&nbsp;&nbsp;Producer：生产者，投递消息的一方。  
&nbsp;&nbsp;生产者创建消息，然后发布到RabbitMQ中。消息一般包含2个部分：消息体和标签（Label）。消息体也可以称之为payload，在实际应用中，消息体一般是一个带有业务逻辑结构的数据，如一个JSON字符串。当然可以进一步对这个消息体进行序列化操作。消息的标签用来描述这条消息，比如一个交换器的名称和一个路由键。生产者把消息交由RabbitMQ，RabbitMQ之后会根据标签把消息发送给感兴趣的消费者（Consumer）。  
&nbsp;&nbsp;Consumer：消费者，接收消息的一方。  
&nbsp;&nbsp;消费者连接到RabbitMQ服务器，并订阅到队列上。当消费者消费一条消息时，只是消费消息的消息体（payload）。在消息路由的过程中，消息的标签会丢弃，存入到队列中的消息只有消息体，消费者也只会消费消息体，也就不知道消息的生产者是谁。  
&nbsp;&nbsp;Broker：消息中间件的服务节点。  
&nbsp;&nbsp;对RabbitMQ来说，一个RabbitMQ Broker可以简单看作一个RabbitMQ的服务节点，或者RabbitMQ的服务实例。大多数情况下也可以将一个RabbitMQ Broker看作一台RabbitMQ服务器。  
&nbsp;&nbsp;对于生产者将消息存入RabbitMQ Broker，以及消费者从Broker消费数据的流程一般是：  
&nbsp;&nbsp;首先生产者将业务方数据进行可能的包装，之后封装成消息，发送（AMQP协议里这个动作对应的命令是Basic.Publish）到Broker中。消费者订阅并接收消息（AMQP协议中这个动作对应的命令为Basic.Consume或Basic.Get），经过可能的解包处理得到原始的数据，之后再进行业务处理逻辑。这个业务处理逻辑并不一定需要和接收消息的逻辑使用同一个线程。消费者进程可以使用一个进程取接收消息，存入到内存中，比如使用Java中的BlockingQueue。业务处理逻辑使用另一个线程从内存中读取数据，这样将应用进一步解耦，提高整个应用的处理效率。  


### Queue  
&nbsp;&nbsp;Queue：队列，是RabbitMQ的内部对象，用于存储消息。  
&nbsp;&nbsp;RabbitMQ中的消息都只能存储在队列中，这一点与Kafka这种消息中间件相反。Kafka将消息存储在topic（主题）这个逻辑层面，而相对应的队列逻辑只是topic实际存储文件中的位移标识。RabbitMQ的生产者产生消息并最终投递到队列中，消费者可以从队列中获取消息并消费。  
&nbsp;&nbsp;多个消费者可以订阅同一个队列，这是队列的消息会被平均分摊（Round-Robin，即轮询）给多个消费者进行处理，而不是每个消费者都接收到所有消息并处理。  
&nbsp;&nbsp;RabbitMQ不支持队列层面的广播消费，如果需要广播消费，需要在其上进行二次开发，处理逻辑会变得复杂，不建议。  

### Exchange-RoutingKey-Binding  
&nbsp;&nbsp;Exchange：交换器。生产者将消息发送到Exchange（交换器，通常也可以用大写的“X”来表示），由交换器将消息路由到一个或多个队列中。如果路由找不到，或许返回给生产者，或许直接丢弃。这里可以将RabbitMQ中的交换器看作一个简单的实体。  
&nbsp;&nbsp;RabbitMQ中的交换器由四种类型，不同的类型有着不同的路由策略。  
&nbsp;&nbsp;RoutingKey：路由键。生产者将消息发送给交换器的时候，一般会指定一个RoutingKey，用来指定这个消息的路由规则，而这个RoutingKey需要与交换器类型和绑定键（BindingKey）联合使用才能生效。  
&nbsp;&nbsp;在交换器类型和绑定键固定的情况下，生产者可以在发送消息给交换器时，通过指定RoutingKey来决定消息流向哪里。  
&nbsp;&nbsp;Binding：绑定。RabbitMQ中通过绑定将交换器和队列关联起来，在绑定的时候一般会指定一个绑定键（BindingKey），这样RabbitMQ就知道如何正确地将消息路由到队列了。  
&nbsp;&nbsp;生产者将消息发送给交换器时，需要一个RoutingKey，当BindingKey和RoutingKey相匹配时，消息会被路由到对应的队列中。在绑定多个队列到同一个交换器的时候，这些绑定允许使用相同的BindingKey。BindingKey并不是在所有情况下都生效，它依赖于交换器类型，比如fanout类型的交换器就会五十BindingKey，而是将消息路由到所有绑定到该交换器的队列中。  
&nbsp;&nbsp;direct类型的 交换器RoutingKey和BindingKey完全匹配才能使用给，而topic交换器类型下，RoutingKey和BindingKey之间需要做模糊匹配，两者并不是相同的。  
&nbsp;&nbsp;BindingKey其实也属于路由键中的一种，官方解释为：`the routing key to use for the binding`。大多时候，都把BindingKey和RoutingKey看作RoutingKey，为了避免混淆，可以这么理解：  
* 在使用绑定的时候，其中需要的路由键是BindingKey。涉及的客户端方法如：channel.exchangeBind、channel.queueBind，对应的AMQP命令为Exchange.Bind、Queue.Bind。  
* 在发送消息的时候，其中需要的路由键是RoutingKey。涉及的客户端方法如：channel.basicPublish，对应的AMQP命令为Basic.Publish。   


### Exchange-Type   
&nbsp;&nbsp;RabbitMQ常用的交换器类型有 fanout、 direct、 topic、 headers这四种。AMQP协议里还提到另外两种类型：System和自定义，这里不予描述。  
&nbsp;&nbsp;**fanout**  
&nbsp;&nbsp;它会把所有发送到该交换器的消息路由到所有与该交换器绑定的队列中。 
&nbsp;&nbsp;**direct**  
&nbsp;&nbsp;direct类型的交换器会把消息路由到那些BindingKey和RoutingKey完全匹配的队列中。  
&nbsp;&nbsp;**topic**  
&nbsp;&nbsp;topic类型的交换器，在匹配规则上做了扩展，它与direct类型的交换器类似，也是消息路由到BindingKey和RoutingKey相匹配的队列中，但匹配规则有些不同，它约定：  
&nbsp;&nbsp; * RoutingKey为一个点号“.”分隔的字符串，如“com.rabbitmq.client”、“java.util.concurrent”、“com.hidden.client”；  
&nbsp;&nbsp; * BindingKey和RoutingKey一样也是点号分隔的字符串；  
&nbsp;&nbsp; * BindingKey中可以存在两种特殊的字符串“\*”和“#”，用于做模糊匹配，其中“\*”用于匹配一个单词，“#”用于匹配多规则单词（可以是零个）。  