/**
 * ProductCard — Flamingo animated inventory / menu card
 *
 * Effects:
 *  • 3-D tilt that follows the mouse cursor (useMotionValue + useTransform)
 *  • Ambient amber highlight that tracks the pointer (like a moving light source)
 *  • Spring-based scale on hover — feels physical, not mechanical
 *  • Glassmorphism background with neumorphic inner shadow
 *
 * Usage:
 *   <ProductCard
 *     name="Mojito Royal"
 *     category="Boissons"
 *     stock={14}
 *     minStock={5}
 *     price={850}
 *     badge="En stock"
 *   />
 *
 * Dependencies: framer-motion (npm install framer-motion)
 */

import { useRef } from "react";
import {
  motion,
  useMotionValue,
  useTransform,
  useSpring,
  MotionValue,
} from "framer-motion";

// ── Design tokens (match index.css Flamingo dark palette) ────────────
const T = {
  amber:   "#F59B35",
  coral:   "#E8603A",
  teal:    "#2EC4B6",
  pearl:   "#F0EEE8",
  mist:    "#9DB4C0",
  surface: "#13203A",
  raised:  "#1C2E4A",
  crimson: "#E63946",
};

// ── Types ─────────────────────────────────────────────────────────────
interface ProductCardProps {
  name:      string;
  category:  string;
  stock:     number;
  minStock:  number;
  price?:    number;
  /** Short label shown in the corner badge (e.g. "En stock", "Stock bas", "Épuisé") */
  badge?:    string;
  onClick?:  () => void;
}

// ── Helper: convert mouse offset to rotation & highlight position ─────
function useCardTilt(ref: React.RefObject<HTMLDivElement | null>) {
  const rawX = useMotionValue(0);
  const rawY = useMotionValue(0);

  // Spring-smooth the raw values so movement feels elastic
  const springConfig = { stiffness: 200, damping: 22, mass: 0.8 };
  const x = useSpring(rawX, springConfig);
  const y = useSpring(rawY, springConfig);

  // Map mouse position (–0.5 → +0.5 of card dimensions) to rotation degrees
  const rotateX = useTransform(y, [-0.5, 0.5], [  8, -8]);
  const rotateY = useTransform(x, [-0.5, 0.5], [-8,  8]);

  // Highlight position (percentage, for the radial gradient)
  const highlightX = useTransform(x, [-0.5, 0.5], ["0%",   "100%"]);
  const highlightY = useTransform(y, [-0.5, 0.5], ["0%",   "100%"]);

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const el = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    rawX.set((e.clientX - rect.left) / rect.width  - 0.5);
    rawY.set((e.clientY - rect.top)  / rect.height - 0.5);
  };

  const handleMouseLeave = () => {
    rawX.set(0);
    rawY.set(0);
  };

  return { rotateX, rotateY, highlightX, highlightY, handleMouseMove, handleMouseLeave };
}

// ── Sub-component: moving light highlight ─────────────────────────────
function CardHighlight({
  x,
  y,
}: {
  x: MotionValue<string>;
  y: MotionValue<string>;
}) {
  return (
    <motion.div
      className="pointer-events-none absolute inset-0 rounded-[20px] opacity-0 group-hover:opacity-100 transition-opacity duration-300"
      style={{
        background: `radial-gradient(circle at ${x.get()} ${y.get()}, rgba(245,155,53,0.14) 0%, transparent 65%)`,
      }}
    />
  );
}

// ── Stock status helpers ───────────────────────────────────────────────
function stockStatus(stock: number, minStock: number) {
  if (stock <= 0)            return { label: "Épuisé",    color: T.crimson };
  if (stock <= minStock)     return { label: "Stock bas", color: T.amber   };
  return                            { label: "En stock",  color: T.teal    };
}

function StockBar({ stock, minStock }: { stock: number; minStock: number }) {
  const max = Math.max(minStock * 3, stock, 1);
  const pct = Math.min((stock / max) * 100, 100);
  const { color } = stockStatus(stock, minStock);

  return (
    <div className="mt-3 space-y-1.5">
      <div className="h-1.5 rounded-full overflow-hidden" style={{ background: "rgba(157,180,192,0.15)" }}>
        <motion.div
          className="h-full rounded-full"
          style={{ background: color }}
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.9, ease: [0.22, 1, 0.36, 1] }}
        />
      </div>
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────
export function ProductCard({
  name,
  category,
  stock,
  minStock,
  price,
  badge,
  onClick,
}: ProductCardProps) {
  const ref = useRef<HTMLDivElement>(null);
  const { rotateX, rotateY, highlightX, highlightY, handleMouseMove, handleMouseLeave } =
    useCardTilt(ref);

  const { label: statusLabel, color: statusColor } = stockStatus(stock, minStock);
  const displayBadge = badge ?? statusLabel;

  return (
    <motion.div
      ref={ref}
      className="group relative cursor-pointer select-none"
      style={{
        perspective: 900,
        transformStyle: "preserve-3d",
      }}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      onClick={onClick}
      // Spring scale on hover
      whileHover={{ scale: 1.035 }}
      whileTap={{   scale: 0.975 }}
      transition={{ type: "spring", stiffness: 260, damping: 20 }}
    >
      <motion.div
        style={{
          rotateX,
          rotateY,
          transformStyle: "preserve-3d",
        }}
        className="relative overflow-hidden rounded-[20px] p-5"
        // Glassmorphism + neumorphism
        css={`
          background: ${T.surface};
          border: 1px solid rgba(46,69,96,0.60);
          box-shadow:
            6px  6px  18px rgba(6,13,24,0.60),
            -4px -4px 12px rgba(36,52,82,0.25),
            inset 0 1px 0 rgba(245,155,53,0.06);
        `}
      >
        {/* Moving light highlight */}
        <CardHighlight x={highlightX} y={highlightY} />

        {/* Header row */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 min-w-0">
            <p
              className="text-xs font-semibold uppercase tracking-[0.12em] mb-1"
              style={{ color: T.mist }}
            >
              {category}
            </p>
            <h3
              className="text-base font-bold leading-snug truncate"
              style={{ color: T.pearl }}
            >
              {name}
            </h3>
          </div>

          {/* Status badge */}
          <span
            className="shrink-0 text-[11px] font-bold px-2.5 py-1 rounded-full"
            style={{
              background: `${statusColor}1A`,  /* 10% opacity */
              color: statusColor,
              border: `1px solid ${statusColor}33`,
            }}
          >
            {displayBadge}
          </span>
        </div>

        {/* Stock quantity */}
        <div className="mt-4 flex items-end justify-between">
          <div>
            <span className="text-3xl font-black" style={{ color: T.pearl }}>
              {stock}
            </span>
            <span className="ml-1.5 text-sm" style={{ color: T.mist }}>
              / {minStock} min
            </span>
          </div>

          {price !== undefined && (
            <span
              className="text-sm font-bold"
              style={{
                background: `linear-gradient(135deg, ${T.amber}, ${T.coral})`,
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
              }}
            >
              {price.toLocaleString("fr-TN")} DT
            </span>
          )}
        </div>

        {/* Animated stock progress bar */}
        <StockBar stock={stock} minStock={minStock} />

        {/* Subtle amber bottom border that glows on hover */}
        <motion.div
          className="absolute bottom-0 left-0 right-0 h-[2px] rounded-b-[20px]"
          style={{
            background: `linear-gradient(90deg, transparent, ${T.amber}, transparent)`,
          }}
          initial={{ opacity: 0, scaleX: 0.4 }}
          whileHover={{ opacity: 1, scaleX: 1 }}
          transition={{ duration: 0.3 }}
        />
      </motion.div>
    </motion.div>
  );
}

// ── Grid wrapper for a page of cards ─────────────────────────────────
export function ProductCardGrid({ children }: { children: React.ReactNode }) {
  return (
    <motion.div
      className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4"
      initial="hidden"
      animate="visible"
      variants={{
        hidden:  {},
        visible: { transition: { staggerChildren: 0.06 } },
      }}
    >
      {children}
    </motion.div>
  );
}

// Wrap each card with this for stagger entrance
export const cardVariants = {
  hidden:  { opacity: 0, y: 24, scale: 0.96 },
  visible: {
    opacity: 1, y: 0, scale: 1,
    transition: { type: "spring", stiffness: 220, damping: 22 },
  },
};
