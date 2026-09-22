import { useState, useEffect } from "react";
import { DEBT_DIRECTIONS, DEBT_DIRECTION_LABELS, DebtDirection } from "@/types/contact";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { HandCoins } from "lucide-react";

export interface DebtDialogResult {
  direction: DebtDirection;
  description: string;
  amount?: number;
}

interface DebtDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: DebtDialogResult) => void | Promise<void>;
  initialData?: Partial<DebtDialogResult>;
  title: string;
}

export const DebtDialog = ({
  open,
  onOpenChange,
  onSubmit,
  initialData,
  title,
}: DebtDialogProps) => {
  const [direction, setDirection] = useState<DebtDirection>(
    initialData?.direction ?? "owed_to_me"
  );
  const [description, setDescription] = useState(initialData?.description ?? "");
  const [amount, setAmount] = useState(
    initialData?.amount !== undefined ? String(initialData.amount) : ""
  );
  const [error, setError] = useState("");

  useEffect(() => {
    if (open) {
      setDirection(initialData?.direction ?? "owed_to_me");
      setDescription(initialData?.description ?? "");
      setAmount(
        initialData?.amount !== undefined ? String(initialData.amount) : ""
      );
      setError("");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = description.trim();
    if (!trimmed) {
      setError("Введите описание долга");
      return;
    }
    const parsed = amount.trim() === "" ? undefined : Number(amount.replace(",", "."));
    if (parsed !== undefined && (!Number.isFinite(parsed) || parsed < 0)) {
      setError("Сумма должна быть неотрицательным числом");
      return;
    }
    onSubmit({
      direction,
      description: trimmed,
      amount: parsed === undefined ? undefined : Math.round(parsed * 100) / 100,
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <HandCoins className="w-5 h-5 text-primary" />
            {title}
          </DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label>Направление</Label>
            <div
              className="grid grid-cols-2 gap-2"
              role="group"
              aria-label="Направление долга"
            >
              {DEBT_DIRECTIONS.map((option) => (
                <Button
                  key={option}
                  type="button"
                  variant={direction === option ? "default" : "outline"}
                  aria-pressed={direction === option}
                  onClick={() => setDirection(option)}
                >
                  {DEBT_DIRECTION_LABELS[option]}
                </Button>
              ))}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="debt-description">
              Что должен / что мне должны <span className="text-destructive">*</span>
            </Label>
            <Textarea
              id="debt-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Например: вернул половину за ноутбук, остались детали..."
              rows={3}
              autoFocus
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="debt-amount">Сумма (₽)</Label>
            <Input
              id="debt-amount"
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value.replace(/[^0-9.,]/g, ""))}
              placeholder="Необязательно"
            />
          </div>

          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}

          <div className="flex gap-3">
            <Button type="submit" className="flex-1">
              Сохранить
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Отмена
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
};
