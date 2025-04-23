package com.codingstuff.todolist;

import android.app.Application;

import com.jakewharton.threetenabp.AndroidThreeTen;

public class ToDoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Khởi tạo thư viện ThreeTenABP
        AndroidThreeTen.init(this);
    }
}
