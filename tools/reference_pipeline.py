#!/usr/bin/env python3
"""Reference document-scan pipeline (detect -> warp -> enhance).

This is the SPEC the Android `core/cv/DocumentCv.kt` mirrors call-for-call.
OpenCV-Python and OpenCV-Android are the same C++ library, so validating the
math here validates the Kotlin. Run with --demo to self-check on a synthetic
skewed document (ponytail: one runnable check behind the logic).

    python3 reference_pipeline.py --demo        # generates fixture + asserts
    python3 reference_pipeline.py photo.jpg out/ # runs on a real photo
"""
import sys
import numpy as np
import cv2


# --- geometry: order 4 corners TL, TR, BR, BL (pure, mirrors Geometry.kt) ---
def order_corners(pts):
    pts = np.asarray(pts, dtype=np.float32).reshape(4, 2)
    s = pts.sum(axis=1)
    d = np.diff(pts, axis=1).ravel()
    return np.array([
        pts[np.argmin(s)],  # TL = smallest x+y
        pts[np.argmin(d)],  # TR = smallest y-x
        pts[np.argmax(s)],  # BR = largest x+y
        pts[np.argmax(d)],  # BL = largest y-x
    ], dtype=np.float32)


# --- detect: largest 4-point convex contour (mirrors DocumentCv.detect) ---
def detect_document(bgr):
    h, w = bgr.shape[:2]
    scale = 500.0 / max(h, w)
    small = cv2.resize(bgr, (int(w * scale), int(h * scale)))
    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(gray, 75, 200)
    edges = cv2.dilate(edges, np.ones((3, 3), np.uint8), iterations=1)
    cnts, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    cnts = sorted(cnts, key=cv2.contourArea, reverse=True)[:5]
    for c in cnts:
        peri = cv2.arcLength(c, True)
        approx = cv2.approxPolyDP(c, 0.02 * peri, True)
        if len(approx) == 4 and cv2.contourArea(approx) > 0.1 * small.shape[0] * small.shape[1]:
            return order_corners(approx.reshape(4, 2) / scale)  # back to full-res coords
    # fallback: whole frame
    return order_corners([[0, 0], [w, 0], [w, h], [0, h]])


# --- warp: perspective-correct to a flat rectangle (mirrors DocumentCv.warp) ---
def warp(bgr, corners):
    tl, tr, br, bl = corners
    wa = np.linalg.norm(br - bl)
    wb = np.linalg.norm(tr - tl)
    ha = np.linalg.norm(tr - br)
    hb = np.linalg.norm(tl - bl)
    W = int(max(wa, wb))
    H = int(max(ha, hb))
    dst = np.array([[0, 0], [W - 1, 0], [W - 1, H - 1], [0, H - 1]], dtype=np.float32)
    M = cv2.getPerspectiveTransform(corners, dst)
    return cv2.warpPerspective(bgr, M, (W, H))


# --- enhance: the 4 filters CamScanner also ships (mirrors DocumentCv.enhance) ---
def enhance(bgr, mode):
    if mode == "original":
        return bgr
    if mode == "gray":
        return cv2.cvtColor(cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY), cv2.COLOR_GRAY2BGR)
    if mode == "bw":
        g = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        bw = cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                   cv2.THRESH_BINARY, 15, 10)
        return cv2.cvtColor(bw, cv2.COLOR_GRAY2BGR)
    if mode == "magic":  # white-balance + CLAHE on L (LAB)
        lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        l = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(l)
        out = cv2.cvtColor(cv2.merge([l, a, b]), cv2.COLOR_LAB2BGR)
        return cv2.convertScaleAbs(out, alpha=1.15, beta=10)  # contrast+brightness
    raise ValueError(mode)


def shadow_removed(bgr):  # free in OpenScan; CamScanner paywalls it
    rgb_planes = cv2.split(bgr)
    out = []
    for p in rgb_planes:
        dil = cv2.dilate(p, np.ones((7, 7), np.uint8))
        bg = cv2.medianBlur(dil, 21)
        diff = 255 - cv2.absdiff(p, bg)
        out.append(cv2.normalize(diff, None, 0, 255, cv2.NORM_MINMAX))
    return cv2.merge(out)


def _make_fixture():
    """White document, perspective-skewed, on a dark textured background."""
    bg = np.random.randint(20, 60, (900, 700, 3), np.uint8)
    doc = np.full((400, 300, 3), 245, np.uint8)
    cv2.putText(doc, "INVOICE", (40, 80), cv2.FONT_HERSHEY_SIMPLEX, 1.2, (30, 30, 30), 3)
    cv2.rectangle(doc, (30, 120), (270, 360), (90, 90, 90), 2)
    src = np.float32([[0, 0], [300, 0], [300, 400], [0, 400]])
    dst = np.float32([[180, 120], [560, 90], [600, 700], [120, 760]])  # skew
    M = cv2.getPerspectiveTransform(src, dst)
    warped = cv2.warpPerspective(doc, M, (700, 900), borderMode=cv2.BORDER_TRANSPARENT, dst=bg.copy())
    return warped, dst


def demo():
    img, true_corners = _make_fixture()
    corners = detect_document(img)
    # assert detected corners land near the true skew corners (within 25 px)
    true_ordered = order_corners(true_corners)
    err = np.linalg.norm(corners - true_ordered, axis=1).max()
    print(f"detect: max corner error = {err:.1f}px")
    assert err < 25, f"detection off by {err:.1f}px"
    flat = warp(img, corners)
    print(f"warp: {img.shape[:2]} -> {flat.shape[:2]} (flat rectangle)")
    assert 1.1 < flat.shape[0] / flat.shape[1] < 1.6, "aspect ratio wrong after warp"
    for mode in ("original", "gray", "bw", "magic"):
        out = enhance(flat, mode)
        assert out.shape == flat.shape, f"{mode} changed shape"
        print(f"enhance[{mode}]: ok {out.shape}")
    print("ALL CHECKS PASSED")
    # also dump proof images if an out dir was given
    if len(sys.argv) > 2:
        import os
        d = sys.argv[2]; os.makedirs(d, exist_ok=True)
        cv2.imwrite(f"{d}/00_input.png", img)
        vis = img.copy()
        cv2.polylines(vis, [corners.astype(int)], True, (0, 255, 0), 3)
        cv2.imwrite(f"{d}/01_detected.png", vis)
        cv2.imwrite(f"{d}/02_warped.png", flat)
        for m in ("gray", "bw", "magic"):
            cv2.imwrite(f"{d}/03_{m}.png", enhance(flat, m))
        cv2.imwrite(f"{d}/04_shadow_removed.png", shadow_removed(flat))
        print(f"proof images -> {d}/")


def run(path, outdir):
    import os
    img = cv2.imread(path)
    if img is None:
        sys.exit(f"cannot read {path}")
    corners = detect_document(img)
    flat = warp(img, corners)
    os.makedirs(outdir, exist_ok=True)
    cv2.imwrite(f"{outdir}/warped.png", flat)
    for m in ("gray", "bw", "magic"):
        cv2.imwrite(f"{outdir}/{m}.png", enhance(flat, m))
    print(f"done -> {outdir}/")


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "--demo":
        demo()
    elif len(sys.argv) == 3:
        run(sys.argv[1], sys.argv[2])
    else:
        print(__doc__)
