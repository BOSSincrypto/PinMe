import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Lock, Upload } from "lucide-react";

interface ImportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImport: (encryptedText: string, password: string) => void;
}

export const ImportDialog = ({ open, onOpenChange, onImport }: ImportDialogProps) => {
  const [password, setPassword] = useState("");
  const [encryptedText, setEncryptedText] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (encryptedText.trim() && password.trim()) {
      onImport(encryptedText, password);
      setPassword("");
      setEncryptedText("");
      onOpenChange(false);
    }
  };

  const handleClose = () => {
    setPassword("");
    setEncryptedText("");
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Lock className="w-5 h-5" />
            Импорт данных
          </DialogTitle>
          <DialogDescription>
            Вставьте зашифрованный текст и введите пароль для расшифровки
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="encrypted-text">Зашифрованный текст</Label>
              <Textarea
                id="encrypted-text"
                value={encryptedText}
                onChange={(e) => setEncryptedText(e.target.value)}
                placeholder="Вставьте зашифрованный текст здесь..."
                className="font-mono text-xs min-h-[200px]"
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="import-password">Пароль</Label>
              <Input
                id="import-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Введите пароль"
                required
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={handleClose}>
              Отмена
            </Button>
            <Button type="submit">
              <Upload className="w-4 h-4 mr-2" />
              Импортировать
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
};
