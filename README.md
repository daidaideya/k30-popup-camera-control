# K30 前摄升降控制

这是一个用于 Redmi K30 Pro / POCO F2 Pro（设备代号 `lmi`）的简易 Android 应用。应用提供“弹出前摄”“收回前摄”和“读取状态”按钮，通过 Magisk root 调用系统已有的 `vendor.xiaomi.hardware.motor@1.0::IMotor` 服务。

## 使用

1. 从 [GitHub Releases 页面](https://github.com/daidaideya/k30-popup-camera-control/releases/latest) 下载并安装 `PopupCameraControl.apk`。如果已有旧版，可以直接覆盖更新。
2. 首次操作时，在 Magisk 弹窗中允许本应用获取 root 权限。
3. 使用“弹出前摄”或“收回前摄”；状态文本会显示电机反馈。

应用不会开机自动弹出，也不会后台持续运行。仅在用户按下按钮时发送动作。电机 HAL 返回的整数状态码不等同于经过验证的物理位置；尤其状态码 14 的公开含义未确认。若状态为 14，收回操作会先要求用户确认镜头实际已弹出、静止且无异响，再发送收回指令。遇到卡滞或异响时请停止操作。

## 设备要求

- Redmi K30 Pro / POCO F2 Pro（`lmi`）或提供相同小米电机 HAL 的兼容 ROM
- Magisk root
- ROM 中存在并运行 `vendor.xiaomi.hardware.motor@1.0::IMotor/default`

应用自身不需要相机、网络或共享存储权限。控制过程使用单独的 root `app_process` 访问系统电机服务。每次操作时，应用把随 APK 携带的控制程序通过 root 标准输入临时写到 `/data/local/tmp`，运行后立即删除，避免 Android 16 的 SELinux 阻止 root 子进程读取应用私有目录。

## 文件

- `app/src/main`：界面与应用清单
- `bridge/MotorBridge.java`：root 进程中的电机 HAL 调用
- `app/src/main/assets/motor_bridge.dex`：随 APK 携带的 root 控制程序
