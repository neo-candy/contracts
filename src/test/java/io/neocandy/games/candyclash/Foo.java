package io.neocandy.games.candyclash;

import java.util.Random;

public class Foo {
    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            System.out.println(foo());
        }

    }

    private static int foo() {
        Random rand = new Random();
        int randomNum = rand.nextInt((10000000 - 1) + 1) + 1;
        int result = (randomNum & 0xFFFFFFFF) % 2;
        return result;
    }
}