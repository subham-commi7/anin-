import React, { useEffect, useRef } from 'react';

interface VoiceWaveformVisualizerProps {
  isActive: boolean;
  waveColor?: string;
  className?: string;
  barCount?: number;
}

export const VoiceWaveformVisualizer: React.FC<VoiceWaveformVisualizerProps> = ({
  isActive,
  waveColor = '#6366f1',
  className = '',
  barCount = 28
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    let phase = 0;

    const render = () => {
      const width = canvas.width;
      const height = canvas.height;
      ctx.clearRect(0, 0, width, height);

      const barWidth = Math.floor(width / (barCount * 1.5));
      const gap = Math.floor(barWidth * 0.5);
      const totalWidth = barCount * (barWidth + gap);
      const startX = (width - totalWidth) / 2;

      for (let i = 0; i < barCount; i++) {
        let barHeight = 4;
        if (isActive) {
          // Dynamic wave pattern
          const factor = Math.sin(phase + i * 0.28) * Math.cos(phase * 0.7 + i * 0.15);
          barHeight = Math.max(6, Math.abs(factor) * (height * 0.85));
        }

        const x = startX + i * (barWidth + gap);
        const y = (height - barHeight) / 2;

        ctx.fillStyle = isActive ? waveColor : '#334155';
        ctx.beginPath();
        ctx.roundRect(x, y, barWidth, barHeight, 4);
        ctx.fill();
      }

      phase += isActive ? 0.12 : 0.02;
      animId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animId);
    };
  }, [isActive, waveColor, barCount]);

  return (
    <canvas
      ref={canvasRef}
      width={360}
      height={56}
      className={`w-full max-w-md h-14 ${className}`}
    />
  );
};
