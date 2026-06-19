/**
 * GlassReservationCard — Flamingo premium reservation card
 *
 * Design:
 *  • Glassmorphism surface (backdrop-blur 22px, amber-tinted border)
 *  • Framer Motion 3-D tilt following the mouse pointer
 *  • Animated moving light highlight (radial gradient tracks cursor)
 *  • Status pill with colour-coded amber / teal / coral / mist palette
 *  • Spring expand/collapse for details panel
 *  • Neumorphic inner shadow on expanded state
 *
 * Dependencies: framer-motion (npm install framer-motion)
 */

import { useRef, useState } from 'react';
import {
  motion,
  useMotionValue,
  useTransform,
  useSpring,
  AnimatePresence,
} from 'framer-motion';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';
import {
  Phone,
  Clock,
  MapPin,
  Users,
  MessageSquare,
  ChevronDown,
  Pencil,
  Trash2,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type { Reservation } from '@/hooks/useReservations';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from '@/components/ui/select';

// ── Design tokens (match index.css .dark palette) ─────────────────────
const T = {
  surface:    '#13203A',
  raised:     '#1C2E4A',
  amber:      '#F59B35',
  coral:      '#E8603A',
  teal:       '#2EC4B6',
  pearl:      '#F0EEE8',
  mist:       '#9DB4C0',
  crimson:    '#E63946',
  borderFaint: 'rgba(245,155,53,0.10)',
  borderHover: 'rgba(245,155,53,0.22)',
};

// ── Status config ──────────────────────────────────────────────────────
const STATUS_CONFIG: Record<Reservation['status'], {
  label: string;
  icon:  string;
  color: string;
  bg:    string;
}> = {
  confirmed: { label: 'Confirmé',  icon: '✓', color: T.teal,   bg: 'rgba(46,196,182,0.12)' },
  pending:   { label: 'En attente', icon: '⏳', color: T.amber,  bg: 'rgba(245,155,53,0.12)' },
  cancelled: { label: 'Annulé',    icon: '✕', color: T.crimson, bg: 'rgba(230,57,70,0.12)' },
  absent:    { label: 'Absent',    icon: '✗', color: T.mist,   bg: 'rgba(157,180,192,0.12)' },
};

// ── 3-D tilt hook ─────────────────────────────────────────────────────
function useTilt(ref: React.RefObject<HTMLDivElement | null>) {
  const rx = useMotionValue(0);
  const ry = useMotionValue(0);
  const sx = useSpring(rx, { stiffness: 180, damping: 22, mass: 0.7 });
  const sy = useSpring(ry, { stiffness: 180, damping: 22, mass: 0.7 });
  const rotateX = useTransform(sy, [-0.5, 0.5], [7, -7]);
  const rotateY = useTransform(sx, [-0.5, 0.5], [-7, 7]);
  const lightX  = useTransform(sx, [-0.5, 0.5], ['0%', '100%']);
  const lightY  = useTransform(sy, [-0.5, 0.5], ['0%', '100%']);

  const onMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const el   = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    rx.set((e.clientX - rect.left) / rect.width  - 0.5);
    ry.set((e.clientY - rect.top)  / rect.height - 0.5);
  };
  const onMouseLeave = () => { rx.set(0); ry.set(0); };

  return { rotateX, rotateY, lightX, lightY, onMouseMove, onMouseLeave };
}

// ── Props ──────────────────────────────────────────────────────────────
interface GlassReservationCardProps {
  res:          Reservation;
  date:         string;
  onStatusChange: (id: string, status: Reservation['status']) => void;
  onEdit:       (res: Reservation) => void;
  onDelete:     (id: string) => void;
  isPast?:      boolean;
}

// ── Component ──────────────────────────────────────────────────────────
export function GlassReservationCard({
  res,
  date,
  onStatusChange,
  onEdit,
  onDelete,
  isPast = false,
}: GlassReservationCardProps) {
  const ref      = useRef<HTMLDivElement>(null);
  const { rotateX, rotateY, lightX, lightY, onMouseMove, onMouseLeave } = useTilt(ref);
  const [expanded, setExpanded] = useState(false);

  const status = STATUS_CONFIG[res.status];

  return (
    <motion.div
      ref={ref}
      style={{ perspective: 900, transformStyle: 'preserve-3d' }}
      onMouseMove={onMouseMove}
      onMouseLeave={onMouseLeave}
      whileHover={{ scale: 1.025 }}
      whileTap={{   scale: 0.975 }}
      transition={{ type: 'spring', stiffness: 240, damping: 22 }}
      className="group relative select-none"
    >
      <motion.div
        style={{
          rotateX,
          rotateY,
          transformStyle: 'preserve-3d',
          background:     T.surface,
          border:         `1px solid ${expanded ? T.borderHover : T.borderFaint}`,
          boxShadow:      expanded
            ? `6px 6px 24px rgba(6,13,24,0.65), -4px -4px 14px rgba(36,52,82,0.28),
               inset 0 1px 0 rgba(245,155,53,0.08)`
            : `4px 4px 16px rgba(6,13,24,0.50), -3px -3px 10px rgba(36,52,82,0.20)`,
          borderRadius: 20,
          overflow: 'hidden',
          transition: 'border-color 0.25s, box-shadow 0.3s',
        }}
        className={cn('relative', isPast && 'opacity-70 grayscale-[0.15]')}
      >
        {/* Moving light highlight */}
        <motion.div
          className="pointer-events-none absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
          style={{
            background: `radial-gradient(circle at ${lightX.get()} ${lightY.get()}, rgba(245,155,53,0.10) 0%, transparent 60%)`,
            borderRadius: 20,
          }}
        />

        {/* ── Header ─────────────────────────────────────────────── */}
        <button
          onClick={() => setExpanded(!expanded)}
          className="w-full flex items-center justify-between px-5 py-4 text-left"
        >
          <div className="flex-1 min-w-0">
            <p
              className="font-bold text-sm truncate"
              style={{ color: T.pearl }}
            >
              {res.firstName} {res.lastName}
            </p>
            <div
              className="flex items-center gap-3 mt-1.5 text-[10px] font-semibold uppercase tracking-wide"
              style={{ color: T.mist }}
            >
              <span className="flex items-center gap-1">
                <Phone className="w-2.5 h-2.5" />
                {res.phone}
              </span>
              <span className="flex items-center gap-1">
                <Clock className="w-2.5 h-2.5" />
                {res.time}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2.5 shrink-0">
            {/* Status pill */}
            <span
              className="text-[10px] font-bold px-2.5 py-1 rounded-full border"
              style={{
                color:           status.color,
                background:      status.bg,
                borderColor:     `${status.color}33`,
              }}
            >
              {status.icon} {status.label}
            </span>

            {/* Expand chevron */}
            <motion.div
              animate={{ rotate: expanded ? 180 : 0 }}
              transition={{ type: 'spring', stiffness: 300, damping: 25 }}
            >
              <ChevronDown
                className="w-4 h-4"
                style={{ color: T.mist }}
              />
            </motion.div>
          </div>
        </button>

        {/* ── Expandable details panel ───────────────────────────── */}
        <AnimatePresence>
          {expanded && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{   height: 0, opacity: 0 }}
              transition={{ type: 'spring', stiffness: 260, damping: 28 }}
              style={{ overflow: 'hidden' }}
            >
              {/* Amber divider */}
              <div
                className="mx-5"
                style={{
                  height: 1,
                  background: `linear-gradient(90deg, transparent, ${T.amber}33, transparent)`,
                }}
              />

              <div
                className="px-5 py-4 space-y-4"
                style={{ background: 'rgba(28,46,74,0.35)' }}
              >
                {/* Detail grid */}
                <div className="grid grid-cols-2 gap-y-2.5 text-[11px]">
                  <DetailRow
                    icon={<Users className="w-3 h-3" />}
                    label="Groupe"
                    value={`${res.adults} adultes • ${res.children} enfants`}
                  />
                  <DetailRow
                    icon={<MapPin className="w-3 h-3" />}
                    label="Position"
                    value={`${res.positionType}${res.positionNumber ? ` · N°${res.positionNumber}` : ''}`}
                  />
                  <DetailRow
                    icon={<Clock className="w-3 h-3" />}
                    label="Date"
                    value={format(new Date(date), 'dd MMMM yyyy', { locale: fr })}
                  />
                </div>

                {/* Notes */}
                {res.notes && (
                  <div
                    className="rounded-xl p-3 text-[10px]"
                    style={{ background: `${T.amber}12`, border: `1px solid ${T.amber}1A` }}
                  >
                    <p
                      className="flex items-center gap-1 font-bold uppercase tracking-wide mb-1"
                      style={{ color: T.amber, opacity: 0.8 }}
                    >
                      <MessageSquare className="w-2.5 h-2.5" />
                      Notes
                    </p>
                    <p className="italic" style={{ color: T.pearl, opacity: 0.75 }}>
                      {res.notes}
                    </p>
                  </div>
                )}

                {/* Actions row */}
                <div className="flex items-center gap-2 pt-1">
                  {/* Status selector */}
                  <Select
                    value={res.status}
                    onValueChange={(val) =>
                      onStatusChange(res.id, val as Reservation['status'])
                    }
                  >
                    <SelectTrigger
                      className="flex-1 h-8 text-[9px] font-bold uppercase rounded-lg"
                      style={{
                        background:   T.raised,
                        border:       `1px solid ${T.borderFaint}`,
                        color:        status.color,
                      }}
                    >
                      <span>{status.icon} {status.label}</span>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="confirmed">✓ Confirmé</SelectItem>
                      <SelectItem value="pending">⏳ En attente</SelectItem>
                      <SelectItem value="cancelled">✕ Annulé</SelectItem>
                      <SelectItem value="absent">✗ Absent</SelectItem>
                    </SelectContent>
                  </Select>

                  {/* Edit */}
                  <motion.button
                    whileTap={{ scale: 0.92 }}
                    onClick={() => onEdit(res)}
                    className="h-8 px-3 rounded-lg text-[9px] font-bold uppercase flex items-center gap-1"
                    style={{
                      background: `${T.amber}15`,
                      border:     `1px solid ${T.amber}30`,
                      color:      T.amber,
                    }}
                  >
                    <Pencil className="w-3 h-3" />
                    Mod
                  </motion.button>

                  {/* Delete */}
                  <motion.button
                    whileTap={{ scale: 0.92 }}
                    onClick={() => onDelete(res.id)}
                    className="h-8 px-3 rounded-lg text-[9px] font-bold uppercase flex items-center gap-1"
                    style={{
                      background: `${T.crimson}15`,
                      border:     `1px solid ${T.crimson}30`,
                      color:      T.crimson,
                    }}
                  >
                    <Trash2 className="w-3 h-3" />
                    Del
                  </motion.button>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Animated amber bottom accent line */}
        <motion.div
          className="absolute bottom-0 left-0 right-0"
          style={{
            height:     2,
            background: `linear-gradient(90deg, transparent, ${T.amber}, transparent)`,
            borderBottomLeftRadius:  20,
            borderBottomRightRadius: 20,
          }}
          initial={{ opacity: 0, scaleX: 0.3 }}
          animate={{ opacity: expanded ? 1 : 0, scaleX: expanded ? 1 : 0.3 }}
          transition={{ duration: 0.3 }}
        />
      </motion.div>
    </motion.div>
  );
}

// ── Detail row sub-component ───────────────────────────────────────────
function DetailRow({
  icon,
  label,
  value,
}: {
  icon:  React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-2">
      <span style={{ color: T.amber, opacity: 0.7 }}>{icon}</span>
      <span style={{ color: T.mist }}>{label}:</span>
      <span className="font-semibold" style={{ color: T.pearl }}>
        {value}
      </span>
    </div>
  );
}

// ── Date section header ────────────────────────────────────────────────
export function ReservationDateHeader({
  date,
  count,
  isPast = false,
}: {
  date:   string;
  count:  number;
  isPast?: boolean;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, x: -12 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ type: 'spring', stiffness: 200, damping: 24 }}
      className="flex items-center gap-3 px-4 py-3 rounded-xl"
      style={{
        background: isPast
          ? 'rgba(157,180,192,0.08)'
          : 'rgba(245,155,53,0.08)',
        borderLeft: `3px solid ${isPast ? T.mist : T.amber}`,
        border:     `1px solid ${isPast ? 'rgba(157,180,192,0.15)' : 'rgba(245,155,53,0.15)'}`,
        borderLeftWidth: 3,
      }}
    >
      <div>
        <p
          className="font-bold text-sm"
          style={{ color: isPast ? T.mist : '#111827' }}
        >
          {format(new Date(date), 'EEEE d MMMM yyyy', { locale: fr })}
        </p>
        <p
          className="text-[10px] font-semibold uppercase tracking-wider mt-0.5"
          style={{ color: isPast ? T.mist : T.amber, opacity: 0.75 }}
        >
          {count} réservation{count > 1 ? 's' : ''}
        </p>
      </div>
    </motion.div>
  );
}

// ── Stagger grid wrapper ───────────────────────────────────────────────
export function ReservationCardGrid({ children }: { children: React.ReactNode }) {
  return (
    <motion.div
      className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
      initial="hidden"
      animate="show"
      variants={{
        hidden: {},
        show:   { transition: { staggerChildren: 0.07 } },
      }}
    >
      {children}
    </motion.div>
  );
}

export const cardEntrance = {
  hidden: { opacity: 0, y: 20, scale: 0.96 },
  show: {
    opacity: 1, y: 0, scale: 1,
    transition: { type: 'spring', stiffness: 220, damping: 24 },
  },
};
