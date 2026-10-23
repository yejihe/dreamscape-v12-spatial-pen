# Dreamscape Hand · Phase 2 — Geometry spike 0.1.0

**Isolation:** Experimental files only. This branch does not modify the original R10 HTML, vendored MediaPipe assets, Android MainActivity, or release workflow.

## Audited baseline (2026-10-23)
- hand-test.html: R10; rear/front camera selection, 21-point landmarks, mirrored preview coordinates, normalized thumb-index pinch hysteresis (on <0.42, off >0.65), 2D strokes, clear.
- .github/workflows/vendor-hand-tracking.yml: pins npm @mediapipe/tasks-vision@0.10.22-rc.20250304 and vendors runtime/model.
- vendor/mediapipe/: verified presence of vision_bundle.mjs, vision_wasm_internal.wasm, hand_landmarker.task. Other files need audit during migration.
- Android V12 v0.3.9: uses ARCore 1.56.0, separate phone-as-pen app; **do not edit**.

## Phase 2 milestone
**M2.1: Coordinate mathematics independent of detector.** FingertipProjection converts (u,v,known optical depth, calibrated intrinsics) to camera-space meters and camera-pose world-space meters. Does not estimate depth or run MediaPipe/ARCore. Five JVM checks pass locally with javac 21. No Android build or phone test claimed.

**M2.2: Native synchronized frame acquisition (not implemented).** In a separate Android application, use *one ARCore Session* and frame.acquireCameraImage() in YUV_420_888, immediately copy/close the image, capture frame timestamp, matching Camera pose/intrinsics; transform rotation/crop/mirror exactly once; run MediaPipe on an executor at <= 15fps. Maintain timestamp key between inference result and captured pose; discard stale tracking and frame mismatches.

**M2.3: Metric fingertip depth (not implemented).** Prefer ARCore Depth API only if device/session support verified; depth represents a surface and may not resolve thin moving fingers. Sample depth around fingertips with confidence checks, try conservative hand-size/landmark estimation as fallback (requires per-user calibration), label estimated depth and quantify error against measured 20cm movements. Never replace missing depth with a silently fixed plane.

## Coordinate contracts
- Utility expects **unmirrored, calibrated CPU-image pixels** and intrinsics in the **same pixel orientation/crop**; portrait hand landmarks cannot be inserted without transforming.
- Depth is **optical Z depth in meters**, not ray length.
- Camera coordinates +X right, +Y up, -Z forward; ARCore camera Pose quaternion [x,y,z,w] + translation meters.
- For each landmark result, use pose from its acquisition timestamp; **not current render pose**.
- For calibration: camera motion with stationary hand should not move the world fingertip; translation 0.20m forward should produce approx 0.20m world displacement with stationary camera. These are acceptance *tests*, not verified outcomes.

## Validation and gates
- Unit tests for camera projection, image y sign, pose transform, quaternion, and invalid depth.
- Real device: confirm front/rear handedness and frame coordinate mapping; measure camera intrinsics and tracking continuity, p50/p95 position jitter, latency, and 20cm displacement error.
- Preserve an immutable R10 snapshot or release tag before moving repositories. Migrating requires GitHub assets + deployment verification.
- No purchases before native 3D measurement proof.
