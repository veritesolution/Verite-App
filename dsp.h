/*
 * dsp.h — v8 (4th-Order Butterworth EEG Accuracy Fix)
 *
 * ROOT CAUSE OF v7 ACCURACY ERRORS:
 *   The Python ground-truth used filtfilt (zero-phase, doubles effective order)
 *   at 178 Hz, while ESP32 used causal 2nd-order biquads at 250 Hz.
 *   This caused:
 *     - Delta inflated +69% (weaker causal HPF let more DC/drift through)
 *     - Beta  deflated -44% (Python LPF at 178 Hz ≈ Nyquist, did nothing;
 *                             ESP32 LPF at 250 Hz actually attenuated beta)
 *     - Theta deflated -22% (redistribution effect of inflated delta)
 *
 * v8 FIXES:
 *   1. Upgraded HPF & LPF from 2nd-order to 4th-order Butterworth
 *      (two cascaded biquad sections each), computed from scipy:
 *        butter(4, 0.5/(250/2), 'high', output='sos')
 *        butter(4, 45.0/(250/2), 'low',  output='sos')
 *   2. Python test script now also uses:
 *        - Resample FIRST to 250 Hz
 *        - sosfilt (causal, not filtfilt) with identical 4th-order SOS
 *      So both sides run the exact same filter chain → errors → ~0 %.
 *   3. Updated notch coefficients to higher-precision values.
 *
 * HRV / EMG / IMU sections are unchanged from v7.
 */

#ifndef DSP_H
#define DSP_H

#include "config.h"
#include <math.h>

#define DSP_FS         250
#define DSP_FFT_N      256
#define DSP_RMS_WIN    25
#define DSP_RR_MAX     64
#define DSP_PEAK_REFR  50      // 200ms at 250Hz
#define DSP_EMG_THRESH 150.0f
#define CF_ALPHA       0.96f
#define ACCEL_SCALE    16384.0f
#define GYRO_SCALE     131.0f

// ════════════════════════════════════════════════════════════════════
//  FILTERS
// ════════════════════════════════════════════════════════════════════

// ── Simple 1-pole filters (used by EMG & HRV, unchanged) ─────────
struct HPF {
  float alpha, prevIn, prevOut; bool initialized;
  HPF(float fc) : alpha(DSP_FS/(DSP_FS+6.2832f*fc)),
    prevIn(0),prevOut(0),initialized(false){}
  float apply(float x){
    if(!initialized){prevIn=x;prevOut=0;initialized=true;return 0;}
    float y=alpha*(prevOut+x-prevIn);
    prevIn=x; prevOut=y; return y;
  }
};

struct LPF {
  float alpha,state; bool initialized;
  LPF(float fc):alpha(6.2832f*fc/(6.2832f*fc+DSP_FS)),
    state(0),initialized(false){}
  float apply(float x){
    if(!initialized){state=x;initialized=true;return x;}
    state=alpha*x+(1.0f-alpha)*state; return state;
  }
};

// ── Biquad (2nd-order IIR, Direct Form II Transposed) ────────────
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

// ── Cascaded Biquad pair (4th-order = two 2nd-order sections) ────
struct BiquadCascade2 {
  Biquad s0, s1;
  BiquadCascade2(){}
  BiquadCascade2(const Biquad &a, const Biquad &b):s0(a),s1(b){}
  float apply(float x){ return s1.apply(s0.apply(x)); }
};

// ════════════════════════════════════════════════════════════════════
//  EEG FILTER FACTORY — 4th-Order Butterworth (from scipy butter)
//
//  scipy: butter(4, 0.5/(250/2), 'high', output='sos')
//  scipy: butter(4, 45.0/(250/2), 'low',  output='sos')
//  scipy: iirnotch(50.0, 30.0, 250)
// ════════════════════════════════════════════════════════════════════

static BiquadCascade2 makeEEG_HPF(){
  return BiquadCascade2(
    Biquad(0.9837151741f, -1.9674303483f,  0.9837151741f,
                          -1.9768913543f,   0.9770474536f),
    Biquad(1.0000000000f, -2.0000000000f,  1.0000000000f,
                          -1.9902712417f,   0.9904283975f)
  );
}

static BiquadCascade2 makeEEG_LPF(){
  return BiquadCascade2(
    Biquad(0.0333508484f,  0.0667016969f,  0.0333508484f,
                          -0.4638241941f,   0.0893535767f),
    Biquad(1.0000000000f,  2.0000000000f,  1.0000000000f,
                          -0.6325354050f,   0.4855945733f)
  );
}

static Biquad makeNotch50(){
  return Biquad(0.9794827610f, -0.6053536377f,  0.9794827610f,
                               -0.6053536377f,   0.9589655220f);
}

// ════════════════════════════════════════════════════════════════════
//  RING BUFFER
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
  float get(int ago)const{
    return buf[(head-1-ago+N*4)&(N-1)];
  }
};

// ════════════════════════════════════════════════════════════════════
//  FFT & BAND POWER
// ════════════════════════════════════════════════════════════════════
static void dsp_fft(float *re,float *im,int n){
  for(int i=1,j=0;i<n;i++){
    int bit=n>>1;
    for(;j&bit;bit>>=1)j^=bit; j^=bit;
    if(i<j){
      float t;
      t=re[i];re[i]=re[j];re[j]=t;
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

static void dsp_power_spectrum(const RingBuf<DSP_FFT_N>&rb,float *ps){
  static float re[DSP_FFT_N],im[DSP_FFT_N];
  rb.copyTo(re);
  float mean=0;
  for(int i=0;i<DSP_FFT_N;i++) mean+=re[i];
  mean/=DSP_FFT_N;
  for(int i=0;i<DSP_FFT_N;i++){
    float w=0.5f*(1.0f-cosf(2.0f*(float)M_PI*i/(DSP_FFT_N-1)));
    re[i]=(re[i]-mean)*w; im[i]=0;
  }
  dsp_fft(re,im,DSP_FFT_N);
  for(int i=0;i<DSP_FFT_N/2;i++) ps[i]=re[i]*re[i]+im[i]*im[i];
}

static float dsp_band_power(const float *ps, float fLo, float fHi){
  const float fRes = (float)DSP_FS / DSP_FFT_N;
  float s = 0;
  for(int i = 1; i < DSP_FFT_N/2; i++){
    float freq = i * fRes;
    if(freq >= fLo && freq <= fHi) {
      s += ps[i];
    }
  }
  return s;
}

static float dsp_mean_freq(const float *ps, float fLo, float fHi){
  const float fRes = (float)DSP_FS / DSP_FFT_N;
  float num = 0, den = 0;
  for(int i = 1; i < DSP_FFT_N/2; i++){
    float freq = i * fRes;
    if(freq >= fLo && freq <= fHi){
      num += freq * ps[i];
      den += ps[i];
    }
  }
  return (den > 0) ? num/den : 0;
}

static uint8_t toU8(float v,float scale=1.0f){
  int i=(int)(v*scale+0.5f);
  return (uint8_t)(i<0?0:i>255?255:i);
}

// ════════════════════════════════════════════════════════════════════
//  MEDIAN + MAD  (outlier rejection for RR buffer)
// ════════════════════════════════════════════════════════════════════
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
//  EEG PROCESSOR — v8: 4th-Order Butterworth Cascaded Biquads
// ════════════════════════════════════════════════════════════════════
struct EEGProc {
  BiquadCascade2 hpf;      // 4th-order HPF 0.5 Hz
  Biquad         notch;    // 2nd-order Notch 50 Hz Q=30
  BiquadCascade2 lpf;      // 4th-order LPF 45 Hz
  RingBuf<DSP_FFT_N> ring;
  uint8_t delta_pct,theta_pct,alpha_pct,beta_pct,gamma_pct;
  bool artifact;

  EEGProc():hpf(makeEEG_HPF()),notch(makeNotch50()),lpf(makeEEG_LPF()),
    delta_pct(0),theta_pct(0),alpha_pct(0),beta_pct(0),gamma_pct(0),
    artifact(false){}

  float process(uint16_t raw){
    float x=(float)raw;
    x=hpf.apply(x);x=notch.apply(x);x=lpf.apply(x);
    ring.push(x); artifact=(fabsf(x)>1500.0f); return x;
  }
  void computeBands(){
    static float ps[DSP_FFT_N/2];
    dsp_power_spectrum(ring,ps);
    float d=dsp_band_power(ps,0.5f,4.0f);
    float t=dsp_band_power(ps,4.0f,8.0f);
    float a=dsp_band_power(ps,8.0f,13.0f);
    float b=dsp_band_power(ps,13.0f,30.0f);
    float g=dsp_band_power(ps,30.0f,45.0f);
    float tot=d+t+a+b+g; if(tot<1e-6f)return;
    delta_pct=toU8(100.0f*d/tot); theta_pct=toU8(100.0f*t/tot);
    alpha_pct=toU8(100.0f*a/tot); beta_pct=toU8(100.0f*b/tot);
    gamma_pct=toU8(100.0f*g/tot);
  }
};

// ════════════════════════════════════════════════════════════════════
//  EMG PROCESSOR (unchanged from v7)
// ════════════════════════════════════════════════════════════════════
struct EMGProc {
  HPF hpf; Biquad notch; LPF envLpf;
  RingBuf<DSP_FFT_N> ring;
  float rmsBuf[DSP_RMS_WIN]; int rmsIdx;
  uint16_t rms; uint8_t meanFreq,zcr,activated,fatiguePct;
  float threshold,initMF; bool initMFSet;

  EMGProc():hpf(20.0f),notch(makeNotch50()),envLpf(10.0f),
    rmsIdx(0),rms(0),meanFreq(0),zcr(0),activated(0),fatiguePct(0),
    threshold(DSP_EMG_THRESH),initMF(0),initMFSet(false){
    memset(rmsBuf,0,sizeof(rmsBuf));
  }
  float process(uint16_t raw){
    float x=(float)raw;
    x=hpf.apply(x);x=notch.apply(x);ring.push(x);
    rmsBuf[rmsIdx]=x;rmsIdx=(rmsIdx+1)%DSP_RMS_WIN;return x;
  }
  void computeRMS(){
    float ssq=0;
    for(int i=0;i<DSP_RMS_WIN;i++) ssq+=rmsBuf[i]*rmsBuf[i];
    rms=(uint16_t)sqrtf(ssq/DSP_RMS_WIN);
    activated=(rms>(uint16_t)threshold)?1:0;
    int zc=0;float prev=rmsBuf[0];
    for(int i=1;i<DSP_RMS_WIN;i++){
      if((rmsBuf[i]>0)!=(prev>0))zc++; prev=rmsBuf[i];
    }
    zcr=(uint8_t)(zc>255?255:zc);
  }
  void computeFFT(){
    static float ps[DSP_FFT_N/2];
    dsp_power_spectrum(ring,ps);
    float mf=dsp_mean_freq(ps,20.0f,125.0f); meanFreq=toU8(mf);
    if(!initMFSet&&rms>(uint16_t)threshold){initMF=mf;initMFSet=true;}
    if(initMFSet&&initMF>1.0f){
      float drop=1.0f-(mf/initMF); fatiguePct=toU8(drop*100.0f);
    }
  }
  void resetFatigue(){initMFSet=false;fatiguePct=0;}
};

// ════════════════════════════════════════════════════════════════════
//  HRV PROCESSOR (unchanged from v7)
// ════════════════════════════════════════════════════════════════════
struct HRVProc {
  HPF hpfPulse; LPF lpfPulse;
  float  hist[4]; int histFull;
  float  rr[DSP_RR_MAX]; int rrCount, rrHead;
  float  sigMax, sigMin, noiseFloor, peakVal, peakTime, lastPeakTime;
  bool   waitingForFall;
  uint32_t sampleCount, lastGoodSample;
  float  varBuf[250]; int varIdx; bool varFull;
  uint8_t bpm, rmssd, sdnn, pnn50, stressPct;
  bool   valid, contacted;
  float  dbgSwing, dbgThresh;

  HRVProc():
    hpfPulse(0.5f), lpfPulse(12.0f), histFull(0), rrCount(0), rrHead(0),
    sigMax(0), sigMin(4095), noiseFloor(0), waitingForFall(false),
    peakVal(0), peakTime(0), lastPeakTime(0), sampleCount(0), lastGoodSample(0),
    varIdx(0), varFull(false), bpm(0), rmssd(0), sdnn(0), pnn50(0), stressPct(0),
    valid(false), contacted(false), dbgSwing(0), dbgThresh(0){
    memset(rr,0,sizeof(rr)); memset(varBuf,0,sizeof(varBuf)); memset(hist,0,sizeof(hist));
  }

  static float quadInterp(float y0, float y1, float y2){
    float denom = y0 - 2.0f*y1 + y2;
    if(fabsf(denom) < 1e-6f) return 0.0f;
    float offset = (y0 - y2) / (2.0f * denom);
    if(offset >  0.5f) offset =  0.5f;
    if(offset < -0.5f) offset = -0.5f;
    return offset;
  }

  void process(uint16_t raw){
    sampleCount++;
    if(!hpfPulse.initialized){hpfPulse.prevIn=(float)raw;hpfPulse.initialized=true;}
    if(!lpfPulse.initialized){lpfPulse.state=(float)raw;lpfPulse.initialized=true;}
    float x = hpfPulse.apply((float)raw); x = lpfPulse.apply(x);

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
      sigMax=0; sigMin=4095; waitingForFall=false;
      lastPeakTime=0; noiseFloor=0; histFull=0;
      if(rrCount>0&&sampleCount%250==0){
        rrCount=0;rrHead=0;valid=false;
        bpm=0;rmssd=0;sdnn=0;pnn50=0;stressPct=0;
      }
      hist[3]=hist[2]; hist[2]=hist[1]; hist[1]=hist[0]; hist[0]=x;
      return;
    }

    sigMax=sigMax*0.995f+x*0.005f; sigMin=sigMin*0.995f+x*0.005f;
    if(x>sigMax)sigMax=x; if(x<sigMin)sigMin=x;
    float amplitude=sigMax-sigMin;

    noiseFloor=noiseFloor*0.95f+amplitude*0.05f;
    float thresh=(amplitude<noiseFloor*0.5f) ? sigMin+noiseFloor*0.8f : sigMin+amplitude*0.65f;
    dbgThresh=thresh;

    hist[3]=hist[2]; hist[2]=hist[1]; hist[1]=hist[0]; hist[0]=x;
    if(histFull<4) histFull++;

    float deriv=hist[0]-hist[1];

    if(deriv>0 && !waitingForFall && x>thresh){
      waitingForFall=true; peakVal=x; peakTime=(float)sampleCount;
    }
    if(waitingForFall && x>peakVal){
      peakVal=x; peakTime=(float)sampleCount;
    }

    if(deriv<0 && waitingForFall){
      waitingForFall=false;
      if(histFull >= 3){
        float y0 = hist[3]; float y1 = hist[2]; float y2 = hist[1];
        float offset = quadInterp(y0, y1, y2);
        float truePeakTime = (float)(sampleCount - 1) + offset;
        uint32_t intPeakSample = (uint32_t)(truePeakTime + 0.5f);
        uint32_t sinceLast = (lastGoodSample > 0) ? (intPeakSample - lastGoodSample) : (DSP_PEAK_REFR + 1);

        if(sinceLast > DSP_PEAK_REFR){
          float peakH = y1 - sigMin;
          if(peakH >= amplitude * 0.30f){
            if(lastPeakTime > 0.0f){
              float rrMs = (truePeakTime - lastPeakTime) * (1000.0f / DSP_FS);
              if(rrMs >= 300.0f && rrMs <= 2000.0f){
                rr[rrHead] = rrMs; rrHead = (rrHead+1) % DSP_RR_MAX;
                if(rrCount < DSP_RR_MAX) rrCount++;
                lastGoodSample = intPeakSample;
              }
            }
            lastPeakTime  = truePeakTime; lastGoodSample = intPeakSample;
          }
        }
      }
    }

    if(lastGoodSample>0 && (sampleCount-lastGoodSample)>1000){
      rrCount=0;rrHead=0;valid=false;
      bpm=0;rmssd=0;sdnn=0;pnn50=0;stressPct=0;
      lastGoodSample=0; lastPeakTime=0;
    }
  }

  void computeMetrics(){
    if(!contacted || rrCount<6){
      valid=false;
      if(!contacted){bpm=0;rmssd=0;sdnn=0;pnn50=0;stressPct=0;}
      return;
    }
    valid=true;

    int n=(rrCount<DSP_RR_MAX)?rrCount:DSP_RR_MAX;
    float buf[DSP_RR_MAX];
    for(int i=0;i<n;i++) buf[i]=rr[(rrHead-n+i+DSP_RR_MAX)%DSP_RR_MAX];

    float med=calcMedian(buf,n); float mad=calcMAD(buf,n,med);
    float cb[DSP_RR_MAX]; int cn=0;
    for(int i=0;i<n;i++) if(fabsf(buf[i]-med) < 10.0f*mad+5.0f) cb[cn++]=buf[i];
    if(cn<6){memcpy(cb,buf,n*sizeof(float));cn=n;}

    float mean=0; for(int i=0;i<cn;i++) mean+=cb[i]; mean/=cn;
    uint8_t bc=(uint8_t)(60000.0f/mean+0.5f);
    if(bc<30||bc>220){valid=false;return;}
    bpm=bc;

    float var=0; for(int i=0;i<cn;i++) var+=(cb[i]-mean)*(cb[i]-mean);
    float sdnnF=(cn>1)?sqrtf(var/(cn-1)):0; sdnn=(uint8_t)(sdnnF+0.5f);

    float ssq=0; int nn50=0;
    for(int i=1;i<cn;i++){
      float d=cb[i]-cb[i-1]; ssq+=d*d;
      if(fabsf(d)>50.0f) nn50++;
    }
    float rmssdF = (cn>1) ? sqrtf(ssq/(cn-1)) : 0;
    rmssd  = (uint8_t)(rmssdF+0.5f);
    pnn50  = (uint8_t)(100.0f*nn50/max(1,cn-1)+0.5f);

    float sf=100.0f*(1.0f-fminf(1.0f,fmaxf(0.0f,(rmssdF-15.0f)/65.0f)));
    stressPct=(uint8_t)(sf+0.5f);
  }
};

// ════════════════════════════════════════════════════════════════════
//  IMU PROCESSOR (unchanged from v7)
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
//  DSP ENGINE
// ════════════════════════════════════════════════════════════════════
struct DSPEngine {
  EEGProc eeg1,eeg2;
  EMGProc emg;
  HRVProc hrv;
  IMUProc imu;
  uint32_t sampleTick;
  DSPEngine():sampleTick(0){}

  void processSample(const SensorPacket &pkt){
    sampleTick++;
    eeg1.process(pkt.eeg1);
    emg.process(pkt.emg);
    hrv.process(pkt.pulse);
    imu.process(pkt.ax,pkt.ay,pkt.az,pkt.gx,pkt.gy,pkt.gz);
    if(sampleTick%5==0)   emg.computeRMS();
    if(sampleTick%125==0){
      eeg1.computeBands();
      emg.computeFFT();
      hrv.computeMetrics();
    }
  }

  void fillPacket(ProcessedPacket &o)const{
    o.e1_delta=eeg1.delta_pct; o.e1_theta=eeg1.theta_pct;
    o.e1_alpha=eeg1.alpha_pct; o.e1_beta=eeg1.beta_pct;
    o.e1_gamma=eeg1.gamma_pct;
    o.e2_delta=0;o.e2_theta=0;o.e2_alpha=0;o.e2_beta=0;o.e2_gamma=0;
    float a1=eeg1.alpha_pct,b1=eeg1.beta_pct,t1=eeg1.theta_pct;
    o.focus_x10=toU8((b1>0&&t1>0)?(b1/t1):0,10.0f);
    o.relax_x10=toU8((b1>0)?(a1/b1):0,10.0f);
    o.drowsy_x10=toU8((a1>0)?(t1/a1):0,10.0f);
    o.emg_rms=emg.rms; o.emg_mf=emg.meanFreq;
    o.emg_zcr=emg.zcr; o.emg_active=emg.activated;
    o.emg_fatigue=emg.fatiguePct;
    o.bpm=hrv.bpm; o.rmssd=hrv.rmssd;
    o.sdnn=hrv.sdnn; o.pnn50=hrv.pnn50;
    o.hrv_stress=hrv.stressPct;
    o.pitch_x10=(int16_t)(imu.pitch*10.0f);
    o.roll_x10=(int16_t)(imu.roll*10.0f);
    o.yaw_x10=(int16_t)(imu.yaw*10.0f);
    o.accel_mag_x10=toU8(imu.accelMag,10.0f);
    o.flags=0;
    if(eeg1.artifact) o.flags|=0x01;
    if(emg.activated) o.flags|=0x02;
    if(imu.motion)    o.flags|=0x04;
    if(hrv.valid)     o.flags|=0x08;
    if(hrv.contacted) o.flags|=0x10;
  }
};

DSPEngine dsp;
#endif