regc(0, "COMBINED_XFORM")
regc(8, "TEXTURE0_XFORM")
regc(18, "FOG_PARAMS")

vshader("

#include <fog.inc>

    vs_1_1

	dcl_position	v0

    def c32, 0.0025, 0.0018, 0.37, 0.61		; ripple tiling (4 m, 5.5 m) and layer offset

    m4x4    r1, v0, c0
    mov     oPos, r1
	FOG(r1.z, c18, r10)

    ;projected reflection
    dp4     oT0.x, v0, c8
    dp4     oT0.y, v0, c9
    dp4     oT0.z, v0, c10
    dp4     oT0.w, v0, c10

    ;rain ripples, two layers
    mul     oT1.xy, v0.xz, c32.xx
    mad     oT1.zw, v0.zx, c32.yy, c32.zw

")
