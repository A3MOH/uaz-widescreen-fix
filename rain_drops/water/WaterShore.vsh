regc(0, "COMBINED_XFORM")
regc(4, "CAMERA_POS_WS")
regc(8, "TEXTURE0_XFORM")
regc(18, "FOG_PARAMS")
regc4f(20, %terrain_inv_x, %terrain_inv_z, 0.0, 0.0)

vshader("

#include <fog.inc>

	vs_1_1

	dcl_position	v0

	m4x4	r1, v0, c0
	mov		oPos, r1
	FOG(r1.z, c18, r10)

	; projected reflection
	dp4		oT0.x, v0, c8
	dp4		oT0.y, v0, c9
	dp4		oT0.z, v0, c10
	dp4		oT0.w, v0, c10

	; water mesh is built in world space
	mul		oT1.xy, v0.xz, c20.xy

	; to camera (not normalized, exact when interpolated)
	add		oT2.xyz, c4, -v0

")
