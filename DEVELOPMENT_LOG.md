# Dreamscape V12 开发档案（持续维护）

最后整理：2026-10-22。此文档用于在聊天历史无法完整加载、跨对话开发时快速恢复上下文。内容以 GitHub 仓库代码和手机实测为准。

## 核心目标
使用 vivo S30 Android 手机作为空间画笔，在真实环境中绘制、观察、编辑三维线条。未来扩展至可进入的虚拟三维世界、虚拟角色，以及在有 VR 设备时支持 VR。无需当前购入昂贵 VR 硬件。**V12 与旧版 V11 手势绘画项目独立，禁止误改 V11。**

## 仓库及发布
- 仓库：yejihe/dreamscape-v12-spatial-pen
- GitHub Pages：https://yejihe.github.io/dreamscape-v12-spatial-pen/
- Android 包名：com.dreamscape.arprobe
- GitHub Actions 构建流程：.github/workflows/android-arcore.yml
- APK 通过 Actions -> Artifacts 下载；新版本尽量保持同一签名以支持覆盖安装。
- GitHub Secrets：DREAMSCAPE_KEYSTORE_BASE64 和 DREAMSCAPE_KEYSTORE_PASSWORD。**绝对不能把签名密钥、密码、Base64 内容提交到公开仓库。**

## 已确认事实
- 浏览器 WebXR immersive-ar 在测试机上曾返回 NotSupportedError，不应据此否定 Android 原生 ARCore。
- Android 原生 ARCore 已在 vivo S30 测到真实摄像头透视、TRACKING / NONE、6DoF XYZ + 姿态数据。
- 用户确认摄像头运动流畅（A），但预览略模糊。
- 用户实际反馈：最初 3D 线条太细难看；绘画按钮太小，应在底部、按下有明显反馈；v0.3.1 实机截图显示底部按钮内容被截断。
- 原生 ARCore 跟踪成功不等于定位精度、长期稳定性或真实笔尖校准完成。

## 当前代码（以最新 Gradle 版本为准）
- Android: android/app/src/main/java/com/dreamscape/arprobe/MainActivity.java
- 功能：三维世界空间轨迹、可调笔刷粗细、按住绘画、松开停止、撤销、清空；追踪诊断、标记/漂移检查、空间锚点和部分过滤功能。
- v0.3.7：进一步压缩移动端 UI，将漂移诊断折叠，优先给摄像头和底部绘画按钮留空间。**必须检查 CI 构建与手机实际效果，不能仅凭代码提交宣称运行正常。**
- 手机顶部中央作为实体笔尖的位姿标定仍待实现；目前为相机姿态推算的虚拟笔尖。

## 产品交互原则
- 单手操作优先：大绘画按钮放屏幕底部；按下红色、松开蓝色，短暂震动。
- 撤销、清空明确；辅助技术诊断默认隐藏。
- 3D 线条要明显，宜具备笔刷尺寸控制。
- 功能改动后应检查版本号与界面标题一致；打包完成才提供可验证 APK。
- 对实际测试范围透明：区分代码写入、GitHub CI 构建成功、用户手机实测成功。

## 下一步优先级
1. 确认最新版构建成功，现场测试底部按钮完整可见及按压反馈。
2. 校准手机顶部中央实际笔尖位置；提供便于标定的 UI。
3. 验证：绘画时线条连贯，停笔后稳定，移动视角重访位置不明显漂移；不要将单次 TRACKING 当作稳定性保证。
4. 优化视觉（高对比颜色、粗细、笔尖指示、相机清晰度与性能权衡）。
5. 空间作品保存与恢复（当前不应承诺已有可靠持久化）。
6. 以后进入虚拟三维世界和 VR 扩展。

## 开发及故障笔记
- 上一次曾遇到不同 CI debug 签名导致 APK 无法覆盖安装；已将稳定签名接入 CI，日志曾显示 Stable signing enabled。
- GitHub Pages 与原生 APK 是两个独立运行路径。
- 长聊天可能无法完整恢复；任何新的重大功能需求、版本发布、实机 BUG、解决方案都更新本文档。
