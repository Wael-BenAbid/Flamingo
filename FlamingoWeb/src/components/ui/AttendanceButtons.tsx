import * as React from 'react';
import { motion } from 'motion/react';
import { Check, X, Clock, Palmtree } from 'lucide-react';
import { cn } from '../../lib/utils';

interface AttendanceButtonsProps {
  onPresent: () => void;
  onAbsent: () => void;
  onHalf: () => void;
  onOff?: () => void;
  orientation?: 'horizontal' | 'vertical';
  size?: 'sm' | 'md' | 'lg';
  compact?: boolean;
  currentStatus?: 'present' | 'absent' | 'half' | 'off';
}

export default function AttendanceButtons({
  onPresent,
  onAbsent,
  onHalf,
  onOff,
  orientation = 'horizontal',
  size = 'md',
  compact = false,
  currentStatus
}: AttendanceButtonsProps) {
  const containerClasses = cn(
    'flex gap-2 p-1 rounded-xl bg-slate-900/40 backdrop-blur-sm border border-white/5',
    orientation === 'vertical' ? 'flex-col' : 'flex-row'
  );

  const buttonClasses = (status: string, activeColor: string) => cn(
    'relative flex items-center justify-center gap-2 rounded-lg transition-all duration-300 font-bold uppercase tracking-widest text-[10px]',
    size === 'sm' ? 'px-2 py-1' : size === 'md' ? 'px-4 py-2' : 'px-6 py-3',
    compact ? 'w-auto' : 'flex-1',
    currentStatus === status ? `bg-${activeColor} text-white shadow-lg shadow-${activeColor}/20` : 'text-slate-400 hover:text-white hover:bg-white/5'
  );

  return (
    <div className={containerClasses}>
      <motion.button
        whileHover={{ scale: 1.02 }}
        whileTap={{ scale: 0.98 }}
        onClick={onPresent}
        className={buttonClasses('present', 'emerald-500')}
      >
        <Check className={cn("w-4 h-4", currentStatus === 'present' ? "text-white" : "text-emerald-500")} />
        {!compact && <span>Présent</span>}
      </motion.button>

      <motion.button
        whileHover={{ scale: 1.02 }}
        whileTap={{ scale: 0.98 }}
        onClick={onHalf}
        className={buttonClasses('half', 'orange-500')}
      >
        <Clock className={cn("w-4 h-4", currentStatus === 'half' ? "text-white" : "text-orange-500")} />
        {!compact && <span>Demi</span>}
      </motion.button>

      <motion.button
        whileHover={{ scale: 1.02 }}
        whileTap={{ scale: 0.98 }}
        onClick={onAbsent}
        className={buttonClasses('absent', 'red-500')}
      >
        <X className={cn("w-4 h-4", currentStatus === 'absent' ? "text-white" : "text-red-500")} />
        {!compact && <span>Absent</span>}
      </motion.button>

      {onOff && (
        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={onOff}
          className={buttonClasses('off', 'slate-500')}
        >
          <Palmtree className={cn("w-4 h-4", currentStatus === 'off' ? "text-white" : "text-slate-400")} />
          {!compact && <span>Off</span>}
        </motion.button>
      )}
    </div>
  );
}
