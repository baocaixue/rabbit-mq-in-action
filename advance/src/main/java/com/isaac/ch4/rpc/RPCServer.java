package com.isaac.ch4.rpc;

import com.isaac.ch4.RabbitMqInfo;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ RPC Server
 * 客户端通过RPC来调用服务端的方法得到相应的菲波那契值
 */
public class RPCServer {
    private static final String RPC_QUEUE_NAME = "rpcQueue";

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(RabbitMqInfo.URI);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null);
        channel.basicQos(1);
        System.out.println(" [x] Awaiting RPC requests");

        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                AMQP.BasicProperties replyProps = new AMQP.BasicProperties.Builder()
                        .correlationId(properties.getCorrelationId())
                        .build();
                String response = "";
                try {
                    String message = new String(body, StandardCharsets.UTF_8);
                    int n = Integer.parseInt(message);
                    System.out.println(" [.] flib(" + message + ")");
                    response += fib(n);
                } catch (Exception e) {
                    System.out.println(" [.] " + e.toString());
                } finally {
                    channel.basicPublish("", properties.getReplyTo(), replyProps, response.getBytes(StandardCharsets.UTF_8));
                    channel.basicAck(envelope.getDeliveryTag(), false);
                    //用作演示，所以处理完一个就关闭
                    connection.close();
                }
            }
        };

        channel.basicConsume(RPC_QUEUE_NAME, false, consumer);

    }

    private static int fib(int n) {
        if(n == 0) return 0;
        if(n == 1) return 1;
        return fib(n -1) + fib(n - 2);
    }

}
