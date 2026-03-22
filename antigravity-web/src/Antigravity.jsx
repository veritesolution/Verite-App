import React, { useEffect, useRef, useState, useMemo } from 'react';
import { 
  Activity, 
  Battery, 
  Bluetooth, 
  Brain, 
  Heart, 
  Info, 
  Layers, 
  Settings, 
  Zap,
  ChevronRight,
  ChevronDown,
  Wind,
  Droplets,
  Eye,
  EyeOff,
  Maximize,
  Minimize
} from 'lucide-react';
import { 
  LineChart, 
  Line, 
  ResponsiveContainer, 
  YAxis, 
  XAxis, 
  Area, 
  AreaChart 
} from 'recharts';
import * as d3 from 'd3';

// --- CONSTANTS ---
const PARTICLE_COUNT = 100;
const WINDOW_SIZE = 30;
const GRAVITY_CONSTANT = 0.5;
const FRICTION = 0.98;

// --- UTILS ---
const clamp = (val, min, max) => Math.min(Math.max(val, min), max);
const lerp = (a, b, t) => a + (b - a) * t;

const Antigravity = () => {
  // --- STATE ---
  const [stress, setStress] = useState(0.5);
  const [heartRate, setHeartRate] = useState(72);
  const [sdnn, setSdnn] = useState(45);
  const [rmssd, setRmssd] = useState(38);
  const [ibiBuffer, setIbiBuffer] = useState(new Array(30).fill(830));
  const [isDiveMode, setIsDiveMode] = useState(false);
  const [showOverlays, setShowOverlays] = useState(true);
  const [showTrails, setShowTrails] = useState(true);
  const [showConnections, setShowConnections] = useState(true);
  const [theme, setTheme] = useState('space'); // 'space', 'ocean', 'forest'
  const [isControlsExpanded, setIsControlsExpanded] = useState(false);

  // Refs for animation
  const canvasRef = useRef(null);
  const requestRef = useRef();
  const particlesRef = useRef([]);
  const lastTimeRef = useRef();
  const themeRef = useRef(theme);
  const stressRef = useRef(stress);

  // Sync refs for the animation loop
  useEffect(() => { themeRef.current = theme; }, [theme]);
  useEffect(() => { stressRef.current = stress; }, [stress]);

  // --- DATA BRIDGE ---
  useEffect(() => {
    // Android JavascriptInterface injection point
    window.updateHRV = (data) => {
      if (data.stress !== undefined) setStress(data.stress);
      if (data.hr !== undefined) setHeartRate(data.hr);
      if (data.sdnn !== undefined) setSdnn(data.sdnn);
      if (data.rmssd !== undefined) setRmssd(data.rmssd);
      if (data.ibis !== undefined) setIbiBuffer(data.ibis);
    };

    return () => {
      window.updateHRV = null;
    };
  }, []);

  // --- THEME COLORS ---
  const getThemeColors = (s) => {
    if (isDiveMode) {
      return {
        primary: '#0066FF',
        secondary: '#00FFB2',
        background: '#000A1A',
        glow: 'rgba(0, 102, 255, 0.3)',
        particles: ['#0066FF', '#00FF99', '#003366', '#FFFFFF']
      };
    }

    if (s > 0.6) { // Stressed
      return {
        primary: '#FF3B3B',
        secondary: '#FF8C42',
        background: '#0A0000',
        glow: 'rgba(255, 60, 60, 0.3)',
        particles: ['#FF3B3B', '#FF8C42', '#FFCC00', '#FF5500']
      };
    } else if (s > 0.3) { // Moderate
      return {
        primary: '#FFB800',
        secondary: '#8B8B8B',
        background: '#0A0A0A',
        glow: 'rgba(255, 184, 0, 0.2)',
        particles: ['#FFB800', '#FFFFFF', '#CCCCCC', '#FFD700']
      };
    } else { // Relaxed
      return {
        primary: '#00FFB2',
        secondary: '#00C8FF',
        background: '#000A0A',
        glow: 'rgba(0, 255, 178, 0.3)',
        particles: ['#00FFB2', '#00C8FF', '#7B61FF', '#FFFFFF']
      };
    }
  };

  const colors = useMemo(() => getThemeColors(stress), [stress, isDiveMode]);

  // --- PARTICLE SYSTEM ---
  const initParticles = (width, height) => {
    const particles = [];
    for (let i = 0; i < PARTICLE_COUNT; i++) {
      particles.push({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - 0.5) * 2,
        vy: (Math.random() - 0.5) * 2,
        size: Math.random() * 20 + 5,
        targetSize: Math.random() * 20 + 5,
        opacity: Math.random() * 0.5 + 0.2,
        colorIndex: Math.floor(Math.random() * 4),
        layer: Math.floor(Math.random() * 3), // 0: bg, 1: mid, 2: fg
        pulse: Math.random() * Math.PI,
        history: [] // for trails
      });
    }
    return particles;
  };

  const animate = (time) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const { width, height } = canvas;

    if (!lastTimeRef.current) lastTimeRef.current = time;
    const deltaTime = (time - lastTimeRef.current) / 16.67; // Normalize to 60fps
    lastTimeRef.current = time;

    // Clear background
    ctx.fillStyle = colors.background;
    ctx.fillRect(0, 0, width, height);

    // Gravity calculation
    // stress 1.0 -> max down, stress 0.0 -> max up
    const gravityForce = (stressRef.current - 0.5) * 0.2;

    const particles = particlesRef.current;

    // Draw grid/background aura (simple aurora effect if very relaxed)
    if (stressRef.current < 0.3) {
      const gradient = ctx.createLinearGradient(0, 0, 0, height);
      gradient.addColorStop(0, colors.glow.replace('0.3', '0.05'));
      gradient.addColorStop(0.5, 'transparent');
      gradient.addColorStop(1, 'transparent');
      ctx.fillStyle = gradient;
      ctx.fillRect(0, 0, width, height);
    }

    // Update and draw particles
    particles.forEach((p, i) => {
      // Physics
      const lift = p.layer === 0 ? 0.5 : p.layer === 1 ? 1 : 1.5; // parallax speed
      p.vy += gravityForce * lift;
      
      // Brownian motion
      p.vx += (Math.random() - 0.5) * 0.1;
      p.vy += (Math.random() - 0.5) * 0.1;

      p.vx *= FRICTION;
      p.vy *= FRICTION;

      p.x += p.vx * deltaTime;
      p.y += p.vy * deltaTime;

      // Wrap boundaries
      if (p.y < -50) p.y = height + 50;
      if (p.y > height + 50) p.y = -50;
      if (p.x < -50) p.x = width + 50;
      if (p.x > width + 50) p.x = -50;

      // Pulse size
      p.pulse += 0.03 * deltaTime;
      const pulseFactor = 1 + Math.sin(p.pulse) * 0.2;
      const currentSize = p.size * pulseFactor;

      // Store history for trails
      if (showTrails) {
        p.history.push({ x: p.x, y: p.y });
        if (p.history.length > 10) p.history.shift();
      } else {
        p.history = [];
      }

      // Draw trails
      if (p.history.length > 1) {
        ctx.beginPath();
        ctx.moveTo(p.history[0].x, p.history[0].y);
        for (let h = 1; h < p.history.length; h++) {
          ctx.lineTo(p.history[h].x, p.history[h].y);
        }
        ctx.strokeStyle = colors.particles[p.colorIndex] + '22'; // low opacity
        ctx.lineWidth = currentSize * 0.5;
        ctx.lineCap = 'round';
        ctx.stroke();
      }

      // Draw particle
      ctx.beginPath();
      ctx.arc(p.x, p.y, currentSize, 0, Math.PI * 2);
      ctx.fillStyle = colors.particles[p.colorIndex] + Math.floor(p.opacity * 255).toString(16).padStart(2, '0');
      
      // Blur based on layer
      if (p.layer === 0) {
        ctx.shadowBlur = 15;
        ctx.shadowColor = colors.particles[p.colorIndex];
      } else {
        ctx.shadowBlur = 0;
      }
      
      ctx.fill();
    });

    // Connections (neural net look)
    if (showConnections) {
      ctx.beginPath();
      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const dx = particles[i].x - particles[j].x;
          const dy = particles[i].y - particles[j].y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          
          if (dist < 100) {
            ctx.moveTo(particles[i].x, particles[i].y);
            ctx.lineTo(particles[j].x, particles[j].y);
            const alpha = Math.floor((1 - dist / 100) * 30).toString(16).padStart(2, '0');
            ctx.strokeStyle = colors.primary + alpha;
            ctx.lineWidth = 1;
            ctx.stroke();
          }
        }
      }
    }

    // Center silhouette (abstract head)
    const centerX = width / 2;
    const centerY = height / 2 + (0.5 - stressRef.current) * 50; // floats up as stress goes down
    
    ctx.save();
    ctx.translate(centerX, centerY);
    
    // Jaggedness based on stress
    const jitter = stressRef.current > 0.6 ? (Math.random() - 0.5) * 5 : 0;
    
    ctx.beginPath();
    // Head outline (circle-ish)
    for (let angle = 0; angle < Math.PI * 2; angle += 0.1) {
      const r = 40 + Math.sin(angle * 5 + time / 500) * 2 + (stressRef.current > 0.6 ? Math.random() * 3 : 0);
      const x = r * Math.cos(angle) + jitter;
      const y = r * Math.sin(angle) + jitter;
      if (angle === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.closePath();
    ctx.strokeStyle = colors.primary;
    ctx.lineWidth = 2;
    ctx.shadowBlur = 10;
    ctx.shadowColor = colors.primary;
    ctx.stroke();

    // Energy lines from silhouette if relaxed
    if (stressRef.current < 0.3) {
      const rayCount = 12;
      for (let r = 0; r < rayCount; r++) {
        const angle = (r / rayCount) * Math.PI * 2 + time / 1000;
        const length = 60 + Math.sin(time / 200 + r) * 20;
        ctx.beginPath();
        ctx.moveTo(Math.cos(angle) * 45, Math.sin(angle) * 45);
        ctx.lineTo(Math.cos(angle) * length, Math.sin(angle) * length);
        ctx.strokeStyle = colors.secondary + '44';
        ctx.stroke();
      }
    }
    
    ctx.restore();

    requestRef.current = requestAnimationFrame(animate);
  };

  useEffect(() => {
    const handleResize = () => {
      if (canvasRef.current) {
        canvasRef.current.width = window.innerWidth;
        canvasRef.current.height = window.innerHeight;
        particlesRef.current = initParticles(window.innerWidth, window.innerHeight);
      }
    };

    window.addEventListener('resize', handleResize);
    handleResize();

    requestRef.current = requestAnimationFrame(animate);
    return () => {
      cancelAnimationFrame(requestRef.current);
      window.removeEventListener('resize', handleResize);
    };
  }, [isDiveMode, showTrails, showConnections]);

  // --- UI COMPONENTS ---
  const MetricCard = ({ icon: Icon, label, value, unit, color }) => (
    <div className="bg-black/40 backdrop-blur-md border border-white/10 rounded-xl p-3 flex items-center gap-3 min-w-[120px]">
      <div className={`p-2 rounded-lg bg-${color}-500/20`}>
        <Icon size={18} style={{ color }} />
      </div>
      <div>
        <p className="text-[10px] uppercase tracking-wider text-white/40">{label}</p>
        <p className="text-lg font-mono font-bold text-white leading-tight">
          {value}<span className="text-[10px] ml-1 text-white/60">{unit}</span>
        </p>
      </div>
    </div>
  );

  return (
    <div className={`fixed inset-0 overflow-hidden font-sans select-none`} style={{ backgroundColor: colors.background }}>
      {/* BACKGROUND CANVAS */}
      <canvas 
        ref={canvasRef} 
        className="absolute inset-0 z-0"
        style={{ filter: isDiveMode ? 'contrast(1.2) brightness(0.8)' : 'none' }}
      />

      {/* OVERLAYS */}
      {showOverlays && (
        <>
          {/* HEADER INFO */}
          <div className="absolute top-6 left-6 z-10 flex flex-col gap-2">
            <div className="flex items-center gap-3 mb-2">
              <div className="w-10 h-10 rounded-full bg-white/5 backdrop-blur-md border border-white/10 flex items-center justify-center">
                <Brain size={24} className="text-white" />
              </div>
              <div>
                <h1 className="text-xl font-bold tracking-tighter text-white">ANTIGRAVITY</h1>
                <div className="flex items-center gap-2">
                  <div className={`w-2 h-2 rounded-full animate-pulse`} style={{ backgroundColor: colors.primary }} />
                  <span className="text-[9px] uppercase tracking-[0.2em] text-white/50">NeuroHeadband v2.0.4</span>
                </div>
              </div>
            </div>
          </div>

          <div className="absolute top-6 right-6 z-10 flex flex-col items-end gap-3 text-right">
             <div className="bg-black/40 backdrop-blur-md border border-white/10 rounded-full px-3 py-1 flex items-center gap-2">
                <span className="text-[10px] font-mono text-white/60">TFLite GRU_CORE</span>
                <div className="w-1 h-3 bg-green-500 rounded-full" />
                <span className="text-[10px] font-mono text-white/60">12ms</span>
             </div>
             <div className="flex gap-2">
                <div className="bg-blue-500/20 text-blue-400 border border-blue-500/30 rounded px-2 py-0.5 text-[10px] font-bold">BLE CONNECTED</div>
                <div className="bg-white/5 text-white/40 border border-white/10 rounded px-2 py-0.5 text-[10px] font-bold">MODE: {isDiveMode ? 'DIVE' : 'FLIGHT'}</div>
             </div>
          </div>

          {/* LEFT METRICS */}
          <div className="absolute top-32 left-6 z-10 flex flex-col gap-3">
            <MetricCard 
              icon={Heart} 
              label="Heart Rate" 
              value={heartRate} 
              unit="BPM" 
              color={stress > 0.6 ? '#FF3B3B' : '#00FFB2'} 
            />
            <MetricCard 
              icon={Zap} 
              label="SDNN" 
              value={sdnn} 
              unit="MS" 
              color={sdnn > 40 ? '#00FFB2' : '#FFB800'} 
            />
            <MetricCard 
              icon={Activity} 
              label="RMSSD" 
              value={rmssd} 
              unit="MS" 
              color={rmssd > 30 ? '#00FFB2' : '#FFB800'} 
            />
          </div>

          {/* RIGHT STRESS BAR */}
          <div className="absolute top-32 right-6 z-10 flex flex-col items-end gap-4 h-[300px]">
            <div className="flex flex-col items-center gap-2 h-full py-4 bg-black/40 backdrop-blur-md border border-white/10 rounded-full w-12">
               <span className="text-[10px] font-mono text-white/40 rotate-90 mb-4 whitespace-nowrap">STRESS</span>
               <div className="flex-1 w-1.5 bg-white/10 rounded-full overflow-hidden flex flex-col justify-end">
                 <div 
                   className="w-full transition-all duration-1000" 
                   style={{ height: `${stress * 100}%`, backgroundColor: colors.primary, boxShadow: `0 0 10px ${colors.primary}` }} 
                 />
               </div>
               <span className="text-[12px] font-mono font-bold text-white mt-4">{(stress * 100).toFixed(0)}%</span>
            </div>

            <div className="flex flex-col items-center gap-2 h-[150px] py-4 bg-black/40 backdrop-blur-md border border-white/10 rounded-full w-12">
               <span className="text-[10px] font-mono text-white/40 rotate-90 mb-4 whitespace-nowrap">LIFT</span>
               <div className="flex-1 w-1.5 bg-white/10 rounded-full overflow-hidden flex flex-col justify-end">
                 <div 
                   className="w-full transition-all duration-1000" 
                   style={{ height: `${(1 - stress) * 100}%`, backgroundColor: colors.secondary, boxShadow: `0 0 10px ${colors.secondary}` }} 
                 />
               </div>
               <span className="text-[12px] font-mono font-bold text-white mt-4">{((1 - stress) * 100).toFixed(0)}%</span>
            </div>
          </div>

          {/* BOTTOM IBI BUFFER CHART */}
          <div className="absolute bottom-32 left-1/2 -translate-x-1/2 z-10 w-[400px]">
            <div className="bg-black/40 backdrop-blur-md border border-white/10 rounded-2xl p-4">
              <div className="flex items-center justify-between mb-2">
                <span className="text-[10px] uppercase font-bold tracking-widest text-white/30">IBI Waveform (30 window)</span>
                <span className="text-[10px] font-mono text-white/50">{ibiBuffer[29]}ms</span>
              </div>
              <div className="h-16 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={ibiBuffer.map((v, i) => ({ val: v, id: i }))}>
                    <defs>
                      <linearGradient id="gradientIBI" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor={colors.primary} stopOpacity={0.6}/>
                        <stop offset="100%" stopColor={colors.primary} stopOpacity={0}/>
                      </linearGradient>
                    </defs>
                    <Area 
                      type="monotone" 
                      dataKey="val" 
                      stroke={colors.primary} 
                      fill="url(#gradientIBI)" 
                      strokeWidth={2}
                      isAnimationActive={false}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>
        </>
      )}

      {/* DIVE BUTTON */}
      <button 
        onClick={() => setIsDiveMode(!isDiveMode)}
        className={`absolute bottom-6 left-1/2 -translate-x-1/2 z-20 px-10 py-4 rounded-full font-bold tracking-[0.3em] transition-all duration-700
          ${isDiveMode ? 'bg-blue-600 shadow-[0_0_30px_rgba(37,99,235,0.5)] text-white' : 'bg-white/5 backdrop-blur-xl border border-white/10 text-white/60 hover:text-white hover:bg-white/10'}
        `}
      >
        {isDiveMode ? 'SURFACE' : 'DIVE'}
      </button>

      {/* CONTROLS */}
      <div className="absolute bottom-6 right-6 z-20 flex flex-col items-end gap-3">
          {isControlsExpanded && (
            <div className="bg-black/60 backdrop-blur-2xl border border-white/10 rounded-2xl p-6 mb-2 w-[300px] shadow-2xl animate-in slide-in-from-bottom-4">
              <div className="flex items-center justify-between mb-6">
                <h3 className="font-bold text-white">SYSTEM CONTROLS</h3>
                <Settings size={16} className="text-white/40" />
              </div>
              
              <div className="space-y-6">
                {/* Stress Slider */}
                <div className="space-y-3">
                  <div className="flex justify-between text-[11px] font-mono text-white/60">
                    <span>STRESS SIMULATOR</span>
                    <span className="text-white">{(stress * 100).toFixed(0)}%</span>
                  </div>
                  <input 
                    type="range" 
                    min="0" 
                    max="1" 
                    step="0.01" 
                    value={stress}
                    onChange={(e) => setStress(parseFloat(e.target.value))}
                    className="w-full accent-white"
                  />
                  <div className="flex justify-between text-[9px] text-white/30 tracking-tight">
                    <span>ZEN</span>
                    <span>NEUTRAL</span>
                    <span>LIMIT</span>
                  </div>
                </div>

                {/* Toggles */}
                 <div className="space-y-2">
                    <div className="flex items-center justify-between p-2 rounded-lg bg-white/5 border border-white/10">
                       <span className="text-xs text-white/70">HUD DISPLAY</span>
                       <button onClick={() => setShowOverlays(!showOverlays)} className="text-white">
                         {showOverlays ? <Eye size={16} /> : <EyeOff size={16} />}
                       </button>
                    </div>
                    <div className="flex items-center justify-between p-2 rounded-lg bg-white/5 border border-white/10">
                       <span className="text-xs text-white/70">NEURAL FILAMENTS</span>
                       <button onClick={() => setShowConnections(!showConnections)} className="text-white">
                         {showConnections ? <Maximize size={16} /> : <Minimize size={16} />}
                       </button>
                    </div>
                    <div className="flex items-center justify-between p-2 rounded-lg bg-white/5 border border-white/10">
                       <span className="text-xs text-white/70">PARTICLE TRAILS</span>
                       <button onClick={() => setShowTrails(!showTrails)} className="text-white">
                         {showTrails ? <Wind size={16} /> : <Droplets size={16} />}
                       </button>
                    </div>
                 </div>

                 <button 
                  onClick={() => particlesRef.current = initParticles(window.innerWidth, window.innerHeight)}
                  className="w-full py-2 bg-white/10 hover:bg-white/20 rounded-lg text-xs font-bold text-white transition-colors"
                 >
                   RESET ENTROPY
                 </button>
              </div>
            </div>
          )}
          
          <button 
            onClick={() => setIsControlsExpanded(!isControlsExpanded)}
            className="w-12 h-12 rounded-full bg-black/40 backdrop-blur-md border border-white/10 flex items-center justify-center text-white/60 hover:text-white transition-colors"
          >
            {isControlsExpanded ? <ChevronDown /> : <Settings />}
          </button>
      </div>

      {/* BREATHING GUIDE (only when very relaxed) */}
      {stress < 0.2 && (
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-none z-5">
           <div className="w-[300px] h-[300px] rounded-full border border-cyan-500/20 animate-pulse-slow" />
           <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 text-cyan-500/40 text-[10px] tracking-[0.5em] whitespace-nowrap">
             BREATHE DEEPLY
           </div>
        </div>
      )}

      <style jsx>{`
        .animate-pulse-slow {
          animation: pulse 4s cubic-bezier(0.4, 0, 0.6, 1) infinite;
        }
        @keyframes pulse {
          0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 0.2; }
          50% { transform: translate(-50%, -50%) scale(1.5); opacity: 0.1; }
        }
        input[type=range] {
          -webkit-appearance: none;
          background: rgba(255,255,255,0.1);
          height: 4px;
          border-radius: 2px;
        }
        input[type=range]::-webkit-slider-thumb {
          -webkit-appearance: none;
          height: 16px;
          width: 16px;
          border-radius: 50%;
          background: white;
          cursor: pointer;
          border: 2px solid black;
        }
      `}</style>
    </div>
  );
};

export default Antigravity;
