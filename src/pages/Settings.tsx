import { useState } from "react";
import { storage } from "@/lib/storage";
import { ExportDialog } from "@/components/ExportDialog";
import { ImportDialog } from "@/components/ImportDialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Download, Upload, Settings as SettingsIcon } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { encryptData, decryptData } from "@/lib/encryption";

const Settings = () => {
  const [showExportDialog, setShowExportDialog] = useState(false);
  const [showImportDialog, setShowImportDialog] = useState(false);
  const { toast } = useToast();

  const handleExport = async (password: string): Promise<string> => {
    try {
      const data = storage.exportAllData();
      const encrypted = await encryptData(data, password);
      toast({
        title: "Успех",
        description: "Контакты зашифрованы и готовы к копированию",
      });
      return encrypted;
    } catch {
      toast({
        title: "Ошибка",
        description: "Не удалось экспортировать контакты",
        variant: "destructive",
      });
      return "";
    }
  };

  const handleImport = async (encryptedText: string, password: string) => {
    try {
      const decrypted = await decryptData(encryptedText, password);
      const success = storage.importAllData(decrypted);
      if (success) {
        toast({
          title: "Импорт завершен",
          description: "Контакты успешно расшифрованы и импортированы",
        });
      } else {
        toast({
          title: "Ошибка импорта",
          description: "Неверный формат данных",
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Ошибка импорта",
        description: error instanceof Error ? error.message : "Не удалось расшифровать данные",
        variant: "destructive",
      });
    }
  };

  return (
    <div className="min-h-screen bg-background pb-20 p-4 md:p-6 lg:p-8">
      <div className="max-w-4xl mx-auto space-y-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-bold flex items-center gap-2">
            <SettingsIcon className="w-8 h-8" />
            Настройки
          </h1>
          <p className="text-muted-foreground">
            Управление данными приложения
          </p>
        </header>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Download className="w-5 h-5" />
                Экспорт данных
              </CardTitle>
              <CardDescription>
                Экспортируйте все контакты в зашифрованном виде
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button onClick={() => setShowExportDialog(true)} className="w-full">
                <Download className="w-4 h-4 mr-2" />
                Экспорт с шифрованием
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Upload className="w-5 h-5" />
                Импорт данных
              </CardTitle>
              <CardDescription>
                Импортируйте контакты и напоминания из зашифрованной резервной копии
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button onClick={() => setShowImportDialog(true)} variant="outline" className="w-full">
                <Upload className="w-4 h-4 mr-2" />
                Импорт с шифрованием
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>

      <ExportDialog
        open={showExportDialog}
        onOpenChange={setShowExportDialog}
        onExport={handleExport}
      />

      <ImportDialog
        open={showImportDialog}
        onOpenChange={setShowImportDialog}
        onImport={handleImport}
      />

    </div>
  );
};

export default Settings;
