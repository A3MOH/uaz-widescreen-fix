regc(0, "COMBINED_XFORM")
regc(4, "TEXTURE4_XFORM")
regc(6, "TEXTURE5_XFORM")
regc(8, "FOG_PARAMS")

regc4f(10, %detailsize, %sidedetailsize, %detailsborder, %terrain_inv_x)
regc(11, "CAMERA_POS_MS")
regcn(12, "DIRECTIONAL_DIR_MS", 0)
regc4f(17, %shorelevel, %shorerange, %shorebias, 0.0)

vshader("

#include <fog.inc>

	vs_1_1

	def		c13, 0.5, 0.5, 0.5, 1.0
	def		c14, -1.0, 1.0, -1.0, 0.0
	def		c15, 0.98, 0.02, 2.0, 0.0
	def		c16, 28.5, -27.5, 0.0, 0.0		; flat = (Ny - 0.965) / 0.035

	dcl_position	v0
	dcl_normal      v1

    m4x4    r0, v0, c0
    mov    oPos, r0
	FOG(r0.z, c8, r10)

    mul     oT0.xy, v0.xz, c10.w		;planar mapping top - color map
    mul     oT1.xy, v0.xz, c10.x		;planar mapping top - details

	dp4		oT2.x, v0, c4				;clouds
	dp4		oT2.y, v0, c5				;clouds

	; waterdeep, shore height from puddle map B: H = W - R + 2R*B
	dp4		r7.x, v0, c6
	dp4		r7.y, v0, c7
	mov		r8.x, c6.y
	mov		r8.y, c7.y
	mov		r9, c17
	slt		r9.w, c14.w, r9.y			;on = range > 0
	mul		r8.xy, r8.xy, r9.w
	add		r9.x, r9.x, -r9.y
	add		r9.x, r9.x, r9.z
	add		r9.x, r9.x, -v0.y
	mad		oT3.xy, r8.xy, r9.x, r7.xy	;uv at W - R + bias
	add		r9.y, r9.y, r9.y
	mul		oT3.zw, r8.xyxy, r9.y		;uv step per unit B

    mul     oT4.xy, v0.xy, c10.y		;planar mapping front - crag detail
    mul     oT5.xy, v0.zy, c10.y		;planar mapping side - crag detail

    sge     oD0.xyz, v1.y, c10.z		;top detail mask
	max     r0, -v1, v1
	sge		oD0.w, r0.x, r0.z			;crag blend mask

	; E normalized
	add		r1.xyz, c11, -v0
	dp3		r1.w, r1, r1
	rsq		r1.w, r1.w
	mul		r1.xyz, r1, r1.w

	; flat water: R = (-Ex, Ey, -Ez), fresnel from Ey
	mul		oT6.xyz, r1, c14
	max		r2.x, r1.y, c14.w
	sub		r2.x, c13.w, r2.x
	mul		r2.y, r2.x, r2.x
	mul		r2.y, r2.y, r2.y
	mul		r2.x, r2.y, r2.x
	mad		oT6.w, r2.x, c15.x, c15.y

	; ground: R = 2(N.E)N - E, fresnel from N.E
	dp3		r3.w, v1, r1
	mul		r4.x, r3.w, c15.z
	mad		oT7.xyz, v1, r4.x, -r1
	max		r2.x, r3.w, c14.w
	sub		r2.x, c13.w, r2.x
	mul		r2.y, r2.x, r2.x
	mul		r2.y, r2.y, r2.y
	mul		r2.x, r2.y, r2.x
	mad		oT7.w, r2.x, c15.x, c15.y

	; sun direction packed 0..1, flatness in alpha
	mov		r5, c13
	mad		oD1.xyz, c12, r5, r5
	mov		r6, c16
	mad		oD1.w, v1.y, r6.x, r6.y

")
