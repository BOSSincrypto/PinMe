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
import { Lock, Copy, Check, Download } from "lucide-react";
import { Capacitor } from "@capacitor/core";
import { Filesystem, Directory } from "@capacitor/filesystem";
import { Share } from "@capacitor/share";

interface ExportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onExport: (password: string) => Promise<string>;
}

export const ExportDialog = ({ open, onOpenChange, onExport }: ExportDialogProps) => {
  const [password, setPassword] = useState("");
  const [exportedText, setExportedText] = useState("");
  const [copied, setCopied] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password.trim()) {
      const encrypted = await onExport(password);
      setExportedText(encrypted);
      setPassword("");
    }
  };

  const handleCopy = async () => {
    if (Capacitor.isNativePlatform()) {
      // На мобильном устройстве используем Share API
      try {
        await Share.share({
          text: exportedText,
          dialogTitle: 'Экспорт контактов (зашифровано)',
        });
      } catch (error) {
        console.error("Share failed:", error);
      }
    } else {
      // На веб-версии используем clipboard
      await navigator.clipboard.writeText(exportedText);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleDownload = async () => {
    if (Capacitor.isNativePlatform()) {
      try {
        const fileName = `contacts_encrypted_${new Date().getTime()}.txt`;
        await Filesystem.writeFile({
          path: fileName,
          data: exportedText,
          directory: Directory.Documents,
        });

        // Поделиться файлом
        const fileUri = await Filesystem.getUri({
          path: fileName,
          directory: Directory.Documents,
        });

        await Share.share({
          url: fileUri.uri,
          dialogTitle: 'Сохранить зашифрованные контакты',
        });
      } catch (error) {
        console.error("Download failed:", error);
      }
    } else {
      // На веб-версии скачиваем файл
      const blob = new Blob([exportedText], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `contacts_encrypted_${new Date().getTime()}.txt`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }
  };

  const handleClose = () => {
    setExportedText("");
    setPassword("");
    setCopied(false);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Lock className="w-5 h-5" />
            {exportedText ? "Скопируйте зашифрованные данные" : "Защита экспорта"}
          </DialogTitle>
          <DialogDescription>
            {exportedText
              ? "Скопируйте текст и сохраните в надежном месте"
              : "Введите пароль для шифрования экспортируемых данных"}
          </DialogDescription>
        </DialogHeader>
        {!exportedText ? (
          <form onSubmit={handleSubmit}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="export-password">Пароль</Label>
                <Input
                  id="export-password"
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
              <Button type="submit">Экспортировать</Button>
            </DialogFooter>
          </form>
        ) : (
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>Зашифрованные данные</Label>
              <Textarea
                value={exportedText}
                readOnly
                className="font-mono text-xs min-h-[200px]"
              />
            </div>
            <DialogFooter className="flex-col sm:flex-row gap-2">
              <Button type="button" variant="outline" onClick={handleClose}>
                Закрыть
              </Button>
              <Button onClick={handleDownload} variant="secondary">
                <Download className="w-4 h-4 mr-2" />
                {Capacitor.isNativePlatform() ? "Сохранить файл" : "Скачать"}
              </Button>
              <Button onClick={handleCopy}>
                {copied ? (
                  <>
                    <Check className="w-4 h-4 mr-2" />
                    Скопировано!
                  </>
                ) : (
                  <>
                    <Copy className="w-4 h-4 mr-2" />
                    {Capacitor.isNativePlatform() ? "Поделиться" : "Копировать"}
                  </>
                )}
              </Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
};
