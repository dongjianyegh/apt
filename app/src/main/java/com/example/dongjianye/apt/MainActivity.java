package com.example.dongjianye.apt;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.example.apt.facade.annotation.Route;

@Route(path = "/test/MainActivity")
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
