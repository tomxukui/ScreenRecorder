package net.yrom.screenrecorder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;

import net.yrom.screenrecorder.R;

public class MainActivity extends AppCompatActivity {

    private Button btn_screenRecorder1;
    private Button btn_screenRecorder2;
    private Button btn_screenRecorder3;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        setView();
    }

    private void initView() {
        btn_screenRecorder1 = findViewById(R.id.btn_screenRecorder1);
        btn_screenRecorder2 = findViewById(R.id.btn_screenRecorder2);
        btn_screenRecorder3 = findViewById(R.id.btn_screenRecorder3);
    }

    private void setView() {
        btn_screenRecorder1.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ScreenRecorder1Activity.class);
            startActivity(intent);
        });

        btn_screenRecorder2.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ScreenRecorder2Activity.class);
            startActivity(intent);
        });

        btn_screenRecorder3.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ScreenRecorder3Activity.class);
            startActivity(intent);
        });
    }

}