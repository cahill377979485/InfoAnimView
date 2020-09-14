package com.cahill377979485.infoanimview;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private InfoAnimView iav;
    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iav = findViewById(R.id.iav);
        iav.setDesc("378.9");
        iav.setClickListener(new InfoAnimView.clickListener() {
            @Override
            public void clickCiv() {
                Toast.makeText(MainActivity.this, "点击了头像", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void clickBtn() {
                Toast.makeText(MainActivity.this, "点击了按钮，开始显示信息", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void clickInfo(String desc) {
                Toast.makeText(MainActivity.this, "点击了信息" + desc, Toast.LENGTH_SHORT).show();
            }
        });
        tv = findViewById(R.id.tv);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Glide.with(MainActivity.this)
                        .load("http://portrait7.sinaimg.cn/2947893190/blog/180")
                        .placeholder(R.mipmap.ic_default_head_girl)
                        .error(R.mipmap.ic_default_head_girl)
                        .downloadOnly(new SimpleTarget<File>() {
                            @Override
                            public void onResourceReady(@NonNull File resource, @Nullable Transition<? super File> transition) {
                                iav.setCivPath(resource.getAbsolutePath());
                            }
                        });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        iav.release();
    }
}