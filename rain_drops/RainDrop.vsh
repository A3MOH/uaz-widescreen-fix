regc(0, "COMBINED_XFORM")
regc(4, "MODEL_XFORM")
regc(12, "FOG_PARAMS")
regcn(13, "DIRECTIONAL_DIR_MS", 0)
regc(14, "CAMERA_POS_MS")

vshader("

#define point		v0
#define vcolor		v1
#define tcoord		v2

#define fogp	c12
#define ldir	c13
#define cpos	c14

	vs_1_1

#include    <fog.inc>

	dcl_position	point
	dcl_color		vcolor
	dcl_texcoord	tcoord

	; position
	m4x4	oPos, point, c0

	; mark color, atlas uv
	mov	oD0, vcolor
	mov	oT0, tcoord

	; eye vector in glass space (unnormalized)
	add	oT1.xyz, cpos, -point

	; sun direction in glass space
	mov	oT2.xyz, ldir

	; glass space -> world rotation rows
	mov	oT3.xyz, c4
	mov	oT4.xyz, c5
	mov	oT5.xyz, c6

	; fog
	dp4	r0.x, point, c2
	FOG(r0.x, fogp, r10)

")
