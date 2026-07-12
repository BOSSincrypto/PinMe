import { useState, useEffect } from "react";
import { Contact } from "@/types/contact";
import { PRIORITY_LABELS } from "@/types/reminder";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { format } from "date-fns";
import { ru } from "date-fns/locale";
import { CalendarIcon, Clock } from "lucide-react";

interface ReminderDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (data: {
    text: string;
    dueAt: string | null;
    priority: 0 | 1 | 2;
    contactId: string | null;
  }) => void;
  contacts: Contact[];
  initialData?: {
    text: string;
    dueAt: string | null;
    priority: 0 | 1 | 2;
    contactId: string | null;
  };
  title?: string;
}

export const ReminderDialog = ({
  open,
  onOpenChange,
  onSubmit,
  contacts,
  initialData,
  title = "Новое напоминание",
}: ReminderDialogProps) => {
  const [text, setText] = useState("");
  const [date, setDate] = useState<Date | undefined>(undefined);
  const [time, setTime] = useState("09:00");
  const [priority, setPriority] = useState<0 | 1 | 2>(1);
  const [contactId, setContactId] = useState<string | null>(null);
  const [hasDate, setHasDate] = useState(false);

  useEffect(() => {
    if (initialData) {
      setText(initialData.text);
      setPriority(initialData.priority);
      setContactId(initialData.contactId);
      if (initialData.dueAt) {
        const dueDate = new Date(initialData.dueAt);
        setDate(dueDate);
        setTime(format(dueDate, "HH:mm"));
        setHasDate(true);
      } else {
        setDate(undefined);
        setTime("09:00");
        setHasDate(false);
      }
    } else {
      setText("");
      setDate(undefined);
      setTime("09:00");
      setPriority(1);
      setContactId(null);
      setHasDate(false);
    }
  }, [initialData, open]);

  const handleSubmit = () => {
    if (!text.trim()) return;

    let dueAt: string | null = null;
    if (hasDate && date) {
      const [hours, minutes] = time.split(":").map(Number);
      const dueDate = new Date(date);
      dueDate.setHours(hours, minutes, 0, 0);
      dueAt = dueDate.toISOString();
    }

    onSubmit({
      text: text.trim(),
      dueAt,
      priority,
      contactId,
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <Label htmlFor="text">Текст напоминания</Label>
            <Textarea
              id="text"
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Введите текст напоминания..."
              className="min-h-[80px]"
            />
          </div>

          <div className="space-y-2">
            <Label>Контакт (опционально)</Label>
            <Select
              value={contactId || "none"}
              onValueChange={(value) => setContactId(value === "none" ? null : value)}
            >
              <SelectTrigger>
                <SelectValue placeholder="Выберите контакт" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">Без контакта</SelectItem>
                {contacts.map((contact) => (
                  <SelectItem key={contact.id} value={contact.id}>
                    {contact.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="hasDate"
                checked={hasDate}
                onChange={(e) => setHasDate(e.target.checked)}
                className="rounded"
              />
              <Label htmlFor="hasDate">Установить дату и время</Label>
            </div>

            {hasDate && (
              <div className="flex gap-2">
                <Popover>
                  <PopoverTrigger asChild>
                    <Button
                      variant="outline"
                      className={cn(
                        "flex-1 justify-start text-left font-normal",
                        !date && "text-muted-foreground"
                      )}
                    >
                      <CalendarIcon className="mr-2 h-4 w-4" />
                      {date ? format(date, "d MMMM yyyy", { locale: ru }) : "Выберите дату"}
                    </Button>
                  </PopoverTrigger>
                  <PopoverContent className="w-auto p-0" align="start">
                    <Calendar
                      mode="single"
                      selected={date}
                      onSelect={setDate}
                      locale={ru}
                      initialFocus
                    />
                  </PopoverContent>
                </Popover>

                <div className="relative">
                  <Clock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    type="time"
                    value={time}
                    onChange={(e) => setTime(e.target.value)}
                    className="pl-10 w-[120px]"
                  />
                </div>
              </div>
            )}
          </div>

          <div className="space-y-2">
            <Label>Приоритет</Label>
            <Select
              value={priority.toString()}
              onValueChange={(value) => setPriority(parseInt(value) as 0 | 1 | 2)}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="0">{PRIORITY_LABELS[0]}</SelectItem>
                <SelectItem value="1">{PRIORITY_LABELS[1]}</SelectItem>
                <SelectItem value="2">{PRIORITY_LABELS[2]}</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Отмена
          </Button>
          <Button onClick={handleSubmit} disabled={!text.trim()}>
            {initialData ? "Сохранить" : "Создать"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
