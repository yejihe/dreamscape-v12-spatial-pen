# 🌌 Dreamscape V12 · Spatial Pen

独立于 V11 手势空气绘画系列的 V12 手机空间画笔技术分支。

## 当前版本
V12.0 — WebXR `immersive-ar` 兼容性检测 + 真实六自由度 XR viewer 位姿验证。不含三维绘画，也不模拟 AR 深度。

## 使用
通过 HTTPS 打开部署后的 `index.html`，检查兼容性。在浏览器报告 immersive-ar 可用时尝试启动真实 AR 测试。

## 注意
手机被 ARCore 官方支持，不等于 Chrome WebXR AR 必然可运行。XR viewer 姿态也不等于手机顶部中央笔尖位置；需要后续单独标定。尚未在 vivo S30 上实测。

## 部署
此仓库连接全新的 Netlify 站点，不要修改或重新关联旧 V11 网站。
