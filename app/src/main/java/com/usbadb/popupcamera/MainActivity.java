package com.usbadb.popupcamera;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private TextView statusView;
    private boolean busy;
    private final Button[] buttons = new Button[3];
    private static final Pattern STATUS = Pattern.compile("MOTOR_STATUS=(\\d+)");
    private static final Pattern NOT_READY = Pattern.compile("MOTOR_NOT_READY=(\\d+)");
    // app_process runs in Magisk's SELinux domain, which cannot read this app's
    // private data directory on this Android 16 ROM. Stream the dex to a
    // root-readable temporary path for each command, then remove it.
    private static final String BRIDGE_PATH = "/data/local/tmp/popup_camera_motor_bridge.dex";
    // Magisk 30.7 on this phone reports /debug_ramdisk as its runtime directory.
    // Try that first, then common system locations and PATH for other ROM builds.
    private static final String[] SU_PATHS = {
            "/debug_ramdisk/su",
            "/product/bin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/apex/com.android.runtime/bin/su",
            "su"
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        runMotorCommand("status");
    }

    private void buildScreen() {
        int pad = dp(22);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, dp(32), pad, pad);

        TextView title = new TextView(this);
        title.setText("K30 前摄升降");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        statusView = new TextView(this);
        statusView.setText("正在读取电机状态…");
        statusView.setTextSize(18);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(30), 0, dp(24));
        root.addView(statusView, matchWrap());

        Button statusButton = makeButton("读取状态");
        Button upButton = makeButton("弹出前摄");
        Button downButton = makeButton("收回前摄");
        buttons[0] = statusButton;
        buttons[1] = upButton;
        buttons[2] = downButton;
        root.addView(statusButton, buttonParams());
        root.addView(upButton, buttonParams());
        root.addView(downButton, buttonParams());

        TextView note = new TextView(this);
        note.setText("首次使用时，请在 Magisk 弹窗中允许本应用获取 root 权限。\n状态码 14 的实际位置含义未确认；请以镜头实际位置为准，电机卡住或有异响时停止操作。");
        note.setTextSize(14);
        note.setPadding(0, dp(26), 0, 0);
        root.addView(note, matchWrap());

        statusButton.setOnClickListener(v -> runMotorCommand("status"));
        upButton.setOnClickListener(v -> runMotorCommand("up"));
        downButton.setOnClickListener(v -> runMotorCommand("down"));
        setContentView(root);
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        params.topMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void runMotorCommand(String action) {
        if (busy) return;
        busy = true;
        for (Button button : buttons) button.setEnabled(false);
        statusView.setText(action.equals("up") ? "正在弹出前摄…" :
                action.equals("down") ? "正在收回前摄…" : "正在读取电机状态…");

        new Thread(() -> {
            String output = "";
            int exitCode = -1;
            try {
                String command = "cat > " + BRIDGE_PATH
                        + " && chmod 0644 " + BRIDGE_PATH
                        + " && CLASSPATH=" + BRIDGE_PATH
                        + " /system/bin/app_process /system/bin MotorBridge " + action
                        + "; result=$?; rm -f " + BRIDGE_PATH + "; exit $result";
                Process process = startRootProcess(command);
                try (InputStream dex = getAssets().open("motor_bridge.dex");
                     OutputStream stdin = process.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = dex.read(buffer)) != -1) stdin.write(buffer, 0, count);
                }
                StringBuilder result = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (result.length() < 2500) result.append(line).append('\n');
                    }
                }
                if (!process.waitFor(45, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IllegalStateException("root 命令超时");
                }
                exitCode = process.exitValue();
                output = result.toString().trim();
            } catch (Exception e) {
                output = e.getClass().getSimpleName() + ": " + e.getMessage();
            }

            final String resultText = formatResult(output, exitCode);
            final boolean confirmUnknownRetract = output.contains("MOTOR_CONFIRM_REQUIRED=14");
            runOnUiThread(() -> {
                statusView.setText(resultText);
                busy = false;
                for (Button button : buttons) button.setEnabled(true);
                if (confirmUnknownRetract) showUnknownStatusRetractConfirmation();
            });
        }).start();
    }

    private void showUnknownStatusRetractConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("确认尝试收回")
                .setMessage("电机服务返回状态码 14，但它的实际位置含义未确认。\n\n请先确认前摄实际已弹出并保持静止，而且没有异响或卡住；确认后才会发送一次收回指令。")
                .setNegativeButton("先不操作", null)
                .setPositiveButton("确认收回", (dialog, which) -> runMotorCommand("down-confirmed"))
                .show();
    }

    private Process startRootProcess(String command) throws IOException {
        IOException lastError = null;
        for (String path : SU_PATHS) {
            try {
                return new ProcessBuilder(path, "-c", command)
                        .redirectErrorStream(true).start();
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw new IOException("找不到本应用可访问的 Magisk su 程序（已尝试常见路径）。"
                + "如果仍失败，请在隐藏模块中取消对本应用的隐藏。", lastError);
    }

    private String formatResult(String output, int exitCode) {
        if (output.contains("MOTOR_HAL_MISSING=")) {
            return "系统未找到 K30 前摄电机服务。\n"
                    + "请确认当前 MYUI 已加载并注册 Redmi K30 的电机 HAL。";
        }
        if (output.contains("MOTOR_CONFIRM_REQUIRED=14")) {
            return "系统状态码为 14，本次尚未发送收回指令；请确认镜头实际位置。";
        }
        Matcher notReady = NOT_READY.matcher(output);
        if (notReady.find()) {
            return "电机服务返回状态码 " + notReady.group(1)
                    + "，本次没有发送电机指令。请以镜头实际位置为准。";
        }
        Matcher matcher = STATUS.matcher(output);
        String actionResult = output.contains("MOTOR_CALL=OK") ? "电机指令已发送。\n" : "";
        if (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1));
            String state;
            switch (code) {
                case 11: state = "前摄已弹出"; break;
                case 13: state = "前摄已收回"; break;
                case 12: state = "前摄正在弹出"; break;
                case 14: state = "电机服务状态码 14（位置含义未确认）"; break;
                case 15: state = "电机状态异常"; break;
                case 17: state = "电机初始化中"; break;
                case 18: state = "电机未初始化"; break;
                case 19: state = "电机暂不可用"; break;
                case -1: state = "无法读取电机状态"; break;
                default: state = "未知电机状态"; break;
            }
            return actionResult + state + "（状态码 " + code + "）";
        }
        if (exitCode == 0 && output.isEmpty()) return actionResult + "操作完成。";
        String detail = output.isEmpty() ? "没有收到电机反馈" : output;
        if (detail.length() > 900) detail = detail.substring(0, 900);
        return "操作失败（退出码 " + exitCode + "）\n" + detail;
    }
}
