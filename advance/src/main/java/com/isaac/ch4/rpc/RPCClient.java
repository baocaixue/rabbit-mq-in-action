package com.isaac.ch4.rpc;

import com.isaac.ch4.RabbitMqInfo;
import com.rabbitmq.client.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class RPCClient {
    private static final String RPC_QUEUE_NAME = "rpcQueue";

    private Connection connection;
    private Channel channel;
    private String replyQueueName;
    private QueueingConsumer consumer;

    public RPCClient() throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(RabbitMqInfo.URI);
        connection = factory.newConnection();
        channel = connection.createChannel();

        replyQueueName = channel.queueDeclare().getQueue();
        consumer = new QueueingConsumer(channel);
        channel.basicConsume(replyQueueName, false, consumer);
    }

    public String call(String message) throws Exception {
        String response = null;
        String corrId = UUID.randomUUID().toString();

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId(corrId)
                .replyTo(replyQueueName)
                .build();
        channel.basicPublish("", RPC_QUEUE_NAME, props, message.getBytes(StandardCharsets.UTF_8));

        while (true){
            QueueingConsumer.Delivery delivery = consumer.nextDelivery();
            if(delivery.getProperties().getCorrelationId().equals(corrId)){
                response = new String(delivery.getBody());
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                break;
            }
        }

        return response;
    }

    public void close() throws Exception {
        connection.close();
    }

    public static void main(String[] args) throws Exception{
        RPCClient client = new RPCClient();
        System.out.println(" [x] Requesting fib(30)");
        String response = client.call("30");
        System.out.println(" [.] Got '" + response + "'");
        client.close();
    }
}
