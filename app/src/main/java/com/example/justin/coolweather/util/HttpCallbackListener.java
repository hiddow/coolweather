package com.example.justin.coolweather.util;

/**
 * Created by Justin on 2014/12/30.
 */
public interface HttpCallbackListener {
    void onFinish(String response);
    void onError(Exception e);
}
