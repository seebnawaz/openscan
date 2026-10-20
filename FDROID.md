# F-Droid readiness

The distribution decision is **F-Droid** (fully-open), which sets the whole stack:
no Google Play Services, no proprietary SDKs, OCR via **Tesseract** (not ML Kit).
This doc is the compliance checklist and the one real build caveat.

## Compliance checklist

| F-Droid requirement | Status | Notes |
|--------------------|--------|-------|
| OSI/FSF free license | ✅ | `LICENSE` — Apache-2.0 (GPLv3 is an option if you prefer copyleft) |
| No proprietary dependencies | ✅ | Deps audited: AndroidX + CameraX + OpenCV only. `grep` for mlkit/firebase/gms/crashlytics → none |
| No Google ML Kit / Play Services | ✅ | OCR is Tesseract; capture is CameraX (camera2, not GMS) |
| No ads / tracking / analytics | ✅ | Nothing network-facing at all; app is 100% on-device |
| No non-free network services | ✅ | No backend. PDF/OCR all local |
| Builds from source | ⚠️ | **One caveat: OpenCV** — see below |
| Reproducible metadata | ✅ | `fastlane/metadata/android/en-US/` + `fdroid/metadata-recipe.yml` |
| Anti-features | none | no non-free deps, no tracking, no upstream non-free |

## The one caveat: OpenCV prebuilt binaries

`org.opencv:opencv:4.11.0` (Maven Central) bundles **prebuilt native `.so`** for each
ABI. F-Droid's scanner flags prebuilt binaries — everything must build from source.

**Resolution (standard F-Droid pattern):** build OpenCV from source as an F-Droid
*srclib* and depend on that module instead of the Maven artifact. The `fdroid/metadata-recipe.yml`
already declares `srclibs: [OpenCV@4.11.0]`; the build then compiles OpenCV's Android
SDK and the gradle module links the source-built AAR. Several F-Droid apps ship OpenCV
this exact way, so it's a solved problem, not a blocker.

- **For local dev / Play-Store / direct-APK builds:** keep the Maven `org.opencv:opencv`
  dep — it's the fastest path and the binaries are legitimate.
- **For the F-Droid build only:** swap to the OpenCV srclib. Gradle build flavors can
  keep both paths side by side (`fdroid` vs `standard` flavor) so dev stays fast.

Tesseract has no such issue: **tesseract4android** (`cz.adaptech.tesseract4android`)
builds Leptonica + Tesseract from source and is already accepted on F-Droid.

## OCR engine — decided

F-Droid rules out ML Kit, so OCR = **Tesseract via tesseract4android**. Encoded in the
architecture now as [`ocr/TextRecognizer.kt`](app/src/main/java/com/openscan/ocr/TextRecognizer.kt)
(the interface); the `TesseractRecognizer` implementation + language-data download is
M4. The interface is engine-agnostic, so a future Play-Store flavor could still add ML
Kit behind it without touching callers.

Language data (`eng.traineddata`, etc.) ships under the Apache-2.0 tessdata license —
bundle the languages you want, or fetch on first use from the official tessdata repo.

## What's left before an actual F-Droid submission

1. Public git repo with a tagged release (`v0.1-m2`) → fill `REPO_URL` in the recipe.
2. OpenCV srclib wired into the build (the caveat above).
3. App icon + at least one screenshot in `fastlane/metadata/android/en-US/images/`.
4. Build the APK once locally to confirm it assembles (needs Android SDK).
5. Submit a merge request to `fdroiddata` with `fdroid/metadata-recipe.yml`.

Items 1–3 are yours (repo, art); 2 and 4 I can do once we build the project.
