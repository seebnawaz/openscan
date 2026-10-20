# OpenScan — through M3

Free, no-watermark document scanner for Android. Clean-room build (see
`../PRD.md`, `../M1_ARCHITECTURE.md`).

- **M2** — CV core: pick image → detect → perspective-warp → enhance filter.
- **M3** — CameraX capture with a live edge overlay, multi-page document, and
  no-watermark PDF export. Full flow: **camera (live outline) → capture → review +
  filter → add page → … → export PDF.**

## What's here

```
app/src/main/java/com/openscan/
  MainActivity.kt            OpenCV init + Compose host
  cv/Geometry.kt             pure corner math (TL/TR/BR/BL, output size) — JVM-testable
  cv/DocumentCv.kt           core/cv: detect() · warp() · enhance() + Bitmap<->Mat
  capture/CameraScreen.kt    CameraX preview + shutter + live detected-edge overlay
  capture/EdgeAnalyzer.kt    per-frame detection (RGBA analysis, stride-safe)
  document/ScanDocument.kt   feature/document: in-memory multi-page model + reorder
  export/PageLayout.kt       pure A4 letterbox math — JVM-testable
  export/PdfExporter.kt      feature/export: multi-page PDF, NO WATERMARK
  ui/ScanScreen.kt           routes camera → review → pages
  ui/ScanViewModel.kt        drives the whole flow off the main thread
app/src/test/java/com/openscan/
  GeometryTest.kt            corner ordering / output size
  PageLayoutTest.kt          PDF fit math
tools/reference_pipeline.py  the SPEC DocumentCv.kt mirrors + a runnable self-check
tools/proof/                 validation images + scan_2page.pdf (see below)
```

## How it was verified (without a device)

OpenCV-Android and OpenCV-Python are the **same C++ library**, so the algorithm was
validated by running the identical pipeline on a real (synthetic, skewed) document:

```
cd tools
python3 reference_pipeline.py --demo proof    # requires: pip install opencv-python-headless
```

Result on the skewed-invoice fixture:
- **detect**: max corner error **3.8 px** (asserts < 25)
- **warp**: 900×700 skewed → 643×484 flat rectangle, aspect sane
- **enhance**: all 4 filters (original/gray/bw/magic) + shadow-removal produce correct output

See `tools/proof/`: `01_detected.png` (green outline on the skewed page),
`02_warped.png` (flattened), `03_bw/gray/magic.png`, `04_shadow_removed.png`.

`DocumentCv.kt` is a call-for-call transcription of that validated Python. The pure-logic
parts — `Geometry.kt` (corner math) and `PageLayout.kt` (PDF fit) — have JVM unit tests
whose vectors were cross-checked against the same math.

**M3 PDF export** was verified the same way: `tools/proof/scan_2page.pdf` is a real,
valid 2-page A4 PDF (letterboxed, white background, **no watermark**) built with the
identical fit math `PdfExporter` uses — `file` reports "PDF document, version 1.4, 2 pages".

## Building the APK

No Gradle wrapper is committed (its binary jar can't be generated here). Either:

- **Android Studio** (easiest): `File → Open` this `OpenScan/` folder. Studio adds the
  wrapper, downloads the SDK, and Run ▶ installs to a device/emulator.
- **CLI**: with Android SDK + Gradle 8.9+ installed and `ANDROID_HOME` set:
  ```
  gradle wrapper --gradle-version 8.9
  ./gradlew :app:assembleDebug      # APK in app/build/outputs/apk/debug/
  ./gradlew test                    # runs GeometryTest
  ```

Dependencies resolve from Maven Central / Google — notably `org.opencv:opencv:4.11.0`
(bundles native `.so` for all ABIs; `OpenCVLoader.initLocal()` loads them, no separate
SDK import).

## Next (M3)

CameraX capture with live edge overlay, multi-page document model, no-watermark PDF
export. Then OCR (M4) — engine decision (Tesseract vs ML Kit) gated on the F-Droid call.
