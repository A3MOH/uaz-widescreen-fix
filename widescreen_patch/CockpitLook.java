public class CockpitLook
{
	static final String SWITCH_DIR = UIAspect.SWITCH_DIR;
	static boolean s_bLookTurn = UIAspect.IniBool("cockpit", "look_turn", true);

	static float[] s_aLookCfg = ReadLookCfg();
	static float s_fLookYaw = 0.0f;
	static float s_fLookTime = -1.0f;

	static float[] ReadLookCfg()
	{
		float[] cfg = new float[] {
			UIAspect.IniFloat("cockpit", "look_turn_angle", 12.0f),
			UIAspect.IniFloat("cockpit", "look_turn_speed", 0.25f) };
		cfg[0] = Math.max(-60.0f, Math.min(60.0f, cfg[0]));
		cfg[1] = Math.max(0.0f, Math.min(5.0f, cfg[1]));
		return cfg;
	}

	public static float CabinLookYaw(float fYaw, CarCamera.InsideState state, CarCamera cam)
	{
		try
		{
			if (!s_bLookTurn || s_aLookCfg[0] == 0.0f || state instanceof CarCamera.MirrorCam || cam == null || cam.m_cObserved == null)
				return fYaw;
			float fTime = cam.ODE_GetCurrentTime();
			float fTarget = cam.m_cObserved.m_fCurrentFrontWheelTurn * s_aLookCfg[0];
			float dt = fTime - s_fLookTime;
			if (s_fLookTime < 0.0f || dt < 0.0f || dt > 1.0f || s_aLookCfg[1] <= 0.0f)
				s_fLookYaw = fTarget;
			else if (dt > 0.0f)
				s_fLookYaw += (fTarget - s_fLookYaw) * Math.min(1.0f, dt / s_aLookCfg[1]);
			s_fLookTime = fTime;
			return fYaw + s_fLookYaw;
		}
		catch (Throwable e)
		{
			return fYaw;
		}
	}
}
