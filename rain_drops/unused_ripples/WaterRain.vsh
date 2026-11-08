regc(0, "COMBINED_XFORM")
regc(4, "CAMERA_POS_WS")
regc(8, "TEXTURE0_XFORM")
regc(12, "TEXTURE1_XFORM")
regc(14, "TEXTURE2_XFORM")
regc(16, "TEXTURE3_XFORM")
regc(18, "FOG_PARAMS")


vshader("

#include <fog.inc>

    vs_1_1

	dcl_position	v0
	dcl_texcoord0   v1

    def c30, 0.3, 0.5, 0.00003, 0.0004
    def c31, 0.00001, 1, 0, 0
    def c32, 0.0067, 0.0049, 0.37, 0.61		; ripple tiling (1.5 m, 2 m) and layer offset

    m4x4    r1, v0, c0
    mov     oPos, r1
	FOG(r1.z, c18, r10)

    dp4     oT0.x, v0, c8
    dp4     oT0.y, v0, c9
    dp4     oT0.z, v0, c10
    dp4     oT0.w, v0, c10

    ;noise 0 mapping
    dp4     oT1.x, v0, c12
    dp4     oT1.y, v0, c13

    ;noise 1 mapping
    dp4     oT2.x, v0, c14
    dp4     oT2.y, v0, c15

    ;clouds mapping
    dp4     oT3.x, v0, c16
    dp4     oT3.y, v0, c17

    ;whole terrain mapping
    mul     oT4.xy, v0.xz, c31.xx

    ;pseudo specular mapping
    mov     r0.xy, v0.xz
    mov     r1.x, c31.y
    mul     r1.x, -c4.y, r1.x
    add     r0.x, r0.x, r1.x
    add     r0.xy, -c4.xz, r0.xy
    mad     oT5.xy, r0.xy, c30.zw, c30.xy

    ;rain ripples, two layers
    mul     oT6.xy, v0.xz, c32.xx
    mad     oT6.zw, v0.zx, c32.yy, c32.zw

")
