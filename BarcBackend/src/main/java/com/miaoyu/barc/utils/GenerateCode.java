package com.miaoyu.barc.utils;

import java.util.Random;

public class GenerateCode {
    public String code(int length){
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int randomNum = random.nextInt(10);
            sb.append(randomNum);
        }
        return sb.toString();
    }
}
