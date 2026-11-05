regc(0, "COMBINED_XFORM")
regc(4, "MODEL_XFORM")
regc(8, "FOG_PARAMS")
regc4f(9, %detailsize, %terrain_inv_x, 0.0, 0.0)
regc(10, "CAMERA_POS_MS")
regcn(11, "DIRECTIONAL_DIR_MS", 0)
regc(12, "TEXTURE4_XFORM")

vshader("

#include <fog.inc>

	vs_1_1

	def		c20, 0.5, 0.5, 0.5, 1.0
	def		c21, -1.0, 1.0, -1.0, 0.0
	def		c22, 0.98, 0.02, 2.0, 0.0
	def		c23, 28.5, -27.5, 0.0, 0.0		; flat = (Ny - 0.965) / 0.035

	dcl_position	v0
	dcl_normal		v1
	dcl_texcoord	v2

	m4x4	r0, v0, c0
	mov		oPos, r0
	FOG(r0.z, c8, r10)

	; world position
	m4x3	r7.xyz, v0, c4
	mov		r7.w, c20.w

	mul		oT0.xy, r7.xz, c9.y			;terrain colormap / mask / puddles
	mul		oT1.xy, r7.xz, c9.x			;terrain details
	dp4		oT2.x, r7, c12				;clouds
	dp4		oT2.y, r7, c13				;clouds
	mov		oT3.xy, v2					;road texture

	; E normalized (model space)
	add		r1.xyz, c10, -v0
	dp3		r1.w, r1, r1
	rsq		r1.w, r1.w
	mul		r1.xyz, r1, r1.w

	; normal in world space, for flatness
	m3x3	r8.xyz, v1, c4
	dp3		r8.w, r8, r8
	rsq		r8.w, r8.w
	mul		r8.xyz, r8, r8.w

	; flat water reflection: model up -> world up assumed
	mul		oT4.xyz, r1, c21
	max		r2.x, r1.y, c21.w
	sub		r2.x, c20.w, r2.x
	mul		r2.y, r2.x, r2.x
	mul		r2.y, r2.y, r2.y
	mul		r2.x, r2.y, r2.x
	mad		oT4.w, r2.x, c22.x, c22.y

	; ground reflection
	dp3		r3.w, v1, r1
	mul		r4.x, r3.w, c22.z
	mad		r9.xyz, v1, r4.x, -r1
	mov		oT5.xyz, r9
	max		r2.x, r3.w, c21.w
	sub		r2.x, c20.w, r2.x
	mul		r2.y, r2.x, r2.x
	mul		r2.y, r2.y, r2.y
	mul		r2.x, r2.y, r2.x
	mad		oT5.w, r2.x, c22.x, c22.y

	; broad road specular (replaces fixed-function, pow 4)
	dp3		r5.x, r9, c11
	max		r5.x, r5.x, c21.w
	mul		r5.x, r5.x, r5.x
	mul		r5.x, r5.x, r5.x

	; sun direction packed 0..1, flatness in alpha
	mov		r6, c20
	mad		oD0.xyz, c11, r6, r6
	mov		r6, c23
	mad		oD0.w, r8.y, r6.x, r6.y
	mov		oD1, r5.x

")
