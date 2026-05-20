package com.codex.forcep2p5g;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
        desc.setText("Hook 作用：\n" +
            "· com.heytap.accessory（强制 P2P 5GHz）\n" +
            "· com.oplus.subsys（VcCapability forbidFlag = 0）\n\n" +
            "改 hook 后点这里杀掉目标进程，下次被拉起就装上新 hook。" +
            "系统 UID 进程（subsys 等）会用 kill -9 强杀，需要 KSU root 授权。");
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
                    // 1) Try am force-stop first (works for normal apps)
                    for (String t : TARGETS) {
                        String pkg = t.contains(":") ? t.substring(0, t.indexOf(':')) : t;
                        os.write(("am force-stop " + pkg + "\n").getBytes());
                    }
                    // 2) kill -9 by process name (works for system UID apps where
                    //    force-stop is silently rejected). pkill -f matches full
                    //    cmdline so we hit the truncated names in /proc.
                    for (String t : TARGETS) {
                        os.write(("pkill -9 -f " + t + " || true\n").getBytes());
                        sb.append("am force-stop / pkill -9 -f ").append(t).append('\n');
                    }
                    os.write("exit\n".getBytes());
                    os.flush();
                    os.close();

                    int rc = p.waitFor();
                    final boolean ok = (rc == 0);
                    final String msg = ok
                        ? "完成：已尝试重启以下进程\n" + sb
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
