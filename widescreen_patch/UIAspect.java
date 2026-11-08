public class UIAspect
{
	static final float ASPECT = 4.0f / 3.0f;
	static final float FULL_WIDTH = 0.95f;
	static final float HUD_LEFT_EDGE = 0.45f;
	static final float HUD_RIGHT_EDGE = 0.55f;

	static final int ANCHOR_LEFT = 0;
	static final int ANCHOR_CENTER = 1;
	static final int ANCHOR_RIGHT = 2;
	static final Integer[] ANCHORS = { new Integer(ANCHOR_LEFT), new Integer(ANCHOR_CENTER), new Integer(ANCHOR_RIGHT) };

	static float s_fW = 1024.0f;
	static float s_fH = 768.0f;
	static float s_fBoxW = 800.0f;
	static java.util.WeakHashMap s_mAnchors = new java.util.WeakHashMap();

	static final String SWITCH_DIR = "widescreen_patch/";
	static final String INI_FILE = "mods.ini";
	static java.util.HashMap s_mIni = null;

	// mods.ini in the game folder: [section] / key = value, ';' or '#' starts a comment
	static java.util.HashMap Ini()
	{
		if (s_mIni != null)
			return s_mIni;
		s_mIni = new java.util.HashMap();
		try
		{
			java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(INI_FILE));
			String sSection = "";
			String s;
			while ((s = r.readLine()) != null)
			{
				int c = s.indexOf(';');
				int h = s.indexOf('#');
				if (h >= 0 && (c < 0 || h < c))
					c = h;
				if (c >= 0)
					s = s.substring(0, c);
				s = s.trim();
				if (s.length() == 0)
					continue;
				if (s.startsWith("[") && s.endsWith("]"))
				{
					sSection = s.substring(1, s.length() - 1).trim().toLowerCase();
					continue;
				}
				int e = s.indexOf('=');
				if (e <= 0)
					continue;
				s_mIni.put(sSection + "." + s.substring(0, e).trim().toLowerCase(), s.substring(e + 1).trim());
			}
			r.close();
		}
		catch (Throwable e)
		{
		}
		return s_mIni;
	}

	public static String IniString(String sSection, String sKey, String sDefault)
	{
		Object o = Ini().get(sSection.toLowerCase() + "." + sKey.toLowerCase());
		return o == null ? sDefault : (String)o;
	}

	public static boolean IniBool(String sSection, String sKey, boolean bDefault)
	{
		String s = IniString(sSection, sKey, null);
		if (s == null)
			return bDefault;
		s = s.toLowerCase();
		return s.equals("1") || s.equals("true") || s.equals("on") || s.equals("yes");
	}

	public static float IniFloat(String sSection, String sKey, float fDefault)
	{
		try
		{
			String s = IniString(sSection, sKey, null);
			return s == null ? fDefault : Float.parseFloat(s);
		}
		catch (Throwable e)
		{
			return fDefault;
		}
	}

	public static int IniInt(String sSection, String sKey, int nDefault)
	{
		return Math.round(IniFloat(sSection, sKey, (float)nDefault));
	}

	static boolean s_bAspect = IniBool("widescreen", "aspect", true);
	static boolean s_bResList = IniBool("widescreen", "resolution_list", true);
	static boolean s_bFov = IniBool("widescreen", "fov", true);
	static boolean s_bView = IniBool("widescreen", "view_distance", true);
	static final float FOV_MAX = 170.0f;
	static int s_nDumps = 0;

	public static float WidenFOV(float fFov)
	{
		if (!s_bFov || fFov <= 0.0f || fFov >= FOV_MAX)
			return fFov;
		float w = (float)GameObject.GetScreenWidth();
		float h = (float)GameObject.GetScreenHeight();
		if (w <= 0.0f || h <= 0.0f)
			return fFov;
		double k = (w / h) / ASPECT;
		if (k <= 1.0)
			return fFov;
		double r = 2.0 * Math.atan(Math.tan(Math.toRadians(fFov) * 0.5) * k);
		float fOut = (float)Math.toDegrees(r);
		return fOut > FOV_MAX ? FOV_MAX : fOut;
	}

	public static void SetFOV(Camera c, float fFov)
	{
		c.SetFOV(WidenFOV(fFov));
	}

	static float s_fViewMul = ReadViewMul();

	static float ReadViewMul()
	{
		float v = IniFloat("widescreen", "view_distance_mul", 1.0f);
		return v >= 0.1f && v <= 10.0f ? v : 1.0f;
	}

	public static float ViewRangeMul()
	{
		if (!s_bView)
			return 1.0f;
		float m = s_fViewMul;
		if (s_bFov)
		{
			float w = (float)GameObject.GetScreenWidth();
			float h = (float)GameObject.GetScreenHeight();
			if (w > 0.0f && h > 0.0f)
			{
				float k = (w / h) / ASPECT;
				if (k > 1.0f)
					m *= k;
			}
		}
		return m;
	}

	public static void SetObjectsVisibilityRange(GameObject o, float fBlend, float fFade)
	{
		float m = ViewRangeMul();
		o.SetObjectsVisibilityRange(fBlend * m, fFade * m);
	}

	public static void SetTreesVisibilityRange(Module o, float fBlend, float fRange)
	{
		float m = ViewRangeMul();
		o.SetTreesVisibilityRange(fBlend * m, fRange * m);
	}

	public static void SetGrassVisMul(Module o, float fMul)
	{
		o.SetGrassVisMul(fMul * ViewRangeMul());
	}
	static final int MAX_DUMPS = 4;

	public static void DumpTreeOnce(UIWindow w, String sTag)
	{
		if (s_nDumps >= MAX_DUMPS)
			return;
		s_nDumps++;
		try
		{
			Update();
			GameObject.CrashLog("[UIAspect.Dump] " + sTag + " aspect=" + s_bAspect + " reslist=" + s_bResList
				+ " W=" + s_fW + " H=" + s_fH + " BoxW=" + s_fBoxW + "\n");
			DumpTree(w, 0);
		}
		catch (Throwable t)
		{
			LogError(t, "DumpTreeOnce");
		}
	}

	static void DumpTree(UIWindow w, int nDepth)
	{
		if (w == null || nDepth > 12)
			return;
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < nDepth; i++)
			sb.append("  ");
		sb.append(w.getClass().getName()).append(' ').append(w.m_sId);
		try
		{
			sb.append(" vis=").append(w.m_bVisible).append('/').append(w.IsVisible()).append('/').append(w.IsActuallyVisible());
			sb.append(" a=").append(w.GetAlpha());
			Vector p = w.GetPos();
			Vector s = w.GetSize();
			if (p != null)
				sb.append(" pos=").append(p.fX).append(',').append(p.fY);
			if (s != null)
				sb.append(" size=").append(s.fX).append(',').append(s.fY);
		}
		catch (Throwable t)
		{
			sb.append(" <").append(t).append('>');
		}
		if (w.m_vLogicPos != null)
			sb.append(" lpos=").append(w.m_vLogicPos.fX).append(',').append(w.m_vLogicPos.fY);
		if (w.m_vLogicSize != null)
			sb.append(" lsize=").append(w.m_vLogicSize.fX).append(',').append(w.m_vLogicSize.fY);
		sb.append(" pp=").append(w.m_bParentPositioning).append('\n');
		GameObject.CrashLog(sb.toString());
		if (w.m_vChildWindows != null)
			for (int i = 0; i < w.m_vChildWindows.size(); i++)
			{
				Object o = w.m_vChildWindows.elementAt(i);
				if (o instanceof UIWindow)
					DumpTree((UIWindow)o, nDepth + 1);
			}
	}

	static void Update()
	{
		float w = (float)GameObject.GetScreenWidth();
		float h = (float)GameObject.GetScreenHeight();
		if (w <= 0.0f || h <= 0.0f)
		{
			w = 800.0f;
			h = 600.0f;
		}
		float box = h * ASPECT;
		s_fW = w;
		s_fH = h;
		s_fBoxW = (s_bAspect && box < w) ? box : w;
		UIWindow.m_fDimX = s_fBoxW;
		UIWindow.m_fDimY = s_fH;
	}

	public static int GetUIWidth()
	{
		Update();
		return Math.round(s_fBoxW);
	}

	static boolean IsFullX(UIWindow w)
	{
		return w.m_vLogicSize != null && w.m_vLogicSize.fX >= FULL_WIDTH;
	}

	static int FindHUD(UIWindow w)
	{
		UIWindow c = w;
		boolean bLinked = false;
		for (int i = 0; c != null && i < 64; i++)
		{
			if (c instanceof HUDBase || c.m_cSupervisor instanceof HUDBase)
				return 1;
			UIWindow p = c.m_wndParent;
			if (p == null && c.m_cSupervisor != c)
				p = c.m_cSupervisor;
			if (p != null)
				bLinked = true;
			c = p;
		}
		return bLinked ? 0 : -1;
	}

	static int GetAnchor(UIWindow w)
	{
		Object o = s_mAnchors.get(w);
		if (o != null)
			return ((Integer)o).intValue();
		int nHUD = FindHUD(w);
		int a = ANCHOR_CENTER;
		boolean bLoaded = false;
		if (nHUD == 1 && w.m_vLogicPos != null && w.m_vLogicSize != null)
		{
			// some HUD windows ask before their position is loaded (pos 0,0): don't remember that
			bLoaded = w.m_vLogicPos.fX != 0.0f || w.m_vLogicPos.fY != 0.0f;
			float cx = w.m_vLogicPos.fX + w.m_vLogicSize.fX * 0.5f;
			if (cx < HUD_LEFT_EDGE)
				a = ANCHOR_LEFT;
			else if (cx > HUD_RIGHT_EDGE)
				a = ANCHOR_RIGHT;
		}
		if (nHUD == 0 || (nHUD == 1 && bLoaded))
			s_mAnchors.put(w, ANCHORS[a]);
		return a;
	}

	static float GetOffsetX(UIWindow w)
	{
		float fExtra = s_fW - s_fBoxW;
		if (fExtra <= 0.0f)
			return 0.0f;
		switch (GetAnchor(w))
		{
			case ANCHOR_LEFT:
				return 0.0f;
			case ANCHOR_RIGHT:
				return fExtra;
			default:
				return fExtra * 0.5f;
		}
	}

	public static float LogicToRealPosX(float fLogic, UIWindow w)
	{
		Update();
		if (IsFullX(w))
			return fLogic * s_fW;
		return GetOffsetX(w) + fLogic * s_fBoxW;
	}

	public static float LogicToRealSizeX(float fLogic, UIWindow w)
	{
		Update();
		if (fLogic >= FULL_WIDTH)
			return fLogic * s_fW;
		return fLogic * s_fBoxW;
	}

	public static float RealToLogicPosX(float fReal, UIWindow w)
	{
		Update();
		if (IsFullX(w))
			return fReal / s_fW;
		return (fReal - GetOffsetX(w)) / s_fBoxW;
	}

	public static float RealToLogicSizeX(float fReal, UIWindow w)
	{
		Update();
		if (fReal >= FULL_WIDTH * s_fW)
			return fReal / s_fW;
		float fLogic = fReal / s_fBoxW;
		if (fLogic >= FULL_WIDTH)
			fLogic = FULL_WIDTH - 0.0001f;
		return fLogic;
	}

	public static void OnCreate(UIWindow w)
	{
		Update();
	}

	public static Vector CalculatePos(UIWindow w)
	{
		Update();
		Vector v = new Vector(0.0f, 0.0f, 0.0f);
		if (!w.m_bParentPositioning)
		{
			v.fX = LogicToRealPosX(w.m_vLogicPos.fX, w);
			v.fY = w.m_vLogicPos.fY * s_fH;
			v.fZ = 0.0f;
		}
		else
		{
			w.UpdateParentPos();
			v.fX = w.GetRealByParentX();
			v.fY = w.GetRealByParentY();
			v.fZ = 0.0f;
		}
		return v;
	}

	public static Vector CalculateSize(UIWindow w)
	{
		Update();
		Vector v = new Vector(LogicToRealSizeX(w.m_vLogicSize.fX, w), w.m_vLogicSize.fY * s_fH, 0.0f);
		if (w.m_bParentSizingX)
		{
			w.UpdateParentPos();
			v.fX = w.m_vLogicSize.fX * w.m_vParentSize.fX;
		}
		if (w.m_bParentSizingY)
		{
			w.UpdateParentPos();
			v.fY = w.m_vLogicSize.fY * w.m_vParentSize.fY;
		}
		return v;
	}

	public static Vector PosRealToLogic(UIWindow w, Vector vReal)
	{
		Vector v = new Vector();
		if (w.m_bParentPositioning)
		{
			float fx = vReal.fX - w.m_vParentPos.fX;
			float fy = vReal.fY - w.m_vParentPos.fY;
			if (w.m_vParentSize.fX != 0.0f)
				v.fX = fx / w.m_vParentSize.fX;
			if (w.m_vParentSize.fY != 0.0f)
				v.fY = fy / w.m_vParentSize.fY;
		}
		else
		{
			v.fX = RealToLogicPosX(vReal.fX, w);
			v.fY = vReal.fY / s_fH;
		}
		return v;
	}

	public static void UpdateLogicSizeAndPos(UIWindow w, Vector vSize, Vector vPos)
	{
		Update();
		w.UpdateParentPos();
		if (!w.m_bParentSizingX)
			w.m_vLogicSize.fX = RealToLogicSizeX(vSize.fX, w);
		else
			w.m_vLogicSize.fX = vSize.fX / w.m_vParentSize.fX;
		if (!w.m_bParentSizingY)
			w.m_vLogicSize.fY = vSize.fY / s_fH;
		else
			w.m_vLogicSize.fY = vSize.fY / w.m_vParentSize.fY;
		if (w.m_bParentPositioning)
		{
			float fx = vPos.fX - w.m_vParentPos.fX;
			float fy = vPos.fY - w.m_vParentPos.fY;
			w.m_vLogicPos.fX = fx / w.m_vParentSize.fX;
			w.m_vLogicPos.fY = fy / w.m_vParentSize.fY;
		}
		else
		{
			w.m_vLogicPos.fX = RealToLogicPosX(vPos.fX, w);
			w.m_vLogicPos.fY = vPos.fY / s_fH;
		}
	}

	public static float GetRealX(UIWindow w)
	{
		if (w.m_bParentPositioning)
			return w.m_vParentSize.fX * w.m_vLogicPos.fX + w.m_vParentPos.fX;
		return LogicToRealPosX(w.m_vLogicPos.fX, w);
	}

	public static float GetResCoeffX(UIWindow w)
	{
		Update();
		return s_fBoxW / 800.0f;
	}

	public static float GetResCoeffY(UIWindow w)
	{
		Update();
		return s_fBoxW / 600.0f;
	}

	static final int MIN_MODE_W = 800;
	static final int MIN_MODE_H = 600;

	public static void LogError(Throwable t, String sWhere)
	{
		try
		{
			StringBuffer sb = new StringBuffer();
			sb.append("[UIAspect] exception in ").append(sWhere).append(": ").append(t).append('\n');
			StackTraceElement[] st = t.getStackTrace();
			for (int i = 0; st != null && i < st.length && i < 12; i++)
				sb.append("    at ").append(st[i]).append('\n');
			GameObject.CrashLog(sb.toString());
		}
		catch (Throwable e)
		{
		}
	}

	static final int MAX_MODES = 512;

	public static void FillResolution(UIWindow w)
	{
		MenuOptionsVideo m = (MenuOptionsVideo)w;
		UIOptionsControlSelect.OptionInfo cSel = null;
		int nAdded = 0;
		try
		{
			int nBpp = GameObject.Is24bppPresent() ? 24 : 16;
			int nCur = GameObject.GetResolutionMode();
			for (int i = 0; i < MAX_MODES; i++)
			{
				String sMode = GameObject.EnumResolution(i, nBpp);
				if (sMode == null || sMode.length() == 0)
					break;
				try
				{
					if (!IsModeAvailable(m, sMode))
						continue;
					if (sMode.equals("800x600"))
						m.m_iStandard800x600Mode = i;
					UIOptionsControlSelect.OptionInfo cOpt = m.m_cResolution.AddOption(sMode, new Integer(i));
					nAdded++;
					if (i == nCur || (cSel == null && s_bResList))
						cSel = cOpt;
				}
				catch (Throwable t)
				{
					GameObject.CrashLog("[UIAspect.FillResolution] mode " + i + " <" + sMode + ">: " + t + "\n");
				}
			}
		}
		catch (Throwable t)
		{
			GameObject.CrashLog("[UIAspect.FillResolution] " + t + "\n");
		}
		try
		{
			if (cSel != null)
				m.m_cResolution.SetSelected(cSel);
		}
		catch (Throwable t)
		{
			GameObject.CrashLog("[UIAspect.FillResolution] SetSelected: " + t + "\n");
		}
	}

	public static boolean IsModeAvailable(UIWindow w, String sMode)
	{
		if (sMode == null)
			return false;
		if (!s_bResList)
			return sMode.equals("800x600") || sMode.equals("1024x768") || sMode.equals("1152x864")
				|| sMode.equals("1280x960") || sMode.equals("1280x1024") || sMode.equals("1600x1200");
		int x = sMode.indexOf('x');
		if (x <= 0)
			return false;
		int nW, nH;
		try
		{
			nW = Integer.parseInt(sMode.substring(0, x).trim());
			nH = Integer.parseInt(sMode.substring(x + 1).trim());
		}
		catch (NumberFormatException e)
		{
			return false;
		}
		if (nW < MIN_MODE_W || nH < MIN_MODE_H)
			return false;
		if (w instanceof MenuOptionsVideo)
		{
			UIOptionsControlSelect cRes = ((MenuOptionsVideo)w).m_cResolution;
			if (cRes != null && cRes.m_vOptions != null)
			{
				for (int i = 0; i < cRes.m_vOptions.size(); i++)
				{
					Object o = cRes.m_vOptions.elementAt(i);
					if (o instanceof UIOptionsControlSelect.OptionInfo && sMode.equals(((UIOptionsControlSelect.OptionInfo)o).m_sName))
						return false;
				}
			}
		}
		return true;
	}
}
