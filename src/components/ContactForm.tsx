import { useState, useRef } from "react";
import { ContactFormData } from "@/types/contact";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { TagManager } from "@/components/TagManager";
import { Plus, X, User, Upload } from "lucide-react";

const isValidEventDate = (value: string): boolean => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const date = new Date(`${value}T00:00:00.000Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
};

interface ContactFormProps {
  initialData?: ContactFormData;
  onSubmit: (data: ContactFormData) => void;
  onCancel: () => void;
  submitLabel: string;
}

export const ContactForm = ({
  initialData,
  onSubmit,
  onCancel,
  submitLabel,
}: ContactFormProps) => {
  const [formData, setFormData] = useState<ContactFormData>(
    initialData || {
      name: "",
      phone: "",
      email: "",
      workplace: "",
      position: "",
      source: "",
      password: "",
      notes: "",
      avatar: "",
      tags: [],
      birthday: "",
      socialMedia: [],
      events: [],
      additionalInfo: {},
    }
  );

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [eventError, setEventError] = useState("");

  const [additionalFields, setAdditionalFields] = useState<
    Array<{ key: string; value: string }>
  >(
    Object.entries(formData.additionalInfo || {}).map(([key, value]) => ({
      key,
      value,
    }))
  );

  const addSocialMedia = () => {
    setFormData({
      ...formData,
      socialMedia: [...(formData.socialMedia || []), { platform: "", url: "" }],
    });
  };

  const removeSocialMedia = (index: number) => {
    setFormData({
      ...formData,
      socialMedia: formData.socialMedia?.filter((_, i) => i !== index),
    });
  };

  const updateSocialMedia = (index: number, field: "platform" | "url", value: string) => {
    const updated = [...(formData.socialMedia || [])];
    updated[index][field] = value;
    setFormData({ ...formData, socialMedia: updated });
  };

  const addEvent = () => {
    setEventError("");
    setFormData({
      ...formData,
      events: [...(formData.events || []), { id: crypto.randomUUID(), title: "", date: "" }],
    });
  };

  const removeEvent = (index: number) => {
    setEventError("");
    setFormData({
      ...formData,
      events: formData.events?.filter((_, i) => i !== index),
    });
  };

  const updateEvent = (index: number, field: "title" | "date", value: string) => {
    setEventError("");
    const updated = [...(formData.events || [])];
    updated[index][field] = value;
    setFormData({ ...formData, events: updated });
  };

  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setFormData({ ...formData, avatar: reader.result as string });
      };
      reader.readAsDataURL(file);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const events = (formData.events || []).map((event) => ({
      ...event,
      title: event.title.trim(),
    }));
    if (events.some((event) => !event.title || !isValidEventDate(event.date))) {
      setEventError("Заполните название и корректную дату для каждого события");
      return;
    }
    setEventError("");
    const additionalInfo: Record<string, string> = {};
    additionalFields.forEach((field) => {
      if (field.key.trim() && field.value.trim()) {
        additionalInfo[field.key] = field.value;
      }
    });
    onSubmit({ ...formData, events, additionalInfo });
  };

  const addAdditionalField = () => {
    setAdditionalFields([...additionalFields, { key: "", value: "" }]);
  };

  const removeAdditionalField = (index: number) => {
    setAdditionalFields(additionalFields.filter((_, i) => i !== index));
  };

  const updateAdditionalField = (
    index: number,
    field: "key" | "value",
    value: string
  ) => {
    const updated = [...additionalFields];
    updated[index][field] = value;
    setAdditionalFields(updated);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div className="flex gap-3 mb-6">
        <Button type="submit" className="flex-1">
          {submitLabel}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel} className="flex-1">
          Отмена
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Аватар</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center gap-4">
            <Avatar className="w-24 h-24 sm:w-32 sm:h-32">
              {formData.avatar ? (
                <AvatarImage src={formData.avatar} alt={formData.name} />
              ) : (
                <AvatarFallback>
                  <User className="w-12 h-12 sm:w-16 sm:h-16" />
                </AvatarFallback>
              )}
            </Avatar>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handleAvatarChange}
              className="hidden"
            />
            <Button
              type="button"
              variant="outline"
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload className="h-4 w-4 mr-2" />
              Загрузить фото
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Основная информация</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="name">
              Имя <span className="text-destructive">*</span>
            </Label>
            <Input
              id="name"
              value={formData.name}
              onChange={(e) =>
                setFormData({ ...formData, name: e.target.value })
              }
              placeholder="Введите имя"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="phone">Телефон</Label>
            <Input
              id="phone"
              type="tel"
              value={formData.phone || ""}
              onChange={(e) =>
                setFormData({ ...formData, phone: e.target.value })
              }
              placeholder="+7 (XXX) XXX-XX-XX"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              value={formData.email || ""}
              onChange={(e) =>
                setFormData({ ...formData, email: e.target.value })
              }
              placeholder="example@email.com"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="workplace">Место работы</Label>
            <Input
              id="workplace"
              value={formData.workplace || ""}
              onChange={(e) =>
                setFormData({ ...formData, workplace: e.target.value })
              }
              placeholder="Название компании"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="position">Должность</Label>
            <Input
              id="position"
              value={formData.position || ""}
              onChange={(e) =>
                setFormData({ ...formData, position: e.target.value })
              }
              placeholder="Должность"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="source">Откуда узнал</Label>
            <Input
              id="source"
              value={formData.source || ""}
              onChange={(e) =>
                setFormData({ ...formData, source: e.target.value })
              }
              placeholder="Откуда вы узнали об этом контакте"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="password">
              Пароль доступа {!initialData && <span className="text-destructive">*</span>}
            </Label>
            <Input
              id="password"
              type="password"
              value={formData.password}
              onChange={(e) =>
                setFormData({ ...formData, password: e.target.value })
              }
              placeholder={initialData ? "Оставьте пустым, чтобы не менять" : "Пароль для защиты информации"}
              required={!initialData}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="birthday">День рождения</Label>
            <Input
              id="birthday"
              type="date"
              value={formData.birthday || ""}
              onChange={(e) =>
                setFormData({ ...formData, birthday: e.target.value })
              }
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="notes">Заметки</Label>
            <Textarea
              id="notes"
              value={formData.notes || ""}
              onChange={(e) =>
                setFormData({ ...formData, notes: e.target.value })
              }
              placeholder="Дополнительная информация..."
              rows={4}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Социальные сети</CardTitle>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={addSocialMedia}
            >
              <Plus className="w-4 h-4 mr-2" />
              Добавить
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          {(!formData.socialMedia || formData.socialMedia.length === 0) ? (
            <p className="text-sm text-muted-foreground text-center py-4">
              Нет социальных сетей. Нажмите кнопку выше, чтобы добавить.
            </p>
          ) : (
            formData.socialMedia.map((social, index) => (
              <div key={index} className="flex gap-2">
                <Input
                  placeholder="Платформа (VK, Telegram...)"
                  value={social.platform}
                  onChange={(e) =>
                    updateSocialMedia(index, "platform", e.target.value)
                  }
                  className="flex-1"
                />
                <Input
                  placeholder="Ссылка"
                  value={social.url}
                  onChange={(e) =>
                    updateSocialMedia(index, "url", e.target.value)
                  }
                  className="flex-1"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => removeSocialMedia(index)}
                >
                  <X className="w-4 h-4" />
                </Button>
              </div>
            ))
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>События</CardTitle>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={addEvent}
            >
              <Plus className="w-4 h-4 mr-2" />
              Добавить
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          {(!formData.events || formData.events.length === 0) ? (
            <p className="text-sm text-muted-foreground text-center py-4">
              Нет событий. Нажмите кнопку выше, чтобы добавить.
            </p>
          ) : (
            formData.events.map((event, index) => (
              <div key={event.id} className="flex gap-2">
                <Input
                  placeholder="Название события"
                  value={event.title}
                  onChange={(e) =>
                    updateEvent(index, "title", e.target.value)
                  }
                  className="flex-1"
                  required
                />
                <Input
                  type="date"
                  value={event.date}
                  onChange={(e) =>
                    updateEvent(index, "date", e.target.value)
                  }
                  className="flex-1"
                  required
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => removeEvent(index)}
                >
                  <X className="w-4 h-4" />
                </Button>
              </div>
            ))
          )}
          {eventError && (
            <p className="text-sm text-destructive" role="alert">
              {eventError}
            </p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Теги</CardTitle>
        </CardHeader>
        <CardContent>
          <TagManager
            tags={formData.tags || []}
            onChange={(tags) => setFormData({ ...formData, tags })}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Дополнительные поля</CardTitle>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={addAdditionalField}
            >
              <Plus className="w-4 h-4 mr-2" />
              Добавить поле
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          {additionalFields.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-4">
              Нет дополнительных полей. Нажмите кнопку выше, чтобы добавить.
            </p>
          ) : (
            additionalFields.map((field, index) => (
              <div key={index} className="flex gap-2">
                <Input
                  placeholder="Название поля"
                  value={field.key}
                  onChange={(e) =>
                    updateAdditionalField(index, "key", e.target.value)
                  }
                  className="flex-1"
                />
                <Input
                  placeholder="Значение"
                  value={field.value}
                  onChange={(e) =>
                    updateAdditionalField(index, "value", e.target.value)
                  }
                  className="flex-1"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => removeAdditionalField(index)}
                >
                  <X className="w-4 h-4" />
                </Button>
              </div>
            ))
          )}
        </CardContent>
      </Card>

      <div className="h-32 md:h-0"></div>
    </form>
  );
};
