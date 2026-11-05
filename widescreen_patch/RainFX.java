public class RainFX
{
	static final String SWITCH_DIR = UIAspect.SWITCH_DIR;
	static boolean s_bGlassDrops = !new java.io.File(SWITCH_DIR + "no_glass_drops.txt").exists();
	static boolean s_bWetness = !new java.io.File(SWITCH_DIR + "no_wetness.txt").exists();

	static final float DROP_ACCEL_SMOOTH = 0.15f;
	static final float DROP_JOLT_DECAY = 0.25f;
	static final float DROP_JOLT_MIN = 6.0f;
	static final float DROP_EXTRA_MAX = 30.0f;
	static float[] s_aDropCfg = ReadDropCfg();
	static java.util.WeakHashMap s_mDropState = new java.util.WeakHashMap();

	static float[] ReadDropCfg()
	{
		float[] cfg = new float[] { 1.0f, 0.5f };
		try
		{
			java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(SWITCH_DIR + "glass_drops.txt"));
			String s = r.readLine();
			r.close();
			if (s != null)
			{
				java.util.StringTokenizer t = new java.util.StringTokenizer(s.trim());
				for (int i = 0; i < cfg.length && t.hasMoreTokens(); i++)
				{
					float v = Float.parseFloat(t.nextToken());
					if (v >= 0.0f && v <= 20.0f)
						cfg[i] = v;
				}
			}
		}
		catch (Throwable e)
		{
		}
		return cfg;
	}

	static final float WET_RISE_BASE = 0.02f;
	static final float WET_RISE_RAIN = 0.05f;
	static final float WET_DRY_TIME = 300.0f;
	static final float WET_RAIN_MUL = 1.5f;
	static float[] s_aWetCfg = ReadWetCfg();
	static java.lang.ref.WeakReference s_rWetWeather = null;
	static float s_fWetness = 0.0f;
	static float s_fWetTime = 0.0f;
	static float s_fWetStart = 0.0f;
	static boolean s_bWetLog = new java.io.File(SWITCH_DIR + "wetness_log.txt").exists();
	static float s_fWetLogTime = -1000.0f;
	static int s_iWetLogLines = 0;
	static final float WET_LOG_STEP = 5.0f;
	static final int WET_LOG_MAX = 200;
	static final float WET_SNAP_TIME = 10.0f;
	static final float WET_RAIN_DEFAULT = 0.6f;
	static float s_fWetSent = -1.0f;

	static float[] ReadWetCfg()
	{
		float[] cfg = new float[] { 1.0f, -1.0f };
		try
		{
			java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(SWITCH_DIR + "wetness.txt"));
			String s = r.readLine();
			r.close();
			if (s != null)
			{
				java.util.StringTokenizer t = new java.util.StringTokenizer(s.trim());
				for (int i = 0; i < cfg.length && t.hasMoreTokens(); i++)
					cfg[i] = Float.parseFloat(t.nextToken());
			}
		}
		catch (Throwable e)
		{
		}
		cfg[0] = Math.max(0.0f, Math.min(2.0f, cfg[0]));
		cfg[1] = Math.min(1.0f, cfg[1]);
		return cfg;
	}

	public static void WeatherFrame(Weather w)
	{
		if (!s_bWetness)
			return;
		try
		{
			float fTime = w.GetTime();
			float fRain = w.GetRainPercentage();
			TrackModule tm = w.m_Module;
			if (tm != null && tm.m_bRainEnabled)
				fRain = Math.max(fRain, tm.m_fRainDensity > 0.0f ? Math.min(1.0f, tm.m_fRainDensity / 100.0f) : WET_RAIN_DEFAULT);
			if (w.IsSnowyWeather())
				fRain = 0.0f;
			float fTarget = Math.min(1.0f, fRain * WET_RAIN_MUL);
			Object prev = s_rWetWeather == null ? null : s_rWetWeather.get();
			if (prev != w || fTime < s_fWetTime)
			{
				s_rWetWeather = new java.lang.ref.WeakReference(w);
				s_fWetness = fTarget;
				s_fWetStart = fTime;
				s_fWetSent = -1.0f;
				Renderer.SetVarFloat("puddles", s_aWetCfg[0]);
			}
			else if (fTime - s_fWetStart < WET_SNAP_TIME)
			{
				s_fWetness = Math.max(s_fWetness, fTarget);
			}
			else
			{
				float dt = Math.min(fTime - s_fWetTime, 1.0f);
				if (s_fWetness < fTarget)
					s_fWetness = Math.min(fTarget, s_fWetness + dt * (WET_RISE_BASE + fRain * WET_RISE_RAIN));
				else
					s_fWetness = Math.max(fTarget, s_fWetness - dt / WET_DRY_TIME);
			}
			s_fWetTime = fTime;
			float fOut = s_aWetCfg[1] >= 0.0f ? s_aWetCfg[1] : s_fWetness;
			if (s_bWetLog && fTime - s_fWetLogTime >= WET_LOG_STEP && s_iWetLogLines < WET_LOG_MAX)
			{
				s_fWetLogTime = fTime;
				s_iWetLogLines++;
				try
				{
					java.io.FileWriter fw = new java.io.FileWriter(SWITCH_DIR + "wetness_log.txt", true);
					fw.write("t=" + fTime + " pct=" + w.GetRainPercentage()
						+ " enabled=" + (tm != null && tm.m_bRainEnabled)
						+ " density=" + (tm != null ? tm.m_fRainDensity : -1.0f)
						+ " snow=" + w.IsSnowyWeather()
						+ " target=" + fTarget + " wet=" + fOut
						+ " var=" + Renderer.GetVarFloat("wetness") + "\r\n");
					fw.close();
				}
				catch (Throwable e)
				{
				}
			}
			if (Math.abs(fOut - s_fWetSent) > 0.002f)
			{
				Renderer.SetVarFloat("wetness", fOut);
				s_fWetSent = fOut;
			}
		}
		catch (Throwable e)
		{
		}
	}

	public static boolean GlassMarksVelocity(CarFX c, int nFX, float fVel)
	{
		float fAccelK = s_aDropCfg[0], fBumpK = s_aDropCfg[1];
		if (!s_bGlassDrops || (fAccelK <= 0.0f && fBumpK <= 0.0f) || c.m_vrLinVel == null)
			return c.FXGlassMarksVelocity(nFX, fVel);

		float fTime = c.GetTime();
		Vector v = c.m_vrLinVel;
		float fVF = v.Dot(c.GetForwardVectorVolatile());
		float fVU = v.Dot(c.GetUpVectorVolatile());

		float[] s = (float[])s_mDropState.get(c);
		if (s == null)
		{
			s = new float[] { fTime, fVF, fVU, 0.0f, 0.0f };
			s_mDropState.put(c, s);
		}
		float dt = fTime - s[0];
		if (dt < 0.0f || dt > 0.5f)
		{
			s[3] = 0.0f;
			s[4] = 0.0f;
		}
		else if (dt > 0.0f)
		{
			float fAF = (fVF - s[1]) / dt;
			float fAU = Math.abs(fVU - s[2]) / dt - DROP_JOLT_MIN;
			s[3] += (fAF - s[3]) * Math.min(1.0f, dt / DROP_ACCEL_SMOOTH);
			s[4] *= (float)Math.exp(-dt / DROP_JOLT_DECAY);
			if (fAU > s[4])
				s[4] = fAU;
		}
		s[0] = fTime;
		s[1] = fVF;
		s[2] = fVU;

		float fExtra = fAccelK * s[3] - fBumpK * s[4];
		if (fExtra > DROP_EXTRA_MAX)
			fExtra = DROP_EXTRA_MAX;
		if (fExtra < -DROP_EXTRA_MAX)
			fExtra = -DROP_EXTRA_MAX;
		return c.FXGlassMarksVelocity(nFX, fVel + fExtra * c.m_fOnFrontGlassFXVelCoeff);
	}
}
