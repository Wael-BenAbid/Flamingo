import React, { useState } from 'react';
import { X } from 'lucide-react';
import { cn } from '../../lib/utils';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './select';

interface MoneyInputDialogProps {
  isOpen: boolean;
  title: string;
  reasonLabel: string;
  amountLabel: string;
  onConfirm: (amount: number, reason: string) => void;
  onCancel: () => void;
  buttonColor?: 'primary' | 'warning' | 'success';
  buttonLabel?: string;
  reasonOptions?: { label: string; value: string }[];
}

export function MoneyInputDialog({
  isOpen,
  title,
  reasonLabel,
  amountLabel,
  onConfirm,
  onCancel,
  buttonColor = 'primary',
  buttonLabel = 'Valider',
  reasonOptions,
}: MoneyInputDialogProps) {
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const selectedReason = reasonOptions && reasonOptions.length > 0
    ? (reason || reasonOptions[0].value)
    : reason;

  if (!isOpen) return null;

  const handleConfirm = () => {
    const amountNum = parseFloat(amount);
    if (!amount || isNaN(amountNum) || amountNum <= 0) {
      alert('Veuillez entrer un montant valide');
      return;
    }
    onConfirm(amountNum, selectedReason.trim() || 'Non spécifié');
    setAmount('');
    setReason('');
  };

  const handleCancel = () => {
    setAmount('');
    setReason('');
    onCancel();
  };

  const buttonColorClass = {
    primary: 'btn-primary',
    warning: 'btn-warning',
    success: 'btn-success'
  }[buttonColor];

  return (
    <div className="modal-overlay" onClick={handleCancel}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between p-6 border-b border-border">
          <h2 className="text-xl font-bold">{title}</h2>
          <button
            onClick={handleCancel}
            className="p-1 hover:bg-muted rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium mb-2 text-foreground">
              {reasonLabel}
            </label>
            {reasonOptions && reasonOptions.length > 0 ? (
              <Select value={selectedReason} onValueChange={setReason}>
                <SelectTrigger className="w-full px-4 py-2 rounded-lg border border-border bg-card text-foreground focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent">
                  <SelectValue placeholder="Choisir une méthode" />
                </SelectTrigger>
                <SelectContent>
                  {reasonOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <input
                type="text"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Ex: Avance demandée, Retard..."
                className="w-full px-4 py-2 rounded-lg border border-border bg-card text-foreground placeholder-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              />
            )}
          </div>

          <div>
            <label className="block text-sm font-medium mb-2 text-foreground">
              {amountLabel}
            </label>
            <div className="relative">
              <input
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                step="0.01"
                min="0"
                className="w-full px-4 py-2 rounded-lg border border-border bg-card text-foreground placeholder-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              />
              <span className="absolute right-4 top-2.5 text-muted-foreground font-medium">DT</span>
            </div>
          </div>
        </div>

        <div className="flex gap-3 p-6 border-t border-border">
          <button
            onClick={handleCancel}
            className="flex-1 px-4 py-2 rounded-lg btn-secondary font-medium transition-colors"
          >
            Annuler
          </button>
          <button
            onClick={handleConfirm}
            className={cn(
              'flex-1 px-4 py-2 rounded-lg font-medium text-foreground transition-opacity',
              buttonColorClass
            )}
          >
            {buttonLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
