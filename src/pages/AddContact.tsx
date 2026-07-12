import { useNavigate } from "react-router-dom";
import { storage } from "@/lib/storage";
import { ContactFormData } from "@/types/contact";
import { ContactForm } from "@/components/ContactForm";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { createPasswordDigest } from "@/lib/encryption";

const AddContact = () => {
  const navigate = useNavigate();
  const { toast } = useToast();

  const handleSubmit = async (data: ContactFormData) => {
    const { password, ...contactData } = data;
    const credentials = await createPasswordDigest(password);
    const newContact = {
      ...contactData,
      id: crypto.randomUUID(),
      passwordHash: credentials.hash,
      passwordSalt: credentials.salt,
      passwordIterations: credentials.iterations,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    if (!storage.addContact(newContact)) {
      toast({
        title: "Ошибка сохранения",
        description: "Не удалось сохранить контакт. Освободите место и попробуйте снова",
        variant: "destructive",
      });
      return;
    }
    toast({
      title: "Контакт добавлен",
      description: `${data.name} успешно добавлен в список контактов`,
    });
    navigate("/");
  };

  const handleCancel = () => {
    navigate("/");
  };

  return (
    <div className="min-h-screen bg-background pb-32 p-4 md:p-6 lg:p-8">
      <div className="max-w-2xl mx-auto space-y-6">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={handleCancel}>
            <ArrowLeft className="w-5 h-5" />
          </Button>
          <h1 className="text-3xl font-bold">Новый контакт</h1>
        </div>

        <ContactForm
          onSubmit={handleSubmit}
          onCancel={handleCancel}
          submitLabel="Добавить контакт"
        />
      </div>
    </div>
  );
};

export default AddContact;
