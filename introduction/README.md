# RabbitMQ简介  
- 什么是消息中间件 ...................................................[1](#What-is-Message)
- 消息中间件的作用 ...................................................[2](#Message-Usage)
- RabbitMQ的起源 ...................................................[3](#RabbitMQ)
- RabbitMQ的安装以及简单使用 ...................................................[4](#Use-RabbitMQ)
    - 安装Erlang ...................................................[4.1](#Install-Erlang)
    - 安装RabbitMQ ...................................................[4.2](#Install-RabbitMQ)
    - RabbitMQ的运行 ...................................................[4.3](#Run-RabbitMQ)
    - 生产和消费消息 ...................................................[4.4](#Produce-Consume-Message)      
    
- 小结 ...................................................[5](#Summary)



*** 
## What-is-Message  
&nbsp;&nbsp;消息（Message）是指在应用间传送的数据。消息可以非常简单，如只包含文本字符串、JSON等，也可以很复杂，如内嵌对象。  
&nbsp;&nbsp;消息队列中间件（Message Queue Middleware，简称MQ）是指利用高效可靠的消息传递机制进行与平台无关的数据交流，并基于数据通信来进行分布式系统的集成。通过提供消息传递和消息排队模型，它可以在分布式环境下扩展进程间的通信。  
&nbsp;&nbsp;消息队列中间件，也可以称为消息队列或消息中间件。它一般有两种传递模式：**点对点（P2P，Point-to-Point）** 模式和**发布/订阅（Pub/Sub）** 模式。点对点模式是基于队列的，消息生产者发送消息到队列，消息消费者从队列中接收消息，队列的存在使得消息的异步传输称为可能。发布订阅模式定义了如何向一个内容节点发布和订阅消息，这个内容节点称为“主题”（topic），主题可以认为是消息传递的中介，消息发布者将消息发布到某个主题，而消息订阅者则从主题中订阅消息。主题使得订阅者与消息发布者互相保持独立，不需要进行接触即可保证消息的传递，发布/订阅模式在消息的一对多广播时采用。  


***
## Message-Usage  
* 解耦
* 冗余（存储）
* 扩展性
* 削峰
* 可恢复性
* 顺序保证
* 缓冲
* 异步通信

*** 
## RabbitMQ  
&nbsp;&nbsp;RabbitMQ采用Erlang语言实现AMQP（Advanced Message Queuing Protocol，高级消息队列协议）的中间件。  
&nbsp;&nbsp;RabbitMQ具体特点：  
* 可靠性
* 灵活路由
* 扩展性
* 高可用性
* 多种协议：处理原生支持AMQP协议，还支持STOMP、MQTT等多种消息中间件协议  
* 多语言客户端
* 管理界面：`>rabbitmq-plugins enable  rabbitmq_management`
* 插件机制：`>rabbitmq-plugins enable  rabbitmq_management`

***  
##  Use-RabbitMQ   
https://www.rabbitmq.com/install-rpm.html .  

### Install-Erlang  
Follow the steps in the [EPEL FAQ](https://fedoraproject.org/wiki/EPEL/FAQ#howtouse) to enable EPEL on the target machine . 

`su -c 'rpm -Uvh https://download.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm'`   
`sudo yum install erlang`

### Install-RabbitMQ   
quick install: `curl -s https://packagecloud.io/install/repositories/rabbitmq/rabbitmq-server/script.rpm.sh | sudo bash` .  

`wget https://www.rabbitmq.com/releases/rabbitmq-server/v3.6.10/rabbitmq-server-3.6.10-1.el7.noarch.rpm` . 
`yum install rabbitmq-server-3.6.10-1.el7.noarch.rpm` . 

### Run-RabbitMQ   
`service rabbitmq-server start`    
开放端口：  
```bash
firewall-cmd --zone=public --add-port=5672/tcp --permanent
firewall-cmd --zone=public --add-port=15672/tcp --permanent
firewall-cmd --reload
```   
管理页面插件：  
`rabbitmq-plugins enable rabbitmq_management`

### Produce-Consume-Message


***

## Summary
