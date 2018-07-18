// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class B77240639 extends TestBase {
  @Test
  public void test1() throws Exception {
    AndroidApp app = compileWithD8(readClasses(TestClass.class));
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertThat(clazz, isPresent());
  }

  @Test
  public void test2() throws Exception {
    AndroidApp app = compileWithD8(readClasses(OtherTestClass.class));
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(OtherTestClass.class);
    assertThat(clazz, isPresent());
    ToolHelper.ProcessResult d8Result = runOnArtRaw(app, OtherTestClass.class.getCanonicalName());
    assertThat(d8Result.stderr, not(containsString("StringIndexOutOfBoundsException")));
  }
}

class TestClass {
  private boolean b;
  private boolean b_flag = false;
  private Boolean ob;
  private boolean ob_flag = false;
  private List<Boolean> vb;
  private boolean vb_flag = false;
  private List<Boolean> vob;
  private boolean vob_flag = false;
  private Map<String, Boolean> db;
  private boolean db_flag = false;
  private Map<String, Boolean> dob;
  private boolean dob_flag = false;
  private int i;
  private boolean i_flag = false;
  private Integer oi;
  private boolean oi_flag = false;
  private List<Boolean> vi;
  private boolean vi_flag = false;
  private List<Boolean> voi;
  private boolean voi_flag = false;
  private Map<String, Boolean> di;
  private boolean di_flag = false;
  private Map<String, Boolean> doi;
  private boolean doi_flag = false;
  private int ui;
  private boolean ui_flag = false;
  private Integer oui;
  private boolean oui_flag = false;
  private List<Boolean> vui;
  private boolean vui_flag = false;
  private List<Boolean> voui;
  private boolean voui_flag = false;
  private Map<String, Boolean> dui;
  private boolean dui_flag = false;
  private Map<String, Boolean> doui;
  private boolean doui_flag = false;
  private long i64;
  private boolean i64_flag = false;
  private Long oi64;
  private boolean oi64_flag = false;
  private List<Boolean> vi64;
  private boolean vi64_flag = false;
  private List<Boolean> voi64;
  private boolean voi64_flag = false;
  private Map<String, Boolean> di64;
  private boolean di64_flag = false;
  private Map<String, Boolean> doi64;
  private boolean doi64_flag = false;
  private float fl;
  private boolean fl_flag = false;
  private Float ofl;
  private boolean ofl_flag = false;
  private List<Boolean> vfl;
  private boolean vfl_flag = false;
  private List<Boolean> vofl;
  private boolean vofl_flag = false;
  private Map<String, Boolean> dfl;
  private boolean dfl_flag = false;
  private Map<String, Boolean> dofl;
  private boolean dofl_flag = false;
  private double d;
  private boolean d_flag = false;
  private Double od;
  private boolean od_flag = false;
  private List<Boolean> vd;
  private boolean vd_flag = false;
  private List<Boolean> vod;
  private boolean vod_flag = false;
  private Map<String, Boolean> dd;
  private boolean dd_flag = false;
  private Map<String, Boolean> dod;
  private boolean dod_flag = false;
  private String s;
  private boolean s_flag = false;
  private String os;
  private boolean os_flag = false;
  private List<Boolean> vs;
  private boolean vs_flag = false;
  private List<Boolean> vos;
  private boolean vos_flag = false;
  private Map<String, Boolean> ds;
  private boolean ds_flag = false;
  private Map<String, Boolean> dos;
  private boolean dos_flag = false;
  private long ti;
  private boolean ti_flag = false;
  private Long oti;
  private boolean oti_flag = false;
  private List<Boolean> vti;
  private boolean vti_flag = false;
  private List<Boolean> voti;
  private boolean voti_flag = false;
  private Map<String, Boolean> dti;
  private boolean dti_flag = false;
  private Map<String, Boolean> doti;
  private boolean doti_flag = false;
  private long at;
  private boolean at_flag = false;
  private Long oat;
  private boolean oat_flag = false;
  private List<Boolean> vat;
  private boolean vat_flag = false;
  private List<Boolean> voat;
  private boolean voat_flag = false;
  private Map<String, Boolean> dat;
  private boolean dat_flag = false;
  private Map<String, Boolean> doat;
  private boolean doat_flag = false;
  private long rt;
  private boolean rt_flag = false;
  private Long ort;
  private boolean ort_flag = false;
  private List<Boolean> vrt;
  private boolean vrt_flag = false;
  private List<Boolean> vort;
  private boolean vort_flag = false;
  private Map<String, Boolean> drt;
  private boolean drt_flag = false;
  private Map<String, Boolean> dort;
  private boolean dort_flag = false;
  private byte[] by;
  private boolean by_flag = false;
  private byte[] oby;
  private boolean oby_flag = false;
  private int c;
  private boolean c_flag = false;
  private Integer oc;
  private boolean oc_flag = false;
  private List<Integer> vc;
  private boolean vc_flag = false;
  private List<Integer> voc;
  private boolean voc_flag = false;
  private Map<String, Integer> dc;
  private boolean dc_flag = false;
  private Map<String, Integer> doc;
  private boolean doc_flag = false;
  private Object p;
  private boolean p_flag = false;
  private Object op;
  private boolean op_flag = false;
  private List<Object> vp;
  private boolean vp_flag = false;
  private List<Object> vop;
  private boolean vop_flag = false;
  private Map<String, Object> dp;
  private boolean dp_flag = false;
  private Map<String, Object> dop;
  private boolean dop_flag = false;
  private Object e;
  private boolean e_flag = false;
  private Object oe;
  private boolean oe_flag = false;
  private List<Object> ve;
  private boolean ve_flag = false;
  private List<Object> voe;
  private boolean voe_flag = false;
  private Map<String, Object> de;
  private boolean de_flag = false;
  private Map<String, Object> doe;
  private boolean doe_flag = false;
  private int be;
  private boolean be_flag = false;
  private Integer obe;
  private boolean obe_flag = false;
  private List<Object> vbe;
  private boolean vbe_flag = false;
  private List<Object> vobe;
  private boolean vobe_flag = false;
  private Map<String, Object> dbe;
  private boolean dbe_flag = false;
  private Map<String, Object> dobe;
  private boolean dobe_flag = false;
  private Object ts;
  private boolean ts_flag = false;
  private Object ots;
  private boolean ots_flag = false;
  private List<Object> vts;
  private boolean vts_flag = false;
  private List<Object> vots;
  private boolean vots_flag = false;
  private Map<String, Object> dts;
  private boolean dts_flag = false;
  private Map<String, Object> dots;
  private boolean dots_flag = false;
  private Object lts;
  private boolean lts_flag = false;
  private Object olts;
  private boolean olts_flag = false;
  private List<Object> vlts;
  private boolean vlts_flag = false;
  private List<Object> volts;
  private boolean volts_flag = false;
  private Map<String, Object> dlts;
  private boolean dlts_flag = false;
  private Map<String, Object> dolts;
  private boolean dolts_flag = false;
  private Object opts;
  private boolean opts_flag = false;
  private Object oopts;
  private boolean oopts_flag = false;
  private List<Object> vopts;
  private boolean vopts_flag = false;
  private List<Object> voopts;
  private boolean voopts_flag = false;
  private Map<String, Object> dopts;
  private boolean dopts_flag = false;
  private Map<String, Object> doopts;
  private boolean doopts_flag = false;
  private Object nativeObject;

  public TestClass() {}

  public TestClass(
      boolean b,
      Boolean ob,
      List<Boolean> vb,
      List<Boolean> vob,
      Map<String, Boolean> db,
      Map<String, Boolean> dob,
      int i,
      Integer oi,
      List<Boolean> vi,
      List<Boolean> voi,
      Map<String, Boolean> di,
      Map<String, Boolean> doi,
      int ui,
      Integer oui,
      List<Boolean> vui,
      List<Boolean> voui,
      Map<String, Boolean> dui,
      Map<String, Boolean> doui,
      long i64,
      Long oi64,
      List<Boolean> vi64,
      List<Boolean> voi64,
      Map<String, Boolean> di64,
      Map<String, Boolean> doi64,
      float fl,
      Float ofl,
      List<Boolean> vfl,
      List<Boolean> vofl,
      Map<String, Boolean> dfl,
      Map<String, Boolean> dofl,
      double d,
      Double od,
      List<Boolean> vd,
      List<Boolean> vod,
      Map<String, Boolean> dd,
      Map<String, Boolean> dod,
      String s,
      String os,
      List<Boolean> vs,
      List<Boolean> vos,
      Map<String, Boolean> ds,
      Map<String, Boolean> dos,
      long ti,
      Long oti,
      List<Boolean> vti,
      List<Boolean> voti,
      Map<String, Boolean> dti,
      Map<String, Boolean> doti,
      long at,
      Long oat,
      List<Boolean> vat,
      List<Boolean> voat,
      Map<String, Boolean> dat,
      Map<String, Boolean> doat,
      long rt,
      Long ort,
      List<Boolean> vrt,
      List<Boolean> vort,
      Map<String, Boolean> drt,
      Map<String, Boolean> dort,
      byte[] by,
      byte[] oby,
      int c,
      Integer oc,
      List<Integer> vc,
      List<Integer> voc,
      Map<String, Integer> dc,
      Map<String, Integer> doc,
      Object p,
      Object op,
      List<Object> vp,
      List<Object> vop,
      Map<String, Object> dp,
      Map<String, Object> dop,
      Object e,
      Object oe,
      List<Object> ve,
      List<Object> voe,
      Map<String, Object> de,
      Map<String, Object> doe,
      int be,
      Integer obe,
      List<Object> vbe,
      List<Object> vobe,
      Map<String, Object> dbe,
      Map<String, Object> dobe,
      Object ts,
      Object ots,
      List<Object> vts,
      List<Object> vots,
      Map<String, Object> dts,
      Map<String, Object> dots,
      Object lts,
      Object olts,
      List<Object> vlts,
      List<Object> volts,
      Map<String, Object> dlts,
      Map<String, Object> dolts,
      Object opts,
      Object oopts,
      List<Object> vopts,
      List<Object> voopts,
      Map<String, Object> dopts,
      Map<String, Object> doopts) {
    if (vb == null) {
      throw new IllegalArgumentException("vb");
    } else if (vob == null) {
      throw new IllegalArgumentException("vob");
    } else if (db == null) {
      throw new IllegalArgumentException("db");
    } else if (dob == null) {
      throw new IllegalArgumentException("dob");
    } else if (vi == null) {
      throw new IllegalArgumentException("vi");
    } else if (voi == null) {
      throw new IllegalArgumentException("voi");
    } else if (di == null) {
      throw new IllegalArgumentException("di");
    } else if (doi == null) {
      throw new IllegalArgumentException("doi");
    } else if (vui == null) {
      throw new IllegalArgumentException("vui");
    } else if (voui == null) {
      throw new IllegalArgumentException("voui");
    } else if (dui == null) {
      throw new IllegalArgumentException("dui");
    } else if (doui == null) {
      throw new IllegalArgumentException("doui");
    } else if (vi64 == null) {
      throw new IllegalArgumentException("vi64");
    } else if (voi64 == null) {
      throw new IllegalArgumentException("voi64");
    } else if (di64 == null) {
      throw new IllegalArgumentException("di64");
    } else if (doi64 == null) {
      throw new IllegalArgumentException("doi64");
    } else if (vfl == null) {
      throw new IllegalArgumentException("vfl");
    } else if (vofl == null) {
      throw new IllegalArgumentException("vofl");
    } else if (dfl == null) {
      throw new IllegalArgumentException("dfl");
    } else if (dofl == null) {
      throw new IllegalArgumentException("dofl");
    } else if (vd == null) {
      throw new IllegalArgumentException("vd");
    } else if (vod == null) {
      throw new IllegalArgumentException("vod");
    } else if (dd == null) {
      throw new IllegalArgumentException("dd");
    } else if (dod == null) {
      throw new IllegalArgumentException("dod");
    } else if (s == null) {
      throw new IllegalArgumentException("s");
    } else if (vs == null) {
      throw new IllegalArgumentException("vs");
    } else if (vos == null) {
      throw new IllegalArgumentException("vos");
    } else if (ds == null) {
      throw new IllegalArgumentException("ds");
    } else if (dos == null) {
      throw new IllegalArgumentException("dos");
    } else if (vti == null) {
      throw new IllegalArgumentException("vti");
    } else if (voti == null) {
      throw new IllegalArgumentException("voti");
    } else if (dti == null) {
      throw new IllegalArgumentException("dti");
    } else if (doti == null) {
      throw new IllegalArgumentException("doti");
    } else if (vat == null) {
      throw new IllegalArgumentException("vat");
    } else if (voat == null) {
      throw new IllegalArgumentException("voat");
    } else if (dat == null) {
      throw new IllegalArgumentException("dat");
    } else if (doat == null) {
      throw new IllegalArgumentException("doat");
    } else if (vrt == null) {
      throw new IllegalArgumentException("vrt");
    } else if (vort == null) {
      throw new IllegalArgumentException("vort");
    } else if (drt == null) {
      throw new IllegalArgumentException("drt");
    } else if (dort == null) {
      throw new IllegalArgumentException("dort");
    } else if (by == null) {
      throw new IllegalArgumentException("by");
    } else if (vc == null) {
      throw new IllegalArgumentException("vc");
    } else if (voc == null) {
      throw new IllegalArgumentException("voc");
    } else if (dc == null) {
      throw new IllegalArgumentException("dc");
    } else if (doc == null) {
      throw new IllegalArgumentException("doc");
    } else if (p == null) {
      throw new IllegalArgumentException("p");
    } else if (vp == null) {
      throw new IllegalArgumentException("vp");
    } else if (vop == null) {
      throw new IllegalArgumentException("vop");
    } else if (dp == null) {
      throw new IllegalArgumentException("dp");
    } else if (dop == null) {
      throw new IllegalArgumentException("dop");
    } else if (e == null) {
      throw new IllegalArgumentException("e");
    } else if (ve == null) {
      throw new IllegalArgumentException("ve");
    } else if (voe == null) {
      throw new IllegalArgumentException("voe");
    } else if (de == null) {
      throw new IllegalArgumentException("de");
    } else if (doe == null) {
      throw new IllegalArgumentException("doe");
    } else if (vbe == null) {
      throw new IllegalArgumentException("vbe");
    } else if (vobe == null) {
      throw new IllegalArgumentException("vobe");
    } else if (dbe == null) {
      throw new IllegalArgumentException("dbe");
    } else if (dobe == null) {
      throw new IllegalArgumentException("dobe");
    } else if (ts == null) {
      throw new IllegalArgumentException("ts");
    } else if (vts == null) {
      throw new IllegalArgumentException("vts");
    } else if (vots == null) {
      throw new IllegalArgumentException("vots");
    } else if (dts == null) {
      throw new IllegalArgumentException("dts");
    } else if (dots == null) {
      throw new IllegalArgumentException("dots");
    } else if (lts == null) {
      throw new IllegalArgumentException("lts");
    } else if (vlts == null) {
      throw new IllegalArgumentException("vlts");
    } else if (volts == null) {
      throw new IllegalArgumentException("volts");
    } else if (dlts == null) {
      throw new IllegalArgumentException("dlts");
    } else if (dolts == null) {
      throw new IllegalArgumentException("dolts");
    } else if (opts == null) {
      throw new IllegalArgumentException("opts");
    } else if (vopts == null) {
      throw new IllegalArgumentException("vopts");
    } else if (voopts == null) {
      throw new IllegalArgumentException("voopts");
    } else if (dopts == null) {
      throw new IllegalArgumentException("dopts");
    } else if (doopts == null) {
      throw new IllegalArgumentException("doopts");
    } else {
      this.nativeObject =
          this.init(
              b, ob, vb, vob, db, dob, i, oi, vi, voi, di, doi, ui, oui, vui, voui, dui, doui, i64,
              oi64, vi64, voi64, di64, doi64, fl, ofl, vfl, vofl, dfl, dofl, d, od, vd, vod, dd,
              dod, s, os, vs, vos, ds, dos, ti, oti, vti, voti, dti, doti, at, oat, vat, voat, dat,
              doat, rt, ort, vrt, vort, drt, dort, by, oby, c, oc, vc, voc, dc, doc, p, op, vp, vop,
              dp, dop, e, oe, ve, voe, de, doe, be, obe, vbe, vobe, dbe, dobe, ts, ots, vts, vots,
              dts, dots, lts, olts, vlts, volts, dlts, dolts, opts, oopts, vopts, voopts, dopts,
              doopts);
      this.b = b;
      this.b_flag = true;
      this.ob = ob;
      this.ob_flag = true;
      this.vb = vb;
      this.vb_flag = true;
      this.vob = vob;
      this.vob_flag = true;
      this.db = db;
      this.db_flag = true;
      this.dob = dob;
      this.dob_flag = true;
      this.i = i;
      this.i_flag = true;
      this.oi = oi;
      this.oi_flag = true;
      this.vi = vi;
      this.vi_flag = true;
      this.voi = voi;
      this.voi_flag = true;
      this.di = di;
      this.di_flag = true;
      this.doi = doi;
      this.doi_flag = true;
      this.ui = ui;
      this.ui_flag = true;
      this.oui = oui;
      this.oui_flag = true;
      this.vui = vui;
      this.vui_flag = true;
      this.voui = voui;
      this.voui_flag = true;
      this.dui = dui;
      this.dui_flag = true;
      this.doui = doui;
      this.doui_flag = true;
      this.i64 = i64;
      this.i64_flag = true;
      this.oi64 = oi64;
      this.oi64_flag = true;
      this.vi64 = vi64;
      this.vi64_flag = true;
      this.voi64 = voi64;
      this.voi64_flag = true;
      this.di64 = di64;
      this.di64_flag = true;
      this.doi64 = doi64;
      this.doi64_flag = true;
      this.fl = fl;
      this.fl_flag = true;
      this.ofl = ofl;
      this.ofl_flag = true;
      this.vfl = vfl;
      this.vfl_flag = true;
      this.vofl = vofl;
      this.vofl_flag = true;
      this.dfl = dfl;
      this.dfl_flag = true;
      this.dofl = dofl;
      this.dofl_flag = true;
      this.d = d;
      this.d_flag = true;
      this.od = od;
      this.od_flag = true;
      this.vd = vd;
      this.vd_flag = true;
      this.vod = vod;
      this.vod_flag = true;
      this.dd = dd;
      this.dd_flag = true;
      this.dod = dod;
      this.dod_flag = true;
      this.s = s;
      this.s_flag = true;
      this.os = os;
      this.os_flag = true;
      this.vs = vs;
      this.vs_flag = true;
      this.vos = vos;
      this.vos_flag = true;
      this.ds = ds;
      this.ds_flag = true;
      this.dos = dos;
      this.dos_flag = true;
      this.ti = ti;
      this.ti_flag = true;
      this.oti = oti;
      this.oti_flag = true;
      this.vti = vti;
      this.vti_flag = true;
      this.voti = voti;
      this.voti_flag = true;
      this.dti = dti;
      this.dti_flag = true;
      this.doti = doti;
      this.doti_flag = true;
      this.at = at;
      this.at_flag = true;
      this.oat = oat;
      this.oat_flag = true;
      this.vat = vat;
      this.vat_flag = true;
      this.voat = voat;
      this.voat_flag = true;
      this.dat = dat;
      this.dat_flag = true;
      this.doat = doat;
      this.doat_flag = true;
      this.rt = rt;
      this.rt_flag = true;
      this.ort = ort;
      this.ort_flag = true;
      this.vrt = vrt;
      this.vrt_flag = true;
      this.vort = vort;
      this.vort_flag = true;
      this.drt = drt;
      this.drt_flag = true;
      this.dort = dort;
      this.dort_flag = true;
      this.by = by;
      this.by_flag = true;
      this.oby = oby;
      this.oby_flag = true;
      this.c = c;
      this.c_flag = true;
      this.oc = oc;
      this.oc_flag = true;
      this.vc = vc;
      this.vc_flag = true;
      this.voc = voc;
      this.voc_flag = true;
      this.dc = dc;
      this.dc_flag = true;
      this.doc = doc;
      this.doc_flag = true;
      this.p = p;
      this.p_flag = true;
      this.op = op;
      this.op_flag = true;
      this.vp = vp;
      this.vp_flag = true;
      this.vop = vop;
      this.vop_flag = true;
      this.dp = dp;
      this.dp_flag = true;
      this.dop = dop;
      this.dop_flag = true;
      this.e = e;
      this.e_flag = true;
      this.oe = oe;
      this.oe_flag = true;
      this.ve = ve;
      this.ve_flag = true;
      this.voe = voe;
      this.voe_flag = true;
      this.de = de;
      this.de_flag = true;
      this.doe = doe;
      this.doe_flag = true;
      this.be = be;
      this.be_flag = true;
      this.obe = obe;
      this.obe_flag = true;
      this.vbe = vbe;
      this.vbe_flag = true;
      this.vobe = vobe;
      this.vobe_flag = true;
      this.dbe = dbe;
      this.dbe_flag = true;
      this.dobe = dobe;
      this.dobe_flag = true;
      this.ts = ts;
      this.ts_flag = true;
      this.ots = ots;
      this.ots_flag = true;
      this.vts = vts;
      this.vts_flag = true;
      this.vots = vots;
      this.vots_flag = true;
      this.dts = dts;
      this.dts_flag = true;
      this.dots = dots;
      this.dots_flag = true;
      this.lts = lts;
      this.lts_flag = true;
      this.olts = olts;
      this.olts_flag = true;
      this.vlts = vlts;
      this.vlts_flag = true;
      this.volts = volts;
      this.volts_flag = true;
      this.dlts = dlts;
      this.dlts_flag = true;
      this.dolts = dolts;
      this.dolts_flag = true;
      this.opts = opts;
      this.opts_flag = true;
      this.oopts = oopts;
      this.oopts_flag = true;
      this.vopts = vopts;
      this.vopts_flag = true;
      this.voopts = voopts;
      this.voopts_flag = true;
      this.dopts = dopts;
      this.dopts_flag = true;
      this.doopts = doopts;
      this.doopts_flag = true;
    }
  }

  private native Object init(
      boolean v1,
      Boolean v2,
      List<Boolean> v3,
      List<Boolean> v4,
      Map<String, Boolean> v5,
      Map<String, Boolean> v6,
      int v7, Integer var8,
      List<Boolean> v9,
      List<Boolean> v10,
      Map<String, Boolean> v11,
      Map<String, Boolean> v12,
      int v13,
      Integer v14,
      List<Boolean> v15,
      List<Boolean> v16,
      Map<String, Boolean> v17,
      Map<String, Boolean> v18,
      long v19, Long var21,
      List<Boolean> v22,
      List<Boolean> v23,
      Map<String, Boolean> v24,
      Map<String, Boolean> v25,
      float v26,
      Float v27,
      List<Boolean> v28,
      List<Boolean> v29,
      Map<String, Boolean> v30,
      Map<String, Boolean> v31,
      double v32,
      Double v34,
      List<Boolean> v35,
      List<Boolean> v36,
      Map<String, Boolean> v37,
      Map<String, Boolean> v38,
      String v39,
      String v40,
      List<Boolean> v41,
      List<Boolean> v42,
      Map<String, Boolean> v43,
      Map<String, Boolean> v44,
      long v45, Long var47,
      List<Boolean> v48,
      List<Boolean> v49,
      Map<String, Boolean> v50,
      Map<String, Boolean> v51,
      long v52,
      Long v54,
      List<Boolean> v55,
      List<Boolean> v56,
      Map<String, Boolean> v57,
      Map<String, Boolean> v58,
      long v59,
      Long v61,
      List<Boolean> v62,
      List<Boolean> v63,
      Map<String, Boolean> v64,
      Map<String, Boolean> v65,
      byte[] v66,
      byte[] v67,
      int v68,
      Integer v69,
      List<Integer> v70,
      List<Integer> v71,
      Map<String, Integer> v72,
      Map<String, Integer> v73,
      Object v74,
      Object v75,
      List<Object> v76,
      List<Object> v77,
      Map<String, Object> v78,
      Map<String, Object> v79,
      Object v80,
      Object v81,
      List<Object> v82,
      List<Object> v83,
      Map<String, Object> v84,
      Map<String, Object> v85,
      int v86,
      Integer v87,
      List<Object> v88,
      List<Object> v89,
      Map<String, Object> v90,
      Map<String, Object> v91,
      Object v92,
      Object v93,
      List<Object> v94,
      List<Object> v95,
      Map<String, Object> v96,
      Map<String, Object> v97,
      Object v98,
      Object v99,
      List<Object> v100,
      List<Object> v101,
      Map<String, Object> v102,
      Map<String, Object> v103,
      Object v104,
      Object v105,
      List<Object> v106,
      List<Object> v107,
      Map<String, Object> v108,
      Map<String, Object> v109);
}

class OtherTestClass {
  public static void main(String[] args) {
    f("x", 0, 1, "", true, true, true);
  }

  static void f(String x1, int x2, int x3, String x4, boolean x5, boolean x6, boolean x7) {
    x1.codePointAt(0);
    int x8 = 37;
    for (int x9 = x2; x9 < x3; x9 += Character.charCount(x8)) {
      if ((x8 >= 128 && x7) || x4.indexOf(37) != -1 || !x5 || x6) {
        Object obj = new Object();
        h(obj, x1, x2, x3, x4, x5, x6, x7);
        obj.toString();
      }
    }
    x1.substring(x2, x3);
  }

  static void h(
      Object obj, String x1, int x2, int x3, String x4, boolean x5, boolean x6, boolean x7) {}
}
