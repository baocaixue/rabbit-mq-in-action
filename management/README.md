# RabbitMQ管理（服务端）    
- 多租户与权限 ...................................................[1](#Multi-Tenancy-And-Permissions)
- 用户管理 ...................................................[2](#User-Management)
- Web端管理 ...................................................[3](#Web-Management)
- 应用与集群管理 ...................................................[4](#Application-Cluster-Management)
- 应用管理 ...................................................[4.1](#Application-Management)
- 集群管理 ...................................................[4.2](#Cluster-Management)
- 服务端状态 ...................................................[5](#Server-Status)
- HTTP API 接口管理 ...................................................[6](#Http-API)    

***    
　　
## Multi-Tenancy-And-Permissions    
　　每个RabbitMQ服务器都能创建虚拟的消息服务器，称之为虚拟主机（virtual host），简称vhost。每一个vhost本质上是一个独立的小型RabbitMQ服
务器，拥有自己独立的队列、交换器及绑定关系，并且它拥有自己独立的权限。vhost就像是虚拟机与物理服务器一样，它们在各个实例间提供逻辑上的分离，为
不同程序安全保密地运行数据，**它既能将同一个RabbitMQ中的众多客户区分开，又可以避免队列和交换器等命名冲突**。vhost之间是绝对隔离的，无法将
vhost1中的交换器与vhost2中的队列进行绑定，这样既保证了安全性，又可以确保可移植性。如果在使用RabbitMQ大到一定规模的时候，建议用户对业务功能、
场景进行归类区分，并为之分配独立的vhost。    
　　vhost是AMQP概念的基础，客户端在连接的时候必须制定一个vhost。RabbitMQ默认创建的vhost为"/"，如果不需要多个vhost或者对vhost的概念不是
很理解，那么用这个默认的vhost也是非常合理的，使用默认用户名guest和密码guest就可以访问它。但是为了安全和方便，建议重新建立一个新的用户来访问。    
　　可以使用`rabbitmqctl add_vhost {vhost}`命令创建一个新的vhost，示例如下：    
```shell script
[root@node1 ~]# rabbitmqctl add_vhost vhost1
Creating vhost "vhost1"
```    
　　可以使用`rabbitmqctl list_vhosts [vhostinfoitem...]`来罗列当期vhost的相关信息。目前vhostinfoitem的取值有2个。    
* name: 表示vhost的名称    
* tracing: 表示是否使用了RabbitMQ的trace功能    

　　对应的删除vhost的命令是：`rabbitmqctl delete_vhost {vhost}`。删除一个vhost的同时也会删除其下所有的队列、交换器、绑定关系、用户权限、
参数和策略等信息。    
　　AMQP协议中并没有指定权限在vhost界别还是在服务器级别实现，由具体的应用自定义。**在RabbitMQ中，权限控制则是以vhost为单位的**。当创建一个
用户时，用户通常会被指派给至少一个vhost，并且只能访问被指派的vhost内的队列、交换器和绑定关系等。因此，RabbitMQ中的授予权限是指在vhost级别
对用户而言的权限授予。    
　　
　　相关的授予权限命令为：`rabbitmqctl set_permissions [-p vhost] {user} {conf} {write} {read}`。其中各个参数含义如下所述：    
* vhost: 授予用户访问权限的vhost名称，可以设置为默认值，即vhost为“/”
* user: 可以访问制定vhost的用户名
* conf: 一个用于匹配用户在哪些资源上拥有可配置权限的正则表达式
* write: 一个用于匹配用户在哪些资源上拥有可写权限的正则表达式
* read: 一个用于匹配用户在哪些资源上拥有可读取权限的曾泽表式    
　　**注：** *可配置指的是队列和交换器的创建以及删除之类的操作；可写指的是发布消息；可读指的是与消息有关的操作，包括读取消息和清空整个队列*    

　　下面表格展示AMQP命令的列表和对应的权限    

| AMQP命令 | 可配置 | 可写 | 可读 
| --- | --- | --- | ---    
| Exchange.Declare | exchange |   |         
| Exchange.Declare(with AE-Alternate Exchange) | exchange | exchange(AE) | exchange    
| Exchange.Delete | exchange |   |        
| Queue.Declare | queue |   |       
| Queue.Declare(with DLX-Dead Letter Exchange) | queue | exchange(DLX) | queue    
| Queue.Delete | queue |   |        
| Exchange.Bind |   | exchange(destination) | exchange(source)    
| Exchange.Unbind |   | exchange(destination) | exchange(source)    
| Queue.Bind |   | queue | exchange    
| Queue.Unbind |   | queue | exchange    
| Basic.Publish |   | exchange |        
| Basic.Get |   |   | queue      
| Basic.Consume |   |   | queue    
| Queue.Purge |   |   | queue    


 　　授予root用户可以访问虚拟主机vhost1，并在所有资源上具备可配置、可读、可写的权限，示例如下：    
 ```shell script
[root@node1 ~]# rabbitmqctl set_permissions -p vhost1 root ".*" ".*" ".*"
Setting permissions for user "root" in vhost "vhost1"
```     
　　授予root用户访问虚拟主机vhost2，在以“queue”开头的资源上具备可配置权限，并在所有资源上拥有可写、可读权限，示例如下：    
```shell script
[root@node ~]# rabbitmqctl set_permissions -p vhost2 root "^queue.*" ".*" ".*"
Setting permissions for user "root" int vhost "vhost2"
```    

　　清除权限也是在vhost级别对用户而言的。清除权限的命令为`rabbitmqctl clear_permissions [-p vhost] {username}`。示例如下：    
```shell script
[root@node1 ~]# rabbitmqctl clear_permisssions -p vhost1 root
Clearing permissions for user "root" in vhost "vhost1"
```    

　　在RabbitMQ中有两个Shell命令可以列举权限信息。第一个命令是`rabbitmqctl list_permissions [-p vhost]`，用来显示虚拟主机上的权限；
第二个命令`rabbitmqctl list_user_permissions {username}`，用来显示用户的权限。    

　　**rabbitmqctl**工具是用来管理RabbitMQ中间件的命令行工具，它通过连接哥哥RabbitMQ节点来执行所有操作。如果节点没有运行，将显示诊断信息：
不能到达或因不匹配的Erlang cookie而拒绝连接。    

　　rabbitmqctl工具的标准语法如下（[]表示可选参数，{}表示必选参数）：    
　　`rabbitmqctl [-n node] [-t timeout] [-q] {command} [command options...]`    
* \[-n node\]    
　　默认节点是“rabbit@hostname”，此处的hostname是主机名称。在一个名为“node.hidden.com”的主机上，RabbitMQ节点的名称通常是rabbit@node
（除非RABBITMQ_NODENAME参数在启动时被设置成了非默认值）。hostname -s命令的输出通常是“@”标志之后的东西。    

* \[-q\]    
　　使用-q标志来启用quiet模式，这样可以屏蔽一些消息的输出。默认不开启quiet模式。    

* \[-t timeout\]    
　　操作超时时间（单位为秒），只适用于“list_xxx”类型的命令，默认是无穷大。    
