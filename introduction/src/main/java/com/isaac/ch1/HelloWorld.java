package com.isaac.ch1;

import java.util.function.Consumer;

public class HelloWorld {
    public static void main(String[] args){
        hello(System.out::print);
    }

    private static void hello(Consumer<String> consumer) {
        consumer.accept("Hello World!\n");
    }
}
