"""Shared pose helpers for the offline curation pipeline.

normalize() must stay identical to BodyPose.fromImageLandmarks() in the app
(app/src/main/java/com/s23cp5054/posewithme/pose/model/BodyPose.java).
"""

import math

from PIL import Image, ImageDraw

COUNT = 33
NOSE = 0
LEFT_EAR, RIGHT_EAR = 7, 8
LEFT_SHOULDER, RIGHT_SHOULDER = 11, 12
LEFT_ELBOW, RIGHT_ELBOW = 13, 14
LEFT_WRIST, RIGHT_WRIST = 15, 16
LEFT_PINKY, RIGHT_PINKY = 17, 18
LEFT_INDEX, RIGHT_INDEX = 19, 20
LEFT_HIP, RIGHT_HIP = 23, 24
LEFT_KNEE, RIGHT_KNEE = 25, 26
LEFT_ANKLE, RIGHT_ANKLE = 27, 28
LEFT_HEEL, RIGHT_HEEL = 29, 30
LEFT_FOOT, RIGHT_FOOT = 31, 32

MIN_FRAME_VISIBILITY = 0.5


def normalize(landmarks, width, height):
    """MediaPipe image landmarks -> body frame (see BodyPose.java), or None.

    landmarks: list of 33 objects with .x .y .z .visibility (normalized to the image).
    Returns a list of 33 [x, y, z, visibility] rows.
    """
    frame = (LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP)
    if any((landmarks[i].visibility or 0.0) < MIN_FRAME_VISIBILITY for i in frame):
        return None
    hip_x = (landmarks[LEFT_HIP].x + landmarks[RIGHT_HIP].x) / 2 * width
    hip_y = (landmarks[LEFT_HIP].y + landmarks[RIGHT_HIP].y) / 2 * height
    hip_z = (landmarks[LEFT_HIP].z + landmarks[RIGHT_HIP].z) / 2 * width
    sh_x = (landmarks[LEFT_SHOULDER].x + landmarks[RIGHT_SHOULDER].x) / 2 * width
    sh_y = (landmarks[LEFT_SHOULDER].y + landmarks[RIGHT_SHOULDER].y) / 2 * height
    torso = math.hypot(sh_x - hip_x, sh_y - hip_y)
    if torso < 1e-3:
        return None
    rows = []
    for lm in landmarks:
        rows.append([
            round((lm.x * width - hip_x) / torso, 4),
            round((lm.y * height - hip_y) / torso, 4),
            round((lm.z * width - hip_z) / torso, 4),
            round(lm.visibility or 0.0, 4),
        ])
    return rows


# Silhouette proportions in torso units. Mirrors SilhouetteRenderer.java.
SHOULDER_PUSH = 0.03
HIP_PUSH = 0.07
WAIST_T = 0.55
WAIST_PULL = 0.12
TORSO_CORNER = 0.07
LIMBS = [
    ("left_upper_arm", LEFT_SHOULDER, LEFT_ELBOW, 0.095, 0.08),
    ("right_upper_arm", RIGHT_SHOULDER, RIGHT_ELBOW, 0.095, 0.08),
    ("left_forearm", LEFT_ELBOW, LEFT_WRIST, 0.085, 0.06),
    ("right_forearm", RIGHT_ELBOW, RIGHT_WRIST, 0.085, 0.06),
    ("left_thigh", LEFT_HIP, LEFT_KNEE, 0.16, 0.11),
    ("right_thigh", RIGHT_HIP, RIGHT_KNEE, 0.16, 0.11),
    ("left_shin", LEFT_KNEE, LEFT_ANKLE, 0.10, 0.065),
    ("right_shin", RIGHT_KNEE, RIGHT_ANKLE, 0.10, 0.065),
    ("left_foot", LEFT_HEEL, LEFT_FOOT, 0.06, 0.045),
    ("right_foot", RIGHT_HEEL, RIGHT_FOOT, 0.06, 0.045),
]


def _capsule(draw, p1, p2, r1, r2, fill):
    (x1, y1), (x2, y2) = p1, p2
    dx, dy = x2 - x1, y2 - y1
    length = math.hypot(dx, dy) or 1e-6
    nx, ny = -dy / length, dx / length
    draw.polygon([(x1 + nx * r1, y1 + ny * r1), (x2 + nx * r2, y2 + ny * r2),
                  (x2 - nx * r2, y2 - ny * r2), (x1 - nx * r1, y1 - ny * r1)], fill=fill)
    draw.ellipse([x1 - r1, y1 - r1, x1 + r1, y1 + r1], fill=fill)
    draw.ellipse([x2 - r2, y2 - r2, x2 + r2, y2 + r2], fill=fill)


def render_silhouette(rows, size=(256, 320), min_visibility=0.3,
                      near=(196, 178, 255), far=(132, 112, 214), bg=(20, 20, 28)):
    """Draws a body-frame pose as a mannequin silhouette, fitted into the image."""
    w, h = size
    img = Image.new("RGB", size, bg)
    draw = ImageDraw.Draw(img)
    visible = [r for r in rows if r[3] >= min_visibility]
    xs = [r[0] for r in visible] + [-0.6, 0.6]
    ys = [r[1] for r in visible] + [-1.6, 0.3]
    min_x, max_x, min_y, max_y = min(xs) - 0.3, max(xs) + 0.3, min(ys) - 0.35, max(ys) + 0.2
    scale = min(w / (max_x - min_x), h / (max_y - min_y))
    off_x = (w - (max_x - min_x) * scale) / 2 - min_x * scale
    off_y = (h - (max_y - min_y) * scale) / 2 - min_y * scale

    def pt(i):
        return rows[i][0] * scale + off_x, rows[i][1] * scale + off_y

    def vis(*idx):
        return all(rows[i][3] >= min_visibility for i in idx)

    parts = []  # (depth, draw_fn)
    torso_z = 0.0
    for _, a, b, ra, rb in LIMBS:
        if vis(a, b):
            depth = (rows[a][2] + rows[b][2]) / 2
            color = far if depth > torso_z + 0.15 else near
            parts.append((depth, lambda a=a, b=b, ra=ra, rb=rb, c=color:
                          _capsule(draw, pt(a), pt(b), ra * scale, rb * scale, c)))
    for wrist, index, pinky in ((LEFT_WRIST, LEFT_INDEX, LEFT_PINKY),
                                (RIGHT_WRIST, RIGHT_INDEX, RIGHT_PINKY)):
        if vis(wrist, index, pinky):
            hx = (rows[index][0] + rows[pinky][0]) / 2 * scale + off_x
            hy = (rows[index][1] + rows[pinky][1]) / 2 * scale + off_y
            depth = rows[wrist][2]
            color = far if depth > torso_z + 0.15 else near
            parts.append((depth - 0.01, lambda w_=wrist, hx=hx, hy=hy, c=color:
                          _capsule(draw, pt(w_), (hx, hy), 0.065 * scale, 0.05 * scale, c)))

    def torso_fn():
        ls, rs, lh, rh = pt(LEFT_SHOULDER), pt(RIGHT_SHOULDER), pt(LEFT_HIP), pt(RIGHT_HIP)

        def push(p, q, amount):
            dx, dy = p[0] - q[0], p[1] - q[1]
            d = math.hypot(dx, dy) or 1e-6
            return p[0] + dx / d * amount * scale, p[1] + dy / d * amount * scale

        def waist(s, h, other_s, other_h):
            # 55% of the way down the side, pulled 12% of the body width towards the centre.
            wx, wy = s[0] + (h[0] - s[0]) * WAIST_T, s[1] + (h[1] - s[1]) * WAIST_T
            ox = other_s[0] + (other_h[0] - other_s[0]) * WAIST_T
            oy = other_s[1] + (other_h[1] - other_s[1]) * WAIST_T
            return wx + (ox - wx) * WAIST_PULL, wy + (oy - wy) * WAIST_PULL

        lso, rso = push(ls, rs, SHOULDER_PUSH), push(rs, ls, SHOULDER_PUSH)
        lho, rho = push(lh, rh, HIP_PUSH), push(rh, lh, HIP_PUSH)
        poly = [lso, rso, waist(rso, rho, lso, lho), rho, lho, waist(lso, lho, rso, rho)]
        draw.polygon(poly, fill=near)
        r = TORSO_CORNER * scale
        for x, y in poly:
            draw.ellipse([x - r, y - r, x + r, y + r], fill=near)
        sh = ((ls[0] + rs[0]) / 2, (ls[1] + rs[1]) / 2)
        head = head_center()
        _capsule(draw, sh, head, 0.10 * scale, 0.085 * scale, near)

    def head_center():
        if vis(LEFT_EAR, RIGHT_EAR):
            return ((rows[LEFT_EAR][0] + rows[RIGHT_EAR][0]) / 2 * scale + off_x,
                    (rows[LEFT_EAR][1] + rows[RIGHT_EAR][1]) / 2 * scale + off_y)
        return pt(NOSE)

    def head_fn():
        cx, cy = head_center()
        rx, ry = 0.18 * scale, 0.23 * scale
        draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=near)

    parts.append((torso_z, torso_fn))
    parts.append((torso_z - 0.05, head_fn))
    for _, fn in sorted(parts, key=lambda p: -p[0]):
        fn()
    return img


def mean_joint_distance(a, b, joints=None):
    """Average 2D distance between two body-frame poses over the given joints."""
    joints = joints or [11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28]
    return sum(math.hypot(a[i][0] - b[i][0], a[i][1] - b[i][1]) for i in joints) / len(joints)
