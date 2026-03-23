/*
 * dsp.h — v10 FINAL COMBINED
 * ════════════════════════════════════════════════════════════════════
 * BioWearable IoT Project — Complete Signal Processing Engine
 *
 * Integrates the best accuracy improvements from all sensor tests:
 *
 *  EEG  (from inject_eeg_test_csv.py accuracy tests):
 *    - 4th-order Butterworth HPF(0.5Hz) + LPF(45Hz) via cascaded biquads
 *    - Higher-precision Notch50 coefficients
 *    - Multi-window sub-FFT (4×64-sample) for lower variance
 *    - EMA smoothing: alpha=0.25 for δ/θ/α/β, alpha=0.08 for γ
 *    - Full 256-sample FFT for gamma band (best high-freq resolution)
 *    - Artifact gating: skip FFT if signal variance too low/high
 *
 *  EMG  (from inject_emg_test.py excellent-accuracy tests):
 *    - 1st-order HPF(20Hz) causal (matches Python sosfilt ground truth)
 *    - Exact Notch50 biquad coefficients
 *    - RMS: 25-sample window, updated every 5 samples (50Hz)
 *    - ZCR: (buf[i]>0)!=(prev>0) — exact formula, no np.sign ambiguity
 *    - MeanFreq: spectral centroid 20-125Hz, 256-pt Hann FFT at 2Hz
 *    - Fatigue: initMF captured on first activation, tracks % drop
 *
 *  HRV  (from inject_and_test.py + RR direct inject tests):
 *    - Bandpass: HPF(0.5Hz) + LPF(12Hz) preserves PPG peak shape
 *    - Quadratic interpolation on peak apex (sub-sample timing accuracy)
 *    - 4-point derivative peak detection (robust against noise)
 *    - MAD outlier rejection on RR buffer (10×MAD window)
 *    - Sample std (÷n-1) for SDNN — matches clinical standard
 *    - Minimum 6 beats required before reporting metrics
 *    - Contact detection: variance > 20 ADC σ over 1-second window
 *    - injectRR(float rrMs): direct RR injection for accuracy testing
 *
 *  IMU  (complementary filter, unchanged — accurate from start):
 *    - LPF(10Hz) on accelerometer axes
 *    - Complementary filter CF_ALPHA=0.96 (gyro 96% + accel 4%)
 *    - Motion detection: |accelMag| outside [0.8g, 1.2g]
 *
 * Processing tiers (Core 1):
 *   250 Hz : all process() calls + complementary filter
 *    50 Hz : emg.computeRMS()
 *     2 Hz : eeg.computeBands(), emg.computeFFT(), hrv.computeMetrics()
 * ════════════════════════════════════════════════════════════════════
 */

#ifndef DSP_H
#define DSP_H

#include "config.h"
#include <math.h>

// ── Constants ────────────────────────────────────────────────────────
#define DSP_FS          250
#define DSP_FFT_N       256
#define DSP_RMS_WIN     25
#define DSP_RR_MAX      64
#define DSP_PEAK_REFR   50       // 200ms minimum RR (max 200 BPM)
#define DSP_EMG_THRESH  150.0f   // ADC units RMS activation threshold
#define CF_ALPHA        0.96f
#define ACCEL_SCALE     16384.0f
#define GYRO_SCALE      131.0f

// ════════════════════════════════════════════════════════════════════
//  FILTER BUILDING BLOCKS
// ════════════════════════════════════════════════════════════════════

// 1st-order High-Pass Filter
// y[n] = α·(y[n-1] + x[n] - x[n-1])   α = FS/(FS + 2π·fc)
struct HPF {
  float alpha, prevIn, prevOut; bool initialized;
  HPF(float fc) : alpha(DSP_FS/(DSP_FS+6.2832f*fc)),
    prevIn(0),prevOut(0),initialized(false){}
  float apply(float x){
    if(!initialized){prevIn=x;prevOut=0;initialized=true;return 0.0f;}
    float y=alpha*(prevOut+x-prevIn); prevIn=x; prevOut=y; return y;
  }
};

// 1st-order Low-Pass Filter (EMA)
// y[n] = α·x[n] + (1-α)·y[n-1]   α = 2π·fc/(2π·fc + FS)
struct LPF {
  float alpha,state; bool initialized;
  LPF(float fc):alpha(6.2832f*fc/(6.2832f*fc+DSP_FS)),
    state(0),initialized(false){}
  float apply(float x){
    if(!initialized){state=x;initialized=true;return x;}
    state=alpha*x+(1.0f-alpha)*state; return state;
  }
};

// 2nd-order Biquad IIR — Direct Form II Transposed
struct Biquad {
  float b0,b1,b2,a1,a2,w1,w2;
  Biquad():b0(1),b1(0),b2(0),a1(0),a2(0),w1(0),w2(0){}
  Biquad(float b0_,float b1_,float b2_,float a1_,float a2_)
    :b0(b0_),b1(b1_),b2(b2_),a1(a1_),a2(a2_),w1(0),w2(0){}
  float apply(float x){
    float w0=x-a1*w1-a2*w2;
    float y=b0*w0+b1*w1+b2*w2;
    w2=w1; w1=w0; return y;
  }
};

// Cascaded pair of Biquads (4th-order)
struct BiquadCascade2 {
  Biquad s0, s1;
  BiquadCascade2(){}
  BiquadCascade2(const Biquad &a, const Biquad &b):s0(a),s1(b){}
  float apply(float x){ return s1.apply(s0.apply(x)); }
};

// ── EEG Filter Coefficients ─────────────────────────────────────────
// 4th-order Butterworth HPF(0.5Hz) at 250Hz  — from scipy butter(4,0.5/125,'high','sos')
static BiquadCascade2 makeEEG_HPF(){
  return BiquadCascade2(
    Biquad(0.9837151741f,-1.9674303483f,0.9837151741f,-1.9768913543f,0.9770474536f),
    Biquad(1.0000000000f,-2.0000000000f,1.0000000000f,-1.9902712417f,0.9904283975f)
  );
}

// 4th-order Butterworth LPF(45Hz) at 250Hz  — from scipy butter(4,45/125,'low','sos')
static BiquadCascade2 makeEEG_LPF(){
  return BiquadCascade2(
    Biquad(0.0333508484f,0.0667016969f,0.0333508484f,-0.4638241941f,0.0893535767f),
    Biquad(1.0000000000f,2.0000000000f,1.0000000000f,-0.6325354050f,0.4855945733f)
  );
}

// Notch 50Hz Q=30 at 250Hz — high-precision coefficients
static Biquad makeNotch50(){
  return Biquad(0.9794827610f,-0.6053536377f,0.9794827610f,
                               -0.6053536377f, 0.9589655220f);
}

// ════════════════════════════════════════════════════════════════════
//  RING BUFFER  (power-of-2 size)
// ════════════════════════════════════════════════════════════════════
template<int N>
struct RingBuf {
  float buf[N]; int head;
  RingBuf():head(0){memset(buf,0,sizeof(buf));}
  void push(float v){buf[head]=v;head=(head+1)&(N-1);}
  void copyTo(float *dst,int len=N)const{
    for(int i=0;i<len;i++) dst[i]=buf[(head+i)&(N-1)];
  }
  float newest()const{return buf[(head-1+N)&(N-1)];}
};

// ════════════════════════════════════════════════════════════════════
//  FFT  — Cooley-Tukey radix-2, in-place
// ════════════════════════════════════════════════════════════════════
static void dsp_fft(float *re,float *im,int n){
  for(int i=1,j=0;i<n;i++){
    int bit=n>>1; for(;j&bit;bit>>=1)j^=bit; j^=bit;
    if(i<j){
      float t; t=re[i];re[i]=re[j];re[j]=t;
                t=im[i];im[i]=im[j];im[j]=t;
    }
  }
  for(int len=2;len<=n;len<<=1){
    float ang=-2.0f*(float)M_PI/len;
    float wRe=cosf(ang),wIm=sinf(ang);
    for(int i=0;i<n;i+=len){
      float cRe=1.0f,cIm=0.0f;
      for(int j=0;j<len/2;j++){
        float uRe=re[i+j],uIm=im[i+j];
        float vRe=re[i+j+len/2]*cRe-im[i+j+len/2]*cIm;
        float vIm=re[i+j+len/2]*cIm+im[i+j+len/2]*cRe;
        re[i+j]=uRe+vRe; im[i+j]=uIm+vIm;
        re[i+j+len/2]=uRe-vRe; im[i+j+len/2]=uIm-vIm;
        float nc=cRe*wRe-cIm*wIm; cIm=cRe*wIm+cIm*wRe; cRe=nc;
      }
    }
  }
}

// Compute power spectrum from ring buffer (Hann windowed, DC removed)
static void dsp_power_spectrum(const RingBuf<DSP_FFT_N>&rb,float *ps){
  static float re[DSP_FFT_N],im[DSP_FFT_N];
  rb.copyTo(re);
  float mean=0; for(int i=0;i<DSP_FFT_N;i++) mean+=re[i]; mean/=DSP_FFT_N;
  for(int i=0;i<DSP_FFT_N;i++){
    float w=0.5f*(1.0f-cosf(2.0f*(float)M_PI*i/(DSP_FFT_N-1)));
    re[i]=(re[i]-mean)*w; im[i]=0;
  }
  dsp_fft(re,im,DSP_FFT_N);
  for(int i=0;i<DSP_FFT_N/2;i++) ps[i]=re[i]*re[i]+im[i]*im[i];
}

// Band power: sum ps[k] for fLo ≤ k·(FS/N) ≤ fHi
static float dsp_band_power(const float *ps,float fLo,float fHi){
  const float fRes=(float)DSP_FS/DSP_FFT_N;
  int b0=(int)(fLo/fRes+0.5f),b1=(int)(fHi/fRes+0.5f);
  b0=(b0<1)?1:b0; b1=(b1>=DSP_FFT_N/2)?DSP_FFT_N/2-1:b1;
  float s=0; for(int i=b0;i<=b1;i++) s+=ps[i]; return s;
}

// Mean power frequency — spectral centroid between fLo and fHi
static float dsp_mean_freq(const float *ps,float fLo,float fHi){
  const float fRes=(float)DSP_FS/DSP_FFT_N;
  int b0=(int)(fLo/fRes+0.5f),b1=(int)(fHi/fRes+0.5f);
  b1=(b1>=DSP_FFT_N/2)?DSP_FFT_N/2-1:b1;
  float num=0,den=0;
  for(int i=b0;i<=b1;i++){num+=i*fRes*ps[i];den+=ps[i];}
  return (den>0)?num/den:0;
}

static uint8_t toU8(float v,float scale=1.0f){
  int i=(int)(v*scale+0.5f); return (uint8_t)(i<0?0:i>255?255:i);
}

// Median + MAD for HRV outlier rejection
static float calcMedian(float *a,int n){
  float t[DSP_RR_MAX]; memcpy(t,a,n*sizeof(float));
  for(int i=1;i<n;i++){
    float k=t[i]; int j=i-1;
    while(j>=0&&t[j]>k){t[j+1]=t[j];j--;} t[j+1]=k;
  }
  return (n%2==0)?(t[n/2-1]+t[n/2])/2.0f:t[n/2];
}
static float calcMAD(float *a,int n,float med){
  float d[DSP_RR_MAX];
  for(int i=0;i<n;i++) d[i]=fabsf(a[i]-med);
  return calcMedian(d,n);
}

// ════════════════════════════════════════════════════════════════════
//  EEG PROCESSOR  — Best accuracy version
//  Improvements:
//    • 4th-order Butterworth filters (lower ripple, steeper roll-off)
//    • 4×64-sample sub-window FFT averaging (4× variance reduction)
//    • EMA smoothing: fast for main bands, heavy for gamma
//    • Full 256-pt FFT for gamma (0.98Hz/bin vs 3.9Hz/bin)
//    • Artifact gating on variance
// ════════════════════════════════════════════════════════════════════
struct EEGProc {
  BiquadCascade2 hpf;    // 4th-order HPF 0.5Hz
  Biquad         notch;  // 2nd-order Notch 50Hz Q=30
  BiquadCascade2 lpf;    // 4th-order LPF 45Hz

  RingBuf<DSP_FFT_N> ring;

  // Smoothed float accumulators (no uint8 rounding during accumulation)
  float sm_delta, sm_theta, sm_alpha, sm_beta, sm_gamma;
  bool  sm_initialized;

  // Public outputs
  uint8_t delta_pct, theta_pct, alpha_pct, beta_pct, gamma_pct;
  bool    artifact;

  // EMA smoothing factors
  // 0.25 = tracks changes while suppressing single-window spikes
  // 0.08 = heavy smoothing for gamma (very low power, high variance)
  static constexpr float SMOOTH       = 0.25f;
  static constexpr float SMOOTH_GAMMA = 0.08f;

  EEGProc():hpf(makeEEG_HPF()),notch(makeNotch50()),lpf(makeEEG_LPF()),
    sm_delta(20),sm_theta(20),sm_alpha(20),sm_beta(20),sm_gamma(20),
    sm_initialized(false),
    delta_pct(20),theta_pct(20),alpha_pct(20),beta_pct(20),gamma_pct(20),
    artifact(false){}

  float process(uint16_t raw){
    float x=(float)raw;
    x=hpf.apply(x); x=notch.apply(x); x=lpf.apply(x);
    ring.push(x);
    artifact=(fabsf(x)>1800.0f);
    return x;
  }

  void computeBands(){
    // Get signal buffer
    float buf[DSP_FFT_N]; ring.copyTo(buf,DSP_FFT_N);

    // Artifact gate: skip if variance too low (sensor off) or too high (motion)
    float mean=0; for(int i=0;i<DSP_FFT_N;i++) mean+=buf[i]; mean/=DSP_FFT_N;
    float var=0;  for(int i=0;i<DSP_FFT_N;i++) var+=(buf[i]-mean)*(buf[i]-mean);
    var/=DSP_FFT_N;
    if(var<10.0f||var>1e9f) return;

    // Sub-window averaging: 4 × 64-sample windows, zero-padded to 256
    // Gives 4× variance reduction vs single full-window FFT
    static float ps_sum[DSP_FFT_N/2];
    static float re_sub[DSP_FFT_N], im_sub[DSP_FFT_N];
    memset(ps_sum,0,sizeof(ps_sum));

    const int SUB_N  = 64;
    const int N_SUBS = 4;
    const int STEP   = (DSP_FFT_N-SUB_N)/(N_SUBS-1);

    for(int w=0;w<N_SUBS;w++){
      int off=w*STEP;
      memset(re_sub,0,sizeof(re_sub));
      memset(im_sub,0,sizeof(im_sub));
      float sm=0; for(int i=0;i<SUB_N;i++) sm+=buf[off+i]; sm/=SUB_N;
      for(int i=0;i<SUB_N;i++){
        float h=0.5f*(1.0f-cosf(2.0f*(float)M_PI*i/(SUB_N-1)));
        re_sub[i]=(buf[off+i]-sm)*h;
      }
      dsp_fft(re_sub,im_sub,DSP_FFT_N);
      for(int i=0;i<DSP_FFT_N/2;i++)
        ps_sum[i]+=re_sub[i]*re_sub[i]+im_sub[i]*im_sub[i];
    }
    static float ps_sub[DSP_FFT_N/2];
    for(int i=0;i<DSP_FFT_N/2;i++) ps_sub[i]=ps_sum[i]/N_SUBS;

    // Band powers from sub-window averaged spectrum
    float d=dsp_band_power(ps_sub, 0.5f, 4.0f);
    float t=dsp_band_power(ps_sub, 4.0f, 8.0f);
    float a=dsp_band_power(ps_sub, 8.0f,13.0f);
    float b=dsp_band_power(ps_sub,13.0f,30.0f);

    // Gamma: full 256-sample FFT for 0.98Hz/bin resolution
    static float ps_full[DSP_FFT_N/2];
    dsp_power_spectrum(ring,ps_full);
    float g      = dsp_band_power(ps_full,30.0f,45.0f);
    float g_tot  = dsp_band_power(ps_full, 0.5f,45.0f);
    if(g_tot>1e-6f) g=g*(d+t+a+b)/g_tot;

    float tot=d+t+a+b+g; if(tot<1e-6f) return;

    float nd=100.0f*d/tot, nt=100.0f*t/tot;
    float na=100.0f*a/tot, nb=100.0f*b/tot, ng=100.0f*g/tot;

    // EMA smoothing
    if(!sm_initialized){
      sm_delta=nd;sm_theta=nt;sm_alpha=na;sm_beta=nb;sm_gamma=ng;
      sm_initialized=true;
    } else {
      sm_delta=SMOOTH*nd+(1.0f-SMOOTH)*sm_delta;
      sm_theta=SMOOTH*nt+(1.0f-SMOOTH)*sm_theta;
      sm_alpha=SMOOTH*na+(1.0f-SMOOTH)*sm_alpha;
      sm_beta =SMOOTH*nb+(1.0f-SMOOTH)*sm_beta;
      sm_gamma=SMOOTH_GAMMA*ng+(1.0f-SMOOTH_GAMMA)*sm_gamma;
    }

    delta_pct=toU8(sm_delta); theta_pct=toU8(sm_theta);
    alpha_pct=toU8(sm_alpha); beta_pct =toU8(sm_beta);
    gamma_pct=toU8(sm_gamma);
  }
};

// ════════════════════════════════════════════════════════════════════
//  EMG PROCESSOR  — Best accuracy version
//  Improvements:
//    • HPF(20Hz) 1st-order causal (verified identical to Python sosfilt)
//    • Exact Notch50 biquad coefficients
//    • ZCR: (buf[i]>0)!=(prev>0) — exact C++ formula, no ambiguity
//    • MeanFreq: spectral centroid 20-125Hz
//    • Fatigue: initMF on first activation, % drop tracking
// ════════════════════════════════════════════════════════════════════
struct EMGProc {
  HPF    hpf;      // HPF fc=20Hz removes motion artefact
  Biquad notch;    // Notch 50Hz Q=30
  LPF    envLpf;   // LPF 10Hz smooths envelope

  RingBuf<DSP_FFT_N> ring;
  float    rmsBuf[DSP_RMS_WIN]; int rmsIdx;

  uint16_t rms;
  uint8_t  meanFreq, zcr, activated, fatiguePct;
  float    threshold, initMF; bool initMFSet;

  EMGProc():hpf(20.0f),notch(makeNotch50()),envLpf(10.0f),
    rmsIdx(0),rms(0),meanFreq(0),zcr(0),activated(0),fatiguePct(0),
    threshold(DSP_EMG_THRESH),initMF(0),initMFSet(false){
    memset(rmsBuf,0,sizeof(rmsBuf));
  }

  float process(uint16_t raw){
    float x=(float)raw;
    x=hpf.apply(x); x=notch.apply(x);
    ring.push(x);
    rmsBuf[rmsIdx]=x; rmsIdx=(rmsIdx+1)%DSP_RMS_WIN;
    return x;
  }

  void computeRMS(){
    float ssq=0;
    for(int i=0;i<DSP_RMS_WIN;i++) ssq+=rmsBuf[i]*rmsBuf[i];
    rms=(uint16_t)sqrtf(ssq/DSP_RMS_WIN);
    activated=(rms>(uint16_t)threshold)?1:0;

    // ZCR: exact formula — counts (positive)↔(non-positive) transitions
    // Matches Python: (buf[i]>0) != (prev>0)
    int zc=0; float prev=rmsBuf[0];
    for(int i=1;i<DSP_RMS_WIN;i++){
      if((rmsBuf[i]>0)!=(prev>0)) zc++;
      prev=rmsBuf[i];
    }
    zcr=(uint8_t)(zc>255?255:zc);
  }

  void computeFFT(){
    static float ps[DSP_FFT_N/2];
    dsp_power_spectrum(ring,ps);
    float mf=dsp_mean_freq(ps,20.0f,125.0f);
    meanFreq=toU8(mf);
    // Capture initial mean frequency when first activated
    if(!initMFSet&&rms>(uint16_t)threshold){initMF=mf;initMFSet=true;}
    if(initMFSet&&initMF>1.0f){
      float drop=1.0f-(mf/initMF);
      fatiguePct=toU8(drop*100.0f);
    }
  }

  void resetFatigue(){initMFSet=false;fatiguePct=0;}
};

// ════════════════════════════════════════════════════════════════════
//  HRV PROCESSOR  — Best accuracy version
//  Improvements:
//    • Bandpass: HPF(0.5Hz) + LPF(12Hz) — preserves peak shape
//    • Quadratic interpolation on peak apex (sub-sample timing)
//    • Requires 6 beats (not 4) before reporting — more stable
//    • MAD outlier rejection (10×MAD window)
//    • Sample std (÷n-1) for SDNN — clinical standard
//    • injectRR(rrMs): bypass peak detection for accuracy testing
// ════════════════════════════════════════════════════════════════════
struct HRVProc {
  HPF hpfPulse;   // HPF 0.5Hz removes DC baseline drift
  LPF lpfPulse;   // LPF 12Hz preserves PPG peak shape

  float    hist[4]; int histFull;  // 4-point derivative buffer
  float    rr[DSP_RR_MAX]; int rrCount, rrHead;

  float    sigMax, sigMin, noiseFloor;
  float    peakVal, peakTime, lastPeakTime;
  bool     waitingForFall;
  uint32_t sampleCount, lastGoodSample;

  float    varBuf[250]; int varIdx; bool varFull;

  uint8_t  bpm, rmssd, sdnn, pnn50, stressPct;
  bool     valid, contacted;
  float    dbgSwing, dbgThresh;

  // RR injection mode (used by accuracy test — bypasses peak detection)
  bool     rrInjectMode;

  HRVProc():
    hpfPulse(0.5f),lpfPulse(12.0f),histFull(0),
    rrCount(0),rrHead(0),
    sigMax(0),sigMin(4095),noiseFloor(0),
    waitingForFall(false),peakVal(0),peakTime(0),lastPeakTime(0),
    sampleCount(0),lastGoodSample(0),
    varIdx(0),varFull(false),
    bpm(0),rmssd(0),sdnn(0),pnn50(0),stressPct(0),
    valid(false),contacted(false),
    dbgSwing(0),dbgThresh(0),rrInjectMode(false){
    memset(rr,0,sizeof(rr));
    memset(varBuf,0,sizeof(varBuf));
    memset(hist,0,sizeof(hist));
  }

  // ── Direct RR injection (bypasses ADC+filters+peak detection) ─────
  // Used by Python test script to eliminate BLE timing jitter.
  void injectRR(float rrMs){
    if(rrMs<300.0f||rrMs>2000.0f) return;
    rr[rrHead]=rrMs; rrHead=(rrHead+1)%DSP_RR_MAX;
    if(rrCount<DSP_RR_MAX) rrCount++;
    contacted=true; varFull=true; dbgSwing=100.0f;
    rrInjectMode=true;
  }

  // ── Quadratic interpolation for sub-sample peak timing ────────────
  static float quadInterp(float y0,float y1,float y2){
    float denom=y0-2.0f*y1+y2;
    if(fabsf(denom)<1e-6f) return 0.0f;
    float offset=(y0-y2)/(2.0f*denom);
    if(offset> 0.5f) offset= 0.5f;
    if(offset<-0.5f) offset=-0.5f;
    return offset;
  }

  // ── Live PPG processing ────────────────────────────────────────────
  void process(uint16_t raw){
    if(rrInjectMode) return;
    sampleCount++;
    if(!hpfPulse.initialized){hpfPulse.prevIn=(float)raw;hpfPulse.initialized=true;}
    if(!lpfPulse.initialized){lpfPulse.state=(float)raw;lpfPulse.initialized=true;}
    float x=hpfPulse.apply((float)raw); x=lpfPulse.apply(x);

    // Contact detection: signal variance over 1-second window
    varBuf[varIdx]=x; varIdx=(varIdx+1)%250;
    if(varIdx==0) varFull=true;
    contacted=false;
    if(varFull||varIdx>50){
      int n=varFull?250:varIdx; float mn=0;
      for(int i=0;i<n;i++) mn+=varBuf[i]; mn/=n;
      float vr=0; for(int i=0;i<n;i++) vr+=(varBuf[i]-mn)*(varBuf[i]-mn); vr/=n;
      dbgSwing=sqrtf(vr); contacted=(dbgSwing>20.0f);
    }

    if(!contacted){
      sigMax=0;sigMin=4095;waitingForFall=false;
      lastPeakTime=0;noiseFloor=0;histFull=0;
      if(rrCount>0&&sampleCount%250==0){
        rrCount=0;rrHead=0;valid=false;
        bpm=0;rmssd=0;sdnn=0;pnn50=0;stressPct=0;
      }
      hist[3]=hist[2];hist[2]=hist[1];hist[1]=hist[0];hist[0]=x;
      return;
    }

    // Adaptive envelope tracking
    sigMax=sigMax*0.995f+x*0.005f; sigMin=sigMin*0.995f+x*0.005f;
    if(x>sigMax) sigMax=x; if(x<sigMin) sigMin=x;
    float amplitude=sigMax-sigMin;
    noiseFloor=noiseFloor*0.95f+amplitude*0.05f;
    float thresh=(amplitude<noiseFloor*0.5f)?
                  sigMin+noiseFloor*0.8f : sigMin+amplitude*0.65f;
    dbgThresh=thresh;

    // Update 4-point history for derivative computation
    hist[3]=hist[2];hist[2]=hist[1];hist[1]=hist[0];hist[0]=x;
    if(histFull<4) histFull++;

    float deriv=hist[0]-hist[1];

    if(deriv>0&&!waitingForFall&&x>thresh){
      waitingForFall=true; peakVal=x; peakTime=(float)sampleCount;
    }
    if(waitingForFall&&x>peakVal){
      peakVal=x; peakTime=(float)sampleCount;
    }

    if(deriv<0&&waitingForFall){
      waitingForFall=false;
      if(histFull>=3){
        // Quadratic interpolation: sub-sample peak timing
        float offset=quadInterp(hist[3],hist[2],hist[1]);
        float truePeakTime=(float)(sampleCount-1)+offset;
        uint32_t intPeak=(uint32_t)(truePeakTime+0.5f);
        uint32_t sinceLast=(lastGoodSample>0)?(intPeak-lastGoodSample):(DSP_PEAK_REFR+1);

        if(sinceLast>DSP_PEAK_REFR){
          float peakH=hist[1]-sigMin;
          if(peakH>=amplitude*0.30f){
            if(lastPeakTime>0.0f){
              float rrMs=(truePeakTime-lastPeakTime)*(1000.0f/DSP_FS);
              if(rrMs>=300.0f&&rrMs<=2000.0f){
                rr[rrHead]=rrMs; rrHead=(rrHead+1)%DSP_RR_MAX;
                if(rrCount<DSP_RR_MAX) rrCount++;
                lastGoodSample=intPeak;
              }
            }
            lastPeakTime=truePeakTime; lastGoodSample=intPeak;
          }
        }
      }
    }

    if(lastGoodSample>0&&(sampleCount-lastGoodSample)>1000){
      rrCount=0;rrHead=0;valid=false;
      bpm=0;rmssd=0;sdnn=0;pnn50=0;stressPct=0;
      lastGoodSample=0;lastPeakTime=0;
    }
  }

  // ── HRV metric computation (2Hz) ──────────────────────────────────
  void computeMetrics(){
    // Need at least 6 beats for stable metrics
    if(!contacted||rrCount<6){
      valid=false;
      if(!contacted){bpm=0;rmssd=0;sdnn=0;pnn50=0;stressPct=0;}
      return;
    }
    valid=true;

    int n=(rrCount<DSP_RR_MAX)?rrCount:DSP_RR_MAX;
    float buf[DSP_RR_MAX];
    for(int i=0;i<n;i++) buf[i]=rr[(rrHead-n+i+DSP_RR_MAX)%DSP_RR_MAX];

    // MAD outlier rejection (10×MAD window keeps real HRV variation)
    float med=calcMedian(buf,n); float mad=calcMAD(buf,n,med);
    float cb[DSP_RR_MAX]; int cn=0;
    for(int i=0;i<n;i++)
      if(fabsf(buf[i]-med)<10.0f*mad+5.0f) cb[cn++]=buf[i];
    if(cn<6){memcpy(cb,buf,n*sizeof(float));cn=n;}

    // BPM from mean RR
    float mean=0; for(int i=0;i<cn;i++) mean+=cb[i]; mean/=cn;
    uint8_t bc=(uint8_t)(60000.0f/mean+0.5f);
    if(bc<30||bc>220){valid=false;return;}
    bpm=bc;

    // SDNN: sample std (÷n-1) — clinical standard
    float var=0; for(int i=0;i<cn;i++) var+=(cb[i]-mean)*(cb[i]-mean);
    float sdnnF=(cn>1)?sqrtf(var/(cn-1)):0;
    sdnn=(uint8_t)(sdnnF+0.5f);

    // RMSSD + pNN50
    float ssq=0; int nn50=0;
    for(int i=1;i<cn;i++){
      float d=cb[i]-cb[i-1]; ssq+=d*d;
      if(fabsf(d)>50.0f) nn50++;
    }
    float rmssdF=(cn>1)?sqrtf(ssq/(cn-1)):0;
    rmssd=(uint8_t)(rmssdF+0.5f);
    pnn50=(uint8_t)(100.0f*nn50/((cn>1)?(cn-1):1)+0.5f);

    // Stress: clinical RMSSD mapping (15ms=high stress, 80ms=relaxed)
    float sf=100.0f*(1.0f-fminf(1.0f,fmaxf(0.0f,(rmssdF-15.0f)/65.0f)));
    stressPct=(uint8_t)(sf+0.5f);
  }
};

// ════════════════════════════════════════════════════════════════════
//  IMU PROCESSOR — Complementary filter (unchanged — accurate)
// ════════════════════════════════════════════════════════════════════
struct IMUProc {
  LPF axLpf,ayLpf,azLpf;
  float pitch,roll,yaw,accelMag; bool motion;
  IMUProc():axLpf(10.0f),ayLpf(10.0f),azLpf(10.0f),
    pitch(0),roll(0),yaw(0),accelMag(1.0f),motion(false){}
  void process(int16_t axR,int16_t ayR,int16_t azR,
               int16_t gxR,int16_t gyR,int16_t gzR){
    const float dt=1.0f/DSP_FS;
    float axG=axR/ACCEL_SCALE,ayG=ayR/ACCEL_SCALE,azG=azR/ACCEL_SCALE;
    float gxD=gxR/GYRO_SCALE,gyD=gyR/GYRO_SCALE,gzD=gzR/GYRO_SCALE;
    float ax=axLpf.apply(axG),ay=ayLpf.apply(ayG),az=azLpf.apply(azG);
    float pA=atan2f(ay,sqrtf(ax*ax+az*az))*(180.0f/(float)M_PI);
    float rA=atan2f(-ax,az)*(180.0f/(float)M_PI);
    pitch=CF_ALPHA*(pitch+gxD*dt)+(1.0f-CF_ALPHA)*pA;
    roll=CF_ALPHA*(roll+gyD*dt)+(1.0f-CF_ALPHA)*rA;
    yaw+=gzD*dt;
    if(yaw>180.0f)yaw-=360.0f; if(yaw<-180.0f)yaw+=360.0f;
    accelMag=sqrtf(axG*axG+ayG*ayG+azG*azG);
    motion=(accelMag>1.2f||accelMag<0.8f);
  }
};

// ════════════════════════════════════════════════════════════════════
//  DSP ENGINE — ties all processors together
// ════════════════════════════════════════════════════════════════════
struct DSPEngine {
  EEGProc eeg1, eeg2;
  EMGProc emg;
  HRVProc hrv;
  IMUProc imu;
  uint32_t sampleTick;
  DSPEngine():sampleTick(0){}

  // TIER 1 — every sample at 250Hz
  void processSample(const SensorPacket &pkt){
    sampleTick++;
    eeg1.process(pkt.eeg1);
    // eeg2: second channel (not fitted — skip to save CPU)
    emg.process(pkt.emg);
    hrv.process(pkt.pulse);
    imu.process(pkt.ax,pkt.ay,pkt.az,pkt.gx,pkt.gy,pkt.gz);

    // TIER 2 — every 5 samples (50Hz)
    if(sampleTick%5==0) emg.computeRMS();

    // TIER 3 — every 125 samples (2Hz)
    if(sampleTick%125==0){
      eeg1.computeBands();
      emg.computeFFT();
      hrv.computeMetrics();
    }
  }

  // Build the 32-byte ProcessedPacket for BLE transmission
  void fillPacket(ProcessedPacket &o)const{
    // EEG band powers (%)
    o.e1_delta=eeg1.delta_pct; o.e1_theta=eeg1.theta_pct;
    o.e1_alpha=eeg1.alpha_pct; o.e1_beta=eeg1.beta_pct;
    o.e1_gamma=eeg1.gamma_pct;
    o.e2_delta=0;o.e2_theta=0;o.e2_alpha=0;o.e2_beta=0;o.e2_gamma=0;

    // Cognitive indices (×10 for 0.1 resolution)
    float a1=eeg1.alpha_pct,b1=eeg1.beta_pct,t1=eeg1.theta_pct;
    o.focus_x10 =toU8((b1>0&&t1>0)?(b1/t1):0,10.0f);  // beta/theta
    o.relax_x10 =toU8((b1>0)?(a1/b1):0,10.0f);         // alpha/beta
    o.drowsy_x10=toU8((a1>0)?(t1/a1):0,10.0f);         // theta/alpha

    // EMG metrics
    o.emg_rms    =emg.rms;
    o.emg_mf     =emg.meanFreq;
    o.emg_zcr    =emg.zcr;
    o.emg_active =emg.activated;
    o.emg_fatigue=emg.fatiguePct;

    // HRV metrics
    o.bpm       =hrv.bpm;
    o.rmssd     =hrv.rmssd;
    o.sdnn      =hrv.sdnn;
    o.pnn50     =hrv.pnn50;
    o.hrv_stress=hrv.stressPct;

    // IMU orientation (×10 = 0.1° resolution)
    o.pitch_x10=(int16_t)(imu.pitch*10.0f);
    o.roll_x10 =(int16_t)(imu.roll*10.0f);
    o.yaw_x10  =(int16_t)(imu.yaw*10.0f);
    o.accel_mag_x10=toU8(imu.accelMag,10.0f);

    // Status flags
    o.flags=0;
    if(eeg1.artifact) o.flags|=0x01; // EEG artefact detected
    if(emg.activated) o.flags|=0x02; // EMG contraction active
    if(imu.motion)    o.flags|=0x04; // Motion detected
    if(hrv.valid)     o.flags|=0x08; // HRV metrics valid
    if(hrv.contacted) o.flags|=0x10; // Pulse sensor contacted
  }
};

// Global DSP engine instance
DSPEngine dsp;

#endif // DSP_H