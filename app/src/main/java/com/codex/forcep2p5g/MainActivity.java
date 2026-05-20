package com.codex.forcep2p5g;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String[] TARGETS = {
        "com.heytap.accessory",
        "com.heytap.accessory:service",
        "com.heytap.accessory:ui",
        "com.heytap.accessory.Plugin",
        "com.oplus.subsys",
        "com.oplus.virtualcomm2",
    };

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 48);

        TextView title = new TextView(this);
        title.setTextSize(20);
        title.setText("ForceP2P5G / VcShare Fix");
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Hook 作用于：\n" +
            "· com.heytap.accessory（强制 P2P 5GHz）\n" +
            "· com.oplus.subsys（VcCapability forbidFlag = 0）\n\n" +
            "修改模块后点下面按钮杀掉目标进程，下次被拉起时会装载新 hook。不需要重启 pad。");
        desc.setPadding(0, 24, 0, 32);
        root.addView(desc);

        Button restart = new Button(this);
        restart.setText("重启所有 hook 作用域进程");
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartScopes();
            }
        });
        root.addView(restart);

        status = new TextView(this);
        status.setPadding(0, 32, 0, 0);
        status.setText("状态：待命");
        status.setGravity(Gravity.START);
        root.addView(status);

        setContentView(root);
    }

    private void restartScopes() {
        status.setText("状态：正在执行...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                Process p = null;
                try {
                    p = Runtime.getRuntime().exec(new String[]{"su"});
                    OutputStream os = p.getOutputStream();
                    for (String t : TARGETS) {
                        String cmd = "am force-stop " + t + "\n";
                        os.write(cmd.getBytes());
                        sb.append(cmd);
                    }
                    os.write("exit\n".getBytes());
                    os.flush();
                    os.close();

                    int rc = p.waitFor();
                    final boolean ok = (rc == 0);
                    final String msg = ok
                        ? "完成：已重启以下进程\n" + sb
                        : "失败：su 返回 " + rc;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            status.setText(msg);
                            Toast.makeText(MainActivity.this,
                                ok ? "Done" : "Failed (su rc=" + rc + ")",
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    final String err = "异常：" + e;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            status.setText(err);
                            Toast.makeText(MainActivity.this,
                                "需要 KSU/Magisk root 授权",
                                Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }
}
