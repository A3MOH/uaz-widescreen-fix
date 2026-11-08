
regc(2, "COMBINED_XFORM")
regc(6, "MODEL_XFORM")

regc(12, "FOG_PARAMS")

regc(13, "CAMERA_POS_MS")
regcn(14, "DIRECTIONAL_DIR_MS", 0)

regc(20, "TEXTURE2_XFORM")
regc(24, "TEXTURE3_XFORM")

regc4f(30, 0.0, 0.0, 0.0, %sunvisibility)
regc4f(31, %wetness, 0.0, 0.0, 0.0)
// ------------ c40 - c70 reserved
!include("vs_spot_consts.inc")

vshader("

#define point		v0
#define point1		v4
#define normal		v1
#define tangent		v2
#define tcoord		v3
#define weight		v5

#define fogp	c12
#define ldir	c14
#define cpos	c13

	vs_1_1

#include    <cardef.h>
#include    <fog.inc>

	dcl_position0	point
	dcl_position1	point1
	dcl_normal		normal
	dcl_tangent		tangent
	dcl_texcoord	tcoord
	dcl_blendweight weight

	def c0, SPECULARPOW, 0, 0, 0
	def	c1, 1, 0, SCRATCHTHRESHOLD, FRESNELSCALE
	def	c32, 0.6, 0.6, 0.08, 0.6
	def	c33, 0.15, 0.35, 0.0, 0.0

	; morphing
	mov r1, point
	sub  r0, point1, r1
	mad  r0, weight.x, r0, r1
	; output position
	m4x4	oPos, r0, c2
	; output DamageLevel
	sge oT1.x, weight.x, c1.z
    ; E in model space
	add r2, cpos, -point

  	; fresnel
    dp3 r6.w, r2, r2
    rsq r6.w, r6.w
    mul r6.xyz, r2, r6.w

	dp3	r6.w, r6, normal
	mad r6.w, r6.w, -r6.w, c1.x  // -dot(E, N)*dot(E, N)+1 = 1- pow(dot(E, N),2)
	mul	r6.w, r6.w, r6.w		//f^2
	; output fresnel
	mul oD1.w, r6.w, c1.w

	; wet terms: x beads, y water reflection, z paint darken, w dirt darken
	mad r7.x, r6.w, c32.w, c32.z
	mul oT5.y, r7.x, c31.x
	mov r8, c31.x
	mul oT5.zw, c33.xxxy, r8.x
	dp3 r7.y, normal, c7
	mad r7.y, r7.y, c32.x, c32.y
	max r7.y, r7.y, c1.y
	min r7.y, r7.y, c1.x
	mul oT5.x, r7.y, c31.x

	; R = reflect E
    dp3 r5.w, r2, normal
    add r6.w, r5.w, r5.w
    mad r6.xyz, normal, r6.w, -r2 // dot(E, N)*2 *N - E

    ; output cubemap
    m3x3	oT4, -r6, c6

    ; normalize E
    dp3 r6.w, r6, r6
    rsq r6.w, r6.w
    mul r6.xyz, r6, r6.w

	dp3		r4.x, normal, ldir	    ; diffuse N*L
	dp3		r4.y, ldir, r6        ; specular L*R
	mov		r4.w, c0.x              ; specular pow
	lit		r3, r4

    ; output diffuse
	mov		oD0.w, r3.y   //  sun
    ; output specular
	mul		oD1.xyz, r3.z, c30.w	//sunvisibility

;------------------------------------------
// spot lights
// used r0 - r2
#if VS_SPOTLIGHTS
#include <vs_spot_func.inc>
#endif
;----------------------------
	; output texcoords
	mov	    oT0, tcoord	       // color

	; terrain lightmap texgen (from model space)
	dp4	    oT2.x, point, c20
	dp4	    oT2.y, point, c21

	; clouds texgen (from model space)
	dp4 	oT3.x, point, c24
	dp4	    oT3.y, point, c25

	; calc fog
   	dp4	r0.x, point, c4
   	FOG(r0.x, fogp, r10)

")

