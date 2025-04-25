package com.codingstuff.todolist;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Không cần ẩn ActionBar vì đã sử dụng theme NoActionBar

        try {
            // Sử dụng Handler an toàn hơn với Looper.getMainLooper()
            new Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 3000); // Giảm thời gian chờ xuống 3 giây để trải nghiệm tốt hơn
        } catch (Exception e) {
            e.printStackTrace();
            // Nếu có lỗi, chuyển ngay đến LoginActivity
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        }
    }
}