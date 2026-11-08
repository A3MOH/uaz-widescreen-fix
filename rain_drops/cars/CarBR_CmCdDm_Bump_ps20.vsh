
regc(0, "COMBINED_XFORM")
regc(4, "MODEL_XFORM")

regc(8, "TEXTURE2_XFORM")
regc(10, "TEXTURE3_XFORM")

regc(12, "FOG_PARAMS")

regcn(13, "DIRECTIONAL_DIR_MS", 0)
regc(14, "CAMERA_POS_MS")
regc4f(21, %wetness, 0.0, 0.0, 0.0)

// ------------ c40 - c60 reserved
!include("vs_spot_consts.inc")

vshader("

#define point		v0
#define point1		v4
#define normal		v1
#define tangent		v2
#define tcoord		v3
#define weight		v5

#define fogp	c12
#define ldir	c13
#define cpos	c14

	vs_1_1

#include    <cardef.h>
#include    <fog.inc>

	dcl_position0	point
	dcl_position1	point1
	dcl_normal		normal
	dcl_tangent		tangent
	dcl_texcoord	tcoord
	dcl_blendweight weight

	def	c20, 1, 0, SCRATCHTHRESHOLD, FRESNELSCALE
	def	c22, 0.6, 0.6, 0.08, 0.6
	def	c23, 0.15, 0.35, 0.0, 0.0

	; morphing
	mov r1, point
	sub  r0, point1, r1
	mad  r0, weight.x, r0, r1
	; output position
	m4x4	oPos, r0, c0
	; output texcoords
	mov	oT0, tcoord	       // color
	; output DamageLevel
	sge oT4.y, weight.x, c20.z

	// binormal = tangent x normal
    mov r0.xyz, normal
    mul r1.xyz, r0.yzxw, tangent.zxyw
    mad r0.xyz, r0.zxyw, tangent.yzxw, -r1

	; r4 L tangent space
    dp3 r4.x, tangent, ldir
    dp3 r4.y, r0, ldir
    dp3 r4.z, normal, ldir

	; normalize L
    dp3 r4.w, r4, r4
    rsq r4.w, r4.w
    mul oT1.xyz, r4, r4.w

	; E in model space
	add r5, cpos, -point
  	; fresnel
    dp3 r6.w, r5, r5
    rsq r6.w, r6.w
    mul r6.xyz, r5, r6.w

	dp3	r6.w, r6, normal
	mad r6.w, r6.w, -r6.w, c20.x  // -dot(E, N)*dot(E, N)+1 = 1- pow(dot(E, N),2)
	mul	r6.w, r6.w, r6.w		//f^2
	; output fresnel
	mul oT4.x, r6.w, c20.w

	; wet terms: x beads, y water reflection, z paint darken, w dirt darken
	mad r7.x, r6.w, c22.w, c22.z
	mul oT7.y, r7.x, c21.x
	mov r8, c21.x
	mul oT7.zw, c23.xxxy, r8.x
	dp3 r7.y, normal, c5
	mad r7.y, r7.y, c22.x, c22.y
	max r7.y, r7.y, c20.y
	min r7.y, r7.y, c20.x
	mul oT7.x, r7.y, c21.x

	; reflect E
    dp3 r5.w, r5, normal
    add r6.w, r5.w, r5.w
    mad r6.xyz, normal, r6.w, -r5 // dot(E, N)*2 *N - E

	; output R in world space	(do cubemapy - nie normalizowac!!!!)
    dp3 oT3.x, -r6, c4
    dp3 oT3.y, -r6, c5
    dp3 oT3.z, -r6, c6

	; normalize R
    dp3 r6.w, r6, r6
    rsq r6.w, r6.w
    mul r6.xyz, r6, r6.w

	; output R in tangent space
    dp3 oT2.x, tangent, r6
    dp3 oT2.y, r0, r6
    dp3 oT2.z, normal, r6

;------------------------------------------
// spot light
// used r0 - r2
#if VS_SPOTLIGHTS
#include <vs_spot_func.inc>
#endif
;----------------------------

	; calc fog
   	dp4	r0.x, point, c2
   	FOG(r0.x, fogp, r10)

	; clouds texgen (from model space)
	dp4	oT5.x, point, c8
	dp4	oT5.y, point, c9

	; terrain lightmap texgen (from model space)
	dp4	oT6.x, point, c10
	dp4	oT6.y, point, c11

")

